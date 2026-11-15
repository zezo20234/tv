package com.realm.tvos

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class SettingsActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var settingsContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("realm_tvos_v22", MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(60, 40, 60, 30)
        }

        val title = TextView(this).apply {
            text = "Settings"
            textSize = 36f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(Color.WHITE)
        }
        header.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val backText = TextView(this).apply {
            text = "← Back"
            textSize = 20f
            setTextColor(0x99FFFFFF.toInt())
            isFocusable = true
            isClickable = true
            setOnClickListener { finish() }
        }
        header.addView(backText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        root.addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        settingsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 20, 60, 60)
        }

        val scrollView = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(settingsContainer)
        }
        root.addView(scrollView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)

        buildSettings()
    }

    private fun buildSettings() {
        settingsContainer.addView(createSectionHeader("Display"))
        settingsContainer.addView(createSettingItem("Theme", "Change color theme") { showThemeDialog() })
        settingsContainer.addView(createSectionHeader("Applications"))
        settingsContainer.addView(createSettingItem("Manage Apps", "View and manage installed apps") { openAppSettings() })
        settingsContainer.addView(createSectionHeader("System"))
        settingsContainer.addView(createSettingItem("Android Settings", "Open system settings") { openSystemSettings() })
        settingsContainer.addView(createSettingItem("Device Info", "View device information") { showDeviceInfo() })
    }

    private fun createSectionHeader(title: String): TextView {
        return TextView(this).apply {
            text = title
            textSize = 24f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(Color.WHITE)
            setPadding(0, 40, 0, 20)
        }
    }

    private fun createSettingItem(title: String, subtitle: String, onClick: () -> Unit): View {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 25, 30, 25)
            background = createItemBackground()
            isFocusable = true
            isClickable = true
            setOnClickListener { onClick() }
        }

        val titleText = TextView(this).apply {
            text = title
            textSize = 22f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(Color.WHITE)
        }
        item.addView(titleText)

        val subtitleText = TextView(this).apply {
            text = subtitle
            textSize = 16f
            setTextColor(0x99FFFFFF.toInt())
            setPadding(0, 8, 0, 0)
        }
        item.addView(subtitleText)

        item.setOnFocusChangeListener { _, focused ->
            item.background = if (focused) {
                createFocusedBackground()
            } else {
                createItemBackground()
            }
        }

        return item
    }

    private fun createItemBackground(): android.graphics.drawable.Drawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(0x3DFFFFFF.toInt())
            cornerRadius = 20f
        }
    }

    private fun createFocusedBackground(): android.graphics.drawable.Drawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(0x6DFFFFFF.toInt())
            cornerRadius = 20f
            setStroke(3, 0xFFFFFFFF.toInt())
        }
    }

    private fun showThemeDialog() {
        finish()
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Couldn't open app settings", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun openSystemSettings() {
        try {
            val intent = Intent(Settings.ACTION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Couldn't open settings", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeviceInfo() {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 32, 40, 32)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 32f
                setColor(0xF01C1C24.toInt())
            }
        }

        panel.addView(TextView(this).apply {
            text = "Device Information"
            textSize = 28f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 24)
        })

        val info = listOf(
            "REALM TV OS v22" to "Version",
            android.os.Build.MODEL to "Device",
            android.os.Build.VERSION.RELEASE to "Android"
        )

        for ((value, label) in info) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 16, 0, 16)
            }
            row.addView(TextView(this).apply {
                text = "$label:"
                textSize = 18f
                setTextColor(0x99FFFFFF.toInt())
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(this).apply {
                text = value
                textSize = 18f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                setTextColor(Color.WHITE)
            })
            panel.addView(row)
        }

        val closeBtn = TextView(this).apply {
            text = "Close"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(32, 16, 32, 16)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 28f
                setColor(0xFF0A84FF.toInt())
            }
            isFocusable = true
            isClickable = true
            setOnClickListener { dialog.dismiss() }
        }
        panel.addView(closeBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply { topMargin = 24 })

        dialog.setContentView(panel)
        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            setLayout(600, android.view.WindowManager.LayoutParams.WRAP_CONTENT)
            setDimAmount(0.7f)
        }
        dialog.show()
        closeBtn.requestFocus()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
