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
        is ForNode -> runFor(node)
        is RangeNode -> runRange(node)
        is ClassNode -> runClass(node)
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
                else -> value
            }
            environment.define(node.name, value)
        }
        return value
    }

    private fun runFunction(
        node: FunctionNode,
        declare: Boolean = true
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
            ref is VariableValue && (ref.type == "Function" || ref.type == "Class") -> (ref.value as PartSCallable).call(
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
                "Object" -> (accessFrom.value as PartSInstance).map[objectKey] ?: VariableValue.void
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
            if (condition is VariableValue && condition.type == "Object") condition = condition.value as PartSInstance
            else {
                throw RuntimeError("Only object can be a for loop condition")
            }
        }

        var forLoopData = checkRange(condition)
        val codeBlock = BlockNode(mutableListOf(node.body))

        while (forLoopData.first) {
            runBlock(
                codeBlock,
                mutableMapOf("it" to (forLoopData.second.map["current"]!!.value as Double).toVariableValue())
            )
            forLoopData = checkRange(condition)
        }
    }

    private fun runRange(node: RangeNode): VariableValue {
        val bottom = toNumber(node.bottom)
        val top = toNumber(node.top)

        return VariableValue(
            "Object", PartSInstance(
                mutableMapOf(
                    "from" to bottom.toVariableValue(),
                    "to" to top.toVariableValue(),
                    "current" to 0.0.toVariableValue()
                )
            )
        )
    }

    private fun runClass(node: ClassNode) {
        environment = environment.enclose()

        var superclass: VariableValue? = null

        if (node.superclass != null) {
            superclass = environment[node.superclass!!] as? VariableValue
            if (superclass == null || superclass.type != "Class") throw RuntimeError("Super class '${node.superclass}'isn't valid class to inherit")
        }

        for (member in node.parameters) runNode(member)
        for (func in node.functions) runFunction(func, declare = true)

        val classBody = environment.values
        environment = environment.enclosing!!

        val clazz = PartSClass(node.name, classBody)
        if (superclass != null) clazz.superclass = superclass.value as PartSClass

        environment.define(node.name, clazz.toVariableValue())
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

    private fun checkRange(range: PartSInstance): Pair<Boolean, PartSInstance> {
        val from = range.map["from"] ?: return Pair(false, range)
        val to = range.map["to"] ?: return Pair(false, range)
        if (from.type == from.type && from.type == "Number") {
            return if ((from.value as Double) >= (to.value as Double)) Pair(false, range)
            else {
                val curr = range.map["current"] ?: from
                if (curr.type != "Number") Pair(false, range)
                else {
                    val currNum = curr.value as Double
                    var checked = false
                    if (currNum < to.value as Double) {
                        range.map["current"] = (currNum + 1).toVariableValue().also { checked = true }
                    }
                    Pair(checked, range)
                }
            }
        }

        return Pair(false, range)
    }

    private fun toNumber(node: Node): Double = (runNode(node) ?: 0.0).let {
        when (it) {
            is VariableValue -> when (it.type) {
                "Number", "String" -> try {
                    "${it.value}".toDouble()
                } catch (e: Exception) {
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
            """class claz {fun init() {print("hi");}} class clazZ: claz{fun init() {super.init();}} clazZ();"""

        )

        for (test in tests) {
            println("\nExecuting now: $test\n")
            //Clearing variables and stuff
            environment = Environment.asGlobal()
            code = mutableListOf()
            // Parse code
            val tokens = scanTokens(test)
            println(tokens.joinToString(" ") { if (it.type != "SEMICOLON") it.value else "${it.value}\n" })
            val parsed = Parser(tokens).parse()
            for (parsedNode in parsed) println(parsedNode)

            this.run(parsed, false)
        }
    }
}

fun Interpreter.run(code: String, timeTaken: Boolean = true) {
    val tokens = scanTokens(code)
    val parsed = Parser(tokens).parse()

    this.run(parsed, timeTaken)
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
        define(name, VariableValue("Function", (
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

    companion object {

        fun asGlobal(): Environment {
            val env = Environment()

            return env.apply {
                addNative("print") { _, arguments ->
                    for (arg in arguments.mapNotNull { it?.value.toString() }) {
                        println(arg)
                    }
                    VariableValue.void
                }

                addNative("time") { _, _ ->
                    VariableValue("Number", (currentTimeMillis() / 1000L).toDouble())
                }
            }
        }
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
class ForNode(var range: Node, var body: Node) : Node
class ClassNode(
    var name: String,
    var functions: MutableList<FunctionNode>,
    var parameters: MutableList<Node>,
    var superclass: String? = null
) : Node

class ReturnNode(var expr: Node) : Node
class VariableNode(var name: String) : Node
class UnaryNode(var op: String, var expr: Node) : Node
class LiteralNode(var type: String, var value: Any?) : Node
class DotNode(var accessFrom: Node, var accessTo: Node) : Node
class RangeNode(var bottom: Node, var top: Node) : Node


open class VariableValue(var type: String, var value: Any?) {
    companion object {
        var void = VariableValue("Void", null)
    }
}

class ConstValue(type: String, value: Any?) : VariableValue(type, value)

class FunctionValue(var declaration: FunctionDeclaration) : PartSCallable {
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

open class PartSInstance(val map: MutableMap<String, VariableValue>) {
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

class PartSClass(
    var name: String,
    map: MutableMap<String, VariableValue> = mutableMapOf()
) : PartSInstance(map),
    PartSCallable {

    var superclass: PartSClass? = null
        set(value) {
            if (value != null) map["super"] = value.toVariableValue()
            if (map.containsKey("super") && value == null) map.remove("super")
            field = value
        }

    override fun call(interpreter: Interpreter, arguments: List<VariableValue?>): VariableValue {
        if (map.containsKey("init")) {
            val value = map["init"] ?: return VariableValue.void
            when (value.type) {
                "Function" -> {
                    interpreter.environment = interpreter.environment.enclose()
                    interpreter.environment.copy(map)
                    (value.value as FunctionValue).call(interpreter, arguments)
                    interpreter.environment = interpreter.environment.enclosing!!
                    return toVariableValue()
                }
                else -> return toVariableValue()
            }
        }
        return toVariableValue()
    }

    fun toVariableValue() = VariableValue("Class", this)
}

fun Double.toVariableValue() = VariableValue("Number", this)