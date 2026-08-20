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
        val scale: Float = 1f,
        /** Optional override for auto outline/bg colour derivation. Null = compute from base texture without side shade. */
        val averageColor: Rgb? = null
    )

    data class Rgb(val r: Int, val g: Int, val b: Int)

    // ---------- Public entry ----------

    fun processTexture(source: Bitmap, options: ProcessOptions = ProcessOptions()): Bitmap {
        require(source.width >= 64 && source.height >= 16) {
            "Image too small: ${source.width}×${source.height}, need at least 64×64"
        }

        // Average colour for auto outline/bg:
        // prefer user override; otherwise sample from base texture WITHOUT side shade (matches JS)
        val headAvg = options.averageColor ?: run {
            val colorBase = createBaseTexture(source, applySideShade = false)
            val avg = getAverageColor(colorBase)
            colorBase.recycle()
            avg
        }

        // Base 32×32 with side shade for actual output
        val base32 = createBaseTexture(source, applySideShade = true)

        val finalBase = buildFinalCanvas(base32, options, headAvg)
        base32.recycle()

        return if (options.scale <= 1f) {
            finalBase
        } else {
            val scaled = applyScale(finalBase, options.scale)
            finalBase.recycle()
            scaled
        }
    }

    // ---------- Core algorithms ----------

    private fun createBaseTexture(source: Bitmap, applySideShade: Boolean = true): Bitmap {
        val canvasBmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val ctx = Canvas(canvasBmp)
        val alpha = if (applySideShade) 76f / 255f else 0f // ≈ 0.298 when shaded

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
        val dw = (sw * scale).roundToInt().coerceAtLeast(1)
        val dh = (sh * scale).roundToInt().coerceAtLeast(1)
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

    private fun drawNearestNeighbor(
        src: Bitmap,
        sx: Int, sy: Int, srcW: Int, srcH: Int,
        dw: Int, dh: Int,
        overlayAlpha: Float
    ): Bitmap {
        val out = Bitmap.createBitmap(dw, dh, Bitmap.Config.ARGB_8888)
        val scaleX = srcW.toFloat() / dw
        val scaleY = srcH.toFloat() / dh
        val hasOverlay = overlayAlpha > 0f
        val invAlpha = if (hasOverlay) 1f - overlayAlpha else 1f

        for (py in 0 until dh) {
            val srcY = floor((py + 0.5f) * scaleY).toInt().coerceIn(0, srcH - 1)
            for (px in 0 until dw) {
                val srcX = floor((px + 0.5f) * scaleX).toInt().coerceIn(0, srcW - 1)
                val c = src.getPixel(sx + srcX, sy + srcY)
                val a = (c ushr 24) and 0xFF
                if (a == 0) continue

                var r = (c shr 16) and 0xFF
                var g = (c shr 8) and 0xFF
                var b = c and 0xFF
                if (hasOverlay) {
                    r = (r * invAlpha).roundToInt()
                    g = (g * invAlpha).roundToInt()
                    b = (b * invAlpha).roundToInt()
                }
                out.setPixel(px, py, Color.argb(a, r, g, b))
            }
        }
        return out
    }

    private fun applyOutline(
        destCanvas: Canvas,
        destBitmap: Bitmap,
        content: Bitmap,
        offsetX: Int,
        offsetY: Int,
        outlineRadius: Int,
        outlineColorHex: String
    ) {
        val dw = destBitmap.width
        val dh = destBitmap.height
        val pixels = IntArray(dw * dh)
        destBitmap.getPixels(pixels, 0, dw, 0, 0, dw, dh)

        val solid = BooleanArray(dw * dh)
        val cw = content.width
        val ch = content.height
        val contentPixels = IntArray(cw * ch)
        content.getPixels(contentPixels, 0, cw, 0, 0, cw, ch)

        for (y in 0 until ch) {
            for (x in 0 until cw) {
                if (((contentPixels[y * cw + x] ushr 24) and 0xFF) > 0) {
                    val gx = x + offsetX
                    val gy = y + offsetY
                    if (gx in 0 until dw && gy in 0 until dh) {
                        solid[gy * dw + gx] = true
                    }
                }
            }
        }

        val outlineColor = Color.parseColor(outlineColorHex) or (0xFF shl 24)
        val minX = max(0, offsetX - outlineRadius)
        val maxX = min(dw - 1, offsetX + cw - 1 + outlineRadius)
        val minY = max(0, offsetY - outlineRadius)
        val maxY = min(dh - 1, offsetY + ch - 1 + outlineRadius)

        for (y in minY..maxY) {
            for (x in minX..maxX) {
                val idx = y * dw + x
                if (solid[idx]) continue
                var found = false
                for (dy in -outlineRadius..outlineRadius) {
                    for (dx in -outlineRadius..outlineRadius) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx
                        val ny = y + dy
                        if (nx !in 0 until dw || ny !in 0 until dh) continue
                        if (solid[ny * dw + nx]) {
                            found = true
                            break
                        }
                    }
                    if (found) break
                }
                if (found) pixels[idx] = outlineColor
            }
        }
        destBitmap.setPixels(pixels, 0, dw, 0, 0, dw, dh)
    }

    fun parseHexToRgb(hex: String): Rgb? {
        val h = hex.trim().removePrefix("#")
        if (h.length != 6) return null
        return try {
            Rgb(
                h.substring(0, 2).toInt(16),
                h.substring(2, 4).toInt(16),
                h.substring(4, 6).toInt(16)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun getAverageColor(bitmap: Bitmap): Rgb {
        var rSum = 0L; var gSum = 0L; var bSum = 0L; var count = 0
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        for (c in pixels) {
            val a = (c ushr 24) and 0xFF
            if (a == 0) continue
            rSum += (c shr 16) and 0xFF
            gSum += (c shr 8) and 0xFF
            bSum += c and 0xFF
            count++
        }
        if (count == 0) return Rgb(128, 128, 128)
        return Rgb((rSum / count).toInt(), (gSum / count).toInt(), (bSum / count).toInt())
    }

    private val outlineGenerators = mapOf(
        "auto" to { avg: Rgb ->
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
        "auto_lighter" to { avg: Rgb ->
            Rgb(
                min(120, (avg.r * 0.4).roundToInt()),
                min(120, (avg.g * 0.4).roundToInt()),
                min(120, (avg.b * 0.4).roundToInt())
            )
        }
    )

    private val bgGenerators = mapOf(
        "auto" to { avg: Rgb ->
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
        "auto_darker" to { avg: Rgb ->
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
        if (presetOrHex.startsWith("auto")) {
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
        if (presetOrHex.startsWith("auto")) {
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
