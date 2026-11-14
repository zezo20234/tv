package com.tvhome

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var appsRow: LinearLayout
    private lateinit var inputsRow: LinearLayout
    private var tim: TvInputManager? = null
    private val handler = Handler(Looper.getMainLooper())

    private val callback = object : TvInputManager.TvInputCallback() {
        override fun onInputStateChanged(inputId: String, state: Int) = refreshInputs()
        override fun onInputAdded(inputId: String) = refreshInputs()
        override fun onInputRemoved(inputId: String) = refreshInputs()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tim = getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager

        val root = FrameLayout(this)
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF1D2B53.toInt(), 0xFF0A0A12.toInt(), 0xFF2B1A3F.toInt())
        )

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(64), dp(36), dp(64), dp(24))
        }

        // Top bar: title + clock
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply {
            text = "Home"; textSize = 34f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(TextClock(this).apply {
            format12Hour = "h:mm a"; format24Hour = "HH:mm"
            textSize = 26f; setTextColor(0xCCFFFFFF.toInt())
        })
        col.addView(top)

        col.addView(sectionLabel("Apps"))
        appsRow = LinearLayout(this)
        col.addView(scroller(appsRow))

        col.addView(sectionLabel("Inputs"))
        inputsRow = LinearLayout(this)
        col.addView(scroller(inputsRow))

        root.addView(col)
        setContentView(root)
    }

    override fun onStart() {
        super.onStart()
        try { tim?.registerCallback(callback, handler) } catch (_: Exception) {}
    }

    override fun onStop() {
        try { tim?.unregisterCallback(callback) } catch (_: Exception) {}
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        refreshApps()
        refreshInputs()
    }

    @Deprecated("Launcher: ignore Back")
    override fun onBackPressed() { /* stay on home */ }

    // ---------- Data ----------

    private fun refreshApps() {
        appsRow.removeAllViews()
        val pm = packageManager
        var list = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)
        if (list.isEmpty()) {
            list = pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        }
        list.filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
            .forEach { ri ->
                val icon = ImageView(this).apply {
                    setImageDrawable(ri.loadIcon(pm))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setPadding(dp(18), dp(18), dp(18), dp(18))
                }
                appsRow.addView(tile(icon, ri.loadLabel(pm).toString()) {
                    try {
                        startActivity(Intent(Intent.ACTION_MAIN)
                            .setComponent(ComponentName(ri.activityInfo.packageName, ri.activityInfo.name))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                        Toast.makeText(this, "Can't open app", Toast.LENGTH_SHORT).show()
                    }
                })
            }
        appsRow.getChildAt(0)?.requestFocus()
    }

    private fun refreshInputs() {
        inputsRow.removeAllViews()
        val manager = tim
        val live = try {
            manager?.tvInputList.orEmpty().filter {
                it.type == TvInputInfo.TYPE_HDMI &&
                    manager!!.getInputState(it.id) == TvInputManager.INPUT_STATE_CONNECTED
            }
        } catch (e: Exception) { emptyList() }

        if (live.isEmpty()) {
            inputsRow.addView(TextView(this).apply {
                text = "No HDMI sources are on"; textSize = 20f
                setTextColor(0x88FFFFFF.toInt()); setPadding(dp(6), dp(24), 0, dp(24))
            })
            return
        }
        live.forEach { info ->
            val label = info.loadLabel(this)?.toString() ?: "HDMI"
            val box = TextView(this).apply {
                text = "HDMI"; textSize = 30f; gravity = Gravity.CENTER
                setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            }
            inputsRow.addView(tile(box, label) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW,
                        TvContract.buildChannelUriForPassthroughInput(info.id))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    Toast.makeText(this, "Can't switch to $label", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    // ---------- UI helpers ----------

    private fun sectionLabel(t: String) = TextView(this).apply {
        text = t; textSize = 22f; setTextColor(0xAAFFFFFF.toInt())
        setPadding(dp(6), dp(28), 0, 0)
    }

    private fun scroller(row: LinearLayout) = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        clipChildren = false; clipToPadding = false
        setPadding(0, dp(20), 0, dp(20))
        addView(row)
    }

    private fun tile(content: View, label: String, onClick: () -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isFocusable = true; isClickable = true
            setPadding(dp(6), dp(6), dp(6), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = dp(26) }
        }
        val frame = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(34).toFloat(); setColor(0x26FFFFFF)
            }
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            addView(content, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        box.addView(frame, LinearLayout.LayoutParams(dp(150), dp(150)))
        val name = TextView(this).apply {
            text = label; textSize = 18f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; maxLines = 1; alpha = 0.55f
            setPadding(0, dp(10), 0, 0)
            maxWidth = dp(160)
        }
        box.addView(name)
        box.setOnClickListener { onClick() }
        box.setOnFocusChangeListener { v, focused ->
            val s = if (focused) 1.14f else 1f
            v.animate().scaleX(s).scaleY(s).setDuration(160).start()
            v.translationZ = if (focused) dp(12).toFloat() else 0f
            name.alpha = if (focused) 1f else 0.55f
            (frame.background as GradientDrawable).apply {
                setColor(if (focused) 0x40FFFFFF else 0x26FFFFFF)
                setStroke(if (focused) dp(3) else 0, Color.WHITE)
            }
        }
        return box
    }
}
