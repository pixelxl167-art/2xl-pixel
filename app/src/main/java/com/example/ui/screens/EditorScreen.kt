package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Filter
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.StudioSettings
import com.example.engine.CropAspectRatio
import com.example.engine.EditorTool
import com.example.engine.FilterPreset
import com.example.engine.ImageAdjustments
import com.example.engine.ImageFilterProcessor
import com.example.engine.MaskStroke
import com.example.engine.NormalizedPoint
import com.example.engine.OfflineInpaintQuality
import com.example.ui.AutoSaveStatus
import com.example.ui.EditorState
import com.example.ui.components.AutoSaveStatusPill
import com.example.ui.components.EditHistorySheet
import com.example.ui.components.OfflineModeChip
import com.example.ui.components.RecoveryBanner
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class AdjustParam(val label: String) {
    BRIGHTNESS("Kecerahan"),
    CONTRAST("Kontras"),
    SATURATION("Saturasi"),
    WARMTH("Hangat"),
    VIGNETTE("Vignette")
}

@Composable
fun EditorScreen(
    state: EditorState,
    settings: StudioSettings,
    isDeviceOnline: Boolean,
    snackbarHostState: SnackbarHostState,
    onCloseCleanly: () -> Unit,
    onDiscardRecovered: () -> Unit,
    onDismissRecoveryBanner: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onUndoSteps: (Int) -> Unit,
    onRedoSteps: (Int) -> Unit,
    onComparingChanged: (Boolean) -> Unit,
    onToggleOfflineMode: () -> Unit,
    onSelectTool: (EditorTool) -> Unit,
    onBrushRadiusChanged: (Float) -> Unit,
    onAddMaskStroke: (MaskStroke) -> Unit,
    onUndoMaskStroke: () -> Unit,
    onRedoMaskStroke: () -> Unit,
    onClearMaskStrokes: () -> Unit,
    onSelectInpaintQuality: (OfflineInpaintQuality) -> Unit,
    onExecuteObjectRemoval: () -> Unit,
    onUpdateAdjustments: (ImageAdjustments) -> Unit,
    onFinishAdjustmentDrag: (String) -> Unit,
    onResetAdjustments: () -> Unit,
    onCommitAdjustments: () -> Unit,
    onSelectFilterPreset: (FilterPreset) -> Unit,
    onRotate90: (Boolean) -> Unit,
    onFlip: (Boolean) -> Unit,
    onSelectCropRatio: (CropAspectRatio) -> Unit,
    onApplyCrop: (Float, Float, Float, Float) -> Unit,
    onSaveNow: () -> Unit,
    onShareCurrent: () -> Unit,
    onExportToGallery: () -> Unit,
    onOpenSettings: () -> Unit
) {
    BackHandler(enabled = !state.isProcessingObjectRemoval) {
        onCloseCleanly()
    }

    val isOfflineEffective = settings.offlineModePreferred || !isDeviceOnline
    var menuExpanded by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var selectedAdjustParam by remember { mutableStateOf(AdjustParam.BRIGHTNESS) }
    var cropInsetFraction by remember { mutableFloatStateOf(0.08f) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            // Top Darkroom Action Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                IconButton(
                    onClick = onCloseCleanly,
                    enabled = !state.isProcessingObjectRemoval,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("editor_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Kembali ke Beranda"
                    )
                }

                IconButton(
                    onClick = onUndo,
                    enabled = state.canUndo && !state.isProcessingObjectRemoval,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("undo_button")
                ) {
                    BadgedBox(
                        badge = {
                            if (state.undoCount > 0) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Text("${state.undoCount}")
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Urungkan (${state.undoCount})"
                        )
                    }
                }

                IconButton(
                    onClick = onRedo,
                    enabled = state.canRedo && !state.isProcessingObjectRemoval,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("redo_button")
                ) {
                    BadgedBox(
                        badge = {
                            if (state.redoCount > 0) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                ) {
                                    Text("${state.redoCount}")
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Ulangi (${state.redoCount})"
                        )
                    }
                }

                // Press & hold compare original
                Surface(
                    shape = CircleShape,
                    color = if (state.isComparingOriginal) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("compare_button")
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    onComparingChanged(true)
                                    tryAwaitRelease()
                                    onComparingChanged(false)
                                }
                            )
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Compare,
                            contentDescription = "Tahan untuk bandingkan foto asli",
                            tint = if (state.isComparingOriginal) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                OfflineModeChip(
                    isOfflineActive = isOfflineEffective,
                    isDeviceOnline = isDeviceOnline,
                    onClick = onToggleOfflineMode
                )

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("editor_more_menu")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Menu simpan dan ekspor"
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Simpan Sekarang (${settings.saveFormat.shortBadge})") },
                            leadingIcon = { Icon(Icons.Filled.Save, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onSaveNow()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Bagikan Foto") },
                            leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onShareCurrent()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Simpan Salinan ke Galeri") },
                            leadingIcon = { Icon(Icons.Filled.SaveAlt, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onExportToGallery()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Pengaturan Studio") },
                            leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onOpenSettings()
                            }
                        )
                    }
                }
            }

            // Recovery Banner (when auto-restored from unexpected exit)
            if (state.isRecoveredSession) {
                RecoveryBanner(
                    visible = true,
                    checkpointTime = state.lastCheckpointTimeText,
                    onDiscard = onDiscardRecovered,
                    onDismiss = onDismissRecoveryBanner,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            // Auto-save, history stack & resolution telemetry strip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                AutoSaveStatusPill(status = state.autoSaveStatus)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.canUndo || state.canRedo) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .testTag("history_stack_button")
                                .clickable(enabled = !state.isProcessingObjectRemoval) {
                                    showHistorySheet = true
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.History,
                                    contentDescription = "Stack riwayat edit",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Stack ${state.undoCount}/${state.redoCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    state.workingBitmap?.let { bmp ->
                        Text(
                            text = "${bmp.width}×${bmp.height}px",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Main Interactive Image Canvas
            BoxWithConstraints(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerLowest,
                        RoundedCornerShape(18.dp)
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(18.dp)
                    )
            ) {
                val activeBitmap = if (state.isComparingOriginal) {
                    state.originalBitmap ?: state.workingBitmap
                } else {
                    state.workingBitmap
                }

                if (activeBitmap != null) {
                    val density = LocalDensity.current
                    val containerW = with(density) { maxWidth.toPx() }
                    val containerH = with(density) { maxHeight.toPx() }
                    val imageRect = remember(containerW, containerH, activeBitmap.width, activeBitmap.height) {
                        computeFitRect(
                            containerW = containerW,
                            containerH = containerH,
                            bitmapW = activeBitmap.width.toFloat(),
                            bitmapH = activeBitmap.height.toFloat()
                        )
                    }

                    val liveColorFilter = remember(state.adjustments, state.isComparingOriginal) {
                        if (state.isComparingOriginal || state.adjustments.isDefault) {
                            null
                        } else {
                            val androidMatrix = ImageFilterProcessor.buildColorMatrix(state.adjustments)
                            ColorFilter.colorMatrix(
                                androidx.compose.ui.graphics.ColorMatrix(androidMatrix.array)
                            )
                        }
                    }

                    val activePoints = remember { mutableStateListOf<NormalizedPoint>() }
                    var activePointerPx by remember { mutableStateOf<Offset?>(null) }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(
                                state.activeTool,
                                state.brushRadiusFraction,
                                state.isProcessingObjectRemoval,
                                imageRect
                            ) {
                                if (state.activeTool != EditorTool.ERASE_OBJECT || state.isProcessingObjectRemoval) {
                                    return@pointerInput
                                }
                                detectDragGestures(
                                    onDragStart = { startOffset ->
                                        activePoints.clear()
                                        if (imageRect.width > 0f && imageRect.height > 0f) {
                                            val nx = ((startOffset.x - imageRect.left) / imageRect.width)
                                                .coerceIn(0f, 1f)
                                            val ny = ((startOffset.y - imageRect.top) / imageRect.height)
                                                .coerceIn(0f, 1f)
                                            activePoints.add(NormalizedPoint(nx, ny))
                                            activePointerPx = startOffset
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        if (imageRect.width > 0f && imageRect.height > 0f) {
                                            val nx = ((change.position.x - imageRect.left) / imageRect.width)
                                                .coerceIn(0f, 1f)
                                            val ny = ((change.position.y - imageRect.top) / imageRect.height)
                                                .coerceIn(0f, 1f)
                                            activePoints.add(NormalizedPoint(nx, ny))
                                            activePointerPx = change.position
                                        }
                                    },
                                    onDragEnd = {
                                        if (activePoints.isNotEmpty()) {
                                            onAddMaskStroke(
                                                MaskStroke(
                                                    points = activePoints.toList(),
                                                    brushRadiusNormalized = state.brushRadiusFraction
                                                )
                                            )
                                        }
                                        activePoints.clear()
                                        activePointerPx = null
                                    },
                                    onDragCancel = {
                                        activePoints.clear()
                                        activePointerPx = null
                                    }
                                )
                            }
                    ) {
                        Image(
                            bitmap = activeBitmap.asImageBitmap(),
                            contentDescription = "Kanvas foto editor",
                            contentScale = ContentScale.Fit,
                            colorFilter = liveColorFilter,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Live Vignette + Mask + Crop overlay
                        val maskColor = MaterialTheme.colorScheme.primary
                        val cropBorderColor = MaterialTheme.colorScheme.secondary
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Vignette preview
                            if (!state.isComparingOriginal && state.adjustments.vignette > 0.01f && imageRect.width > 0f) {
                                drawRect(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Transparent,
                                            Color.Black.copy(alpha = state.adjustments.vignette.coerceIn(0f, 0.85f))
                                        ),
                                        center = Offset(
                                            imageRect.left + imageRect.width / 2f,
                                            imageRect.top + imageRect.height / 2f
                                        ),
                                        radius = max(imageRect.width, imageRect.height) * 0.65f
                                    ),
                                    topLeft = Offset(imageRect.left, imageRect.top),
                                    size = Size(imageRect.width, imageRect.height)
                                )
                            }

                            // Drawn object removal brush mask
                            if (!state.isComparingOriginal && state.activeTool == EditorTool.ERASE_OBJECT) {
                                val minImgDim = min(imageRect.width, imageRect.height)
                                val allStrokes = if (activePoints.isNotEmpty()) {
                                    state.maskStrokes + MaskStroke(
                                        points = activePoints.toList(),
                                        brushRadiusNormalized = state.brushRadiusFraction
                                    )
                                } else {
                                    state.maskStrokes
                                }

                                for (stroke in allStrokes) {
                                    if (stroke.points.isEmpty()) continue
                                    val radiusPx = max(6f, stroke.brushRadiusNormalized * minImgDim)
                                    if (stroke.points.size == 1) {
                                        val p = stroke.points.first()
                                        drawCircle(
                                            color = maskColor.copy(alpha = 0.45f),
                                            radius = radiusPx,
                                            center = Offset(
                                                imageRect.left + p.x * imageRect.width,
                                                imageRect.top + p.y * imageRect.height
                                            )
                                        )
                                    } else {
                                        val path = Path()
                                        val first = stroke.points.first()
                                        path.moveTo(
                                            imageRect.left + first.x * imageRect.width,
                                            imageRect.top + first.y * imageRect.height
                                        )
                                        for (i in 1 until stroke.points.size) {
                                            val prev = stroke.points[i - 1]
                                            val curr = stroke.points[i]
                                            val midX = imageRect.left + ((prev.x + curr.x) * 0.5f) * imageRect.width
                                            val midY = imageRect.top + ((prev.y + curr.y) * 0.5f) * imageRect.height
                                            path.quadraticTo(
                                                imageRect.left + prev.x * imageRect.width,
                                                imageRect.top + prev.y * imageRect.height,
                                                midX,
                                                midY
                                            )
                                        }
                                        val last = stroke.points.last()
                                        path.lineTo(
                                            imageRect.left + last.x * imageRect.width,
                                            imageRect.top + last.y * imageRect.height
                                        )
                                        drawPath(
                                            path = path,
                                            color = maskColor.copy(alpha = 0.45f),
                                            style = Stroke(
                                                width = radiusPx * 2f,
                                                cap = StrokeCap.Round,
                                                join = StrokeJoin.Round
                                            )
                                        )
                                    }
                                }

                                // Live brush cursor ring while dragging
                                activePointerPx?.let { cursor ->
                                    val radiusPx = max(6f, state.brushRadiusFraction * minImgDim)
                                    drawCircle(
                                        color = Color.White.copy(alpha = 0.9f),
                                        radius = radiusPx,
                                        center = cursor,
                                        style = Stroke(width = 2.dp.toPx())
                                    )
                                }
                            }

                            // Crop overlay preview in TRANSFORM mode
                            if (!state.isComparingOriginal && state.activeTool == EditorTool.TRANSFORM && imageRect.width > 0f) {
                                val cropNorm = computeNormalizedCropRect(
                                    bitmapW = activeBitmap.width.toFloat(),
                                    bitmapH = activeBitmap.height.toFloat(),
                                    ratio = state.cropRatio,
                                    insetFraction = cropInsetFraction
                                )
                                val cropLeft = imageRect.left + cropNorm.left * imageRect.width
                                val cropTop = imageRect.top + cropNorm.top * imageRect.height
                                val cropW = (cropNorm.right - cropNorm.left) * imageRect.width
                                val cropH = (cropNorm.bottom - cropNorm.top) * imageRect.height

                                drawRect(
                                    color = cropBorderColor,
                                    topLeft = Offset(cropLeft, cropTop),
                                    size = Size(cropW, cropH),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                                // Rule-of-thirds guides
                                val thirdW = cropW / 3f
                                val thirdH = cropH / 3f
                                for (k in 1..2) {
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.45f),
                                        start = Offset(cropLeft + thirdW * k, cropTop),
                                        end = Offset(cropLeft + thirdW * k, cropTop + cropH),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.45f),
                                        start = Offset(cropLeft, cropTop + thirdH * k),
                                        end = Offset(cropLeft + cropW, cropTop + thirdH * k),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                }
                            }
                        }

                        if (state.isComparingOriginal) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 12.dp)
                            ) {
                                Text(
                                    text = "FOTO ASLI",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }
                        }

                        if (state.isProcessingObjectRemoval) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.78f))
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.large,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.padding(24.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            progress = { state.processingProgress },
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(46.dp)
                                        )
                                        Text(
                                            text = "Menghapus Objek (${(state.processingProgress * 100).roundToInt()}%)",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = if (isOfflineEffective) {
                                                "Berjalan via Mode Offline (100% data lokal)"
                                            } else {
                                                "Pemrosesan lokal piksel berjalan…"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Tool Panel + Navigation Tabs
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    when (state.activeTool) {
                        EditorTool.ERASE_OBJECT -> {
                            EraseObjectPanel(
                                state = state,
                                settings = settings,
                                isOfflineEffective = isOfflineEffective,
                                onBrushRadiusChanged = onBrushRadiusChanged,
                                onUndoStroke = onUndoMaskStroke,
                                onRedoStroke = onRedoMaskStroke,
                                onClearStrokes = onClearMaskStrokes,
                                onSelectQuality = onSelectInpaintQuality,
                                onExecute = onExecuteObjectRemoval
                            )
                        }
                        EditorTool.ADJUST -> {
                            AdjustPanel(
                                adjustments = state.adjustments,
                                selectedParam = selectedAdjustParam,
                                onSelectParam = { selectedAdjustParam = it },
                                onUpdateAdjustments = onUpdateAdjustments,
                                onFinishDrag = { onFinishAdjustmentDrag(selectedAdjustParam.label) },
                                onReset = onResetAdjustments,
                                onCommit = onCommitAdjustments
                            )
                        }
                        EditorTool.FILTER -> {
                            FilterPanel(
                                selectedPreset = state.adjustments.filterPreset,
                                onSelectPreset = onSelectFilterPreset,
                                onCommit = onCommitAdjustments
                            )
                        }
                        EditorTool.TRANSFORM -> {
                            TransformPanel(
                                selectedRatio = state.cropRatio,
                                insetFraction = cropInsetFraction,
                                onRotate90 = onRotate90,
                                onFlip = onFlip,
                                onSelectRatio = onSelectCropRatio,
                                onInsetChanged = { cropInsetFraction = it },
                                onApplyCrop = {
                                    val bmp = state.workingBitmap ?: return@TransformPanel
                                    val rect = computeNormalizedCropRect(
                                        bitmapW = bmp.width.toFloat(),
                                        bitmapH = bmp.height.toFloat(),
                                        ratio = state.cropRatio,
                                        insetFraction = cropInsetFraction
                                    )
                                    onApplyCrop(rect.left, rect.top, rect.right, rect.bottom)
                                    cropInsetFraction = 0.08f
                                }
                            )
                        }
                    }

                    // Bottom Tool Selector Strip
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        EditorToolTab(
                            icon = Icons.Filled.AutoFixHigh,
                            label = "Hapus Objek",
                            selected = state.activeTool == EditorTool.ERASE_OBJECT,
                            testTag = "tab_erase_object",
                            onClick = { onSelectTool(EditorTool.ERASE_OBJECT) }
                        )
                        EditorToolTab(
                            icon = Icons.Filled.Tune,
                            label = "Sesuaikan",
                            selected = state.activeTool == EditorTool.ADJUST,
                            testTag = "tab_adjust",
                            onClick = { onSelectTool(EditorTool.ADJUST) }
                        )
                        EditorToolTab(
                            icon = Icons.Filled.Filter,
                            label = "Filter",
                            selected = state.activeTool == EditorTool.FILTER,
                            testTag = "tab_filter",
                            onClick = { onSelectTool(EditorTool.FILTER) }
                        )
                        EditorToolTab(
                            icon = Icons.Filled.Crop,
                            label = "Transform",
                            selected = state.activeTool == EditorTool.TRANSFORM,
                            testTag = "tab_transform",
                            onClick = { onSelectTool(EditorTool.TRANSFORM) }
                        )
                    }
                }
            }
        }
    }

    if (showHistorySheet) {
        EditHistorySheet(
            currentActionLabel = state.currentActionLabel,
            undoHistory = state.undoHistory,
            redoHistory = state.redoHistory,
            onUndoSteps = onUndoSteps,
            onRedoSteps = onRedoSteps,
            onDismiss = { showHistorySheet = false }
        )
    }
}

@Composable
private fun EraseObjectPanel(
    state: EditorState,
    settings: StudioSettings,
    isOfflineEffective: Boolean,
    onBrushRadiusChanged: (Float) -> Unit,
    onUndoStroke: () -> Unit,
    onRedoStroke: () -> Unit,
    onClearStrokes: () -> Unit,
    onSelectQuality: (OfflineInpaintQuality) -> Unit,
    onExecute: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (isOfflineEffective) {
                    "Usap objek untuk hapus (Mode Offline Lokal)"
                } else {
                    "Usap objek untuk hapus (Lokal)"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = settings.inpaintQuality == OfflineInpaintQuality.DETAILED_TEXTURE,
                    onClick = { onSelectQuality(OfflineInpaintQuality.DETAILED_TEXTURE) },
                    label = { Text("Detail", style = MaterialTheme.typography.labelSmall) }
                )
                FilterChip(
                    selected = settings.inpaintQuality == OfflineInpaintQuality.FAST_DIFFUSION,
                    onClick = { onSelectQuality(OfflineInpaintQuality.FAST_DIFFUSION) },
                    label = { Text("Cepat", style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Kuas",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(42.dp)
            )
            Slider(
                value = state.brushRadiusFraction,
                onValueChange = onBrushRadiusChanged,
                valueRange = 0.015f..0.11f,
                modifier = Modifier
                    .weight(1f)
                    .testTag("brush_size_slider")
            )
            IconButton(
                onClick = onUndoStroke,
                enabled = state.canUndoMaskStroke && !state.isProcessingObjectRemoval,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("undo_stroke_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Urungkan kuas")
            }
            IconButton(
                onClick = onRedoStroke,
                enabled = state.canRedoMaskStroke && !state.isProcessingObjectRemoval,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("redo_stroke_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Ulangi kuas")
            }
            IconButton(
                onClick = onClearStrokes,
                enabled = state.maskStrokes.isNotEmpty() && !state.isProcessingObjectRemoval,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("clear_mask_button")
            ) {
                Icon(Icons.Filled.CleaningServices, contentDescription = "Bersihkan kuas")
            }
        }

        Button(
            onClick = onExecute,
            enabled = state.maskStrokes.isNotEmpty() && !state.isProcessingObjectRemoval,
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("execute_erase_button")
        ) {
            Icon(
                imageVector = Icons.Filled.AutoFixHigh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (state.maskStrokes.isEmpty()) {
                    "Usap Objek pada Foto"
                } else {
                    "Hapus Objek Sekarang (Offline Lokal)"
                },
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun AdjustPanel(
    adjustments: ImageAdjustments,
    selectedParam: AdjustParam,
    onSelectParam: (AdjustParam) -> Unit,
    onUpdateAdjustments: (ImageAdjustments) -> Unit,
    onFinishDrag: () -> Unit,
    onReset: () -> Unit,
    onCommit: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(AdjustParam.entries) { param ->
                FilterChip(
                    selected = selectedParam == param,
                    onClick = { onSelectParam(param) },
                    label = { Text(param.label, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }

        val (value, range, setter) = when (selectedParam) {
            AdjustParam.BRIGHTNESS -> Triple(
                adjustments.brightness,
                -0.45f..0.45f
            ) { v: Float -> adjustments.copy(brightness = v) }
            AdjustParam.CONTRAST -> Triple(
                adjustments.contrast,
                0.5f..1.6f
            ) { v: Float -> adjustments.copy(contrast = v) }
            AdjustParam.SATURATION -> Triple(
                adjustments.saturation,
                0f..2f
            ) { v: Float -> adjustments.copy(saturation = v) }
            AdjustParam.WARMTH -> Triple(
                adjustments.warmth,
                -0.4f..0.4f
            ) { v: Float -> adjustments.copy(warmth = v) }
            AdjustParam.VIGNETTE -> Triple(
                adjustments.vignette,
                0f..0.8f
            ) { v: Float -> adjustments.copy(vignette = v) }
        }

        Slider(
            value = value,
            onValueChange = { onUpdateAdjustments(setter(it)) },
            onValueChangeFinished = onFinishDrag,
            valueRange = range,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("adjust_slider")
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = onReset,
                enabled = !adjustments.isDefault,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("adjust_reset_button")
            ) {
                Text("Reset")
            }
            Button(
                onClick = onCommit,
                enabled = !adjustments.isDefault,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("adjust_apply_button")
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Terapkan")
            }
        }
    }
}

@Composable
private fun FilterPanel(
    selectedPreset: FilterPreset,
    onSelectPreset: (FilterPreset) -> Unit,
    onCommit: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            items(FilterPreset.entries) { preset ->
                val selected = selectedPreset == preset
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .testTag("filter_${preset.name}")
                        .clickable { onSelectPreset(preset) }
                ) {
                    Text(
                        text = preset.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }

        Button(
            onClick = onCommit,
            enabled = selectedPreset != FilterPreset.ORIGINAL,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("filter_apply_button")
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Terapkan Filter & Simpan Otomatis")
        }
    }
}

@Composable
private fun TransformPanel(
    selectedRatio: CropAspectRatio,
    insetFraction: Float,
    onRotate90: (Boolean) -> Unit,
    onFlip: (Boolean) -> Unit,
    onSelectRatio: (CropAspectRatio) -> Unit,
    onInsetChanged: (Float) -> Unit,
    onApplyCrop: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = { onRotate90(false) },
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("rotate_left_button")
            ) {
                Icon(Icons.Filled.Rotate90DegreesCcw, contentDescription = "Putar kiri", modifier = Modifier.size(18.dp))
            }
            OutlinedButton(
                onClick = { onRotate90(true) },
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("rotate_right_button")
            ) {
                Icon(Icons.Filled.Rotate90DegreesCw, contentDescription = "Putar kanan", modifier = Modifier.size(18.dp))
            }
            OutlinedButton(
                onClick = { onFlip(true) },
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("flip_h_button")
            ) {
                Icon(Icons.Filled.Flip, contentDescription = "Balik horizontal", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("H", style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                onClick = { onFlip(false) },
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("flip_v_button")
            ) {
                Icon(Icons.Filled.Flip, contentDescription = "Balik vertikal", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("V", style = MaterialTheme.typography.labelMedium)
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(CropAspectRatio.entries) { ratio ->
                FilterChip(
                    selected = selectedRatio == ratio,
                    onClick = { onSelectRatio(ratio) },
                    label = { Text(ratio.label, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Area", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(38.dp))
            Slider(
                value = insetFraction,
                onValueChange = onInsetChanged,
                valueRange = 0f..0.32f,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onApplyCrop,
                modifier = Modifier
                    .height(44.dp)
                    .testTag("apply_crop_button")
            ) {
                Text("Potong")
            }
        }
    }
}

@Composable
private fun EditorToolTab(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .testTag(testTag)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

private fun computeFitRect(
    containerW: Float,
    containerH: Float,
    bitmapW: Float,
    bitmapH: Float
): Rect {
    if (containerW <= 0f || containerH <= 0f || bitmapW <= 0f || bitmapH <= 0f) {
        return Rect.Zero
    }
    val scale = min(containerW / bitmapW, containerH / bitmapH)
    val drawnW = bitmapW * scale
    val drawnH = bitmapH * scale
    val left = (containerW - drawnW) * 0.5f
    val top = (containerH - drawnH) * 0.5f
    return Rect(left, top, left + drawnW, top + drawnH)
}

private fun computeNormalizedCropRect(
    bitmapW: Float,
    bitmapH: Float,
    ratio: CropAspectRatio,
    insetFraction: Float
): Rect {
    val baseScale = 1f - insetFraction.coerceIn(0f, 0.35f) * 2f
    val targetRatio = ratio.ratio
    if (targetRatio == null || bitmapW <= 0f || bitmapH <= 0f) {
        val margin = (1f - baseScale) * 0.5f
        return Rect(margin, margin, 1f - margin, 1f - margin)
    }
    val imageAspect = bitmapW / bitmapH
    var normW: Float
    var normH: Float
    if (imageAspect > targetRatio) {
        normH = baseScale
        normW = (normH * targetRatio) / imageAspect
    } else {
        normW = baseScale
        normH = (normW * imageAspect) / targetRatio
    }
    normW = normW.coerceIn(0.15f, 1f)
    normH = normH.coerceIn(0.15f, 1f)
    val left = (1f - normW) * 0.5f
    val top = (1f - normH) * 0.5f
    return Rect(left, top, left + normW, top + normH)
}
