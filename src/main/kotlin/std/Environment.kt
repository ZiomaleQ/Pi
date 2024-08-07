package std

import EnclosedValue
import RuntimeError
import std.lib.JSON
import std.lib.SystemSTD

class Environment(var enclosing: Environment? = null) {
  val values: MutableMap<String, VariableValue<*>> = mutableMapOf()

  fun enclose() = Environment(this)

  fun copy(map: MutableMap<String, VariableValue<*>>) {
    for (key in map.keys) {
      values[key] = map[key] as VariableValue
    }
  }

  fun copy(map: MutableList<EnclosedValue>) {
    for (entry in map) {
      values[entry.key.prettyPrint()] = entry.value
    }
  }

  fun define(name: String, value: VariableValue<*>): VariableValue<*> {
    if (!values.containsKey(name)) values[name] = value
    else throw RuntimeError("Variable '$name' already declared.")
    return value
  }

  private fun internalGet(name: String, unwrap: Boolean = true): VariableValue<*> = OptionValue(
    Option(
      when {
        values.containsKey(name) -> values[name]
        enclosing != null -> enclosing!!.internalGet(name, false)
        else -> null
      }
    )
  ).let { if (unwrap) it.unwrapAll() else it }

  operator fun get(name: String) = OptionValue(Option(internalGet(name)))

  fun assign(name: String, value: VariableValue<*>): Unit = when {
    values.containsKey(name) -> {
      if (values[name]?.const == true) throw RuntimeError("Can't override const value of '$name'")
      else values[name] = value
    }

    enclosing != null -> enclosing!!.assign(name, value)
    else -> throw RuntimeError("Undefined '$name' reference")
  }

  fun dump(): String {
    var out = ""
    for ((key, value) in values) {
      out += "\"$key\": ${value.toJSON()},"
    }
    return "{${out.removeSuffix(",")}}"
  }

  fun toPartsInstance(): PartSInstance =
    PartSInstance(values.entries.map { EnclosedValue(it.key.toVariableValue(), it.value, false) }.toMutableList())

  companion object {

    fun asGlobal(): Environment {
      val env = Environment()

      return env.apply {
        define("Iterable", PartsIterable().toVariableValue())
        define("Option", PartSNativeClass().apply {
          addNativeProp("None", OptionValue.None)
          addNativeMethod("Some") { _, arguments ->
            val value = arguments.getOrNull(0) ?: return@addNativeMethod OptionValue.None

            if(value is OptionValue) {
              val temp = value.unwrapAll()

              if (temp is OptionValue) {
                OptionValue.None
              } else {
                OptionValue.Some(temp)
              }
            } else {
              OptionValue.Some(value)
            }
          }
        }.toVariableValue())

        define("JSON", JSON)
        define("System", SystemSTD)
      }
    }
  }
}