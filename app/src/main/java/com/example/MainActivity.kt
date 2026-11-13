package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.AutoSaveStatus
import com.example.ui.MainViewModel
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsBottomSheet
import com.example.ui.theme.LokaFotoTheme

private const val ROUTE_HOME = "home"
private const val ROUTE_EDITOR = "editor"

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            LokaFotoTheme(darkTheme = true) {
                LokaFotoApp(
                    viewModel = mainViewModel,
                    onShareFile = { filePath ->
                        val uri = mainViewModel.getShareUri(filePath) ?: return@LokaFotoApp
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/*"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(shareIntent, "Bagikan Hasil Edit"))
                    }
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        mainViewModel.onAppBackgrounded()
    }
}

@Composable
private fun LokaFotoApp(
    viewModel: MainViewModel,
    onShareFile: (String) -> Unit
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val savedPhotos by viewModel.savedPhotos.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isDeviceOnline by viewModel.isDeviceOnline.collectAsStateWithLifecycle()
    val editorState by viewModel.editorState.collectAsStateWithLifecycle()
    val shouldAutoOpenEditor by viewModel.shouldAutoOpenEditor.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val isLoadingPhoto by viewModel.isLoadingPhoto.collectAsStateWithLifecycle()

    var showSettingsSheet by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.openPhotoFromUri(uri) {
                navController.navigate(ROUTE_EDITOR) {
                    launchSingleTop = true
                }
            }
        }
    }

    LaunchedEffect(shouldAutoOpenEditor) {
        if (shouldAutoOpenEditor) {
            viewModel.consumeAutoOpenEditor()
            navController.navigate(ROUTE_EDITOR) {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeSnackbar()
    }

    NavHost(
        navController = navController,
        startDestination = ROUTE_HOME
    ) {
        composable(ROUTE_HOME) {
            HomeScreen(
                savedPhotos = savedPhotos,
                settings = settings,
                isDeviceOnline = isDeviceOnline,
                isLoadingPhoto = isLoadingPhoto,
                snackbarHostState = snackbarHostState,
                onPickPhoto = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onSelectSample = { sample ->
                    viewModel.openSamplePhoto(sample) {
                        navController.navigate(ROUTE_EDITOR) {
                            launchSingleTop = true
                        }
                    }
                },
                onOpenSavedPhoto = { photo ->
                    viewModel.openSavedPhoto(photo) {
                        navController.navigate(ROUTE_EDITOR) {
                            launchSingleTop = true
                        }
                    }
                },
                onExportSavedPhoto = { photo ->
                    viewModel.exportPhotoToGallery(photo.filePath, photo.title)
                },
                onShareSavedPhoto = { photo ->
                    onShareFile(photo.filePath)
                },
                onDeleteSavedPhoto = { photo ->
                    viewModel.deleteSavedPhoto(photo)
                },
                onToggleOfflineMode = {
                    viewModel.toggleOfflineMode(!settings.offlineModePreferred)
                },
                onOpenSettings = { showSettingsSheet = true }
            )
        }

        composable(ROUTE_EDITOR) {
            EditorScreen(
                state = editorState,
                settings = settings,
                isDeviceOnline = isDeviceOnline,
                snackbarHostState = snackbarHostState,
                onCloseCleanly = {
                    viewModel.closeEditorCleanly {
                        navController.popBackStack(ROUTE_HOME, inclusive = false)
                    }
                },
                onDiscardRecovered = {
                    viewModel.discardRecoveredSessionAndExit {
                        navController.popBackStack(ROUTE_HOME, inclusive = false)
                    }
                },
                onDismissRecoveryBanner = {
                    viewModel.dismissRecoveryBanner()
                },
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                onUndoSteps = { viewModel.undoSteps(it) },
                onRedoSteps = { viewModel.redoSteps(it) },
                onComparingChanged = { viewModel.setComparingOriginal(it) },
                onToggleOfflineMode = {
                    viewModel.toggleOfflineMode(!settings.offlineModePreferred)
                },
                onSelectTool = { viewModel.selectTool(it) },
                onBrushRadiusChanged = { viewModel.setBrushRadiusFraction(it) },
                onAddMaskStroke = { viewModel.addMaskStroke(it) },
                onUndoMaskStroke = { viewModel.undoMaskStroke() },
                onRedoMaskStroke = { viewModel.redoMaskStroke() },
                onClearMaskStrokes = { viewModel.clearMaskStrokes() },
                onSelectInpaintQuality = { viewModel.setInpaintQuality(it) },
                onExecuteObjectRemoval = { viewModel.executeOfflineObjectRemoval() },
                onUpdateAdjustments = { viewModel.updateAdjustments(it) },
                onFinishAdjustmentDrag = { viewModel.finishAdjustmentDrag(it) },
                onResetAdjustments = { viewModel.resetAdjustments() },
                onCommitAdjustments = { viewModel.commitAdjustments() },
                onSelectFilterPreset = { viewModel.selectFilterPreset(it) },
                onRotate90 = { viewModel.rotate90(it) },
                onFlip = { viewModel.flipImage(it) },
                onSelectCropRatio = { viewModel.selectCropRatio(it) },
                onApplyCrop = { l, t, r, b -> viewModel.applyCrop(l, t, r, b) },
                onSaveNow = { viewModel.saveNowExplicitly() },
                onShareCurrent = {
                    val saved = editorState.autoSaveStatus as? AutoSaveStatus.Saved
                    if (saved != null) {
                        onShareFile(saved.filePath)
                    } else {
                        viewModel.saveNowExplicitly()
                    }
                },
                onExportToGallery = {
                    val saved = editorState.autoSaveStatus as? AutoSaveStatus.Saved
                    if (saved != null) {
                        viewModel.exportPhotoToGallery(saved.filePath, editorState.title)
                    } else {
                        viewModel.saveNowExplicitly()
                    }
                },
                onOpenSettings = { showSettingsSheet = true }
            )
        }
    }

    if (showSettingsSheet) {
        SettingsBottomSheet(
            settings = settings,
            isDeviceOnline = isDeviceOnline,
            onToggleOfflineMode = { viewModel.toggleOfflineMode(it) },
            onSelectSaveFormat = { viewModel.setSaveFormat(it) },
            onSelectInpaintQuality = { viewModel.setInpaintQuality(it) },
            onToggleAutoRecovery = { viewModel.setAutoRecoveryEnabled(it) },
            onDismiss = { showSettingsSheet = false }
        )
    }
}
