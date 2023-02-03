// Code generated by Wire protocol buffer compiler, do not edit.
// Source: play.IntLongMapValue in play.proto
package play

import com.squareup.wire.*
import com.squareup.wire.Syntax.PROTO_3
import okio.ByteString
import org.eclipse.collections.api.factory.primitive.IntLongMaps
import org.eclipse.collections.api.map.primitive.IntLongMap
import org.eclipse.collectionx.asJava
import org.eclipse.collectionx.ofAll

public class IntLongMapValue(
  value: IntLongMap = IntLongMaps.immutable.empty(),
  unknownFields: ByteString = ByteString.EMPTY,
) : Message<IntLongMapValue, Nothing>(ADAPTER, unknownFields) {

  constructor(
    value: Map<Int, Long> = emptyMap(),
    unknownFields: ByteString = ByteString.EMPTY
  ) : this(IntLongMaps.immutable.ofAll(value), unknownFields)

  @field:WireField(
    tag = 1,
    keyAdapter = "com.squareup.wire.ProtoAdapter#INT32",
    adapter = "com.squareup.wire.ProtoAdapter#INT64",
    declaredName = "value",
  )
  public val value: IntLongMap = value.toImmutable()

  @Deprecated(
    message = "Shouldn't be used in Kotlin",
    level = DeprecationLevel.HIDDEN,
  )
  public override fun newBuilder(): Nothing =
    throw AssertionError("Builders are deprecated and only available in a javaInterop build; see https://square.github.io/wire/wire_compiler/#kotlin")

  public override fun equals(other: Any?): Boolean {
    if (other === this) return true
    if (other !is IntLongMapValue) return false
    if (unknownFields != other.unknownFields) return false
    if (value != other.value) return false
    return true
  }

  public override fun hashCode(): Int {
    var result = super.hashCode
    if (result == 0) {
      result = unknownFields.hashCode()
      result = result * 37 + value.hashCode()
      super.hashCode = result
    }
    return result
  }

  public override fun toString(): String {
    val result = mutableListOf<String>()
    if (value.notEmpty()) result += """value_=$value"""
    return result.joinToString(prefix = "IntLongMapValue{", separator = ", ", postfix = "}")
  }

  public fun copy(
    value: IntLongMap = this.value, unknownFields: ByteString =
      this.unknownFields
  ): IntLongMapValue = IntLongMapValue(value, unknownFields)

  public companion object {

    @JvmStatic
    val EMPTY = IntLongMapValue(IntLongMaps.immutable.empty())

    @JvmField
    public val ADAPTER: ProtoAdapter<IntLongMapValue> = object : ProtoAdapter<IntLongMapValue>(
      FieldEncoding.LENGTH_DELIMITED,
      IntLongMapValue::class,
      "type.googleapis.com/play.IntLongMapValue",
      PROTO_3,
      null,
      "play.proto"
    ) {
      private val valueAdapter: ProtoAdapter<Map<Int, Long>> by lazy {
        ProtoAdapter.newMapAdapter(ProtoAdapter.INT32, ProtoAdapter.INT64)
      }

      public override fun encodedSize(`value`: IntLongMapValue): Int {
        var size = value.unknownFields.size
        size += valueAdapter.encodedSizeWithTag(1, value.value.asJava())
        return size
      }

      public override fun encode(writer: ProtoWriter, `value`: IntLongMapValue): Unit {
        valueAdapter.encodeWithTag(writer, 1, value.value.asJava())
        writer.writeBytes(value.unknownFields)
      }

      public override fun encode(writer: ReverseProtoWriter, `value`: IntLongMapValue): Unit {
        writer.writeBytes(value.unknownFields)
        valueAdapter.encodeWithTag(writer, 1, value.value.asJava())
      }

      public override fun decode(reader: ProtoReader): IntLongMapValue {
        val value = IntLongMaps.mutable.empty()
        val unknownFields = reader.forEachTag { tag ->
          when (tag) {
            1 -> valueAdapter.decode(reader).forEach(value::put)
            else -> reader.readUnknownField(tag)
          }
        }
        return IntLongMapValue(
          value = value,
          unknownFields = unknownFields
        )
      }

      public override fun redact(`value`: IntLongMapValue): IntLongMapValue = value.copy(
        unknownFields = ByteString.EMPTY
      )
    }

    private const val serialVersionUID: Long = 0L
  }
}