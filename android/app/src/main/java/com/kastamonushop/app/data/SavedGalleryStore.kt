package com.kastamonushop.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class SavedPhotoItem(
    val id: String,
    val displayName: String,
    val filePath: String,
    val thumbPath: String,
    val width: Int,
    val height: Int,
    val savedAtMs: Long
)

object SavedGalleryStore {
    private const val DIR_NAME = "saved_gallery"
    private const val INDEX_FILE = "index.json"
    private const val MAX_ITEMS = 48

    private fun galleryDir(context: Context): File {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun list(context: Context): List<SavedPhotoItem> {
        val index = File(galleryDir(context), INDEX_FILE)
        if (!index.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(index.readText())
            val out = ArrayList<SavedPhotoItem>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val filePath = o.optString("filePath")
                val thumbPath = o.optString("thumbPath")
                if (File(filePath).exists()) {
                    out.add(
                        SavedPhotoItem(
                            id = o.optString("id"),
                            displayName = o.optString("displayName"),
                            filePath = filePath,
                            thumbPath = thumbPath,
                            width = o.optInt("width"),
                            height = o.optInt("height"),
                            savedAtMs = o.optLong("savedAtMs")
                        )
                    )
                }
            }
            out.sortedByDescending { it.savedAtMs }
        }.getOrDefault(emptyList())
    }

    fun recordSavedPhoto(
        context: Context,
        displayName: String,
        formatExt: String,
        exportedBytes: ByteArray,
        previewPngBytes: ByteArray?,
        width: Int,
        height: Int,
        existingId: String? = null
    ): Pair<String, List<SavedPhotoItem>> {
        val dir = galleryDir(context)
        val ts = System.currentTimeMillis()
        val current = list(context).toMutableList()
        val prevItem = if (!existingId.isNullOrBlank()) current.firstOrNull { it.id == existingId } else null
        val id = prevItem?.id ?: "sb_$ts"
        val finalName = prevItem?.displayName?.ifBlank { displayName } ?: displayName

        val cleanExt = formatExt.trimStart('.').lowercase().ifBlank { "png" }
        val file = File(dir, "$id.$cleanExt")
        file.writeBytes(exportedBytes)

        // Use a fresh thumb filename on overwrite so Compose never displays a stale cached thumbnail
        val thumbFile = File(dir, "${id}_thumb_$ts.jpg")
        val thumbSource = previewPngBytes ?: exportedBytes
        runCatching {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(thumbSource, 0, thumbSource.size, opts)
            var sample = 1
            while ((opts.outWidth / sample) > 640 || (opts.outHeight / sample) > 640) {
                sample *= 2
            }
            val decOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = BitmapFactory.decodeByteArray(thumbSource, 0, thumbSource.size, decOpts)
            if (bmp != null) {
                FileOutputStream(thumbFile).use { fos ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 85, fos)
                }
                bmp.recycle()
            }
        }

        if (prevItem != null) {
            if (prevItem.filePath != file.absolutePath) {
                runCatching { File(prevItem.filePath).delete() }
            }
            if (prevItem.thumbPath != thumbFile.absolutePath && prevItem.thumbPath != file.absolutePath) {
                runCatching { File(prevItem.thumbPath).delete() }
            }
            current.removeAll { it.id == id }
        }
        current.add(
            0,
            SavedPhotoItem(
                id = id,
                displayName = finalName,
                filePath = file.absolutePath,
                thumbPath = if (thumbFile.exists()) thumbFile.absolutePath else file.absolutePath,
                width = width,
                height = height,
                savedAtMs = ts
            )
        )
        // Clean up orphaned files when exceeding MAX_ITEMS
        if (current.size > MAX_ITEMS) {
            current.drop(MAX_ITEMS).forEach { dropped ->
                runCatching { File(dropped.filePath).delete() }
                runCatching { File(dropped.thumbPath).delete() }
            }
        }
        val trimmed = current.take(MAX_ITEMS)
        saveIndex(context, trimmed)
        return id to trimmed
    }

    fun delete(context: Context, id: String): List<SavedPhotoItem> {
        val current = list(context)
        val target = current.firstOrNull { it.id == id }
        if (target != null) {
            runCatching { File(target.filePath).delete() }
            runCatching { File(target.thumbPath).delete() }
        }
        val remaining = current.filterNot { it.id == id }
        saveIndex(context, remaining)
        return remaining
    }

    private fun saveIndex(context: Context, items: List<SavedPhotoItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("displayName", item.displayName)
                    put("filePath", item.filePath)
                    put("thumbPath", item.thumbPath)
                    put("width", item.width)
                    put("height", item.height)
                    put("savedAtMs", item.savedAtMs)
                }
            )
        }
        File(galleryDir(context), INDEX_FILE).writeText(arr.toString())
    }
}
