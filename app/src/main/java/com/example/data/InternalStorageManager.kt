package com.example.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.engine.SaveFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class InternalStorageManager(private val context: Context) {

    private val editedPhotosDir: File
        get() = File(context.filesDir, "edited_photos").apply { if (!exists()) mkdirs() }

    private val recoveryDir: File
        get() = File(context.filesDir, "recovery").apply { if (!exists()) mkdirs() }

    /**
     * Atomically writes high-quality bitmap directly into the user's device internal storage
     * (filesDir/edited_photos) using temp file + rename so crashes never corrupt saved files.
     */
    suspend fun saveHighQualityToInternal(
        bitmap: Bitmap,
        photoId: String,
        format: SaveFormat
    ): File = withContext(Dispatchers.IO) {
        val fileName = "${photoId}.${format.extension}"
        val targetFile = File(editedPhotosDir, fileName)
        val tempFile = File(editedPhotosDir, "${photoId}.tmp")

        // Clean up alternate extension if user switched format on same project
        val altExt = if (format == SaveFormat.PNG_LOSSLESS) "jpg" else "png"
        File(editedPhotosDir, "${photoId}.$altExt").takeIf { it.exists() }?.delete()

        FileOutputStream(tempFile).use { out ->
            val compressFormat = when (format) {
                SaveFormat.PNG_LOSSLESS -> Bitmap.CompressFormat.PNG
                SaveFormat.JPEG_HIGH_98 -> Bitmap.CompressFormat.JPEG
            }
            val quality = when (format) {
                SaveFormat.PNG_LOSSLESS -> 100
                SaveFormat.JPEG_HIGH_98 -> 98
            }
            bitmap.compress(compressFormat, quality, out)
            out.fd.sync()
        }

        if (targetFile.exists()) targetFile.delete()
        if (!tempFile.renameTo(targetFile)) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }
        targetFile
    }

    /**
     * Atomically writes lossless PNG recovery snapshots to internal storage.
     */
    suspend fun saveRecoverySnapshot(
        bitmap: Bitmap,
        fileName: String
    ): File = withContext(Dispatchers.IO) {
        val targetFile = File(recoveryDir, fileName)
        val tempFile = File(recoveryDir, "$fileName.tmp")
        FileOutputStream(tempFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.fd.sync()
        }
        if (targetFile.exists()) targetFile.delete()
        if (!tempFile.renameTo(targetFile)) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }
        targetFile
    }

    fun deleteFileIfExists(path: String) {
        runCatching {
            val file = File(path)
            if (file.exists()) file.delete()
        }
    }

    fun clearRecoveryFiles() {
        runCatching {
            recoveryDir.listFiles()?.forEach { it.delete() }
        }
    }

    fun getShareUri(filePath: String): Uri? {
        val file = File(filePath)
        if (!file.exists()) return null
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Exports a copy of an internally saved photo to the device's public Pictures/LokaFoto
     * collection via MediaStore without requesting broad storage permissions.
     */
    suspend fun exportToPublicGallery(
        sourceFilePath: String,
        title: String,
        format: SaveFormat
    ): Boolean = withContext(Dispatchers.IO) {
        val sourceFile = File(sourceFilePath)
        if (!sourceFile.exists()) return@withContext false
        val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath) ?: return@withContext false

        val cleanName = title.replace(Regex("[^A-Za-z0-9_\\- ]"), "").trim().ifEmpty { "Dableh-FOTO" }
        val displayName = "${cleanName}_${System.currentTimeMillis()}.${format.extension}"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Dableh-FOTO")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext false

        return@withContext try {
            resolver.openOutputStream(uri)?.use { out ->
                val cf = if (format == SaveFormat.PNG_LOSSLESS) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                val q = if (format == SaveFormat.PNG_LOSSLESS) 100 else 98
                bitmap.compress(cf, q, out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            true
        } catch (_: Exception) {
            resolver.delete(uri, null, null)
            false
        } finally {
            bitmap.recycle()
        }
    }

    companion object {
        fun formatFileSize(bytes: Long): String {
            if (bytes < 1024L) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024.0) return String.format(Locale.getDefault(), "%.0f KB", kb)
            val mb = kb / 1024.0
            return String.format(Locale.getDefault(), "%.2f MB", mb)
        }
    }
}
