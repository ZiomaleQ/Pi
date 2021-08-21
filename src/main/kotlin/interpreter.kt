import java.lang.System.*

class Interpreter {
    private val globals = Environment()
    private var environment = globals
    private var code = mutableListOf<Node>()

    init {
        addNative("print") { _, arguments ->
            val args = arguments.mapNotNull { it?.value.toString() }
            for (arg in args) {
                println(arg)
            }
            VariableValue.void
        }

        addNative("time") { _, _ ->
            VariableValue("Number", (currentTimeMillis() / 1000L).toDouble())
        }
    }

    private fun addNative(name: String, func: (Interpreter, List<VariableValue?>) -> VariableValue) {
        globals.define(name, VariableValue("Function", (
                object : PartSCallable {
                    override fun call(
                        interpreter: Interpreter,
                        arguments: List<VariableValue?>
                    ): VariableValue {
                        return func.invoke(interpreter, arguments)
                    }

                    override fun toString(): String = "<native fn $name>"
                }
                )))
    }

    fun run(code: List<Node>) {
        this.code.addAll(code)
        val time = currentTimeMillis().toDouble()
        while (this.code.size != 0) runNode(advance())
        println("Successfully run code in ${(currentTimeMillis().toDouble() - time) / 1000.0}s")
    }

    fun runNode(node: Node) = when (node) {
        is LetNode -> runLet(node)
        is ConstNode -> runConst(node)
        is FunctionNode -> runFunction(node)
        is IfNode -> runIf(node)
        is ReturnNode -> throw runReturn(node)
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
        else -> {
            throw Error("Unexpected node type '${node}'")
        }
    }

    private fun runReturn(node: ReturnNode): Return = Return(runNode(node.expr))

    private fun runLet(node: LetNode): VariableValue {
        var value = VariableValue.void
        if (node.value == null) environment.define(node.name, value)
        else {
            val tempNode = node.value as Node
            value = when (tempNode) {
                is LiteralNode -> VariableValue(tempNode.type, tempNode.value)
                is FunctionNode -> VariableValue("Function", runFunction(tempNode, declare = false))
                is ObjectNode -> VariableValue("Object", runObject(tempNode).value)
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
                else -> value
            }
            environment.define(node.name, value)
        }
        return value
    }

    private fun runFunction(node: FunctionNode, declare: Boolean = true): VariableValue = FunctionValue(
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

    fun runBlock(node: BlockNode, map: MutableMap<String, VariableValue>? = null): Any {
        environment = environment.enclose()
        if (map != null) for (x in map.keys) environment.define(x, map[x]!!)
        val temp = node.body

        try {
            for (x in temp) {
                runNode(x)
            }
        } finally {
            environment = environment.enclosing!!
        }

        return temp
    }

    private fun runBinary(node: BinaryNode) = when (node.op) {
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
        "GREATER", "GREATER_EQUAL", "LESS", "LESS_EQUAL", "EQUAL_EQUAL", "BANG_EQUAL" -> {
            val left = toNumber(node.left)
            val right = toNumber(node.right)
            VariableValue(
                "Boolean", when (node.op) {
                    "GREATER" -> left > right
                    "GREATER_EQUAL" -> left >= right
                    "LESS" -> left < right
                    "LESS_EQUAL" -> left <= right
                    "BANG_EQUAL" -> left != right
                    "EQUAL_EQUAL" -> left == right
                    else -> null
                }
            )
        }
        else -> null
    }

    @Suppress("UNCHECKED_CAST")
    private fun runCall(node: CallNode): VariableValue {
        val ref = environment[node.name] ?: throw RuntimeError("Undefined '${node.name}' reference")
        return when {
            ref is VariableValue && ref.type == "Function" -> (ref.value as PartSCallable).call(
                this,
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

        val objectKey = if (node.accessTo is VariableNode) {
            (node.accessTo as VariableNode).name
        } else {
            when (val accessTo = runNode(node.accessTo)) {
                is VariableValue -> when (accessTo.type) {
                    "Number", "String", "Boolean" -> accessTo.value.toString()
                    else -> throw RuntimeError("Invalid access key")
                }
                else -> throw RuntimeError("Invalid access key")
            }
        }


        return when (accessFrom) {
            is VariableValue -> when (accessFrom.type) {
                "Object" -> (accessFrom.value as PartSInstance).map[objectKey] ?: VariableValue.void
                else -> throw RuntimeError("STD methods for type: '${accessFrom.type}' are not implemented")
            }
            is PartSInstance -> accessFrom.map[objectKey] ?: VariableValue.void
            else -> VariableValue.void
        }
    }

    private fun runEnclosed(node: Node): VariableValue {
        environment = environment.enclose()
        val temp = runNode(node)
        environment = environment.enclosing!!
        return temp as VariableValue
    }

    private fun toBoolean(node: Node): Boolean = runNode(node)?.let {
        when (it) {
            is VariableValue -> when (it.type) {
                "Number", "String" -> "${it.value}".isNotEmpty()
                "Boolean" -> "${it.value}" == "true"
                "Function" -> true
                "Object" -> (it as PartSInstance).map.isNotEmpty()
                else -> false
            }
            is FunctionValue -> true
            is PartSInstance -> it.map.isNotEmpty()
            else -> false
        }
    } ?: false

    private fun toNumber(node: Node): Double = (runNode(node) ?: 0.0).let {
        when (it) {
            is VariableValue -> when (it.type) {
                "Number", "String" -> try {
                    "${it.value}".toDouble()
                } catch (e: Error) {
                    0.0
                }
                "Boolean" -> if ("${it.value}" == "true") 1.0 else 0.0
                "Function" -> 1.0
                "Object" -> (it as PartSInstance).map.size.toDouble()
                else -> 0.0
            }
            is FunctionValue -> 1.0
            is PartSInstance -> it.map.size.toDouble()
            else -> 0.0
        }
    }

    private fun advance() = code.removeFirst()
}


class Environment(var enclosing: Environment? = null) {
    private val values: MutableMap<String, VariableValue> = mutableMapOf()

    fun enclose() = Environment(this)

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
}

interface Node

class LetNode(var name: String, var value: Node?) : Node
class ConstNode(var name: String, var value: Node?) : Node
class AssignNode(var name: String, var value: Node?) : Node
class FunctionNode(var name: String, var parameters: List<FunctionParameter>, var body: BlockNode) : Node
class IfNode(var condition: Node, var thenBranch: Node, var elseBranch: Node?) : Node
class BinaryNode(var op: String, var left: Node, var right: Node) : Node
class CallNode(var name: String, var args: List<Node>) : Node
class BlockNode(var body: MutableList<Node>) : Node
class ObjectNode(var map: MutableMap<String, Node>) : Node

class ReturnNode(var expr: Node) : Node
class VariableNode(var name: String) : Node
class UnaryNode(var op: String, var expr: Node) : Node
class LiteralNode(var type: String, var value: Any?) : Node
class DotNode(var accessFrom: Node, var accessTo: Node) : Node

open class VariableValue(var type: String, var value: Any?) {
    companion object {
        var void = VariableValue("Void", null)
    }
}

class ConstValue(type: String, value: Any?) : VariableValue(type, value)

class FunctionValue(var declaration: FunctionDeclaration, var closure: Environment? = null) : PartSCallable {
    override fun toString() = "${declaration.name} function"
    override fun call(interpreter: Interpreter, arguments: List<VariableValue?>): VariableValue {
        val map = mutableMapOf<String, VariableValue>()
        for (i in declaration.parameters.indices) {
            val variableValue = arguments.getOrNull(i) ?: if (declaration.parameters[i] is DefaultParameter)
                interpreter.runNode((declaration.parameters[i] as DefaultParameter).value) as? VariableValue
            else
                VariableValue.void
            map[declaration.parameters[i].name] = variableValue ?: VariableValue.void
        }
        var returnValue = VariableValue("Void", null)
        try {
            interpreter.runBlock(declaration.body, map)
        } catch (rv: Return) {
            returnValue = rv.value as VariableValue
        }
        return returnValue
    }
}

data class FunctionDeclaration(var name: String, var parameters: List<FunctionParameter>, var body: BlockNode)

open class FunctionParameter(val name: String)
class DefaultParameter(name: String, val value: Node) : FunctionParameter(name)

interface PartSCallable {
    fun call(interpreter: Interpreter, arguments: List<VariableValue?>): VariableValue
}

class PartSInstance(val map: MutableMap<String, VariableValue>) {
    override fun toString(): String {
        if (map.isEmpty()) return "#> Empty #<"
        val length = map.keys.maxByOrNull { it.length }!!.length.coerceAtLeast("key ".length)
        var string = "Key${" ".repeat(length - "key".length)} - Property\n"
        for (key in map.keys) {
            string += "$key${" ".repeat(length - key.length)} | ${if (map[key] is VariableValue) map[key]!!.value.toString() else map[key]!!.toString()}\n"
        }
        return string
    }
}