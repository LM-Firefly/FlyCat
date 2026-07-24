@file:DependsOn("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.0.21")

import java.io.File
import java.io.IOException
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtPsiFactory

val DRY_RUN = args.contains("--dry-run")
val VERBOSE = args.contains("--verbose")

val rootPath = args.firstOrNull { !it.startsWith("--") } ?: "."
val root = File(rootPath).absoluteFile

if (!root.exists()) {
    println("Error: Directory not found: $rootPath")
    System.exit(1)
}

println("Removing ALL comments from: $root")

println("Options: dryRun=$DRY_RUN")

println()

setIdeaIoUseFallback()

val disposable: Disposable = Disposer.newDisposable()

val compilerConfig =
    CompilerConfiguration().apply {
        put(CLIConfigurationKeys.ALLOW_KOTLIN_PACKAGE, true)
    }

val environment =
    KotlinCoreEnvironment.createForProduction(
        disposable,
        compilerConfig,
        EnvironmentConfigFiles.JVM_CONFIG_FILES,
    )

val psiFactory = KtPsiFactory(environment.project, markGenerated = false)

var totalFiles = 0
var totalComments = 0
var totalSaved = 0L

val files =
    root.walkTopDown().filter { it.extension == "kt" }.filter { "build" !in it.path }.toList()

println("Found ${files.size} Kotlin files")

println()

for (file in files) {
    try {
        val result = processFile(file, psiFactory)
        if (result != null) {
            totalFiles++
            totalComments += result.commentsRemoved
            totalSaved += result.charsSaved
        }
    } catch (e: IOException) {
        println("[ERROR] ${file.path}: ${e.message}")
        if (VERBOSE) e.printStackTrace()
    }
}

Disposer.dispose(disposable)

println()

println("=== Summary ===")

println("Files processed: $totalFiles")

println("Comments removed: $totalComments")

println("Characters saved: $totalSaved")

if (DRY_RUN) {
    println("(DRY RUN - no files were modified)")
}

data class ProcessResult(val commentsRemoved: Int, val charsSaved: Long)

fun processFile(file: File, psiFactory: KtPsiFactory): ProcessResult? {
    var text = file.readText()
    val originalLength = text.length

    text = removeKDocComments(text)
    text = removeBlockComments(text)
    text = removeLineComments(text)
    text = cleanUpEmptyLines(text)

    val charsSaved = originalLength - text.length
    val commentsRemoved = countCommentsRemoved(file.readText(), text)

    if (charsSaved <= 0) {
        return null
    }

    if (DRY_RUN) {
        println(
            "[DRY RUN] ${file.path}: would remove $commentsRemoved comments ($charsSaved chars)"
        )
    } else {
        file.writeText(text)
        println("[PROCESSED] ${file.path}: removed $commentsRemoved comments ($charsSaved chars)")
    }

    return ProcessResult(commentsRemoved, charsSaved.toLong())
}

fun removeKDocComments(text: String): String {
    var result = text
    val kdocPattern = Regex("""/\*\*[\s\S]*?\*/""")

    while (kdocPattern.containsMatchIn(result)) {
        result =
            kdocPattern.replace(result) { matchResult ->
                val match = matchResult.value
                val start = matchResult.range.first
                val before = result.substring(0, start)
                val after = result.substring(start + match.length)

                val lineStart = before.lastIndexOf('\n')
                val beforeLine = if (lineStart >= 0) before.substring(0, lineStart + 1) else ""
                val lineContent = if (lineStart >= 0) before.substring(lineStart + 1) else before

                val afterFirstLine = after.indexOf('\n')
                val afterLineContent =
                    if (afterFirstLine >= 0) after.substring(0, afterFirstLine) else after
                val afterRest = if (afterFirstLine >= 0) after.substring(afterFirstLine) else ""

                if (lineContent.isBlank() && afterLineContent.isBlank()) {
                    ""
                } else {
                    ""
                }
            }
    }

    return result
}

fun removeBlockComments(text: String): String {
    var result = text
    val blockPattern = Regex("""/\*[\s\S]*?\*/""")

    while (blockPattern.containsMatchIn(result)) {
        result = blockPattern.replace(result, "")
    }

    return result
}

fun removeLineComments(text: String): String {
    val lines = text.lines()
    val result = StringBuilder()

    for (line in lines) {
        val trimmed = line.trimStart()
        if (trimmed.startsWith("//")) {
            continue
        }

        val commentIndex = line.indexOf("//")
        if (commentIndex > 0) {
            val beforeComment = line.substring(0, commentIndex).trimEnd()
            result.append(beforeComment).append("\n")
        } else {
            result.append(line).append("\n")
        }
    }

    return result.toString()
}

fun cleanUpEmptyLines(text: String): String {
    var result = text

    while (result.contains("\n\n\n")) {
        result = result.replace("\n\n\n", "\n\n")
    }

    while (result.contains("\r\n\r\n\r\n")) {
        result = result.replace("\r\n\r\n\r\n", "\r\n\r\n")
    }

    while (result.contains(" \n")) {
        result = result.replace(" \n", "\n")
    }

    while (result.contains(" \r\n")) {
        result = result.replace(" \r\n", "\r\n")
    }

    return result.trimEnd() + "\n"
}

fun countCommentsRemoved(original: String, processed: String): Int {
    val originalKdoc = Regex("""/\*\*[\s\S]*?\*/""").findAll(original).count()
    val originalBlock = Regex("""/\*[\s\S]*?\*/""").findAll(original).count() - originalKdoc
    val originalLine = original.lines().count { it.trimStart().startsWith("//") }

    return originalKdoc + originalBlock + originalLine
}
