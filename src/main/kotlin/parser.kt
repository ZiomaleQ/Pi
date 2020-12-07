class Parser(private val code: MutableList<Token>) {
    private var lastToken: Token = Token("x", "x", 0, 0)
    private var tokens: MutableList<ParserObject> = mutableListOf()
    fun parse(): MutableList<ParserObject> {
        lastToken = getEOT()
        try {
            while (code.size != 0) {
                tokens.add(declaration())
            }
        } catch (err: Error) {
            println(err.message)
        }
        return tokens
    }

    private fun declaration(): ParserObject = when {
        match("LET") -> letStmt()
        else -> statement()
    }

    private fun letStmt(): ParserObject {
        val name = consume("IDENTIFIER", "Expected name after let keyword got '${peek().value}' (${peek().type})")
        val init = if (match("EQUAL")) expression() else null
        consume("SEMICOLON", "Expect ';' after variable declaration.")
        return ParserObject("Let", mutableMapOf("name" to name.value, "initializer" to init))
    }

    private fun statement(): ParserObject = when {
        match("IF") -> {
            consume("LEFT_PAREN", "Expect '(' after 'if'.")
            val condition = expression()
            consume("RIGHT_PAREN", "Expect ')' after if condition.")
            val thenBranch = statement()
            val elseBranch = if (match("ELSE")) statement() else null
            ParserObject("If", mutableMapOf("condition" to condition, "thenBranch" to thenBranch, "elseBranch" to elseBranch))
        }
        match("WHILE") -> {
          consume("LEFT_PAREN", "Expect '(' after 'while'.")
          val condition = expression()
          consume("RIGHT_PAREN", "Expect ')' after while condition.")
          val body = statement()
          ParserObject("While", mutableMapOf("condition" to condition, "body" to body))
        }
        match("FOR") -> {
          consume("LEFT_PAREN", "Expect '(' after 'for'.")
          var initializer = when(peek().type) {
            "SEMICOLON" -> null
            "LET" -> {advance(); letStmt()}
            else -> expressionStatement()
          }

          val condition = if(peek().type != "SEMICOLON") expression()
            else ParserObject("Literal", mutableMapOf("type" to "Boolean", "value" to true))
          consume("SEMICOLON", "Expect ';' after loop condition.")

          val increment = if(peek().type != "RIGHT_PAREN") expression() else null
          consume("RIGHT_PAREN", "Expect ')' after for loop.")

          var body = if(increment == null) statement() else ParserObject("Block", mutableMapOf("body" to listOf(statement(), increment)))

          body = ParserObject("While", mutableMapOf("condition" to condition, "body" to body))

          if(initializer == null) body else ParserObject("Block", mutableMapOf("body" to listOf(initializer, body)))
        }
        else -> expressionStatement()
    }

    private fun expressionStatement(): ParserObject {
        val temp = expression()
        consume("SEMICOLON", "Expected semicolon after expression, got '${peek().value}'")
        return ParserObject("Expression", mutableMapOf("expr" to temp))
    }

    private fun expression(): ParserObject {
        var expr = primary()
        expr = when {
            match(
                "AND", "OR", "GREATER", "GREATER_EQUAL", "LESS", "PLUS",
                "LESS_EQUAL", "BANG_EQUAL", "EQUAL_EQUAL", "SLASH", "STAR", "MINUS"
            ) -> ParserObject("Binary", mutableMapOf("left" to expr, "operator" to lastToken.type, "right" to expression()))
            match("BANG", "MINUS") -> ParserObject(
                "Unary", mutableMapOf("token" to lastToken.value as String, "right" to expression())
            )
            match("EQUAL") -> when (expr.name) {
                "Variable" -> ParserObject("Assign", mutableMapOf("name" to lastToken.value, "value" to expression()))
                "Get" -> ParserObject(
                    "Set", mutableMapOf("parent" to expr["object"], "prop" to expr["name"], "value" to expression())
                )
                else -> error("Invalid assignment target")
            }
            else -> {
                loop@ while (true) {
                    expr = when {
                        match("LEFT_PAREN") -> finishCall(expr)
                        match("DOT") -> ParserObject(
                            "Get",
                            mutableMapOf("object" to expr, "name" to consume("IDENTIFIER", "Expect property name after '.'."))
                        )
                        else -> break@loop
                    }
                }
                expr
            }
        }
        return expr
    }

    private fun finishCall(callee: ParserObject): ParserObject {
        val args = mutableListOf<ParserObject>()

        if (peek().type != "RIGHT_PAREN") {
            do {
                args.add(expression())
            } while (match("COMMA"))
        }
        consume("RIGHT_PAREN", "Expected ')' after arguments, got ${peek().value}")
        return ParserObject("Call", mutableMapOf("callee" to callee, "arguments" to args))
    }

    private fun primary(): ParserObject = when {
        match("IDENTIFIER") -> ParserObject("Variable", mutableMapOf("name" to lastToken.value))
        match("FALSE", "TRUE") -> ParserObject(
            "Literal", mutableMapOf("type" to "Boolean", "value" to (lastToken.type == "TRUE"))
        )
        match("GROUPING") -> expression().let {
            consume("RIGHT_PAREN", "Expect ')' after expression.")
            ParserObject("Grouping", mutableMapOf("expr" to it))
        }
        match("NIL") -> ParserObject("Literal", mutableMapOf("type" to "Void", "value" to null))
        match("NUMBER", "STRING") -> ParserObject(
            "Literal", mutableMapOf("type" to lastToken.type.toCamelCase(), "value" to lastToken.value)
        )
        else -> error("Expect expression. ${peek().value}")
    }

    private fun consume(type: String, message: String): Token = if (match(type)) lastToken else error(message)
    private fun match(vararg types: String): Boolean = if (peek().type in types) true.also { advance() } else false
    private fun advance(): Token = code.removeFirst().also { lastToken = it }
    private fun error(message: String): Nothing = throw Error("[${lastToken.line} line] $message")
    private fun peek(): Token = if (code.size > 0) code[0] else getEOT()
    private fun getEOT(): Token = Token("EOT", "EOT", 3, lastToken.line)
}
