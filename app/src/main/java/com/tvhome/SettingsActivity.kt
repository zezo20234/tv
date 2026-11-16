package com.tvhome

import android.app.Activity
import android.app.Dialog
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import kotlin.math.max

private const val MATCH = -1
private const val WRAP = -2

class SettingsActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private var W = 1000
    private var H = 600

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dm = resources.displayMetrics
        W = dm.widthPixels
        H = dm.heightPixels
        prefs = getSharedPreferences("tvhome", MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(PremiumColors.BACKGROUND_DEEP)
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(u(40f), u(32f), u(40f), u(20f))
        }
        val backBtn = TextView(this).apply {
            text = "←"
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, u(32f).toFloat())
            setTextColor(PremiumColors.TEXT_PRIMARY)
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            setPadding(u(16f), u(8f), u(16f), u(8f))
            background = GradientDrawable().apply {
                cornerRadius = u(24f).toFloat()
                setColor(Color.argb(30, 255, 255, 255))
            }
            setOnFocusChangeListener { _, f ->
                val alpha = if (f) 0.5f else 0.3f
                background = GradientDrawable().apply {
                    cornerRadius = u(24f).toFloat()
                    setColor(Color.argb((alpha * 255).toInt(), 255, 255, 255))
                }
            }
            setOnClickListener { finish() }
        }
        header.addView(backBtn, LinearLayout.LayoutParams(u(64f), u(64f)))
        val title = tv("Settings", 40f, PremiumColors.TEXT_PRIMARY, true).apply {
            setPadding(u(24f), 0, 0, 0)
        }
        header.addView(title, LinearLayout.LayoutParams(0, WRAP, 1f))
        root.addView(header, LinearLayout.LayoutParams(MATCH, WRAP))

        // Scrollable content
        val scrollView = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(u(40f), 0, u(40f), u(40f))
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // Appearance section
        content.addView(sectionHeader("Appearance"))
        content.addView(buildColorModeRow())
        content.addView(buildSeparator())

        // Launcher settings section
        content.addView(sectionHeader("Launcher"))
        content.addView(buildFavoritesRow())
        content.addView(buildSeparator())

        scrollView.addView(content, LinearLayout.LayoutParams(MATCH, WRAP))
        root.addView(scrollView, LinearLayout.LayoutParams(MATCH, 0, 1f))

        setContentView(root)
    }

    private fun sectionHeader(title: String): TextView = tv(title, 20f, PremiumColors.TEXT_SECONDARY, true).apply {
        setPadding(0, u(24f), 0, u(12f))
    }

    private fun buildSeparator(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, u(1f)).apply { topMargin = u(16f); bottomMargin = u(16f) }
        setBackgroundColor(PremiumColors.BORDER_SUBTLE)
    }

    private fun buildColorModeRow(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(u(16f), u(16f), u(16f), u(16f))
            background = rowBackground()
            isFocusable = true
            isClickable = true
            setOnClickListener { showColorModeDialog() }
            setOnFocusChangeListener { _, f -> updateRowBackground(this, f) }
        }

        val icon = TextView(this).apply {
            text = "🎨"
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, u(28f).toFloat())
            setPadding(u(20f), u(12f), u(20f), u(12f))
        }
        row.addView(icon)

        val textCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textCol.addView(tv("Color Theme", 18f, PremiumColors.TEXT_PRIMARY, true))
        textCol.addView(tv("Choose your preferred accent color", 14f, PremiumColors.TEXT_TERTIARY).apply {
            setPadding(0, u(4f), 0, 0)
        })
        row.addView(textCol, LinearLayout.LayoutParams(0, WRAP, 1f))

        val currentMode = prefs.getString("color_mode", null) ?: ColorMode.EMERALD.displayName
        val value = tv(currentMode, 16f, PremiumColors.TEXT_SECONDARY)
        row.addView(value)

        val arrow = tv("›", 24f, PremiumColors.TEXT_TERTIARY).apply { setPadding(u(16f), 0, 0, 0) }
        row.addView(arrow)

        return row
    }

    private fun buildFavoritesRow(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(u(16f), u(16f), u(16f), u(16f))
            background = rowBackground()
            isFocusable = true
            isClickable = true
            setOnClickListener { showFavoritesDialog() }
            setOnFocusChangeListener { _, f -> updateRowBackground(this, f) }
        }

        val icon = TextView(this).apply {
            text = "⭐"
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, u(28f).toFloat())
            setPadding(u(20f), u(12f), u(20f), u(12f))
        }
        row.addView(icon)

        val textCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textCol.addView(tv("Favorites", 18f, PremiumColors.TEXT_PRIMARY, true))
        textCol.addView(tv("Manage favorite apps", 14f, PremiumColors.TEXT_TERTIARY).apply {
            setPadding(0, u(4f), 0, 0)
        })
        row.addView(textCol, LinearLayout.LayoutParams(0, WRAP, 1f))

        val arrow = tv("›", 24f, PremiumColors.TEXT_TERTIARY).apply { setPadding(u(16f), 0, 0, 0) }
        row.addView(arrow)

        return row
    }

    private fun rowBackground(): GradientDrawable = GradientDrawable().apply {
        cornerRadius = u(16f).toFloat()
        setColor(PremiumColors.BACKGROUND_CARD)
    }

    private fun updateRowBackground(row: LinearLayout, focused: Boolean) {
        val color = if (focused) PremiumColors.BACKGROUND_CARD_HOVER else PremiumColors.BACKGROUND_CARD
        row.background = GradientDrawable().apply {
            cornerRadius = u(16f).toFloat()
            setColor(color)
        }
    }

    private fun showColorModeDialog() {
        val dlg = Dialog(this)
        dlg.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(u(28f), u(24f), u(28f), u(24f))
            background = GradientDrawable().apply {
                cornerRadius = u(28f).toFloat()
                setColor(PremiumColors.BACKGROUND_CHARCOAL)
                setStroke(max(1, u(1f)), PremiumColors.BORDER_SUBTLE)
            }
        }
        panel.addView(tv("Color Theme", 24f, PremiumColors.TEXT_PRIMARY, true))
        panel.addView(tv("Choose your preferred color mode", 14f, PremiumColors.TEXT_SECONDARY).apply {
            setPadding(0, u(6f), 0, u(20f))
        })

        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, u(16f))
        }
        val modeButtons = ArrayList<TextView>()
        val currentMode = prefs.getString("color_mode", null) ?: ColorMode.EMERALD.displayName
        for (mode in ColorMode.values()) {
            val btn = TextView(this).apply {
                text = mode.displayName
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, u(13f).toFloat())
                setTextColor(PremiumColors.TEXT_PRIMARY)
                setPadding(u(14f), u(10f), u(14f), u(10f))
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    cornerRadius = u(14f).toFloat()
                    setColor(mode.colors[0])
                }
                isFocusable = true
                isClickable = true
            }
            btn.setOnClickListener {
                modeButtons.forEach { it.alpha = 0.5f }
                btn.alpha = 1f
                prefs.edit().putString("color_mode", mode.displayName).apply()
            }
            if (mode.displayName == currentMode) btn.alpha = 1f else btn.alpha = 0.5f
            modeButtons.add(btn)
            modeRow.addView(btn, LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = u(8f) })
        }
        panel.addView(modeRow)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END; setPadding(0, u(18f), 0, 0)
        }
        buttons.addView(pill("Close", false) { dlg.dismiss() })
        panel.addView(buttons, LinearLayout.LayoutParams(MATCH, WRAP))
        dlg.setContentView(panel)
        dlg.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(u(600f), WindowManager.LayoutParams.WRAP_CONTENT)
            setDimAmount(0.65f)
        }
        dlg.show()
        modeButtons.find { it.text == currentMode }?.requestFocus()
    }

    private fun showFavoritesDialog() {
        val dlg = Dialog(this)
        dlg.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(u(28f), u(24f), u(28f), u(24f))
            background = GradientDrawable().apply {
                cornerRadius = u(28f).toFloat()
                setColor(PremiumColors.BACKGROUND_CHARCOAL)
                setStroke(max(1, u(1f)), PremiumColors.BORDER_SUBTLE)
            }
        }
        panel.addView(tv("Favorites", 24f, PremiumColors.TEXT_PRIMARY, true))
        panel.addView(tv("Manage your favorite apps from the home screen", 14f, PremiumColors.TEXT_SECONDARY).apply {
            setPadding(0, u(6f), 0, u(20f))
        })

        panel.addView(tv("Favorites are automatically managed based on popular apps", 14f, PremiumColors.TEXT_TERTIARY).apply {
            setPadding(0, 0, 0, u(20f))
        })

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END; setPadding(0, u(18f), 0, 0)
        }
        buttons.addView(pill("Close", false) { dlg.dismiss() })
        panel.addView(buttons, LinearLayout.LayoutParams(MATCH, WRAP))
        dlg.setContentView(panel)
        dlg.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(u(520f), WindowManager.LayoutParams.WRAP_CONTENT)
            setDimAmount(0.65f)
        }
        dlg.show()
    }

    private fun pill(text: String, primary: Boolean, onClick: () -> Unit): TextView {
        val t = tv(text, 15f, PremiumColors.TEXT_PRIMARY, true)
        fun paint(f: Boolean) {
            t.background = GradientDrawable().apply {
                cornerRadius = u(30f).toFloat()
                setColor(if (f) PremiumColors.TEXT_PRIMARY else if (primary) PremiumColors.ACCENT_EMERALD else Color.argb(51, 255, 255, 255))
            }
            t.setTextColor(if (f) PremiumColors.BACKGROUND_DEEP else PremiumColors.TEXT_PRIMARY)
        }
        t.gravity = Gravity.CENTER
        t.isFocusable = true
        t.isClickable = true
        t.setPadding(u(26f), u(11f), u(26f), u(11f))
        paint(false)
        t.setOnFocusChangeListener { _, f -> paint(f) }
        t.setOnClickListener { onClick() }
        return t
    }

    private fun tv(text: String, sizeU: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, u(sizeU).toFloat())
            setTextColor(color)
            typeface = if (bold) android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL) else android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        }

    private fun u(f: Float): Int = (f * W / 1000f).toInt()
}
