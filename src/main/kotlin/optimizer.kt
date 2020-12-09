class Optimizer(private var code: MutableList<ParserObject>) {
    private val variables = mutableMapOf<String, ParserObject>()
    private var optimizedCode = mutableListOf<ParserObject>()

    fun parse(): MutableList<ParserObject> {
      for (x in code) optimizedCode.add(optimizeNode(x))
      return optimizedCode
    }

    private fun optimizeCall(call: ParserObject): ParserObject {
        val temp = (call["arguments"] as MutableList<*>).map { optimizeNode(it as ParserObject) }
        return ParserObject("Call", mutableMapOf("callee" to call["callee"], "arguments" to temp))
    }

    private fun isTruthy(node: ParserObject) = when(node["type"]) {
      "Boolean" -> node["value"] as Boolean
      "Number" -> node["value"] as Double != 0.0
      "String" -> (node["value"] as String).isNotEmpty()
      else -> false
    }

    private fun toNumber(node: ParserObject): Double = when (node["type"]) {
        "Boolean" -> if (node["value"] as Boolean) 1.0 else 0.0
        "Number", "String" -> try {
            (node["value"] as String).toDouble()
        } catch (e: Error) {
            0.0
        }
        else -> 0.0
    }

    private fun optimizeIf(expr: ParserObject): ParserObject {
        expr["condition"] = optimizeNode(expr["condition"] as ParserObject)
        if((expr["condition"] as ParserObject).name == "Literal") {
          return optimizeNode(if(isTruthy(expr["condition"] as ParserObject)) expr["thenBranch"] as ParserObject else expr["elseBranch"] as ParserObject)
        }
        expr["thenBranch"] = optimizeNode(expr["thenBranch"] as ParserObject)
        if (expr["elseBranch"] != null) expr["elseBranch"] = optimizeNode(expr["elseBranch"] as ParserObject)
        else expr.delete("elseBranch")
        return expr
    }

    private fun optimizeWhile(expr: ParserObject): ParserObject {
      expr["condition"] = optimizeNode(expr["condition"] as ParserObject)
      expr["body"] = optimizeNode(expr["body"] as ParserObject)
      return expr
    }

    private fun optimizeBlock(expr: ParserObject): ParserObject {
      val temp = mutableListOf<ParserObject>()
      for(x in (expr["body"] as List<ParserObject>)) temp.add(optimizeNode(x))
      expr["body"] = temp
      return expr
    }

    private fun optimizeLet(node: ParserObject): ParserObject {
      if(variables.containsKey(node["name"] as String)) throw Error("Redeclaration of '${node["name"]}' variable." )
      node["initializer"] = optimizeNode(node["initializer"] as ParserObject)
      variables[node["name"] as String] = node["initializer"] as ParserObject
      return node
    }

    private fun optimizeAssign(node: ParserObject): ParserObject {
      node["value"] = optimizeNode(node["value"] as ParserObject)
      return node
    }

    private fun optimizeUnary(node: ParserObject): ParserObject {
      node["right"] = optimizeNode(node["right"] as ParserObject)
      return node
    }

    private fun optimizeBinary(node: ParserObject): ParserObject {
      node["left"] = optimizeNode(node["left"] as ParserObject)
      node["right"] = optimizeNode(node["right"] as ParserObject)
      return node
    }

    private fun optimizeFunction(node: ParserObject): ParserObject {
      node["body"] = optimizeNode(node["body"] as ParserObject)
      return node
    }

    private fun optimizeReturn(node: ParserObject): ParserObject {
      node["expr"] = optimizeNode(node["expr"] as ParserObject)
      return node
    }

    private fun optimizeNode(node: ParserObject): ParserObject = when (node.name) {
            "Expression", "Grouping" -> optimizeNode(node["expr"] as ParserObject)
            "If" -> optimizeIf(node)
            "Function" -> optimizeFunction(node)
            "Binary" -> optimizeBinary(node)
            "Return" -> optimizeReturn(node)
            "Call" -> optimizeCall(node)
            "While" -> optimizeWhile(node)
            "Block" -> optimizeBlock(node)
            "Let" -> optimizeLet(node)
            "Assign" -> optimizeAssign(node)
            "Unary" -> optimizeUnary(node)
            else -> node
    }
}
