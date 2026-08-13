package com.molanko.avatargenerator.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure-logic texture processor for Minecraft-style skins.
 * Ported from the original JS implementation to guarantee identical
 * nearest-neighbor behaviour across platforms.
 */
object TextureProcessor {

    data class ProcessOptions(
        val outlineMode: Int = 0,          // 0 = none, 1+ = radius
        val outlineColor: String = "#000000", // hex or "auto_dark" / "auto_darker" / "auto_medium_dark"
        val bgColor: String = "#ffffff",      // hex or "auto_light" / "auto_lighter" / "auto_medium_light"
        val upscale48: Boolean = false,
        val fillBackground: Boolean = true,
        val scale: Float = 1f
    )

    data class Rgb(val r: Int, val g: Int, val b: Int)

    // ---------- Public entry ----------

    fun processTexture(source: Bitmap, options: ProcessOptions = ProcessOptions()): Bitmap {
        require(source.width >= 64 && source.height >= 16) {
            "Image too small: ${source.width}×${source.height}, need at least 64×64"
        }

        // 1. Head region average colour (64×16)
        val head = Bitmap.createBitmap(64, 16, Bitmap.Config.ARGB_8888)
        Canvas(head).drawBitmap(
            source,
            Rect(0, 0, 64, 16),
            Rect(0, 0, 64, 16),
            null
        )
        val headAvg = getAverageColor(head)
        head.recycle()

        // 2. Base 32×32 texture
        val base32 = createBaseTexture(source)

        // 3. Final canvas with optional outline / background
        val finalBase = buildFinalCanvas(base32, options, headAvg)
        base32.recycle()

        // 4. Optional extra scale
        return if (options.scale <= 1f) {
            finalBase
        } else {
            val scaled = applyScale(finalBase, options.scale)
            finalBase.recycle()
            scaled
        }
    }

    // ---------- Core algorithms ----------

    private fun createBaseTexture(source: Bitmap): Bitmap {
        val canvasBmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val ctx = Canvas(canvasBmp)
        val alpha = 76f / 255f // ≈ 0.298

        // Front / side parts
        drawStretch(ctx, source, 56, 8, 8, 8, 10, 7, 18, 18, 0f)
        drawStretch(ctx, source, 48, 8, 8, 8, 4, 7, 6, 18, alpha)
        drawStretch(ctx, source, 24, 8, 8, 8, 11, 8, 16, 16, 0f)
        drawStretch(ctx, source, 16, 8, 8, 8, 5, 8, 6, 16, alpha)

        // Horizontal flip then draw the other side
        val flipped = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val fCanvas = Canvas(flipped)
        val srcPixels = IntArray(32 * 32)
        canvasBmp.getPixels(srcPixels, 0, 32, 0, 0, 32, 32)
        val dstPixels = IntArray(32 * 32)
        for (y in 0 until 32) {
            for (x in 0 until 32) {
                dstPixels[y * 32 + (31 - x)] = srcPixels[y * 32 + x]
            }
        }
        flipped.setPixels(dstPixels, 0, 32, 0, 0, 32, 32)

        drawStretch(fCanvas, source, 8, 8, 8, 8, 11, 8, 16, 16, 0f)
        drawStretch(fCanvas, source, 0, 8, 8, 8, 5, 8, 6, 16, alpha)
        drawStretch(fCanvas, source, 40, 8, 8, 8, 10, 7, 18, 18, 0f)
        drawStretch(fCanvas, source, 32, 8, 8, 8, 4, 7, 6, 18, alpha)

        canvasBmp.recycle()
        return flipped
    }

    private fun buildFinalCanvas(
        base32: Bitmap,
        options: ProcessOptions,
        customAvg: Rgb?
    ): Bitmap {
        val avg = customAvg ?: getAverageColor(base32)
        val finalOutline = resolveOutlineColor(options.outlineColor, avg)
        val finalBg = resolveBgColor(options.bgColor, avg)

        val (finalW, finalH, offsetX, offsetY) = if (options.upscale48) {
            listOf(48, 48, 8, 8)
        } else {
            listOf(32, 32, 0, 0)
        }

        val canvas = Bitmap.createBitmap(finalW, finalH, Bitmap.Config.ARGB_8888)
        val ctx = Canvas(canvas)

        if (options.fillBackground) {
            val paint = Paint().apply { color = Color.parseColor(finalBg) }
            ctx.drawRect(0f, 0f, finalW.toFloat(), finalH.toFloat(), paint)
        }

        // 1:1 draw
        ctx.drawBitmap(base32, offsetX.toFloat(), offsetY.toFloat(), null)

        if (options.outlineMode > 0) {
            applyOutline(ctx, canvas, base32, offsetX, offsetY, options.outlineMode, finalOutline)
        }

        return canvas
    }

    fun applyScale(source: Bitmap, scale: Float): Bitmap {
        val sw = source.width
        val sh = source.height
        val dw = (sw * scale).roundToInt()
        val dh = (sh * scale).roundToInt()
        return drawNearestNeighbor(source, 0, 0, sw, sh, dw, dh, 0f)
    }

    // ---------- Drawing helpers (manual nearest-neighbour) ----------

    private fun drawStretch(
        destCanvas: Canvas,
        src: Bitmap,
        sx: Int, sy: Int, sw: Int, sh: Int,
        dx: Int, dy: Int, dw: Int, dh: Int,
        overlayAlpha: Float
    ) {
        // Fast path: 1:1 no overlay
        if (sw == dw && sh == dh && overlayAlpha <= 0f) {
            destCanvas.drawBitmap(
                src,
                Rect(sx, sy, sx + sw, sy + sh),
                Rect(dx, dy, dx + dw, dy + dh),
                null
            )
            return
        }

        val srcRegion = Bitmap.createBitmap(src, sx, sy, sw, sh)
        val scaled = drawNearestNeighbor(srcRegion, 0, 0, sw, sh, dw, dh, overlayAlpha)
        srcRegion.recycle()

        destCanvas.drawBitmap(scaled, dx.toFloat(), dy.toFloat(), null)
        scaled.recycle()
    }

    /**
     * Exact port of the JS drawNearestNeighbor.
     * Uses floor((px - dx + 0.5) * scale) sampling.
     */
    private fun drawNearestNeighbor(
        src: Bitmap,
        sx: Int, sy: Int, srcW: Int, srcH: Int,
        dw: Int, dh: Int,
        overlayAlpha: Float
    ): Bitmap {
        val dest = Bitmap.createBitmap(dw, dh, Bitmap.Config.ARGB_8888)
        val destPixels = IntArray(dw * dh)
        val srcPixels = IntArray(srcW * srcH)
        src.getPixels(srcPixels, 0, srcW, sx, sy, srcW, srcH)

        val scaleX = srcW.toFloat() / dw
        val scaleY = srcH.toFloat() / dh
        val hasOverlay = overlayAlpha > 0f
        val invAlpha = if (hasOverlay) 1f - overlayAlpha else 1f

        for (py in 0 until dh) {
            val srcY = floor((py + 0.5f) * scaleY).toInt().coerceIn(0, srcH - 1)
            val srcRow = srcY * srcW
            for (px in 0 until dw) {
                val srcX = floor((px + 0.5f) * scaleX).toInt().coerceIn(0, srcW - 1)
                val si = srcRow + srcX
                var pixel = srcPixels[si]
                val a = Color.alpha(pixel)

                if (a == 0) {
                    destPixels[py * dw + px] = 0 // fully transparent
                    continue
                }

                var r = Color.red(pixel)
                var g = Color.green(pixel)
                var b = Color.blue(pixel)

                if (hasOverlay) {
                    r = (r * invAlpha).roundToInt()
                    g = (g * invAlpha).roundToInt()
                    b = (b * invAlpha).roundToInt()
                }

                destPixels[py * dw + px] = Color.argb(a, r, g, b)
            }
        }

        dest.setPixels(destPixels, 0, dw, 0, 0, dw, dh)
        return dest
    }

    private fun applyOutline(
        destCanvas: Canvas,
        destBitmap: Bitmap,
        content: Bitmap,
        offsetX: Int,
        offsetY: Int,
        radius: Int,
        outlineHex: String
    ) {
        val dw = destBitmap.width
        val dh = destBitmap.height
        val pixels = IntArray(dw * dh)
        destBitmap.getPixels(pixels, 0, dw, 0, 0, dw, dh)

        val solid = HashSet<Int>()
        val contentPixels = IntArray(content.width * content.height)
        content.getPixels(contentPixels, 0, content.width, 0, 0, content.width, content.height)

        for (y in 0 until content.height) {
            for (x in 0 until content.width) {
                if (Color.alpha(contentPixels[y * content.width + x]) > 0) {
                    val gx = x + offsetX
                    val gy = y + offsetY
                    if (gx in 0 until dw && gy in 0 until dh) {
                        solid.add(gy * dw + gx)
                    }
                }
            }
        }

        val outline = HashSet<Int>()
        val minX = max(0, offsetX - radius)
        val maxX = min(dw - 1, offsetX + content.width - 1 + radius)
        val minY = max(0, offsetY - radius)
        val maxY = min(dh - 1, offsetY + content.height - 1 + radius)

        for (y in minY..maxY) {
            for (x in minX..maxX) {
                val idx = y * dw + x
                if (solid.contains(idx)) continue

                var found = false
                outer@ for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx
                        val ny = y + dy
                        if (nx < 0 || nx >= dw || ny < 0 || ny >= dh) continue
                        if (solid.contains(ny * dw + nx)) {
                            found = true
                            break@outer
                        }
                    }
                }
                if (found) outline.add(idx)
            }
        }

        val outlineColor = Color.parseColor(outlineHex)
        for (idx in outline) {
            pixels[idx] = outlineColor
        }
        destBitmap.setPixels(pixels, 0, dw, 0, 0, dw, dh)
    }

    // ---------- Colour helpers ----------

    fun getAverageColor(bitmap: Bitmap): Rgb {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var r = 0L
        var g = 0L
        var b = 0L
        var count = 0

        for (p in pixels) {
            if (Color.alpha(p) > 0) {
                r += Color.red(p)
                g += Color.green(p)
                b += Color.blue(p)
                count++
            }
        }

        if (count == 0) return Rgb(128, 128, 128)
        return Rgb(
            (r / count).toInt(),
            (g / count).toInt(),
            (b / count).toInt()
        )
    }

    private val outlineGenerators = mapOf(
        "auto_dark" to { avg: Rgb ->
            Rgb(
                min(80, (avg.r * 0.25).roundToInt()),
                min(80, (avg.g * 0.25).roundToInt()),
                min(80, (avg.b * 0.25).roundToInt())
            )
        },
        "auto_darker" to { avg: Rgb ->
            Rgb(
                min(50, (avg.r * 0.15).roundToInt()),
                min(50, (avg.g * 0.15).roundToInt()),
                min(50, (avg.b * 0.15).roundToInt())
            )
        },
        "auto_medium_dark" to { avg: Rgb ->
            Rgb(
                min(120, (avg.r * 0.4).roundToInt()),
                min(120, (avg.g * 0.4).roundToInt()),
                min(120, (avg.b * 0.4).roundToInt())
            )
        }
    )

    private val bgGenerators = mapOf(
        "auto_light" to { avg: Rgb ->
            Rgb(
                min(230, (avg.r * 1.2 + 10).roundToInt()),
                min(230, (avg.g * 1.2 + 10).roundToInt()),
                min(230, (avg.b * 1.2 + 10).roundToInt())
            )
        },
        "auto_lighter" to { avg: Rgb ->
            Rgb(
                min(250, (avg.r * 1.5 + 30).roundToInt()),
                min(250, (avg.g * 1.5 + 30).roundToInt()),
                min(250, (avg.b * 1.5 + 30).roundToInt())
            )
        },
        "auto_medium_light" to { avg: Rgb ->
            Rgb(
                min(200, (avg.r * 0.9 + 30).roundToInt()),
                min(200, (avg.g * 0.9 + 30).roundToInt()),
                min(200, (avg.b * 0.9 + 30).roundToInt())
            )
        }
    )

    private fun rgbToHex(r: Int, g: Int, b: Int): String =
        String.format("#%02x%02x%02x", r, g, b)

    private fun resolveOutlineColor(presetOrHex: String, avg: Rgb): String {
        if (presetOrHex.startsWith("auto_")) {
            val gen = outlineGenerators[presetOrHex]
            if (gen != null) {
                val c = gen(avg)
                return rgbToHex(c.r, c.g, c.b)
            }
            return "#000000"
        }
        return presetOrHex.ifBlank { "#000000" }
    }

    private fun resolveBgColor(presetOrHex: String, avg: Rgb): String {
        if (presetOrHex.startsWith("auto_")) {
            val gen = bgGenerators[presetOrHex]
            if (gen != null) {
                val c = gen(avg)
                return rgbToHex(c.r, c.g, c.b)
            }
            return "#ffffff"
        }
        return presetOrHex.ifBlank { "#ffffff" }
    }
}