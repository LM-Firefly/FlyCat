/*
    File to Download Public Resources License (F2DLPRL or F2DLPR License)

    This license applies to source code, distributions, written works, and other original forms of work.

    OFFICIAL WEBSITE: http://license.fileto.download
    IN CASE OF DISCREPANCIES, THE OFFICIAL WEBSITE SHALL PREVAIL.


    Terms and conditions for use, reproduction and redistribution:

    The descriptions provided by the copyright owners in the source code, accompanying documentation, or other materials distributed with the original take precedence over the terms and conditions of this license.

    Use, reproduction, and redistribution of the original document constitutes acceptance of the opinions, values, or other content that may be contained in the original, including, but not limited to, political positions, cultural opinions, or other implied information, as well as additional or implicit content that may be involved.

    Use of the original is prohibited if it would cause damage to the interests of the copyright owners or contributors, except for normal wear and tear caused by normal use.

    Any subject is permitted to use, reproduction, and redistribute source code, textual works, or other forms of work.
    Distributions may only be used and copied, but may not be redistributed, even if modified.
    Source code, literary works, or other forms of work may be used for personal purposes, but not for commercial purposes, even if modified, but may be used for commercial purposes when included as dependencies for displaying content on user interface or as non‑core dependencies.
    Distributions may only be used for personal purposes and not for commercial purposes, even if modified, but the distributions used to display content on user interface may be used for commercial purposes.
    Only the copyright owners has the right to use the original for commercial purposes.
    The original cannot be used for exploitative purposes or model training.

    No subject is granted the right to use any copyright, patent, trade name, trademark, service mark, or product license derived from the original, except as reasonably and customarily required to describe the source of the original.

    Redistribution of the original requires retaining this license and the copyright owners' copyright notice, or providing a link to the original source of the original and acknowledging the copyright owners.
    If the distributions are redistributed in a compressed format that supports multiple files, this license must be included in the archive, otherwise, this license will need to be incorporated into the accompanying documentation or other materials redistributed with the original.
    Additionally, the copyright owners' copyright notice must be reproduced in the accompanying documentation or other materials redistributed with the original.

    If the modified version or derivative work of the original is redistributed, the changes must be clearly indicated.


    THIS ORIGINAL IS PROVIDED BY THE COPYRIGHT OWNERS AND CONTRIBUTORS "AS IS" WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED.
    IN NO EVENT SHALL THE COPYRIGHT OWNERS AND CONTRIBUTORS BE LIABLE FOR ANY NEGATIVE IMPACT, BUT ONLY FOR THE POSITIVE IMPACT, WHETHER OR NOT THE USER, COPIER, OR REDISTRIBUTOR HAS BEEN INFORMED OF THE POSSIBILITY OF CAUSING DAMAGE.
    IN NO EVENT SHALL THE COPYRIGHT OWNERS AND CONTRIBUTORS GUARANTEE THE SUITABILITY OF THIS ORIGINAL FOR ANY PURPOSE, INCLUDING THE POSSIBILITY OF NORMAL USE OF THIS ORIGINAL.

*/


@file:Suppress(
    "PackageDirectoryMismatch",
    "PackageName",
    "ClassName",
    "ObjectPropertyName",
    "PropertyName",
    "FunctionName",
    "NonAsciiCharacters",
    "RemoveRedundantBackticks",
    "REDUNDANT_ELSE_IN_WHEN",
    "UnusedExpression",
    "unused"
) @file:Repository("https://repo.maven.apache.org/maven2") @file:DependsOn("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.9.0")

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.modules.EmptySerializersModule
import java.io.File
import kotlin.math.absoluteValue
import kotlin.reflect.KClass

private val SUPPRESS_ANNOTATION =
    "@file:Suppress(\"PackageDirectoryMismatch\", \"PackageName\", \"ClassName\", \"ObjectPropertyName\", \"PropertyName\", \"FunctionName\", \"NonAsciiCharacters\", \"RemoveRedundantBackticks\", \"REDUNDANT_ELSE_IN_WHEN\", \"UnusedExpression\", \"unused\")\n"

open class FVVV(
    value: Any? = null,
    var nodes: MutableMap<String, FVVV> = mutableMapOf(),
    var desc: String = "",
    var link: String = "",
) {
    companion object {
        @PublishedApi
        internal val integerClasses by lazy { setOf(Long::class, Int::class) }

        @PublishedApi
        internal val floatingPointClasses by lazy { setOf(Double::class, Float::class) }

        @PublishedApi
        @Suppress("UNCHECKED_CAST")
        internal inline fun <reified T> List<*>.cast() = when {
            all { it is T } -> this
            T::class in floatingPointClasses && all { it != null && it::class in floatingPointClasses } -> when (T::class) {
                Double::class -> map { (it as Number).toDouble() }
                Float::class -> map { (it as Number).toFloat() }
                else -> null
            }

            T::class in integerClasses && all { it != null && it::class in integerClasses } -> when (T::class) {
                Long::class -> map { (it as Number).toLong() }
                Int::class -> map { (it as Number).toInt() }
                else -> null
            }

            else -> null
        } as? List<T>?

        private val escapeTable by lazy {
            IntArray(1 shl Byte.SIZE_BITS).apply {
                setOf(
                    'b' to '\b',
                    'f' to '\u000C',
                    'n' to '\n',
                    'r' to '\r',
                    't' to '\t',
                    '\\' to '\\',
                ).forEach { (source, target) ->
                    this[source.code] = target.code
                }
            }
        }

        private fun getEscapedChar(character: Char) = character.code.let { characterCode ->
            if (characterCode < escapeTable.size) {
                escapeTable[characterCode].takeIf { it != 0 }?.toChar()
            } else {
                null
            }
        }
    }

    enum class FormatOpt(val mask: Int) {
        Common(0), UseWrapper(1 shl 0), Minify(1 shl 1), UseCRLF(1 shl 2), UseCR(1 shl 3), UseSpace2(1 shl 4), UseSpace4(
            1 shl 5
        ),
        IntBinary(1 shl 6), IntOctal(1 shl 7), IntHex(1 shl 8), DigitSep3(1 shl 9), DigitSep4(1 shl 10), UseColon(1 shl 11), FullWidth(
            1 shl 12
        ),
        KeepListSingle(1 shl 13), ForceUseSeparator(1 shl 14), RawMultilineString(1 shl 15), NoDescs(1 shl 16), NoLinks(
            1 shl 17
        ),
        FlattenPaths(1 shl 18), FwwStyle(1 shl 19), ;

        companion object {
            infix fun FormatOpt.or(other: FormatOpt) = this.mask or other.mask
            infix fun FormatOpt.or(other: Int) = this.mask or other
            infix fun Int.or(other: FormatOpt) = this or other.mask
            fun Int.toFmtOpts() = entries.filterTo(mutableSetOf()) { (this and it.mask) != Common.mask }
        }
    }

    var value = null as Any?
        set(target) = (if (target is FVVV) target.value else target).let {
            field = when (it) {
                is Int -> it.toLong()
                is Float -> it.toDouble()
                is List<*> -> it.map { item ->
                    when (item) {
                        is Int -> item.toLong()
                        is Float -> item.toDouble()
                        else -> item
                    }
                }

                else -> it
            }
        }

    init {
        this.value = value
    }

    operator fun get(key: String) = key.split('.').fold(this) { target, path ->
        target.nodes.getOrPut(path) { FVVV() }
    }

    operator fun set(key: String, target: Any?) = target.also { this[key].value = it }

    override fun equals(other: Any?) = when {
        this === other -> true
        other !is FVVV -> false
        else -> value == other.value && nodes == other.nodes
    }

    override fun hashCode() = listOf(value, nodes).hashCode()

    fun isEmpty() = when (value) {
        null -> true
        is String -> (value as String).isEmpty()
        is List<*> -> (value as List<*>).isEmpty()
        else -> false
    }

    fun isNotEmpty() = !isEmpty()

    inline fun <reified T> `is`() = value is T || value?.let {
        (it::class in floatingPointClasses && T::class in floatingPointClasses) || (it::class in integerClasses && T::class in integerClasses)
    } ?: false

    inline fun <reified T> isType() = `is`<T>()
    inline fun <reified T> isList() = (value as? List<*>?)?.cast<T>() != null
    val type get() = value?.run { this::class }

    inline fun <reified T> `as`() = value as? T? ?: (value as? Number)?.let { number ->
        when {
            number::class in floatingPointClasses && T::class in floatingPointClasses -> when (T::class) {
                Double::class -> number.toDouble()
                Float::class -> number.toFloat()
                else -> null
            }

            number::class in integerClasses && T::class in integerClasses -> when (T::class) {
                Long::class -> number.toLong()
                Int::class -> number.toInt()
                else -> null
            }

            else -> null
        }
    } as? T?

    inline fun <reified T> `as`(default: T) = `as`() ?: default
    inline fun <reified T> asType() = `as`<T>()
    inline fun <reified T> asType(default: T) = `as`(default)
    inline fun <reified T> get() = `as`<T>()!!
    inline fun <reified T> list(default: List<T> = emptyList()) = (value as? List<*>?)?.cast<T>() ?: default

    val bool get() = `as`(false)
    val boolean get() = bool
    val int get() = `as`(0L)
    val integer get() = int
    val double get() = `as`(0.0)
    val float get() = double
    val string get() = `as`("")
    val bools get() = list<Boolean>()
    val ints get() = list<Long>()
    val doubles get() = list<Double>()
    val strings get() = list<String>()
    val fvvvs get() = list<FVVV>()

    fun unlink(): Unit = nodes.forEach { (_, value) -> value.unlink() }.also { link = "" }

    fun parse(text: String) {
        if (text.trim().isEmpty()) return

        val textContext = TextCtx(text)
        val scopeStack = mutableListOf<FVVV>()

        textContext.skipBlanks()
        val hasWrapper = textContext.match('{', '｛')
        parseMain(textContext, scopeStack)

        if (hasWrapper) {
            textContext.skipBlanks()
            if (!textContext.match('}', '｝')) throw textContext.err.notFound("wrapper")
        }
        textContext.skipBlanks()
        if (!textContext.isEof) throw textContext.err.whyNotEOF()
    }

    inline fun <reified T> parse(text: String): T {
        parse(text)
        return to()
    }

    override fun toString() = toString(FormatCtx())
    fun toString(vararg flags: FormatOpt) =
        toString(FormatCtx(flags.fold(FormatOpt.Common.mask) { target, index -> target or index.mask }))

    fun toString(vararg flags: Int) =
        toString(FormatCtx(flags.fold(FormatOpt.Common.mask) { target, index -> target or index }))

    private fun toString(context: FormatCtx) = buildString {
        if (context.useWrapper) {
            append(context.fwvBegin)
            if (!context.minify) append(context.newline)
        }
        toStringRoot(context, this, if (context.useWrapper) 1 else 0)
        if (context.useWrapper) {
            if (!context.minify) append(context.newline)
            append(context.fwvEnd)
        }
    }

    inline fun <reified T> to() = FVVVDecoder(this).decodeSerializableValue(serializer<T>())
    inline fun <reified T> from(data: T) =
        unlink().also { FVVVEncoder(this).encodeSerializableValue(serializer<T>(), data) }

    private class TextCtx(val input: String) {
        var index = 0
        val linesStart = mutableListOf(0)
        val err by lazy { ErrHandler(this) }

        init {
            if (input.startsWith('\uFEFF')) index = 1
        }

        fun preview() = if (isEof) null else input[index]
        fun prematch(vararg targets: Char) = preview()?.let { character -> targets.any { character == it } } ?: false

        fun next() = if (isEof) null else input[index++].also {
            when (it) {
                '\r' -> linesStart.add(index)
                '\n' -> if (index >= 2 && input[index - 2] == '\r') {
                    linesStart[linesStart.size - 1] = index
                } else {
                    linesStart.add(index)
                }
            }
        }

        fun match(vararg targets: Char, skipBlanks: Boolean = true, sameLine: Boolean = false): Boolean {
            if (skipBlanks) this.skipBlanks(sameLine = sameLine)
            return prematch(*targets).also { if (it) next() }
        }

        fun skipBlanks(sameLine: Boolean = false) {
            while (!isEof && input[index].isWhitespace()) {
                if (sameLine && prematch('\n', '\r')) {
                    break
                } else {
                    next()
                }
            }
        }

        val isEof get() = index >= input.length

        fun isSameLine() = linesStart.size.let { before ->
            skipBlanks()
            before == linesStart.size
        }

        class ErrHandler(private val context: TextCtx) {
            private fun makeError(message: String) =
                ParseException("${context.linesStart.size}:${context.index - context.linesStart.last() + 1}: $message")

            fun unknown() = makeError("Why??? IDK!!!")
            fun whyEOF() = makeError("Why EOF???")
            fun whyNotEOF() = makeError("Why not EOF???")
            fun notFound(target: String) = makeError("Where is the ${if (target.length > 1) target else "'$target'"}?")
            fun noValue(target: String) = makeError("Cannot find the value of '$target'")
            fun plusList() = makeError("Why plus with list?")
            fun valuePlusFVVV() = makeError("Why value plus with FVVV?")
        }
    }

    private class FormatCtx(flags: Int = FormatOpt.Common.mask) {
        var newline = "\n"
        var indentUnit = "\t"
        var assignOperator = " = "
        var listBegin = '['
        var listEnd = ']'
        var fwvBegin = '{'
        var fwvEnd = '}'
        var itemSeparator = ','
        var statementSeparator = ';'
        var integerBase = 10
        var digitSeparatorStep = 0
        var digitSeparatorCharacter = null as Char?
        var useWrapper = false
        var minify = false
        var fullWidth = false
        var keepListSingle = false
        var forceSeparator = false
        var rawMultilineString = false
        var noDescriptions = false
        var noLinks = false
        var flattenPaths = false
        var fwwStyle = false

        init {
            fun Int.has(option: FormatOpt) = (this and option.mask) != FormatOpt.Common.mask

            useWrapper = flags.has(FormatOpt.UseWrapper)

            when {
                flags.has(FormatOpt.UseCRLF) -> newline = "\r\n"
                flags.has(FormatOpt.UseCR) -> newline = "\r"
            }

            when {
                flags.has(FormatOpt.UseSpace2) -> indentUnit = "  "
                flags.has(FormatOpt.UseSpace4) -> indentUnit = "    "
            }

            when {
                flags.has(FormatOpt.IntHex) -> integerBase = 16
                flags.has(FormatOpt.IntOctal) -> integerBase = 8
                flags.has(FormatOpt.IntBinary) -> integerBase = 2
            }

            when {
                flags.has(FormatOpt.DigitSep3) -> digitSeparatorStep = 3
                flags.has(FormatOpt.DigitSep4) -> digitSeparatorStep = 4
            }

            fullWidth = flags.has(FormatOpt.FullWidth)
            if (fullWidth) {
                if (flags.has(FormatOpt.UseColon)) assignOperator = "："
                listBegin = '［'
                listEnd = '］'
                fwvBegin = '｛'
                fwvEnd = '｝'
                itemSeparator = '，'
                statementSeparator = '；'
                if (digitSeparatorStep > 0) digitSeparatorCharacter = '’'
            } else {
                if (flags.has(FormatOpt.UseColon)) assignOperator = ": "
                if (digitSeparatorStep > 0) digitSeparatorCharacter = '\''
            }

            keepListSingle = flags.has(FormatOpt.KeepListSingle)
            forceSeparator = flags.has(FormatOpt.ForceUseSeparator)
            rawMultilineString = flags.has(FormatOpt.RawMultilineString)
            noDescriptions = flags.has(FormatOpt.NoDescs)
            noLinks = flags.has(FormatOpt.NoLinks)
            flattenPaths = flags.has(FormatOpt.FlattenPaths)
            fwwStyle = flags.has(FormatOpt.FwwStyle)
            minify = flags.has(FormatOpt.Minify)

            if (minify) {
                newline = ""
                indentUnit = ""
                assignOperator = assignOperator.trim()
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    @PublishedApi
    internal class FVVVDecoder(private val node: FVVV) : AbstractDecoder() {
        override val serializersModule = EmptySerializersModule()
        private var elementIndex = 0
        private var targetNode = node

        override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
            if (descriptor.kind is StructureKind.LIST) {
                val list = node.value as? List<*> ?: return CompositeDecoder.DECODE_DONE
                if (elementIndex < list.size) {
                    val item = list[elementIndex]
                    targetNode = item as? FVVV ?: FVVV(item)
                    return elementIndex++
                }
                return CompositeDecoder.DECODE_DONE
            }

            while (elementIndex < descriptor.elementsCount) {
                node.nodes[descriptor.getElementName(elementIndex)]?.apply {
                    targetNode = this
                    return elementIndex++
                }
                ++elementIndex
            }
            return CompositeDecoder.DECODE_DONE
        }

        override fun decodeBoolean() = targetNode.boolean
        override fun decodeByte() = targetNode.int.toByte()
        override fun decodeShort() = targetNode.int.toShort()
        override fun decodeInt() = targetNode.int.toInt()
        override fun decodeLong() = targetNode.int
        override fun decodeFloat() = targetNode.double.toFloat()
        override fun decodeDouble() = targetNode.double
        override fun decodeChar() = targetNode.string.first()
        override fun decodeString() = targetNode.string
        override fun decodeEnum(enumDescriptor: SerialDescriptor) =
            enumDescriptor.getElementIndex("${targetNode.value}")

        override fun <T> decodeSerializableElement(
            descriptor: SerialDescriptor,
            index: Int,
            deserializer: DeserializationStrategy<T>,
            previousValue: T?,
        ) = FVVVDecoder(targetNode).decodeSerializableValue(deserializer)
    }

    @OptIn(ExperimentalSerializationApi::class)
    @PublishedApi
    internal class FVVVEncoder(private val node: FVVV) : AbstractEncoder() {
        override val serializersModule = EmptySerializersModule()
        private var targetKey = null as String?

        override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
            if (descriptor.kind !is StructureKind.LIST) targetKey = descriptor.getElementName(index)
            return true
        }

        private fun writeValue(value: Any) {
            if (node.value is MutableList<*>) {
                @Suppress("UNCHECKED_CAST") (node.value as MutableList<Any>).add(value)
            } else {
                targetKey?.let { key ->
                    node[key].value = value
                    targetKey = null
                } ?: run {
                    node.value = value
                }
            }
        }

        override fun encodeBoolean(value: Boolean) = writeValue(value)
        override fun encodeByte(value: Byte) = writeValue(value.toLong())
        override fun encodeShort(value: Short) = writeValue(value.toLong())
        override fun encodeInt(value: Int) = writeValue(value.toLong())
        override fun encodeLong(value: Long) = writeValue(value)
        override fun encodeFloat(value: Float) = writeValue(value.toDouble())
        override fun encodeDouble(value: Double) = writeValue(value)
        override fun encodeChar(value: Char) = writeValue("$value")
        override fun encodeString(value: String) = writeValue(value)
        override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) =
            writeValue(enumDescriptor.getElementName(index))

        override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder = also {
            if (descriptor.kind is StructureKind.LIST) {
                val elementDescriptor = descriptor.getElementDescriptor(0)
                node.value = when (elementDescriptor.kind) {
                    PrimitiveKind.BOOLEAN -> mutableListOf<Boolean>()
                    PrimitiveKind.BYTE, PrimitiveKind.SHORT, PrimitiveKind.INT, PrimitiveKind.LONG -> mutableListOf<Long>()
                    PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> mutableListOf<Double>()
                    PrimitiveKind.CHAR, PrimitiveKind.STRING, SerialKind.ENUM -> mutableListOf<String>()
                    StructureKind.CLASS, StructureKind.OBJECT -> mutableListOf<FVVV>()
                    else -> throw SerializationException("List<${elementDescriptor.kind}> is not supported")
                }
            }
        }

        override fun <T> encodeSerializableElement(
            descriptor: SerialDescriptor,
            index: Int,
            serializer: SerializationStrategy<T>,
            value: T,
        ) {
            if (descriptor.kind is StructureKind.LIST) {
                if (serializer.descriptor.kind.run { this is StructureKind.CLASS || this is StructureKind.OBJECT }) {
                    FVVV().apply {
                        @Suppress("UNCHECKED_CAST") (node.value as MutableList<FVVV>).add(this)
                        FVVVEncoder(this).encodeSerializableValue(serializer, value)
                    }
                } else {
                    serializer.serialize(this, value)
                }
            } else {
                node[descriptor.getElementName(index)].apply {
                    FVVVEncoder(this).encodeSerializableValue(serializer, value)
                }
            }
        }
    }

    class ParseException(message: String) : Exception(message) {
        override fun toString() = "ParseException: $message"
    }

    private fun parseMain(context: TextCtx, scopeStack: MutableList<FVVV>) {
        fun findKey(path: String, scopeStack: List<FVVV>) = path.split('.').takeIf { it.isNotEmpty() }?.let { paths ->
            scopeStack.asReversed().firstNotNullOfOrNull { index ->
                paths.fold(index as FVVV?) { target, pathIndex ->
                    target?.nodes?.get(pathIndex)
                }
            }
        }

        fun parseName(context: TextCtx) = context.skipBlanks().let {
            buildString {
                while (!context.isEof && !context.prematch('=', ':', '：', '<')) append(context.next()!!)
            }.trimEnd()
        }

        fun parseDescription(
            context: TextCtx,
            description: StringBuilder,
            scopeStack: List<FVVV>,
            skipBlanks: Boolean = true,
            sameLine: Boolean = false,
        ) {
            while (true) {
                val (originalIndex, originalLine) = context.index to context.linesStart.size
                if (!context.match('<', sameLine = sameLine)) {
                    if (!skipBlanks) {
                        context.index = originalIndex
                        while (context.linesStart.size > originalLine) context.linesStart.removeLast()
                    }
                    break
                }

                description.clear()
                while (true) when {
                    context.isEof -> throw context.err.whyEOF()
                    context.match('>', skipBlanks = false) -> {
                        findKey("$description", scopeStack)?.takeIf { it.isType<String>() }?.also { target ->
                                description.clear().append(target.get<String>())
                            }
                        break
                    }

                    context.match('\\', skipBlanks = false) -> when {
                        context.isEof -> throw context.err.whyEOF()
                        context.match('>', skipBlanks = false) -> description.append('>')
                        else -> {
                            val character = context.next()!!
                            getEscapedChar(character)?.let { target -> description.append(target) }
                                ?: description.append('\\', character)
                        }
                    }

                    else -> description.append(context.next()!!)
                }
            }
        }

        fun parseText(context: TextCtx, text: StringBuilder) {
            if (context.match('`')) {
                while (true) when {
                    context.isEof -> throw context.err.whyEOF()
                    context.match('`', skipBlanks = false) -> break
                    else -> text.append(context.next()!!)
                }
                "$text".trimIndent().trim().also { trimmedText ->
                    text.clear().append(trimmedText)
                }
                return
            }

            val isFullWidth = context.match('“') || !context.match('"')
            while (true) when {
                context.isEof -> throw context.err.whyEOF()
                if (isFullWidth) context.match('”', skipBlanks = false)
                else context.match('"', skipBlanks = false) -> return

                context.match('\\', skipBlanks = false) -> when {
                    context.isEof -> throw context.err.whyEOF()
                    isFullWidth && context.match('”', skipBlanks = false) -> text.append('”')
                    !isFullWidth && context.match('"', skipBlanks = false) -> text.append('"')
                    else -> {
                        val character = context.next()!!
                        getEscapedChar(character)?.let { target -> text.append(target) } ?: text.append('\\', character)
                    }
                }

                else -> text.append(context.next()!!)
            }
        }

        fun tryParseNumber(targetString: String): Number? {
            val sanitizedString = targetString.filterNot { it in "'’" }
            if (sanitizedString.isEmpty()) return null

            var sign = 1
            var index = 0
            if (sanitizedString.startsWith('-')) sign = (-1).also { ++index }
            else if (sanitizedString.startsWith('+')) ++index

            var radix = 10
            if (index < sanitizedString.length && sanitizedString[index] == '0' && index + 1 < sanitizedString.length) {
                when (sanitizedString[index + 1]) {
                    'x', 'X' -> radix = 16.also { index += 2 }
                    'o', 'O' -> radix = 8.also { index += 2 }
                    'b', 'B' -> radix = 2.also { index += 2 }
                    '0', '1', '2', '3', '4', '5', '6', '7' -> radix = 8.also { ++index }
                }
            }

            val digitString = sanitizedString.substring(index)
            return digitString.takeIf { it.isNotEmpty() }?.let {
                when {
                    radix != 10 -> digitString.toLongOrNull(radix)
                    digitString.any { character -> character == '.' || character == 'e' || character == 'E' } -> digitString.toDoubleOrNull()
                    else -> digitString.toLongOrNull()
                }?.let {
                    when (it) {
                        is Long -> it * sign
                        is Double -> it * sign
                        else -> null
                    }
                }
            }
        }

        fun parseValue(
            context: TextCtx,
            scopeStack: List<FVVV>,
            targetFvvv: FVVV,
            keyDescription: StringBuilder,
            inList: Boolean = false,
        ) {
            while (true) {
                parseDescription(context, keyDescription, scopeStack, skipBlanks = inList, sameLine = !inList)
                if (context.isEof || (if (inList) {
                        context.match(',', '，') || context.prematch(']', '］')
                    } else {
                        !context.isSameLine() || context.match(';', '；') || context.prematch('}', '｝')
                    })
                ) {
                    throw context.err.notFound("value")
                }

                val temporaryText = StringBuilder()
                if (context.prematch('"', '“', '`')) {
                    parseText(context, temporaryText)
                    if (targetFvvv.value == null) {
                        targetFvvv.value = "$temporaryText"
                    } else {
                        targetFvvv.apply {
                            link = ""
                            value = "${targetFvvv.value}$temporaryText"
                        }
                    }
                } else {
                    while (!context.isEof && !context.prematch('<', '+') && !context.prematch('\r', '\n')) {
                        if (if (inList) {
                                context.prematch(',', '，', ']', '］')
                            } else {
                                context.prematch(';', '；', '}', '｝')
                            }
                        ) {
                            break
                        }
                        temporaryText.append(context.next()!!)
                    }

                    val temporaryString = "$temporaryText".trimEnd()
                    if (temporaryString.isEmpty()) throw context.err.notFound("value")

                    val isTrue = temporaryString.toBoolean()
                    if (isTrue || temporaryString.equals("false", true)) {
                        if (targetFvvv.value == null) {
                            targetFvvv.value = isTrue
                        } else {
                            targetFvvv.apply {
                                link = ""
                                value = "${targetFvvv.value}$temporaryString"
                            }
                        }
                    } else {
                        tryParseNumber(temporaryString)?.also { temporaryNumber ->
                            if (targetFvvv.value == null) {
                                targetFvvv.value = temporaryNumber
                            } else {
                                targetFvvv.apply {
                                    link = ""
                                    value = "${targetFvvv.value}$temporaryString"
                                }
                            }
                        } ?: findKey(temporaryString, scopeStack)?.also { target ->
                            if (targetFvvv.value != null && target.value is List<*>) throw context.err.plusList()
                            if (targetFvvv.value == null) {
                                targetFvvv.apply {
                                    link = temporaryString
                                    value = target.value
                                }
                            } else {
                                targetFvvv.apply {
                                    link = ""
                                    value = "${targetFvvv.value}${target.value}"
                                }
                            }
                            targetFvvv.nodes = target.nodes
                        } ?: throw context.err.noValue(temporaryString)
                    }
                }

                parseDescription(context, keyDescription, scopeStack, skipBlanks = false, sameLine = true)
                if (context.isEof || !context.isSameLine() || (if (inList) {
                        context.match(',', '，') || context.prematch(']', '］')
                    } else {
                        context.match(';', '；') || context.prematch('}', '｝')
                    })
                ) {
                    return
                }

                if (context.match('+')) continue else throw context.err.notFound("+")
            }
        }

        scopeStack.add(this)

        while (true) {
            val keyDescription = StringBuilder()
            parseDescription(context, keyDescription, scopeStack, skipBlanks = false)
            if (!context.isSameLine()) keyDescription.clear()
            if (context.isEof || context.prematch('}', '｝')) break

            val name = parseName(context)
            if (name.isEmpty()) throw context.err.notFound("name")
            parseDescription(context, keyDescription, scopeStack)
            if (!context.match('=', ':', '：')) throw context.err.notFound("=")
            parseDescription(context, keyDescription, scopeStack)

            var goto = false
            val targetKey = this[name]
            if (context.match('[', '［')) {
                val targetList = mutableListOf<Any>()
                var listType = null as KClass<*>?
                while (true) {
                    val valueDescription = StringBuilder()
                    parseDescription(context, valueDescription, scopeStack, skipBlanks = false)
                    if (!context.isSameLine()) valueDescription.clear()

                    if (context.isEof) throw context.err.whyEOF()
                    if (context.match('{', '｛')) {
                        listType = FVVV::class
                        val temporaryValue = FVVV().apply { parseMain(context, scopeStack) }
                        if (!context.match('}', '｝')) throw context.err.notFound("}")
                        parseDescription(context, valueDescription, scopeStack, skipBlanks = false, sameLine = true)
                        temporaryValue.desc = "$valueDescription"
                        targetList.add(temporaryValue)
                        if (context.isSameLine() && !context.match(',', '，') && !context.prematch(']', '］')) {
                            throw context.err.notFound("EOL")
                        }
                    } else {
                        val targetFvvv = FVVV()
                        parseValue(context, scopeStack, targetFvvv, keyDescription, inList = true)

                        @Suppress("UNCHECKED_CAST") if (targetFvvv.value is List<*>) {
                            targetList.addAll(targetFvvv.value as List<Any>)
                        } else if (targetFvvv.value != null) {
                            targetList.add(targetFvvv.value!!)
                        } else {
                            targetList.add(targetFvvv)
                        }

                        if (listType == null) {
                            listType = targetList.last()::class
                        } else if (listType != targetList.last()::class) {
                            if (listType == FVVV::class || targetList.last()::class == FVVV::class) {
                                throw context.err.valuePlusFVVV()
                            }
                            when (targetList.last()::class) {
                                String::class -> listType = String::class
                                Double::class -> if (listType != String::class) listType = Double::class
                                Long::class -> if (listType != String::class && listType != Double::class) listType =
                                    Long::class
                            }
                        }
                    }
                    if (context.match(']', '］')) break
                }

                if (listType != FVVV::class) {
                    targetList.map { item ->
                        if (item::class == listType) {
                            item
                        } else {
                            when (listType) {
                                String::class -> "$item"
                                Double::class -> when (item) {
                                    is Long -> item.toDouble()
                                    is Boolean -> if (item) 1.0 else 0.0
                                    else -> item
                                }

                                Long::class -> when (item) {
                                    is Boolean -> if (item) 1 else 0
                                    else -> item
                                }

                                else -> item
                            }
                        }
                    }.also { normalizedList ->
                        targetList.apply {
                            clear()
                            addAll(normalizedList)
                        }
                    }
                }

                targetKey.value = when (listType) {
                    FVVV::class -> targetList.cast<FVVV>()!!
                    String::class -> targetList.cast<String>()!!
                    Double::class -> targetList.cast<Double>()!!
                    Long::class -> targetList.cast<Long>()!!
                    Boolean::class -> targetList.cast<Boolean>()!!
                    else -> TODO()
                }
            } else if (context.match('{', '｛')) {
                targetKey.parseMain(context, scopeStack)
                if (!context.match('}', '｝')) throw context.err.notFound("}")
            } else {
                parseValue(context, scopeStack, targetKey, keyDescription)
                goto = true
            }

            if (!goto) {
                parseDescription(context, keyDescription, scopeStack, skipBlanks = false, sameLine = true)
                if (context.isSameLine() && !context.isEof && !context.match(';', '；') && !context.prematch('}', '｝')) {
                    throw context.err.notFound("EOL")
                }
            }
            targetKey.desc = "$keyDescription"
        }

        scopeStack.removeLast()
    }

    private fun toStringRoot(context: FormatCtx, result: StringBuilder, level: Int) {
        if (nodes.isEmpty()) return
        nodes.entries.forEachIndexed { index, (key, value) ->
            value.toStringMain(context, key, result, level, index == nodes.size - 1)
        }
    }

    private fun toStringMain(
        context: FormatCtx,
        name: String,
        result: StringBuilder,
        level: Int,
        isBack: Boolean,
    ) {
        fun escapeString(text: String, isDescription: Boolean, fullWidth: Boolean = false) =
            buildString(text.length + 6) {
                if (isDescription) append('<') else append(if (fullWidth) '“' else '"')
                text.forEach { character ->
                    when (character) {
                        '\\' -> "\\\\"
                        '\b' -> "\\b"
                        '\u000C' -> "\\f"
                        '\n' -> "\\n"
                        '\r' -> "\\r"
                        '\t' -> "\\t"
                        '"' -> if (!fullWidth && !isDescription) "\\\"" else character
                        '”' -> if (fullWidth && !isDescription) "\\”" else character
                        '>' -> if (isDescription) "\\>" else character
                        else -> character
                    }.also { append(it) }
                }
                if (isDescription) append('>') else append(if (fullWidth) '”' else '"')
            }

        fun toStringFwv(context: FormatCtx, targetFvvv: FVVV, result: StringBuilder, indent: String, level: Int) =
            result.apply {
                append(context.fwvBegin)
                if (!context.minify) append(context.newline)
                targetFvvv.toStringRoot(context, result, level + 1)
                if (!context.minify) append(context.newline).append(indent)
                append(context.fwvEnd)
            }

        fun toStringValue(
            context: FormatCtx,
            targetValue: Any,
            result: StringBuilder,
            indent: String,
            level: Int = 0,
        ) {
            when (targetValue) {
                is Boolean -> result.append("$targetValue")
                is Number -> result.apply {
                    if ((targetValue is Long || targetValue is Int) && context.integerBase != 10) {
                        if (targetValue == 0L) {
                            when (context.integerBase) {
                                16 -> "0x0"
                                8 -> "0o0"
                                2 -> "0b0"
                                else -> TODO()
                            }.also {
                                append(it)
                                return
                            }
                        }

                        if (targetValue < 0) append('-')
                        val absoluteValue = targetValue.toLong().absoluteValue
                        when (context.integerBase) {
                            2 -> "0b"
                            8 -> "0o"
                            16 -> "0x"
                            else -> TODO()
                        }.also {
                            append(it, absoluteValue.toString(context.integerBase))
                        }
                        return
                    }

                    val rawNumber = "$targetValue"
                    if (context.digitSeparatorStep == 0) {
                        append(rawNumber)
                        return
                    }

                    val parts = rawNumber.split('.')
                    var integerPart = parts[0]
                    var hasSign = false
                    if (integerPart.startsWith('-') || integerPart.startsWith('+')) {
                        hasSign = true
                        integerPart = integerPart.substring(1)
                    }
                    val integerLength = integerPart.length

                    if (integerLength <= context.digitSeparatorStep) {
                        append(rawNumber)
                        return
                    }

                    result.ensureCapacity(result.length + rawNumber.length + integerLength / context.digitSeparatorStep + 1)
                    if (hasSign) append(rawNumber[0])
                    integerPart.forEachIndexed { index, character ->
                        if (index > 0 && (integerLength - index) % context.digitSeparatorStep == 0) {
                            append(context.digitSeparatorCharacter!!)
                        }
                        append(character)
                    }

                    if (parts.size >= 2) append('.').append(parts[1])
                }

                is String -> result.apply {
                    if (!context.minify && context.rawMultilineString && targetValue.length >= 3 && !targetValue.contains(
                            '`'
                        ) && targetValue.trim().any { it in "\r\n" }
                    ) {
                        val stringIndent = indent + context.indentUnit
                        result.ensureCapacity(result.length + targetValue.length + stringIndent.length * 6)
                        append('`').append(context.newline)
                        targetValue.trimIndent().trim().lineSequence().forEach { line ->
                            if (line.isNotEmpty()) append(stringIndent)
                            append(line).append(context.newline)
                        }
                        append(indent).append('`')
                        return
                    }

                    if (level == 0 && context.fullWidth && result.last() == ' ') result.setLength(result.lastIndex)
                    append(escapeString(targetValue, isDescription = false, fullWidth = context.fullWidth))
                }

                is FVVV -> result.apply {
                    if (context.fwwStyle && targetValue.desc.isNotEmpty()) {
                        append(escapeString(targetValue.desc, isDescription = true, fullWidth = context.fullWidth))
                        if (!context.minify && !context.fullWidth) append(' ')
                    }
                    toStringFwv(context, targetValue, result, indent, level)
                    if (!context.noDescriptions && !context.fwwStyle && targetValue.desc.isNotEmpty()) {
                        if (!context.minify && !context.fullWidth) append(' ')
                        append(escapeString(targetValue.desc, isDescription = true, fullWidth = context.fullWidth))
                    }
                }
            }
        }

        if (name.isEmpty() || (value !is String && isEmpty() && nodes.isEmpty())) return
        var currentName = name
        result.ensureCapacity(result.length + nodes.size * 6)

        var targetNode = this
        if (context.flattenPaths) {
            currentName = buildString(currentName.length + 6) {
                append(currentName)
                while (targetNode.nodes.size == 1 && (context.noDescriptions || targetNode.desc.isEmpty()) && (context.noLinks || targetNode.link.isEmpty())) {
                    val nodePair = targetNode.nodes.entries.first()
                    append('.').append(nodePair.key)
                    targetNode = nodePair.value
                }
            }
        }

        var indent = ""
        if (!context.minify && level > 0) indent = context.indentUnit.repeat(level).also { result.append(it) }
        result.append(currentName).append(context.assignOperator)

        if (!context.noLinks && targetNode.link.isNotEmpty()) {
            result.append(targetNode.link)
        } else if (targetNode.nodes.isNotEmpty()) {
            if (context.fwwStyle && targetNode.desc.isNotEmpty()) {
                result.append(escapeString(targetNode.desc, isDescription = true))
                if (!context.minify) result.append(' ')
            }
            if (context.fullWidth && result.last() == ' ') result.setLength(result.lastIndex)
            toStringFwv(context, targetNode, result, indent, level)
        } else if (targetNode.value !is List<*>) {
            toStringValue(context, targetNode.value!!, result, indent)
        } else {
            var multiline = false
            if (!context.minify && !context.keepListSingle) {
                multiline = targetNode.`is`<List<FVVV>>() || run {
                    var longItems = 0
                    (targetNode.value as List<*>).any { item ->
                        when (item) {
                            is String -> if (item.length + 2 >= 16) ++longItems
                            else -> if ("$item".length >= 16) ++longItems
                        }
                        longItems >= 6
                    }
                }
            }

            val valueIndent = indent + context.indentUnit
            val valueLevel = level + 1
            if (context.fullWidth && result.last() == ' ') result.setLength(result.lastIndex)
            result.append(context.listBegin)
            if (multiline) result.append(context.newline)

            (targetNode.value as List<*>).forEachIndexed { index, item ->
                if (multiline) result.append(valueIndent)
                toStringValue(context, item!!, result, valueIndent, valueLevel)
                if (if (multiline) context.forceSeparator else index != (targetNode.value as List<*>).lastIndex) {
                    result.append(context.itemSeparator)
                    if (!multiline && !context.fullWidth && !context.minify) result.append(' ')
                }
                if (multiline) result.append(context.newline)
            }

            if (multiline) result.append(indent)
            result.append(context.listEnd)
        }

        if (!context.noDescriptions && targetNode.desc.isNotEmpty() && ((targetNode.nodes.isEmpty() && (!targetNode.`is`<List<FVVV>>())) || targetNode.link.isNotEmpty() || !context.fwwStyle)) {
            if (!context.minify && (!context.fullWidth || targetNode.link.isNotEmpty() || (targetNode.nodes.isEmpty() && targetNode.value !is List<*> && targetNode.value !is String) || (targetNode.value is String && result.last() == '`'))) {
                result.append(' ')
            }
            result.append(escapeString(targetNode.desc, isDescription = true))
        }

        if (context.minify || context.forceSeparator) result.append(context.statementSeparator)
        if (!context.minify && !isBack) result.append(context.newline)
    }
}

class LocaleProjectConfig(private val arguments: Array<String>) {
    val repositoryRoot: File =
        arguments.firstOrNull { !it.startsWith("--") }?.let(::File)?.absoluteFile ?: File(".").absoluteFile

    val localeDirectory: File = File(repositoryRoot, "locale")
    val sourceDirectory: File = File(localeDirectory, "lang")
    val outputFile: File = File(localeDirectory, "build/generated/fytxt/kotlin/commonMain/kotlin/fytxt.kt")

    val packageName: String = "dev.oom_wg.purejoy.mlang"
    val objectName: String = "MLang"
    val defaultLanguage: String = "ZH"
    val commonGroupName: String = "lang"
    val generateComposeApis: Boolean = true
    val generateInternalClasses: Boolean = false

    fun validate() {
        require(localeDirectory.isDirectory) { "Locale module not found: ${localeDirectory.absolutePath}" }
        require(sourceDirectory.isDirectory) { "Language source directory not found: ${sourceDirectory.absolutePath}" }
    }
}

class LocaleGenerator(private val config: LocaleProjectConfig) {
    fun generate() {
        config.validate()

        val normalizedDefaultLanguage = config.defaultLanguage.uppercase()
        val parsedLanguages = linkedMapOf(
            config.commonGroupName to loadLanguageGroup(config.sourceDirectory),
        )

        require(parsedLanguages.isNotEmpty()) { "No language groups configured." }
        require(parsedLanguages.getValue(config.commonGroupName).containsKey(normalizedDefaultLanguage)) {
            "Default language '$normalizedDefaultLanguage' not found in ${config.sourceDirectory.absolutePath}"
        }

        val generatedSource = buildGeneratedSource(
            parsedLanguages = parsedLanguages.toList(),
            defaultLanguage = normalizedDefaultLanguage,
        )

        config.outputFile.parentFile.deleteRecursively()
        config.outputFile.parentFile.mkdirs()
        config.outputFile.writeText(generatedSource)

        println("[locale] Generated ${config.outputFile.absolutePath}")
        println("[locale] Groups: ${parsedLanguages.keys.joinToString()}")
        println("[locale] Languages: ${parsedLanguages.getValue(config.commonGroupName).keys.joinToString()}")
    }

    private fun loadLanguageGroup(sourceDirectory: File): Map<String, FVVV> {
        return sourceDirectory.listFiles { file -> file.isDirectory }?.sortedBy { it.name }
            ?.associate { languageDirectory ->
                languageDirectory.name.uppercase() to FVVV().apply {
                    languageDirectory.walkTopDown()
                        .filter { file -> file.isFile && file.extension in setOf("fvv", "fw") }
                        .sortedBy { file -> file.relativeTo(languageDirectory).invariantSeparatorsPath }
                        .forEach { file -> parse(file.readText()) }
                }
            }.orEmpty()
    }

    private fun buildGeneratedSource(
        parsedLanguages: List<Pair<String, Map<String, FVVV>>>,
        defaultLanguage: String,
    ): String {
        val commonLanguageGroup = parsedLanguages.first()
        val variantLanguageGroups = parsedLanguages.drop(1)
        val languageTags = commonLanguageGroup.second.keys
        val defaultLanguageRoot = commonLanguageGroup.second.getValue(defaultLanguage)

        return buildString {
            appendLine(SUPPRESS_ANNOTATION)
            appendLine("package ${config.packageName}")
            appendLine()
            appendLine("import androidx.compose.runtime.Composable")
            appendLine("import androidx.compose.runtime.mutableStateOf")
            appendLine("import androidx.compose.runtime.remember")
            appendLine("import java.util.Locale")
            appendLine()
            appendLine("private interface FYTxtGroup{val name:String;val stats:Map<out FYTxtTag,Double>}")
            appendLine("private interface FYTxtTag{val name:String;val pattern:Regex?}")
            appendLine("private object FYTxtConfig{")
            appendLine("private val activeGroupState=mutableStateOf<FYTxtGroup?>(null)")
            appendLine("private val activeTagsState=mutableStateOf<List<FYTxtTag>>(emptyList())")
            appendLine("val activeGroup get()=activeGroupState.value")
            appendLine("val activeTags get()=activeTagsState.value")
            appendLine("fun update(group:FYTxtGroup,tags:List<FYTxtTag>){activeGroupState.value=group;activeTagsState.value=tags}")
            appendLine("fun init(group:FYTxtGroup,tags:List<FYTxtTag>)=update(group,tags)")
            appendLine("}")
            appendLine("private fun String.fmt(args:Array<out Any?>)=if(args.isEmpty())this else format(*args)")
            appendLine("@Composable")
            appendLine("private inline fun <T> observeFYTxt(crossinline block:()->T):T{")
            appendLine("val group=FYTxtConfig.activeGroup")
            appendLine("val tags=FYTxtConfig.activeTags")
            appendLine("return remember(group,tags){block()}")
            appendLine("}")
            appendLine()
            appendLine("${visibilityPrefix()}object`${config.objectName}`{")

            fun countTexts(node: FVVV): Int = if (node.`is`<String>()) 1 else node.nodes.values.sumOf(::countTexts)

            val totalTexts = countTexts(defaultLanguageRoot).toDouble()
            appendLine(
                "${visibilityPrefix()}enum class`${config.objectName}Groups`:FYTxtGroup{" + parsedLanguages.joinToString(
                    ","
                ) { (groupName, languageMap) ->
                    "`$groupName`{override val stats=mapOf(" + languageMap.map { (tag, node) ->
                        "`${config.objectName}`.`${config.objectName}Tags`.$tag to ${countTexts(node) / totalTexts}"
                    }.joinToString(",") + ")}"
                } + "}")
            appendLine(
                "${visibilityPrefix()}enum class`${config.objectName}Tags`:FYTxtTag{" + languageTags.joinToString(
                    ","
                ) { languageTag ->
                    "$languageTag{override val pattern=Regex(\"\"\"${buildLanguagePattern(languageTag)}\"\"\",RegexOption.IGNORE_CASE)}"
                } + "}")
            appendLine("private val defaultGroup get()=`${config.objectName}Groups`.`${commonLanguageGroup.first}`")
            appendLine("private val defaultTag get()=`${config.objectName}Tags`.$defaultLanguage")
            appendLine("private fun prioritize(primary:`${config.objectName}Tags`,includeFallbacks:Boolean=true): List<`${config.objectName}Tags`> = buildList{")
            appendLine("add(primary)")
            appendLine("if(includeFallbacks){addAll(`${config.objectName}Tags`.entries.filterNot{it==primary})}")
            appendLine("}")
            appendLine("private fun normalizeLanguageTag(languageTag:String):String=languageTag.trim().replace('_','-')")
            appendLine("val activeGroup:`${config.objectName}Groups`")
            appendLine("get()=(FYTxtConfig.activeGroup as? `${config.objectName}Groups`)?:defaultGroup")
            appendLine("val activeTags: List<`${config.objectName}Tags`>")
            appendLine("get() = FYTxtConfig.activeTags.mapNotNull{it as? `${config.objectName}Tags`}.ifEmpty{prioritize(defaultTag)}")
            appendLine("fun currentTags(): List<`${config.objectName}Tags`> = activeTags")
            appendLine("fun resolveTag(languageTag:String?):`${config.objectName}Tags`?=")
            appendLine("languageTag?.trim()?.takeIf{it.isNotEmpty()}?.let{normalized->`${config.objectName}Tags`.entries.firstOrNull{tag->tag.pattern?.matches(normalizeLanguageTag(normalized))==true}}")
            appendLine("fun resolveTag(locale:Locale):`${config.objectName}Tags`?=resolveTag(locale.toLanguageTag())")
            appendLine("fun resolveTags(languageTag:String?): List<`${config.objectName}Tags`> = prioritize(resolveTag(languageTag)?:defaultTag)")
            appendLine("fun resolveTags(locale:Locale): List<`${config.objectName}Tags`> = resolveTags(locale.toLanguageTag())")
            appendLine("private fun normalizeTags(tags:List<`${config.objectName}Tags`>,includeFallbacks:Boolean=true): List<`${config.objectName}Tags`>{")
            appendLine("val distinctTags=tags.distinct()")
            appendLine("if(distinctTags.isEmpty())return prioritize(defaultTag)")
            appendLine("if(!includeFallbacks)return distinctTags")
            appendLine("return buildList{")
            appendLine("addAll(distinctTags)")
            appendLine("addAll(`${config.objectName}Tags`.entries.filterNot{it in distinctTags})")
            appendLine("}")
            appendLine("}")
            appendLine("fun update(group:`${config.objectName}Groups`=activeGroup,tags: List<`${config.objectName}Tags`> = activeTags){")
            appendLine("FYTxtConfig.update(group,normalizeTags(tags,includeFallbacks=false))")
            appendLine("}")
            appendLine("fun updateGroup(group:`${config.objectName}Groups`){")
            appendLine("update(group,activeTags)")
            appendLine("}")
            appendLine("fun updateTags(tags: List<`${config.objectName}Tags`>,group:`${config.objectName}Groups`=activeGroup,includeFallbacks:Boolean=true){")
            appendLine("update(group,normalizeTags(tags,includeFallbacks))")
            appendLine("}")
            appendLine("fun updateTagNames(tagNames: List<String>,group:`${config.objectName}Groups`=activeGroup,includeFallbacks:Boolean=true){")
            appendLine("updateTags(tagNames.mapNotNull(::resolveTag),group,includeFallbacks)")
            appendLine("}")
            appendLine("fun updateLocale(locale:Locale?,group:`${config.objectName}Groups`=activeGroup,includeFallbacks:Boolean=true){")
            appendLine("val nextLocale=locale?:Locale.getDefault()")
            appendLine("updateTags(listOfNotNull(resolveTag(nextLocale)),group,includeFallbacks)")
            appendLine("}")
            appendLine("fun updateLocales(locales: List<Locale>,group:`${config.objectName}Groups`=activeGroup,includeFallbacks:Boolean=true){")
            appendLine("updateTags(locales.mapNotNull(::resolveTag),group,includeFallbacks)")
            appendLine("}")
            appendLine("fun useTag(tag:`${config.objectName}Tags`,group:`${config.objectName}Groups`=activeGroup,includeFallbacks:Boolean=true){")
            appendLine("updateTags(listOf(tag),group,includeFallbacks)")
            appendLine("}")
            appendLine("fun useLanguageTag(languageTag:String?,group:`${config.objectName}Groups`=activeGroup,includeFallbacks:Boolean=true){")
            appendLine("updateTagNames(listOfNotNull(languageTag),group,includeFallbacks)")
            appendLine("}")
            appendLine("fun useLocale(locale:Locale,group:`${config.objectName}Groups`=activeGroup,includeFallbacks:Boolean=true){")
            appendLine("updateLocale(locale,group,includeFallbacks)")
            appendLine("}")
            appendLine("fun reset(group:`${config.objectName}Groups`=defaultGroup){")
            appendLine("updateLocale(null,group)")
            appendLine("}")
            appendLine("init{updateLocale(Locale.getDefault(),defaultGroup)}")
            appendLine()

            fun StringBuilder.writeNodes(currentNodes: Map<String, FVVV>, path: List<String> = emptyList()) {
                currentNodes.forEach { (nodeKey, nodeValue) ->
                    if (nodeValue.nodes.isEmpty()) {
                        val textVariantsByGroup = parsedLanguages.associate { (groupName, languageMap) ->
                            groupName to languageMap.mapValues { (_, rootNode) ->
                                path.fold(rootNode) { currentNode, segment -> currentNode[segment] }[nodeKey].`as`<String>()
                            }
                        }

                        val commonTexts = textVariantsByGroup.getValue(commonLanguageGroup.first)
                        val missingTagsByGroup = parsedLanguages.mapNotNull { (groupName, _) ->
                            languageTags.filter { languageTag -> textVariantsByGroup.getValue(groupName)[languageTag] == null }
                                .joinToString(", ").takeIf { it.isNotEmpty() }?.let { "$groupName: $it" }
                        }.joinToString(" | ")

                        if (missingTagsByGroup.isNotEmpty()) {
                            println("[locale] ${path.joinToString(".")}.${nodeKey} NA: $missingTagsByGroup")
                        }

                        val defaultText = commonTexts.getValue(defaultLanguage) ?: ""
                        val escapedTip = defaultText.replace("\n", "\n*")

                        appendLine("/**$escapedTip")
                        if (missingTagsByGroup.isNotEmpty()) {
                            appendLine("*@suppress $missingTagsByGroup")
                        }
                        appendLine("*/")
                        appendLine("val`$nodeKey`get()=FYTxtConfig.activeTags.firstNotNullOfOrNull{activeTag->")
                        appendLine("val localeTag=activeTag as? `${config.objectName}`.`${config.objectName}Tags` ?: return@firstNotNullOfOrNull null")
                        if (variantLanguageGroups.isNotEmpty()) {
                            appendLine("when(FYTxtConfig.activeGroup as? `${config.objectName}`.`${config.objectName}Groups`){")
                            variantLanguageGroups.forEach { (groupName, _) ->
                                appendLine("`${config.objectName}`.`${config.objectName}Groups`.`$groupName`->when(localeTag){")
                                textVariantsByGroup.getValue(groupName).forEach { (languageTag, textValue) ->
                                    if (textValue != null) {
                                        appendLine(
                                            "`${config.objectName}`.`${config.objectName}Tags`.$languageTag->\"\"\"${
                                                escapeTripleQuoted(
                                                    textValue
                                                )
                                            }\"\"\""
                                        )
                                    }
                                }
                                appendLine("else->null}")
                            }
                            appendLine("else->null}?:")
                        }
                        appendLine("when(localeTag){")
                        commonTexts.forEach { (languageTag, textValue) ->
                            if (textValue != null) {
                                appendLine(
                                    "`${config.objectName}`.`${config.objectName}Tags`.$languageTag->\"\"\"${
                                        escapeTripleQuoted(
                                            textValue
                                        )
                                    }\"\"\""
                                )
                            }
                        }
                        appendLine("else -> null}")
                        appendLine("}?:\"\"\"${escapeTripleQuoted(defaultText)}\"\"\"")

                        if (config.generateComposeApis) {
                            appendLine("/**$escapedTip")
                            if (missingTagsByGroup.isNotEmpty()) {
                                appendLine("*@suppress $missingTagsByGroup")
                            }
                            appendLine("*/")
                            appendLine("@Composable")
                            appendLine("fun`$nodeKey`(vararg args:Any?)=observeFYTxt{`$nodeKey`.fmt(args)}")
                        }
                    } else {
                        appendLine("object`$nodeKey`{")
                        writeNodes(nodeValue.nodes, path + nodeKey)
                        appendLine("}")
                    }
                }
            }

            writeNodes(defaultLanguageRoot.nodes)
            append('}')
        }
    }

    private fun escapeTripleQuoted(text: String): String = text.replace("\"\"\"", "\\\"\\\"\\\"")

    private fun buildLanguagePattern(languageTag: String): String {
        val segments = languageTag.lowercase().split(Regex("[-_]")).filter { it.isNotBlank() }
        val normalized = segments.joinToString("[-_]") { Regex.escape(it) }
        return "^$normalized(?:[-_].*)?$"
    }

    private fun visibilityPrefix(): String = if (config.generateInternalClasses) "internal " else ""
}

fun printUsage() {
    println(
        """
        FlyCat Locale Generator

        Usage: kotlin scripts/generate-locale.main.kts [repositoryRoot]

        Generates:
          locale/build/generated/fytxt/kotlin/commonMain/kotlin/fytxt.kt
        """.trimIndent()
    )
}

fun main(args: Array<String>) {
    if (args.contains("--help")) {
        printUsage()
        return
    }

    val config = LocaleProjectConfig(args)
    LocaleGenerator(config).generate()
}

main(args)
