package dev.yume.packer

import java.io.File
import java.util.zip.ZipFile
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "The output depends on the installed Android SDK toolchain")
abstract class BuildLoaderDexTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val loaderAar: RegularFileProperty

    @get:Classpath
    abstract val runtimeArtifacts: ConfigurableFileCollection

    @get:Internal
    abstract val sdkDirectory: DirectoryProperty

    @get:Input
    abstract val minSdk: Property<Int>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun buildDex() {
        val output = outputDirectory.get().asFile
        output.deleteRecursively()
        output.mkdirs()

        val inputs = mutableListOf<File>()
        inputs += extractClassesJar(loaderAar.get().asFile, "loader")
        runtimeArtifacts.files.sortedBy(File::getName).forEachIndexed { index, artifact ->
            inputs += if (artifact.extension == "aar") {
                extractClassesJar(artifact, "dependency-$index")
            } else {
                artifact
            }
        }

        val sdk = sdkDirectory.get().asFile
        val buildTools = sdk.resolve("build-tools").listFiles()
            ?.filter(File::isDirectory)
            ?.maxWithOrNull { left, right -> compareVersions(left.name, right.name) }
            ?: error("No Android build-tools installation found under $sdk")
        val platform = sdk.resolve("platforms").listFiles()
            ?.filter { it.isDirectory && it.resolve("android.jar").isFile }
            ?.maxWithOrNull { left, right ->
                compareVersions(left.name.removePrefix("android-"), right.name.removePrefix("android-"))
            }
            ?: error("No Android platform installation found under $sdk")
        val d8Jar = buildTools.resolve("lib/d8.jar")
        check(d8Jar.isFile) { "D8 not found: $d8Jar" }

        val command = mutableListOf(
            javaExecutable(),
            "-cp",
            d8Jar.absolutePath,
            "com.android.tools.r8.D8",
            "--release",
            "--min-api",
            minSdk.get().toString(),
            "--lib",
            platform.resolve("android.jar").absolutePath,
            "--output",
            output.absolutePath,
        )
        command += inputs.map(File::getAbsolutePath)
        runCommand(command, "D8 failed while building the loader DEX")
        check(output.resolve("classes.dex").isFile) { "D8 did not produce classes.dex" }
    }

    private fun extractClassesJar(aar: File, stem: String): File {
        val destination = temporaryDir.resolve("$stem-classes.jar")
        ZipFile(aar).use { zip ->
            val entry = zip.getEntry("classes.jar") ?: error("classes.jar missing from $aar")
            zip.getInputStream(entry).use { input ->
                destination.outputStream().use(input::copyTo)
            }
        }
        return destination
    }

    private fun compareVersions(left: String, right: String): Int {
        val a = left.split('.', '-', '_').map { it.toIntOrNull() ?: 0 }
        val b = right.split('.', '-', '_').map { it.toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(a.size, b.size)) {
            val comparison = (a.getOrElse(index) { 0 }).compareTo(b.getOrElse(index) { 0 })
            if (comparison != 0) return comparison
        }
        return 0
    }

    private fun javaExecutable(): String =
        File(System.getProperty("java.home"), "bin/java").absolutePath

    private fun runCommand(command: List<String>, failure: String) {
        val process = ProcessBuilder(command).inheritIO().start()
        check(process.waitFor() == 0) { failure }
    }
}
