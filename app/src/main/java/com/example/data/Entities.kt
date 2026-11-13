package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "edited_photos")
data class EditedPhotoEntity(
    @PrimaryKey val id: String,
    val filePath: String,
    val title: String,
    val formatBadge: String,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long,
    val lastEditedAt: Long,
    val usedOfflineEraser: Boolean = false
)

@Entity(tableName = "recovery_session")
data class RecoverySessionEntity(
    @PrimaryKey val id: Int = 1,
    val projectId: String,
    val title: String,
    val originalImagePath: String,
    val workingImagePath: String,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val warmth: Float,
    val vignette: Float,
    val filterPresetName: String,
    val activeToolName: String,
    val offlineModeActive: Boolean,
    val savedPhotoId: String?,
    val cleanExit: Boolean,
    val lastCheckpointAt: Long
)
