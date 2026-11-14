package com.tvhome

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.tv.TvInputManager
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Lists the TV's own system apps that look like input / source / live-TV apps.
 * OK opens one to test it; holding OK saves it, and the HDMI cards will then open it.
 */
class FinderActivity : Activity() {

    private class Cand(val pkg: String, val cls: String, val score: Int)

    private var px = 1f
    private lateinit var list: LinearLayout
    private lateinit var status: TextView

    private fun tv(text: String, sizeU: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_PX, sizeU * px)
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun statusText(): String {
        val tif = try {
            val n = (getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager)?.tvInputList?.size
            "TV inputs visible to apps: ${n ?: "none"}"
        } catch (e: Exception) {
            "TV inputs visible to apps: error (${e.javaClass.simpleName})"
        }
        val saved = getSharedPreferences("tvhome", MODE_PRIVATE).getString("input_app", null)
        return tif + "     |     " + (if (saved.isNullOrBlank()) "Nothing saved yet" else "Saved: $saved")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        px = resources.displayMetrics.widthPixels / 1000f
        val pad = (46 * px).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0B0F1A.toInt())
            setPadding(pad, (30 * px).toInt(), pad, (20 * px).toInt())
        }
        root.addView(tv("Input Finder", 36f, Color.WHITE, true))
        root.addView(tv("Find the app that switches your TV's inputs.   OK: open it   ·   Hold OK: use it for the HDMI cards   ·   Back: return",
            15f, 0xB3FFFFFF.toInt()).apply { setPadding(0, (6 * px).toInt(), 0, (6 * px).toInt()) })
        status = tv(statusText(), 15f, 0xFF7FD1FF.toInt()).apply { setPadding(0, 0, 0, (10 * px).toInt()) }
        root.addView(status)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val sv = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(list)
        }
        root.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        list.addView(tv("Scanning…", 18f, Color.WHITE))
        Thread {
            val found = scan()
            runOnUiThread { fill(found) }
        }.start()
    }

    private fun scan(): List<Cand> {
        val pm = packageManager
        val out = ArrayList<Cand>()
        val pkgs = try { pm.getInstalledPackages(PackageManager.GET_ACTIVITIES) } catch (e: Exception) { emptyList() }
        for (pi in pkgs) {
            val ai = pi.applicationInfo ?: continue
            val system = (ai.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
            if (!system || pi.packageName == packageName) continue
            val acts = pi.activities ?: continue
            for (a in acts) {
                if (!a.exported || !a.enabled) continue
                val key = (pi.packageName + "/" + a.name).lowercase()
                if (key.contains("inputmethod") || key.contains("keyboard")) continue
                var score = 0
                if (key.contains("hdmi")) score += 5
                if (key.contains("source")) score += 4
                if (key.contains("input")) score += 3
                if (key.contains("tvcenter") || key.contains("wwtv")) score += 4
                if (key.contains("livetv") || key.contains("tuner")) score += 3
                for (k in listOf("mtk", "mediatek", "droidlogic", "realtek", "mstar", "tcl", "toshiba", "vestel", "hisense")) {
                    if (key.contains(k)) score += 1
                }
                if (key.contains("tv")) score += 1
                if (score > 0) out.add(Cand(pi.packageName, a.name, score))
            }
        }
        return out.sortedWith(compareByDescending<Cand> { it.score }.thenBy { it.pkg + it.cls }).take(80)
    }

    private fun fill(found: List<Cand>) {
        list.removeAllViews()
        val saved = getSharedPreferences("tvhome", MODE_PRIVATE).getString("input_app", null)
        if (!saved.isNullOrBlank()) {
            list.addView(item("Clear the saved choice", saved) {
                getSharedPreferences("tvhome", MODE_PRIVATE).edit().remove("input_app").apply()
                status.text = statusText()
                Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show()
            }, null)
        }
        if (found.isEmpty()) {
            list.addView(tv("No input-related system apps found on this TV.", 18f, Color.WHITE))
            return
        }
        for (c in found) {
            val short = c.cls.substringAfterLast('.')
            list.addView(item(short, c.pkg + "  /  " + c.cls, { open(c) }, { save(c) }))
        }
        list.getChildAt(0)?.requestFocus()
    }

    private fun item(title: String, sub: String, onClick: () -> Unit, onLong: (() -> Unit)? = null): TextView {
        val t = TextView(this)
        t.text = title + "\n" + sub
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, 16f * px)
        t.setPadding((20 * px).toInt(), (10 * px).toInt(), (20 * px).toInt(), (10 * px).toInt())
        t.isFocusable = true
        t.isClickable = true
        fun paint(f: Boolean) {
            t.background = GradientDrawable().apply {
                cornerRadius = 14f * px
                setColor(if (f) Color.WHITE else 0x22FFFFFF)
            }
            t.setTextColor(if (f) Color.BLACK else Color.WHITE)
        }
        paint(false)
        t.setOnFocusChangeListener { _, f -> paint(f) }
        t.setOnClickListener { onClick() }
        if (onLong != null) {
            t.isLongClickable = true
            t.setOnLongClickListener { onLong(); true }
        }
        t.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (8 * px).toInt() }
        return t
    }

    private fun item(title: String, sub: String, onClick: () -> Unit): TextView = item(title, sub, onClick, null)

    private fun open(c: Cand) {
        try {
            startActivity(Intent(Intent.ACTION_MAIN).setClassName(c.pkg, c.cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(this, "Can't open that one", Toast.LENGTH_SHORT).show()
        }
    }

    private fun save(c: Cand) {
        getSharedPreferences("tvhome", MODE_PRIVATE).edit().putString("input_app", c.pkg + "/" + c.cls).apply()
        status.text = statusText()
        Toast.makeText(this, "Saved. The HDMI cards will now open this.", Toast.LENGTH_LONG).show()
    }
}
