class Coder(code: MutableList<ParserObject>) {
    fun toByteCode(): VMConfig {
        return VMConfig(listOf(), listOf())
    }

}

data class VMConfig(var variables: List<Any>, var code: List<ByteCode>) {
    override fun toString() =
        "Vars: ${variables.joinToString { "    $it\n" }}\nCode: ${code.joinToString { "    $it\n" }}"
}

data class ByteCode(var instruction: String, var data: List<Any>) {
    override fun toString() = "Codename: $instruction ${data.joinToString { "$it" }}"
}

data class Variable(var name: String, var data: Any) {
    override fun toString(): String = "Name = $name, data = $data"
}