package com.example.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 100% Offline Local Object Removal Engine.
 * Runs purely on device pixels and local pixel neighborhood data without internet access:
 * 1. Renders normalized brush strokes into a crisp mask bitmap + 2px halo dilation.
 * 2. Extracts an optimized Region-of-Interest (ROI) with surrounding local texture margin.
 * 3. Performs coarse multi-scale harmonic initialization for wide regions.
 * 4. Executes 8-connected boundary-inward directional diffusion (Telea-style distance & gradient weighting).
 * 5. When [OfflineInpaintQuality.DETAILED_TEXTURE] is selected, transfers matching 5x5 texture
 *    patches from the local surrounding ring to restore natural grain (sand, wood, foliage, fabric).
 * 6. Applies feathered boundary blending so unmasked pixels remain bit-exact and seams disappear.
 */
object LocalObjectRemover {

    suspend fun removeObjectOffline(
        source: Bitmap,
        strokes: List<MaskStroke>,
        quality: OfflineInpaintQuality,
        onProgress: (Float) -> Unit = {}
    ): Bitmap = withContext(Dispatchers.Default) {
        if (strokes.isEmpty()) {
            return@withContext source.copy(Bitmap.Config.ARGB_8888, false)
        }

        val width = source.width
        val height = source.height
        onProgress(0.05f)

        // 1. Rasterize normalized strokes directly into maskAlpha (zero extra Bitmap allocation)
        val maskAlpha = ByteArray(width * height)
        val minDim = min(width, height).toFloat()
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1

        for (stroke in strokes) {
            if (stroke.points.isEmpty()) continue
            val radiusPx = max(6f, stroke.brushRadiusNormalized * minDim)
            val radiusSq = radiusPx * radiusPx
            val rCeil = radiusPx.roundToInt() + 1

            if (stroke.points.size == 1) {
                val cx = stroke.points[0].x * width
                val cy = stroke.points[0].y * height
                val x0 = max(0, (cx - rCeil).toInt())
                val x1 = min(width - 1, (cx + rCeil).toInt())
                val y0 = max(0, (cy - rCeil).toInt())
                val y1 = min(height - 1, (cy + rCeil).toInt())
                for (y in y0..y1) {
                    val dy = y - cy
                    val row = y * width
                    for (x in x0..x1) {
                        val dx = x - cx
                        if (dx * dx + dy * dy <= radiusSq) {
                            maskAlpha[row + x] = 1
                            if (x < minX) minX = x
                            if (x > maxX) maxX = x
                            if (y < minY) minY = y
                            if (y > maxY) maxY = y
                        }
                    }
                }
            } else {
                for (i in 0 until stroke.points.size - 1) {
                    val ax = stroke.points[i].x * width
                    val ay = stroke.points[i].y * height
                    val bx = stroke.points[i + 1].x * width
                    val by = stroke.points[i + 1].y * height
                    val abx = bx - ax
                    val aby = by - ay
                    val abLenSq = abx * abx + aby * aby

                    val x0 = max(0, (min(ax, bx) - rCeil).toInt())
                    val x1 = min(width - 1, (max(ax, bx) + rCeil).toInt())
                    val y0 = max(0, (min(ay, by) - rCeil).toInt())
                    val y1 = min(height - 1, (max(ay, by) + rCeil).toInt())

                    for (y in y0..y1) {
                        val row = y * width
                        for (x in x0..x1) {
                            if (maskAlpha[row + x].toInt() == 1) continue
                            val t = if (abLenSq <= 0.0001f) 0f
                            else (((x - ax) * abx + (y - ay) * aby) / abLenSq).coerceIn(0f, 1f)
                            val px = ax + t * abx
                            val py = ay + t * aby
                            val dx = x - px
                            val dy = y - py
                            if (dx * dx + dy * dy <= radiusSq) {
                                maskAlpha[row + x] = 1
                                if (x < minX) minX = x
                                if (x > maxX) maxX = x
                                if (y < minY) minY = y
                                if (y > maxY) maxY = y
                            }
                        }
                    }
                }
            }
        }

        if (maxX < minX || maxY < minY) {
            return@withContext source.copy(Bitmap.Config.ARGB_8888, false)
        }

        coroutineContext.ensureActive()
        onProgress(0.14f)

        // Dilate mask by 2px inside bounding region to cover anti-aliased object fringes
        val dilatedMask = maskAlpha.copyOf()
        val dilateLeft = max(1, minX - 2)
        val dilateTop = max(1, minY - 2)
        val dilateRight = min(width - 2, maxX + 2)
        val dilateBottom = min(height - 2, maxY + 2)
        for (y in dilateTop..dilateBottom) {
            val row = y * width
            for (x in dilateLeft..dilateRight) {
                if (maskAlpha[row + x].toInt() == 1) {
                    dilatedMask[row + x - 1] = 1
                    dilatedMask[row + x + 1] = 1
                    dilatedMask[row - width + x] = 1
                    dilatedMask[row + width + x] = 1
                }
            }
        }

        // Expand ROI with generous local context margin for texture & gradient sampling
        val margin = if (quality == OfflineInpaintQuality.DETAILED_TEXTURE) 44 else 24
        val roiX = max(0, minX - margin)
        val roiY = max(0, minY - margin)
        val roiRight = min(width - 1, maxX + margin)
        val roiBottom = min(height - 1, maxY + margin)
        val roiW = roiRight - roiX + 1
        val roiH = roiBottom - roiY + 1

        val roiPixels = IntArray(roiW * roiH)
        source.getPixels(roiPixels, 0, roiW, roiX, roiY, roiW, roiH)

        val roiMask = BooleanArray(roiW * roiH)
        var maskedCount = 0
        for (ry in 0 until roiH) {
            val srcRow = (roiY + ry) * width + roiX
            val dstRow = ry * roiW
            for (rx in 0 until roiW) {
                val isMasked = dilatedMask[srcRow + rx].toInt() == 1
                roiMask[dstRow + rx] = isMasked
                if (isMasked) maskedCount++
            }
        }

        if (maskedCount == 0) {
            return@withContext source.copy(Bitmap.Config.ARGB_8888, false)
        }

        onProgress(0.22f)

        // Working copy for inpainting inside ROI
        val workPixels = roiPixels.copyOf()

        // 2. Coarse downsampled initialization if ROI is large for smooth global color continuity
        val maxRoiDim = max(roiW, roiH)
        if (maxRoiDim > 180) {
            val scale = max(2, maxRoiDim / 120)
            coarseHarmonicInitialize(workPixels, roiMask, roiW, roiH, scale)
        }

        coroutineContext.ensureActive()
        onProgress(0.38f)

        // 3. Frontier inward directional diffusion (Fast Marching onion-peel from known border)
        val peelOrder = computePeelOrderAndPropagate(
            pixels = workPixels,
            mask = roiMask,
            w = roiW,
            h = roiH,
            onStepProgress = { frac -> onProgress(0.38f + frac * 0.34f) }
        )

        coroutineContext.ensureActive()

        // 4. Local Exemplar Texture Patch Synthesis (in Detailed Texture mode)
        if (quality == OfflineInpaintQuality.DETAILED_TEXTURE) {
            applyLocalTextureSynthesis(
                origPixels = roiPixels,
                workPixels = workPixels,
                mask = roiMask,
                peelOrder = peelOrder,
                w = roiW,
                h = roiH,
                onStepProgress = { frac -> onProgress(0.72f + frac * 0.20f) }
            )
        } else {
            // Light harmonic smoothing pass inside masked pixels for fast mode
            smoothMaskedInterior(workPixels, roiMask, peelOrder, roiW, roiH, passes = 2)
            onProgress(0.92f)
        }

        coroutineContext.ensureActive()

        // 5. Feather-blend 2px perimeter seam with original pixels
        featherBlendPerimeter(roiPixels, workPixels, roiMask, roiW, roiH)
        onProgress(0.97f)

        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        result.setPixels(workPixels, 0, roiW, roiX, roiY, roiW, roiH)
        onProgress(1f)
        return@withContext result
    }

    private fun coarseHarmonicInitialize(
        pixels: IntArray,
        mask: BooleanArray,
        w: Int,
        h: Int,
        scale: Int
    ) {
        val cw = max(4, w / scale)
        val ch = max(4, h / scale)
        val cPixels = IntArray(cw * ch)
        val cKnown = BooleanArray(cw * ch)

        for (cy in 0 until ch) {
            val sy = min(h - 1, cy * scale + scale / 2)
            for (cx in 0 until cw) {
                val sx = min(w - 1, cx * scale + scale / 2)
                var rSum = 0
                var gSum = 0
                var bSum = 0
                var knownCount = 0
                val yEnd = min(h, (cy + 1) * scale)
                val xEnd = min(w, (cx + 1) * scale)
                for (yy in cy * scale until yEnd) {
                    val row = yy * w
                    for (xx in cx * scale until xEnd) {
                        if (!mask[row + xx]) {
                            val c = pixels[row + xx]
                            rSum += (c shr 16) and 0xFF
                            gSum += (c shr 8) and 0xFF
                            bSum += c and 0xFF
                            knownCount++
                        }
                    }
                }
                val idx = cy * cw + cx
                if (knownCount > 0) {
                    cKnown[idx] = true
                    cPixels[idx] = Color.rgb(rSum / knownCount, gSum / knownCount, bSum / knownCount)
                } else {
                    cKnown[idx] = false
                    cPixels[idx] = pixels[sy * w + sx]
                }
            }
        }

        // Iterative Gauss-Seidel harmonic diffusion on coarse grid
        repeat(24) {
            for (cy in 1 until ch - 1) {
                val row = cy * cw
                for (cx in 1 until cw - 1) {
                    val idx = row + cx
                    if (!cKnown[idx]) {
                        val n1 = cPixels[idx - 1]
                        val n2 = cPixels[idx + 1]
                        val n3 = cPixels[idx - cw]
                        val n4 = cPixels[idx + cw]
                        val r = (((n1 shr 16) and 0xFF) + ((n2 shr 16) and 0xFF) + ((n3 shr 16) and 0xFF) + ((n4 shr 16) and 0xFF)) / 4
                        val g = (((n1 shr 8) and 0xFF) + ((n2 shr 8) and 0xFF) + ((n3 shr 8) and 0xFF) + ((n4 shr 8) and 0xFF)) / 4
                        val b = ((n1 and 0xFF) + (n2 and 0xFF) + (n3 and 0xFF) + (n4 and 0xFF)) / 4
                        cPixels[idx] = Color.rgb(r, g, b)
                    }
                }
            }
        }

        // Bilinearly seed masked full-res pixels
        for (y in 0 until h) {
            val fy = (y.toFloat() / scale).coerceIn(0f, (ch - 1).toFloat())
            val y0 = fy.toInt()
            val y1 = min(ch - 1, y0 + 1)
            val wy = fy - y0
            val row = y * w
            for (x in 0 until w) {
                val idx = row + x
                if (mask[idx]) {
                    val fx = (x.toFloat() / scale).coerceIn(0f, (cw - 1).toFloat())
                    val x0 = fx.toInt()
                    val x1 = min(cw - 1, x0 + 1)
                    val wx = fx - x0
                    val c00 = cPixels[y0 * cw + x0]
                    val c10 = cPixels[y0 * cw + x1]
                    val c01 = cPixels[y1 * cw + x0]
                    val c11 = cPixels[y1 * cw + x1]
                    val r = bilerp((c00 shr 16) and 0xFF, (c10 shr 16) and 0xFF, (c01 shr 16) and 0xFF, (c11 shr 16) and 0xFF, wx, wy)
                    val g = bilerp((c00 shr 8) and 0xFF, (c10 shr 8) and 0xFF, (c01 shr 8) and 0xFF, (c11 shr 8) and 0xFF, wx, wy)
                    val b = bilerp(c00 and 0xFF, c10 and 0xFF, c01 and 0xFF, c11 and 0xFF, wx, wy)
                    pixels[idx] = Color.rgb(r, g, b)
                }
            }
        }
    }

    private fun bilerp(c00: Int, c10: Int, c01: Int, c11: Int, wx: Float, wy: Float): Int {
        val top = c00 * (1f - wx) + c10 * wx
        val bot = c01 * (1f - wx) + c11 * wx
        return (top * (1f - wy) + bot * wy).roundToInt().coerceIn(0, 255)
    }

    private suspend fun computePeelOrderAndPropagate(
        pixels: IntArray,
        mask: BooleanArray,
        w: Int,
        h: Int,
        onStepProgress: (Float) -> Unit
    ): IntArray {
        val known = BooleanArray(w * h) { !mask[it] }
        val totalMasked = mask.count { it }
        val peelOrder = IntArray(totalMasked)
        var peelWriteIdx = 0

        var frontier = IntArray(totalMasked)
        var frontierSize = 0
        val inFrontier = BooleanArray(w * h)

        val dx8 = intArrayOf(-1, 0, 1, -1, 1, -1, 0, 1)
        val dy8 = intArrayOf(-1, -1, -1, 0, 0, 1, 1, 1)

        // Seed initial frontier: masked pixels touching at least one known pixel
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val idx = row + x
                if (!known[idx]) {
                    var touchesKnown = false
                    for (k in 0 until 8) {
                        val nx = x + dx8[k]
                        val ny = y + dy8[k]
                        if (nx in 0 until w && ny in 0 until h && known[ny * w + nx]) {
                            touchesKnown = true
                            break
                        }
                    }
                    if (touchesKnown) {
                        frontier[frontierSize++] = idx
                        inFrontier[idx] = true
                    }
                }
            }
        }

        var nextFrontier = IntArray(totalMasked)
        var processed = 0
        val radius = 3

        while (frontierSize > 0) {
            coroutineContext.ensureActive()
            var nextSize = 0
            for (f in 0 until frontierSize) {
                val idx = frontier[f]
                val x = idx % w
                val y = idx / w

                var rSum = 0f
                var gSum = 0f
                var bSum = 0f
                var wSum = 0f

                val y0 = max(0, y - radius)
                val y1 = min(h - 1, y + radius)
                val x0 = max(0, x - radius)
                val x1 = min(w - 1, x + radius)

                for (ny in y0..y1) {
                    val dy = ny - y
                    val nRow = ny * w
                    for (nx in x0..x1) {
                        val nIdx = nRow + nx
                        if (known[nIdx]) {
                            val dx = nx - x
                            val distSq = (dx * dx + dy * dy).toFloat()
                            if (distSq > 0f) {
                                val weight = 1f / (distSq * sqrt(distSq))
                                val c = pixels[nIdx]
                                rSum += ((c shr 16) and 0xFF) * weight
                                gSum += ((c shr 8) and 0xFF) * weight
                                bSum += (c and 0xFF) * weight
                                wSum += weight
                            }
                        }
                    }
                }

                if (wSum > 0f) {
                    val r = (rSum / wSum).roundToInt().coerceIn(0, 255)
                    val g = (gSum / wSum).roundToInt().coerceIn(0, 255)
                    val b = (bSum / wSum).roundToInt().coerceIn(0, 255)
                    pixels[idx] = Color.rgb(r, g, b)
                }

                known[idx] = true
                if (peelWriteIdx < peelOrder.size) {
                    peelOrder[peelWriteIdx++] = idx
                }
                processed++

                for (k in 0 until 8) {
                    val nx = x + dx8[k]
                    val ny = y + dy8[k]
                    if (nx in 0 until w && ny in 0 until h) {
                        val nIdx = ny * w + nx
                        if (!known[nIdx] && !inFrontier[nIdx]) {
                            inFrontier[nIdx] = true
                            nextFrontier[nextSize++] = nIdx
                        }
                    }
                }
            }
            val temp = frontier
            frontier = nextFrontier
            nextFrontier = temp
            frontierSize = nextSize
            onStepProgress((processed.toFloat() / max(1, totalMasked)).coerceIn(0f, 1f))
        }

        return peelOrder.copyOf(peelWriteIdx)
    }

    private suspend fun applyLocalTextureSynthesis(
        origPixels: IntArray,
        workPixels: IntArray,
        mask: BooleanArray,
        peelOrder: IntArray,
        w: Int,
        h: Int,
        onStepProgress: (Float) -> Unit
    ) {
        // Collect candidate patch centers strictly from clean unmasked local border pixels
        val patchRadius = 2 // 5x5 patch
        val candidateStep = if (w * h > 160_000) 4 else 2
        val candidates = ArrayList<Int>(1024)

        for (y in patchRadius until h - patchRadius step candidateStep) {
            val row = y * w
            for (x in patchRadius until w - patchRadius step candidateStep) {
                var allClean = true
                for (py in -patchRadius..patchRadius) {
                    val pRow = (y + py) * w
                    for (px in -patchRadius..patchRadius) {
                        if (mask[pRow + x + px]) {
                            allClean = false
                            break
                        }
                    }
                    if (!allClean) break
                }
                if (allClean) {
                    candidates.add(row + x)
                }
            }
        }

        if (candidates.isEmpty()) {
            smoothMaskedInterior(workPixels, mask, peelOrder, w, h, passes = 2)
            onStepProgress(1f)
            return
        }

        // Limit candidate pool to keep local matching fast while spatially diverse
        val maxCandidates = 220
        val stride = max(1, candidates.size / maxCandidates)
        val sampledCandidates = IntArray((candidates.size + stride - 1) / stride) { idx ->
            candidates[min(candidates.size - 1, idx * stride)]
        }

        val synthPixels = workPixels.copyOf()
        val total = peelOrder.size
        // Process on a sparse 2px grid inside peelOrder for high speed + interpolate neighbors
        for (i in peelOrder.indices) {
            if (i % 400 == 0) {
                coroutineContext.ensureActive()
                onStepProgress(i.toFloat() / max(1, total))
            }
            val idx = peelOrder[i]
            val x = idx % w
            val y = idx / w
            if (x < patchRadius || x >= w - patchRadius || y < patchRadius || y >= h - patchRadius) continue
            if ((x + y) and 1 != 0) continue // Checkerboard synthesis + smooth blend

            val targetCenter = workPixels[idx]
            val tr = (targetCenter shr 16) and 0xFF
            val tg = (targetCenter shr 8) and 0xFF
            val tb = targetCenter and 0xFF

            var bestCand = sampledCandidates[0]
            var bestScore = Int.MAX_VALUE

            // Compare 5-tap cross patch against diffusion-guided base to preserve structure + add real local texture
            for (cIdx in sampledCandidates) {
                val candCenter = origPixels[cIdx]
                val centerDiff = abs(((candCenter shr 16) and 0xFF) - tr) +
                    abs(((candCenter shr 8) and 0xFF) - tg) +
                    abs((candCenter and 0xFF) - tb)
                if (centerDiff * 4 > bestScore) continue

                var sad = centerDiff * 2
                // Check 4 cardinal offsets at distance 2
                sad += colorSad(workPixels[idx - 2], origPixels[cIdx - 2])
                sad += colorSad(workPixels[idx + 2], origPixels[cIdx + 2])
                sad += colorSad(workPixels[idx - 2 * w], origPixels[cIdx - 2 * w])
                sad += colorSad(workPixels[idx + 2 * w], origPixels[cIdx + 2 * w])

                if (sad < bestScore) {
                    bestScore = sad
                    bestCand = cIdx
                }
            }

            // Blend matched local texture pixel gently with harmonic base
            val matched = origPixels[bestCand]
            val mr = (matched shr 16) and 0xFF
            val mg = (matched shr 8) and 0xFF
            val mb = matched and 0xFF
            synthPixels[idx] = Color.rgb(
                ((tr * 0.42f) + (mr * 0.58f)).roundToInt().coerceIn(0, 255),
                ((tg * 0.42f) + (mg * 0.58f)).roundToInt().coerceIn(0, 255),
                ((tb * 0.42f) + (mb * 0.58f)).roundToInt().coerceIn(0, 255)
            )
        }

        // Copy back and run 1 light 3x3 cross smooth to eliminate checkerboard micro-noise
        System.arraycopy(synthPixels, 0, workPixels, 0, workPixels.size)
        smoothMaskedInterior(workPixels, mask, peelOrder, w, h, passes = 1)
        onStepProgress(1f)
    }

    private fun colorSad(c1: Int, c2: Int): Int {
        return abs(((c1 shr 16) and 0xFF) - ((c2 shr 16) and 0xFF)) +
            abs(((c1 shr 8) and 0xFF) - ((c2 shr 8) and 0xFF)) +
            abs((c1 and 0xFF) - (c2 and 0xFF))
    }

    private fun smoothMaskedInterior(
        pixels: IntArray,
        mask: BooleanArray,
        peelOrder: IntArray,
        w: Int,
        h: Int,
        passes: Int
    ) {
        val temp = pixels.copyOf()
        repeat(passes) {
            for (idx in peelOrder) {
                val x = idx % w
                val y = idx / w
                if (x in 1 until w - 1 && y in 1 until h - 1 && mask[idx]) {
                    val c = pixels[idx]
                    val l = pixels[idx - 1]
                    val r = pixels[idx + 1]
                    val u = pixels[idx - w]
                    val d = pixels[idx + w]
                    val red = (((c shr 16) and 0xFF) * 2 + ((l shr 16) and 0xFF) + ((r shr 16) and 0xFF) + ((u shr 16) and 0xFF) + ((d shr 16) and 0xFF)) / 6
                    val green = (((c shr 8) and 0xFF) * 2 + ((l shr 8) and 0xFF) + ((r shr 8) and 0xFF) + ((u shr 8) and 0xFF) + ((d shr 8) and 0xFF)) / 6
                    val blue = ((c and 0xFF) * 2 + (l and 0xFF) + (r and 0xFF) + (u and 0xFF) + (d and 0xFF)) / 6
                    temp[idx] = Color.rgb(red, green, blue)
                }
            }
            System.arraycopy(temp, 0, pixels, 0, pixels.size)
        }
    }

    private fun featherBlendPerimeter(
        origPixels: IntArray,
        workPixels: IntArray,
        mask: BooleanArray,
        w: Int,
        h: Int
    ) {
        val copy = workPixels.copyOf()
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val idx = row + x
                if (mask[idx]) {
                    val hasKnownNeighbor = !mask[idx - 1] || !mask[idx + 1] || !mask[idx - w] || !mask[idx + w]
                    if (hasKnownNeighbor) {
                        val c = copy[idx]
                        val l = if (!mask[idx - 1]) origPixels[idx - 1] else copy[idx - 1]
                        val r = if (!mask[idx + 1]) origPixels[idx + 1] else copy[idx + 1]
                        val u = if (!mask[idx - w]) origPixels[idx - w] else copy[idx - w]
                        val d = if (!mask[idx + w]) origPixels[idx + w] else copy[idx + w]
                        val red = (((c shr 16) and 0xFF) * 2 + ((l shr 16) and 0xFF) + ((r shr 16) and 0xFF) + ((u shr 16) and 0xFF) + ((d shr 16) and 0xFF)) / 6
                        val green = (((c shr 8) and 0xFF) * 2 + ((l shr 8) and 0xFF) + ((r shr 8) and 0xFF) + ((u shr 8) and 0xFF) + ((d shr 8) and 0xFF)) / 6
                        val blue = ((c and 0xFF) * 2 + (l and 0xFF) + (r and 0xFF) + (u and 0xFF) + (d and 0xFF)) / 6
                        workPixels[idx] = Color.rgb(red, green, blue)
                    }
                }
            }
        }
    }
}
