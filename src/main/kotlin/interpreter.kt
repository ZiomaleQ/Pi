import java.lang.System.currentTimeMillis

class Interpreter {
    private val globals = Environment()
    private var environment = globals
    private var code = mutableListOf<ParserObject>();

    init {
    globals.define("print", VariableValue("Function", (object : PartSCallable {
            override fun arity(): Int = 1
            override fun call(interpreter: Interpreter, arguments: List<Any?>) = println((arguments[0] as VariableValue).value)
            override fun toString(): String = "<native fn>"
        })))
}

    fun run(code: List<ParserObject>) {
      this.code.addAll(code)
      var time = currentTimeMillis().toDouble()
      while(this.code.size != 0) runNode(advance())
      println("Succesfully run code in ${(currentTimeMillis().toDouble() - time) / 1000.0}s")
    }

    private fun runNode(node: ParserObject) = when(node.name) {
      "Let" -> runLet(node)
      "Function" -> runFunction(node)
      "If" -> runIf(node)
      "While" -> runWhile(node)
      "Return" -> throw runReturn(node)
      "Block" -> runBlock(node)
      "Binary" -> runBinary(node)
      "Call" -> runCall(node)
      "Assign" -> runAssign(node)
      "Variable" -> environment[node["name"] as String]
      "Literal" -> VariableValue(node["type"] as String, node["value"])
      else -> {println("Unexpected node type '${node.name}'"); null}
    }

    private fun runReturn(node: ParserObject): Return {
      var temp = runNode(node["expr"] as ParserObject)
      return Return(temp)
    }

    private fun runLet(node: ParserObject): VariableValue {
      var value = VariableValue("Void", null)
      if(node["initializer"] == null) environment.define(node["name"] as String, value)
      else {
        var tempNode = node["initializer"] as ParserObject;
        value = when (tempNode.name) {
          "Literal" -> VariableValue(tempNode["type"] as String, tempNode["value"])
          "Function" -> VariableValue("function", runFunction(tempNode, declare = false))
          else -> value
        }
        environment.define(node["name"] as String, value)
      }
      return value
    }

    @Suppress("UNCHECKED_CAST")
    private fun runFunction(node: ParserObject, declare: Boolean = true): VariableValue {
      var value = FunctionValue(FunctionDeclaration(node["name"] as String,
        node["parameters"] as List<String>, node["body"] as ParserObject)
      )
      return if(declare && node["name"] as String != "\$Anonymous\$") environment.define(node["name"] as String, VariableValue("Function", value)) else VariableValue("Function", value)
    }

    private fun runIf(node: ParserObject) = when {
      toBoolean(node["condition"] as ParserObject) -> runEnclosed(node["thenBranch"] as ParserObject)
      node["elseBranch"] != null -> runEnclosed(node["elseBranch"] as ParserObject)
      else -> null
    }

    private fun runWhile(node: ParserObject) {
      if(node["initializer"] != null) {
        this.environment = environment.enclose()
        runNode(node["initializer"] as ParserObject)
      }
      while(toBoolean(node["condition"] as ParserObject)) { runEnclosed(node["body"] as ParserObject) }
      if(node["initializer"] != null) this.environment = environment.enclosing!!
    }

    @Suppress("UNCHECKED_CAST")
    fun runBlock(node: ParserObject, map: MutableMap<String, VariableValue>? = null): Any? {
      environment = environment.enclose()
      if(map != null) for (x in map.keys) environment.define(x, map[x]!!)
      var temp = node["body"] as MutableList<ParserObject>

      try {
        for(x in temp) { runNode(x) }
      }
      finally {
        environment = environment.enclosing!!
      }
      return temp
    }

    private fun runBinary(node: ParserObject) = when(node["operator"] as String) {
      "OR", "AND" -> {
        VariableValue("Boolean", toBoolean(node["left"] as ParserObject).let {
        if(it && node["operator"] as String == "AND") toBoolean(node["right"] as ParserObject)
        else it || toBoolean(node["right"] as ParserObject)})
      }
      "MINUS", "STAR", "SLASH", "PLUS" -> {
        val left = toNumber(node["left"] as ParserObject)
        val right = toNumber(node["right"] as ParserObject)
        if(right == 0.0 && node["operator"] as String == "SLASH") throw RuntimeError("Do not divide by zero (0).")
        VariableValue("Number", when(node["operator"] as String) {
          "MINUS" -> left - right
          "PLUS" -> left + right
          "SLASH" -> left / right
          "STAR" -> left * right
          "BANG_EQUAL" -> left != right
          "EQUAL_EQUAL" -> left == right
          else -> null
        })}
      "GREATER", "GREATER_EQUAL", "LESS", "LESS_EQUAL" -> {
        val left = toNumber(node["left"] as ParserObject)
        val right = toNumber(node["right"] as ParserObject)
        VariableValue("Boolean", when(node["operator"] as String) {
          "GREATER" -> left > right
          "GREATER_EQUAL" -> left >= right
          "LESS" -> left < right
          "LESS_EQUAL" -> left <= right
          else -> null
        })
      }
      else -> null
    }

    @Suppress("UNCHECKED_CAST")
    private fun runCall(node: ParserObject): Any? {
      var ref = environment[node["callee"] as String] ?: throw RuntimeError("Undefined '${node["callee"]}' reference")
      return when {
        ref is VariableValue && ref.type == "Function" -> (ref.value as PartSCallable).call(this, (node["arguments"] as List<ParserObject>).map {runNode(it)})
        else -> throw RuntimeError("Variable '${node["callee"]}' is not a function")
      }
    }

    private fun runAssign(node: ParserObject): VariableValue {
      var value = VariableValue("Void", null)

      if(node["value"] == null) return value
      var tempNode = node["value"] as ParserObject;
      value = when (tempNode.name) {
        "Literal" -> VariableValue(tempNode["type"] as String, tempNode["value"])
        "Function" -> VariableValue("function", runFunction(tempNode, declare = false))
        else -> runNode(tempNode) as VariableValue
      }
      environment.assign(node["name"] as String, value)
      return value
    }

    private fun runEnclosed(node: ParserObject): Any? {
      environment = environment.enclose()
      var temp = runNode(node)
      environment = environment.enclosing!!
      return temp
    }

    private fun toBoolean(node: ParserObject): Boolean = runNode(node)?.let { when (it) {
        is VariableValue -> when (it.type) {
            "Number", "String" -> "${it.value}".isNotEmpty()
            "Boolean" -> "${it.value}" == "true"
            "Function" -> true
            else -> false
        }
        is FunctionValue -> true
        else -> false
      }
    } ?: false

    private fun toNumber(node: ParserObject): Double = (runNode(node)?: 0.0).let { when (it) {
        is VariableValue -> when (it.type) {
            "Number", "String" -> try { "${it.value}".toDouble() } catch (e: Error) { 0.0 }
            "Boolean" -> if("${it.value}" == "true") 1.0 else 0.0
            "Function" -> 1.0
            else -> 0.0
        }
        is FunctionValue -> 1.0
        else -> 0.0
      }
    }
    private fun advance() = code.removeFirst()
}

class Environment(var enclosing: Environment? = null) {
    private val values: MutableMap<String, VariableValue> = mutableMapOf()

    fun enclose() = Environment(this)

    fun define(name: String, value: VariableValue): VariableValue {
        if(!values.containsKey(name)) values[name] = value
        else throw RuntimeError("Variable '$name' arleady declared.")
        return value
    }

    operator fun get(name: String): Any? = when {
        values.containsKey(name) -> values[name]
        enclosing != null -> enclosing!![name]
        else -> null
    }


    fun assign(name: String, value: VariableValue): Unit = when {
          values.containsKey(name) -> values[name] = value
          enclosing != null -> enclosing!!.assign(name, value)
          else -> throw RuntimeError("Undefined variable '${name}'.")
    }

    fun getAt(distance: Int, name: String?): Any? = ancestor(distance)?.values?.get(name)

    private fun ancestor(distance: Int): Environment? = if (distance <= 0) this.enclosing else this.ancestor(distance - 1)

    fun assignAt(distance: Int, name: String, value: VariableValue) {
        ancestor(distance)!!.values[name] = value
    }
 }

data class VariableValue(var type: String, var value: Any?)
data class FunctionValue(var declaration: FunctionDeclaration, var closure: Environment? = null): PartSCallable {
   override fun arity() = declaration.parameters.size
   override fun toString() = "${declaration.name} function"
   override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
      val map = mutableMapOf<String, VariableValue>()
      for (i in declaration.parameters.indices) map[declaration.parameters[i]] = arguments[i] as VariableValue
      var returnValue: VariableValue? = VariableValue("Void", null)
      try { interpreter.runBlock(declaration.body, map) }
        catch (rv: Return) {returnValue = rv.value as VariableValue }
      return returnValue
  }
}
data class FunctionDeclaration(var name: String, var parameters: List<String>, var body: ParserObject)

interface PartSCallable {
     fun arity(): Int
     fun call(interpreter: Interpreter, arguments: List<Any?>): Any?
 }
