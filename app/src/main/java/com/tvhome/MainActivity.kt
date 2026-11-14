package com.tvhome

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.min

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
// How many HDMI cards to always show
private const val HDMI_COUNT = 3

// ======================= Custom views (all cheap to draw) =======================

/** Static dark cinematic background (drawn once, never animated: keeps D-pad smooth). */
class StaticBackground(ctx: Context) : View(ctx) {
    private val base = Paint().apply { color = 0xFF05070D.toInt() }
    private val blobs = ArrayList<Pair<Paint, FloatArray>>()

    private fun blob(color: Int, alpha: Int, r: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, r, intArrayOf(
            Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)),
            Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))
        ), null, Shader.TileMode.CLAMP)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        blobs.clear()
        val m = max(w, h).toFloat()
        blobs.add(Pair(blob(0xFF1E40AF.toInt(), 150, m * 0.7f), floatArrayOf(w * 0.12f, h * 0.05f, m * 0.7f)))
        blobs.add(Pair(blob(0xFF6D28D9.toInt(), 105, m * 0.55f), floatArrayOf(w * 0.92f, h * 0.30f, m * 0.55f)))
        blobs.add(Pair(blob(0xFF0F766E.toInt(), 100, m * 0.65f), floatArrayOf(w * 0.50f, h * 1.00f, m * 0.65f)))
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), base)
        for ((p, d) in blobs) {
            canvas.save(); canvas.translate(d[0], d[1]); canvas.drawCircle(0f, 0f, d[2], p); canvas.restore()
        }
    }
}

/** Rounded bitmap without clipToOutline (BitmapShader is much faster on weak TV GPUs). */
class RoundedImageView(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val rect = RectF()
    var radius = 0f
    private var bmp: Bitmap? = null

    fun setBitmap(b: Bitmap?) { bmp = b; rebuild(); invalidate() }

    private fun rebuild() {
        val b = bmp
        if (b == null || width == 0 || height == 0) { if (b == null) paint.shader = null; return }
        val shader = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val sc = max(width.toFloat() / b.width, height.toFloat() / b.height)
        val m = Matrix()
        m.setScale(sc, sc)
        m.postTranslate((width - b.width * sc) / 2f, (height - b.height * sc) / 2f)
        shader.setLocalMatrix(m)
        paint.shader = shader
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { super.onSizeChanged(w, h, ow, oh); rebuild() }

    override fun onDraw(canvas: Canvas) {
        if (paint.shader == null) return
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, radius, radius, paint)
    }
}

class WifiView(ctx: Context) : View(ctx) {
    var on: Boolean = true
        set(v) { if (field != v) { field = v; invalidate() } }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()
    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val color = if (on) Color.WHITE else 0x55FFFFFF
        p.color = color; dot.color = color; p.strokeWidth = h * 0.10f
        val cx = w / 2f; val cy = h * 0.92f
        for (i in 1..3) {
            val r = h * 0.30f * i
            rect.set(cx - r, cy - r, cx + r, cy + r)
            canvas.drawArc(rect, 225f, 90f, false, p)
        }
        canvas.drawCircle(cx, cy, h * 0.07f, dot)
    }
}

class GearView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFFE6E9F0.toInt() }
    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val r = min(width, height) / 2f
        p.strokeCap = Paint.Cap.BUTT
        p.strokeWidth = r * 0.26f
        canvas.drawCircle(cx, cy, r * 0.52f, p)
        p.strokeWidth = r * 0.30f
        for (i in 0 until 8) {
            canvas.save(); canvas.rotate(i * 45f, cx, cy)
            canvas.drawLine(cx, cy - r * 0.62f, cx, cy - r * 0.95f, p)
            canvas.restore()
        }
    }
}

class HdmiPortView(ctx: Context) : View(ctx) {
    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cavity = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0E1016.toInt() }
    private val pin = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC9CED8.toInt() }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x99FFFFFF.toInt() }
    private val outer = Path()
    private val inner = Path()

    private fun shape(p: Path, l: Float, t: Float, r: Float, b: Float) {
        val w = r - l; val h = b - t
        p.reset()
        p.moveTo(l, t); p.lineTo(r, t); p.lineTo(r, t + h * 0.52f)
        p.lineTo(r - w * 0.10f, b); p.lineTo(l + w * 0.10f, b); p.lineTo(l, t + h * 0.52f)
        p.close()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        body.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
            intArrayOf(0xFFF2F4F8.toInt(), 0xFF9AA2B1.toInt(), 0xFFDDE1E8.toInt(), 0xFF6E7687.toInt()),
            floatArrayOf(0f, 0.35f, 0.6f, 1f), Shader.TileMode.CLAMP)
        rim.strokeWidth = max(1f, h * 0.03f)
        shape(outer, 0f, 0f, w.toFloat(), h.toFloat())
        val i = h * 0.14f
        shape(inner, i * 1.2f, i, w - i * 1.2f, h - i * 0.9f)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawPath(outer, body); canvas.drawPath(outer, rim); canvas.drawPath(inner, cavity)
        val n = 9
        val pw = width * 0.05f; val ph = height * 0.12f
        for (k in 0 until n) {
            val x = width * 0.18f + k * (width * 0.64f / (n - 1)) - pw / 2f
            canvas.drawRoundRect(x, height * 0.34f, x + pw, height * 0.34f + ph, 2f, 2f, pin)
        }
    }
}

/** Tiny weather glyph: 0 sun, 1 moon, 2 cloud, 3 rain, 4 storm */
class WeatherView(ctx: Context) : View(ctx) {
    var kind: Int = 2
        set(v) { field = v; invalidate() }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val path = Path()
    private val path2 = Path()

    private fun cloud(c: Canvas, w: Float, h: Float) {
        fill.color = 0xFFE6E9F0.toInt()
        c.drawCircle(w * 0.34f, h * 0.52f, h * 0.20f, fill)
        c.drawCircle(w * 0.52f, h * 0.40f, h * 0.26f, fill)
        c.drawCircle(w * 0.72f, h * 0.52f, h * 0.20f, fill)
        c.drawRoundRect(w * 0.18f, h * 0.48f, w * 0.88f, h * 0.72f, h * 0.12f, h * 0.12f, fill)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        when (kind) {
            0 -> {
                fill.color = 0xFFFFC83D.toInt(); line.color = 0xFFFFC83D.toInt(); line.strokeWidth = h * 0.07f
                c.drawCircle(w / 2f, h / 2f, h * 0.20f, fill)
                for (i in 0 until 8) {
                    c.save(); c.rotate(i * 45f, w / 2f, h / 2f)
                    c.drawLine(w / 2f, h * 0.04f, w / 2f, h * 0.15f, line)
                    c.restore()
                }
            }
            1 -> {
                path.reset(); path.addCircle(w / 2f, h / 2f, h * 0.32f, Path.Direction.CW)
                path2.reset(); path2.addCircle(w * 0.66f, h * 0.38f, h * 0.27f, Path.Direction.CW)
                path.op(path2, Path.Op.DIFFERENCE)
                fill.color = 0xFFE6E9F0.toInt()
                c.drawPath(path, fill)
            }
            3 -> {
                cloud(c, w, h)
                line.color = 0xFF6CB6FF.toInt(); line.strokeWidth = h * 0.07f
                c.drawLine(w * 0.36f, h * 0.80f, w * 0.32f, h * 0.95f, line)
                c.drawLine(w * 0.54f, h * 0.80f, w * 0.50f, h * 0.95f, line)
                c.drawLine(w * 0.72f, h * 0.80f, w * 0.68f, h * 0.95f, line)
            }
            4 -> {
                cloud(c, w, h)
                fill.color = 0xFFFFC83D.toInt()
                path.reset()
                path.moveTo(w * 0.54f, h * 0.68f); path.lineTo(w * 0.42f, h * 0.88f)
                path.lineTo(w * 0.52f, h * 0.88f); path.lineTo(w * 0.46f, h * 1.00f)
                path.lineTo(w * 0.64f, h * 0.80f); path.lineTo(w * 0.54f, h * 0.80f)
                path.close()
                c.drawPath(path, fill)
            }
            else -> cloud(c, w, h)
        }
    }
}

// ======================= Activity =======================

class MainActivity : Activity() {

    private class AppEntry(val pkg: String, val cls: String, val label: String, val ri: ResolveInfo)

    /** kind: 0 = app, 1 = HDMI input, 2 = settings */
    private class Tile(
        val kind: Int, val title: String, val subtitle: String, val color: Int,
        val art: Bitmap?, val pkg: String = "", val cls: String = "", val n: Int = 0
    )

    private val handler = Handler(Looper.getMainLooper())
    private var W = 1000
    private var H = 600
    private var cardW = 0
    private var cardH = 0
    private var gap = 0
    private var padH = 0

    private lateinit var shelves: ScrollView
    private lateinit var favRow: LinearLayout
    private lateinit var inputsRow: LinearLayout
    private lateinit var appsRow: LinearLayout
    private lateinit var tint: View
    private lateinit var heroTitle: TextView
    private lateinit var heroSub: TextView
    private lateinit var heroFrame: FrameLayout
    private lateinit var heroImg: RoundedImageView
    private lateinit var heroPort: HdmiPortView
    private lateinit var heroGear: GearView
    private lateinit var weatherText: TextView
    private lateinit var weatherIcon: WeatherView
    private lateinit var wifi: WifiView
    private lateinit var prefs: SharedPreferences
    private var tim: TvInputManager? = null
    private var lastSig = ""
    @Volatile private var loading = false
    private var lastWeather = 0L
    private var pendingTile: Tile? = null

    private val palette = intArrayOf(
        0xFFE50914.toInt(), 0xFF0A84FF.toInt(), 0xFF8E44AD.toInt(), 0xFF16A085.toInt(),
        0xFFE67E22.toInt(), 0xFF2E86DE.toInt(), 0xFFD63384.toInt(), 0xFF27AE60.toInt()
    )

    private val heroRunnable = Runnable { pendingTile?.let { applyHero(it) } }

    private val ticker = object : Runnable {
        override fun run() {
            val online = isOnline()
            wifi.on = online
            if (online && SystemClock.elapsedRealtime() - lastWeather > 15 * 60 * 1000L) {
                lastWeather = SystemClock.elapsedRealtime()
                fetchWeather()
            }
            handler.postDelayed(this, 5000L)
        }
    }

    private fun u(f: Float): Int = (f * W / 1000f).toInt()
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ---------- lifecycle ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dm = resources.displayMetrics
        W = dm.widthPixels
        H = dm.heightPixels
        prefs = getSharedPreferences("tvhome", MODE_PRIVATE)
        tim = getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager

        gap = u(16f)
        padH = u(46f)
        cardW = (W * 0.19f).toInt()
        cardH = cardW * 9 / 16

        val root = FrameLayout(this)
        root.addView(StaticBackground(this), MATCH, MATCH)
        tint = View(this)
        root.addView(tint, FrameLayout.LayoutParams(MATCH, (H * 0.62f).toInt()))

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(buildTopBar(), LinearLayout.LayoutParams(MATCH, WRAP))
        col.addView(buildHero(), LinearLayout.LayoutParams(MATCH, (H * 0.30f).toInt()))

        favRow = row(); inputsRow = row(); appsRow = row()
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
        content.addView(header("Favorites")); content.addView(shelf(favRow))
        content.addView(header("Inputs")); content.addView(shelf(inputsRow))
        content.addView(header("All Apps")); content.addView(shelf(appsRow))
        shelves = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipChildren = false
            clipToPadding = false
            addView(content, MATCH, WRAP)
        }
        col.addView(shelves, LinearLayout.LayoutParams(MATCH, 0, 1f))
        root.addView(col, MATCH, MATCH)
        setContentView(root)
        applyTint(0xFF1E3A8A.toInt())
    }

    override fun onResume() {
        super.onResume()
        refreshApps(false)
        refreshInputs(0)
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(heroRunnable)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        shelves.scrollTo(0, 0)
        favRow.getChildAt(0)?.requestFocus()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() { /* launcher: stay home */ }

    // ---------- top bar + hero ----------

    private fun buildTopBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(padH, u(22f), padH, 0)
        }
        val left = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        weatherIcon = WeatherView(this)
        left.addView(weatherIcon, LinearLayout.LayoutParams(u(34f), u(34f)).apply { rightMargin = u(10f) })
        weatherText = tv("Riyadh", 20f, 0xE6FFFFFF.toInt(), true)
        left.addView(weatherText)
        bar.addView(left, LinearLayout.LayoutParams(0, WRAP, 1f))

        val right = LinearLayout(this).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        wifi = WifiView(this)
        right.addView(wifi, LinearLayout.LayoutParams(u(26f), u(21f)).apply { rightMargin = u(20f) })
        right.addView(TextClock(this).apply {
            format12Hour = "h:mm a"; format24Hour = "HH:mm"
            setTextSize(TypedValue.COMPLEX_UNIT_PX, u(22f).toFloat())
            setTextColor(0xE6FFFFFF.toInt())
        })
        bar.addView(right, LinearLayout.LayoutParams(0, WRAP, 1f))
        return bar
    }

    private fun buildHero(): View {
        val hero = FrameLayout(this).apply { setPadding(padH, 0, padH, 0); clipChildren = false }

        val text = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heroTitle = tv("", 46f, Color.WHITE, true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; maxWidth = (W * 0.46f).toInt() }
        heroSub = tv("", 18f, 0xB3FFFFFF.toInt()).apply { setPadding(0, u(6f), 0, 0) }
        text.addView(heroTitle); text.addView(heroSub)
        hero.addView(text, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.START).apply { bottomMargin = u(26f) })

        val hh = (H * 0.30f * 0.84f).toInt()
        val hw = hh * 16 / 9
        heroFrame = FrameLayout(this)
        heroImg = RoundedImageView(this).apply { radius = dp(22).toFloat() }
        heroFrame.addView(heroImg, FrameLayout.LayoutParams(MATCH, MATCH))
        heroPort = HdmiPortView(this)
        heroFrame.addView(heroPort, FrameLayout.LayoutParams((hw * 0.34f).toInt(), (hw * 0.34f * 0.46f).toInt(), Gravity.CENTER))
        heroGear = GearView(this)
        heroFrame.addView(heroGear, FrameLayout.LayoutParams((hh * 0.5f).toInt(), (hh * 0.5f).toInt(), Gravity.CENTER))
        hero.addView(heroFrame, FrameLayout.LayoutParams(hw, hh, Gravity.END or Gravity.CENTER_VERTICAL))
        return hero
    }

    private fun applyHero(t: Tile) {
        heroTitle.text = t.title
        heroSub.text = t.subtitle
        heroImg.visibility = if (t.kind == 0) View.VISIBLE else View.GONE
        heroPort.visibility = if (t.kind == 1) View.VISIBLE else View.GONE
        heroGear.visibility = if (t.kind == 2) View.VISIBLE else View.GONE
        if (t.kind == 0) {
            heroImg.setBitmap(t.art)
            heroFrame.background = null
        } else {
            heroFrame.background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(shade(t.color, 1.3f), shade(t.color, 0.7f))).apply { cornerRadius = dp(22).toFloat() }
        }
        applyTint(t.color)
    }

    private fun applyTint(color: Int) {
        val c = shade(color, 0.9f)
        tint.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(0x8C, Color.red(c), Color.green(c), Color.blue(c)), 0x00000000))
    }

    private fun onTileFocused(t: Tile) {
        pendingTile = t
        handler.removeCallbacks(heroRunnable)
        handler.postDelayed(heroRunnable, 45L)
    }

    // ---------- weather (Open-Meteo, no key) ----------

    private fun fetchWeather() {
        Thread {
            try {
                val url = URL("https://api.open-meteo.com/v1/forecast?latitude=24.7136&longitude=46.6753&current=temperature_2m,weather_code,is_day&timezone=auto")
                val c = url.openConnection() as HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 8000
                val body = c.inputStream.bufferedReader().use { it.readText() }
                c.disconnect()
                val cur = JSONObject(body).getJSONObject("current")
                val temp = Math.round(cur.getDouble("temperature_2m")).toInt()
                val code = cur.getInt("weather_code")
                val day = cur.optInt("is_day", 1) == 1
                runOnUiThread {
                    weatherText.text = "Riyadh   $temp°   ${describe(code)}"
                    weatherIcon.kind = when (code) {
                        0, 1 -> if (day) 0 else 1
                        in 51..67, in 80..82 -> 3
                        in 95..99 -> 4
                        else -> 2
                    }
                }
            } catch (e: Exception) {
                lastWeather = SystemClock.elapsedRealtime() - 14 * 60 * 1000L // retry in ~1 min
            }
        }.start()
    }

    private fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67, in 80..82 -> "Rain"
        in 71..77, 85, 86 -> "Snow"
        in 95..99 -> "Thunderstorm"
        else -> ""
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
        val leanPkgs = HashSet<String>()
        val out = ArrayList<AppEntry>()
        for (ri in leanback) {
            val p = ri.activityInfo.packageName
            if (p == packageName || !seen.add(p + "/" + ri.activityInfo.name)) continue
            leanPkgs.add(p)
            out.add(AppEntry(p, ri.activityInfo.name, ri.loadLabel(pm).toString(), ri))
        }
        for (ri in launcher) {
            val p = ri.activityInfo.packageName
            if (p == packageName || leanPkgs.contains(p)) continue
            if (!seen.add(p + "/" + ri.activityInfo.name)) continue
            out.add(AppEntry(p, ri.activityInfo.name, ri.loadLabel(pm).toString(), ri))
        }
        return out.sortedBy { it.label.lowercase() }
    }

    /** Heavy work (icons, bitmaps) happens off the UI thread so startup never stutters. */
    private fun refreshApps(force: Boolean) {
        if (loading) return
        loading = true
        Thread {
            try {
                val apps = loadApps()
                val sig = apps.joinToString("|") { it.pkg + "/" + it.cls }
                if (force || sig != lastSig) {
                    val tiles = apps.map { buildAppTile(it) }
                    runOnUiThread { lastSig = sig; populateApps(tiles) }
                }
            } catch (e: Exception) { /* ignore */ }
            loading = false
        }.start()
    }

    private fun buildAppTile(a: AppEntry): Tile {
        val pm = packageManager
        val banner: Drawable? = try { a.ri.activityInfo.loadBanner(pm) } catch (e: Exception) { null }
        val icon: Drawable? = try { a.ri.loadIcon(pm) } catch (e: Exception) { null }
        val color = cardColor(banner ?: icon, a.pkg)
        val art = composeArt(banner, icon, color, (cardW * 1.25f).toInt(), (cardH * 1.25f).toInt())
        return Tile(0, a.label, "Press OK to open", color, art, a.pkg, a.cls)
    }

    private fun composeArt(banner: Drawable?, icon: Drawable?, color: Int, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (banner != null) {
            banner.setBounds(0, 0, w, h); banner.draw(c)
        } else {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
                shade(color, 1.2f), shade(color, 0.6f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            if (icon != null) {
                val s = (h * 0.58f).toInt()
                val l = (w - s) / 2; val t = (h - s) / 2
                icon.setBounds(l, t, l + s, t + s); icon.draw(c)
            }
        }
        return bmp
    }

    private fun pickFavorites(tiles: List<Tile>): List<Tile> {
        val keys = listOf("netflix", "youtube", "prime", "disney", "shahid", "spotify", "hbo", "apple", "browser", "multimedia")
        val out = ArrayList<Tile>()
        for (k in keys) {
            val m = tiles.firstOrNull { !out.contains(it) && (it.title.lowercase().contains(k) || it.pkg.lowercase().contains(k)) }
            if (m != null) out.add(m)
            if (out.size == 6) return out
        }
        for (t in tiles) { if (!out.contains(t)) out.add(t); if (out.size == 6) break }
        return out
    }

    private fun populateApps(tiles: List<Tile>) {
        favRow.removeAllViews(); appsRow.removeAllViews()
        val favs = pickFavorites(tiles)
        favs.forEachIndexed { i, t -> favRow.addView(buildCard(t, i < favs.size - 1)) }
        tiles.forEachIndexed { i, t -> appsRow.addView(buildCard(t, i < tiles.size - 1)) }
        if (currentFocus == null) favRow.getChildAt(0)?.requestFocus()
    }

    private fun launchApp(t: Tile) {
        try {
            startActivity(Intent(Intent.ACTION_MAIN).setClassName(t.pkg, t.cls)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        } catch (e: Exception) {
            try {
                val i = packageManager.getLaunchIntentForPackage(t.pkg)
                if (i != null) startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                else Toast.makeText(this, "Can't open ${t.title}", Toast.LENGTH_SHORT).show()
            } catch (e2: Exception) {
                Toast.makeText(this, "Can't open ${t.title}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---------- HDMI ----------

    private fun hdmiName(n: Int): String =
        prefs.getString("name_$n", null)?.takeIf { it.isNotBlank() } ?: "HDMI $n"

    private fun hwNumber(id: String): Int =
        Regex("HW(\\d+)$").find(id)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE

    /** Finds the TV's real input id for physical HDMI port n (falls back to the MediaTek default id). */
    private fun inputIdFor(n: Int): String {
        try {
            val list = tim?.tvInputList.orEmpty()
                .filter { it.type == TvInputInfo.TYPE_HDMI && it.isPassthroughInput && it.parentId == null }
                .sortedBy { hwNumber(it.id) }
            if (list.size >= n) return list[n - 1].id
        } catch (e: Exception) { /* use fallback */ }
        return "com.mediatek.tvinput/.hdmi.HDMIInputService/HW$n"
    }

    private fun isInputLive(n: Int): Boolean {
        return try {
            tim?.getInputState(inputIdFor(n)) == TvInputManager.INPUT_STATE_CONNECTED
        } catch (e: Exception) { false }
    }

    private fun refreshInputs(focusN: Int) {
        inputsRow.removeAllViews()
        for (n in 1..HDMI_COUNT) {
            val id = inputIdFor(n)
            inputsRow.addView(buildCard(
                Tile(1, hdmiName(n), "HDMI $n   ·   Press OK to switch, hold OK to rename",
                    0xFF2C3550.toInt(), null, id, "", n), true))
        }
        inputsRow.addView(buildCard(Tile(2, "Settings", "System settings", 0xFF3B4254.toInt(), null), false))
        if (focusN in 1..HDMI_COUNT) inputsRow.getChildAt(focusN - 1)?.requestFocus()
    }

    private fun openHdmi(t: Tile) {
        try {
            startActivity(Intent(this, HdmiActivity::class.java)
                .putExtra("input_id", t.pkg)
                .putExtra("name", t.title)
                .putExtra("n", t.n))
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open ${t.title}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openSettings() {
        val attempts = ArrayList<Intent>()
        attempts.add(Intent(android.provider.Settings.ACTION_SETTINGS))
        attempts.add(Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.MainSettings"))
        for (pkg in listOf("com.android.tv.settings", "com.android.settings", "com.mediatek.tvsettings")) {
            packageManager.getLaunchIntentForPackage(pkg)?.let { attempts.add(it) }
        }
        for (i in attempts) {
            try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); return } catch (e: Exception) { }
        }
        Toast.makeText(this, "Couldn't open Settings on this TV.", Toast.LENGTH_LONG).show()
    }

    // ---------- cards ----------

    private fun roundOutline(r: Float) = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, r)
        }
    }

    private fun buildCard(t: Tile, notLast: Boolean): View {
        val r = dp(20).toFloat()
        val frame = FrameLayout(this)
        frame.outlineProvider = roundOutline(r)

        when (t.kind) {
            0 -> {
                val iv = RoundedImageView(this)
                iv.radius = r
                iv.setBitmap(t.art)
                frame.addView(iv, FrameLayout.LayoutParams(MATCH, MATCH))
            }
            1 -> {
                frame.background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    intArrayOf(0xFF454E67.toInt(), 0xFF1A1F2E.toInt())).apply { cornerRadius = r }
                val pw = (cardW * 0.40f).toInt()
                frame.addView(HdmiPortView(this),
                    FrameLayout.LayoutParams(pw, (pw * 0.46f).toInt(), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                        topMargin = (cardH * 0.26f).toInt()
                    })
                frame.addView(tv(t.title, 15f, Color.WHITE, true).apply {
                    maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER
                }, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM).apply {
                    bottomMargin = u(10f); leftMargin = u(8f); rightMargin = u(8f)
                })
                val live = isInputLive(t.n)
                val badge = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(u(8f), u(4f), u(10f), u(4f))
                    background = GradientDrawable().apply { cornerRadius = u(14f).toFloat(); setColor(0x59000000) }
                }
                badge.addView(View(this).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(if (live) 0xFF34C759.toInt() else 0xFF6B7280.toInt())
                    }
                }, LinearLayout.LayoutParams(u(7f), u(7f)).apply { rightMargin = u(6f) })
                badge.addView(tv("PORT ${t.n}", 10f, 0xCCFFFFFF.toInt(), true))
                frame.addView(badge, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START).apply {
                    leftMargin = u(9f); topMargin = u(9f)
                })
            }
            else -> {
                frame.background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    intArrayOf(0xFF4A4F5E.toInt(), 0xFF1E2230.toInt())).apply { cornerRadius = r }
                val gs = (cardH * 0.42f).toInt()
                frame.addView(GearView(this),
                    FrameLayout.LayoutParams(gs, gs, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                        topMargin = (cardH * 0.16f).toInt()
                    })
                frame.addView(tv("Settings", 15f, Color.WHITE, true).apply { gravity = Gravity.CENTER },
                    FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM).apply { bottomMargin = u(10f) })
            }
        }

        // glass highlight on the top half (rounded top corners only, so no clipping is needed)
        frame.addView(View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x30FFFFFF, 0x00FFFFFF)).apply { cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f) }
        }, FrameLayout.LayoutParams(MATCH, (cardH * 0.5f).toInt(), Gravity.TOP))

        val border = View(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = r; setColor(Color.TRANSPARENT); setStroke(max(2, u(2.4f)), Color.WHITE)
            }
            alpha = 0.10f
        }
        frame.addView(border, FrameLayout.LayoutParams(MATCH, MATCH))

        val label = tv(t.title, 16f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            alpha = 0f; setPadding(0, u(12f), 0, 0)
        }

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.isFocusable = true
        box.isClickable = true
        box.clipChildren = false
        box.alpha = 0.62f
        box.pivotX = cardW / 2f
        box.pivotY = cardH / 2f
        if (Build.VERSION.SDK_INT >= 26) box.setDefaultFocusHighlightEnabled(false)
        box.addView(frame, LinearLayout.LayoutParams(cardW, cardH))
        box.addView(label, LinearLayout.LayoutParams(cardW, WRAP))
        box.layoutParams = LinearLayout.LayoutParams(cardW, WRAP).apply { rightMargin = if (notLast) gap else 0 }

        box.setOnClickListener {
            when (t.kind) { 0 -> launchApp(t); 1 -> openHdmi(t); else -> openSettings() }
        }
        if (t.kind == 1) {
            box.isLongClickable = true
            box.setOnLongClickListener { showRenameDialog(t.n); true }
        }
        // light focus effect: scale + brightness + shadow on the focused card only (fast on weak GPUs)
        box.setOnFocusChangeListener { v, f ->
            val s = if (f) 1.10f else 1f
            v.animate().scaleX(s).scaleY(s).alpha(if (f) 1f else 0.62f)
                .setDuration(120).setInterpolator(DecelerateInterpolator()).start()
            frame.animate().translationZ(if (f) u(20f).toFloat() else 0f).setDuration(120).start()
            border.alpha = if (f) 1f else 0.10f
            label.alpha = if (f && t.kind == 0) 1f else 0f
            if (f) onTileFocused(t)
        }
        return box
    }

    // ---------- rename dialog ----------

    private fun showRenameDialog(n: Int) {
        val dlg = Dialog(this)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(u(28f), u(24f), u(28f), u(24f))
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat(); setColor(0xF01C1C24.toInt()); setStroke(max(1, u(1f)), 0x33FFFFFF)
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
                cornerRadius = u(16f).toFloat(); setColor(0x22FFFFFF); setStroke(max(2, u(1.5f)), 0x66FFFFFF)
            }
            setSelection(text.length)
        }
        panel.addView(edit, LinearLayout.LayoutParams(MATCH, WRAP))
        val save = {
            val txt = edit.text.toString().trim()
            val e = prefs.edit()
            if (txt.isEmpty()) e.remove("name_$n") else e.putString("name_$n", txt)
            e.apply()
            dlg.dismiss()
            refreshInputs(n)
        }
        edit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { save(); true } else false
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END; setPadding(0, u(18f), 0, 0)
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

    // ---------- small builders ----------

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

    private fun header(title: String): View = tv(title, 24f, Color.WHITE, true).apply {
        setPadding(padH, u(16f), padH, 0)
    }

    private fun shelf(row: LinearLayout): HorizontalScrollView = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        clipChildren = false
        clipToPadding = false
        setPadding(padH, u(16f), padH, u(30f))
        addView(row)
    }

    // ---------- colors ----------

    private fun shade(c: Int, f: Float): Int {
        val r = min(255, (Color.red(c) * f).toInt())
        val g = min(255, (Color.green(c) * f).toInt())
        val b = min(255, (Color.blue(c) * f).toInt())
        return Color.rgb(r, g, b)
    }

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
                if (Color.alpha(p) > 200) { r += Color.red(p); g += Color.green(p); b += Color.blue(p); n++ }
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
