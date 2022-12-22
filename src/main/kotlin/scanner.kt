fun scanTokens(code: String): MutableList<Token> {
  return Tokenizer(code).tokenizeCode()
}

class Tokenizer(private val code: String) {
  private var rules: MutableList<Rule> = mutableListOf(
    Rule(true, "OPERATOR", "+-/*;[](){}.!:,"),
    Rule(false, "NUMBER", "[0-9]|[\\.]"),
    Rule(false, "LOGIC", "[\\||\\&|\\=|\\>|\\<|\\!\\#]"),
    Rule(false, "ORDER", "[A-Z]+"),
    Rule(false, "NEW-LINE", "[\\r\\n]+")
  )
  private var current = 0
  private var line = 1

  fun tokenizeCode(): MutableList<Token> {
    val code = this.code.toCharArray()
    val tokens = mutableListOf<Token>()
    val rulesLocal = rules.groupBy { it.isSingle }
    while (current < code.size) {
      var found = false
      var expr: String
      if (peek() == '"') {
        current++
        if (peek() == '"') {
          current++
          expr = ""
          found = tokens.add(Token("STRING", expr, 0, line))
        } else {
          expr = "${peekNext()}"
          while (current < code.size && peek() != '"') expr += peekNext()
          if (peekNext() != '"') throw Error("[$line] Unterminated string")
          found = tokens.add(Token("STRING", expr, expr.length, line))
        }
      }
      for (rule in rulesLocal[true] ?: listOf()) {
        if (found) break
        if (rule.rule.toCharArray().find { it == peek() } != null) {
          found = tokens.add(Token(rule.name, "${peekNext()}", 1, line))
          break
        }
      }
      for (rule in rulesLocal[false] ?: listOf()) {
        if (found) break
        if (rule.test(code[current])) {
          expr = code[current++].toString().also { found = true }
          while (current < code.size && rule.test(peek())) expr = "$expr${peekNext()}"
          if (rule.name == "NEW-LINE") line++
          tokens.add(Token(rule.name, expr, expr.length, line))
        }
      }
      if (!found) current++
    }
    return tokens.filter { it.type != "NEW-LINE" }.map { it.parse() }.toMutableList()
  }

  private fun peek(): Char = code[current]
  private fun peekNext(): Char = code[current++]

  private data class Rule(var isSingle: Boolean, var name: String, var rule: String) {
    override fun toString(): String = "[ $isSingle, '$name', /$rule/ ]"
    fun test(check: Any): Boolean = rule.toRegex(RegexOption.IGNORE_CASE).matches("$check")
  }
}
