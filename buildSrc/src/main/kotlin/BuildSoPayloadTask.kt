import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream

/**
 * 将非引导原生库原地替换为 XZ 压缩内容（文件名不变），显著缩小 APK（XZ/LZMA2 压缩比远高于 APK 的 deflate）。
 * 保留文件名是关键：strip/package 的输入清单会被 Gradle 缓存固化，改名/删除会让下游任务 No such file。
 * split APK 按 ABI 过滤 lib/（每个分包只携带本 ABI）；运行时由 core 的 NativeLibraryLoader 按 XZ 魔数探测
 * 解载到私有目录并注入 classloader 的 native library 搜索路径。
 * 引导库保留原 ELF：libprivkitstarter.so 必须可被 priv-kit 以 linker 直载或安装解压路径启动。
 */
@DisableCachingByDefault(because = "The output depends on the input library set")
abstract class BuildSoPayloadTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputLibs: ConfigurableFileCollection
    @TaskAction
    fun pack() {
        inputLibs.files.filter { it.isDirectory }.forEach { inputRoot ->
            inputRoot.walkTopDown().filter { it.isFile }.toList().forEach { file ->
                val relative = file.relativeTo(inputRoot).invariantSeparatorsPath
                // 兼容 inputRoot 位于任务根（relative=out/lib/...）或 out 层级（relative=lib/...）两种形态：
                // Gradle outputs.files.singleFile 的层级不固定，按路径含 lib/ 段判定而非前缀。
                if ((!relative.startsWith("lib/") && !relative.contains("/lib/")) || BOOTSTRAP_LIBRARIES.contains(file.name)) {
                    return@forEach
                }
                val original = file.readBytes()
                file.outputStream().use { out ->
                    XZOutputStream(out, LZMA2Options(6)).use { it.write(original) }
                }
            }
        }
    }
    companion object {
        val BOOTSTRAP_LIBRARIES = setOf("libprivkitstarter.so")
    }
}
