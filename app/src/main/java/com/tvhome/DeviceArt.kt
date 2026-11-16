package com.tvhome

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import kotlin.math.max
import kotlin.math.min

/** PlayStation 5 standing up: white side plates, black core, soft blue glow. */
class Ps5ConsoleView(ctx: Context) : View(ctx) {
    private val plate = Paint(Paint.ANTI_ALIAS_FLAG)
    private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0A0C12.toInt() }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x40000000 }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val led = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF5CB0FF.toInt() }
    private val ground = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55000000 }
    private val stand = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF14171F.toInt() }
    private val r = RectF()

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        val rad = max(1f, min(w, h) * 0.5f)
        glow.shader = RadialGradient(w / 2f, h / 2f, rad, 0x555CB0FF, 0x005CB0FF, Shader.TileMode.CLAMP)
        plate.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFFFFFFFF.toInt(), 0xFFC4CCDB.toInt(), Shader.TileMode.CLAMP)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        c.drawCircle(w / 2f, h / 2f, max(1f, min(w, h) * 0.5f), glow)

        val ch = h * 0.84f
        val cw = ch * 0.40f
        val left = (w - cw) / 2f
        val top = h * 0.06f
        val bottom = top + ch
        edge.strokeWidth = max(1f, cw * 0.012f)

        // ground shadow and stand
        r.set(w / 2f - cw * 0.85f, bottom - ch * 0.02f, w / 2f + cw * 0.85f, bottom + ch * 0.05f)
        c.drawOval(r, ground)
        r.set(left + cw * 0.05f, bottom - ch * 0.012f, left + cw * 0.95f, bottom + ch * 0.028f)
        c.drawRoundRect(r, cw * 0.05f, cw * 0.05f, stand)

        // black core
        r.set(left + cw * 0.2f, top + ch * 0.02f, left + cw * 0.8f, bottom - ch * 0.01f)
        c.drawRoundRect(r, cw * 0.1f, cw * 0.1f, core)

        // white plates
        val pr = cw * 0.22f
        r.set(left, top, left + cw * 0.44f, bottom)
        c.drawRoundRect(r, pr, pr, plate)
        c.drawRoundRect(r, pr, pr, edge)
        r.set(left + cw * 0.56f, top, left + cw, bottom)
        c.drawRoundRect(r, pr, pr, plate)
        c.drawRoundRect(r, pr, pr, edge)

        // blue light line in the slot between the plates
        r.set(left + cw * 0.485f, top + ch * 0.18f, left + cw * 0.515f, bottom - ch * 0.12f)
        c.drawRoundRect(r, cw * 0.015f, cw * 0.015f, led)
    }
}

/** A small TV box: dark body with a lit screen showing a play button. */
class TvBoxView(ctx: Context) : View(ctx) {
    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x66FFFFFF }
    private val screen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val play = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6FFFFFF.toInt() }
    private val led = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF34D399.toInt() }
    private val slot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x44FFFFFF }
    private val foot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0C0E13.toInt() }
    private val ground = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55000000 }
    private val r = RectF()
    private val tri = Path()
    private var bw = 0f
    private var bh = 0f
    private var bl = 0f
    private var bt = 0f

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        bw = min(w * 0.92f, h * 1.9f)
        bh = bw * 0.46f
        bl = (w - bw) / 2f
        bt = (h - bh) / 2f - bh * 0.04f
        body.shader = LinearGradient(0f, bt, 0f, bt + bh, 0xFF4A5060.toInt(), 0xFF1B1E26.toInt(), Shader.TileMode.CLAMP)
        screen.shader = LinearGradient(0f, bt, 0f, bt + bh * 0.66f, 0xFF2D8CFF.toInt(), 0xFF0B2E6B.toInt(), Shader.TileMode.CLAMP)
        rim.strokeWidth = max(1f, bh * 0.02f)
    }

    override fun onDraw(c: Canvas) {
        if (bw <= 0f) return
        val w = width.toFloat()
        val rad = bh * 0.2f
        val bottom = bt + bh

        // ground shadow and feet
        r.set(w / 2f - bw * 0.5f, bottom + bh * 0.02f, w / 2f + bw * 0.5f, bottom + bh * 0.16f)
        c.drawOval(r, ground)
        r.set(bl + bw * 0.12f, bottom - bh * 0.01f, bl + bw * 0.2f, bottom + bh * 0.07f)
        c.drawRoundRect(r, bh * 0.02f, bh * 0.02f, foot)
        r.set(bl + bw * 0.8f, bottom - bh * 0.01f, bl + bw * 0.88f, bottom + bh * 0.07f)
        c.drawRoundRect(r, bh * 0.02f, bh * 0.02f, foot)

        // body
        r.set(bl, bt, bl + bw, bottom)
        c.drawRoundRect(r, rad, rad, body)
        c.drawRoundRect(r, rad, rad, rim)

        // screen
        val m = bh * 0.12f
        val sTop = bt + m
        val sBottom = bt + bh * 0.66f
        r.set(bl + m, sTop, bl + bw - m, sBottom)
        c.drawRoundRect(r, bh * 0.1f, bh * 0.1f, screen)

        // play button
        val cx = bl + bw / 2f
        val cy = (sTop + sBottom) / 2f
        val s = (sBottom - sTop) * 0.5f
        tri.reset()
        tri.moveTo(cx - s * 0.35f, cy - s * 0.5f)
        tri.lineTo(cx - s * 0.35f, cy + s * 0.5f)
        tri.lineTo(cx + s * 0.55f, cy)
        tri.close()
        c.drawPath(tri, play)

        // status light and slot
        c.drawCircle(bl + bw * 0.12f, bt + bh * 0.83f, bh * 0.04f, led)
        r.set(bl + bw * 0.22f, bt + bh * 0.815f, bl + bw * 0.5f, bt + bh * 0.845f)
        c.drawRoundRect(r, bh * 0.015f, bh * 0.015f, slot)
    }
}
