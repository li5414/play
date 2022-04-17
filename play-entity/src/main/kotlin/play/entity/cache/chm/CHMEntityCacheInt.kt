package play.entity.cache.chm

import mu.KLogging
import play.entity.IntIdEntity
import play.entity.cache.*
import play.inject.PlayInjector
import play.scheduling.Scheduler
import play.util.collection.ConcurrentIntObjectMap
import play.util.concurrent.Future
import play.util.concurrent.PlayFuture
import play.util.control.Retryable
import play.util.function.IntToObjFunction
import play.util.getOrNull
import play.util.json.Json
import play.util.time.Time.currentMillis
import play.util.toOptional
import play.util.unsafeCast
import java.time.Duration
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater

class CHMEntityCacheInt<E : IntIdEntity>(
  override val entityClass: Class<E>,
  private val entityCacheWriter: EntityCacheWriter,
  private val entityCacheLoader: EntityCacheLoader,
  private val injector: PlayInjector,
  private val scheduler: Scheduler,
  private val executor: Executor,
  private val settings: EntityCacheFactory.Settings,
  private val initializerProvider: EntityInitializerProvider
) : EntityCache<Int, E>, UnsafeEntityCacheOps<Int>, EntityCacheInternalApi {
  companion object : KLogging()

  private var initialized = false

  private var _cache: ConcurrentIntObjectMap<CacheObj<E>>? = null

  private lateinit var initializer: EntityInitializer<E>

  private lateinit var expireEvaluator: ExpireEvaluator

  private val isImmutable = entityClass.isAnnotationPresent(ImmutableEntity::class.java)

  private val isResident = EntityCacheHelper.isResident(entityClass)

  private fun getCache(): ConcurrentIntObjectMap<CacheObj<E>> {
    val cache = _cache
    if (cache != null) {
      return cache
    }
    ensureInitialized()
    return _cache!!
  }

  private fun ensureInitialized() {
    if (initialized) {
      return
    }
    synchronized(this) {
      if (initialized) {
        return
      }
      expireEvaluator = EntityCacheHelper.getExpireEvaluator(entityClass, injector)
      initializer = initializerProvider.get(entityClass)
      val cacheSpec = EntityCacheHelper.getCacheSpec(entityClass)

      EntityCacheHelper.reportMissingInitialCacheSize(entityClass)
      val initialCacheSize = EntityCacheHelper.getInitialSizeOrDefault(entityClass, settings.initialSize)
      val cache = ConcurrentIntObjectMap<CacheObj<E>>(initialCacheSize)
      val isLoadAllOnInit = cacheSpec.loadAllOnInit
      if (isLoadAllOnInit) {
        logger.debug { "Loading all [${entityClass.simpleName}]" }
        entityCacheLoader.loadAll(entityClass, cache) { c, e ->
          initializer.initialize(e)
          c[e.id] = NonEmpty(e)
          c
        }.await()
        logger.debug { "Loaded ${cache.size} [${entityClass.simpleName}] into cache." }
      }
      this._cache = cache

      val isNeverExpire = expireEvaluator == NeverExpireEvaluator
      if (!isNeverExpire) {
        val duration =
          if (cacheSpec.expireAfterAccess > 0) Duration.ofSeconds(cacheSpec.expireAfterAccess.toLong())
          else settings.expireAfterAccess
        val durationMillis = duration.toMillis()
        scheduler.scheduleWithFixedDelay(duration, duration.dividedBy(2), executor) { scheduledExpire(durationMillis) }
      }

      if (!isImmutable) {
        scheduler.scheduleWithFixedDelay(
          settings.persistInterval, settings.persistInterval.dividedBy(2), executor, this::scheduledPersist
        )
      }
      initialized = true
    }
  }

  private fun scheduledPersist() {
    val entities = getCache().values.asSequence()
      .filterIsInstance<NonEmpty<E>>()
      .filter { it.lastAccessTime() > it.lastPersistTime }
      .map { it.peekEntity() }
      .toList()
    if (entities.isNotEmpty()) {
      entityCacheWriter.batchInsertOrUpdate(entities).onSuccess {
        val cache = getCache()
        val now = currentMillis()
        for (entity in entities) {
          val obj = cache[entity.id()]
          if (obj is NonEmpty<E>) {
            obj.lastPersistTime = now
          }
        }
      }
    }
  }

  private fun scheduledExpire(expireAfterAccess: Long) {
    val cache = getCache()
    val accessTimeThreshold = currentMillis() - expireAfterAccess
    for (id in cache.keys) {
      cache.computeIfPresent(id) { _, v ->
        if (v.lastAccessTime() > accessTimeThreshold) v
        else if (v is NonEmpty<E>) {
          if (v.lastPersistTime > v.lastAccessTime() && expireEvaluator.canExpire(v.peekEntity())) {
            v.setExpired()
            null
          } else v
        } else null
      }
    }
  }

  private fun load(id: Int): E? {
    if (isResident) {
      return null
    }
    val f = entityCacheLoader.loadById(id, entityClass)
    try {
      val entity: E = f.blockingGet(settings.loadTimeout).getOrNull() ?: return null
      if (entity.isDeleted()) {
        entityCacheWriter.deleteById(id, entityClass)
        return null
      }
      initializer.initialize(entity)
      return entity
    } catch (e: Exception) {
      logger.error(e) { "Failed to load entity: ${entityClass.simpleName}($id)" }
      throw e
    }
  }

  private fun computeIfAbsent(id: Int, loader: IntToObjFunction<E?>?): E? {
    return computeIfAbsent(id, null, loader)
  }

  private fun computeIfAbsent(id: Int, loadOnEmpty: IntToObjFunction<E>?, loadOnAbsent: IntToObjFunction<E?>?): E? {
    val cache = getCache()
    var cacheObj = cache[id]
    if (cacheObj != null) {
      if (cacheObj is NonEmpty<E>) {
        val entity = cacheObj.accessEntity()
        if (!cacheObj.isExpired() ||
          cache.putIfAbsent(id, NonEmpty(entity)) == null /* 这一刻刚好过期了，但是还没有从数据库重新加载，可以直接使用 */) {
          return entity
        }
      } else if (loadOnEmpty == null) {
        return null
      }
    }
    if (loadOnEmpty == null && loadOnAbsent == null) return null
    cacheObj = cache.compute(id) { k, v ->
      if (v == null) {
        if (loadOnAbsent == null) null else loadOnAbsent(k)?.let { NonEmpty(it) } ?: Empty()
      } else if (v.isEmpty() && loadOnEmpty != null) {
        NonEmpty(loadOnEmpty(k))
      } else {
        if (v is NonEmpty) v.accessEntity() // update access time
        v
      }
    }
    return if (cacheObj is NonEmpty) cacheObj.accessEntity() else null
  }

  override fun get(id: Int): Optional<E> {
    return getOrNull(id).toOptional()
  }

  override fun getOrNull(id: Int): E? {
    return computeIfAbsent(id, ::load)
  }

  override fun getOrThrow(id: Int): E {
    return getOrNull(id) ?: throw NoSuchElementException("${entityClass.simpleName}($id)")
  }

  override fun getOrCreate(id: Int, creation: (Int) -> E): E {
    return computeIfAbsent(id, { k -> createEntity(k, creation) }, { k -> load(k) ?: createEntity(k, creation) })
      ?: error("won't happen")
  }

  private fun createEntity(id: Int, creation: IntToObjFunction<E>): E {
    val entity = creation(id)
    initializer.initialize(entity)
    entityCacheWriter.insert(entity)
    return entity
  }

  override fun getCached(id: Int): Optional<E> {
    return computeIfAbsent(id, null).toOptional()
  }

  override fun getCachedEntities(): Sequence<E> {
    return getCache().values.asSequence().filterIsInstance<NonEmpty<E>>().map { it.peekEntity() }
  }

  override fun getAll(ids: Iterable<Int>): List<E> {
    val result = arrayListOf<E>()
    val missing = arrayListOf<Int>()
    for (id in ids) {
      val entity = getOrNull(id)
      if (entity != null) {
        result.add(entity)
      } else {
        missing.add(id)
      }
    }
    if (missing.isEmpty()) {
      return result
    }
    val loaded = entityCacheLoader.loadAll(missing, entityClass).blockingGet(Duration.ofSeconds(5))
    for (entity in loaded) {
      if (entity.isDeleted()) {
        continue
      }
      val e = computeIfAbsent(entity.id, null) { entity }
      if (e != null) {
        result.add(e)
      }
    }
    return result
  }

  override fun create(e: E): E {
    val that = getOrCreate(e.id) { e }
    if (e !== that) {
      throw EntityExistsException(e.javaClass, e.id)
    }
    return e
  }

  override fun delete(e: E) {
    delete(e.id)
  }

  override fun delete(id: Int) {
    getCache().compute(id) { _, _ ->
      entityCacheWriter.deleteById(id, entityClass).onFailure {
        logger.error(it) { "Delete entity failed: ${entityClass.simpleName}($id)" }
        retryDelete(id)
      }
      Empty()
    }
  }

  private fun retryDelete(id: Int) {
    // 防止Empty对象缓存过期:
    // 1. 重置间隔要小于过期时间
    // 2. 失败后需要刷新缓存
    Retryable.foreverAsync(
      "delete ${entityClass.simpleName}($id)",
      settings.expireAfterAccess.dividedBy(2).toMillis(),
      scheduler,
      executor
    ) {
      entityCacheWriter.deleteById(id, entityClass).unsafeCast<PlayFuture<Any?>>()
        .recoverWith { ex ->
          val cache = getCache()
          // 删除的实体被重新创建？
          if (cache[id] is NonEmpty<E>) {
            logger.warn { "Deleted entity has bean created again: ${entityClass.simpleName}($id)" }
            Future.successful(Unit)
          } else {
            // 刷新缓存，避免从数据加载一个被删除了的数据
            cache.compute(id) { _, v ->
              if (v is Empty<E>) Empty() else v
            }
            Future.failed(ex)
          }
        }
    }
  }

  override fun size(): Int {
    return getCache().size
  }

  override fun isCached(id: Int): Boolean {
    return getCache().containsKey(id)
  }

  override fun isEmpty(): Boolean {
    return getCache().isEmpty()
  }

  override fun dump(): String {
    return Json.prettyWriter().writeValueAsString(getCachedEntities().toList())
  }

  @Suppress("UNCHECKED_CAST")
  override fun persist(): Future<Unit> {
    if (!initialized) {
      return Future.successful(Unit)
    }
    val entities = getCache().values
      .asSequence()
      .filterIsInstance<NonEmpty<E>>()
      .filterNot { isImmutable && it.lastPersistTime != 0L }
      .map { it.peekEntity() }
      .toList()
    return entityCacheWriter.batchInsertOrUpdate(entities) as Future<Unit>
  }

  override fun expireEvaluator(): ExpireEvaluator = expireEvaluator

  override fun initWithEmptyValue(id: Int) {
    val prev = getCache().putIfAbsent(id, Empty())
    if (prev != null) {
      logger.warn { "初始化为空值失败, Entity已经存在: $prev" }
    }
  }

  private sealed class CacheObj<E : IntIdEntity> {
    abstract fun isEmpty(): Boolean

    fun isNotEmpty(): Boolean = !isEmpty()

    abstract fun lastAccessTime(): Long

    fun asEmpty(): Empty<E> = this.unsafeCast()

    fun asNonEmpty(): NonEmpty<E> = this.unsafeCast()
  }

  private class Empty<E : IntIdEntity> : CacheObj<E>() {
    private val createTime: Long = currentMillis()

    override fun isEmpty(): Boolean = true

    override fun lastAccessTime(): Long = createTime

    override fun toString(): String {
      return "Empty"
    }
  }

  private class NonEmpty<E : IntIdEntity>(
    private val entity: E,
    _lastAccessTime: Long,
    _lastPersistTime: Long
  ) : CacheObj<E>() {

    constructor(entity: E) : this(entity, currentMillis(), 0)

    @Volatile
    var lastAccessTime: Long = _lastAccessTime
      private set

    @Volatile
    var lastPersistTime: Long = _lastPersistTime

    @Volatile
    private var expired = 0

    fun isExpired() = expired == 1

    fun setExpired() {
      if (!ExpiredUpdater.compareAndSet(this, 0, 1)) {
        throw IllegalStateException("Entity Expired")
      }
    }

    fun accessEntity(): E {
      lastAccessTime = currentMillis()
      return entity
    }

    override fun isEmpty(): Boolean = false

    override fun lastAccessTime(): Long = lastAccessTime

    fun peekEntity(): E = entity

    override fun toString(): String {
      return entity.toString()
    }

    companion object {
      private val ExpiredUpdater: AtomicIntegerFieldUpdater<NonEmpty<*>> =
        AtomicIntegerFieldUpdater.newUpdater(NonEmpty::class.java, "expired")
    }
  }
}