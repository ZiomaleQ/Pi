class Parser(private val code: MutableList<Token>) {
  private var lastToken: Token = Token("x", "x", 0, 0)
  private var tokens: MutableList<Node> = mutableListOf()
  fun parse(): MutableList<Node> {
    lastToken = getEOT()
    try {
      while (code.size != 0) {
        tokens.add(declaration())
      }
    } catch (err: Error) {
      println(code.take(2))
      println(err.message)
    }
    return tokens
  }

  private fun declaration(): Node = when {
    match("LET") -> {
      val name = consume("IDENTIFIER", "Expected name after let keyword got '${peek().value}' (${peek().type})")
      val init = if (match("EQUAL")) {
        if (peek().type == "FUN") declaration() else expression()
      } else null
      if (init != null && init !is FunctionNode) consume("SEMICOLON", "Expected ';' after variable declaration.")
      LetNode(name = name.value, value = init)
    }

    match("CONST") -> {
      val name = consume("IDENTIFIER", "Expected name after let keyword got '${peek().value}' (${peek().type})")
      val init = if (match("EQUAL")) {
        if (peek().type == "FUN") declaration() else expression()
      } else null
      if (init != null && init !is FunctionNode) consume("SEMICOLON", "Expected ';' after variable declaration.")
      ConstNode(name.value, init)
    }

    match("FUN") -> {
      val name = if (peek().type == "IDENTIFIER") advance() else {
        advance(); Token("IDENTIFIER", "\$Anonymous\$", "\$Anonymous\$".length, peek().line)
      }
      consume("LEFT_PAREN", "Expected '(' after function declaration, got ${peek().value}")

      val params = mutableListOf<FunctionParameter>()
      var defOnly = false

      if (peek().type != "RIGHT_PAREN") {
        do {
          val argName = consume("IDENTIFIER", "Expected identifier in function parameters, got ${peek().value}").value
          if (match("EQUAL")) {
            params.add(DefaultParameter(argName, expression()))
            defOnly = true
          } else {
            if (defOnly) error("Only default parameters after first default parameter")
            params.add(FunctionParameter(argName))
          }
        } while (match("COMMA"))
      }
      consume("RIGHT_PAREN", "Expected ')' after arguments, got ${peek().value}")

      if (match("EQUAL")) {
        val body = BlockNode(
          mutableListOf(
            ReturnNode(expressionStatement())
          )
        )
        FunctionNode(name = name.value, parameters = params, body = body)
      } else {
        consume("LEFT_BRACE", "Expect '{' before function body, got ${peek().value}")
        FunctionNode(name = name.value, parameters = params, body = block())
      }
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
      val parameters = mutableListOf<Node>()
      val stubs = mutableListOf<ImplementNode>()

      while (peek().type != "RIGHT_BRACE") {
        when (peek().type) {
          "LET", "CONST" -> {
            parameters.add(declaration())
          }

          "FUN" -> {
            val function = declaration() as FunctionNode
            if (function.name == "\$Anonymous\$") error("Expected name after function declaration, got: ${peek().value}")
            functions.add(function)
          }

          "IMPLEMENT" -> {
            advance()
            val funName = consume(
              "IDENTIFIER", "Expected name after let keyword got '${peek().value}' (${peek().type})"
            )
            consume("SEMICOLON", "Expected ';' after must-implement method declaration.")
            stubs.add(ImplementNode(funName.value))
          }

          else -> error("Wrong token, expected method / member declaration")
        }
      }

      consume("RIGHT_BRACE", "Expect '}' after function body, got ${peek().value}")

      ClassNode(name.value, functions, parameters, stubs, superclass)
    }

    match("IMPORT") -> when {
      match("STAR") -> {
        var alias: String? = null
        if (match("AS")) alias = consume("IDENTIFIER", "Expected identifier, got '${peek().value}'").value
        consume("FROM", "Excepted 'from' after import specifier, got '${peek().value}'")
        primary().let {
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
              match("IDENTIFIER") -> advance().value
              match("STAR") -> "#Default"
              else -> error("Expected identifier or star, got '${peek().value}'")
            }
            var alias: String? = null
            if (import == "#Default" && peek().type != "AS") error("Expected 'as' after *, got '${peek().value}'")
            if (match("AS")) alias = consume("IDENTIFIER", "Expected identifier, got '${peek().value}'").value
            identifiers.add(ImportIdentifier(import, alias))
          } while (match("COMMA"))
        }
        consume("FROM", "Excepted 'from' after import specifier, got '${peek().value}'")
        primary().let {
          if ((it !is LiteralNode) || ((it as? LiteralNode)?.type == VariableType.String)) {
            error("Import path can only be a string, got '${peek().value}'")
          }
          ImportNode(import = identifiers, from = it.value as String)
        }
      }

      else -> error("Excepted 'from' after import specifier, got '${peek().value}'")
    }

    else -> statement()
  }

  private fun statement(): Node = when {
    match("IF") -> {
      consume("LEFT_PAREN", "Expect '(' after 'if'.")
      val condition = expression()
      consume("RIGHT_PAREN", "Expect ')' after if condition.")
      IfNode(
        condition = condition, thenBranch = statement(), elseBranch = if (match("ELSE")) statement() else null
      )
    }

    match("FOR") -> {
      consume("LEFT_PAREN", "Expect '(' after 'for'.")
      val condition = expression()
      consume("RIGHT_PAREN", "Expect ')' after for condition.")
      ForNode(condition, statement().let { if (it is BlockNode) it else BlockNode(mutableListOf(it)) })
    }

    match("LEFT_BRACE") -> block()
    match("RETURN") -> ReturnNode(expressionStatement())
    match("CONTINUE") -> ContinueNode
    match("BREAK") -> BreakNode
    else -> expressionStatement()
  }

  private fun block(): BlockNode {
    val statements: MutableList<Node> = ArrayList()
    while (peek().type != "RIGHT_BRACE" && peek().type != "EOF") statements.add(declaration())
    consume("RIGHT_BRACE", "Expect '}' after block, got ${peek().value}")
    return BlockNode(body = statements)
  }

  private fun expressionStatement(): Node = expression().let {
    match("SEMICOLON")
    it
  }

  private fun expression(): Node {
    var expr = primary()
    while (true) {
      expr = when {
        match(
          "AND",
          "OR",
          "NOR",
          "XOR",
          "GREATER",
          "GREATER_EQUAL",
          "LESS",
          "PLUS",
          "LESS_EQUAL",
          "BANG_EQUAL",
          "EQUAL_EQUAL",
          "SLASH",
          "STAR",
          "MINUS",
          "NULL_ELSE"
        ) -> BinaryNode(op = lastToken.type, left = expr, right = expression())

        match("BANG", "MINUS") -> UnaryNode(op = lastToken.value, expr = expression())
        match("EQUAL") -> when (expr) {
          is VariableNode -> AssignNode(name = expr.name, value = expression())
          else -> error("Invalid assignment target")
        }

        match("LEFT_PAREN") -> finishCall(expr as VariableNode)
        match("DOT") -> DotNode(expr, expression())
        match("TO") -> RangeNode(expr, expression())
        else -> break
      }
    }
    return expr
  }

  private fun finishCall(callee: VariableNode): Node {
    val args = mutableListOf<Node>()

    if (peek().type != "RIGHT_PAREN") {
      do {
        args.add(expression())
      } while (match("COMMA"))
    }
    consume("RIGHT_PAREN", "Expected ')' after arguments, got ${peek().value}")
    return CallNode(name = callee.name, args = args)
  }

  private fun primary(): Node = when {
    match("IDENTIFIER") -> VariableNode(name = lastToken.value)
    match("FALSE", "TRUE") -> LiteralNode(VariableType.Boolean, value = (lastToken.type == "TRUE"))
    match("LEFT_PAREN") -> expression().let {
      consume("RIGHT_PAREN", "Expect ')' after expression.")
      it
    }

    match("LEFT_BRACKET") -> {
      val list = mutableListOf<Node>()
      if (peek().type != "RIGHT_BRACKET") {
        do {
          list.add(expression())
        } while (match("COMMA"))
      }
      consume("RIGHT_BRACKET", "Expected ']' after array body, got ${peek().value}")
      ArrayNode(list)
    }

    match("NIL") -> LiteralNode(VariableType.Void, value = null)
    match(
      "NUMBER",
      "STRING"
    ) -> LiteralNode(
      type = if (lastToken.type.toCamelCase() == "String") VariableType.String else VariableType.Number,
      value = lastToken.value
    )

    match("OBJ_START") -> {
      val map = mutableMapOf<String, Node>()
      if (peek().type != "OBJ_END") {
        do {
          val key = if (match("IDENTIFIER", "NUMBER", "STRING")) lastToken.value
          else error("Invalid object key, expected one of: identifier, number, string")

          consume("TO", "Expected 'to' after object key, got ${peek().value}")

          val exp = expression()
          map[key] = exp
        } while (match("COMMA"))
      }
      consume("OBJ_END", "Expected '<#' after object body, got ${peek().value}")
      ObjectNode(map)
    }

    else -> error("Expected expression. Got ${peek().value}")
  }

  private fun consume(type: String, message: String): Token = if (match(type)) lastToken else error(message)
  private fun match(vararg types: String): Boolean = if (peek().type in types) true.also { advance() } else false
  private fun advance(): Token = code.removeFirst().also { lastToken = it }
  private fun error(message: String): Nothing = throw Error("[${lastToken.line} line] $message")
  private fun peek(): Token = if (code.size > 0) code[0] else getEOT()
  private fun getEOT(): Token = Token("EOT", "EOT", 3, lastToken.line)
}