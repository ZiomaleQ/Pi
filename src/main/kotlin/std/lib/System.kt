package std.lib

import EnclosedValue
import RecoverableError
import std.*
import java.io.File
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

val SystemFS = PartSNativeClass().apply {
  addNativeMethod("getCWD") { _, _ ->
    Paths.get("").toAbsolutePath().absolutePathString().toVariableValue()
  }

  addNativeMethod("getDirContent") { _, arguments ->
    val dirPathObj = arguments.getOrNull(0) ?: throw RecoverableError("No path passed")

    val dirPath = if (dirPathObj is StringValue) dirPathObj.value else throw RecoverableError("It's not a valid path")

    val files = File(dirPath).listFiles() ?: throw RecoverableError("No files in dir")

    ArrayValue(ArrayData(files.map { file ->
      ObjectValue(
        PartSInstance(
          mutableMapOf(
            "name" to file.name.toVariableValue(),
            "isDir" to file.isDirectory.toVariableValue()
          ).entries.map { EnclosedValue(it.key.toVariableValue(), it.value, false) }.toMutableList()
        )
      )
    }.toMutableList()))
  }

  addNativeMethod("readFile") { _, arguments ->
    val filePathObj = arguments.getOrNull(0) ?: throw RecoverableError("No path passed")

    val filePath =
      if (filePathObj is StringValue) filePathObj.value else throw RecoverableError("It's not a valid file")

    StringValue(File(filePath).readText())
  }
}.toVariableValue()

val SystemSTD = PartSNativeClass().apply {
  addNativeProp("Fs", SystemFS)

  addNativeMethod("readLine") { _, _ ->
    System.console().readLine().toVariableValue()
  }

  addNativeMethod("writeLine") { _, arguments ->
    println(arguments.joinToString(separator = " ") {
      if (it is OptionValue) {
        if (it.isNone) {
          "Option.None"
        } else {
          it.unwrapAll().prettyPrint()
        }
      } else {
        it.prettyPrint()
      }
    })
    OptionValue.None
  }

  addNativeMethod("currentTime") { _, _ ->
    NumberValue((System.nanoTime() / 1000L).toDouble())
  }
}.toVariableValue()