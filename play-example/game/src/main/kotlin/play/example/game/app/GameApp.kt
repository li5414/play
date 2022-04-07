package play.example.game.app

import com.typesafe.config.Config
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import play.DefaultGracefullyShutdown
import play.GracefullyShutdown
import play.db.Repository
import play.db.memory.MemoryRepository
import play.entity.PlayEntityCacheConfiguration
import play.entity.cache.DefaultEntityCachePersistFailOver
import play.entity.cache.EntityCacheManager
import play.entity.cache.EntityCachePersistFailOver
import play.event.EnableGuavaEventBus
import play.example.game.container.command.CommandManager
import play.example.game.container.command.CommandService
import play.example.game.container.gs.domain.GameServerId
import play.inject.PlayInjector
import play.inject.SpringPlayInjector
import play.scheduling.Scheduler
import play.util.concurrent.PlayFuture

/**
 *
 * @author LiangZengle
 */
@SpringBootApplication
@Import(value = [PlayEntityCacheConfiguration::class])
@EnableGuavaEventBus
@Configuration(proxyBeanMethods = false)
class GameApp {

  @Bean
  fun gmCommandService(injector: PlayInjector, invokerManager: CommandManager): CommandService {
    return CommandService(injector, invokerManager)
  }

  @Bean
  fun playInjector(applicationContext: ApplicationContext): PlayInjector {
    return SpringPlayInjector(applicationContext)
  }

  @Bean
  fun entityCachePersistFailOver(gameServerId: GameServerId): EntityCachePersistFailOver {
    return DefaultEntityCachePersistFailOver("entity_back_up/${gameServerId.toInt()}")
  }

  @Bean
  @ConditionalOnProperty(prefix = "play", name = ["db.repository"], havingValue = "memory")
  fun repository(): Repository {
    return MemoryRepository()
  }

  /**
   * game application should use the [GracefullyShutdown] in container
   */
  @Bean(destroyMethod = "run")
  fun gracefullyShutdown(
    config: Config, scheduler: Scheduler, entityCacheManager: EntityCacheManager, gameServerId: GameServerId
  ): GracefullyShutdown {
    val phases = GracefullyShutdown.phaseFromConfig(config.getConfig("play.shutdown"))
    val shutdown = DefaultGracefullyShutdown("GameApp(${gameServerId.toInt()})", phases, false, null)
    shutdown.addTask(
      GracefullyShutdown.PHASE_SHUTDOWN_SCHEDULER, GracefullyShutdown.PHASE_SHUTDOWN_SCHEDULER, scheduler
    ) { PlayFuture { if (it is AutoCloseable) it.close() } }
    shutdown.addTask(
      GracefullyShutdown.PHASE_FLUSH_ENTITY_CACHE, GracefullyShutdown.PHASE_FLUSH_ENTITY_CACHE, entityCacheManager
    ) { PlayFuture { if (it is AutoCloseable) it.close() } }
    return shutdown
  }
}
