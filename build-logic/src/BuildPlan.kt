package dev.flycat.build

data class BuildRequest(
    val productName: String,
    val applicationId: String,
    val baseVersionName: String,
    val uiBuildId: String,
    val buildHash: String? = null,
    val buildBranch: String? = null,
    val withExtension: Boolean = false,
    val outputPrefix: String? = null,
    val outputChannel: String? = null,
)

data class BuildPlan(
    val applicationId: String,
    val versionCode: Int,
    val versionName: String,
    val baseVersionName: String,
    val outputPrefix: String,
    val geoSegment: String,
    val outputChannel: String?,
    val uiBuildId: String,
) {
    /** <prefix>-<geo>[-<channel>]-<abi>-release-<uiBuildId>.apk（与 reusable-prepare-publish.yml 保持同步）。 */
    fun apkFileName(abi: String): String =
        buildList {
            add(outputPrefix)
            add(geoSegment)
            add(abi)
            outputChannel?.let(::add)
            add("release")
            add(uiBuildId)
        }.joinToString("-") + ".apk"

    /** extension-<abi>-<variant>-<uiBuildId>.apk */
    fun extensionApkFileName(abi: String, variant: String): String = "extension-$abi-$variant-$uiBuildId.apk"
}

fun planBuild(request: BuildRequest): BuildPlan {
    val digits = request.uiBuildId.takeWhile { it.isDigit() }.take(8)
    require(digits.length == 8) { "update.uiBuildId must start with a yyyyMMdd build stamp" }
    val versionCode = digits.toInt()
    val hash = request.buildHash.nonBlank()?.take(8)
    // 分支段规范化（必须与 reusable-prepare-publish.yml 保持同步）：小写，每段连续的非 [a-z0-9] 字符合并为单个 '-'，去掉开头和结尾的 '-'。
    val branch = request.buildBranch?.lowercase()?.replace(Regex("[^a-z0-9]+"), "-")?.trim('-')?.nonBlank()
    return BuildPlan(
        applicationId = request.applicationId,
        versionCode = versionCode,
        versionName = hash?.let { listOfNotNull(request.baseVersionName, branch, it).joinToString(".") }
            ?: request.baseVersionName,
        baseVersionName = request.baseVersionName,
        outputPrefix = request.outputPrefix.nonBlank() ?: request.productName,
        geoSegment = if (request.withExtension) "builtin_Extension" else "builtin",
        outputChannel = request.outputChannel.nonBlank(),
        uiBuildId = request.uiBuildId,
    )
}

private fun String?.nonBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
