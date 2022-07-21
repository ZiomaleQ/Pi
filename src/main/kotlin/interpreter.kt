import java.lang.System.*

class Interpreter {
  var environment = Environment.asGlobal().enclose()
  private var code = mutableListOf<Node>()

  fun run(code: List<Node>, timeTaken: Boolean = true) {
    this.code.addAll(code)
    val time = currentTimeMillis().toDouble()
    while (this.code.size != 0) runNode(advance())
    if (timeTaken) println("Successfully run code in ${(currentTimeMillis().toDouble() - time) / 1000.0}s")
  }

  fun runNode(node: Node) = when (node) {
    is LetNode -> runLet(node)
    is ConstNode -> runConst(node)
    is FunctionNode -> runFunction(node)
    is IfNode -> runIf(node)
    is BlockNode -> runBlock(node)
    is BinaryNode -> runBinary(node)
    is CallNode -> runCall(node)
    is AssignNode -> runAssign(node)
    is VariableNode -> environment[node.name]
    is LiteralNode -> VariableValue(node.type, node.value)
    is UnaryNode -> {
      if (node.op == "MINUS") -toNumber(node.expr)
      else !toBoolean(node.expr)
    }
    is ObjectNode -> runObject(node)
    is DotNode -> runDot(node)
    is ForNode -> runFor(node)
    is RangeNode -> runRange(node)
    is ClassNode -> runClass(node)
    is ArrayNode -> runArray(node)
    else -> {
      throw Error("Unexpected node type '${node}'")
    }
  }

  private fun runLet(node: LetNode): VariableValue {
    var value = VariableValue.void
    if (node.value == null) environment.define(node.name, value)
    else {
      val tempNode = node.value as Node
      value = when (tempNode) {
        is LiteralNode -> VariableValue(tempNode.type, tempNode.value)
        is FunctionNode -> VariableValue("Function", runFunction(tempNode, declare = false))
        is ObjectNode -> VariableValue("Object", runObject(tempNode).value)
        is ArrayNode -> runArray(tempNode)
        is RangeNode -> runRange(tempNode)
        is CallNode -> runCall(tempNode)
        else -> value
      }

      environment.define(node.name, value)
    }
    return value
  }

  private fun runConst(node: ConstNode): ConstValue {
    var value = ConstValue("Void", null)
    if (node.value == null) environment.define(node.name, value)
    else {
      val tempNode = node.value as Node
      value = when (tempNode) {
        is LiteralNode -> ConstValue(tempNode.type, tempNode.value)
        is FunctionNode -> ConstValue("Function", runFunction(tempNode, declare = false))
        is ObjectNode -> ConstValue("Object", runObject(tempNode).value)
        is ArrayNode -> runArray(tempNode).toConst()
        is RangeNode -> runRange(tempNode).toConst()
        is CallNode -> runCall(tempNode).toConst()
        else -> value
      }
      environment.define(node.name, value)
    }
    return value
  }

  private fun runFunction(
    node: FunctionNode, declare: Boolean = true
  ): VariableValue = FunctionValue(
    FunctionDeclaration(node.name, node.parameters, node.body)
  ).let {
    val varFunction = VariableValue("Function", it)
    if (declare && node.name != "\$Anonymous\$") environment.define(node.name, varFunction) else varFunction
  }

  private fun runIf(node: IfNode) = when {
    toBoolean(node.condition) -> runEnclosed(node.thenBranch)
    node.elseBranch != null -> runEnclosed(node.elseBranch as Node)
    else -> null
  }

  fun runBlock(node: BlockNode, map: MutableMap<String, VariableValue>? = null): Pair<BlockReturn, VariableValue> {
    environment = environment.enclose()
    if (map != null) for (x in map.keys) environment.define(x, map[x]!!)

    var breakReason = BlockReturn.End
    var returnValue = VariableValue.void

    for (childNode in node.body) {
      when (childNode) {
        is ReturnNode -> {
          breakReason = BlockReturn.Return
          returnValue = runNode(childNode.expr) as VariableValue
          break
        }
        is BreakNode, is ContinueNode -> {
          breakReason = if (childNode is BreakNode) BlockReturn.Break else BlockReturn.Continue
          break
        }
        is BlockNode -> {
          val blockResult = runBlock(childNode)
          if (blockResult.first != BlockReturn.End) {
            breakReason = blockResult.first
            returnValue = blockResult.second
            break
          }
        }
        else -> runNode(childNode)
      }
    }

    environment = environment.enclosing!!
    return Pair(breakReason, returnValue)
  }

  private fun runBinary(node: BinaryNode): VariableValue = when (node.op) {
    "OR", "AND" -> {
      VariableValue("Boolean", toBoolean(node.left).let {
        if (it && node.op == "AND") toBoolean(node.right)
        else it || toBoolean(node.right)
      })
    }
    "MINUS", "STAR", "SLASH", "PLUS" -> {
      val left = toNumber(node.left)
      val right = toNumber(node.right)
      if (right == 0.0 && node.op == "SLASH") throw RuntimeError("Do not divide by zero (0).")
      VariableValue(
        "Number", when (node.op) {
          "MINUS" -> left - right
          "PLUS" -> left + right
          "SLASH" -> left / right
          "STAR" -> left * right
          else -> null
        }
      )
    }
    "GREATER", "GREATER_EQUAL", "LESS", "LESS_EQUAL" -> {
      val left = toNumber(node.left)
      val right = toNumber(node.right)
      VariableValue(
        "Boolean", when (node.op) {
          "GREATER" -> left > right
          "GREATER_EQUAL" -> left >= right
          "LESS" -> {
            left < right
          }
          "LESS_EQUAL" -> left <= right
          else -> null
        }
      )
    }
    "EQUAL_EQUAL" -> VariableValue("Boolean", runNode(node.left) == runNode(node.right))
    "BANG_EQUAL" -> VariableValue("Boolean", runNode(node.left) != runNode(node.right))
    else -> VariableValue.void
  }

  @Suppress("UNCHECKED_CAST")
  private fun runCall(node: CallNode): VariableValue {
    val ref = environment[node.name] ?: throw RuntimeError("Undefined '${node.name}' reference")
    return when {
      ref is VariableValue && (ref.type == "Function" || ref.type == "Class") -> (ref.value as PartSCallable).call(this,
        node.args.map { runNode(it) as? VariableValue })
      else -> throw RuntimeError("Variable '${node.name}' is not a function")
    }
  }

  private fun runAssign(node: AssignNode): VariableValue {
    var value = VariableValue.void

    if (node.value == null) return value
    value = when (node.value) {
      is LiteralNode -> VariableValue((node.value as LiteralNode).type, (node.value as LiteralNode).value)
      is FunctionNode -> VariableValue("Function", runFunction(node.value as FunctionNode, declare = false))
      is ObjectNode -> VariableValue("Object", runObject(node.value as ObjectNode).value)
      else -> runNode(node.value as Node) as VariableValue
    }
    environment.assign(node.name, value)
    return value
  }

  private fun runObject(node: ObjectNode): VariableValue {
    val mappedMap = mutableMapOf<String, VariableValue>()

    for (key in node.map.keys) mappedMap[key] = runNode(node.map[key]!!) as VariableValue

    return VariableValue("Object", PartSInstance(mappedMap))
  }

  private fun runDot(node: DotNode): VariableValue {
    val accessFrom = runNode(node.accessFrom)

    if (node.accessTo is AssignNode) {
      val nodeAT = node.accessTo as AssignNode
      if (accessFrom is PartSInstance) accessFrom.map[nodeAT.name] = runNode(nodeAT.value!!) as VariableValue
    }

    val objectKey = when (node.accessTo) {
      is VariableNode -> (node.accessTo as VariableNode).name
      is AssignNode -> when (accessFrom) {
        is PartSInstance -> {
          val nodeAT = (node.accessTo as AssignNode)
          accessFrom.map[nodeAT.name] = runNode(nodeAT.value!!) as VariableValue
          //Object assign basically = 'obj.x = 0'
          return VariableValue.void
        }
        is VariableValue -> {
          if (accessFrom.type == "Object") {
            val getFrom = accessFrom.value as PartSInstance
            val nodeAT = (node.accessTo as AssignNode)
            getFrom.map[nodeAT.name] = runNode(nodeAT.value!!) as VariableValue
            //Object assign basically = 'obj.x = 0'
            return VariableValue.void
          } else throw RuntimeError("Invalid assignment target")
        }
        else -> throw RuntimeError("This shit shouldn't happen")
      }
      is CallNode -> (node.accessTo as CallNode).name
      else -> when (val accessTo = runNode(node.accessTo)) {
        is VariableValue -> when (accessTo.type) {
          "Number", "String", "Boolean" -> accessTo.value.toString()
          else -> throw RuntimeError("Invalid access key")
        }
        else -> throw RuntimeError("Invalid access key")
      }
    }

    return when (accessFrom) {
      is VariableValue -> when (accessFrom.type) {
        "Object" -> {
          val value = (accessFrom.value as PartSInstance).map[objectKey] ?: VariableValue.void
          if (value.type == "Void") return value
          if (node.accessTo is CallNode) {
            return when (value.type) {
              "Function" -> {
                environment = environment.enclose()
                environment.copy((accessFrom.value as PartSInstance).map)
                val returnValue = (value.value as PartSCallable).call(
                  this,
                  (node.accessTo as CallNode).args.map { runNode(it) as? VariableValue })
                environment = environment.enclosing!!
                returnValue
              }
              else -> throw RuntimeError("Member '${objectKey}' is not a function")
            }
          }
          return value
        }
        "Class" -> {
          val value = (accessFrom.value as PartSInstance).map[objectKey] ?: VariableValue.void
          if (value.type == "Void") return value
          if (node.accessTo is CallNode) {
            return when (value.type) {
              "Function" -> {
                environment = environment.enclose()
                environment.copy((accessFrom.value as PartSInstance).map)
                val returnValue = (value.value as PartSCallable).call(
                  this,
                  (node.accessTo as CallNode).args.map { runNode(it) as? VariableValue })
                environment = environment.enclosing!!
                returnValue
              }
              else -> throw RuntimeError("Member '${objectKey}' is not a function")
            }
          }
          return value
        }
        "Array" -> {
          val value = ((accessFrom as ArrayValue).std as PartSInstance).map[objectKey] ?: VariableValue.void
          if (value.type == "Void") return value
          if (node.accessTo is CallNode) {
            return when (value.type) {
              "Function" -> {
                environment = environment.enclose()
                val returnValue = (value.value as PartSCallable).call(
                  this,
                  (node.accessTo as CallNode).args.map { runNode(it) as? VariableValue })
                environment = environment.enclosing!!
                returnValue
              }
              else -> throw RuntimeError("Member '${objectKey}' is not a function")
            }
          }
          return value
        }
        else -> {
          throw RuntimeError("STD methods for type: '${accessFrom.type}' are not implemented")
        }
      }
      is PartSInstance -> {
        val value = accessFrom.map[objectKey] ?: VariableValue.void
        if (value.type == "Void") return value
        if (node.accessTo is CallNode) {
          return when (value.type) {
            "Function" -> {
              environment = environment.enclose()
              environment.copy(accessFrom.map)
              val returnValue = (value.value as PartSCallable).call(
                this,
                (node.accessTo as CallNode).args.map { runNode(it) as? VariableValue })
              environment = environment.enclosing!!
              returnValue
            }
            else -> throw RuntimeError("Member '${objectKey}' is not a function")
          }
        }
        return value
      }
      else -> VariableValue.void
    }
  }

  private fun runFor(node: ForNode) {
    var condition = runNode(node.range)

    if (condition !is PartSInstance) {
      if (condition is VariableValue && listOf(
          "Class", "Object", "Iterable", "Array"
        ).contains(condition.type)
      ) condition = if (condition.type == "Array") (condition as ArrayValue).createIterator()
      else condition.value as PartSInstance
      else {
        throw RuntimeError("Only objects and classes can be for loop condition")
      }
    }

    val hasNextRaw = condition.map["hasNext"] ?: throw RuntimeError("There is no 'hasNext' function on the object")
    if (hasNextRaw.type != "Function") throw RuntimeError("'hasNext' is not a function")
    val hasNext = hasNextRaw.value as FunctionValue

    val nextRaw = condition.map["next"] ?: throw RuntimeError("There is no 'next' function on the object")
    if (nextRaw.type != "Function") throw RuntimeError("'next' is not a function")
    val next = nextRaw.value as FunctionValue
    val codeBlock = node.body as BlockNode

    var `continue` = toBoolean(condition.runWithContext(hasNext, interpreter, emptyList()))

    while (`continue`) {
      val current = condition.runWithContext(next, interpreter, emptyList())
      condition.map["current"] = current

      val blockResult = runBlock(
        codeBlock, mutableMapOf("it" to current)
      )

      when (blockResult.first) {
        BlockReturn.Continue -> continue
        BlockReturn.Return, BlockReturn.Break -> break
        else -> Unit
      }

      `continue` = toBoolean(condition.runWithContext(hasNext, interpreter, emptyList()))
    }
  }

  private fun runRange(node: RangeNode): VariableValue {
    val bottom = toNumber(node.bottom) - 1
    val top = toNumber(node.top)

    val hasNext = FunctionValue(
      FunctionDeclaration(
        "hasNext", listOf(), Parser(scanTokens("{ return (bottom < top) && (current < top);}")).parse()[0] as BlockNode
      )
    )
    val next = FunctionValue(
      FunctionDeclaration(
        "next", listOf(), Parser(scanTokens("{ return current + 1; }")).parse()[0] as BlockNode
      )
    )

    val claz = PartSClass(
      "Range", mutableListOf(), mutableMapOf(
        "bottom" to bottom.toVariableValue(),
        "top" to top.toVariableValue(),
        "current" to bottom.toVariableValue(),
        "hasNext" to hasNext.toVariableValue(),
        "next" to next.toVariableValue()
      )
    )
    claz.superclass = PartsIterable()

    return claz.toVariableValue()
  }

  private fun runClass(node: ClassNode) {
    environment = environment.enclose()

    var superclass: VariableValue? = null

    if (node.superclass != null) {
      superclass = environment[node.superclass!!] as? VariableValue
      if (superclass == null || superclass.value !is PartSClass) throw RuntimeError("Super class '${node.superclass}'isn't valid class to inherit")
    }

    for (member in node.parameters) runNode(member)
    for (func in node.functions) runFunction(func, declare = true)

    val classBody = environment.values
    environment = environment.enclosing!!

    val clazz = PartSClass(node.name, node.stubs.map { it.name }.toMutableList(), classBody)
    if (superclass != null) clazz.superclass = superclass.value as PartSClass

    environment.define(node.name, clazz.toVariableValue())
  }

  private fun runEnclosed(node: Node): VariableValue {
    environment = environment.enclose()
    val temp = runNode(node)
    environment = environment.enclosing!!
    return temp as VariableValue
  }

  private fun runArray(node: ArrayNode): ArrayValue {
    return node.data.mapNotNull { this.runNode(it) as VariableValue? }.toVariableValue()
  }

  private fun toBoolean(node: Node): Boolean = runNode(node)?.let {
    toBoolean(it)
  } ?: false

  private fun toBoolean(thing: Any): Boolean = when (thing) {
    is VariableValue -> when (thing.type) {
      "Number" -> thing.value != 0
      "String" -> (thing.value as String).isNotEmpty()
      "Boolean" -> "${thing.value}" == "true"
      "Function" -> true
      "Array" -> (thing.value as ArrayValue).toList().isNotEmpty()
      "Object" -> (thing.value as PartSInstance).map.isNotEmpty()
      else -> false
    }
    is FunctionValue -> true
    is PartSInstance -> thing.map.isNotEmpty()
    else -> false
  }

  private fun toNumber(node: Node): Double = runNode(node)?.let {
    toNumber(it)
  } ?: 0.0

  fun toNumber(thing: Any?): Double = when (thing) {
    is VariableValue -> when (thing.type) {
      "Number" -> thing.value.let { if (it is Double) it else (it as String).toDouble() }
      "String" -> try {
        "${thing.value}".toDouble()
      } catch (e: Exception) {
        0.0
      }
      "Boolean" -> if ("${thing.value}" == "true") 1.0 else 0.0
      "Function" -> 1.0
      "Array" -> (thing as ArrayValue).toList().size.toDouble()
      "Object" -> (thing.value as PartSInstance).map.size.toDouble()
      else -> 0.0
    }
    is FunctionValue -> 1.0
    is PartSInstance -> thing.map.size.toDouble()
    else -> 0.0
  }

  private fun advance() = code.removeFirst()

  fun runTests() {
    val tests = listOf(
      """let x = 0;""",
      """"I like wolfs";""",
      """if (true) print("Hack"); else print("Bruh");""",
      """fun fib(n) { if (n <= 1) return 1; else return fib(n - 1) + fib(n - 2); } print(fib(5));""",
      """fun fight(should = false) { if(should) print("We fight boiz"); else print("We don't fight boiz");}""",
      """let obj = #> x to 0 <#; print(obj.x);""",
      """let obj = #> x to 0 <#; obj.x = 1; print(obj.x);""",
      """let obj = #> x to 0 <#; obj.x = 1; print(obj);""",
      """let range = 1 to 3; for(range) { print(it); }""",
      """class claz {fun func() {print("this is called inside class");}} let clas = claz(); clas.func();""",
      """class claz {fun init() {print("this is called on init");}} claz();""",
      """class claz {let x = 0;} print(claz.x);""",
      """class claz {fun func() {print("this is called inside class");}} let clas = claz(); clas.func();""",
      """class claz {let x = 0;fun init() {print(x);}} claz();""",
      """class claz {fun init() {print("hi");}} class clazZ: claz{fun init() {super.init();}} clazZ();""",
      """class claz {implement next;} class clazz: claz{}""",
      """class ci: Iterable { let top = 10; let bottom = 0; let current = 0;
                fun hasNext() { return (bottom < top) && (current < top); }
                fun next() { return current + 1; }}
                for(ci()) print(it);""",
    )

    for (test in tests) {
      println("\nExecuting now: $test\n")
      //Clearing variables and stuff
      environment = Environment.asGlobal()
      code = mutableListOf()
      // Parse code
      val tokens = scanTokens(test)
      val parsed = Parser(tokens).parse()
      for (parsedNode in parsed) println(parsedNode)

      try {
        this.run(parsed, false)
      } catch (e: Exception) {
        println("ERROR: ${e.message}")
      }
    }
  }
}

fun Interpreter.run(code: String, timeTaken: Boolean = true) {
  val tokens = scanTokens(code)
  val parsed = Parser(tokens).parse()

  this.run(parsed, timeTaken)
}

fun scan(code: String) {
  val tokens = scanTokens(code)

  println("Tokens:")

  tokens.forEach(::println)

  val parsed = Parser(tokens).parse()

  println("Parsed AST:")

  parsed.forEach(::println)
}

class Environment(var enclosing: Environment? = null) {
  val values: MutableMap<String, VariableValue> = mutableMapOf()

  fun enclose() = Environment(this)

  fun copy(map: MutableMap<String, VariableValue>) {
    for (key in map.keys) {
      values[key] = map[key] as VariableValue
    }
  }

  fun define(name: String, value: VariableValue): VariableValue {
    if (!values.containsKey(name)) values[name] = value
    else throw RuntimeError("Variable '$name' already declared.")
    return value
  }

  operator fun get(name: String): Any? = when {
    values.containsKey(name) -> values[name]
    enclosing != null -> enclosing!![name]
    else -> null
  }

  fun assign(name: String, value: VariableValue): Unit = when {
    values.containsKey(name) -> {
      if (values[name] is ConstValue) throw RuntimeError("Can't override const value of '$name'")
      else values[name] = value
    }
    enclosing != null -> enclosing!!.assign(name, value)
    else -> throw RuntimeError("Undefined '$name' reference")
  }

  private fun addNative(name: String, func: (Interpreter, List<VariableValue?>) -> VariableValue) {
    define(name, VariableValue("Function", (object : PartSCallable {
      override fun call(
        interpreter: Interpreter, arguments: List<VariableValue?>
      ): VariableValue {
        return func.invoke(interpreter, arguments)
      }

      override fun toString(): String = "<native fn $name>"
    })))
  }

  companion object {

    fun asGlobal(): Environment {
      val env = Environment()

      return env.apply {
        addNative("print") { _, arguments ->
          val args = arguments.mapNotNull {
            if (it == null) return@mapNotNull null
            it.prettyPrint()
          }
          for (arg in args) {
            println(arg)
          }
          VariableValue.void
        }

        addNative("time") { _, _ ->
          VariableValue("Number", (currentTimeMillis() / 1000L).toDouble())
        }

        define("Iterable", PartsIterable().toVariableValue())
      }
    }
  }
}

open class VariableValue(var type: String, var value: Any?) {

  override fun equals(other: Any?): Boolean {
    return if (other !is VariableValue) false
    else type == other.type && value == other.value
  }

  override fun hashCode(): Int {
    var result = type.hashCode()
    result = 31 * result + (value?.hashCode() ?: 0)
    return result
  }

  override fun toString() = "(T: $type, V: $value)"
  fun prettyPrint() = if (value == null && type != "Void") toString()
  else value.toString()

  fun toConst() = ConstValue(type, value)

  companion object {
    var void = VariableValue("Void", null)
  }
}

class ConstValue(type: String, value: Any?) : VariableValue(type, value) {
  override fun toString() = "(T: $type (const), V: $value)"
}

open class FunctionValue(private var declaration: FunctionDeclaration) : PartSCallable {
  override fun toString() = "${declaration.name} function"
  override fun call(interpreter: Interpreter, arguments: List<VariableValue?>): VariableValue {
    val map = mutableMapOf<String, VariableValue>()

    for (i in declaration.parameters.indices) {
      val variableValue = arguments.getOrNull(i)
        ?: if (declaration.parameters[i] is DefaultParameter) interpreter.runNode((declaration.parameters[i] as DefaultParameter).value) as? VariableValue
        else VariableValue.void
      map[declaration.parameters[i].name] = variableValue ?: VariableValue.void
    }

    return interpreter.runBlock(declaration.body, map).let {
      when (it.first) {
        BlockReturn.Return -> it.second
        else -> VariableValue.void
      }
    }
  }

  fun toVariableValue() = VariableValue("Function", this)
}

data class FunctionDeclaration(var name: String, var parameters: List<FunctionParameter>, var body: BlockNode)

open class FunctionParameter(val name: String)
class DefaultParameter(name: String, val value: Node) : FunctionParameter(name)

class ArrayValue(elts: MutableList<VariableValue>) : VariableValue("Array", null) {
  private val valueList = elts.withIndex().toMutableList()

  fun toList(): List<VariableValue> {
    valueList.sortBy { it.index }
    return valueList.map { it.value }.toList()
  }

  operator fun get(index: Int): VariableValue {
    val foundIndex = valueList.find { it.index == index }
    return foundIndex?.value ?: void
  }

  fun createIterator(): PartSInstance {

    val hasNext = FunctionValue(
      FunctionDeclaration(
        "hasNext", listOf(), Parser(scanTokens("{ return (0 < top) && (index < top);}")).parse()[0] as BlockNode
      )
    )
    val next = FunctionValue(
      FunctionDeclaration(
        "next",
        listOf(),
        Parser(scanTokens("{ index = index + 1; return data.get(index - 1) }")).parse()[0] as BlockNode
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
      )
    )

  }

  operator fun set(index: Int, value: VariableValue) {
    val largestIn = valueList.maxOf { it.index }

    if (index < largestIn) {
      valueList.removeIf { it.index == index }
      valueList.add(IndexedValue(index, value))
    } else {
      val diff = index - largestIn
      if (diff == 0) valueList.add(IndexedValue(index, value))
      else {
        (index..diff).forEach { valueList.add(IndexedValue(it, value)) }
        valueList.add(IndexedValue(index, value))
      }
    }
  }

  val std = PartSNativeClass().apply {
    addNativeMethod("get") { interpreter, arguments ->
      val num = arguments.getOrNull(0).let {
        interpreter.toNumber(it)
      }

      val foundIndex = valueList.find { it.index == num.toInt() }
      foundIndex?.value ?: void
    }

    addNativeMethod("set") { _, arguments ->
      val largestIn = valueList.maxOfOrNull { it.index } ?: 0

      val index = arguments.getOrNull(0).let {
        interpreter.toNumber(it)
      }.toInt()
      val value = arguments.getOrNull(1) ?: throw RuntimeError("Can't set void, use 'nil' instead")

      if (index < largestIn) {
        valueList.removeIf { it.index == index }
        valueList.add(IndexedValue(index, value))
      } else {
        val diff = index - largestIn
        if (diff == 0) valueList.add(IndexedValue(index, value))
        else {
          (index..diff).forEach { valueList.add(IndexedValue(it, value)) }
          valueList.add(IndexedValue(index, value))
        }
      }

      value
    }
  }

  override fun toString() = "[${valueList.joinToString(", ") { (it.value).value.toString() }}]"
}

interface PartSCallable {
  fun call(interpreter: Interpreter, arguments: List<VariableValue?>): VariableValue
}

open class PartSInstance(val map: MutableMap<String, VariableValue>) {
  override fun toString(): String {
    if (map.isEmpty()) return "#> Empty #<"
    val length = map.keys.maxByOrNull { it.length }!!.length.coerceAtLeast("key ".length)
    var string = "Key${" ".repeat(length - "key".length)} - Property\n"
    for (key in map.keys) {
      string += "$key${" ".repeat(length - key.length)} | ${if (map[key] is VariableValue) map[key]!!.prettyPrint() else map[key]!!.toString()}\n"
    }
    return string
  }

  fun runWithContext(
    function: FunctionValue, interpreter: Interpreter, arguments: List<VariableValue?>
  ): VariableValue {
    interpreter.environment = interpreter.environment.enclose()
    interpreter.environment.copy(map)
    val returnValue = function.call(interpreter, arguments)
    val curr = interpreter.environment.values
    for (key in map.keys) {
      map[key] = (if (map[key] != curr[key]) curr[key] else map[key])!!
    }
    interpreter.environment = interpreter.environment.enclosing!!
    return returnValue
  }
}

open class PartSClass(
  private val name: String,
  private val stubs: MutableList<String> = mutableListOf(),
  map: MutableMap<String, VariableValue> = mutableMapOf()
) : PartSInstance(map), PartSCallable {

  init {
    checkImplemented()
    map["this"] = this.toVariableValue()
  }

  var superclass: PartSClass? = null
    set(value) {
      if (value != null) map["super"] = value.toVariableValue()
      if (map.containsKey("super") && value == null) map.remove("super")
      checkImplemented()
      field = value
    }

  private fun checkImplemented(raise: Boolean = true): Boolean {
    val localStubs = mutableListOf<String>()
    localStubs.addAll(superclass?.stubs ?: mutableListOf())
    val mapped = localStubs.map { Pair(it, map[it]) }
    val notImplemented = mutableListOf<String>()

    for (mappedStub in mapped) {
      if (mappedStub.second == null || mappedStub.second!!.type != "Function") {
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

  open fun toVariableValue() = VariableValue("Class", this)

  override fun call(interpreter: Interpreter, arguments: List<VariableValue?>): VariableValue {
    if (map.containsKey("init")) {
      val value = map["init"] ?: return VariableValue.void
      return when (value.type) {
        "Function" -> {
          interpreter.environment = interpreter.environment.enclose()
          interpreter.environment.copy(map)
          (value.value as FunctionValue).call(interpreter, arguments)
          interpreter.environment = interpreter.environment.enclosing!!
          toVariableValue()
        }
        else -> toVariableValue()
      }
    }
    return toVariableValue()
  }

  override fun toString(): String = "{\n  Class name: $name with data:\n ${super.toString()}\n}"
}

open class PartsIterable : PartSClass("Iterable", mutableListOf("hasNext", "next")) {
  override fun toVariableValue() = VariableValue("Iterable", this)
}

class PartSNativeClass : PartSInstance(mutableMapOf()) {
  fun addNativeMethod(name: String, method: (Interpreter, List<VariableValue?>) -> VariableValue) {
    map[name] = VariableValue("Function", (object : PartSCallable {
      override fun call(
        interpreter: Interpreter, arguments: List<VariableValue?>
      ): VariableValue {
        return method.invoke(interpreter, arguments)
      }

      override fun toString(): String = "<native fn $name>"
    }))
  }
}

fun Double.toVariableValue() = VariableValue("Number", this)
fun List<VariableValue>.toVariableValue() = ArrayValue(this.toMutableList())
enum class BlockReturn { Return, Break, Continue, End }