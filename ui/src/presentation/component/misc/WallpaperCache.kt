/*
 * This file is part of FlyCat.
 *
 * FlyCat is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * Copyright (c) YumeYucca 2025 - Present
 * Based on YumeBox by YumeYucca
 */

package com.github.lmfirefly.flycat.presentation.component.misc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.github.panpf.sketch.cache.CachePolicy
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.resize.Precision
import com.github.panpf.sketch.resize.Scale
import java.io.File
import java.io.InputStream
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val BUNDLED_WALLPAPER_URI = "file:///android_asset/wallpaper.jpg"

/** file:// 模型去掉 `#v=` 内容版本后缀后的磁盘路径；内置资源（/android_asset/）与非 file:// 模型返回 null。 */
private fun localFilePathOf(model: String): String? = if (model.startsWith("file://") && !model.startsWith("file:///android_asset/")) { model.removePrefix("file://").substringBefore('#') } else { null }

/** 将壁纸设置解析为实际可加载的 model：空值或失效的本地副本回退到内置壁纸，content:// 不可读同样回退。 */
fun resolveWallpaperModel(context: Context, uri: String): String {
    if (uri.isBlank()) return BUNDLED_WALLPAPER_URI
    localFilePathOf(uri)?.let { path -> return if (File(path).exists()) uri else BUNDLED_WALLPAPER_URI }
    if (uri.startsWith("file:///android_asset/")) return uri
    val readable = runCatching { context.contentResolver.openInputStream(Uri.parse(uri))?.use { true } ?: false }.getOrDefault(false)
    return if (readable) uri else BUNDLED_WALLPAPER_URI
}

private fun openWallpaperStream(context: Context, model: String): InputStream? = if (model.startsWith("file:///android_asset/")) {
    context.assets.open(model.removePrefix("file:///android_asset/"))
} else { context.contentResolver.openInputStream(Uri.parse(model)) }

private fun ExifInterface?.exifOrientationOrNormal(): Int = this?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) ?: ExifInterface.ORIENTATION_NORMAL

private fun readExifOrientation(context: Context, model: String): Int = runCatching {
    if (model.startsWith("file:///android_asset/")) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { context.assets.open(model.removePrefix("file:///android_asset/")).use { ExifInterface(it) } } else { null } } else {
        val path = localFilePathOf(model)
        if (path != null) { ExifInterface(path) } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { context.contentResolver.openInputStream(Uri.parse(model))?.use { ExifInterface(it) } } else { null }
    }.exifOrientationOrNormal()
}.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

/**
 * 只读图片头取显示宽高（不解码像素）。本地文件/内置资源/content:// 均支持，失败返回 null。
 * Sketch/BitmapFactory 解码会应用 EXIF 旋转，这里必须按 EXIF 方向修正宽高，否则相机竖拍照片的取景比例会算错。
 */
fun readWallpaperBounds(context: Context, model: String): Pair<Int, Int>? = runCatching {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    if (model.startsWith("file:///android_asset/")) { context.assets.open(model.removePrefix("file:///android_asset/")).use { BitmapFactory.decodeStream(it, null, options) } } else {
        val path = localFilePathOf(model)
        if (path != null) { BitmapFactory.decodeFile(path, options) } else { context.contentResolver.openInputStream(Uri.parse(model))?.use { BitmapFactory.decodeStream(it, null, options) } }
    }
    if (options.outWidth <= 0 || options.outHeight <= 0) return@runCatching null
    val orientation = readExifOrientation(context, model)
    // 5-8 为需要交换宽高的旋转/转置方向
    if (orientation in 5..8) options.outHeight to options.outWidth else options.outWidth to options.outHeight
}.getOrNull()

/**
 * 壁纸导入会覆盖写入同一个固定路径文件；缓存键必须携带内容版本，否则换壁纸后缓存会命中旧图。
 * model 自带 `#v=` 后缀（见 MoeWallpaperImporter）时直接以其为键；备份还原等场景的裸路径退化为 lastModified 版本。
 */
fun wallpaperCacheKey(model: String): String {
    if (model.contains('#')) return model
    val version = localFilePathOf(model)?.let { File(it).lastModified().toString() } ?: "0"
    return "$model#$version"
}

/** 等比「盖满」适配：输出保持源图完整画幅（不裁切，偏置取景依赖完整画幅），分辨率按盖满 box 的需要取，长边封顶控制解码成本。 */
fun coverFitSize(srcWidth: Int, srcHeight: Int, boxWidth: Int, boxHeight: Int, multiplier: Float = 1f, maxLongSide: Int = 2560): Pair<Int, Int> {
    val scale = max(boxWidth * multiplier / srcWidth, boxHeight * multiplier / srcHeight)
    var width = (srcWidth * scale).toInt()
    var height = (srcHeight * scale).toInt()
    val longSide = max(width, height)
    if (longSide > maxLongSide) {
        width = width * maxLongSide / longSide
        height = height * maxLongSide / longSide
    }
    return max(width, 1) to max(height, 1)
}

/** 壁纸异步加载请求的构造入口（仅在无法预解码时兜底）：等比盖满解码（保持完整画幅）＋显式缓存键。 */
fun wallpaperImageRequest(context: Context, model: String, cacheKey: String, bounds: Pair<Int, Int>?, boxWidth: Int, boxHeight: Int, multiplier: Float = 1f): ImageRequest {
    val fallback = max(1, (boxWidth * multiplier).toInt()) to max(1, (boxHeight * multiplier).toInt())
    val (width, height) = if (bounds != null) coverFitSize(bounds.first, bounds.second, boxWidth, boxHeight, multiplier) else fallback
    return ImageRequest(context, model) {
        scale(Scale.CENTER_CROP)
        size(width, height)
        precision(Precision.LESS_PIXELS)
        if (bounds != null) {
            memoryCachePolicy(CachePolicy.ENABLED)
            resultCachePolicy(CachePolicy.ENABLED)
            memoryCacheKey(cacheKey)
            resultCacheKey(cacheKey)
        } else {
            // 尺寸未知时只能按 box 比例解码，画幅不完整不能进共享缓存，否则会污染其他消费方的取景。
            memoryCachePolicy(CachePolicy.DISABLED)
            resultCachePolicy(CachePolicy.DISABLED)
        }
        downloadCachePolicy(CachePolicy.DISABLED)
    }
}

private fun decodeWallpaperSync(context: Context, model: String, bounds: Pair<Int, Int>, targetWidth: Int, targetHeight: Int): ImageBitmap? = runCatching {
    var sample = 1
    while (bounds.first / (sample * 2) >= targetWidth && bounds.second / (sample * 2) >= targetHeight) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val raw = openWallpaperStream(context, model)?.use { BitmapFactory.decodeStream(it, null, options) }?: localFilePathOf(model)?.let { BitmapFactory.decodeFile(it, options) }?: return@runCatching null
    val matrix = Matrix()
    when (readExifOrientation(context, model)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f)
            matrix.postScale(-1f, 1f)
        }
    }
    var bitmap: Bitmap = if (matrix.isIdentity) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
    // 只缩不放（与 LESS_PIXELS 语义一致）：target 本身就是等比 cover-fit 尺寸
    if (bitmap.width > targetWidth || bitmap.height > targetHeight) { bitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true) }
    bitmap.asImageBitmap()
}.getOrNull()

/**
 * 壁纸解码位图的唯一取用入口（进程内单槽缓存）：
 * 1. 命中槽内缓存 → 直接返回，首帧同步绘制，不出现「垫色 → 淡入」；
 * 2. 未命中 → 当场同步解码（等比盖满、EXIF 旋转）并入槽。同步解码发生在首帧组合期间，耗时被系统 Splash 吸收，用户看到的第一帧 UI 就已带壁纸。启动预热（[warmUp]）只是把这次解码提前到后台。
 */
object WallpaperDecodeCache {
    @Volatile private var slotKey: String? = null
    @Volatile private var slotBitmap: ImageBitmap? = null
    fun imageBitmap(context: Context, model: String, cacheKey: String, bounds: Pair<Int, Int>?, boxWidth: Int, boxHeight: Int, multiplier: Float = 1f): ImageBitmap? {
        slotBitmap?.let { if (slotKey == cacheKey) return it }
        if (bounds == null) return null
        val (width, height) = coverFitSize(bounds.first, bounds.second, boxWidth, boxHeight, multiplier)
        val decoded = decodeWallpaperSync(context, model, bounds, width, height) ?: return null
        slotKey = cacheKey
        slotBitmap = decoded
        return decoded
    }
    suspend fun warmUp(context: Context, model: String, boxWidth: Int, boxHeight: Int) {
        withContext(Dispatchers.IO) {
            imageBitmap(
                context = context,
                model = model,
                cacheKey = wallpaperCacheKey(model),
                bounds = readWallpaperBounds(context, model),
                boxWidth = boxWidth,
                boxHeight = boxHeight,
                multiplier = 1.2f,
            )
        }
    }
}
