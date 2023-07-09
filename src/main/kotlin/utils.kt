val keywords = listOf(
  "and",
  "or",
  "xor",
  "else",
  "false",
  "if",
  "let",
  "true",
  "fun",
  "return",
  "const",
  "to",
  "for",
  "class",
  "implement",
  "break",
  "continue",
  "import",
  "from",
  "as",
  "when",
  "static"
)
val operators = mapOf(
  '+' to "PLUS",
  '-' to "MINUS",
  '/' to "SLASH",
  '*' to "STAR",
  ';' to "SEMICOLON",
  ':' to "COLON",
  '.' to "DOT",
  ',' to "COMMA",
  '(' to "LEFT_PAREN",
  ')' to "RIGHT_PAREN",
  '{' to "LEFT_BRACE",
  '}' to "RIGHT_BRACE",
  '[' to "LEFT_BRACKET",
  ']' to "RIGHT_BRACKET"
)
val logic = listOf(
  LO("=", "EQUAL"), LO("==", "EQUAL_EQUAL"),
  LO("<", "LESS"), LO("<=", "LESS_EQUAL"),
  LO(">", "GREATER"), LO(">=", "GREATER_EQUAL"),
  LO("!", "BANG"), LO("!=", "BANG_EQUAL"),
  LO("||", "OR"), LO("&&", "AND"),
  LO("#>", "OBJ_START"), LO("<#", "OBJ_END"),
  LO("?>", "NULL_ELSE"), LO("<-", "GET"),
  LO(">-", "SET")
).groupBy { it.value.length }

data class LO(val value: String, val name: String)

data class Token(var type: String, var value: String, val length: Int, val line: Int) {
  private var parsed: Boolean = false
  override fun toString(): String = "Token of type $type with value $value"
  fun parse(): Token {
    if (parsed) return this
    when (type) {
      "NUMBER" -> value = when {
        value.startsWith("0b") -> value.removePrefix("0b").toInt(radix = 2).toString()
        value.startsWith("0x") -> value.removePrefix("0x").toInt(radix = 16).toString()
        else -> value.toDouble().toString()
      }

      "ORDER" -> type = keywords.find { value.lowercase() == it }?.uppercase() ?: "IDENTIFIER"
      "OPERATOR" -> type = operators[value[0]] ?: "ERROR-XD"
      "LOGIC" -> {
        type = logic[value.length]?.find { value == it.value }?.name ?: "ERROR-WRONG-LOGIC"
      }
    }
    if (type == "ERROR-WRONG-LOGIC") throw Error("[$line] Wrong logic type, value: '$value   '")
    return this.also { parsed = true }
  }
}

class RuntimeError(message: String?) : RuntimeException(message)