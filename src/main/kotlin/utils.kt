data class Token(var type: String, var value: String, val length: Int, val line: Int) {
    private var parsed: Boolean = false
    override fun toString(): String = "Token of type $type with value $value"
    fun parse(): Token {
        if (parsed) return this
        val keywords = listOf("and", "else", "false", "if", "let", "nil", "true", "fun", "return")
        val operators = mapOf(
            '+' to "PLUS", '-' to "MINUS",
            '/' to "SLASH", '*' to "STAR",
            ';' to "SEMICOLON", ':' to "COLON",
            '.' to "DOT", ',' to "COMMA",
            '(' to "LEFT_PAREN", ')' to "RIGHT_PAREN",
            '{' to "LEFT_BRACE", '}' to "RIGHT_BRACE",
            '[' to "LEFT_BRACKET", ']' to "RIGHT_BRACKET"
        )
        val logic = listOf(
            LO("=", "EQUAL"), LO("==", "EQUAL_EQUAL"),
            LO("<", "LESS"), LO("<=", "LESS_EQUAL"),
            LO(">", "GREATER"), LO(">=", "GREATER_EQUAL"),
            LO("!", "BANG"), LO("!=", "BANG_EQUAL"),
            LO("||", "OR"), LO("&&", "AND")
        ).groupBy { it.value.length }
        when (type) {
            "NUMBER" -> value = value.toDouble().toString()
            "ORDER" -> type = keywords.find { value.lowercase() == it }?.uppercase()
                ?: "".let { if (it == "") "IDENTIFIER" else it.uppercase() }
            "OPERATOR" -> type = operators[value[0]] ?: "ERROR-XD"
            "LOGIC" -> {
                type = logic[value.length]?.find { value == it.value }?.name
                    ?: "ERROR-WRONG-LOGIC"
            }
        }
        if (type == "ERROR-WRONG-LOGIC") throw Error("[$line] Wrong logic type, value: '$value'")
        return this.also { parsed = true }
    }

    private data class LO(val value: String, val name: String)
}

fun String.toCamelCase(): String = toString().let { "${it[0].uppercase()}${it.substring(1).lowercase()}" }

class Return(val value: Any?) : RuntimeException(null, null, false, false)
class RuntimeError(message: String?) : RuntimeException(message)
