fun main( /* args: Array<String> */) {

  /* var poArr = parseCode(""); */

  parseExamples()
}

fun parseCode(code: String, debug: Boolean = false): MutableList<ParserObject> {
  var tokens = scanTokens(code)
  val temp = Parser(tokens).parse()
  if(debug) {for (x in temp) println(x)}
  return Optimizer(temp).parse()
}

fun parseExamples() {
  println("\nLet example:")
  println("Code: " + "'let x = 0;'")
  var temp = parseCode("let x = 0;")
  for (x in temp) println(x)

  println("\nIf else example:")
  println("Code: " + "'if(true) print(0 + 1 + 2); else doSth();'")
  temp = parseCode("if(true) print(0 + 1 + 2); else doSth();")
  for (x in temp) println(x)

  println("\nIdk example:")
  println("Code: " + "'print(true < false);'")
  temp = parseCode("print(true < false);")
  for (x in temp) println(x)

  println("\nWhile example:")
  println("Code: " + """'while(true) print(x);'""")
  temp = parseCode("""while(true) print(x);""")
  for (x in temp) println(x)

  println("\nFor example:")
  println("Code: " + """'for(let x = 0; x < 0; x = x + 1) print(x);'""")
  temp = parseCode("""for(let x = 0; x < 0; x = x + 1) print(x);""")
  for (x in temp) println(x)

  println("\nPrint with let example:")
  println("Code: " + "'let x = 0; print(x);'")
  temp = parseCode("let x = 0; print(x);")
  for (x in temp) println(x)

  println("\nReasign example:")
  println("Code: " + "'let x = 1; x = x + 1; print(x);'")
   temp = parseCode("let x = 1; x = x + 1; print(x);")
  for (x in temp) println(x)

  println("\nFunction example:")
  println("Code: " + "'fun x(y) {return y + 1;}'")
   temp = parseCode("fun x(y) {return y + 1;}")
  for (x in temp) println(x)

  println("\n2 Let example [ERROR]:")
  println("Code: " + """'let x = 0; let x = 1;'""")
  try {
    temp = parseCode("""let x = 0; let x = 1;""")
  } catch(err: Error) { println(err.message) }

  println("\nUndefined variable example [ERROR]:")
  println("Code: " + """'x = x + 1;'""")
  try {
    temp = parseCode("""x = x + 1;""")
  } catch(err: Error) { println(err.message) }
}
