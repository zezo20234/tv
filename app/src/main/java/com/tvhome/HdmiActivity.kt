package com.tvhome

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.media.tv.TvView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Full-screen HDMI viewer using the TV's own input service.
 * If the first guess doesn't work it tries the others; Left/Right switches input by hand
 * and OK remembers the working one for this HDMI port.
 */
class HdmiActivity : Activity() {

    private lateinit var tvView: TvView
    private lateinit var msg: TextView
    private lateinit var hint: TextView
    private lateinit var title: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val cands = ArrayList<String>()
    private var idx = 0
    private var n = 1
    private var name = "HDMI"
    private var px = 1f
    private val hideChrome = Runnable {
        title.visibility = ViewGroup.INVISIBLE
        hint.visibility = ViewGroup.INVISIBLE
    }

    private fun hw(id: String): Int =
        Regex("HW([0-9]+)$").find(id)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE

    private fun label(text: String, sizeU: Float, bold: Boolean = true) = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_PX, sizeU * px)
        setTextColor(Color.WHITE)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val first = intent.getStringExtra("input_id") ?: ""
        name = intent.getStringExtra("name") ?: "HDMI"
        n = intent.getIntExtra("n", 1)
        px = resources.displayMetrics.widthPixels / 1000f

        // candidate inputs: the guess first, then everything the TV reports, then MediaTek defaults
        if (first.isNotEmpty()) cands.add(first)
        try {
            val tim = getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
            tim?.tvInputList.orEmpty()
                .filter { it.type == TvInputInfo.TYPE_HDMI || it.isPassthroughInput }
                .sortedBy { hw(it.id) }
                .forEach { if (!cands.contains(it.id)) cands.add(it.id) }
        } catch (e: Exception) { }
        for (k in 1..8) {
            val g = "com.mediatek.tvinput/.hdmi.HDMIInputService/HW$k"
            if (!cands.contains(g)) cands.add(g)
        }

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        tvView = TvView(this)
        root.addView(tvView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        msg = label("", 28f).apply {
            gravity = Gravity.CENTER
            setPadding((30 * px).toInt(), (20 * px).toInt(), (30 * px).toInt(), (20 * px).toInt())
            background = GradientDrawable().apply { cornerRadius = 24f * px; setColor(0xB3000000.toInt()) }
            visibility = ViewGroup.INVISIBLE
        }
        root.addView(msg, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        title = label("", 22f).apply {
            setPadding((40 * px).toInt(), (28 * px).toInt(), 0, 0)
            setShadowLayer(6f, 0f, 2f, Color.BLACK)
        }
        root.addView(title, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START))

        hint = label("Left / Right: try another input      OK: save for $name      Back: return", 17f, false).apply {
            setPadding((24 * px).toInt(), (12 * px).toInt(), (24 * px).toInt(), (12 * px).toInt())
            background = GradientDrawable().apply { cornerRadius = 30f * px; setColor(0x99000000.toInt()) }
        }
        root.addView(hint, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = (36 * px).toInt()
        })
        setContentView(root)

        tvView.setCallback(object : TvView.TvInputCallback() {
            override fun onConnectionFailed(id: String?) { handler.post { if (id == cands[idx]) onFail() } }
            override fun onDisconnected(id: String?) { handler.post { show("Input disconnected") } }
            override fun onVideoAvailable(id: String?) { handler.post { msg.visibility = ViewGroup.INVISIBLE } }
            override fun onVideoUnavailable(id: String?, reason: Int) {
                handler.post {
                    if (reason == TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING ||
                        reason == TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING) show("Loading…")
                    else show("No signal on this input")
                }
            }
        })
        tune()
    }

    private fun showChrome() {
        title.visibility = ViewGroup.VISIBLE
        hint.visibility = ViewGroup.VISIBLE
        handler.removeCallbacks(hideChrome)
        handler.postDelayed(hideChrome, 7000L)
    }

    private fun show(text: String) {
        msg.text = text
        msg.visibility = ViewGroup.VISIBLE
        showChrome()
    }

    private fun tune() {
        if (cands.isEmpty()) { show("No inputs found"); return }
        val id = cands[idx]
        msg.visibility = ViewGroup.INVISIBLE
        title.text = "$name   ·   input ${idx + 1} of ${cands.size}:  ${id.substringAfterLast('/')}"
        showChrome()
        try {
            tvView.reset()
            tvView.tune(id, TvContract.buildChannelUriForPassthroughInput(id))
        } catch (e: Exception) {
            onFail()
        }
    }

    private fun onFail() {
        if (idx < cands.size - 1) {
            idx++
            tune()
        } else {
            Toast.makeText(this, "Couldn't open any input. Opening the Input Finder…", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, FinderActivity::class.java))
            finish()
        }
    }

    private fun diag(): String {
        val sb = StringBuilder("TV inputs visible to apps: ")
        try {
            val list = (getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager)?.tvInputList.orEmpty()
            sb.append(list.size).append('\n')
            list.take(8).forEach { sb.append(it.type).append("   ").append(it.id).append('\n') }
        } catch (e: Exception) {
            sb.append("error: ").append(e.javaClass.simpleName)
        }
        return sb.toString()
    }

    private fun save() {
        if (cands.isEmpty()) return
        getSharedPreferences("tvhome", MODE_PRIVATE).edit().putString("map_$n", cands[idx]).apply()
        Toast.makeText(this, "Saved: $name will open this input", Toast.LENGTH_LONG).show()
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0 && cands.isNotEmpty()) {
            when (e.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { idx = (idx - 1 + cands.size) % cands.size; tune(); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { idx = (idx + 1) % cands.size; tune(); return true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { save(); return true }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> { showChrome(); return true }
            }
        }
        return super.dispatchKeyEvent(e)
    }

    override fun onStop() {
        try { tvView.reset() } catch (e: Exception) { }
        super.onStop()
        finish()
    }
}
