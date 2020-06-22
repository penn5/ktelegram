/*
 *     TeleKat (Telegram MTProto client library)
 *     Copyright (C) 2020 Hackintosh Five
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as
 *     published by the Free Software Foundation, either version 3 of the
 *     License, or (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Affero General Public License for more details.
 *
 *     You should have received a copy of the GNU Affero General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package tk.hack5.telekat.generator

sealed class KtWriter(private val output: (String) -> Unit, private val packageName: String, name: String? = null) {
    lateinit var tlName: String
    init {
        name?.let {
            tlName = fixNamespace(name).capitalize()
        }
    }

    private var indentationLevel = 0
    protected fun write(text: String = "", indent: Int = 0) {
        if (indent < 0)
            indentationLevel += indent
        output("    ".repeat(indentationLevel) + text + "\n")
        if (indent > 0)
            indentationLevel += indent
    }

    fun writeHeader(writeSuppressions: Boolean = true) {
        if (writeSuppressions)
            write("@file:Suppress(\"ObjectPropertyName\", \"ClassName\", \"LocalVariableName\", \"MemberVisibilityCanBePrivate\", \"SpellCheckingInspection\")")
        else
            write("@file:Suppress(\"SpellCheckingInspection\")")
        write()
        write()
        write()
        write("package $packageName")
        write()
        write("// Generated code, do not modify.")
        write("// See generator/src/commonMain/KtGenerator.kt")
        write()
    }

    protected fun writeDeclEnd() = write("}", -1)

    abstract fun build()
}

@ExperimentalUnsignedTypes
class MapKtWriter(output: (String) -> Unit, private val data: TLData, packageName: String) : KtWriter(output, packageName) {
    override fun build() {
        writeHeader()
        write("import tk.hack5.telekat.core.tl.TLConstructor")
        write("import tk.hack5.telekat.core.tl.TLObject")
        write()
        write("object TlMappings {", 1)
        // Vectors need special serialization due to the generics, so exclude
        // them from here (they don't implement TLConstructor anyway)
        write("val CONSTRUCTORS = mapOf(" + data.constructors.joinToString {
            "${it.id} to ${fixNamespace(it.name).substringBeforeLast(" ").capitalize()}Object.Companion"
        } + ")")
        write()
        write("class GenericConstructor<T : TLObject<*>> : TLConstructor<T> {", 1)
        write("override fun _fromTlRepr(data: IntArray): Pair<Int, T>? {", 1)
        write("@Suppress(\"UNCHECKED_CAST\")")
        write("return (CONSTRUCTORS[data[0]] as TLConstructor<T>).fromTlRepr(data, false)")
        writeDeclEnd()
        write()
        write("override val id: Int? = null")
        writeDeclEnd()
        writeDeclEnd()
    }
}

@ExperimentalUnsignedTypes
class NormalKtWriter(output: (String) -> Unit, private val entry: TLEntry, packageName: String) : KtWriter(output, packageName, entry.name) {
    private val requestExtension = if (entry.entryType == EntryType.METHOD) "Request" else "Object"
    private var genericType: String? = null

    private fun isSpecial(): Boolean {
        return entry.type == "Vector t"
    }

    private fun buildSpecial(): Boolean {
        if (entry.type == "Vector t") {
            buildVector()
            return true
        }
        return false
    }

    private fun buildVector() {
        write("import org.gciatto.kt.math.BigInteger")
        write("import tk.hack5.telekat.core.tl.*")
        write("import kotlin.jvm.JvmName")
        write()
        write("@JvmName(\"GenericCollectionAsTlObject\")")
        write("fun <E : TLObject<*>>Collection<E>.asTlObject(bare: Boolean) = VectorObject(this, bare)")
        for (primitive in PRIMITIVE_TYPES) {
            if (primitive.value == "BigInteger") continue
            write("@JvmName(\"${primitive.value}CollectionAsTlObject\")")
            write("fun Collection<${primitive.value}>.asTlObject(bare: Boolean) = VectorObject(map { it.asTlObject() }, bare)")
        }
        write("@JvmName(\"BigInteger128CollectionAsTlObject\")")
        write("fun Collection<BigInteger>.asTlObject128(bare: Boolean) = VectorObject(map { it.asTlObject128() }, bare)")
        write("@JvmName(\"BigInteger256CollectionAsTlObject\")")
        write("fun Collection<BigInteger>.asTlObject256(bare: Boolean) = VectorObject(map { it.asTlObject256() }, bare)")
        write()
        write(
            "class VectorObject<E : TLObject<*>>(private val list: Collection<E>, override val bare: Boolean) " +
                    ": TLObject<Collection<E>> {", 1
        )
        write("override fun _toTlRepr(): IntArray =", 1)
        write("intArrayOf(list.size, *list.map { it.toTlRepr().toList() }.flatten().toIntArray())", -1)
        write()
        write("override val native: List<E> = list.toList()")
        write()
        write("override val _id = ${entry.id}")
        write()
        write("override val fields by lazy { list.mapIndexed { index, value -> index.toString() to value }.toMap() }")
        write()
        write("companion object : TLConstructor<VectorObject<*>> {", 1)
        write("// Entirely generic constructor; can return any type of VectorObject without generics-safety")
        write("@ExperimentalUnsignedTypes")
        write("override fun _fromTlRepr(data: IntArray): Pair<Int, VectorObject<*>>? = fromTlRepr<TLObject<*>>(data, true)")
        write("// If null is passed as constructor, a boxed type is assumed. Non-null implies bare.")
        write("@ExperimentalUnsignedTypes")
        write(
            "fun <G : TLObject<*>>fromTlRepr(data: IntArray, bare: Boolean, generic: TLConstructor<G>? = null): Pair<Int, VectorObject<G>>? {",
            1
        )
        write("if (!bare && data[0] != id) return null")
        write("var off = if (bare) 0 else 1")
        write("if (data.size <= off) return Pair(off, VectorObject(listOf(), bare))")
        write("val size = data[off++]")
        write("val ret = mutableListOf<G>()")
        write("var tmp: Pair<Int, G>")
        write("var count = 0")
        write("while (count++ < size) {", 1)
        write("@Suppress(\"UNCHECKED_CAST\")")
        write("tmp = (generic ?: TlMappings.CONSTRUCTORS[data[off]] as TLConstructor<G>).fromTlRepr(data.sliceArray(off until data.size), generic != null)!!")
        write("off += tmp.first")
        write("ret += tmp.second")
        writeDeclEnd()
        write("return Pair(off, VectorObject(ret, bare))")
        writeDeclEnd()
        write()
        write("override val id = ${entry.id}")
        writeDeclEnd()
        write()
        write("class VectorConstructor<G : TLObject<*>>(val generic: TLConstructor<G>?) : TLConstructor<VectorObject<G>> {", 1)
        write("@ExperimentalUnsignedTypes")
        write("override fun _fromTlRepr(data: IntArray): Pair<Int, VectorObject<G>>? = fromTlRepr(data, true, generic)")
        write()
        write("override val id = VectorObject.id")
        writeDeclEnd()
        writeDeclEnd()
    }

    override fun build() {
        if (buildSpecial())
            return
        write()
        writeClassDef()
        writeToTlReprDef()
        writeToTlReprBody()
        writeDeclEnd()
        write()
        writeNativeGetter()
        writeFields()
        write()
        writeIdGetter(false)
        if (entry.entryType == EntryType.CONSTRUCTOR) {
            write()
            writeCompanionStart()
            writeIdGetter(true)
        }
        write()
        if (entry.entryType == EntryType.CONSTRUCTOR) {
            writeFromTlReprDef()
            writeFromTlReprBody()
            writeDeclEnd()
            writeDeclEnd()
        } else {
            writeConstructor()
            write()
        }
        writeDeclEnd()
        write()
        if (entry.name == "true") {
            write("val TrueObject?.native get() = this != null")
            write()
        }
    }

    fun writeImports() {
        if (isSpecial()) return
        write("import tk.hack5.telekat.core.tl.TLObject")
        when (entry.entryType) {
            EntryType.CONSTRUCTOR -> {
                write("import tk.hack5.telekat.core.tl.TLConstructor")
                entry.params.asSequence().map { it.type.toLowerCase().split("?", "<", ">") }
                    .flatten().distinct().map {
                        when (it) {
                            "int" -> listOf("import tk.hack5.telekat.core.tl.IntObject")
                            "long" -> listOf("import tk.hack5.telekat.core.tl.LongObject")
                            "double" -> listOf("import tk.hack5.telekat.core.tl.DoubleObject")
                            "string" -> listOf("import tk.hack5.telekat.core.tl.StringObject")
                            "bytes" -> listOf("import tk.hack5.telekat.core.tl.BytesObject")
                            "int128", "int256" -> {
                                listOf(
                                    "import tk.hack5.telekat.core.tl.asTlObject${it.takeLast(3)}",
                                    "import org.gciatto.kt.math.BigInteger",
                                    "import tk.hack5.telekat.core.tl.Int${it.takeLast(3)}Object"
                                )
                            }
                            else -> null
                        }
                    }.filterNotNull().flatten().distinct().forEach { write(it) }
            }
            EntryType.METHOD -> {
                write("import tk.hack5.telekat.core.tl.TLMethod")
                val type = entry.type.substringAfter("?").substringAfter("<").substringBefore(">")
                if (type in PRIMITIVE_TYPES)
                    write("import tk.hack5.telekat.core.tl.${type.capitalize()}Object")
                entry.params.asSequence().map { it.type.toLowerCase().split("?", "<", ">") }.flatten().map {
                    when (it) {
                        "int128", "int256" -> listOf(
                            "import tk.hack5.telekat.core.tl.asTlObject${it.takeLast(3)}",
                            "import org.gciatto.kt.math.BigInteger"
                        )
                        else -> null
                    }
                }.filterNotNull().flatten().distinct().forEach { write(it) }
            }
        }
        if (entry.params.any { it.type.split("?").last().toLowerCase() in PRIMITIVE_TYPES + Pair("bool", "") })
            write("import tk.hack5.telekat.core.tl.asTlObject")
    }
    private fun writeClassDef() {
        var generic = ""
        val params = (entry.params.mapNotNull {
            if (it.type.first() == '!') {
                genericType = it.type.substring(1)
                generic = "<R: TLObject<*>, $genericType : TLMethod<R>>"
                return@mapNotNull "val ${it.name}: $genericType"
            }
            fixType(it.type)?.let { type ->
                "val ${it.name}: ${fixNamespace(type)}" + if ("?" in type) " = null" else ""
            }
        } + "override val bare: Boolean = false").joinToString(", ")
        val extends = if (entry.entryType == EntryType.CONSTRUCTOR) {
            fixNamespace(entry.type).capitalize() + "Type()"
        } else {
            write("@Suppress(\"unused\", \"SpellCheckingInspection\")")
            val fixedType = fixNamespace(fixType(entry.type, true)!!)
            if (entry.type.startsWith("Vector<")) {
                "TLMethod<$fixedType>"
            } else {
                if (entry.type == genericType) "TLMethod<R>" else
                    "TLMethod<$fixedType>"
            }
        }
        write("data class $tlName$requestExtension$generic($params) : $extends {", 1)
    }
    private fun writeToTlReprDef() {
        write("@ExperimentalUnsignedTypes")
        write("override fun _toTlRepr(): IntArray {", 1)
    }
    private fun writeToTlReprBody() {
        entry.params.filter { it.type == "#" }.forEach { param ->
            write("var ${param.name}_flags = 0U")
            entry.params.map { Triple(it.type.substringBefore("?", "").substringAfter(".", ""),
                it.name, it.type.substringAfter("?")) }
                .filter { it.first.isNotEmpty() }.forEach {
                write("if (${it.second}${if (it.third != "true") " != null" else ""}) ${param.name}_flags += ${1.shl(it.first.toInt())}U")
            }
        }
        val params = entry.params.mapNotNull { fixSerialization(it.name, it.type) }.joinToString()
        write("return intArrayOf($params)")
    }

    private fun writeNativeGetter() {
        write(
            "override val native = " + when (entry.name) {
                "true", "boolTrue" -> "true"
                "boolFalse" -> "false"
                else -> "this"
            }
        )
        write()
    }

    private fun writeIdGetter(constructor: Boolean) {
        write("override val ${if (constructor) "" else "_"}id = ${entry.id}")
    }

    private fun writeFields() {
        val inner = entry.params.filter { it.type != "#" }
            .joinToString { "\"${it.name}\" to ${fixToTlObject(it.name, it.type)}" }
        write("override val fields by lazy { mapOf<String, TLObject<*>?>($inner) }")
    }

    private fun writeConstructor() {
//        val fixedType = fixNamespace(fixType(entry.type, true)!!)
        write(
            "override val constructor = " + if (entry.type.startsWith("Vector<")) {
                val genericConstructor = entry.type.split("<", ">")[1]
                val firstChar = genericConstructor.substringAfterLast(".").first()
//            val constructor = if (genericConstructor.first().toLowerCase() != genericConstructor.first()) "null" else fixedType
                val open = if (firstChar.toLowerCase() != firstChar) "<" else "("
                val close = if (firstChar.toLowerCase() != firstChar) ">(null)" else ")"
                "VectorObject.VectorConstructor" +
                    "$open${fixNamespace(formatType(entry.type.split("<", ">")[1]))}$close"
        } else {
            "TlMappings.GenericConstructor<${if (entry.type == genericType) "R" else fixNamespace(formatType(entry.type))}>()"
        })
    }
    private fun writeCompanionStart() = write("companion object : TLConstructor<$tlName$requestExtension> {", 1)
    private fun writeFromTlReprDef() {
        write("@ExperimentalUnsignedTypes")
        write("override fun _fromTlRepr(data: IntArray): Pair<Int, $tlName$requestExtension>? {", 1)
    }
    private fun writeFromTlReprBody() {

        val consumed = if (entry.params.isNotEmpty()) {
            write("var dataOffset = 0")
            for (param in entry.params)
                fixDeserialization(param.name, param.type).forEach { write(it.first, it.second) }
            "dataOffset"
        } else "0"
        write("return Pair($consumed, $tlName$requestExtension(" + entry.params.filter { it.type != "#" }.joinToString {
            val nullable = "?" in it.type
            val nullability = if (nullable) "?" else ""
            val nullabilityBool = if (nullable && it.type.substringAfter("?") != "true") "?" else ""
            "${it.name}_param$nullability.second$nullabilityBool.native" +
                    if (it.type.startsWith("Vector<")) ".map { it.native }" else ""
        } + "))")
    }
}

class TypeKtWriter(output: (String) -> Unit, typeName: String, packageName: String) : KtWriter(output, packageName, typeName) {
    private fun buildSpecial(): Boolean {
        if (tlName == "Object") {
            write("data class ObjectObject(val innerObject: TLObject<*>) : ObjectType() {", 1)
            write("override val bare = true")
            write()
            write("@ExperimentalUnsignedTypes")
            write("override fun _toTlRepr(): IntArray {", 1)
            write("return innerObject.toTlRepr()")
            writeDeclEnd()
            write()
            write("override val native = this")
            write()
            write("override val _id: Int? = null")
            write()
            write("override val fields by lazy { mapOf(\"innerObject\" to innerObject) }")
            write()
            write("companion object : TLConstructor<ObjectType> {", 1)
            write("override val id: Int? = null")
            write()
            write("override fun _fromTlRepr(data: IntArray): Pair<Int, ObjectType>? {", 1)
            write("val innerObject = (TlMappings.CONSTRUCTORS[data.first()]", 1)
            write("?: error(\"Attempting to deserialize unrecognized datatype\")).fromTlRepr(data)")
            write("?: error(\"Unable to deserialize data\")", -1)
            write("return Pair(innerObject.first, innerObject.second as? ObjectType ?: ObjectObject(innerObject.second))")
            writeDeclEnd()
            writeDeclEnd()
            writeDeclEnd()
            return false
        }
        if (tlName == "True") {
            write("sealed class ${tlName}Type : TLObject<Boolean>")
            return true
        }
        if (tlName == "Bool") {
            write("sealed class ${tlName}Type : TLObject<Boolean>")
            return true
        }
        val split = tlName.split("<", ">", " ")
        if (split.size > 1) {
            if (split.first() == "Vector") {
                return true
            } else {
                throw RuntimeException("Unexpected generic type $tlName")
            }
        }
        return false
    }

    override fun build() {
        if (buildSpecial())
            return
        writeInterfaceDef()
    }

    fun writeImports() {
        if (tlName == "Object")
            write("import tk.hack5.telekat.core.TlMappings")
        write("import tk.hack5.telekat.core.tl.TLObject")
        write()
    }

    private fun writeInterfaceDef() = write("sealed class ${tlName}Type : TLObject<${tlName}Type>")
}

class ErrorsWriter(output: (String) -> Unit, packageName: String, private val errors: Collection<Error>) :
    KtWriter(output, packageName) {
    override fun build() {
        writeHeader(false)
        writeImports()
        writeRootClass()
        write()
        writeSubClass(300..399, "Redirected")
        write()
        writeSubClass(400..499, "BadRequest")
        write()
        writeSubClass(500..599, "InternalServer")
        write()
        writeRegexFix()
    }

    private fun writeImports() {
        write("import tk.hack5.telekat.core.tl.TLMethod")
    }

    private fun writeRootClass() {
        write(
            "open class RpcError(val code: Int, val id: String, message: String?, val request: TLMethod<*>) : Error(\"\$message (caused by \$request)\") {",
            1
        )
        write("companion object {", 1)
        write("operator fun invoke(code: Int, id: String, request: TLMethod<*>) = when (code) {", 1)
        write("in 300..399 -> RedirectedError(code, id, request)")
        write("in 400..499 -> BadRequestError(code, id, request)")
        write("in 500..599 -> InternalServerError(code, id, request)")
        write("else -> RpcError(code, id, null, request)")
        writeDeclEnd()
        writeDeclEnd()
        writeDeclEnd()
    }

    private fun writeSubClass(codes: IntRange, name: String) {
        write(
            "open class ${name}Error(code: Int, id: String, message: String?, request: TLMethod<*>) : RpcError(code, id, message, request) {",
            1
        )
        write("companion object {", 1)
        write("operator fun invoke(code: Int, id: String, request: TLMethod<*>) = when(id) {", 1)
        val applicableErrors = errors.filter { codes.intersect(it.codes).isNotEmpty() }
        for (error in applicableErrors) {
            val hasRegex = !Regex("[A-Z0-9_]+").matches(error.name)
            write(
                (if (!hasRegex) "\"${error.name}\"" else "in Regex(\"${error.name}\")")
                        + " -> ${fixError(error.name)}Error(code, id, request" +
                        if (hasRegex) ", Regex(\"${error.name}\").matchEntire(id)!!.groupValues[1].toInt())" else ")"
            )
        }
        write("else -> ${name}Error(code, id, null, request)")
        writeDeclEnd()
        writeDeclEnd()

        for (error in applicableErrors) {
            var paramName: String? = null
            if (error.description.contains("{"))
                paramName = Regex("\\{([a-z]+)}").find(error.description)?.groupValues?.getOrNull(1)
            write()
            write(
                "class ${fixError(error.name)}Error(code: Int, id: String, request: TLMethod<*>" + (
                        if (paramName != null)
                            ", val $paramName: Int"
                        else
                            ""
                        ) + ") : ${name}Error(code, id, \"${error.description}\"" + (
                        if (paramName != null) {
                            ".replace(\"{$paramName}\", $paramName.toString()), request)"
                        } else
                            ", request)"
                        )
            )
        }
        writeDeclEnd()
    }

    private fun writeRegexFix() {
        write("@Suppress(\"NOTHING_TO_INLINE\")")
        write("private inline operator fun Regex.contains(text: CharSequence): Boolean = matches(text)")
    }
}

private fun fixError(name: String): String {
    val withoutRegex = name.replace(Regex("\\(.*?\\)"), "")
    val split = withoutRegex.split("_").filter { it.isNotEmpty() }
    return split.joinToString("") { it.toLowerCase().capitalize() }
}

private fun fixType(type: String, raw: Boolean? = false): String? {
    if (type.isEmpty())
        return null
    val collection = when (raw) {
        true -> Pair("VectorObject<", ">")
        false -> Pair("List<", ">")
        null -> Pair("", "")
    }
    val splitType = type.split("?")
    if (raw != true) {
        if (splitType.last() == "true")
            if (splitType.size != 2) error("True is outside flags")
            else return "Boolean"
        if (splitType.last() == "Bool")
            return "Boolean" + if (splitType.size == 2) "?" else ""
    }
    val splitGeneric = splitType.last().split(" ")
    val splitGenericUses = splitType.last().split("<", ">")
    return when {
        splitGeneric.first().toLowerCase() == "vector" ->
            collection.first + fixType(splitGeneric[1], raw) + collection.second
        splitGenericUses.first().toLowerCase() == "vector" ->
            collection.first + fixType(splitGenericUses[1], raw) + collection.second
        else -> fixTypeName(type.split("?").last(), raw != false)
    }?.let { it + if ("?" in type) "?" else "" }
}

private fun fixSerialization(name: String, type: String): String? {
    if (type == "#")
        return "${name}_flags.toInt()"
    if (type.endsWith("?true"))
        return null
    return "*" + fixToTlObject(name, type) + if ("?" in type) "?.toTlRepr() ?: intArrayOf()" else ".toTlRepr()"
}

private fun fixToTlObject(name: String, type: String): String? {
    val nullability = if ("?" in type && !type.endsWith("?true"))
        "?"
    else
        ""
    val intBits = when (type.substringAfterLast("?").substringAfterLast("<").substringBefore(">")) {
        "int128" -> "128"
        "int256" -> "256"
        else -> ""
    }
    val vectorBare = when {
        type.substringAfterLast("?").startsWith("vector<", true) -> if (type.first() == 'V') "false" else "true"
        else -> ""
    }
    return "$name$nullability.asTlObject$intBits($vectorBare)"
}

private fun fixDeserialization(name: String, type: String, _internal: Boolean = false): List<Pair<String, Int>> {
    if (type == "#")
        return listOf(Pair("val ${name}_flags = data[dataOffset++].toUInt()", 0))
    if ("?" in type) {
        val split = type.split(".", "?", limit=3)
        val ret = fixDeserialization(name, split[2], true)
        return listOf(
            Pair("val ${name}_param = if (${split[0]}_flags.and(1U.shl(${split[1]})) != 0U) {", 1),
            *ret.take(ret.size - 1).toTypedArray(),
            Pair("} else null", -1),
            ret.last()
        )
    }
    type.substringAfterLast(".").first().let { char ->
        return when {
            type.toLowerCase().startsWith(("vector<")) -> {
                val vectorFirst = type.first()
                val bare = vectorFirst.toLowerCase() == vectorFirst
                val generic = type.split("<", ">")[1]
                generic.substringAfterLast(".").first().let {
                    if (it.toLowerCase() != it) {
                        listOf(
                            Pair(
                                (if (!_internal) "val ${name}_param = " else "") +
                                        "(VectorObject.fromTlRepr<${fixNamespace(formatType(generic))}>" +
                                        "(data.sliceArray(dataOffset until data.size), $bare) ?: error(\"Unable to deserialize data\"))",
                                0
                            )
                        )
                    } else {
                        listOf(
                            Pair(
                                (if (!_internal) "val ${name}_param = " else "") +
                                        "(VectorObject.fromTlRepr(data.sliceArray(dataOffset until data.size), $bare, " +
                                        fixType(generic, true) + ") ?: error(\"Unable to deserialize data\"))", 0
                            )
                        )
                    }
                }

            }
            type == "Object" -> listOf(
                Pair(
                    (if (!_internal) "val ${name}_param = " else "") + "ObjectObject." +
                            "fromTlRepr(data.sliceArray(dataOffset until data.size)) ?: error(\"Unable to deserialize data\")",
                    0
                )
            )
            char.toLowerCase() != char -> listOf(
                Pair("@Suppress(\"UNCHECKED_CAST\")", 0),
                Pair(
                    (if (!_internal) "val ${name}_param = " else "") + "((TlMappings.CONSTRUCTORS[data[dataOffset]] " +
                            "?: error(\"Attempting to deserialize unrecognized datatype\"))." +
                            "fromTlRepr(data.sliceArray(dataOffset until data.size)) ?: error(\"Unable to deserialize data\")) as " +
                            "Pair<Int, " + fixNamespace(formatType(type)) + ">", 0
                )
            )
            else -> listOf(
                Pair(
                    (if (!_internal) "val ${name}_param = " else "") + formatType(type) +
                            ".fromTlRepr(data.sliceArray(dataOffset until data.size), true) ?: error(\"Unable to deserialize data\")",
                    0
                )
            )
        } + Pair("dataOffset += ${name}_param${if (_internal) "?" else ""}.first${if (_internal) " ?: 0" else ""}", 0)

    }
}

private fun fixTypeName(name: String, raw: Boolean = false): String? {
    if (name == "#") return null
    if (raw) return formatType(name)
    return PRIMITIVE_TYPES.getOrElse(name) {
        formatType(name)
    }
}

private fun formatType(name: String): String {
    if (name.isEmpty()) return name
    return name.capitalize().replace("%", "").replace(".", "_") + name.substringAfterLast(".").first().let {
        if (it.toLowerCase() != it) "Type" else "Object"
    }
}

internal fun fixNamespace(tlName: String): String {
    val dotOffset = tlName.indexOf(".")
    val tlPackage = if (dotOffset >= 0) tlName.substring(0, dotOffset).toLowerCase() + "_" else ""
    return tlPackage.capitalize() + tlName.substring(if (dotOffset >= 0) dotOffset + 1 else 0).capitalize()
}

val PRIMITIVE_TYPES = mapOf("int" to "Int", "long" to "Long", "double" to "Double", "string" to "String", "bytes" to "ByteArray", "int128" to "BigInteger", "int256" to "BigInteger")