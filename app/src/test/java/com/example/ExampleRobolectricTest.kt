package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.InternalStorageManager
import com.example.data.LokaFotoDatabase
import com.example.data.PhotoEditorRepository
import com.example.data.SettingsPreferences
import com.example.engine.EditSnapshot
import com.example.engine.EditStack
import com.example.engine.EditorTool
import com.example.engine.FilterPreset
import com.example.engine.ImageAdjustments
import com.example.engine.LocalObjectRemover
import com.example.engine.MaskStroke
import com.example.engine.NormalizedPoint
import com.example.engine.OfflineInpaintQuality
import com.example.engine.SaveFormat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches Dableh-FOTO`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Dableh-FOTO", context.getString(R.string.app_name))
    }

    @Test
    fun `offline object removal replaces masked red object with surrounding blue pixels`() = runTest {
        val width = 80
        val height = 80
        val source = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height) { Color.rgb(40, 120, 220) }
        for (y in 32 until 48) {
            for (x in 32 until 48) {
                pixels[y * width + x] = Color.rgb(240, 20, 20)
            }
        }
        source.setPixels(pixels, 0, width, 0, 0, width, height)

        val stroke = MaskStroke(
            points = listOf(
                NormalizedPoint(0.42f, 0.50f),
                NormalizedPoint(0.58f, 0.50f)
            ),
            brushRadiusNormalized = 0.14f
        )

        val cleaned = LocalObjectRemover.removeObjectOffline(
            source = source,
            strokes = listOf(stroke),
            quality = OfflineInpaintQuality.DETAILED_TEXTURE
        )

        val centerPixel = cleaned.getPixel(40, 40)
        // Center should now be bluish (blue channel > red channel), no longer bright red
        assertTrue(Color.blue(centerPixel) > Color.red(centerPixel))
        // Unmasked corner pixel should remain untouched
        assertEquals(source.getPixel(2, 2), cleaned.getPixel(2, 2))
    }

    @Test
    fun `autoSaveHighQuality writes valid PNG directly to internal filesDir`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, LokaFotoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = PhotoEditorRepository(
            context = context,
            dao = db.dao(),
            storageManager = InternalStorageManager(context),
            settingsPreferences = SettingsPreferences(context)
        )

        val bmp = Bitmap.createBitmap(120, 90, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.CYAN)
        }
        val saved = repo.autoSaveHighQuality(
            photoId = "test_png_1",
            title = "Uji PNG",
            renderedBitmap = bmp,
            format = SaveFormat.PNG_LOSSLESS,
            usedOfflineEraser = true
        )

        val savedFile = File(saved.filePath)
        assertTrue(savedFile.exists())
        assertTrue(savedFile.absolutePath.startsWith(context.filesDir.absolutePath))
        assertTrue(savedFile.length() > 0L)
        val decoded = BitmapFactory.decodeFile(savedFile.absolutePath)
        assertNotNull(decoded)
        assertEquals(120, decoded.width)
        assertEquals(90, decoded.height)
        db.close()
    }

    @Test
    fun `unexpected exit triggers auto recovery while clean exit clears recovery`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, LokaFotoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = PhotoEditorRepository(
            context = context,
            dao = db.dao(),
            storageManager = InternalStorageManager(context),
            settingsPreferences = SettingsPreferences(context)
        )

        val orig = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.DKGRAY) }
        val work = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }

        repo.saveRecoveryCheckpoint(
            projectId = "proj_crash_1",
            title = "Sesi Terputus",
            originalBitmap = orig,
            workingBitmap = work,
            adjustments = ImageAdjustments(brightness = 0.15f, filterPreset = FilterPreset.WARM),
            activeTool = EditorTool.ERASE_OBJECT,
            offlineModeActive = true,
            savedPhotoId = null
        )

        // Simulate restart without clean exit
        val restored = repo.getInterruptedRecoverySession()
        assertNotNull(restored)
        assertEquals("proj_crash_1", restored!!.projectId)
        assertEquals(0.15f, restored.brightness, 0.001f)
        assertEquals(FilterPreset.WARM.name, restored.filterPresetName)
        assertNotEquals(true, restored.cleanExit)

        // Simulate clean user exit
        repo.markCleanExit()
        val afterClean = repo.getInterruptedRecoverySession()
        assertNull(afterClean)
        db.close()
    }

    @Test
    fun `editStack performs LIFO undo redo and clears redo branch on new edit`() {
        val stack = EditStack<EditSnapshot>(maxCapacity = 10)
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)

        var current = EditSnapshot(bmp, ImageAdjustments(), "Foto Awal")
        assertFalse(stack.canUndo)
        assertFalse(stack.canRedo)

        // Edit 1: Filter Hangat
        stack.push(current)
        current = EditSnapshot(bmp, ImageAdjustments(filterPreset = FilterPreset.WARM), "Filter: Hangat")
        // Edit 2: Putar +90
        stack.push(current)
        current = EditSnapshot(bmp, ImageAdjustments(filterPreset = FilterPreset.WARM), "Putar +90°")

        assertEquals(2, stack.undoCount)
        assertEquals(0, stack.redoCount)

        // Undo 1 -> Filter Hangat
        current = stack.undo(current)!!
        assertEquals("Filter: Hangat", current.actionLabel)
        assertEquals(1, stack.undoCount)
        assertEquals(1, stack.redoCount)

        // Undo 2 -> Foto Awal
        current = stack.undo(current)!!
        assertEquals("Foto Awal", current.actionLabel)
        assertEquals(0, stack.undoCount)
        assertEquals(2, stack.redoCount)

        // Redo 1 -> Filter Hangat
        current = stack.redo(current)!!
        assertEquals("Filter: Hangat", current.actionLabel)
        assertEquals(1, stack.undoCount)
        assertEquals(1, stack.redoCount)

        // New edit clears Redo stack
        stack.push(current)
        current = EditSnapshot(bmp, ImageAdjustments(filterPreset = FilterPreset.VINTAGE), "Filter: Vintage")
        assertEquals(2, stack.undoCount)
        assertEquals(0, stack.redoCount)
        assertFalse(stack.canRedo)
    }
}
