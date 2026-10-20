package pro.dasoft.ownsonic.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import java.io.File

/** Decodes cached server artwork with the same display bounds as embedded covers. */
internal fun readCover(file: File): Bitmap? {
    if (!file.isFile) return null
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val options = coverOptions(bounds) ?: return null
        BitmapFactory.decodeFile(file.absolutePath, options)
    } catch (_: Exception) {
        null
    }
}

private fun coverOptions(bounds: BitmapFactory.Options): BitmapFactory.Options? {
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    return BitmapFactory.Options().apply {
        inSampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 512) inSampleSize *= 2
    }
}

/** Reads a small display bitmap from local audio metadata. */
internal fun readEmbeddedCover(file: File): Bitmap? {
    if (!file.isFile) return null
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        val bytes = retriever.embeddedPicture ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = coverOptions(bounds) ?: return null
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    } catch (_: Exception) {
        null
    } finally {
        retriever.release()
    }
}
