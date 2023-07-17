package std

import BlockNode
import BlockReturn
import EnclosedValue
import Interpreter
import Node
import Parser
import PartSCallable
import RuntimeError
import scanTokens

sealed class VariableValue<T>(var value: T, val const: Boolean = false) {
  open val type = VariableType.Option
  open fun prettyPrint(): String = value.toString()

  override fun equals(other: Any?) = other is VariableValue<*> && type == other.type && value == other.value
  override fun hashCode() = (31 * type.hashCode()) + (value?.hashCode() ?: 0)
  override fun toString() = "(T: $type${if (const) " (const)" else ""}, V: $value)"

  abstract fun toJSON(): String
  abstract fun inverseMutability(): VariableValue<T>
}

class StringValue(value: String, const: Boolean = false) : VariableValue<String>(value, const) {
  constructor(value: Char, const: Boolean = false) : this(value.toString(), const)

  override val type = VariableType.String

  override fun toJSON() = "\"${value.replace("\"", "\\\"")}\""
  override fun inverseMutability() = StringValue(value, !const)

  val std by lazy {
    PartSNativeClass().apply {
      addNativeAccessor(
        "length",
        getter = { _, _ -> value.length.toDouble().toVariableValue() },
        setter = { _, _ -> value.length.toDouble().toVariableValue() }
      )

      addNativeMethod("last") { _, _ ->
        StringValue(value.last())
      }

      addNativeMethod("first") { _, _ ->
        StringValue(value.first())
      }

      addNativeMethod("get") { interpreter, arguments ->
        val num = interpreter.toNumber(arguments.getOrNull(0)).toInt()
        value.getOrNull(num)?.let { OptionValue.Some(StringValue(it)) } ?: OptionValue.None
      }

      addNativeMethod("toBool") { _, _ ->
        value.toBooleanStrict().toVariableValue()
      }
      addNativeMethod("toNumber") { _, _ ->
        value.toDouble().toVariableValue()
      }
      addNativeMethod("toArray") { _, _ ->
        ArrayValue(ArrayData(value.toCharArray().map { it.toString().toVariableValue() }.toMutableList()))
      }
    }
  }

  fun createIterator() = ArrayData(value.map { it.toString().toVariableValue() }.toMutableList()).createIterator()
}

class BooleanValue(value: Boolean, const: Boolean = false) : VariableValue<Boolean>(value, const) {
  override val type = VariableType.Boolean

  override fun toJSON() = if (value) "true" else "false"
  override fun inverseMutability() = BooleanValue(value, !const)
}

class NumberValue(value: Double, const: Boolean = false) : VariableValue<Double>(value, const) {
  override val type = VariableType.Number

  override fun toJSON() = value.toString()
  override fun inverseMutability() = NumberValue(value, !const)
}

class FunctionValue<T : PartSCallable>(value: T, const: Boolean = false) : VariableValue<T>(value, const) {
  override val type = VariableType.Function

  override fun toJSON() = "\"\""
  override fun inverseMutability() = FunctionValue(value, !const)
}

open class FunctionData(private var declaration: FunctionDeclaration) : PartSCallable {

  override fun toString() = "${declaration.name} function"
  override fun call(interpreter: Interpreter, arguments: List<OptionValue>): VariableValue<*> {
    val map = mutableMapOf<String, VariableValue<*>>()

    for (i in declaration.parameters.indices) {
      val param = declaration.parameters[i]
      val variable = arguments.getOrNull(i) ?: continue
      val defaultParam = if (param is DefaultParameter) interpreter.runNode(param.value) else null

      if (variable.isNone) {
        map[declaration.parameters[i].name] = defaultParam ?: OptionValue.None
        continue
      }

      map[declaration.parameters[i].name] = variable.unwrap()
    }

    return interpreter.runBlock(declaration.body, map).let {
      when (it.first) {
        BlockReturn.Return -> OptionValue.Some(it.second.unwrapAll())
        else -> OptionValue.None
      }
    }
  }

  fun toVariableValue() = FunctionValue(this)
}

class OptionValue(value: Option, const: Boolean = false) : VariableValue<Option>(value, const) {
  override val type = VariableType.Option

  override fun toJSON() = value.data?.toJSON() ?: "null"
  override fun inverseMutability() = OptionValue(value, !const)

  override fun prettyPrint(): String {
    return "Option.${if (value.data != null) "Some(${value.data!!.prettyPrint()})" else "None"}"
  }

  override fun toString(): String = "(T: $type${if (const) " (const)" else ""}, V: $value)"

  fun unwrap() = value.data!!
  private fun unwrapOr(value: VariableValue<*>) = this.value.unwrapOr(value)
  private fun expect(err: RuntimeError) = if (isNone) throw err else unwrap()
  fun expect(msg: String): VariableValue<*> = expect(RuntimeError(msg))
  fun unwrapAll(): VariableValue<*> {
    var curr: VariableValue<*> = this
    while (curr is OptionValue && curr.isSome) curr = curr.unwrap()
    return curr
  }

  val isNone: Boolean
    get() = this.value.data == null

  val isSome: Boolean
    get() = this.value.data != null

  val std by lazy {
    PartSNativeClass().apply {
      addNativeProp("isSome", isSome.toVariableValue())
      addNativeProp("isNone", isNone.toVariableValue())

      addNativeMethod("unwrap") { _, _ -> expect("Unwrapped Option.None") }
      addNativeMethod("unwrapOrElse") { _, arguments ->
        unwrapOr(arguments.getOrElse(0) { None }.unwrapAll())
      }
    }
  }

  companion object {
    val None = OptionValue(Option(null))

    @Suppress("FunctionName")
    fun Some(value: VariableValue<*>) = OptionValue(Option(value))
  }
}

data class Option(val data: VariableValue<*>?) {
  fun unwrap() = data!!
  fun unwrapOr(value: VariableValue<*>) = data ?: value

  override fun toString(): String = if (data?.value == null) "null" else data.value.toString()
}

class ArrayValue(value: ArrayData, const: Boolean = false) : VariableValue<ArrayData>(value, const) {
  override val type = VariableType.Array

  override fun toJSON() = "[${(value).toList().joinToString(separator = ",") { it.toJSON() }}]"
  override fun inverseMutability() = ArrayValue(value, !const)
}

class ArrayData(elements: MutableList<VariableValue<*>>) {
  private val valueList = elements.withIndex().toMutableList()

  fun toList(): List<VariableValue<*>> {
    valueList.sortBy { it.index }
    return valueList.map { it.value }.toList()
  }

  operator fun get(index: Int): VariableValue<*> {
    val foundIndex = valueList.find { it.index == index }
    return OptionValue(Option(foundIndex?.value))
  }

  fun createIterator(): PartSInstance {
    val hasNext = FunctionData(
      FunctionDeclaration(
        "hasNext", listOf(), Parser(scanTokens("{ return (0 < top) && (index < top);}")).parse()[0] as BlockNode
      )
    )
    val next = FunctionData(
      FunctionDeclaration(
        "next",
        listOf(),
        Parser(scanTokens("{ index += 1; return data.get(index - 1) }")).parse()[0] as BlockNode
      )
    )

    val list = toList()

    return PartSInstance(
      mutableMapOf(
        "data" to list.toVariableValue(),
        "top" to list.size.toDouble().toVariableValue(),
        "current" to list[0],
        "index" to 0.0.toVariableValue(),
        "hasNext" to hasNext.toVariableValue(),
        "next" to next.toVariableValue()
      ).entries.map { EnclosedValue(it.key.toVariableValue(), it.value, false) }.toMutableList()
    )
  }

  operator fun set(index: Int, value: VariableValue<*>) = internalSet(index, value)

  private fun internalSet(index: Int, value: VariableValue<*>) {
    val largestIn = valueList.maxOfOrNull { it.index } ?: 0

    if (index < largestIn) {
      valueList.removeIf { it.index == index }
      valueList.add(IndexedValue(index, value))
    } else {
      val diff = index - largestIn
      if (diff == 0) valueList.add(IndexedValue(index, value))
      else {
        valueList.add(IndexedValue(index, value))
      }
    }
  }

  val std by lazy {
    PartSNativeClass().apply {
      addNativeMethod("get") { interpreter, arguments ->
        val num = interpreter.toNumber(arguments.getOrNull(0))

        val foundIndex = valueList.find { it.index == num.toInt() }
        OptionValue(Option(foundIndex?.value))
      }

      addNativeMethod("getOrDefault") { interpreter, arguments ->
        val num = interpreter.toNumber(arguments.getOrNull(0))

        val foundIndex = valueList.find { it.index == num.toInt() }
        OptionValue(Option(foundIndex?.value ?: arguments.getOrNull(1)))
      }

      addNativeMethod("set") { interpreter, arguments ->
        val index = interpreter.toNumber(arguments.getOrNull(0))
        val value = arguments.getOrNull(1) ?: throw RuntimeError("Can't set void, use 'Option.None' instead")

        internalSet(index.toInt(), value)

        value
      }

      addNativeMethod("merge") { _, arguments ->
        val child = arguments.getOrNull(0) ?: return@addNativeMethod ArrayValue(this@ArrayData)

        child.unwrap().let {
          if (it is ArrayValue) {
            ArrayValue(
              ArrayData(
                (valueList.map { elt -> elt.value } + it.value.valueList.map { elt -> elt.value }).toMutableList()
              )
            )
          } else {
            set(valueList.maxOf { elt -> elt.index }, child)
            ArrayValue(this@ArrayData)
          }
        }
      }


      addNativeAccessor(
        "length",
        getter = { _, _ -> toList().size.toDouble().toVariableValue() },
        setter = { _, _ -> toList().size.toDouble().toVariableValue() }
      )
    }
  }

  override fun toString(): String {
    return "[${valueList.joinToString(", ") { elt -> (elt.value).let { if (it is ArrayValue) it.toString() else it.value.toString() } }}]"
  }
}

class ClassValue<T : PartSClass>(
  value: T, override val type: VariableType = VariableType.Class, const: Boolean = false
) : VariableValue<T>(value, const) {
  override fun inverseMutability() = ClassValue(value, type, !const)
  override fun toJSON() = Environment().apply { copy(value.entries) }.dump()
}

open class PartSClass(
  val name: String,
  private val stubs: MutableList<String> = mutableListOf(),
  elements: MutableList<EnclosedValue> = mutableListOf()
) : PartSInstance(elements), PartSCallable {

  init {
    checkImplemented()
    elements["this"] = this.toVariableValue()
    if ("#init" !in elements) elements["#init"] = false.toVariableValue()
  }

  var superclass: PartSClass? = null
    set(value) {
      if (value != null) elements["super"] = value.toVariableValue()
      if ("super" in elements && value == null) elements.removeIf { it.key.prettyPrint() == "super" }
      checkImplemented()
      field = value
    }

  private fun checkImplemented(raise: Boolean = true): Boolean {
    val localStubs = mutableListOf<String>()
    if (superclass?.stubs?.isNotEmpty() == true) localStubs.addAll(superclass!!.stubs)

    val mapped = localStubs.map { Pair(it, elements[it]) }
    val notImplemented = mutableListOf<String>()

    for (mappedStub in mapped) {
      if (mappedStub.second == null || mappedStub.second!!.type != VariableType.Function) {
        notImplemented.add(mappedStub.first)
        continue
      }
    }

    if (notImplemented.size > 0) {
      if (raise) throw RuntimeError("Not implemented methods in '$name' class are: '${notImplemented.joinToString()}'")
      else return false
    }

    return true
  }

  override fun call(interpreter: Interpreter, arguments: List<OptionValue>): VariableValue<*> {
    if (elements["#init"]!!.type == VariableType.Boolean && elements["#init"]!!.value == true) {
      throw RuntimeError("Can't call already initialised class")
    }

    if ("init" in elements) {
      val value = elements["init"] ?: return OptionValue.None
      when (value.type) {
        VariableType.Function -> {
          interpreter.environment = interpreter.environment.enclose()
          interpreter.environment.copy(elements)
          (value.value as FunctionData).call(interpreter, arguments)
          interpreter.environment = interpreter.environment.enclosing!!
        }

        else -> Unit
      }
    }

    elements["#init"] = true.toVariableValue()

    return toVariableValue()
  }

  override fun toVariableValue() = ClassValue(this)

  override fun toString(): String = "{\n  Class name: $name with data:\n ${super.toString()}\n}"
}

operator fun MutableList<EnclosedValue>.get(key: String) = this.find { key == it.key.prettyPrint() }?.value

operator fun MutableList<EnclosedValue>.set(key: String, value: VariableValue<*>) {
  val temp = EnclosedValue(key.toVariableValue(), value, false)
  if (this[key] == null) {
    this.add(temp)
  } else {
    this[this.indexOfFirst { it.key.prettyPrint() == key }] = temp
  }
}

operator fun MutableList<EnclosedValue>.contains(key: String) = this.find { key == it.key.prettyPrint() } != null

open class PartsIterable : PartSClass("Iterable", mutableListOf("hasNext", "next")) {
  override fun toVariableValue(): ClassValue<PartSClass> = ClassValue(this, VariableType.Iterable)
}

open class PartsAccessor : PartSClass("PropertyAccessor") {
  override fun toVariableValue(): ClassValue<PartSClass> = ClassValue(this, VariableType.PropertyAccessor)
}

class ObjectValue<T : PartSInstance>(value: T, const: Boolean = false) : VariableValue<T>(value, const) {
  override val type: VariableType = VariableType.Object
  override fun inverseMutability() = ObjectValue(value, !const)

  override fun toJSON() = Environment().apply { copy(value.entries) }.dump()
}

open class PartSInstance(protected val elements: MutableList<EnclosedValue>) {

  val size: Int
    get() = elements.size

  fun isEmpty() = elements.isEmpty()
  fun isNotEmpty() = elements.isNotEmpty()

  operator fun get(key: String) = when (val value = elements[key] ?: OptionValue.None) {
    is OptionValue -> if (value.isNone) OptionValue.None else OptionValue.Some(value.unwrapAll())
    else -> OptionValue.Some(value)
  }

  operator fun set(key: String, value: VariableValue<*>) {
    val temp = EnclosedValue(key.toVariableValue(), value, false)

    if (elements[key] == null) {
      elements.add(temp)
    } else {
      elements[elements.indexOfFirst { it.key.prettyPrint() == key }] = temp
    }
  }

  operator fun contains(key: String) = elements.find { key == it.key.prettyPrint() } != null

  val entries
    get() = this.elements

  override fun toString(): String {
    if (elements.isEmpty()) return "#> Empty <#"

    val length = elements.map { it.key.prettyPrint() }.maxByOrNull { it.length }!!.length.coerceAtLeast("key ".length)
    var string = "Key${" ".repeat(length - "key".length)} - Property\n"
    for (entry in elements) {
      val key = entry.key.prettyPrint()
      val value = entry.value.value
      string += if (entry.key.type == VariableType.Class) {
        "${key}${" ".repeat(length - key.length)} | #> instance of class ${(value as PartSClass).name} superclass ${value.superclass?.name ?: "none"}<#\n"
      } else "$key${" ".repeat(length - key.length)} | ${entry.value.prettyPrint()}\n"
    }
    return string
  }

  fun runWithContext(
    function: FunctionData, interpreter: Interpreter, arguments: List<OptionValue>
  ): VariableValue<*> {
    interpreter.environment = interpreter.environment.enclose()
    interpreter.environment.copy(elements)

    val returnValue = function.call(interpreter, arguments)
    val curr = interpreter.environment.values

    elements.mapIndexed { ind, it ->
      elements[ind] = EnclosedValue(
        it.key.prettyPrint().toVariableValue(),
        it.key.prettyPrint().let { key -> if (it.value != curr[key]) curr[key] ?: it.value else it.value },
        it.static
      )
    }

    interpreter.environment = interpreter.environment.enclosing!!
    return returnValue
  }

  open fun toVariableValue(): VariableValue<*> = ObjectValue(this)
}

class PartSNativeClass : PartSInstance(mutableListOf()) {
  fun addNativeMethod(name: String, method: (Interpreter, List<OptionValue>) -> VariableValue<*>) {
    elements[name] = FunctionValue(object : PartSCallable {
      override fun call(
        interpreter: Interpreter, arguments: List<OptionValue>
      ): VariableValue<*> {
        return method.invoke(interpreter, arguments)
      }

      override fun toString(): String = "<native fn $name>"
    })
  }

  fun addNativeAccessor(
    name: String,
    defaultValue: VariableValue<*> = OptionValue.None,
    getter: (Interpreter, List<OptionValue>) -> VariableValue<*>,
    setter: (Interpreter, List<OptionValue>) -> VariableValue<*>
  ) {
    val memberClass = PartSClass(
      name + "Wrapper",
      elements = mutableMapOf(
        "#default" to defaultValue,
        "getter" to FunctionValue(
          object : PartSCallable {
            override fun call(
              interpreter: Interpreter, arguments: List<OptionValue>
            ): VariableValue<*> {
              return getter.invoke(interpreter, arguments)
            }
          }),
        "setter" to FunctionValue(object : PartSCallable {
          override fun call(
            interpreter: Interpreter, arguments: List<OptionValue>
          ): VariableValue<*> {
            return setter.invoke(interpreter, arguments)
          }
        })
      ).entries.map { EnclosedValue(it.key.toVariableValue(), it.value, false) }.toMutableList()
    )

    memberClass.superclass = PartsAccessor()

    elements[name] = memberClass.toVariableValue()
  }

  fun addNativeProp(name: String, value: VariableValue<*>) {
    elements[name] = value
  }
}

fun List<VariableValue<*>>.toVariableValue() = ArrayValue(ArrayData(this.toMutableList()))
fun Double.toVariableValue() = NumberValue(this)
fun String.toVariableValue() = StringValue(this)
fun Boolean.toVariableValue() = BooleanValue(this)

data class FunctionDeclaration(var name: String, var parameters: List<FunctionParameter>, var body: BlockNode)
open class FunctionParameter(val name: String)
class DefaultParameter(name: String, val value: Node) : FunctionParameter(name)

enum class VariableType { String, Boolean, Number, Function, Object, Class, Iterable, Array, PropertyAccessor, Option }