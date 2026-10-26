package pl.asystentfanatyka.camera

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.asystentfanatyka.core.measurement.ImageQuality
import pl.asystentfanatyka.core.measurement.ImageQualityReport
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Zdjęcia są przechowywane wyłącznie w prywatnym katalogu aplikacji (files/photos).
 * Nic nie jest wysyłane na zewnątrz – w MVP nie ma zdalnego rozpoznawania.
 */
class PhotoStorage(private val context: Context) {

    private val dir: File get() = File(context.filesDir, "photos").apply { mkdirs() }

    fun newPhotoFile(): File = File(dir, "ryba_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")

    /** Kopiuje zdjęcie z galerii do katalogu aplikacji, normalizując orientację i rozmiar. */
    suspend fun importFromUri(resolver: ContentResolver, uri: Uri): File = withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "import_${System.nanoTime()}.jpg")
        resolver.openInputStream(uri)?.use { input -> FileOutputStream(tmp).use { input.copyTo(it) } }
            ?: error("Nie można otworzyć wybranego zdjęcia")
        try {
            normalize(tmp)
        } finally {
            tmp.delete()
        }
    }

    /** Obraca zgodnie z EXIF i zmniejsza do [MAX_SIDE] px, zapisując nowy plik JPEG w katalogu zdjęć. */
    suspend fun normalize(source: File): File = withContext(Dispatchers.IO) {
        val bmp = loadBitmap(source.absolutePath, MAX_SIDE) ?: error("Nie można odczytać zdjęcia")
        val out = newPhotoFile()
        FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bmp.recycle()
        out
    }

    suspend fun saveCropped(bitmap: Bitmap): File = withContext(Dispatchers.IO) {
        val out = newPhotoFile()
        FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        out
    }

    fun deleteAllExcept(keep: Set<String>) {
        dir.listFiles()?.filter { it.absolutePath !in keep }?.forEach { it.delete() }
    }

    fun deleteAll() {
        dir.listFiles()?.forEach { it.delete() }
    }

    companion object {
        const val MAX_SIDE = 2560

        /** Wczytuje bitmapę z poprawną orientacją (EXIF), zmniejszając ją tak, by dłuższy bok nie przekraczał [maxSide]. */
        fun loadBitmap(path: String, maxSide: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            val scaled = run {
                val longSide = maxOf(decoded.width, decoded.height)
                if (longSide > maxSide) {
                    val f = maxSide.toFloat() / longSide
                    Bitmap.createScaledBitmap(decoded, (decoded.width * f).toInt(), (decoded.height * f).toInt(), true)
                        .also { if (it !== decoded) decoded.recycle() }
                } else decoded
            }
            val rotation = try {
                when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } catch (e: Exception) { 0f }
            if (rotation == 0f) return scaled
            val m = Matrix().apply { postRotate(rotation) }
            return Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, m, true).also { if (it !== scaled) scaled.recycle() }
        }

        /** Ocena jakości na zmniejszonej kopii w skali szarości. */
        fun analyzeQuality(bitmap: Bitmap): ImageQualityReport {
            val longSide = maxOf(bitmap.width, bitmap.height)
            val f = if (longSide > 512) 512f / longSide else 1f
            val small = if (f < 1f) Bitmap.createScaledBitmap(bitmap, maxOf(3, (bitmap.width * f).toInt()), maxOf(3, (bitmap.height * f).toInt()), true) else bitmap
            val w = small.width
            val h = small.height
            val px = IntArray(w * h)
            small.getPixels(px, 0, w, 0, 0, w, h)
            if (small !== bitmap) small.recycle()
            val gray = IntArray(px.size) { i ->
                val c = px[i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                (r * 299 + g * 587 + b * 114) / 1000
            }
            return ImageQuality.analyze(gray, w, h)
        }
    }
}
