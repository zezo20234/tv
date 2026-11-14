package com.tvhome

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.animation.ValueAnimator
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.Window
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

// ======================= Custom views =======================

/** Dark cinematic mesh gradient with slowly drifting colored light blobs (parallax feel). */
class AmbientBackground(ctx: Context) : View(ctx) {
    private class Blob(
        val paint: Paint, val r: Float,
        val fx: Float, val fy: Float, val ax: Float, val ay: Float, val phase: Float
    )

    private val base = Paint().apply { color = 0xFF05070D.toInt() }
    private val blobs = ArrayList<Blob>()
    private var t = 0f
    private var lastInvalidate = 0L

    private val anim = ValueAnimator.ofFloat(0f, (2.0 * Math.PI).toFloat()).apply {
        duration = 48000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            t = it.animatedValue as Float
            val now = SystemClock.uptimeMillis()
            if (now - lastInvalidate > 50L) { lastInvalidate = now; invalidate() }
        }
    }

    private fun blobPaint(color: Int, alpha: Int, r: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            0f, 0f, r,
            intArrayOf(
                Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)),
                Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))
            ),
            null, Shader.TileMode.CLAMP
        )
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        blobs.clear()
        val m = max(w, h).toFloat()
        val blue = 0xFF1E40AF.toInt()
        val purple = 0xFF6D28D9.toInt()
        val teal = 0xFF0F766E.toInt()
        blobs.add(Blob(blobPaint(blue, 175, m * 0.75f), m * 0.75f, 0.15f, 0.10f, 0.10f, 0.08f, 0f))
        blobs.add(Blob(blobPaint(purple, 125, m * 0.60f), m * 0.60f, 0.88f, 0.30f, 0.08f, 0.12f, 2.1f))
        blobs.add(Blob(blobPaint(teal, 115, m * 0.70f), m * 0.70f, 0.55f, 1.00f, 0.12f, 0.06f, 4.2f))
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), base)
        for (b in blobs) {
            val cx = (b.fx + b.ax * sin(t + b.phase)) * width
            val cy = (b.fy + b.ay * cos(2f * t + b.phase)) * height
            canvas.save()
            canvas.translate(cx, cy)
            canvas.drawCircle(0f, 0f, b.r, b.paint)
            canvas.restore()
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }
}

/** Little Wi-Fi glyph. */
class WifiView(ctx: Context) : View(ctx) {
    var on: Boolean = true
        set(v) { if (field != v) { field = v; invalidate() } }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val color = if (on) Color.WHITE else 0x55FFFFFF
        p.color = color; dot.color = color
        p.strokeWidth = h * 0.10f
        val cx = w / 2f; val cy = h * 0.92f
        for (i in 1..3) {
            val r = h * 0.30f * i
            rect.set(cx - r, cy - r, cx + r, cy + r)
            canvas.drawArc(rect, 225f, 90f, false, p)
        }
        canvas.drawCircle(cx, cy, h * 0.07f, dot)
    }
}

/** Metallic HDMI port glyph. */
class HdmiPortView(ctx: Context) : View(ctx) {
    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cavity = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0E1016.toInt() }
    private val pin = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC9CED8.toInt() }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = 0x99FFFFFF.toInt()
    }
    private val outer = Path()
    private val inner = Path()

    private fun shape(p: Path, l: Float, t: Float, r: Float, b: Float) {
        val w = r - l; val h = b - t
        p.reset()
        p.moveTo(l, t); p.lineTo(r, t)
        p.lineTo(r, t + h * 0.52f)
        p.lineTo(r - w * 0.10f, b)
        p.lineTo(l + w * 0.10f, b)
        p.lineTo(l, t + h * 0.52f)
        p.close()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        body.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(0xFFF2F4F8.toInt(), 0xFF9AA2B1.toInt(), 0xFFDDE1E8.toInt(), 0xFF6E7687.toInt()),
            floatArrayOf(0f, 0.35f, 0.6f, 1f), Shader.TileMode.CLAMP
        )
        rim.strokeWidth = max(1f, h * 0.03f)
        shape(outer, 0f, 0f, w.toFloat(), h.toFloat())
        val i = h * 0.14f
        shape(inner, i * 1.2f, i, w - i * 1.2f, h - i * 0.9f)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawPath(outer, body)
        canvas.drawPath(outer, rim)
        canvas.drawPath(inner, cavity)
        val n = 9
        val pw = width * 0.05f
        val ph = height * 0.12f
        for (k in 0 until n) {
            val x = width * 0.18f + k * (width * 0.64f / (n - 1)) - pw / 2f
            canvas.drawRoundRect(x, height * 0.34f, x + pw, height * 0.34f + ph, 2f, 2f, pin)
        }
    }
}

// ======================= Activity =======================

class MainActivity : Activity() {

    private class AppEntry(val pkg: String, val cls: String, val label: String, val ri: ResolveInfo)

    private val handler = Handler(Looper.getMainLooper())
    private var W = 1000
    private var cardW = 0
    private var cardH = 0
    private var gap = 0

    private lateinit var scroll: ScrollView
    private lateinit var heroRow: LinearLayout
    private lateinit var inputsRow: LinearLayout
    private lateinit var gridCol: LinearLayout
    private lateinit var greeting: TextView
    private lateinit var wifi: WifiView
    private lateinit var prefs: SharedPreferences
    private var tim: TvInputManager? = null
    private var lastSig = ""

    private val palette = intArrayOf(
        0xFFE50914.toInt(), 0xFF0A84FF.toInt(), 0xFF8E44AD.toInt(), 0xFF16A085.toInt(),
        0xFFE67E22.toInt(), 0xFF2E86DE.toInt(), 0xFFD63384.toInt(), 0xFF27AE60.toInt()
    )

    private val ticker = object : Runnable {
        override fun run() {
            wifi.on = isOnline()
            updateGreeting()
            handler.postDelayed(this, 5000L)
        }
    }

    // relative unit: 1000 units = screen width, so the layout scales to any TV resolution
    private fun u(f: Float): Int = (f * W / 1000f).toInt()
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ---------- lifecycle ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        W = resources.displayMetrics.widthPixels
        prefs = getSharedPreferences("tvhome", MODE_PRIVATE)
        tim = getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager

        gap = u(18f)
        val padH = u(42f)
        cardW = (W - 2 * padH - 4 * gap) / 5
        cardH = cardW * 9 / 16

        val root = FrameLayout(this)
        root.addView(AmbientBackground(this), MATCH, MATCH)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            setPadding(padH, u(22f), padH, u(10f))
        }
        column.addView(buildTopBar(), LinearLayout.LayoutParams(MATCH, WRAP))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        heroRow = row()
        inputsRow = row()
        gridCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
        content.addView(heroRow)
        content.addView(sectionHeader("Inputs", "Hold OK to rename"))
        content.addView(inputsRow)
        content.addView(sectionHeader("All Apps", null))
        content.addView(gridCol)

        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipChildren = false
            clipToPadding = false
            setPadding(0, u(26f), 0, u(30f))
            addView(content, MATCH, WRAP)
        }
        column.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        root.addView(column, MATCH, MATCH)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refreshApps(false)
        refreshInputs(0)
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Home pressed while already on home: jump back to the top
        scroll.smoothScrollTo(0, 0)
        heroRow.getChildAt(0)?.requestFocus()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() { /* launcher: stay home */ }

    // ---------- top bar ----------

    private fun buildTopBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(u(26f), u(10f), u(26f), u(10f))
            background = GradientDrawable().apply {
                cornerRadius = u(40f).toFloat()
                setColor(0x26FFFFFF)
                setStroke(max(1, u(1f)), 0x33FFFFFF)
            }
        }
        greeting = tv("Hello", 22f, Color.WHITE, true)
        bar.addView(greeting, LinearLayout.LayoutParams(0, WRAP, 1f))

        wifi = WifiView(this)
        bar.addView(wifi, LinearLayout.LayoutParams(u(26f), u(21f)).apply { rightMargin = u(20f) })

        val clock = TextClock(this).apply {
            format12Hour = "h:mm a"
            format24Hour = "HH:mm"
            setTextSize(TypedValue.COMPLEX_UNIT_PX, u(22f).toFloat())
            setTextColor(0xE6FFFFFF.toInt())
        }
        bar.addView(clock)
        updateGreeting()
        return bar
    }

    private fun updateGreeting() {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        greeting.text = when {
            h < 5 -> "Good night"
            h < 12 -> "Good morning"
            h < 18 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    private fun isOnline(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= 23) {
                val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else true
        } catch (e: Exception) { true }
    }

    // ---------- apps ----------

    private fun loadApps(): List<AppEntry> {
        val pm = packageManager
        val leanback = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)
        val launcher = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val seen = HashSet<String>()
        val out = ArrayList<AppEntry>()
        for (ri in leanback + launcher) {
            val p = ri.activityInfo.packageName
            if (p == packageName || !seen.add(p)) continue
            out.add(AppEntry(p, ri.activityInfo.name, ri.loadLabel(pm).toString(), ri))
        }
        return out.sortedBy { it.label.lowercase() }
    }

    private fun pickHero(apps: List<AppEntry>): List<AppEntry> {
        val keys = listOf("netflix", "youtube", "prime", "disney", "shahid",
            "spotify", "hbo", "apple", "browser", "multimedia")
        val out = ArrayList<AppEntry>()
        for (k in keys) {
            val m = apps.firstOrNull {
                !out.contains(it) && (it.label.lowercase().contains(k) || it.pkg.lowercase().contains(k))
            }
            if (m != null) out.add(m)
            if (out.size == 5) return out
        }
        for (a in apps) {
            if (!out.contains(a)) out.add(a)
            if (out.size == 5) break
        }
        return out
    }

    private fun refreshApps(force: Boolean) {
        val apps = loadApps()
        val sig = apps.joinToString("|") { it.pkg }
        if (!force && sig == lastSig) return
        lastSig = sig
        heroRow.removeAllViews()
        gridCol.removeAllViews()

        val hero = pickHero(apps)
        hero.forEachIndexed { i, a -> heroRow.addView(appCard(a, true, i < hero.size - 1)) }
        apps.chunked(5).forEach { chunk ->
            val r = row()
            chunk.forEachIndexed { i, a -> r.addView(appCard(a, false, i < chunk.size - 1)) }
            gridCol.addView(r)
        }
        heroRow.getChildAt(0)?.requestFocus()
    }

    private fun launchApp(a: AppEntry) {
        try {
            startActivity(Intent(Intent.ACTION_MAIN).setClassName(a.pkg, a.cls)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        } catch (e: Exception) {
            try {
                val i = packageManager.getLaunchIntentForPackage(a.pkg)
                if (i != null) startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                else Toast.makeText(this, "Can't open ${a.label}", Toast.LENGTH_SHORT).show()
            } catch (e2: Exception) {
                Toast.makeText(this, "Can't open ${a.label}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun appCard(a: AppEntry, hero: Boolean, notLast: Boolean): View {
        val pm = packageManager
        val banner: Drawable? = try { a.ri.activityInfo.loadBanner(pm) } catch (e: Exception) { null }
        val icon: Drawable? = try { a.ri.loadIcon(pm) } catch (e: Exception) { null }
        val color = cardColor(banner ?: icon, a.pkg)

        val frame = makeFrame(cardBackground(color))
        if (banner != null) {
            frame.addView(ImageView(this).apply {
                setImageDrawable(banner)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }, FrameLayout.LayoutParams(MATCH, MATCH))
        } else if (icon != null) {
            val s = (cardH * 0.58f).toInt()
            frame.addView(ImageView(this).apply {
                setImageDrawable(icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, FrameLayout.LayoutParams(s, s, Gravity.CENTER))
        }
        if (hero && banner == null) {
            val scrim = View(this).apply {
                background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(0x00000000, 0xAA000000.toInt()))
            }
            frame.addView(scrim, FrameLayout.LayoutParams(MATCH, (cardH * 0.45f).toInt(), Gravity.BOTTOM))
            val t = tv(a.label, 13f, Color.WHITE, true).apply {
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            }
            frame.addView(t, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.START).apply {
                leftMargin = u(10f); bottomMargin = u(7f); rightMargin = u(10f)
            })
        }
        val (border, sheen) = overlays(frame)

        val label = tv(a.label, 13f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            alpha = 0.5f
            setPadding(0, u(10f), 0, 0)
        }

        val box = cardBox(frame, if (hero) null else label)
        box.setOnClickListener { launchApp(a) }
        focusFx(box, frame, border, sheen, if (hero) null else label)
        box.layoutParams = LinearLayout.LayoutParams(cardW, WRAP).apply {
            rightMargin = if (notLast) gap else 0
            bottomMargin = u(14f)
        }
        return box
    }

    // ---------- HDMI ----------

    private fun hdmiName(n: Int): String =
        prefs.getString("name_$n", null)?.takeIf { it.isNotBlank() } ?: "HDMI $n"

    private fun isInputLive(n: Int): Boolean {
        return try {
            val m = tim
            if (m == null) false else {
                val info = m.tvInputList.firstOrNull {
                    it.type == TvInputInfo.TYPE_HDMI && it.id.endsWith("/HW$n")
                }
                info != null && m.getInputState(info.id) == TvInputManager.INPUT_STATE_CONNECTED
            }
        } catch (e: Exception) { false }
    }

    private fun refreshInputs(focusN: Int) {
        inputsRow.removeAllViews()
        for (n in 1..3) inputsRow.addView(hdmiCard(n, n < 3))
        if (focusN in 1..3) inputsRow.getChildAt(focusN - 1)?.requestFocus()
    }

    private fun hdmiCard(n: Int, notLast: Boolean): View {
        val frame = makeFrame(GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF454E67.toInt(), 0xFF1A1F2E.toInt())
        ).apply { cornerRadius = dp(22).toFloat() })

        // glass highlight
        frame.addView(View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x30FFFFFF, 0x00FFFFFF))
        }, FrameLayout.LayoutParams(MATCH, MATCH))

        val pw = (cardW * 0.40f).toInt()
        val ph = (pw * 0.46f).toInt()
        frame.addView(HdmiPortView(this),
            FrameLayout.LayoutParams(pw, ph, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = (cardH * 0.26f).toInt()
            })

        frame.addView(tv(hdmiName(n), 15f, Color.WHITE, true).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER
        }, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM).apply {
            bottomMargin = u(10f); leftMargin = u(8f); rightMargin = u(8f)
        })

        // indicator badge (green dot = TV reports a device on this port)
        val live = isInputLive(n)
        val badge = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(u(8f), u(4f), u(10f), u(4f))
            background = GradientDrawable().apply {
                cornerRadius = u(14f).toFloat(); setColor(0x59000000)
            }
        }
        badge.addView(View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (live) 0xFF34C759.toInt() else 0xFF6B7280.toInt())
            }
        }, LinearLayout.LayoutParams(u(7f), u(7f)).apply { rightMargin = u(6f) })
        badge.addView(tv("PORT $n", 10f, 0xCCFFFFFF.toInt(), true))
        frame.addView(badge, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START).apply {
            leftMargin = u(9f); topMargin = u(9f)
        })

        val (border, sheen) = overlays(frame)
        val box = cardBox(frame, null)
        box.setOnClickListener { switchHdmi(n) }
        box.isLongClickable = true
        box.setOnLongClickListener { showRenameDialog(n); true }
        focusFx(box, frame, border, sheen, null)
        box.layoutParams = LinearLayout.LayoutParams(cardW, WRAP).apply {
            rightMargin = if (notLast) gap else 0
            bottomMargin = u(14f)
        }
        return box
    }

    private fun switchHdmi(n: Int) {
        val attempts = ArrayList<Intent>()
        try {
            val info = tim?.tvInputList?.firstOrNull {
                it.type == TvInputInfo.TYPE_HDMI && it.id.endsWith("/HW$n")
            }
            if (info != null) {
                attempts.add(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(info.id)))
            }
        } catch (e: Exception) { }
        attempts.add(Intent(Intent.ACTION_VIEW, Uri.parse(
            "content://android.media.tv/passthrough/com.mediatek.tvinput%2F.hdmi.HDMIInputService%2FHW$n")))
        for (pkg in listOf("com.mediatek.wwtv.tvcenter", "com.android.tv", "com.google.android.tv")) {
            packageManager.getLaunchIntentForPackage(pkg)?.let { attempts.add(it) }
        }
        attempts.add(Intent("android.intent.action.TV_INPUT_BUTTON"))

        for (i in attempts) {
            try {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
                return
            } catch (e: Exception) { /* try next */ }
        }
        Toast.makeText(this, "Couldn't switch to ${hdmiName(n)}. Press INPUT on your remote.",
            Toast.LENGTH_LONG).show()
    }

    // ---------- rename dialog ----------

    private fun showRenameDialog(n: Int) {
        val dlg = Dialog(this)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(u(28f), u(24f), u(28f), u(24f))
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setColor(0xF01C1C24.toInt())
                setStroke(max(1, u(1f)), 0x33FFFFFF)
            }
        }
        panel.addView(tv("Rename HDMI $n", 22f, Color.WHITE, true))
        panel.addView(tv("e.g. PlayStation 5, Apple TV. Leave empty to reset.", 13f, 0x99FFFFFF.toInt()).apply {
            setPadding(0, u(6f), 0, u(16f))
        })

        val edit = EditText(this).apply {
            setText(prefs.getString("name_$n", "") ?: "")
            hint = "HDMI $n"
            setHintTextColor(0x66FFFFFF)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, u(18f).toFloat())
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_DONE
            filters = arrayOf(InputFilter.LengthFilter(24))
            setPadding(u(18f), u(12f), u(18f), u(12f))
            background = GradientDrawable().apply {
                cornerRadius = u(16f).toFloat()
                setColor(0x22FFFFFF)
                setStroke(max(2, u(1.5f)), 0x66FFFFFF)
            }
            setSelection(text.length)
        }
        panel.addView(edit, LinearLayout.LayoutParams(MATCH, WRAP))

        val save = {
            val t = edit.text.toString().trim()
            val e = prefs.edit()
            if (t.isEmpty()) e.remove("name_$n") else e.putString("name_$n", t)
            e.apply()
            dlg.dismiss()
            refreshInputs(n)
        }
        edit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { save(); true } else false
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, u(18f), 0, 0)
        }
        buttons.addView(pill("Cancel", false) { dlg.dismiss() },
            LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = u(12f) })
        buttons.addView(pill("Save", true) { save() })
        panel.addView(buttons, LinearLayout.LayoutParams(MATCH, WRAP))

        dlg.setContentView(panel)
        dlg.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(u(520f), WindowManager.LayoutParams.WRAP_CONTENT)
            setDimAmount(0.65f)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        }
        dlg.show()
        edit.postDelayed({
            edit.requestFocus()
            try {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT)
            } catch (e: Exception) { }
        }, 250L)
    }

    private fun pill(text: String, primary: Boolean, onClick: () -> Unit): TextView {
        val t = tv(text, 15f, Color.WHITE, true)
        fun paint(f: Boolean) {
            t.background = GradientDrawable().apply {
                cornerRadius = u(30f).toFloat()
                setColor(if (f) Color.WHITE else if (primary) 0xFF0A84FF.toInt() else 0x33FFFFFF)
            }
            t.setTextColor(if (f) Color.BLACK else Color.WHITE)
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

    // ---------- shared UI builders ----------

    private fun tv(text: String, sizeU: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_PX, u(sizeU).toFloat())
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun row(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        clipChildren = false
        clipToPadding = false
    }

    private fun sectionHeader(title: String, hint: String?): View {
        val l = LinearLayout(this).apply {
            gravity = Gravity.BOTTOM
            setPadding(u(6f), u(18f), u(6f), u(12f))
        }
        l.addView(tv(title, 19f, Color.WHITE, true), LinearLayout.LayoutParams(0, WRAP, 1f))
        if (hint != null) l.addView(tv(hint, 12f, 0x80FFFFFF.toInt()))
        return l
    }

    private fun makeFrame(bg: Drawable): FrameLayout = FrameLayout(this).apply {
        background = bg
        outlineProvider = ViewOutlineProvider.BACKGROUND
        clipToOutline = true
        elevation = u(3f).toFloat()
        if (Build.VERSION.SDK_INT >= 28) {
            outlineSpotShadowColor = Color.BLACK
            outlineAmbientShadowColor = Color.BLACK
        }
    }

    private fun overlays(frame: FrameLayout): Pair<View, View> {
        val sheen = View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0x45FFFFFF, 0x00FFFFFF, 0x12FFFFFF))
            alpha = 0f
        }
        val border = View(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(Color.TRANSPARENT)
                setStroke(max(2, u(2.4f)), Color.WHITE)
            }
            alpha = 0f
        }
        frame.addView(sheen, MATCH, MATCH)
        frame.addView(border, MATCH, MATCH)
        return Pair(border, sheen)
    }

    private fun cardBox(frame: View, label: View?): LinearLayout {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.isFocusable = true
        box.isClickable = true
        box.clipChildren = false
        box.cameraDistance = 12000f * resources.displayMetrics.density
        box.pivotX = cardW / 2f
        box.pivotY = cardH / 2f
        if (Build.VERSION.SDK_INT >= 26) box.setDefaultFocusHighlightEnabled(false)
        box.addView(frame, LinearLayout.LayoutParams(cardW, cardH))
        if (label != null) box.addView(label, LinearLayout.LayoutParams(cardW, WRAP))
        return box
    }

    /** tvOS-style focus: scale 1.10, slight tilt + lift, white border, specular sheen, deep shadow. */
    private fun focusFx(box: View, frame: View, border: View, sheen: View, label: View?) {
        box.setOnFocusChangeListener { v, f ->
            val s = if (f) 1.10f else 1f
            v.animate().scaleX(s).scaleY(s)
                .rotationX(if (f) 3f else 0f)
                .translationY(if (f) -u(5f).toFloat() else 0f)
                .setDuration(180).setInterpolator(DecelerateInterpolator()).start()
            frame.animate().translationZ(if (f) u(26f).toFloat() else 0f).setDuration(180).start()
            border.animate().alpha(if (f) 1f else 0f).setDuration(180).start()
            sheen.animate().alpha(if (f) 1f else 0f).setDuration(180).start()
            label?.animate()?.alpha(if (f) 1f else 0.5f)?.setDuration(180)?.start()
        }
    }

    // ---------- colors ----------

    private fun shade(c: Int, f: Float): Int {
        val r = min(255, (Color.red(c) * f).toInt())
        val g = min(255, (Color.green(c) * f).toInt())
        val b = min(255, (Color.blue(c) * f).toInt())
        return Color.rgb(r, g, b)
    }

    private fun cardBackground(color: Int): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(shade(color, 1.2f), shade(color, 0.6f))
        ).apply { cornerRadius = dp(22).toFloat() }

    private fun cardColor(d: Drawable?, key: String): Int {
        val fallback = palette[(key.hashCode() and 0x7fffffff) % palette.size]
        if (d == null) return fallback
        return try {
            val s = 24
            val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            d.setBounds(0, 0, s, s)
            d.draw(c)
            var r = 0L; var g = 0L; var b = 0L; var n = 0L
            for (y in 0 until s) for (x in 0 until s) {
                val p = bmp.getPixel(x, y)
                if (Color.alpha(p) > 200) {
                    r += Color.red(p); g += Color.green(p); b += Color.blue(p); n++
                }
            }
            bmp.recycle()
            if (n == 0L) return fallback
            val hsv = FloatArray(3)
            Color.RGBToHSV((r / n).toInt(), (g / n).toInt(), (b / n).toInt(), hsv)
            if (hsv[1] < 0.18f) fallback else {
                hsv[1] = min(1f, hsv[1] * 1.15f + 0.05f)
                hsv[2] = hsv[2].coerceIn(0.55f, 0.85f)
                Color.HSVToColor(hsv)
            }
        } catch (e: Exception) { fallback }
    }
}
