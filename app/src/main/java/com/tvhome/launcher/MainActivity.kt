package com.tvhome.launcher

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.*

class MainActivity : Activity() {

    data class AppEntry(val label: String, val pkg: String, val component: ComponentName, val icon: Drawable)

    // keywords match the app's name, packages are known ids (either is enough)
    private val featured: List<Pair<List<String>, List<String>>> = listOf(
        listOf("youtube") to listOf("com.google.android.youtube.tv", "com.google.android.youtube.tvunplugged", "com.google.android.youtube"),
        listOf("netflix") to listOf("com.netflix.ninja"),
        listOf("abc iview", "iview") to listOf("au.net.abc.iview", "au.net.abc.iview.tv"),
        listOf("disney+", "disney plus") to listOf("com.disney.disneyplus"),
        listOf("stan") to listOf("au.com.stan.and"),
        listOf("binge") to listOf("au.com.streamotion.binge", "au.com.streamotion.ares.tv"),
        listOf("prime video") to listOf("com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient"),
        listOf("sbs") to listOf("au.com.sbs.ondemand.tv", "au.com.sbs.ondemand"),
        listOf("apple tv") to listOf("com.apple.atve.androidtv.appletv"),
        listOf("paramount+", "paramount") to listOf("com.cbs.ott", "com.cbs.ca")
    )

    private var apps: List<AppEntry> = emptyList()
    private var person = 1
    private var overlay: View? = null
    private lateinit var root: FrameLayout
    private lateinit var dock: LinearLayout
    private lateinit var recentRow: LinearLayout
    private lateinit var pill1: TextView
    private lateinit var pill2: TextView
    private val prefs by lazy { getSharedPreferences("tvhome", MODE_PRIVATE) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        loadApps()
        rebuildDock()
        rebuildRecents()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        closeOverlay() // pressing Home returns to the main screen
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (overlay != null) closeOverlay() // on the home screen Back does nothing
    }

    // ---------- data ----------

    private fun loadApps() {
        val pm = packageManager
        val out = LinkedHashMap<String, AppEntry>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val q = Intent(Intent.ACTION_MAIN).addCategory(cat)
            for (ri in pm.queryIntentActivities(q, 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == packageName || out.containsKey(pkg)) continue
                out[pkg] = AppEntry(
                    ri.loadLabel(pm).toString(), pkg,
                    ComponentName(pkg, ri.activityInfo.name), ri.loadIcon(pm)
                )
            }
        }
        apps = out.values.sortedBy { it.label.lowercase() }
    }

    private fun find(keys: List<String>, pkgs: List<String>): AppEntry? {
        apps.firstOrNull { it.pkg in pkgs }?.let { return it }
        return apps.firstOrNull { a ->
            val l = a.label.lowercase()
            keys.any { k -> l == k || l.startsWith("$k ") }
        }
    }

    private fun launch(a: AppEntry) {
        try {
            startActivity(Intent(Intent.ACTION_MAIN).setComponent(a.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val key = "recent_$person"
            val list = (prefs.getString(key, "") ?: "").split(",").filter { it.isNotBlank() && it != a.pkg }.toMutableList()
            list.add(0, a.pkg)
            prefs.edit().putString(key, list.take(4).joinToString(",")).apply()
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open ${a.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uninstallSelf() {
        try {
            startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            } catch (e2: Exception) {
                Toast.makeText(this, "Open Settings > Apps > TV Home > Uninstall", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ---------- UI ----------

    private fun buildUi() {
        root = FrameLayout(this)
        root.background = SceneDrawable()

        // person pills (top left)
        val pills = LinearLayout(this)
        pill1 = pill("Person 1", { person == 1 }) { person = 1; refreshPills(); rebuildRecents() }
        pill2 = pill("Person 2", { person == 2 }) { person = 2; refreshPills(); rebuildRecents() }
        pills.addView(pill1); pills.addView(pill2)
        root.addView(pills, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
            setMargins(dp(28), dp(24), 0, 0)
        })

        // date (top centre)
        val date = TextClock(this).apply {
            format12Hour = "EEEE d MMMM"; format24Hour = "EEEE d MMMM"
            setTextColor(0xDDFFFFFF.toInt()); textSize = 16f
        }
        root.addView(date, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(26)
        })

        // moon button (top right) -> screensaver settings
        val moon = FrameLayout(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x44FFFFFF) }
            addView(MoonView(context), FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        }
        focusable(moon, 24) {
            try { startActivity(Intent("android.settings.DREAM_SETTINGS")) }
            catch (e: Exception) { try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) {} }
        }
        root.addView(moon, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP or Gravity.END).apply {
            setMargins(0, dp(22), dp(28), 0)
        })

        // big clock
        val clock = TextClock(this).apply {
            format12Hour = "h:mm"; format24Hour = "H:mm"
            setTextColor(0xCCFFFFFF.toInt()); textSize = 190f
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            includeFontPadding = false
        }
        root.addView(clock, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(36)
        })

        // keep watching
        val kw = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        kw.addView(TextView(this).apply {
            text = "Keep watching"; setTextColor(0xCCFFFFFF.toInt()); textSize = 12f
            setPadding(dp(6), 0, 0, dp(6))
        })
        recentRow = LinearLayout(this)
        kw.addView(recentRow)
        root.addView(kw, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply {
            setMargins(dp(40), 0, 0, dp(112))
        })

        // dock
        dock = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply { cornerRadius = dp(26).toFloat(); setColor(0x88101018.toInt()) }
        }
        root.addView(dock, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(22)
        })

        setContentView(root)
        refreshPills()
    }

    private fun rebuildDock() {
        dock.removeAllViews()
        var first: View? = null
        for ((keys, pkgs) in featured) {
            val a = find(keys, pkgs) ?: continue
            val t = iconTile(a.icon, 62) { launch(a) }
            if (first == null) first = t
            dock.addView(t)
        }
        // settings
        val gear = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_manage)
            setColorFilter(Color.WHITE)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(0x44FFFFFF) }
        }
        dock.addView(wrapTile(gear, 62) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) {}
        })
        // more -> all apps
        val more = TextView(this).apply {
            text = "•••"; setTextColor(Color.WHITE); textSize = 18f; gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(0x44FFFFFF) }
        }
        val moreTile = wrapTile(more, 62) { showAllApps() }
        dock.addView(moreTile)
        (first ?: moreTile).requestFocus()
    }

    private fun rebuildRecents() {
        recentRow.removeAllViews()
        val pkgs = (prefs.getString("recent_$person", "") ?: "").split(",").filter { it.isNotBlank() }
        val items = pkgs.mapNotNull { p -> apps.firstOrNull { it.pkg == p } }.take(2)
        if (items.isEmpty()) {
            recentRow.addView(card(null, "TV HOME", "Apps you open show up here") {})
        } else {
            items.forEach { a -> recentRow.addView(card(a.icon, a.label.uppercase(), "Continue") { launch(a) }) }
        }
    }

    private fun card(icon: Drawable?, top: String, bottom: String, onClick: () -> Unit): View {
        val c = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(0x88181820.toInt()) }
        }
        if (icon != null) {
            c.addView(ImageView(this).apply {
                setImageDrawable(icon)
                background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(0x33FFFFFF) }
                clipToOutline = true
                outlineProvider = roundOutline(10)
            }, LinearLayout.LayoutParams(dp(52), dp(52)).apply { rightMargin = dp(12) })
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(this).apply { text = top; setTextColor(0x99FFFFFF.toInt()); textSize = 9f })
        col.addView(TextView(this).apply { text = bottom; setTextColor(Color.WHITE); textSize = 14f; typeface = Typeface.DEFAULT_BOLD })
        c.addView(col)
        focusable(c, 18, onClick)
        c.layoutParams = LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(4), 0, dp(8), 0) }
        return c
    }

    // ---------- all apps screen with the uninstall button ----------

    private fun showAllApps() {
        if (overlay != null) return
        val o = FrameLayout(this).apply { setBackgroundColor(0xF2101018.toInt()); isClickable = true }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(28), dp(48), dp(16))
        }
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        bar.addView(TextView(this).apply {
            text = "All apps"; setTextColor(Color.WHITE); textSize = 26f
        }, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(pill("Close", { false }) { closeOverlay() })
        bar.addView(pill("Uninstall this launcher", { false }, danger = true) { uninstallSelf() })
        col.addView(bar, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })

        val grid = GridView(this).apply {
            numColumns = 6
            horizontalSpacing = dp(8); verticalSpacing = dp(8)
            selector = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(0x44FFFFFF) }
            adapter = object : BaseAdapter() {
                override fun getCount() = apps.size
                override fun getItem(position: Int) = apps[position]
                override fun getItemId(position: Int) = position.toLong()
                override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                    val a = apps[position]
                    return LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_HORIZONTAL
                        setPadding(dp(8), dp(10), dp(8), dp(10))
                        addView(ImageView(context).apply { setImageDrawable(a.icon) },
                            LinearLayout.LayoutParams(dp(64), dp(64)))
                        addView(TextView(context).apply {
                            text = a.label; setTextColor(Color.WHITE); textSize = 12f
                            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                            gravity = Gravity.CENTER
                        })
                    }
                }
            }
            setOnItemClickListener { _, _, pos, _ -> closeOverlay(); launch(apps[pos]) }
        }
        col.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        o.addView(col, FrameLayout.LayoutParams(-1, -1))
        root.addView(o, FrameLayout.LayoutParams(-1, -1))
        overlay = o
        grid.requestFocus()
    }

    private fun closeOverlay() {
        overlay?.let { root.removeView(it) }
        overlay = null
        if (::dock.isInitialized && dock.childCount > 0) dock.getChildAt(0).requestFocus()
    }

    // ---------- small helpers ----------

    private fun roundOutline(r: Int) = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, dp(r).toFloat())
        }
    }

    private fun focusable(v: View, radiusDp: Int, onClick: () -> Unit) {
        v.isFocusable = true
        v.isClickable = true
        v.setOnClickListener { onClick() }
        v.setOnFocusChangeListener { view, has ->
            view.animate().scaleX(if (has) 1.12f else 1f).scaleY(if (has) 1.12f else 1f).setDuration(120).start()
            view.foreground = if (has) GradientDrawable().apply {
                cornerRadius = dp(radiusDp).toFloat(); setStroke(dp(3), Color.WHITE); setColor(Color.TRANSPARENT)
            } else null
        }
    }

    private fun wrapTile(inner: View, sizeDp: Int, onClick: () -> Unit): View {
        inner.clipToOutline = true
        inner.outlineProvider = roundOutline(14)
        inner.layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)).apply {
            setMargins(dp(7), dp(4), dp(7), dp(4))
        }
        focusable(inner, 14, onClick)
        return inner
    }

    private fun iconTile(d: Drawable, sizeDp: Int, onClick: () -> Unit): View {
        val iv = ImageView(this).apply {
            setImageDrawable(d)
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(0xFF1E1E24.toInt()) }
        }
        return wrapTile(iv, sizeDp, onClick)
    }

    private fun pill(text: String, selected: () -> Boolean, danger: Boolean = false, onClick: () -> Unit): TextView {
        val tv = TextView(this).apply {
            this.text = text; textSize = 13f
            setPadding(dp(18), dp(8), dp(18), dp(8))
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { setMargins(0, 0, dp(8), 0) }
        }
        fun style(active: Boolean) {
            tv.setTextColor(if (active) 0xFF111111.toInt() else Color.WHITE)
            tv.background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(if (active) Color.WHITE else if (danger) 0xFFB3261E.toInt() else 0x55222230)
            }
        }
        tv.isFocusable = true; tv.isClickable = true
        tv.setOnClickListener { onClick() }
        tv.setOnFocusChangeListener { _, f -> style(f || selected()) }
        tv.tag = { style(tv.hasFocus() || selected()) }
        style(selected())
        return tv
    }

    @Suppress("UNCHECKED_CAST")
    private fun refreshPills() {
        (pill1.tag as? () -> Unit)?.invoke()
        (pill2.tag as? () -> Unit)?.invoke()
    }
}

/** Crescent moon icon */
class MoonView(ctx: android.content.Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val a = Path().apply { addCircle(w / 2, h / 2, w * 0.45f, Path.Direction.CW) }
        val b = Path().apply { addCircle(w * 0.68f, h * 0.38f, w * 0.38f, Path.Direction.CW) }
        a.op(b, Path.Op.DIFFERENCE)
        c.drawPath(a, paint)
    }
}
