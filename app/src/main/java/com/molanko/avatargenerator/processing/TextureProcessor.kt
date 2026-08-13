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

        val headRgb = sampleHeadColor(source)
        val resolvedOutline = resolveColor(options.outlineColor, headRgb, dark = true)
        val resolvedBg = resolveColor(options.bgColor, headRgb, dark = false)

        val base = renderHead(source, options, resolvedOutline, resolvedBg)

        return if (options.scale <= 1f) {
            base
        } else {
            val scaled = applyScale(base, options.scale)
            if (scaled !== base) base.recycle()
            scaled
        }
    }

    private fun sampleHeadColor(source: Bitmap): Rgb {
        // Sample a few opaque pixels from the head region (8,8)-(16,16) face area heuristic
        var rSum = 0L; var gSum = 0L; var bSum = 0L; var count = 0
        val w = source.width
        val h = source.height
        val x0 = (w * 8 / 64).coerceAtLeast(0)
        val y0 = (h * 8 / 64).coerceAtLeast(0)
        val x1 = (w * 16 / 64).coerceAtMost(w)
        val y1 = (h * 16 / 64).coerceAtMost(h)
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                val c = source.getPixel(x, y)
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

    private fun resolveColor(spec: String, head: Rgb, dark: Boolean): Int {
        val s = spec.trim().lowercase()
        when {
            s.startsWith("#") || s.all { it in '0'..'9' || it in 'a'..'f' } -> {
                return try {
                    Color.parseColor(if (s.startsWith("#")) s else "#$s")
                } catch (_: Exception) {
                    if (dark) Color.BLACK else Color.WHITE
                }
            }
            s.contains("darker") -> return shade(head, if (dark) 0.35f else 1.35f)
            s.contains("medium") -> return shade(head, if (dark) 0.55f else 1.2f)
            s.contains("dark") -> return shade(head, if (dark) 0.45f else 1.25f)
            s.contains("lighter") -> return shade(head, if (dark) 0.7f else 1.5f)
            s.contains("light") -> return shade(head, if (dark) 0.6f else 1.4f)
            else -> return if (dark) Color.BLACK else Color.WHITE
        }
    }

    private fun shade(rgb: Rgb, factor: Float): Int {
        fun ch(v: Int) = (v * factor).roundToInt().coerceIn(0, 255)
        return Color.rgb(ch(rgb.r), ch(rgb.g), ch(rgb.b))
    }

    private fun renderHead(
        source: Bitmap,
        options: ProcessOptions,
        outlineColor: Int,
        bgColor: Int
    ): Bitmap {
        val (finalW, finalH, offsetX, offsetY) = if (options.upscale48) {
            listOf(48, 48, 8, 8)
        } else {
            listOf(32, 32, 0, 0)
        }

        val out = Bitmap.createBitmap(finalW, finalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        if (options.fillBackground) {
            canvas.drawColor(bgColor)
        } else {
            canvas.drawColor(Color.TRANSPARENT)
        }

        // Head base 8x8 from (8,8), hat overlay from (40,8)
        val scale = if (options.upscale48) 4 else 4 // 8 -> 32
        val destSize = 8 * 4 // always draw 32x32 head into canvas

        // Draw base head
        drawStretch(canvas, source, 8, 8, 8, 8, offsetX, offsetY, destSize, destSize, 0f)
        // Hat layer
        drawStretch(canvas, source, 40, 8, 8, 8, offsetX, offsetY, destSize, destSize, 0f)

        if (options.outlineMode > 0) {
            applyOutline(out, options.outlineMode, outlineColor)
        }
        return out
    }

    private fun applyOutline(bmp: Bitmap, radius: Int, color: Int) {
        // Simple outline: for transparent-ish edge neighbors of opaque pixels
        val w = bmp.width
        val h = bmp.height
        val src = IntArray(w * h)
        bmp.getPixels(src, 0, w, 0, 0, w, h)
        val dst = src.copyOf()
        val opaque = BooleanArray(w * h) { ((src[it] ushr 24) and 0xFF) >= 128 }
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                if (opaque[i]) continue
                var near = false
                for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx
                        val ny = y + dy
                        if (nx !in 0 until w || ny !in 0 until h) continue
                        if (opaque[ny * w + nx]) {
                            near = true
                            break
                        }
                    }
                    if (near) break
                }
                if (near) dst[i] = color or (0xFF shl 24)
            }
        }
        bmp.setPixels(dst, 0, w, 0, 0, w, h)
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
                out.setPixel(px, py, src.getPixel(sx + srcX, sy + srcY))
            }
        }
        return out
    }
}
