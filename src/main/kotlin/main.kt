import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

val interpreter = Interpreter()

fun main(args: Array<String>) {
    if (args.isEmpty()) runREPL()
    else {
        val file = File(args[0])
        val code = file.readText()
        interpreter.run(code)
    }
}

fun runREPL() {
    val input = InputStreamReader(System.`in`)
    val reader = BufferedReader(input)

    while (true) {
        print("> ")
        val line = reader.readLine() ?: break
        interpreter.run(line, false)
    }
}