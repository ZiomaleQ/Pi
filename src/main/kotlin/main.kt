import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

val interpreter = Interpreter()

fun main(args: Array<String>) {
  if (args.isEmpty()) runREPL()
  else {
    if (args[0] == "test") {
      interpreter.runTests()
    } else {
      val file = File(args[0])
      val code = file.readText()
      interpreter.run(code)
    }
  }
}

fun runREPL() {
  val input = InputStreamReader(System.`in`)
  val reader = BufferedReader(input)

  println("Start line with: \n- ':{' to see ast \n- '><' to measure time\n- '<<' to print output")

  while (true) {
    print("> ")
    val line = reader.readLine() ?: break
    try {
      when (line.take(2)) {
        ":{" -> scan(line.substring(2))
        "><" -> interpreter.run(line.substring(2), true)
        "<<" -> interpreter.run("return " + line.substring(2))
        else -> interpreter.run(line, false)
      }
    } catch (err: RuntimeError) {
      println(err.message)
      continue
    }
  }
}