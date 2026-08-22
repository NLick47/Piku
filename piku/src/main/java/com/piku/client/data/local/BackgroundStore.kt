package com.piku.client.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class SavedBackground(
    val path: String,
    val scrimDark: Int?,
    val scrimLight: Int?,
    val imgWidth: Int = 0,
    val imgHeight: Int = 0,
)

@Singleton
class BackgroundStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val backgroundDir: File
        get() = File(context.filesDir, "background").apply { mkdirs() }

    suspend fun saveFromUri(uri: Uri): SavedBackground? = withContext(Dispatchers.IO) {
        try {
            saveInternal(uri)
        } catch (t: Throwable) {
            Log.d(TAG, "saveFromUri: exception", t)
            null
        }
    }


    suspend fun saveBackdropFromUri(uri: Uri): SavedBackground? = withContext(Dispatchers.IO) {
        try {
            saveInternal(uri, prefix = BACKDROP_PREFIX, extractScrim = false)
        } catch (t: Throwable) {
            Log.d(TAG, "saveBackdropFromUri: exception", t)
            null
        }
    }

    private fun saveInternal(
        uri: Uri,
        prefix: String = HERO_PREFIX,
        extractScrim: Boolean = true,
    ): SavedBackground? {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsInput = resolver.openInputStream(uri)
        if (boundsInput == null) {
            Log.d(TAG, "saveFromUri: openInputStream(null) uri=$uri")
            return null
        }
        boundsInput.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Log.d(
                TAG,
                "saveFromUri: undecodable w=${bounds.outWidth} h=${bounds.outHeight} " +
                    "mime=${bounds.outMimeType}",
            )
            return null
        }
        val imgWidth = bounds.outWidth
        val imgHeight = bounds.outHeight


        val stamp = System.currentTimeMillis()
        var target = File(backgroundDir, "${prefix}_$stamp.${extOf(bounds.outMimeType)}")
        var tmp = File(backgroundDir, target.name + ".tmp")

        val copyInput = resolver.openInputStream(uri)
        if (copyInput == null) {
            Log.d(TAG, "saveFromUri: copy openInputStream(null)")
            return null
        }
        copyInput.use { input ->
            tmp.outputStream().use { output -> input.copyTo(output, COPY_BUFFER_BYTES) }
        }

        if (tmp.length() > MAX_FILE_BYTES) {
            Log.d(TAG, "saveFromUri: too large ${tmp.length()}, re-encoding")
            val scaled = decodeScaled(uri)
            if (scaled != null) {
                val jpgTarget = File(backgroundDir, "${prefix}_$stamp.jpg")
                val jpgTmp = File(backgroundDir, jpgTarget.name + ".tmp")
                jpgTmp.outputStream().use { output ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
                }
                scaled.recycle()
                tmp.delete()
                tmp = jpgTmp
                target = jpgTarget
            } else {
                Log.d(TAG, "saveFromUri: re-encode failed, keeping original")
            }
        }

        if (!tmp.renameTo(target)) {
            Log.d(TAG, "saveFromUri: renameTo failed ${tmp.name}")
            tmp.delete()
            return null
        }

        backgroundDir.listFiles { f ->
            f.name.startsWith("${prefix}_") && f.name != target.name
        }?.forEach { it.delete() }

        val (scrimDark, scrimLight) = if (extractScrim) extractScrims(uri) else null to null
        return SavedBackground(target.absolutePath, scrimDark, scrimLight, imgWidth, imgHeight)
    }


    private fun extractScrims(uri: Uri): Pair<Int?, Int?> {
        return try {
            val bitmap = decodeSmall(uri, PALETTE_SIDE) ?: return null to null
            val palette = Palette.from(bitmap).maximumColorCount(24).generate()
            bitmap.recycle()

            fun clampL(color: Int, lMin: Float, lMax: Float): Int {
                val hsl = FloatArray(3)
                ColorUtils.colorToHSL(color, hsl)
                hsl[2] = hsl[2].coerceIn(lMin, lMax)
                return ColorUtils.HSLToColor(hsl)
            }

            val darkSwatch = palette.darkMutedSwatch
                ?: palette.darkVibrantSwatch
                ?: palette.mutedSwatch
                ?: palette.dominantSwatch
            val lightSwatch = palette.lightMutedSwatch
                ?: palette.lightVibrantSwatch
                ?: palette.vibrantSwatch
                ?: palette.dominantSwatch

            val scrimDark = darkSwatch?.let { clampL(it.rgb, 0f, DARK_SCRIM_L_MAX) }
            val scrimLight = lightSwatch?.let { clampL(it.rgb, LIGHT_SCRIM_L_MIN, 1f) }
            Log.d(TAG, "extractScrims: dark=$scrimDark light=$scrimLight")
            scrimDark to scrimLight
        } catch (t: Throwable) {
            Log.d(TAG, "extractScrims: exception", t)
            null to null
        }
    }

    /** 解码到最长边 [side] 内的小图（用于取色等低精度场景） */
    private fun decodeSmall(uri: Uri, side: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= side) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, opts)
        }
    }

    /** 删除自定义背景文件 */
    suspend fun clear() = withContext(Dispatchers.IO) {
        runCatching {
            backgroundDir.listFiles()?.forEach { it.delete() }
        }
    }

    private fun decodeScaled(uri: Uri): Bitmap? {
        val decoded = decodeSmall(uri, MAX_SIDE) ?: return null
        // inSampleSize 只能按 2 的幂缩，最后一步精确缩到 MAX_SIDE 内
        val longest = maxOf(decoded.width, decoded.height)
        if (longest <= MAX_SIDE) return decoded
        val scale = MAX_SIDE.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled != decoded) decoded.recycle()
        return scaled
    }

    private fun extOf(mime: String?): String = when {
        mime == null -> "jpg"
        mime.contains("png") -> "png"
        mime.contains("webp") -> "webp"
        mime.contains("gif") -> "gif"
        mime.contains("heic") || mime.contains("heif") -> "heic"
        mime.contains("avif") -> "avif"
        else -> "jpg"
    }

    companion object {
        private const val TAG = "PikuDiag"

        /** 头部层图片文件名前缀 */
        private const val HERO_PREFIX = "custom_bg"

        /** 独立背景层图片文件名前缀 */
        private const val BACKDROP_PREFIX = "backdrop"

        /** 原始文件超过该大小（20MB）时降采样重编码 */
        private const val MAX_FILE_BYTES = 20L * 1024 * 1024

        /** 重编码后的最长边 */
        private const val MAX_SIDE = 2048

        /** 取色用小图最长边 */
        private const val PALETTE_SIDE = 128

        /** 遮罩色亮度钳制：暗色遮罩足够深且保留可见色相、亮色遮罩足够浅 */
        private const val DARK_SCRIM_L_MAX = 0.30f
        private const val LIGHT_SCRIM_L_MIN = 0.84f

        private const val JPEG_QUALITY = 85
        private const val COPY_BUFFER_BYTES = 64 * 1024
    }
}
