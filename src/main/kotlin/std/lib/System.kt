package std.lib

import std.PartSNativeClass
import std.toVariableValue
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

val SystemFS = PartSNativeClass().apply {
  addNativeMethod("getCWD") { _, _ ->
    Paths.get("").toAbsolutePath().absolutePathString().toVariableValue()
  }

}.toVariableValue()

val SystemSTD = PartSNativeClass().apply {
  addNativeProp("Fs", SystemFS)
}.toVariableValue()