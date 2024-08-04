import std.*
import java.io.File
import java.lang.System.*
import java.net.URL

class Interpreter {
  var environment = Environment.asGlobal().enclose()
  private var code = mutableListOf<Node>()

  fun run(code: List<Node>, timeTaken: Boolean = true) {
    this.code.addAll(code)
    val time = currentTimeMillis().toDouble()
    while (this.code.size != 0) runNode(advance())
    if (timeTaken) println("Successfully run code in ${(currentTimeMillis().toDouble() - time) / 1000.0}s")
  }

  fun runNode(node: Node): VariableValue<*> = when (node) {
    is LetNode -> runLet(node)
    is FunctionNode -> runFunction(node)
    is IfNode -> runIf(node)
    is BlockNode -> runBlock(node).second
    is BinaryNode -> runBinary(node)
    is CallNode -> runCall(node)
    is AssignNode -> runAssign(node)
    is VariableNode -> environment[node.name].unwrap()
    is LiteralNode -> runLiteral(node)
    is UnaryNode -> {
      if (node.op == "-") (-toNumber(node.expr)).toVariableValue()
      else toBoolean(node.expr).not().toVariableValue()
    }

    is ReturnNode -> {
      val os = environment["System"].unwrap()
      val func = (os as ObjectValue<*>).value["writeLine"]
      (func as FunctionValue<*>).value.call(
        this, mutableListOf(runNode(node.expr))
      )
    }

    is ObjectNode -> runObject(node)
    is DotNode -> runDot(normaliseDot(node))
    is ForNode -> runFor(node)
    is RangeNode -> runRange(node)
    is ClassNode -> runClass(node)
    is ArrayNode -> runArray(node)
    is ImportNode -> runImport(node)
    is OptionalNode -> runOptional(node)
    else -> {
      throw Error("Unexpected node type '${node}'")
    }
  }

  private fun runOptional(node: OptionalNode): OptionValue {
    val body: VariableValue<*>?

    try {
      body = runNode(node.body)
    } catch (err: RecoverableError) {
      println("Suppressed error: ${err.message}")
      return OptionValue(Option(null))
    }

    return OptionValue(Option(body))
  }

  private fun runLiteral(node: LiteralNode): VariableValue<*> = when (node.type) {
    VariableType.String -> StringValue(node.value as String)
    VariableType.Boolean -> BooleanValue(node.value as Boolean)
    VariableType.Number -> NumberValue(node.value as Double)
    else -> OptionValue.None
  }

  private fun runLet(node: LetNode): VariableValue<*> {
    node.value ?: return environment.define(node.name, OptionValue(Option(null), node.const))

    val value = when (val tempNode = node.value as Node) {
      is LiteralNode -> runLiteral(tempNode)
      is FunctionNode -> runFunction(tempNode, declare = false)
      is ObjectNode -> runObject(tempNode)
      is DotNode -> runDot(normaliseDot(tempNode))
      is ArrayNode -> runArray(tempNode)
      is RangeNode -> runRange(tempNode)
      is CallNode -> runCall(tempNode)
      is ForNode -> runFor(tempNode)
      is IfNode -> runIf(tempNode)
      else -> OptionValue.None
    }

    return value.also { environment.define(node.name, if (node.const) it.inverseMutability() else it) }
  }

  private fun runFunction(
    node: FunctionNode, declare: Boolean = true
  ): FunctionValue<*> = FunctionData(
    FunctionDeclaration(node.name ?: "\$Anonymous\$", node.parameters, node.body)
  ).let {
    FunctionValue(it).let { fVal ->
      if (declare && node.name != null) environment.define(node.name!!, fVal) as FunctionValue<*> else fVal
    }
  }

  private fun runIf(node: IfNode) = when {
    toBoolean(node.condition) -> runEnclosed(node.thenBranch)
    node.elseBranch != null -> runEnclosed(node.elseBranch as Node)
    else -> OptionValue.None
  }

  fun runBlock(
    node: BlockNode, map: MutableMap<String, VariableValue<*>>? = null
  ): Pair<BlockReturn, VariableValue<*>> {
    environment = environment.enclose()
    if (map != null) for (x in map.keys) environment.define(x, map[x]!!)

    var breakReason = BlockReturn.End
    var returnValue: VariableValue<*> = OptionValue.None

    for (childNode in node.body) {
      when (childNode) {
        is ReturnNode -> {
          breakReason = BlockReturn.Return
          returnValue = runNode(childNode.expr)
          break
        }

        is BreakNode, is ContinueNode -> {
          breakReason = if (childNode is BreakNode) BlockReturn.Break else BlockReturn.Continue
          break
        }

        is BlockNode -> {
          val blockResult = runBlock(childNode)
          if (blockResult.first != BlockReturn.End) {
            breakReason = blockResult.first
            returnValue = blockResult.second
            break
          }
        }

        is IfNode -> {
          val condition = toBoolean(childNode.condition)

          if (condition) {
            val blockResult = runBlock(
              if (childNode.thenBranch is BlockNode) childNode.thenBranch as BlockNode
              else BlockNode(mutableListOf(childNode.thenBranch))
            )

            if (blockResult.first != BlockReturn.End) {
              breakReason = blockResult.first
              returnValue = blockResult.second
              break
            }
          } else {
            if (childNode.elseBranch != null) {
              val blockResult = runBlock(
                if (childNode.elseBranch is BlockNode) childNode.elseBranch as BlockNode
                else BlockNode(mutableListOf(childNode.elseBranch!!))
              )

              if (blockResult.first != BlockReturn.End) {
                breakReason = blockResult.first
                returnValue = blockResult.second
                break
              }
            }
          }
        }

        else -> returnValue = runNode(childNode)
      }
    }

    environment = environment.enclosing!!
    return Pair(breakReason, returnValue)
  }

  private fun runBinary(node: BinaryNode): VariableValue<*> = when (node.op) {
    "OR", "AND" -> {
      toBoolean(node.left).let {
        if (it && node.op == "AND") toBoolean(node.right)
        else it || toBoolean(node.right)
      }.toVariableValue()
    }

    "XOR" -> (toBoolean(node.left) xor toBoolean(node.right)).toVariableValue()
    "MINUS", "STAR", "SLASH", "PLUS" -> {
      val left = toNumber(node.left)
      val right = toNumber(node.right)
      if (right == 0.0 && node.op == "SLASH") throw RuntimeError("Do not divide by zero (0).")
      when (node.op) {
        "MINUS" -> left - right
        "PLUS" -> left + right
        "SLASH" -> left / right
        "STAR" -> left * right
        else -> 0.0
      }.toVariableValue()
    }

    "GREATER", "GREATER_EQUAL", "LESS", "LESS_EQUAL" -> {
      val left = toNumber(node.left)
      val right = toNumber(node.right)

      when (node.op) {
        "GREATER" -> left > right
        "GREATER_EQUAL" -> left >= right
        "LESS" -> {
          left < right
        }

        "LESS_EQUAL" -> left <= right
        else -> false
      }.toVariableValue()
    }

    "EQUAL_EQUAL" -> (runNode(node.left) == runNode(node.right)).toVariableValue()
    "BANG_EQUAL" -> (runNode(node.left) != runNode(node.right)).toVariableValue()

    "NULL_ELSE" -> {
      val outcome = runNode(node.left)

      if ((outcome as? OptionValue)?.isNone == true) runNode(node.right) else outcome
    }

    else -> OptionValue.None
  }

  private fun runCall(node: CallNode): VariableValue<*> =
    when (val value = environment[node.name].expect("Can't find '${node.name}' in current scope")) {
      is ClassValue<*>, is FunctionValue<*> -> (value.value as PartSCallable).call(this, node.args.map { runNode(it) })

      else -> throw RuntimeError("Variable '${node.name}' is not a function ${environment.toPartsInstance()}")
    }


  private fun runAssign(node: AssignNode): VariableValue<*> {
    node.value ?: return OptionValue.None

    val value = when (node.value) {
      is LiteralNode -> OptionValue.Some(runLiteral(node.value as LiteralNode))
      is FunctionNode -> OptionValue.Some(runFunction(node.value as FunctionNode, declare = false))
      is ObjectNode -> OptionValue.Some(runObject(node.value as ObjectNode))
      else -> runNode(node.value as Node)
    }

    return value.also { environment.assign(node.name, it) }
  }

  private fun runObject(node: ObjectNode): ObjectValue<*> {
    val obj = mutableListOf<EnclosedValue>()

    for (key in node.map.keys) {
      obj.add(EnclosedValue(key.toVariableValue(), runNode(node.map[key]!!), false))
    }

    return ObjectValue(PartSInstance(obj))
  }

  private fun resolveDot(node: DotNode, accessFrom: Any, objectKey: VariableValue<*>): VariableValue<*> {
    return when (accessFrom) {
      is VariableValue<*> -> when (accessFrom) {
        is ObjectValue<*> -> {
          val value = accessFrom.value[objectKey.prettyPrint()]

          return when (node.accessTo) {
            is CallNode -> when (value) {
              is FunctionValue<*> -> {
                environment = environment.enclose()
                environment.copy(accessFrom.value.entries)
                val returnValue = value.value.call(this, (node.accessTo as CallNode).args.map { runNode(it) })
                environment = environment.enclosing!!
                returnValue
              }

              else -> throw RuntimeError("Member '${objectKey}' is not a function")
            }

            is BinaryNode -> {
              node.accessTo = runBinary(node.accessTo as BinaryNode).let { LiteralNode(it.type, it.value) }

              value
            }

            is LiteralNode, is VariableNode -> value
            else -> throw RuntimeError("Unknown node ${node.accessTo}")
          }
        }

        is ClassValue<*> -> {
          val value = accessFrom.value[objectKey.prettyPrint()]
          val rawValueEntry =
            (accessFrom.value as PartSInstance).entries.find { objectKey.prettyPrint() == it.key.prettyPrint() }

          val classInitiated = toBoolean(accessFrom.value["#init"])

          if (rawValueEntry?.static == false) {
            if (!classInitiated) {
              throw RuntimeError("Class not initiated, define property as static")
            }
          }

          if (value is ClassValue<*> && value.value.superclass is PartsAccessor) {
            environment = environment.enclose()
            environment.copy(value.value.entries)
            val returnValue = (environment["getter"].unwrap() as PartSCallable).call(this, listOf())
            environment = environment.enclosing!!
            return returnValue
          }

          if (node.accessTo is CallNode) {
            return when (value) {
              is FunctionValue<*> -> {
                environment = environment.enclose()
                environment.copy(accessFrom.value.entries)
                val returnValue = value.value.call(this,
                  (node.accessTo as CallNode).args.map { runNode(it) })
                environment = environment.enclosing!!
                returnValue
              }

              else -> throw RuntimeError("Member '${objectKey}' is not a function")
            }
          }
          return value
        }

        is ArrayValue -> {
          val value = accessFrom.value.std[objectKey.prettyPrint()]

          if (value is ClassValue<*> && value.value.superclass is PartsAccessor) {
            val localValue = value.value

            environment = environment.enclose()
            environment.copy(localValue.entries)
            val returnValue = (environment["getter"].unwrap() as FunctionValue<*>).value.call(this, listOf())
            environment = environment.enclosing!!
            return returnValue

          }
          if (node.accessTo is CallNode) {
            return when (value) {
              is FunctionValue<*> -> {
                environment = environment.enclose()
                val returnValue = value.value.call(this,
                  (node.accessTo as CallNode).args.map { runNode(it) })
                environment = environment.enclosing!!
                returnValue
              }

              else -> throw RuntimeError("Member '${objectKey}' is not a function")
            }
          }
          return value
        }

        is OptionValue -> {
          val value = accessFrom.std[objectKey.prettyPrint()]

          if (value is ClassValue<*> && value.value.superclass is PartsAccessor) {
            val localValue = value.value

            environment = environment.enclose()
            environment.copy(localValue.entries)
            val returnValue = (environment["getter"].unwrap() as FunctionValue<*>).value.call(this, listOf())
            environment = environment.enclosing!!
            return returnValue

          }
          if (node.accessTo is CallNode) {
            return when (value) {
              is FunctionValue<*> -> {
                environment = environment.enclose()
                val returnValue = value.value.call(this,
                  (node.accessTo as CallNode).args.map { runNode(it) })
                environment = environment.enclosing!!
                returnValue
              }

              else -> throw RuntimeError("Member '${objectKey}' is not a function")
            }
          }
          return value
        }

        is StringValue -> {
          val value = accessFrom.std[objectKey.prettyPrint()]

          if (value is ClassValue<*> && value.value.superclass is PartsAccessor) {
            val localValue = value.value

            environment = environment.enclose()
            environment.copy(localValue.entries)
            val returnValue = (environment["getter"].unwrap() as FunctionValue<*>).value.call(this, listOf())
            environment = environment.enclosing!!
            return returnValue

          }
          if (node.accessTo is CallNode) {
            return when (value) {
              is FunctionValue<*> -> {
                environment = environment.enclose()
                val returnValue = value.value.call(this,
                  (node.accessTo as CallNode).args.map { runNode(it) })
                environment = environment.enclosing!!
                returnValue
              }

              else -> throw RuntimeError("Member '${objectKey}' is not a function")
            }
          }
          return value
        }

        is ResultValue -> {
          val value = accessFrom.std[objectKey.prettyPrint()]

          if (value is ClassValue<*> && value.value.superclass is PartsAccessor) {
            val localValue = value.value

            environment = environment.enclose()
            environment.copy(localValue.entries)
            val returnValue = (environment["getter"].unwrap() as FunctionValue<*>).value.call(this, listOf())
            environment = environment.enclosing!!
            return returnValue

          }
          if (node.accessTo is CallNode) {
            return when (value) {
              is FunctionValue<*> -> {
                environment = environment.enclose()
                val returnValue = value.value.call(this, (node.accessTo as CallNode).args.map { runNode(it) })
                environment = environment.enclosing!!
                returnValue
              }

              else -> throw RuntimeError("Member '${objectKey}' is not a function")
            }
          }
          return value
        }

        else -> {
          throw RuntimeError("STD methods for type: '${accessFrom.type}' are not implemented")
        }
      }

      is PartSInstance -> {
        val value = accessFrom[objectKey.prettyPrint()]

        if (node.accessTo is CallNode) {
          return when (value.type) {
            VariableType.Function -> {
              environment = environment.enclose()
              environment.copy(accessFrom.entries)
              val returnValue = (value.value as PartSCallable).call(this,
                (node.accessTo as CallNode).args.map { runNode(it) })
              environment = environment.enclosing!!
              returnValue
            }

            else -> throw RuntimeError("Member '${objectKey}' is not a function")
          }
        }
        return value
      }

      else -> OptionValue.None
    }
  }

  private fun normaliseDot(node: DotNode): DotNode {
    val history = mutableListOf<Node>()
    var currentNode: DotNode? = node

    while (currentNode != null) {
      history.add(currentNode.accessFrom)

      currentNode = if (currentNode.accessTo is DotNode) {
        currentNode.accessTo as DotNode
      } else {
        history.add(currentNode.accessTo)
        null
      }

    }

    val accessTo = history.removeLast()
    var accessFrom = history.removeLast()

    while (history.isNotEmpty()) accessFrom = DotNode(history.removeLast(), accessFrom)

    return DotNode(accessFrom, accessTo)
  }

  private fun runDot(node: DotNode): VariableValue<*> {
    val accessFrom = runNode(node.accessFrom)

    when (node.accessTo) {
      is AssignNode -> {
        val nodeAT = node.accessTo as AssignNode

        return when (accessFrom) {
          is ObjectValue<*> -> {
            accessFrom.value[nodeAT.name] = runNode(nodeAT.value!!)
            //Object assign basically = 'obj.x = 0'
            OptionValue.None
          }

          is ClassValue<*> -> {

            val getFrom = accessFrom.value
            val nodeAt = (node.accessTo as AssignNode)
            val prop = getFrom[nodeAt.name]

            if (prop is ClassValue<*> && prop.value.superclass is PartsAccessor) {
              val propSetter = prop.value["setter"]

              environment = environment.enclose()
              environment.copy(getFrom.entries)
              val returnValue = (propSetter as FunctionValue<*>).value.call(
                this, listOf(OptionValue(Option(nodeAt.value?.let {
                  runNode(nodeAt.value!!)
                })))
              )
              environment = environment.enclosing!!
              prop.value["#default"] = returnValue
            }

            OptionValue.None

          }

          else -> OptionValue.None
        }
      } // object assign
      is ObjectAssignNode -> {
        val objectKey = runNode((node.accessTo as ObjectAssignNode).key)
        val nodeAT = node.accessTo as ObjectAssignNode


        return when (accessFrom) {
          is ObjectValue<*> -> {
            accessFrom.value[objectKey.prettyPrint()] = runNode(nodeAT.value!!)
            //Object assign basically = 'obj.x = 0'
            OptionValue.None
          }

          is ClassValue<*> -> {
            val getFrom = accessFrom.value
            val prop = getFrom[objectKey.prettyPrint()]

            if (prop is ClassValue<*> && prop.value.superclass is PartsAccessor) {
              environment = environment.enclose()
              environment.copy(getFrom.entries)
              val returnValue = (prop.value["setter"] as FunctionValue<*>).value.call(
                this, listOf(OptionValue(Option(nodeAT.value?.let { runNode(nodeAT.value!!) })))
              )
              environment = environment.enclosing!!
              prop.value["#default"] = returnValue
            }

            OptionValue.None

          }

          else -> OptionValue.None
        }
      } //object assign but chad
      is CallNode -> {
        val objectKey = (node.accessTo as CallNode).name

        return resolveDot(node, accessFrom, objectKey.toVariableValue())

      } // method call
      is LiteralNode -> when (val accessor = runNode(node.accessTo)) {
        is BooleanValue, is NumberValue, is StringValue -> return resolveDot(node, accessFrom, accessor)
        else -> throw RuntimeError("Wrong accessor")
      } // object access
      is VariableNode -> {
        val objectKey = (node.accessTo as VariableNode).name

        return resolveDot(node, accessFrom, objectKey.toVariableValue())
      }//object access
      is ObjectNode, is RangeNode, is ArrayNode, is UnaryNode -> throw RuntimeError("Invalid access")
      else -> return resolveDot(node, accessFrom, runNode(node.accessTo))
    }
  }

  private fun runFor(node: ForNode): VariableValue<*> {
    val iteratorObject = when (val temp = runNode(node.range)) {
      is ClassValue<*> -> temp.value
      is ObjectValue<*> -> temp.value
      is ArrayValue -> temp.value.createIterator()
      is StringValue -> temp.createIterator()
      else -> throw RuntimeError("Only objects, classes and arrays (of chars - strings) can be for loop condition")
    }

    val hasNextRaw = iteratorObject["hasNext"]
    if (hasNextRaw is OptionValue && hasNextRaw.isNone) throw RuntimeError("There is no 'hasNext' function on the object")
    if (hasNextRaw !is FunctionValue<*>) throw RuntimeError("'hasNext' is not a function")
    val hasNext = hasNextRaw.value as FunctionData

    val nextRaw = iteratorObject["next"]
    if (nextRaw is OptionValue && nextRaw.isNone) throw RuntimeError("There is no 'next' function on the object")
    if (nextRaw !is FunctionValue<*>) throw RuntimeError("'next' is not a function")
    val next = nextRaw.value as FunctionData

    val codeBlock = node.body as BlockNode

    var `continue` = toBoolean(iteratorObject.runWithContext(hasNext, interpreter, emptyList()))

    while (`continue`) {
      val current = iteratorObject.runWithContext(next, interpreter, emptyList())
      iteratorObject["current"] = current

      val blockResult = runBlock(codeBlock, mutableMapOf("it" to current))

      when (blockResult.first) {
        BlockReturn.Continue -> continue
        BlockReturn.Break -> break
        BlockReturn.Return -> return blockResult.second
        else -> Unit
      }

      `continue` = toBoolean(iteratorObject.runWithContext(hasNext, interpreter, emptyList()))
    }

    return iteratorObject["current"]
  }

  private fun runRange(node: RangeNode): VariableValue<*> {
    val bottom = toNumber(node.bottom) - 1
    val top = toNumber(node.top)

    val hasNext = FunctionData(
      FunctionDeclaration(
        "hasNext", listOf(), Parser(scanTokens("{ return (bottom < top) && (current < top);}")).parse()[0] as BlockNode
      )
    )

    val next = FunctionData(
      FunctionDeclaration(
        "next", listOf(), Parser(scanTokens("{ return current + 1; }")).parse()[0] as BlockNode
      )
    )

    val clazz = PartSClass(
      "Range", mutableListOf(), mutableMapOf(
        "bottom" to bottom.toVariableValue(),
        "top" to top.toVariableValue(),
        "current" to bottom.toVariableValue(),
        "hasNext" to hasNext.toVariableValue(),
        "next" to next.toVariableValue()
      ).entries.map { EnclosedValue(it.key.toVariableValue(), it.value, false) }.toMutableList()
    )
    clazz.superclass = PartsIterable()

    return clazz.toVariableValue()
  }

  private fun runClass(node: ClassNode): ClassValue<PartSClass> {
    environment = environment.enclose()

    var superclass: OptionValue = OptionValue.None

    if (node.superclass != null) {
      superclass = OptionValue.Some(environment[node.superclass!!])
      if (superclass.value.unwrap() !is ClassValue<*>) throw RuntimeError("Super class '${node.superclass}' isn't valid class to inherit")
    }

    for (member in node.parameters) {
      if (member is ExtendedProperty) {
        val defaultValue = member.value?.let { runNode(member.value) } ?: OptionValue.None

        val getter = FunctionData(
          FunctionDeclaration(
            "getter",
            listOf(DefaultParameter("value", VariableNode("#default"))),
            member.getter ?: BlockNode(mutableListOf(ReturnNode(VariableNode("value"))))
          )
        )

        val setter = FunctionData(
          FunctionDeclaration(
            "setter",
            listOf(FunctionParameter("newValue"), DefaultParameter("defaultValue", VariableNode("#default"))),
            member.setter ?: BlockNode(mutableListOf(ReturnNode(AssignNode("#default", VariableNode("newValue")))))
          )
        )

        val memberClass = PartSClass(
          member.name + "Wrapper", elements = mutableMapOf(
            "#default" to defaultValue, "getter" to getter.toVariableValue(), "setter" to setter.toVariableValue()
          ).entries.map { EnclosedValue(it.key.toVariableValue(), it.value, false) }.toMutableList()
        )

        memberClass.superclass = PartsAccessor()
        environment.define(member.name, memberClass.call(this, listOf()))
      } else {
        runNode(member.value!!)
      }
    }

    for (func in node.functions) runFunction(func, declare = true)

    val classBody = environment.values.entries.map {
      EnclosedValue(
        it.key.toVariableValue(), it.value, node.parameters.find { env -> env.name == it.key }?.static ?: false
      )
    }.toMutableList()
    environment = environment.enclosing!!

    val clazz = PartSClass(node.name, node.stubs.map { it.name }.toMutableList(), classBody)
    if (superclass.isSome) clazz.superclass = superclass.unwrap().value as PartSClass

    return clazz.toVariableValue().also { environment.define(node.name, it) }
  }

  private fun runEnclosed(node: Node) = runBlock(if (node is BlockNode) node else BlockNode(mutableListOf(node))).second
  private fun runArray(node: ArrayNode) = node.data.map { runNode(it) }.toVariableValue()

  private fun runImport(node: ImportNode): OptionValue {
    if (node.import.isEmpty()) return OptionValue.None

    val content = if (node.from.startsWith("http")) {
      URL(node.from).openStream().use { it.readAllBytes() }.let { String(it, Charsets.UTF_8) }
    } else {
      File(fileCWD, node.from).readText()
    }

    try {
      val newEnv = Interpreter().let {
        it.run(content)
        it.environment
      }

      if (node.import[0] is ImportAllIdentifier && node.import.size == 1) {
        node.import[0].let { all ->
          if (all.alias == null) {
            environment.copy(newEnv.values)
          } else {
            environment.define(all.alias, newEnv.toPartsInstance().toVariableValue())
          }
        }
      } else {
        val exclude = mutableListOf<String>()

        node.import.forEach { import ->
          if (import is ImportAllIdentifier) {
            newEnv.values.filter { it.key in exclude }.toMutableMap().let {
              //In this context it will be always defined
              import.alias?.let { alias ->
                environment.define(alias,
                  PartSInstance(it.entries.map { EnclosedValue(it.key.toVariableValue(), it.value, false) }
                    .toMutableList()).toVariableValue())
              }
            }
          } else {
            val resolvedEntity = newEnv[import.name]
            exclude.add(import.name)
            environment.define(import.alias ?: import.name, resolvedEntity)
          }

        }
      }
    } catch (err: Error) {
      error("There was error inside imported file details: ${err.message}")
    }

    return OptionValue.None
  }

  private fun toBoolean(node: Node) = toBoolean(runNode(node))

  private fun toBoolean(thing: Any?): Boolean = when (thing) {

    is VariableValue<*> -> when (thing) {
      is ArrayValue -> thing.value.toList().isNotEmpty()
      is BooleanValue -> thing.value
      is ClassValue<*> -> true
      is FunctionValue<*> -> true
      is NumberValue -> thing.value > 0
      is OptionValue -> if (thing.isNone) false else toBoolean(thing.unwrap())
      is StringValue -> thing.value.isNotBlank()
      is ObjectValue<*> -> thing.value.isNotEmpty()
      is ResultValue -> if (thing.isError) false else toBoolean(thing.unwrap())
    }

    is FunctionData -> true
    is PartSInstance -> thing.isNotEmpty()
    else -> false
  }

  private fun toNumber(node: Node) = toNumber(runNode(node))

  fun toNumber(thing: Any?): Double = when (thing) {
    is VariableValue<*> -> when (thing) {
      is ArrayValue -> thing.value.toList().size
      is BooleanValue -> if (thing.value) 1 else 0
      is ClassValue<*> -> 1
      is FunctionValue<*> -> 1
      is NumberValue -> thing.value
      is OptionValue -> if (thing.isNone) 0 else toNumber(thing.value.unwrap())
      is StringValue -> thing.value.toDoubleOrNull() ?: thing.value.length
      is ObjectValue<*> -> thing.value.size
      is ResultValue -> if (thing.isError) 0 else toNumber(thing.unwrap())
    }

    is FunctionData -> 1
    is PartSInstance -> thing.size
    else -> 0
  }.toDouble()

  private fun advance() = code.removeFirst()

  fun runTests() {
    val tests = listOf(
      """let x = 0;""",
      """"I like wolfs";""",
      """if (true) print("Hack"); else print("I'm hiding!");""",
      """fun fib(n) { if (n <= 1) return 1; else return fib(n - 1) + fib(n - 2); } print(fib(5));""",
      """fun fight(should = false) { if(should) print("We fight guys"); else print("We don't fight guys");} fight()""",
      """let obj = #> x to 0 <#; print(obj.x);""",
      """let obj = #> x to 0 <#; obj.x = 1; print(obj.x);""",
      """let obj = #> x to 0 <#; obj.x = 1; print(obj);""",
      """let range = 1 to 3; for(range) { print(it); }""",
      """class clazz {fun func() {print("this is called inside class");}} let clas = clazz(); clas.func();""",
      """class clazz {fun init() {print("this is called on init");}} clazz();""",
      """class clazz {let x = 0;} print(clazz.x);""",
      """class clazz {fun func() {print("this is called inside class");}} let clas = clazz(); clas.func();""",
      """class clazz {let x = 0;fun init() {print(x);}} clazz();""",
      """class clazz {fun init() {print("hi");}} class clay: clazz{fun init() {super.init();}} clay();""",
      """class clazz {implement next;} class clazz: clazz{}""",
      """class ci: Iterable { let top = 10; let bottom = 0; let current = 0;
                fun hasNext() { return (bottom < top) && (current < top); }
                fun next() { return current + 1; }}
                for(ci()) print(it);""",
      """[2].getOrDefault(1, 5)"""
    )

    for (test in tests) {
      println("\nExecuting now: $test\n")
      //Clearing variables and stuff
      environment = Environment.asGlobal()
      code = mutableListOf()
      // Parse code
      val tokens = scanTokens(test)
      val parsed = Parser(tokens).parse()
      for (parsedNode in parsed) println(parsedNode)

      try {
        this.run(parsed, false)
      } catch (e: Exception) {
        println("ERROR: ${e.message}")
      }
    }
  }
}

fun Interpreter.run(code: String, timeTaken: Boolean = false) {
  val tokens = scanTokens(code)
  val parsed = Parser(tokens).parse()

  this.run(parsed, timeTaken)
}

fun scan(code: String) {
  val tokens = scanTokens(code)

  println("Tokens:")

  tokens.forEach(::println)

  val parsed = Parser(tokens).parse()

  println("Parsed AST:")

  parsed.forEach(::println)
}

interface PartSCallable {
  fun call(interpreter: Interpreter, arguments: List<VariableValue<*>>): VariableValue<*>
}

open class EnclosedValue(val key: VariableValue<*>, val value: VariableValue<*>, val static: Boolean)

operator fun MutableList<EnclosedValue>.get(key: String) = this.find { key == it.key.prettyPrint() }?.value

operator fun MutableList<EnclosedValue>.set(key: String, value: VariableValue<*>) {
  val temp = EnclosedValue(key.toVariableValue(), value, false)
  if (this[key] == null) {
    this.add(temp)
  } else {
    this[this.indexOfFirst { it.key.prettyPrint() == key }] = temp
  }
}

operator fun MutableList<EnclosedValue>.contains(key: String) = this.find { key == it.key.prettyPrint() } != null

enum class BlockReturn { Return, Break, Continue, End }