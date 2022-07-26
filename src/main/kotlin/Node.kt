sealed interface Node

class LetNode(var name: String, var value: Node?) : Node {
  override fun toString() = "LetNode(name='$name', value=$value)"
}

class ConstNode(var name: String, var value: Node?) : Node {
  override fun toString() = "ConstNode(name='$name', value=$value)"
}

class AssignNode(var name: String, var value: Node?) : Node {
  override fun toString() = "AssignNode(name ='$name', value=$value)"
}

class FunctionNode(var name: String, var parameters: List<FunctionParameter>, var body: BlockNode) : Node {
  override fun toString() =
    "FunctionNode(name='$name', parameters='${parameters.joinToString(", ") { if (it is DefaultParameter) "(Default: ${it.value})" else "(${it.name})" }}', body: ($body))"
}

class ImplementNode(var name: String) : Node {
  override fun toString() = "Stub($name)"
}

class IfNode(var condition: Node, var thenBranch: Node, var elseBranch: Node?) : Node {
  override fun toString() = "IfNode(condition = '$condition', then = '$thenBranch', else = `$elseBranch`)"
}

class BinaryNode(var op: String, var left: Node, var right: Node) : Node {
  override fun toString() = "BinaryNode(op = '$op', left = '$left', right = '$right')"
}

class CallNode(var name: String, var args: List<Node>) : Node {

  override fun toString() = "CallNode(name = '$name', args = ${if (args.isEmpty()) "[]" else args.joinToString()})"
}

class BlockNode(var body: MutableList<Node>) : Node {
  override fun toString() = "BodyNode(body = '${body.joinToString()}')"
}

class ObjectNode(var map: MutableMap<String, Node>) : Node {
  override fun toString() = "ObjectNode(map = '${map.entries.joinToString { "${it.key}:${it.value}" }}')"
}

class ForNode(var range: Node, var body: Node) : Node {
  override fun toString() = "ForNode(range = '$range', body = '$body')"
}

class ClassNode(
  var name: String,
  var functions: MutableList<FunctionNode>,
  var parameters: MutableList<Node>,
  var stubs: MutableList<ImplementNode>,
  var superclass: String?
) : Node {
  override fun toString() =
    "ClassNode(name = '$name', functions = '${functions.joinToString()}, parameters = '${parameters.joinToString()}, stubs = '${stubs.joinToString()}', superclass = '$superclass')"
}

class ReturnNode(var expr: Node) : Node {
  override fun toString() = "ReturnNode('$expr')"
}

object BreakNode : Node {
  override fun toString() = "<BreakNode>"
}

object ContinueNode : Node {
  override fun toString() = "<ContinueNode>"
}

class VariableNode(var name: String) : Node {
  override fun toString() = "VariableNode('$name')"
}

class UnaryNode(var op: String, var expr: Node) : Node {
  override fun toString() = "UnaryNode(op = '$op', expr = '$expr')"
}

class LiteralNode(var type: String, var value: Any?) : Node {
  override fun toString() = "LiteralNode(type = '$type', value = '$value')"
}

class DotNode(var accessFrom: Node, var accessTo: Node) : Node {
  override fun toString() = "DotNode(from = '$accessFrom', to = '$accessTo')"
}

class RangeNode(var bottom: Node, var top: Node) : Node {
  override fun toString() = "RangeNode(from = '$bottom', to = '$top')"
}

class ArrayNode(var data: MutableList<Node>) : Node {
  override fun toString() = "ArrayNode(data = [${data.joinToString(", ")}])"
}

class ImportNode(var import: MutableList<ImportIdentifier>, var from: String) : Node {
  override fun toString() = "Import {${import.joinToString(", ")}} from '$from'"
}