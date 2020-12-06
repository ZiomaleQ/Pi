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
        val name = consume("VARIABLE", "Expected name after keyword got '${peek().value}'")
        val init = if (match("EQUAL")) expression() else null
        consume("SEMICOLON", "Expect ';' after variable declaration.")
        return ParserObject("Let", mapOf("name" to name.value, "initializer" to init))
    }

    private fun statement(): ParserObject = when {
        match("IF") -> {
            consume("LEFT_PAREN", "Expect '(' after 'if'.")
            val condition = expression()
            consume("RIGHT_PAREN", "Expect ')' after if condition.")
            val thenBranch = statement()
            val elseBranch = if (match("ELSE")) statement() else null
            ParserObject("If", mapOf("condition" to condition, "thenBranch" to thenBranch, "elseBranch" to elseBranch))
        }
        else -> expressionStatement()
    }

    private fun expressionStatement(): ParserObject {
        val temp = expression()
        consume("SEMICOLON", "Expected semicolon after expression, got '${peek().value}'")
        return ParserObject("Expression", mapOf("expr" to temp))
    }

    private fun expression(): ParserObject {
        var expr = primary()
        expr = when {
            match(
                "AND", "OR", "GREATER", "GREATER_EQUAL", "LESS", "PLUS",
                "LESS_EQUAL", "BANG_EQUAL", "EQUAL_EQUAL", "SLASH", "STAR", "MINUS"
            ) -> ParserObject("Binary", mapOf("left" to expr, "operator" to lastToken.type, "right" to expression()))
            match("BANG", "MINUS") -> ParserObject(
                "Unary", mapOf("token" to lastToken.value as String, "right" to expression())
            )
            match("EQUAL") -> when (expr.name) {
                "Variable" -> ParserObject("Assign", mapOf("name" to lastToken.value, "value" to expression()))
                "Get" -> ParserObject(
                    "Set", mapOf("parent" to expr["object"], "prop" to expr["name"], "value" to expression())
                )
                else -> error("Invalid assignment target")
            }
            else -> {
                loop@ while (true) {
                    expr = when {
                        match("LEFT_PAREN") -> finishCall(expr)
                        match("DOT") -> ParserObject(
                            "Get",
                            mapOf("object" to expr, "name" to consume("IDENTIFIER", "Expect property name after '.'."))
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
        return ParserObject("Call", mapOf("callee" to callee, "arguments" to args))
    }

    private fun primary(): ParserObject = when {
        match("IDENTIFIER") -> ParserObject("Variable", mapOf("name" to lastToken.value))
        match("FALSE", "TRUE") -> ParserObject(
            "Literal", mapOf("type" to "Boolean", "value" to (lastToken.type == "TRUE"))
        )
        match("GROUPING") -> expression().let {
            consume("RIGHT_PAREN", "Expect ')' after expression.")
            ParserObject("Grouping", mapOf("expr" to it))
        }
        match("NIL") -> ParserObject("Literal", mapOf("type" to "Void", "value" to null))
        match("NUMBER", "STRING") -> ParserObject(
            "Literal", mapOf("type" to lastToken.type.toCamelCase(), "value" to lastToken.value)
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
