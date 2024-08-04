package std.lib

import EnclosedValue
import kotlinx.serialization.json.*
import std.*

class JSONStd {
  companion object {
    fun fromJSON(str: String): VariableValue<*> = Json.parseToJsonElement(str).toVariableValue()
    fun toJSON(obj: VariableValue<*>): String = obj.toJSON()
  }
}

val JSON = PartSNativeClass().apply {
  addNativeMethod("parse") { _, arguments ->
    var value = arguments.getOrNull(0) ?: return@addNativeMethod OptionValue.None

    if (value is OptionValue && value.isNone) return@addNativeMethod OptionValue.None

    if (value is OptionValue) {
      value = value.unwrapAll()
    }

    when (value) {
      is StringValue -> ResultValue.Okay(JSONStd.fromJSON(value.value))
      else -> ResultValue.Error("Expected 'string' in JSON.parse".toVariableValue())
    }
  }
  addNativeMethod("compose") { _, arguments ->
    var value = arguments.getOrNull(0) ?: return@addNativeMethod OptionValue.None

    if (value is OptionValue && (value as OptionValue).isNone) return@addNativeMethod OptionValue.None

    if (value is OptionValue) {
      value = (value as OptionValue).unwrapAll()
    }

    JSONStd.toJSON(value).toVariableValue()
  }
}.toVariableValue()

fun JsonElement.toVariableValue(): VariableValue<*> = when (this) {
  is JsonArray -> ArrayValue(ArrayData(this.map { it.toVariableValue() }.toMutableList()))
  is JsonObject -> ObjectValue(PartSInstance(entries.map {
    EnclosedValue(it.key.toVariableValue(), it.value.toVariableValue(), false)
  }.toMutableList()))
  is JsonNull -> OptionValue.None
  is JsonPrimitive -> when {
    booleanOrNull != null -> boolean.toVariableValue()
    doubleOrNull != null -> double.toVariableValue()
    floatOrNull != null -> float.toDouble().toVariableValue()
    intOrNull != null -> int.toDouble().toVariableValue()
    else -> content.toVariableValue()
  }
}