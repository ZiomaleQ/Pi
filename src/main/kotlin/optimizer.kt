class Optimizer(private var code: MutableList<ParserObject>) {
    fun parse(): MutableList<ParserObject> {
        return optimizeParts()
    }

    private fun optimizeParts(): MutableList<ParserObject> {
      val temp: MutableList<ParserObject> = mutableListOf()
        for (x in code) {
            when (x.name) {
                "Expression", "Grouping" -> temp.add(optimizeNode(x["expr"] as ParserObject))
                "If" -> temp.add(optimizeIf(x))
                "Binary" -> temp.add(optimizeBinary(x))
                "Call" -> temp.add(optimizeCall(x))
                else -> temp.add(x)
            }
        }
        return temp
    }

    private fun optimizeCall(call: ParserObject): ParserObject {
        val temp = (call["arguments"] as MutableList<*>).map { optimizeNode(it as ParserObject) }
        return ParserObject("Call", mapOf("callee" to call["callee"], "arguments" to temp))
    }

    private fun optimizeBinary(binary: ParserObject): ParserObject {
        val temp = binary.toPOModif()
        val x = optimizeNode(temp["left"] as ParserObject)
        val y = optimizeNode(temp["right"] as ParserObject)
        var z: ParserObject? = null
        if (x.name == "Literal" && y.name == "Literal") {
            when (temp["operator"]) {
                "AND", "OR" -> {
                    z = if (x["type"] == y["type"] && x["type"] == "void") x
                    else ParserObject(
                        "Literal",
                        mapOf(
                            "type" to "Boolean", "value" to (if (temp["operator"] == "AND")
                                isTruthy(x) && isTruthy(y) else isTruthy(x) || isTruthy(y))
                        )
                    )
                }
                "GREATER", "GREATER_EQUAL", "LESS", "LESS_EQUAL", "BANG_EQUAL", "EQUAL_EQUAL" -> {
                    z = if (x["type"] == y["type"] && x["type"] == "void") x
                    else {
                        val value = when (temp["operator"]) {
                            "GREATER" -> toNumber(x) > toNumber(y)
                            "GREATER_EQUAL" -> toNumber(x) >= toNumber(y)
                            "LESS" -> toNumber(x) > toNumber(y)
                            "LESS_EQUAL" -> toNumber(x) >= toNumber(y)
                            "BANG_EQUAL" -> x == y
                            "EQUAL_EQUAL" -> x != y
                            else -> false
                        }
                        ParserObject("Literal", mapOf("type" to "Boolean", "value" to value))
                    }
                }
                "PLUS", "SLASH", "STAR", "MINUS" -> {
                    z = if (x["type"] == y["type"] && x["type"] == "void") x
                    else {
                        val value: Any = when (temp["operator"]) {
                            "PLUS" -> {
                                if (x["type"] == "String" || y["type"] == "String") "${x["value"]}${y["value"]}"
                                else toNumber(x) + toNumber(y)
                            }
                            "MINUS" -> toNumber(x) - toNumber(y)
                            "STAR" -> toNumber(x) * toNumber(y)
                            "SLASH" -> {
                                if (toNumber(y) == 0.0) throw Error("Don't divide by zero")
                                toNumber(x) / toNumber(y)
                            }
                            else -> 0.0
                        }
                        ParserObject(
                            "Literal",
                            mapOf(
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
        "Number" -> (node["value"] as String).toDouble()
        "String" -> try {
            (node["value"] as String).toDouble()
        } catch (e: Error) {
            0.0
        }
        else -> 0.0
    }

    private fun optimizeIf(expr: ParserObject): ParserObject {
        val temp = expr.toPOModif()
        temp["condition"] = optimizeNode(temp["condition"] as ParserObject)
        if((temp["condition"] as ParserObject).name == "Literal") {
          return optimizeNode(if(isTruthy(temp["condition"] as ParserObject)) temp["thenBranch"] as ParserObject else temp["elseBranch"] as ParserObject)
        }
        temp["thenBranch"] = optimizeNode(temp["thenBranch"] as ParserObject)
        if (temp["elseBranch"] != null) temp["elseBranch"] = optimizeNode(temp["elseBranch"] as ParserObject)
        else temp.delete("elseBranch")
        return temp.toPO()
    }

    private fun optimizeNode(node: ParserObject): ParserObject {
        return when (node.name) {
            "Expression", "Grouping" -> optimizeNode(node["expr"] as ParserObject)
            "If" -> optimizeIf(node)
            "Binary" -> optimizeBinary(node)
            "Call" -> optimizeCall(node)
            else -> node
        }
    }
}
