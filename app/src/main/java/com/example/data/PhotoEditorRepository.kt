package com.example.data

import android.content.Context
import android.graphics.Bitmap
import com.example.engine.EditorTool
import com.example.engine.ImageAdjustments
import com.example.engine.SaveFormat
import kotlinx.coroutines.flow.Flow
import java.io.File

class PhotoEditorRepository(
    context: Context,
    private val dao: LokaFotoDao = LokaFotoDatabase.getInstance(context).dao(),
    private val storageManager: InternalStorageManager = InternalStorageManager(context),
    private val settingsPreferences: SettingsPreferences = SettingsPreferences(context)
) {

    val editedPhotosFlow: Flow<List<EditedPhotoEntity>> = dao.observeEditedPhotos()
    val settingsFlow: Flow<StudioSettings> = settingsPreferences.settingsFlow
    val recoverySessionFlow: Flow<RecoverySessionEntity?> = dao.observeRecoverySession()

    suspend fun setOfflineModePreferred(enabled: Boolean) =
        settingsPreferences.setOfflineModePreferred(enabled)

    suspend fun setSaveFormat(format: SaveFormat) =
        settingsPreferences.setSaveFormat(format)

    suspend fun setInpaintQuality(quality: com.example.engine.OfflineInpaintQuality) =
        settingsPreferences.setInpaintQuality(quality)

    suspend fun setAutoRecoveryEnabled(enabled: Boolean) =
        settingsPreferences.setAutoRecoveryEnabled(enabled)

    /**
     * Automatically saves the edited high-quality bitmap directly into internal storage
     * and records metadata in Room.
     */
    suspend fun autoSaveHighQuality(
        photoId: String,
        title: String,
        renderedBitmap: Bitmap,
        format: SaveFormat,
        usedOfflineEraser: Boolean
    ): EditedPhotoEntity {
        val file = storageManager.saveHighQualityToInternal(renderedBitmap, photoId, format)
        val entity = EditedPhotoEntity(
            id = photoId,
            filePath = file.absolutePath,
            title = title,
            formatBadge = format.shortBadge,
            width = renderedBitmap.width,
            height = renderedBitmap.height,
            fileSizeBytes = file.length(),
            lastEditedAt = System.currentTimeMillis(),
            usedOfflineEraser = usedOfflineEraser
        )
        dao.upsertEditedPhoto(entity)
        return entity
    }

    suspend fun deleteEditedPhoto(photo: EditedPhotoEntity) {
        storageManager.deleteFileIfExists(photo.filePath)
        dao.deleteEditedPhotoById(photo.id)
    }

    /**
     * Writes lossless recovery snapshots to internal storage and marks cleanExit = false
     * so any unexpected closure or crash restores the exact editing session on next launch.
     */
    suspend fun saveRecoveryCheckpoint(
        projectId: String,
        title: String,
        originalBitmap: Bitmap,
        workingBitmap: Bitmap,
        adjustments: ImageAdjustments,
        activeTool: EditorTool,
        offlineModeActive: Boolean,
        savedPhotoId: String?,
        saveOriginalSnapshot: Boolean = true
    ) {
        val existing = dao.getRecoverySession()
        val origFile = if (saveOriginalSnapshot || existing == null || !File(existing.originalImagePath).exists()) {
            storageManager.saveRecoverySnapshot(originalBitmap, "recovery_original.png")
        } else {
            File(existing.originalImagePath)
        }
        val workFile = storageManager.saveRecoverySnapshot(workingBitmap, "recovery_working.png")

        dao.upsertRecoverySession(
            RecoverySessionEntity(
                id = 1,
                projectId = projectId,
                title = title,
                originalImagePath = origFile.absolutePath,
                workingImagePath = workFile.absolutePath,
                brightness = adjustments.brightness,
                contrast = adjustments.contrast,
                saturation = adjustments.saturation,
                warmth = adjustments.warmth,
                vignette = adjustments.vignette,
                filterPresetName = adjustments.filterPreset.name,
                activeToolName = activeTool.name,
                offlineModeActive = offlineModeActive,
                savedPhotoId = savedPhotoId,
                cleanExit = false,
                lastCheckpointAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getInterruptedRecoverySession(): RecoverySessionEntity? {
        val session = dao.getRecoverySession() ?: return null
        if (session.cleanExit) return null
        val workExists = File(session.workingImagePath).exists()
        val origExists = File(session.originalImagePath).exists()
        return if (workExists && origExists) session else null
    }

    suspend fun markCleanExit() {
        dao.markRecoveryCleanExit()
        storageManager.clearRecoveryFiles()
    }

    suspend fun discardRecoverySession() {
        dao.clearRecoverySession()
        storageManager.clearRecoveryFiles()
    }

    fun getShareUri(filePath: String) = storageManager.getShareUri(filePath)

    suspend fun exportToPublicGallery(
        filePath: String,
        title: String,
        format: SaveFormat
    ): Boolean = storageManager.exportToPublicGallery(filePath, title, format)
}
