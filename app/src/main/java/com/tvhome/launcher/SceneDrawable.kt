package com.tvhome.launcher

import android.graphics.*
import android.graphics.drawable.Drawable
import java.util.Random

/** Draws a dusk mountain lake (sky, peak, pine forest, water reflection) with no image files. */
class SceneDrawable : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun scene(c: Canvas, w: Float, hz: Float) {
        p.shader = LinearGradient(
            0f, 0f, 0f, hz,
            intArrayOf(0xFF23284A.toInt(), 0xFF6B5A7E.toInt(), 0xFFD59A8E.toInt()),
            floatArrayOf(0f, 0.65f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, hz, p)
        p.shader = null

        // mountain
        p.color = 0xFF2A2C3F.toInt()
        val m = Path().apply {
            moveTo(w * .26f, hz)
            lineTo(w * .40f, hz * .74f)
            lineTo(w * .45f, hz * .71f)
            lineTo(w * .50f, hz * .58f)
            lineTo(w * .54f, hz * .67f)
            lineTo(w * .60f, hz * .74f)
            lineTo(w * .74f, hz)
            close()
        }
        c.drawPath(m, p)

        // pine forest, taller toward the edges
        p.color = 0xFF10141E.toInt()
        val rnd = Random(7)
        val n = 70
        for (i in 0..n) {
            val x = w * i / n
            val edge = Math.abs(x / w - 0.5f) * 2f
            val th = hz * (0.07f + 0.34f * edge * edge + rnd.nextFloat() * 0.06f)
            val tw = w / 34f
            val t = Path().apply {
                moveTo(x - tw / 2, hz)
                lineTo(x, hz - th)
                lineTo(x + tw / 2, hz)
                close()
            }
            c.drawPath(t, p)
        }
        c.drawRect(0f, hz - hz * 0.03f, w, hz, p)
    }

    override fun draw(c: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        val hz = h * 0.62f

        scene(c, w, hz)

        // water = mirrored scene, darkened
        c.save()
        c.clipRect(0f, hz, w, h)
        c.translate(0f, 2 * hz)
        c.scale(1f, -1f)
        scene(c, w, hz)
        c.restore()
        p.color = 0x88101420.toInt()
        c.drawRect(0f, hz, w, h, p)

        // bottom scrim so the dock/cards stay readable
        p.shader = LinearGradient(
            0f, h * 0.55f, 0f, h,
            0x00000000, 0xAA000000.toInt(), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, h * 0.55f, w, h, p)
        p.shader = null
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.OPAQUE
}
