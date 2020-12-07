fun main(args: Array<String>) {
        val tokens = scanTokens(
        /* """
    if(n < 1) print(n);
    |else if(n > 1) print(0);
    |print(x);
    """.trimMargin() */
            /* "print(0 + 1 + 2);" */
            /* "if(true) print(0 + 1 + 2); else fuckme();" */
            /* "print(true < false);" */
            """while(true) print("XDD");"""
        )
        val temp = Parser(tokens).parse()
        val temp1 = Optimizer(temp).parse()
        for (x in temp1) println(x)
}
