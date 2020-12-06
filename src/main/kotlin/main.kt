fun main(args: Array<String>) {
        val tokens = scanTokens(
        """
    if(n < 1) print(n);
    |else if(n > 1) print(0);
    |print(x);
    """.trimMargin()
            /* "print(0 + 1 + 2);" */
            /* "if(true) print(0 + 1 + 2); else fuckme();" */
        )
        val temp = Parser(tokens).parse()
        val temp1 = Checker(temp).parse()
        val temp2 = Optimizer(temp).parse()
        for (x in temp2) println(x)
}
