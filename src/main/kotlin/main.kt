fun main(args: Array<String>) {
        println("If else example:")
        var temp = parseCode("if(true) print(0 + 1 + 2); else doSth();")
        for (x in temp) println(x)

        println("Idk example:")
        var temp = parseCode("print(true < false);")
        for (x in temp) println(x)

        println("While example:")
        var temp = parseCode("""while(true) print(x);""")
        for (x in temp) println(x)

        println("For example:")
        var temp = parseCode("""for(let x = 0; x < 0; x++) print(x);""")
        for (x in temp) println(x)

}

fun parseCode(code: String): MutableList<ParserObject> {
  var tokens = scanTokens(code)
  val temp = Parser(tokens).parse()
  return Optimizer(temp).parse()
}
