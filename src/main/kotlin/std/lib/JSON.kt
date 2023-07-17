package std.lib

import EnclosedValue
import RuntimeError
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
    val value = arguments.getOrNull(0) ?: return@addNativeMethod OptionValue.None

    if (value.isNone) return@addNativeMethod OptionValue.None

    when (val unwrapped = value.unwrap()) {
      is StringValue -> JSONStd.fromJSON(unwrapped.value)
      else -> throw RuntimeError("Expected 'string' in JSON.parse")
    }
  }
  addNativeMethod("compose") { _, arguments ->
    val value = arguments.getOrNull(0) ?: return@addNativeMethod OptionValue.None

    if (value.isNone) return@addNativeMethod OptionValue.None

    JSONStd.toJSON(value.unwrap()).toVariableValue()
  }
}.toVariableValue()

fun JsonElement.toVariableValue(): VariableValue<*> = when (this) {
  is JsonArray -> ArrayValue(ArrayData(this.map { it.toVariableValue() }.toMutableList()))
  is JsonObject -> ObjectValue(PartSInstance(entries.map {
    EnclosedValue(it.key.toVariableValue(), it.value.toVariableValue(), false)
  }.toMutableList()))

  is JsonPrimitive -> when {
    booleanOrNull != null -> boolean.toVariableValue()
    doubleOrNull != null -> double.toVariableValue()
    floatOrNull != null -> float.toDouble().toVariableValue()
    intOrNull != null -> int.toDouble().toVariableValue()
    else -> content.toVariableValue()
  }

  is JsonNull -> OptionValue.None
}