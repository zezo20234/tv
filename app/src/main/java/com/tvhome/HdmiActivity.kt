package com.tvhome

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.media.tv.TvContract
import android.media.tv.TvView
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView

/** Full-screen HDMI viewer using the TV's own input service (no "press INPUT" needed). */
class HdmiActivity : Activity() {

    private lateinit var tvView: TvView
    private lateinit var msg: TextView
    private lateinit var title: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var name = "HDMI"
    private var inputId = ""
    private var triedFallback = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inputId = intent.getStringExtra("input_id") ?: ""
        name = intent.getStringExtra("name") ?: "HDMI"
        if (inputId.isEmpty()) { finish(); return }

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        tvView = TvView(this)
        root.addView(tvView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val px = resources.displayMetrics.widthPixels / 1000f
        msg = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 30f * px)
            setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; visibility = ViewGroup.INVISIBLE
        }
        root.addView(msg, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        title = TextView(this).apply {
            text = name
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 26f * px)
            setTextColor(0xE6FFFFFF.toInt()); typeface = Typeface.DEFAULT_BOLD
            setPadding((40 * px).toInt(), (30 * px).toInt(), 0, 0)
        }
        root.addView(title, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START))
        setContentView(root)

        tvView.setCallback(object : TvView.TvInputCallback() {
            override fun onConnectionFailed(id: String?) { fallback() }
            override fun onDisconnected(id: String?) { show("Input disconnected") }
            override fun onVideoAvailable(id: String?) { msg.visibility = ViewGroup.INVISIBLE }
            override fun onVideoUnavailable(id: String?, reason: Int) { show("No signal on $name") }
        })
        try {
            tvView.tune(inputId, TvContract.buildChannelUriForPassthroughInput(inputId))
        } catch (e: Exception) {
            fallback()
        }
        handler.postDelayed({ title.visibility = ViewGroup.INVISIBLE }, 3500L)
    }

    private fun show(text: String) {
        msg.text = "$text\n\nPress Back to return"
        msg.visibility = ViewGroup.VISIBLE
    }

    /** Last resort: ask the TV's own app to open the input. */
    private fun fallback() {
        if (triedFallback) return
        triedFallback = true
        val attempts = ArrayList<Intent>()
        attempts.add(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(inputId)))
        attempts.add(Intent(Intent.ACTION_VIEW, Uri.parse(
            "content://android.media.tv/passthrough/" + Uri.encode(inputId))))
        for (pkg in listOf("com.mediatek.wwtv.tvcenter", "com.android.tv", "com.google.android.tv")) {
            packageManager.getLaunchIntentForPackage(pkg)?.let { attempts.add(it) }
        }
        for (i in attempts) {
            try {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
                finish()
                return
            } catch (e: Exception) { /* next */ }
        }
        show("Couldn't open $name")
    }

    override fun onStop() {
        try { tvView.reset() } catch (e: Exception) { }
        super.onStop()
        finish()
    }
}
