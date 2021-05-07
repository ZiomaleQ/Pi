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
        match("FUN") -> {
            val name = if (peek().type == "IDENTIFIER") advance() else {
                advance(); Token("IDENTIFIER", "\$Anonymous\$", "\$Anonymous\$".length, peek().line)
            }
            consume("LEFT_PAREN", "Expected '(' after function declaration, got ${peek().value}")

            val params = mutableListOf<String>()

            if (peek().type != "RIGHT_PAREN") {
                do {
                    params.add(consume("IDENTIFIER", "Only identifiers in function args got '${peek().value}'").value)
                } while (match("COMMA"))
            }
            consume("RIGHT_PAREN", "Expected ')' after arguments, got ${peek().value}")
            consume("LEFT_BRACE", "Expect '{' before function body, got ${peek().value}")
            FunctionNode(name = name.value, parameters = params, body = block())
        }
        else -> statement()
    }

    private fun statement(): Node = when {
        match("IF") -> {
            consume("LEFT_PAREN", "Expect '(' after 'if'.")
            val condition = expression()
            consume("RIGHT_PAREN", "Expect ')' after if condition.")
            IfNode(
                condition = condition,
                thenBranch = statement(),
                elseBranch = if (match("ELSE")) statement() else null
            )
        }
        match("LEFT_BRACE") -> block()
        match("RETURN") -> ReturnNode(expressionStatement())
        else -> expressionStatement()
    }

    private fun block(): BlockNode {
        val statements: MutableList<Node> = ArrayList()
        while (peek().type != "RIGHT_BRACE" && peek().type != "EOF") statements.add(declaration())
        consume("RIGHT_BRACE", "Expect '}' after block, got ${peek().value}")
        return BlockNode(body = statements)
    }

    private fun expressionStatement(): Node =
        expression().let { consume("SEMICOLON", "Expected semicolon after expression, got '${peek().value}'"); it }

    private fun expression(): Node {
        var expr = primary()
        expr = when {
            match(
                "AND", "OR", "GREATER", "GREATER_EQUAL", "LESS", "PLUS",
                "LESS_EQUAL", "BANG_EQUAL", "EQUAL_EQUAL", "SLASH", "STAR", "MINUS"
            ) -> BinaryNode(op = lastToken.type, left = expr, right = expression())
            match("BANG", "MINUS") -> UnaryNode(op = lastToken.value, expr = expression())
            match("EQUAL") -> when (expr) {
                is VariableNode -> AssignNode(name = expr.name, value = expression())
                else -> error("Invalid assignment target")
            }
            else -> {
                loop@ while (true) {
                    expr = when {
                        match("LEFT_PAREN") -> finishCall(expr as VariableNode)
                        else -> break@loop
                    }
                }
                expr
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
        match("FALSE", "TRUE") -> LiteralNode(type = "Boolean", value = (lastToken.type == "TRUE"))
        match("GROUPING") -> expression().let {
            consume("RIGHT_PAREN", "Expect ')' after expression.")
            it
        }
        match("NIL") -> LiteralNode(type = "Void", value = null)
        match("NUMBER", "STRING") -> LiteralNode(type = lastToken.type.toCamelCase(), value = lastToken.value)
        else -> error("Expect expression. ${peek().value}")
    }

    private fun consume(type: String, message: String): Token = if (match(type)) lastToken else error(message)
    private fun match(vararg types: String): Boolean = if (peek().type in types) true.also { advance() } else false
    private fun advance(): Token = code.removeFirst().also { lastToken = it }
    private fun error(message: String): Nothing = throw Error("[${lastToken.line} line] $message")
    private fun peek(): Token = if (code.size > 0) code[0] else getEOT()
    private fun getEOT(): Token = Token("EOT", "EOT", 3, lastToken.line)
}