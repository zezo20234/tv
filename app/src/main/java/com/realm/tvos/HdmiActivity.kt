package com.realm.tvos

import android.app.Activity
import android.graphics.Color
import android.media.tv.TvContentRating
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.media.tv.TvView
import android.media.tv.TvView.TvInputCallback
import android.os.Bundle
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.TextView

class HdmiActivity : Activity() {

    private lateinit var tvView: TvView
    private lateinit var titleText: TextView
    private var inputId: String? = null
    private var inputName: String? = null
    private var inputNumber: Int = 0
    private var tim: TvInputManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        inputId = intent.getStringExtra("input_id")
        inputName = intent.getStringExtra("name")
        inputNumber = intent.getIntExtra("n", 0)
        tim = getSystemService(android.content.Context.TV_INPUT_SERVICE) as? TvInputManager

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(40, 20, 40, 20)
            background = createGlassBackground()
        }

        titleText = TextView(this).apply {
            text = inputName ?: "HDMI Input"
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
        }
        header.addView(titleText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val exitHint = TextView(this).apply {
            text = "Press BACK to exit"
            textSize = 16f
            setTextColor(0x99FFFFFF.toInt())
        }
        header.addView(exitHint, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        root.addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        tvView = TvView(this)
        root.addView(tvView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)

        if (inputId != null) {
            try {
                tvView.setCallback(object : TvInputCallback {
                    override fun onChannelRetuned(inputId: String, channelNumber: String) {}
                    override fun onVideoAvailable(inputId: String) {}
                    override fun onVideoUnavailable(inputId: String, reason: Int) {}
                    override fun onContentAllowed(inputId: String) {}
                    override fun onContentBlocked(inputId: String, rating: TvContentRating) {}
                    override fun onTrackSelected(inputId: String, type: Int, trackId: String) {}
                })
                tvView.tune(inputId!!, null)
            } catch (e: Exception) {
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            tvView.reset()
        } catch (e: Exception) { }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun createGlassBackground(): android.graphics.drawable.Drawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(0xE01C1C24.toInt())
            cornerRadius = 24f
        }
    }
}
