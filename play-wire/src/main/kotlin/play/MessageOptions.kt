// Code generated by Wire protocol buffer compiler, do not edit.
// Source: play.MessageOptions in play.proto
package play

import com.squareup.wire.*
import com.squareup.wire.Syntax.PROTO_3
import com.squareup.wire.internal.immutableCopyOf
import com.squareup.wire.internal.sanitize
import okio.ByteString

public class MessageOptions(
  implements: List<String> = emptyList(),
  unknownFields: ByteString = ByteString.EMPTY,
) : Message<MessageOptions, Nothing>(ADAPTER, unknownFields) {
  @field:WireField(
    tag = 1,
    adapter = "com.squareup.wire.ProtoAdapter#STRING",
    label = WireField.Label.REPEATED,
  )
  public val implements: List<String> = immutableCopyOf("implements", implements)

  @Deprecated(
    message = "Shouldn't be used in Kotlin",
    level = DeprecationLevel.HIDDEN,
  )
  public override fun newBuilder(): Nothing = throw
      AssertionError("Builders are deprecated and only available in a javaInterop build; see https://square.github.io/wire/wire_compiler/#kotlin")

  public override fun equals(other: Any?): Boolean {
    if (other === this) return true
    if (other !is MessageOptions) return false
    if (unknownFields != other.unknownFields) return false
    if (implements != other.implements) return false
    return true
  }

  public override fun hashCode(): Int {
    var result = super.hashCode
    if (result == 0) {
      result = unknownFields.hashCode()
      result = result * 37 + implements.hashCode()
      super.hashCode = result
    }
    return result
  }

  public override fun toString(): String {
    val result = mutableListOf<String>()
    if (implements.isNotEmpty()) result += """implements=${sanitize(implements)}"""
    return result.joinToString(prefix = "MessageOptions{", separator = ", ", postfix = "}")
  }

  public fun copy(implements: List<String> = this.implements, unknownFields: ByteString =
      this.unknownFields): MessageOptions = MessageOptions(implements, unknownFields)

  public companion object {
    @JvmField
    public val ADAPTER: ProtoAdapter<MessageOptions> = object : ProtoAdapter<MessageOptions>(
      FieldEncoding.LENGTH_DELIMITED, 
      MessageOptions::class, 
      "type.googleapis.com/play.MessageOptions", 
      PROTO_3, 
      null, 
      "play.proto"
    ) {
      public override fun encodedSize(`value`: MessageOptions): Int {
        var size = value.unknownFields.size
        size += ProtoAdapter.STRING.asRepeated().encodedSizeWithTag(1, value.implements)
        return size
      }

      public override fun encode(writer: ProtoWriter, `value`: MessageOptions): Unit {
        ProtoAdapter.STRING.asRepeated().encodeWithTag(writer, 1, value.implements)
        writer.writeBytes(value.unknownFields)
      }

      public override fun encode(writer: ReverseProtoWriter, `value`: MessageOptions): Unit {
        writer.writeBytes(value.unknownFields)
        ProtoAdapter.STRING.asRepeated().encodeWithTag(writer, 1, value.implements)
      }

      public override fun decode(reader: ProtoReader): MessageOptions {
        val implements = mutableListOf<String>()
        val unknownFields = reader.forEachTag { tag ->
          when (tag) {
            1 -> implements.add(ProtoAdapter.STRING.decode(reader))
            else -> reader.readUnknownField(tag)
          }
        }
        return MessageOptions(
          implements = implements,
          unknownFields = unknownFields
        )
      }

      public override fun redact(`value`: MessageOptions): MessageOptions = value.copy(
        unknownFields = ByteString.EMPTY
      )
    }

    private const val serialVersionUID: Long = 0L
  }
}
