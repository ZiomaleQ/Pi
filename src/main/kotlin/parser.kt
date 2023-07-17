import std.DefaultParameter
import std.FunctionParameter
import std.VariableType

enum class ParserScope {
  TOP_LEVEL,
  EXPRESSION
}

class Parser(private val code: MutableList<Token>) {
  private var lastToken: Token = Token("", "", 0, 0)
  private var scope = ParserScope.TOP_LEVEL

  fun parse(): MutableList<Node> {
    lastToken = getEOT()
    val output = mutableListOf<Node>()

    try {
      while (code.isNotEmpty()) {
        output.add(node())
      }
    } catch (err: Error) {
      println(code.take(2))
      println(err.message)
    }

    return output
  }

  private fun node(): Node = when (scope) {
    ParserScope.TOP_LEVEL -> parseTopLevel()
    ParserScope.EXPRESSION -> parseExpression()
  }

  private fun runInScope(wantedScope: ParserScope): Node {
    val lastScope = scope.also { scope = wantedScope }

    val result = node()

    return result.also { scope = lastScope }
  }

  private fun parseTopLevel(): Node = when {
    match("LET", "CONST") -> {
      val op = lastToken.type
      val name = consume(
        "IDENTIFIER", "Expected name after ${op.lowercase()} keyword got '${peek().value}' (${peek().type})"
      )
      val init = if (match("EQUAL")) runInScope(ParserScope.EXPRESSION) else null

      LetNode(name = name.value, value = init, op == "CONST")
    }

    match("CLASS") -> {
      val name = consume("IDENTIFIER", "Expected name after class keyword got '${peek().value}' (${peek().type})")

      val superclass = if (match("COLON")) {
        consume(
          "IDENTIFIER", "Expected identifier after class inheritance operator got '${peek().value}' (${peek().type})"
        ).value
      } else null

      consume("LEFT_BRACE", "Expected '{' after class declaration, got ${peek().value}")

      val functions = mutableListOf<FunctionNode>()
      val parameters = mutableListOf<DefaultProperty>()
      val stubs = mutableListOf<ImplementNode>()

      var last: Node? = null
      var lastName: String? = null

      while (peek().type != "RIGHT_BRACE") {
        when (peek().type) {
          "LET", "CONST" -> {
            val value = runInScope(ParserScope.EXPRESSION).also { last = it }.also { lastName = null }
            parameters.add(DefaultProperty((value as LetNode).name, value))
          }

          "GET" -> {
            advance()

            val getterName = if (!(last is LetNode || last is BlockNode)) {
              consume("IDENTIFIER", "Expected name after 'get' operator '${peek().value}' (${peek().type})").value
            } else {
              when (last) {
                is LetNode -> (last as LetNode).name
                is BlockNode -> lastName ?: ""
                // shouldn't be possible
                else -> "\$Anonymous\$"
              }
            }

            val body = when {
              match("EQUAL") -> BlockNode(ReturnNode(runInScope(ParserScope.EXPRESSION)))
              match("LEFT_BRACE") -> block()
              else -> error("Invalid expression in get return")
            }

            val index = parameters.indexOfFirst { it.name == getterName }

            if (index == -1) {
              parameters.add(ExtendedProperty(getterName, defaultValue = null, getter = body))
            } else {
              val temp = parameters[index]
              val value = if (temp.value is LetNode) temp.value.value else temp.value

              parameters[index] = ExtendedProperty(
                getterName,
                value,
                getter = body,
                (temp as? ExtendedProperty)?.setter
              )
            }

            lastName = getterName

          }

          "SET" -> {
            advance()

            val setterName = if (!(last is LetNode || last is BlockNode)) {
              consume("IDENTIFIER", "Expected name after 'get' operator '${peek().value}' (${peek().type})").value
            } else {
              when (last) {
                is LetNode -> (last as LetNode).name
                is BlockNode -> lastName ?: ""
                // shouldn't be possible
                else -> "\$Anonymous\$"
              }
            }

            val body = when {
              match("EQUAL") -> BlockNode(ReturnNode(runInScope(ParserScope.EXPRESSION)))
              match("LEFT_BRACE") -> block()
              else -> error("Invalid expression set return")
            }

            val index = parameters.indexOfFirst { it.name == setterName }

            if (index == -1) {
              parameters.add(ExtendedProperty(setterName, defaultValue = null, setter = body))
            } else {
              val temp = parameters[index]
              parameters[index] = ExtendedProperty(
                setterName,
                (temp as? ExtendedProperty)?.value,
                (temp as? ExtendedProperty)?.getter,
                setter = body
              )
            }

            lastName = setterName
          }

          "FUN" -> {
            val function = parseExpression() as FunctionNode
            function.name ?: error("Expected name after function declaration, got: ${peek().value}")
            functions.add(function.also { last = it })
          }

          "IMPLEMENT" -> {
            advance()
            val funName = consume(
              "IDENTIFIER", "Expected name after let keyword got '${peek().value}' (${peek().type})"
            )
            consume("SEMICOLON", "Expected ';' after must-implement method declaration.")
            stubs.add(ImplementNode(funName.value).also { last = it })
          }

          "STATIC" -> {
            advance()
            if (listOf("LET", "CONST").contains(peek().type)) {
              val value = runInScope(ParserScope.EXPRESSION).also { last = it }.also { lastName = null }
              parameters.add(DefaultProperty((value as LetNode).name, value, true))
            }
          }

          else -> error("Wrong token, expected method / member declaration")
        }
      }

      consume("RIGHT_BRACE", "Expect '}' after class body, got ${peek().value}")

      ClassNode(name.value, functions, parameters, stubs, superclass)
    }

    match("IMPORT") -> when {
      match("STAR") -> {
        var alias: String? = null
        if (match("AS")) alias = consume("IDENTIFIER", "Expected identifier, got '${peek().value}'").value
        consume("FROM", "Excepted 'from' after import specifier, got '${peek().value}'")
        runInScope(ParserScope.EXPRESSION).let {
          if ((it !is LiteralNode) || ((it as? LiteralNode)?.type != VariableType.String)) {
            error("Import path can only be a string, got '${peek().value}'")
          }
          ImportNode(import = mutableListOf(ImportAllIdentifier(alias)), from = it.value as String)
        }
      }

      match("LEFT_BRACE") -> {
        val identifiers = mutableListOf<ImportIdentifier>()
        if (peek().type != "RIGHT_BRACE") {
          do {
            val import = when {
              match("IDENTIFIER") -> lastToken.value
              match("STAR") -> "#Default"
              else -> error("Expected identifier or star, got '${peek().value}'")
            }

            var alias: String? = null
            if (import == "#Default" && peek().type != "AS") error("Expected 'as' after *, got '${peek().value}'")
            if (match("AS")) alias = consume("IDENTIFIER", "Expected identifier, got '${peek().value}'").value
            identifiers.add(ImportIdentifier(import, alias))
          } while (match("COMMA"))
          consume("RIGHT_BRACE", "Expected '}' after import body, got '${peek().value}'")
        }

        consume("FROM", "Excepted 'from' after import specifier, got '${peek().value}'")
        primary().let {
          if (it !is LiteralNode) error("Only string literals in import specifier, got '${peek().value}'")
          if (it.type != VariableType.String) error("Import path can only be a string, got '${peek().value}'")

          ImportNode(import = identifiers, from = it.value as String)
        }
      }

      else -> error("Excepted 'from' after import specifier, got '${peek().value}'")
    }

    match("FOR") -> {
      consume("LEFT_PAREN", "Expect '(' after 'for'.")
      val condition = runInScope(ParserScope.EXPRESSION)
      consume("RIGHT_PAREN", "Expect ')' after for condition.")
      ForNode(
        condition,
        runInScope(ParserScope.EXPRESSION).let { if (it is BlockNode) it else BlockNode(mutableListOf(it)) }
      )
    }

    match("RETURN") -> ReturnNode(runInScope(ParserScope.EXPRESSION))
    match("CONTINUE") -> ContinueNode
    match("BREAK") -> BreakNode
    else -> parseExpression()
  }

  private fun parseExpression(): Node = when {
    match("IF") -> {
      consume("LEFT_PAREN", "Expect '(' after 'if'.")
      val condition = runInScope(ParserScope.EXPRESSION)
      consume("RIGHT_PAREN", "Expect ')' after if condition.")
      IfNode(
        condition = condition,
        thenBranch = runInScope(ParserScope.EXPRESSION),
        elseBranch = if (match("ELSE")) runInScope(ParserScope.EXPRESSION) else null
      )
    }

    match("FUN") -> {
      val name = if (peek().type == "IDENTIFIER") advance().value else null

      consume("LEFT_PAREN", "Expected '(' after function declaration, got ${peek().value}")

      val params = mutableListOf<FunctionParameter>()
      var defOnly = false

      if (peek().type != "RIGHT_PAREN") {
        do {
          val argName = consume("IDENTIFIER", "Expected identifier in function parameters, got ${peek().value}").value
          if (match("EQUAL")) {
            params.add(
              DefaultParameter(argName, runInScope(ParserScope.EXPRESSION))
            )
            defOnly = true
          } else {
            if (defOnly) error("Only default parameters after first default parameter")
            params.add(
              FunctionParameter(argName)
            )
          }
        } while (match("COMMA"))
      }

      consume("RIGHT_PAREN", "Expected ')' after arguments, got ${peek().value}")

      FunctionNode(
        name = name,
        parameters = params,
        body = if (match("EQUAL")) BlockNode(ReturnNode(runInScope(ParserScope.EXPRESSION))) else block(true)
      )
    }

    match("LEFT_BRACE") -> block(false)

    else -> {
      var expr = primary()
      while (true) {
        expr = when {
          match(
            "AND",
            "OR",
            "XOR",
            "PLUS",
            "SLASH",
            "STAR",
            "MINUS",
          ) -> {
            val op = lastToken.type
            when {
              match("EQUAL") -> when (expr) {
                is VariableNode -> AssignNode(
                  name = expr.name,
                  value = BinaryNode(op = op, left = expr, right = runInScope(ParserScope.EXPRESSION)).optimize()
                )

                else -> error("Invalid assignment target")
              }

              else -> BinaryNode(
                op = lastToken.type,
                left = expr,
                right = runInScope(ParserScope.EXPRESSION)
              ).optimize()
            }
          }

          match(
            "NULL_ELSE",
            "LESS_EQUAL",
            "BANG_EQUAL",
            "EQUAL_EQUAL",
            "GREATER",
            "GREATER_EQUAL",
            "LESS"
          ) -> BinaryNode(op = lastToken.type, left = expr, right = runInScope(ParserScope.EXPRESSION)).optimize()

          match("EQUAL") -> when (expr) {
            is VariableNode -> AssignNode(name = expr.name, value = runInScope(ParserScope.EXPRESSION))
            is BinaryNode, is LiteralNode -> ObjectAssignNode(key = expr, value = runInScope(ParserScope.EXPRESSION))
            else -> error("Invalid assignment target $expr")
          }

          match("LEFT_PAREN") -> finishCall(expr as VariableNode)
          match("DOT") -> DotNode(expr, runInScope(ParserScope.EXPRESSION))
          match("TO") -> RangeNode(expr, runInScope(ParserScope.EXPRESSION))
          else -> break
        }
      }
      expr
    }
  }.also { if (peek().type == "SEMICOLON") advance() }

  private fun block(checkBrace: Boolean = false): BlockNode {
    if (checkBrace) consume("LEFT_BRACE", "Expect '{' before function body, got ${peek().value}")
    val statements: MutableList<Node> = ArrayList()
    while (peek().type != "RIGHT_BRACE" && peek().type != "EOF") statements.add(runInScope(ParserScope.TOP_LEVEL))
    consume("RIGHT_BRACE", "Expect '}' after block, got ${peek().value}")
    return BlockNode(body = statements)
  }

  private fun finishCall(callee: VariableNode): Node {
    val args = mutableListOf<Node>()

    if (peek().type != "RIGHT_PAREN") {
      do {
        args.add(runInScope(ParserScope.EXPRESSION))
      } while (match("COMMA"))
    }
    consume("RIGHT_PAREN", "Expected ')' after arguments, got ${peek().value}")
    return CallNode(name = callee.name, args = args)
  }

  private fun primary(): Node = when {
    match("IDENTIFIER") -> VariableNode(name = lastToken.value)
    match("FALSE", "TRUE") -> LiteralNode(VariableType.Boolean, value = (lastToken.type == "TRUE"))
    match("LEFT_PAREN") -> runInScope(ParserScope.EXPRESSION).let {
      consume("RIGHT_PAREN", "Expect ')' after expression.")
      it
    }

    match("LEFT_BRACKET") -> {
      val list = mutableListOf<Node>()
      if (peek().type != "RIGHT_BRACKET") {
        do {
          list.add(runInScope(ParserScope.EXPRESSION))
        } while (match("COMMA"))
      }
      consume("RIGHT_BRACKET", "Expected ']' after array body, got ${peek().value}")
      ArrayNode(list)
    }

    match("STRING") -> LiteralNode(VariableType.String, lastToken.value)
    match("NUMBER") -> LiteralNode(VariableType.Number, lastToken.value.toDoubleOrNull() ?: 0.0)
    match("OBJ_START") -> {
      val map = mutableMapOf<String, Node>()
      if (peek().type != "OBJ_END") {
        do {
          val key = if (match("IDENTIFIER", "NUMBER", "STRING")) lastToken.value
          else error("Invalid object key, expected one of: identifier, number, string")

          consume("TO", "Expected 'to' after object key, got ${peek().value}")

          map[key] = runInScope(ParserScope.EXPRESSION)
        } while (match("COMMA"))
      }
      consume("OBJ_END", "Expected '<#' after object body, got ${peek().value}")
      ObjectNode(map)
    }

    match("BANG", "MINUS") -> UnaryNode(op = lastToken.value, expr = primary())

    else -> error("Expected expression. Got ${peek().value}")
  }

  private fun consume(type: String, message: String): Token = if (match(type)) lastToken else error(message)
  private fun match(vararg types: String): Boolean = if (peek().type in types) true.also { advance() } else false
  private fun advance(): Token = code.removeFirst().also { lastToken = it }
  private fun error(message: String): Nothing = throw Error("[${lastToken.line} line] $message")
  private fun peek(): Token = if (code.isNotEmpty()) code[0] else getEOT()
  private fun getEOT(): Token = Token("EOT", "EOT", 3, lastToken.line)
}

fun BinaryNode.optimize(): Node {
  if (!(left is LiteralNode && right is LiteralNode)) {
    return this
  }

  val toBoolean = { lit: LiteralNode ->
    when (lit.type) {
      VariableType.String -> (lit.value as String).isNotBlank()
      VariableType.Boolean -> lit.value as Boolean
      VariableType.Number -> (lit.value as Double) > 0
      else -> false
    }
  }

  val toNumber = { lit: LiteralNode ->
    when (lit.type) {
      VariableType.String -> (lit.value as String).length
      VariableType.Boolean -> if (lit.value as Boolean) 1 else 0
      VariableType.Number -> lit.value as Double
      else -> 0
    }.toDouble()
  }

  return when (op) {
    "OR", "AND" -> {
      LiteralNode(VariableType.Boolean, toBoolean(left as LiteralNode).let {
        if (it && op == "AND") toBoolean(right as LiteralNode)
        else it || toBoolean(right as LiteralNode)
      })
    }

    "XOR" -> LiteralNode(VariableType.Boolean, toBoolean(left as LiteralNode) xor toBoolean(right as LiteralNode))
    "MINUS", "STAR", "SLASH", "PLUS" -> {
      val left = toNumber(left as LiteralNode)
      val right = toNumber(right as LiteralNode)
      if (right == 0.0 && op == "SLASH") throw RuntimeError("Do not divide by zero (0).")
      val ret = when (op) {
        "MINUS" -> left - right
        "PLUS" -> left + right
        "SLASH" -> left / right
        "STAR" -> left * right
        else -> 0.0
      }
      LiteralNode(VariableType.Number, ret)
    }

    "GREATER", "GREATER_EQUAL", "LESS", "LESS_EQUAL" -> {
      val left = toNumber(left as LiteralNode)
      val right = toNumber(right as LiteralNode)

      val ret = when (op) {
        "GREATER" -> left > right
        "GREATER_EQUAL" -> left >= right
        "LESS" -> {
          left < right
        }

        "LESS_EQUAL" -> left <= right
        else -> false
      }

      LiteralNode(VariableType.Boolean, ret)
    }

    else -> this
  }
}