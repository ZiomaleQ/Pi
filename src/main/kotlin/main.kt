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

    try {
        println("\nConst example")
        println("Code: 'const x = 0; x = 1;'")
        Interpreter().run(parseCode("const x = 0; x = 1;"))
    } catch (err: Exception) {
        if (err is RuntimeError) println("There was error, but it was expected: ${err.message}")
        else throw  err
    }

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

    println("\nVararg print")
    println("Code: " + """print("I like ding dongs", 1234, "You're mean");""")
    Interpreter().run(parseCode("""print("I like ding dongs", 1234, "You're mean");"""))

    println("\nDefault parameters")
    println("Code: " + """fun x(y = 0) {print(y);} x(); x(1);""")
    Interpreter().run(parseCode("""fun x(y = 0) {print(y);} x(); x(1);"""))

    println("\nPrint empty obj")
    println("Code: " + """let obj = #> <#; print(obj);""")
    Interpreter().run(parseCode("""let obj = #> <#; print(obj);"""))

    println("\nPrint obj with key")
    println("Code: " + """let obj = #> x to 0 <#; print(obj);""")
    Interpreter().run(parseCode("""let obj = #> x to 0 <#; print(obj);"""))

    println("\nPrint key of an obj")
    println("Code: " + """let obj = #> x to 0 <#; print(obj.x);""")
    Interpreter().run(parseCode("""let obj = #> x to 0 <#; print(obj.x);"""))
}