data class Token(var type: String, var value: String?, val length: Int, val line: Int) {
    private var parsed: Boolean = false
    override fun toString(): String = "Token of type $type with value $value"
    fun parse(): Token {
        if (parsed) return this
        val keywords = listOf("and", "else", "false", "for", "if", "let", "nil", "true", "while", "fun", "return")
        val operators = mapOf(
            '+' to "PLUS", '-' to "MINUS", '/' to "SLASH", '*' to "STAR",
            ';' to "SEMICOLON", '.' to "DOT", ',' to "COMMA", ':' to "COLON",
            '(' to "LEFT_PAREN", ')' to "RIGHT_PAREN", '{' to "LEFT_BRACE", '}' to "RIGHT_BRACE"
        )
        val logic = listOf(
            LO("=", "EQUAL"), LO("==", "EQUAL_EQUAL"),
            LO("<", "LESS"), LO("<=", "LESS_EQUAL"),
            LO(">", "GREATER"), LO(">=", "GREATER_EQUAL"),
            LO("!", "BANG"), LO("!=", "BANG_EQUAL"),
            LO("||", "OR"), LO("&&", "AND")
        ).groupBy { it.value.length }
        when (type) {
            "NUMBER" -> value = (value as String).toDouble().toString()
            "ORDER" -> type = keywords.find { (value as String).toLowerCase() == it }?.toUpperCase()
                ?: "".let { if (it.isEmpty()) "IDENTIFIER" else it.toUpperCase() }
            "OPERATOR" -> type = operators[value?.get(0)] ?: "ERROR-XD"
            "LOGIC" -> {
                type = logic[(value as String).length]?.find { value as String == it.value }?.name
                    ?: "ERROR-WRONG-LOGIC"
            }
        }
        if (type == "ERROR-WRONG-LOGIC") throw Error("[$line] Wrong logic type, value: '$value'")
        return this.also { parsed = true }
    }

    private data class LO(val value: String, val name: String)
}

data class ParserObject(var name: String, var data: MutableMap<String, Any?>) {
    operator fun get(key: String): Any? = data[key]
    operator fun set(key: String, value: Any?) {
        data[key] = value
    }

    fun delete(key: String) = data.remove(key)
    override fun equals(other: Any?): Boolean {
        if (other !is ParserObject) return false
        return name == other.name && data.size == other.data.size && data == other.data
    }

    override fun hashCode(): Int = 31 * name.hashCode() + data.hashCode()
}

fun String.toCamelCase(): String = toString().let { "${it[0].toUpperCase()}${it.substring(1).toLowerCase()}" }

class Return(val value: Any?) : RuntimeException(null, null, false, false)
class RuntimeError(message: String?) : RuntimeException(message)
