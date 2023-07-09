fun scanTokens(code: String): MutableList<Token> {
  return Tokenizer(code.toCharArray()).tokenizeCode()
}

val OPERATORS = listOf('+', '-', '/', '*', ';', '[', ']', '(', ')', '{', '}', '.', ':', ',')
val LOGIC = listOf('|', '&', '>', '<', '!', '#', '-', '=')

data class Rule(
  var isSingle: Boolean,
  var name: String,
  var additionalCheck: ((String) -> Boolean)? = null,
  var rule: (Char) -> Boolean,
)

val rules = mutableListOf(
  Rule(true, "OPERATOR") { OPERATORS.contains(it) },
  Rule(
    false,
    "NUMBER",
    additionalCheck = {
      it.startsWith('0') || it.all { ch -> ch.isDigit() || ch == '.' }
    }
  ) { it.isDigit() || it == '.' || it == 'x' || it == 'b' },
  Rule(false, "LOGIC") { LOGIC.contains(it) },
  Rule(false, "ORDER") { it.isLetter() },
  Rule(false, "NEW-LINE") { it == '\n' || it == '\r' }
)

class Tokenizer(private val code: CharArray) {
  private var current = 0
  private var line = 1

  fun tokenizeCode(): MutableList<Token> {
    val tokens = mutableListOf<Token>()
    while (current < code.size) {
      var found = false
      var expr: String
      if (peek() == '"') {
        current++
        if (peek() == '"') {
          current++
          found = tokens.add(Token("STRING", "", 0, line))
        } else {
          expr = "${peekNext()}"
          while (current < code.size && peek() != '"') expr += peekNext()
          if (peekNext() != '"') throw Error("[$line] Unterminated string")
          found = tokens.add(Token("STRING", expr, expr.length, line))
        }
      }

      for (rule in rules) {
        if (found) break

        if (rule.isSingle) {
          if (rule.rule.invoke(peek()) && rule.additionalCheck?.invoke(peek().toString()) != false) {
            found = tokens.add(Token(rule.name, "${peekNext()}", 1, line))
            break
          }
        } else {
          if (rule.rule.invoke(peek())) {
            expr = code[current++].toString()
            if (rule.additionalCheck?.invoke(expr).let { it == false }) {
              current -= 1
            } else {
              while (current < code.size && rule.rule.invoke(peek()) && rule.additionalCheck?.invoke(expr)
                  .let { it == null || it == true }
              )
                expr = "$expr${peekNext()}"
              if (rule.name == "NEW-LINE") line++
              found = tokens.add(Token(rule.name, expr, expr.length, line))
              break
            }
          }
        }
      }

      if (!found) current++
    }
    return tokens.filter { it.type != "NEW-LINE" }.map { it.parse() }.toMutableList()
  }

  private fun peek(): Char = code[current]
  private fun peekNext(): Char = code[current++]
}
