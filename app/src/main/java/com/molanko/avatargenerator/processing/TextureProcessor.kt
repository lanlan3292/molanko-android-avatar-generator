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
        val outlineMode: Int = 0,
        val outlineColor: String = "#000000",
        val bgColor: String = "#ffffff",
        val upscale48: Boolean = false,
        val fillBackground: Boolean = true,
        val scale: Float = 1f
    )

    data class Rgb(val r: Int, val g: Int, val b: Int)

    fun processTexture(source: Bitmap, options: ProcessOptions = ProcessOptions()): Bitmap {
        require(source.width >= 64 && source.height >= 16) {
            "Image too small: ${source.width}×${source.height}, need at least 64×64"
        }

        val head = Bitmap.createBitmap(64, 16, Bitmap.Config.ARGB_8888)
        Canvas(head).drawBitmap(
            source,
            Rect(0, 0, 64, 16),
            Rect(0, 0, 64, 16),
            null
        )
        val headAvg = getAverageColor(head)
        head.recycle()

        val base32 = createBaseTexture(source)

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

    private fun createBaseTexture(source: Bitmap): Bitmap {
        val canvasBmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val ctx = Canvas(canvasBmp)
        val alpha = 76f / 255f

        drawStretch(ctx, source, 56, 8, 8, 8, 10, 7, 18, 18, 0f)
        drawStretch(ctx, source, 48, 8, 8, 8, 4, 7, 18, 18, 0f)
        drawStretch(ctx, source, 8, 8, 8, 8, 7, 10, 18, 18, 0f)
        drawStretch(ctx, source, 40, 8, 8, 8, 7, 4, 18, 18, alpha)
        drawStretch(ctx, source, 16, 8, 8, 8, 10, 10, 12, 12, 0f)
        drawStretch(ctx, source, 24, 8, 8, 8, 10, 4, 12, 12, alpha)

        return canvasBmp
    }

    private fun buildFinalCanvas(base32: Bitmap, options: ProcessOptions, headAvg: Rgb): Bitmap {
        val (finalW, finalH) = if (options.upscale48) 48 to 48 else 32 to 32
        val offset = if (options.upscale48) 8 else 0

        val out = Bitmap.createBitmap(finalW, finalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        if (options.fillBackground) {
            val bgHex = resolveBgColor(options.bgColor, headAvg)
            canvas.drawColor(Color.parseColor(bgHex))
        }

        if (options.outlineMode > 0) {
            val outlineHex = resolveOutlineColor(options.outlineColor, headAvg)
            val outlineColor = Color.parseColor(outlineHex)
            val paint = Paint().apply { color = outlineColor; isAntiAlias = false }
            val r = options.outlineMode
            for (dy in -r..r) {
                for (dx in -r..r) {
                    if (dx == 0 && dy == 0) continue
                    if (dx * dx + dy * dy > r * r) continue
                    canvas.drawBitmap(base32, (offset + dx).toFloat(), (offset + dy).toFloat(), paint)
                }
            }
        }

        canvas.drawBitmap(base32, offset.toFloat(), offset.toFloat(), null)
        return out
    }

    /** Nearest-neighbor final scale (used when saving). */
    fun applyScale(source: Bitmap, scale: Float): Bitmap {
        val sw = source.width
        val sh = source.height
        val dw = (sw * scale).roundToInt().coerceAtLeast(1)
        val dh = (sh * scale).roundToInt().coerceAtLeast(1)
        return drawNearestNeighbor(source, 0, 0, sw, sh, dw, dh, 0f)
    }

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
        for (py in 0 until dh) {
            val srcY = floor((py + 0.5f) * scaleY).toInt().coerceIn(0, srcH - 1)
            for (px in 0 until dw) {
                val srcX = floor((px + 0.5f) * scaleX).toInt().coerceIn(0, srcW - 1)
                var c = src.getPixel(sx + srcX, sy + srcY)
                if (overlayAlpha > 0f) {
                    val a = ((c ushr 24) and 0xFF) / 255f * (1f - overlayAlpha)
                    c = (c and 0x00FFFFFF) or ((a * 255).toInt().coerceIn(0, 255) shl 24)
                }
                out.setPixel(px, py, c)
            }
        }
        return out
    }

    private fun getAverageColor(bmp: Bitmap): Rgb {
        var rSum = 0L; var gSum = 0L; var bSum = 0L; var count = 0
        val w = bmp.width
        val h = bmp.height
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = bmp.getPixel(x, y)
                val a = (c ushr 24) and 0xFF
                if (a < 128) continue
                rSum += (c shr 16) and 0xFF
                gSum += (c shr 8) and 0xFF
                bSum += c and 0xFF
                count++
            }
        }
        if (count == 0) return Rgb(128, 128, 128)
        return Rgb((rSum / count).toInt(), (gSum / count).toInt(), (bSum / count).toInt())
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
