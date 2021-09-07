package std

import Interpreter
import PartSNativeClass
import RuntimeError
import VariableValue
import toVariableValue

fun createStringStd(): PartSNativeClass {
    return PartSNativeClass().apply {
        addNativeMethod("concat") { _: Interpreter, list: List<VariableValue?> ->
            list.mapNotNull { it?.value }.joinToString("").toVariableValue()
        }

        addNativeMethod("charAt") { interpreter: Interpreter, list: List<VariableValue?> ->
            if (list.size >= 2) {
                if (list[0] == null || list[0]?.type != "String") throw RuntimeError("First argument has to be string")
                else "${(list[0]!!.value as String)[interpreter.toNumber(list[1]).toInt()]}".toVariableValue()
            } else throw RuntimeError("Expected at least two arguments in 'charAt'")
        }

        addNativeMethod("toString") { _: Interpreter, list: List<VariableValue?> ->
            if (list.isEmpty()) "".toVariableValue()
            else list[0]!!.value.toString().toVariableValue()
        }
    }
}