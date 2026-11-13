package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.EditedPhotoEntity
import com.example.data.InternalStorageManager
import com.example.data.NetworkMonitor
import com.example.data.PhotoEditorRepository
import com.example.data.StudioSettings
import com.example.engine.CropAspectRatio
import com.example.engine.EditSnapshot
import com.example.engine.EditStack
import com.example.engine.EditorTool
import com.example.engine.FilterPreset
import com.example.engine.ImageAdjustments
import com.example.engine.ImageFilterProcessor
import com.example.engine.LocalObjectRemover
import com.example.engine.MaskStroke
import com.example.engine.OfflineInpaintQuality
import com.example.engine.SamplePhoto
import com.example.engine.SaveFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface AutoSaveStatus {
    data object Idle : AutoSaveStatus
    data object Saving : AutoSaveStatus
    data class Saved(
        val filePath: String,
        val formatBadge: String,
        val sizeText: String,
        val timeText: String
    ) : AutoSaveStatus
}

data class EditorState(
    val isActive: Boolean = false,
    val projectId: String = "",
    val title: String = "Foto Baru",
    val originalBitmap: Bitmap? = null,
    val workingBitmap: Bitmap? = null,
    val currentActionLabel: String = "Foto Awal",
    val undoCount: Int = 0,
    val redoCount: Int = 0,
    val undoHistory: List<EditSnapshot> = emptyList(),
    val redoHistory: List<EditSnapshot> = emptyList(),
    val activeTool: EditorTool = EditorTool.ERASE_OBJECT,
    val maskStrokes: List<MaskStroke> = emptyList(),
    val canUndoMaskStroke: Boolean = false,
    val canRedoMaskStroke: Boolean = false,
    val brushRadiusFraction: Float = 0.045f,
    val adjustments: ImageAdjustments = ImageAdjustments(),
    val cropRatio: CropAspectRatio = CropAspectRatio.FREE,
    val isProcessingObjectRemoval: Boolean = false,
    val processingProgress: Float = 0f,
    val isComparingOriginal: Boolean = false,
    val savedPhotoId: String? = null,
    val usedOfflineEraser: Boolean = false,
    val autoSaveStatus: AutoSaveStatus = AutoSaveStatus.Idle,
    val isRecoveredSession: Boolean = false,
    val lastCheckpointTimeText: String? = null
) {
    val canUndo: Boolean get() = undoCount > 0
    val canRedo: Boolean get() = redoCount > 0
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val repository = PhotoEditorRepository(appContext)
    private val networkMonitor = NetworkMonitor(appContext)

    private val editHistoryStack = EditStack<EditSnapshot>(maxCapacity = 20)
    private val maskStrokeStack = EditStack<List<MaskStroke>>(maxCapacity = 25)
    private var adjustmentBaseline: EditSnapshot? = null

    val savedPhotos: StateFlow<List<EditedPhotoEntity>> = repository.editedPhotosFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<StudioSettings> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StudioSettings())

    val isDeviceOnline: StateFlow<Boolean> = networkMonitor.isDeviceOnline

    private val _editorState = MutableStateFlow(EditorState())
    val editorState: StateFlow<EditorState> = _editorState.asStateFlow()

    private val _shouldAutoOpenEditor = MutableStateFlow(false)
    val shouldAutoOpenEditor: StateFlow<Boolean> = _shouldAutoOpenEditor.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _isLoadingPhoto = MutableStateFlow(false)
    val isLoadingPhoto: StateFlow<Boolean> = _isLoadingPhoto.asStateFlow()

    private var processingJob: Job? = null
    private var autoSaveJob: Job? = null
    private var lastSavedOriginalHash: Int = 0

    init {
        checkAndRestoreInterruptedSession()
    }

    private fun checkAndRestoreInterruptedSession() {
        viewModelScope.launch {
            val currentSettings = repository.settingsFlow.first()
            if (!currentSettings.autoRecoveryEnabled) return@launch
            val interrupted = repository.getInterruptedRecoverySession() ?: return@launch

            val restored = withContext(Dispatchers.IO) {
                val orig = ImageFilterProcessor.decodeBitmapFromFile(interrupted.originalImagePath)
                val work = ImageFilterProcessor.decodeBitmapFromFile(interrupted.workingImagePath)
                if (orig != null && work != null) Pair(orig, work) else null
            } ?: return@launch

            editHistoryStack.clear()
            maskStrokeStack.clear()
            adjustmentBaseline = null

            val filter = FilterPreset.entries.firstOrNull { it.name == interrupted.filterPresetName }
                ?: FilterPreset.ORIGINAL
            val tool = EditorTool.entries.firstOrNull { it.name == interrupted.activeToolName }
                ?: EditorTool.ERASE_OBJECT

            val existingPhoto = interrupted.savedPhotoId?.let { id ->
                savedPhotos.value.firstOrNull { it.id == id }
            }

            _editorState.value = EditorState(
                isActive = true,
                projectId = interrupted.projectId,
                title = interrupted.title,
                originalBitmap = restored.first,
                workingBitmap = restored.second,
                currentActionLabel = "Sesi Dipulihkan",
                activeTool = tool,
                adjustments = ImageAdjustments(
                    brightness = interrupted.brightness,
                    contrast = interrupted.contrast,
                    saturation = interrupted.saturation,
                    warmth = interrupted.warmth,
                    vignette = interrupted.vignette,
                    filterPreset = filter
                ),
                savedPhotoId = interrupted.savedPhotoId,
                isRecoveredSession = true,
                lastCheckpointTimeText = formatTime(interrupted.lastCheckpointAt),
                autoSaveStatus = if (existingPhoto != null) {
                    AutoSaveStatus.Saved(
                        filePath = existingPhoto.filePath,
                        formatBadge = existingPhoto.formatBadge,
                        sizeText = InternalStorageManager.formatFileSize(existingPhoto.fileSizeBytes),
                        timeText = formatTime(existingPhoto.lastEditedAt)
                    )
                } else {
                    AutoSaveStatus.Idle
                }
            )
            lastSavedOriginalHash = System.identityHashCode(restored.first)
            _shouldAutoOpenEditor.value = true
        }
    }

    fun consumeAutoOpenEditor() {
        _shouldAutoOpenEditor.value = false
    }

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }

    fun isOfflineModeEffective(): Boolean {
        return settings.value.offlineModePreferred || !isDeviceOnline.value
    }

    fun toggleOfflineMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.setOfflineModePreferred(enabled)
            val msg = if (enabled) {
                "Mode Offline diaktifkan. Hapus objek berjalan 100% lokal."
            } else if (!isDeviceOnline.value) {
                "Tidak ada koneksi internet. Mode Offline otomatis tetap aktif."
            } else {
                "Mode Offline dimatikan. Pemrosesan tetap aman di perangkat."
            }
            _snackbarMessage.value = msg
        }
    }

    fun setSaveFormat(format: SaveFormat) {
        viewModelScope.launch {
            repository.setSaveFormat(format)
            if (_editorState.value.isActive) {
                autoSaveCurrentWork(forceRender = true)
            }
        }
    }

    fun setInpaintQuality(quality: OfflineInpaintQuality) {
        viewModelScope.launch {
            repository.setInpaintQuality(quality)
        }
    }

    fun setAutoRecoveryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoRecoveryEnabled(enabled)
        }
    }

    fun openPhotoFromUri(uri: Uri, onReady: () -> Unit) {
        viewModelScope.launch {
            _isLoadingPhoto.value = true
            val bitmap = withContext(Dispatchers.IO) {
                ImageFilterProcessor.decodeBitmapFromUri(appContext, uri)
            }
            _isLoadingPhoto.value = false
            if (bitmap == null) {
                _snackbarMessage.value = "Gagal memuat gambar. Pilih file gambar lain."
                return@launch
            }
            startNewSession(
                title = "Foto_${SimpleDateFormat("HHmm", Locale.getDefault()).format(Date())}",
                bitmap = bitmap,
                existingSavedId = null
            )
            onReady()
        }
    }

    fun openSamplePhoto(sample: SamplePhoto, onReady: () -> Unit) {
        viewModelScope.launch {
            _isLoadingPhoto.value = true
            val bitmap = withContext(Dispatchers.IO) {
                ImageFilterProcessor.decodeBitmapFromResource(appContext, sample.drawableRes)
            }
            _isLoadingPhoto.value = false
            if (bitmap == null) {
                _snackbarMessage.value = "Gagal memuat foto contoh."
                return@launch
            }
            startNewSession(
                title = sample.title,
                bitmap = bitmap,
                existingSavedId = null
            )
            onReady()
        }
    }

    fun openSavedPhoto(photo: EditedPhotoEntity, onReady: () -> Unit) {
        viewModelScope.launch {
            _isLoadingPhoto.value = true
            val bitmap = withContext(Dispatchers.IO) {
                ImageFilterProcessor.decodeBitmapFromFile(photo.filePath)
            }
            _isLoadingPhoto.value = false
            if (bitmap == null) {
                _snackbarMessage.value = "File internal tidak ditemukan."
                return@launch
            }
            startNewSession(
                title = photo.title,
                bitmap = bitmap,
                existingSavedId = photo.id
            )
            onReady()
        }
    }

    private suspend fun startNewSession(
        title: String,
        bitmap: Bitmap,
        existingSavedId: String?
    ) {
        editHistoryStack.clear()
        maskStrokeStack.clear()
        adjustmentBaseline = null

        val projectId = existingSavedId ?: "dableh_${System.currentTimeMillis()}"
        val originalCopy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        _editorState.value = EditorState(
            isActive = true,
            projectId = projectId,
            title = title,
            originalBitmap = originalCopy,
            workingBitmap = bitmap,
            currentActionLabel = "Foto Awal",
            activeTool = EditorTool.ERASE_OBJECT,
            savedPhotoId = existingSavedId,
            isRecoveredSession = false
        )
        lastSavedOriginalHash = System.identityHashCode(originalCopy)
        persistRecoveryCheckpoint()
        if (existingSavedId == null) {
            autoSaveCurrentWork(forceRender = false)
        }
    }

    private fun currentSnapshot(): EditSnapshot? {
        val state = _editorState.value
        val working = state.workingBitmap ?: return null
        return EditSnapshot(
            bitmap = working,
            adjustments = state.adjustments,
            actionLabel = state.currentActionLabel
        )
    }

    private fun syncStackMetadata(state: EditorState): EditorState {
        return state.copy(
            undoCount = editHistoryStack.undoCount,
            redoCount = editHistoryStack.redoCount,
            undoHistory = editHistoryStack.undoHistoryTopFirst(),
            redoHistory = editHistoryStack.redoHistoryTopFirst(),
            canUndoMaskStroke = maskStrokeStack.canUndo,
            canRedoMaskStroke = maskStrokeStack.canRedo
        )
    }

    fun selectTool(tool: EditorTool) {
        _editorState.update { it.copy(activeTool = tool) }
        persistCheckpointAsync()
    }

    fun setBrushRadiusFraction(fraction: Float) {
        _editorState.update { it.copy(brushRadiusFraction = fraction.coerceIn(0.015f, 0.11f)) }
    }

    fun addMaskStroke(stroke: MaskStroke) {
        if (stroke.points.isEmpty()) return
        val currentStrokes = _editorState.value.maskStrokes
        maskStrokeStack.push(currentStrokes)
        _editorState.update { state ->
            syncStackMetadata(state.copy(maskStrokes = currentStrokes + stroke))
        }
    }

    fun undoMaskStroke() {
        val currentStrokes = _editorState.value.maskStrokes
        val restored = maskStrokeStack.undo(currentStrokes) ?: return
        _editorState.update { state ->
            syncStackMetadata(state.copy(maskStrokes = restored))
        }
    }

    fun redoMaskStroke() {
        val currentStrokes = _editorState.value.maskStrokes
        val restored = maskStrokeStack.redo(currentStrokes) ?: return
        _editorState.update { state ->
            syncStackMetadata(state.copy(maskStrokes = restored))
        }
    }

    fun clearMaskStrokes() {
        val currentStrokes = _editorState.value.maskStrokes
        if (currentStrokes.isEmpty()) return
        maskStrokeStack.push(currentStrokes)
        _editorState.update { state ->
            syncStackMetadata(state.copy(maskStrokes = emptyList()))
        }
    }

    fun setComparingOriginal(comparing: Boolean) {
        _editorState.update { it.copy(isComparingOriginal = comparing) }
    }

    fun executeOfflineObjectRemoval() {
        val state = _editorState.value
        val working = state.workingBitmap ?: return
        if (state.maskStrokes.isEmpty()) {
            _snackbarMessage.value = "Usap area objek yang ingin dihapus terlebih dahulu."
            return
        }
        if (state.isProcessingObjectRemoval) return

        val previousSnapshot = currentSnapshot() ?: return
        val baseForErase = if (state.adjustments.isDefault) {
            working
        } else {
            ImageFilterProcessor.applyAdjustmentsToBitmap(working, state.adjustments)
        }

        val quality = settings.value.inpaintQuality
        val strokesSnapshot = state.maskStrokes

        processingJob = viewModelScope.launch {
            _editorState.update {
                it.copy(
                    isProcessingObjectRemoval = true,
                    processingProgress = 0.02f
                )
            }
            try {
                val cleaned = LocalObjectRemover.removeObjectOffline(
                    source = baseForErase,
                    strokes = strokesSnapshot,
                    quality = quality,
                    onProgress = { progress ->
                        _editorState.update { it.copy(processingProgress = progress) }
                    }
                )
                editHistoryStack.push(previousSnapshot)
                maskStrokeStack.clear()
                _editorState.update { current ->
                    syncStackMetadata(
                        current.copy(
                            workingBitmap = cleaned,
                            currentActionLabel = "Hapus Objek (Offline)",
                            maskStrokes = emptyList(),
                            adjustments = ImageAdjustments(),
                            isProcessingObjectRemoval = false,
                            processingProgress = 1f,
                            usedOfflineEraser = true
                        )
                    )
                }
                autoSaveCurrentWork(forceRender = false)
                persistRecoveryCheckpoint()
                val modeTag = if (isOfflineModeEffective()) "Mode Offline (Lokal)" else "Pemrosesan Lokal"
                _snackbarMessage.value = "Objek dihapus via $modeTag & tersimpan otomatis."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _editorState.update {
                    it.copy(isProcessingObjectRemoval = false, processingProgress = 0f)
                }
                _snackbarMessage.value = "Gagal memproses hapus objek: ${e.localizedMessage ?: "Kesalahan lokal"}"
            }
        }
    }

    fun updateAdjustments(adjustments: ImageAdjustments) {
        if (adjustmentBaseline == null) {
            adjustmentBaseline = currentSnapshot()
        }
        _editorState.update { it.copy(adjustments = adjustments) }
    }

    fun finishAdjustmentDrag(paramLabel: String) {
        val baseline = adjustmentBaseline ?: return
        adjustmentBaseline = null
        val currentAdj = _editorState.value.adjustments
        if (baseline.adjustments == currentAdj) return

        editHistoryStack.push(baseline)
        _editorState.update { current ->
            syncStackMetadata(current.copy(currentActionLabel = "Sesuaikan: $paramLabel"))
        }
        triggerBackgroundAutoSaveAndCheckpoint()
    }

    fun selectFilterPreset(preset: FilterPreset) {
        val state = _editorState.value
        if (state.adjustments.filterPreset == preset) return
        val previousSnapshot = currentSnapshot() ?: return
        adjustmentBaseline = null

        editHistoryStack.push(previousSnapshot)
        _editorState.update { current ->
            syncStackMetadata(
                current.copy(
                    adjustments = current.adjustments.copy(filterPreset = preset),
                    currentActionLabel = "Filter: ${preset.displayName}"
                )
            )
        }
        triggerBackgroundAutoSaveAndCheckpoint()
    }

    fun resetAdjustments() {
        val state = _editorState.value
        if (state.adjustments.isDefault) return
        val previousSnapshot = currentSnapshot() ?: return
        adjustmentBaseline = null

        editHistoryStack.push(previousSnapshot)
        _editorState.update { current ->
            syncStackMetadata(
                current.copy(
                    adjustments = ImageAdjustments(),
                    currentActionLabel = "Reset Penyesuaian"
                )
            )
        }
        triggerBackgroundAutoSaveAndCheckpoint()
    }

    fun commitAdjustments() {
        val state = _editorState.value
        val working = state.workingBitmap ?: return
        if (state.adjustments.isDefault) {
            viewModelScope.launch { autoSaveCurrentWork(forceRender = false) }
            return
        }
        val previousSnapshot = adjustmentBaseline ?: currentSnapshot() ?: return
        adjustmentBaseline = null

        viewModelScope.launch {
            val rendered = withContext(Dispatchers.Default) {
                ImageFilterProcessor.applyAdjustmentsToBitmap(working, state.adjustments)
            }
            editHistoryStack.push(previousSnapshot)
            _editorState.update { current ->
                syncStackMetadata(
                    current.copy(
                        workingBitmap = rendered,
                        adjustments = ImageAdjustments(),
                        currentActionLabel = "Terapkan Penyesuaian"
                    )
                )
            }
            autoSaveCurrentWork(forceRender = false)
            persistRecoveryCheckpoint()
            _snackbarMessage.value = "Penyesuaian diterapkan & disimpan otomatis."
        }
    }

    fun rotate90(clockwise: Boolean = true) {
        val state = _editorState.value
        val working = state.workingBitmap ?: return
        val previousSnapshot = currentSnapshot() ?: return

        viewModelScope.launch {
            val base = if (state.adjustments.isDefault) {
                working
            } else {
                withContext(Dispatchers.Default) {
                    ImageFilterProcessor.applyAdjustmentsToBitmap(working, state.adjustments)
                }
            }
            val rotated = withContext(Dispatchers.Default) {
                ImageFilterProcessor.rotateBitmap(base, if (clockwise) 90f else -90f)
            }
            editHistoryStack.push(previousSnapshot)
            maskStrokeStack.clear()
            _editorState.update { current ->
                syncStackMetadata(
                    current.copy(
                        workingBitmap = rotated,
                        adjustments = ImageAdjustments(),
                        maskStrokes = emptyList(),
                        currentActionLabel = if (clockwise) "Putar +90°" else "Putar -90°"
                    )
                )
            }
            autoSaveCurrentWork(forceRender = false)
            persistRecoveryCheckpoint()
        }
    }

    fun flipImage(horizontal: Boolean) {
        val state = _editorState.value
        val working = state.workingBitmap ?: return
        val previousSnapshot = currentSnapshot() ?: return

        viewModelScope.launch {
            val base = if (state.adjustments.isDefault) {
                working
            } else {
                withContext(Dispatchers.Default) {
                    ImageFilterProcessor.applyAdjustmentsToBitmap(working, state.adjustments)
                }
            }
            val flipped = withContext(Dispatchers.Default) {
                ImageFilterProcessor.flipBitmap(base, horizontal = horizontal, vertical = !horizontal)
            }
            editHistoryStack.push(previousSnapshot)
            maskStrokeStack.clear()
            _editorState.update { current ->
                syncStackMetadata(
                    current.copy(
                        workingBitmap = flipped,
                        adjustments = ImageAdjustments(),
                        maskStrokes = emptyList(),
                        currentActionLabel = if (horizontal) "Balik Horizontal" else "Balik Vertikal"
                    )
                )
            }
            autoSaveCurrentWork(forceRender = false)
            persistRecoveryCheckpoint()
        }
    }

    fun selectCropRatio(ratio: CropAspectRatio) {
        _editorState.update { it.copy(cropRatio = ratio) }
    }

    fun applyCrop(left: Float, top: Float, right: Float, bottom: Float) {
        val state = _editorState.value
        val working = state.workingBitmap ?: return
        val previousSnapshot = currentSnapshot() ?: return

        viewModelScope.launch {
            val base = if (state.adjustments.isDefault) {
                working
            } else {
                withContext(Dispatchers.Default) {
                    ImageFilterProcessor.applyAdjustmentsToBitmap(working, state.adjustments)
                }
            }
            val cropped = withContext(Dispatchers.Default) {
                ImageFilterProcessor.cropBitmapNormalized(base, left, top, right, bottom)
            }
            editHistoryStack.push(previousSnapshot)
            maskStrokeStack.clear()
            _editorState.update { current ->
                syncStackMetadata(
                    current.copy(
                        workingBitmap = cropped,
                        adjustments = ImageAdjustments(),
                        maskStrokes = emptyList(),
                        currentActionLabel = "Potong Gambar"
                    )
                )
            }
            autoSaveCurrentWork(forceRender = false)
            persistRecoveryCheckpoint()
            _snackbarMessage.value = "Potongan diterapkan & tersimpan otomatis."
        }
    }

    fun undo() {
        val current = currentSnapshot() ?: return
        val restored = editHistoryStack.undo(current) ?: return
        applyRestoredSnapshot(restored)
    }

    fun redo() {
        val current = currentSnapshot() ?: return
        val restored = editHistoryStack.redo(current) ?: return
        applyRestoredSnapshot(restored)
    }

    fun undoSteps(steps: Int) {
        val current = currentSnapshot() ?: return
        val restored = editHistoryStack.undoSteps(steps, current) ?: return
        applyRestoredSnapshot(restored)
    }

    fun redoSteps(steps: Int) {
        val current = currentSnapshot() ?: return
        val restored = editHistoryStack.redoSteps(steps, current) ?: return
        applyRestoredSnapshot(restored)
    }

    private fun applyRestoredSnapshot(restored: EditSnapshot) {
        adjustmentBaseline = null
        maskStrokeStack.clear()
        _editorState.update { state ->
            syncStackMetadata(
                state.copy(
                    workingBitmap = restored.bitmap,
                    adjustments = restored.adjustments,
                    currentActionLabel = restored.actionLabel,
                    maskStrokes = emptyList()
                )
            )
        }
        triggerBackgroundAutoSaveAndCheckpoint()
    }

    private fun triggerBackgroundAutoSaveAndCheckpoint() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            autoSaveCurrentWork(forceRender = true)
            persistRecoveryCheckpoint()
        }
    }

    fun saveNowExplicitly() {
        viewModelScope.launch {
            if (!_editorState.value.adjustments.isDefault) {
                commitAdjustments()
            } else {
                autoSaveCurrentWork(forceRender = true)
                _snackbarMessage.value = "Foto berkualitas tinggi tersimpan di memori internal."
            }
        }
    }

    private suspend fun autoSaveCurrentWork(forceRender: Boolean) {
        val state = _editorState.value
        val working = state.workingBitmap ?: return
        _editorState.update { it.copy(autoSaveStatus = AutoSaveStatus.Saving) }

        val toSave = if (forceRender && !state.adjustments.isDefault) {
            withContext(Dispatchers.Default) {
                ImageFilterProcessor.applyAdjustmentsToBitmap(working, state.adjustments)
            }
        } else {
            working
        }

        val photoId = state.savedPhotoId ?: state.projectId
        val format = settings.value.saveFormat
        val savedEntity = repository.autoSaveHighQuality(
            photoId = photoId,
            title = state.title,
            renderedBitmap = toSave,
            format = format,
            usedOfflineEraser = state.usedOfflineEraser
        )

        _editorState.update {
            it.copy(
                savedPhotoId = savedEntity.id,
                autoSaveStatus = AutoSaveStatus.Saved(
                    filePath = savedEntity.filePath,
                    formatBadge = savedEntity.formatBadge,
                    sizeText = InternalStorageManager.formatFileSize(savedEntity.fileSizeBytes),
                    timeText = formatTime(savedEntity.lastEditedAt)
                )
            )
        }
    }

    fun dismissRecoveryBanner() {
        _editorState.update { it.copy(isRecoveredSession = false) }
    }

    fun closeEditorCleanly(onClosed: () -> Unit) {
        processingJob?.cancel()
        autoSaveJob?.cancel()
        viewModelScope.launch {
            val state = _editorState.value
            if (state.isActive && state.workingBitmap != null) {
                autoSaveCurrentWork(forceRender = true)
            }
            repository.markCleanExit()
            editHistoryStack.clear()
            maskStrokeStack.clear()
            adjustmentBaseline = null
            _editorState.value = EditorState()
            onClosed()
        }
    }

    fun discardRecoveredSessionAndExit(onClosed: () -> Unit) {
        processingJob?.cancel()
        autoSaveJob?.cancel()
        viewModelScope.launch {
            repository.discardRecoverySession()
            editHistoryStack.clear()
            maskStrokeStack.clear()
            adjustmentBaseline = null
            _editorState.value = EditorState()
            onClosed()
        }
    }

    fun onAppBackgrounded() {
        val state = _editorState.value
        if (!state.isActive || !settings.value.autoRecoveryEnabled) return
        viewModelScope.launch(NonCancellable) {
            persistRecoveryCheckpoint()
        }
    }

    private fun persistCheckpointAsync() {
        viewModelScope.launch {
            persistRecoveryCheckpoint()
        }
    }

    private suspend fun persistRecoveryCheckpoint() {
        val state = _editorState.value
        if (!state.isActive || !settings.value.autoRecoveryEnabled) return
        val orig = state.originalBitmap ?: return
        val work = state.workingBitmap ?: return

        val origIdentity = System.identityHashCode(orig)
        val saveOrig = origIdentity != lastSavedOriginalHash
        repository.saveRecoveryCheckpoint(
            projectId = state.projectId,
            title = state.title,
            originalBitmap = orig,
            workingBitmap = work,
            adjustments = state.adjustments,
            activeTool = state.activeTool,
            offlineModeActive = isOfflineModeEffective(),
            savedPhotoId = state.savedPhotoId,
            saveOriginalSnapshot = saveOrig
        )
        lastSavedOriginalHash = origIdentity
        _editorState.update {
            it.copy(lastCheckpointTimeText = formatTime(System.currentTimeMillis()))
        }
    }

    fun deleteSavedPhoto(photo: EditedPhotoEntity) {
        viewModelScope.launch {
            repository.deleteEditedPhoto(photo)
            _snackbarMessage.value = "Foto dihapus dari memori internal."
        }
    }

    fun exportPhotoToGallery(filePath: String, title: String) {
        viewModelScope.launch {
            val format = settings.value.saveFormat
            val ok = repository.exportToPublicGallery(filePath, title, format)
            _snackbarMessage.value = if (ok) {
                "Salinan diekspor ke Galeri (Pictures/Dableh-FOTO)."
            } else {
                "Gagal mengekspor ke Galeri perangkat."
            }
        }
    }

    fun getShareUri(filePath: String): Uri? = repository.getShareUri(filePath)

    private fun formatTime(timestamp: Long): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
    }

    override fun onCleared() {
        super.onCleared()
        networkMonitor.unregister()
    }
}
