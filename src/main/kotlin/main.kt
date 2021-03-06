fun main( /* args: Array<String> */) {
    runExamples()
}

fun parseCode(code: String, debug: Boolean = false): MutableList<Node> {
    val tokens = scanTokens(code)
    if (debug) {
        for (x in tokens) println(x); }
    val parsed = Parser(tokens).parse()
    if (debug) {
        for (x in parsed) println(x); }
    return parsed
}

fun runExamples() {
    println("\nLet example")
    println("Code: 'let x = 0;'")
    Interpreter().run(parseCode("let x = 0;"))

    println("\nReassign example")
    println("Code: 'let x = 0; x = x + 1; print(x)'")
    Interpreter().run(parseCode("let x = 0; x = x + 1; print(x);"))

    println("\nIf else example:")
    println("Code: " + "'if(true) print(0 + 1 + 2); else doSth();'")
    Interpreter().run(parseCode("if(true) print(0 + 1 + 2); else doSth();"))

    println("\nIdk example:")
    println("Code: " + "'print(true < false);'")
    Interpreter().run(parseCode("print(true < false);"))

    println("\nPrint with let example:")
    println("Code: " + "'let x = 0; print(x);'")
    Interpreter().run(parseCode("let x = 0; print(x);"))

    println("\nFunction example:")
    println("Code: " + "'fun x(y) {return y + 1;} print(x(0));'")
    Interpreter().run(parseCode("fun x(y) {return y + 1;} print(x(0));"))

    println("\nVarrarg print")
    println("Code: " + """print("I like ding dongs", 1234, "You're mean");""")
    val x = parseCode("""print("I like ding dongs", 1234, "You're mean");""")
    Interpreter().run(x)
}

fun parseExamples() {
    println("\nLet example:")
    println("Code: " + "'let x = 0;'")
    var temp = parseCode("let x = 0;")
    for (x in temp) println(x)

    println("\nReassign example:")
    println("Code: " + "'let x = 1; x = x + 1; print(x);'")
    temp = parseCode("let x = 1; x = x + 1; print(x);")
    for (x in temp) println(x)

    println("\nIf else example:")
    println("Code: " + "'if(true) print(0 + 1 + 2); else doSth();'")
    temp = parseCode("if(true) print(0 + 1 + 2); else doSth();")
    for (x in temp) println(x)

    println("\nIdk example:")
    println("Code: " + "'print(true < false);'")
    temp = parseCode("print(true < false);")
    for (x in temp) println(x)

    println("\nPrint with let example:")
    println("Code: " + "'let x = 0; print(x);'")
    temp = parseCode("let x = 0; print(x);")
    for (x in temp) println(x)

    println("\nFunction example:")
    println("Code: " + "'fun x(y) {return y + 1;}'")
    temp = parseCode("fun x(y) {return y + 1;}")
    for (x in temp) println(x)
}
