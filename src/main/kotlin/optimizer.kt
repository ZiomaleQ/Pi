class Optimizer(private var code: MutableList<ParserObject>) {
    fun parse(): MutableList<ParserObject> = optimizeParts()

    private fun optimizeParts(): MutableList<ParserObject> {
      val temp: MutableList<ParserObject> = mutableListOf()
      for (x in code) temp.add(optimizeNode(x))
      return temp
    }

    private fun optimizeCall(call: ParserObject): ParserObject {
        val temp = (call["arguments"] as MutableList<*>).map { optimizeNode(it as ParserObject) }
        return ParserObject("Call", mutableMapOf("callee" to call["callee"], "arguments" to temp))
    }

    private fun optimizeBinary(binary: ParserObject): ParserObject {
        val x = optimizeNode(binary["left"] as ParserObject)
        val y = optimizeNode(binary["right"] as ParserObject)
        var z: ParserObject? = null
        if (x.name == "Literal" && y.name == "Literal") {
            when (binary["operator"]) {
                "AND", "OR" -> {
                    z = if (x["type"] == y["type"] && x["type"] == "void") x
                    else ParserObject(
                        "Literal",
                        mutableMapOf(
                            "type" to "Boolean", "value" to (if (binary["operator"] == "AND")
                                isTruthy(x) && isTruthy(y) else isTruthy(x) || isTruthy(y))
                        )
                    )
                }
                "GREATER", "GREATER_EQUAL", "LESS", "LESS_EQUAL", "BANG_EQUAL", "EQUAL_EQUAL" -> {
                    z = if (x["type"] == y["type"] && x["type"] == "void") x
                    else {
                        val value = when (binary["operator"]) {
                            "GREATER" -> toNumber(x) > toNumber(y)
                            "GREATER_EQUAL" -> toNumber(x) >= toNumber(y)
                            "LESS" -> toNumber(x) < toNumber(y)
                            "LESS_EQUAL" -> toNumber(x) <= toNumber(y)
                            "BANG_EQUAL" -> x == y
                            "EQUAL_EQUAL" -> x != y
                            else -> false
                        }
                        ParserObject("Literal", mutableMapOf("type" to "Boolean", "value" to value))
                    }
                }
                "PLUS", "SLASH", "STAR", "MINUS" -> {
                    z = if (x["type"] == y["type"] && x["type"] == "void") x
                    else {
                        val value: Any = when (binary["operator"]) {
                            "PLUS" -> {
                                if (x["type"] == "String" || y["type"] == "String") "${x["value"]}${y["value"]}"
                                else toNumber(x) + toNumber(y)
                            }
                            "MINUS" -> toNumber(x) - toNumber(y)
                            "STAR" -> toNumber(x) * toNumber(y)
                            "SLASH" -> {
                                if (toNumber(y) == 0.0) throw Error("Don't divide by zero plz")
                                toNumber(x) / toNumber(y)
                            }
                            else -> 0.0
                        }
                        ParserObject(
                            "Literal",
                            mutableMapOf(
                                "type" to if (x["type"] == "String" || y["type"] == "String") "String" else "Number",
                                "value" to value.toString()
                            )
                        )
                    }
                }
            }
        }
        return z ?: binary
    }

    private fun isTruthy(node: ParserObject): Boolean {
        if (node["type"] == "Boolean") return node["value"] as Boolean
        if (node["type"] == "Number") return node["value"] as Double > 0
        if (node["type"] == "String") return (node["value"] as String).isNotEmpty()
        return false
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

    private fun optimizeNode(node: ParserObject): ParserObject = when (node.name) {
            "Expression", "Grouping" -> optimizeNode(node["expr"] as ParserObject)
            "If" -> optimizeIf(node)
            "Binary" -> optimizeBinary(node)
            "Call" -> optimizeCall(node)
            "While" -> optimizeWhile(node)
            else -> node
    }
}
