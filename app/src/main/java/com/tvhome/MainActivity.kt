package com.tvhome

import android.app.Activity
import android.app.Dialog
import android.app.DownloadManager
import android.app.PendingIntent
import android.net.Uri
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
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
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.math.max
import kotlin.math.min

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
private const val HDMI_COUNT = 3
private const val REPO = "zezo20234/tv"
private const val ACTION_INSTALL = "com.tvhome.app.INSTALL_RESULT"

private val permissiveSslContext by lazy {
    val tm = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
    val ctx = SSLContext.getInstance("TLS")
    ctx.init(null, arrayOf(tm), java.security.SecureRandom())
    ctx
}

private val permissiveHostnameVerifier = javax.net.ssl.HostnameVerifier { hostname: String, session: javax.net.ssl.SSLSession -> true }

// Scenic Design Color System
object ScenicColors {
    const val GLASS_LIGHT = 0x33FFFFFF.toInt()
    const val GLASS_DARK = 0x22FFFFFF.toInt()
    const val GLASS_DOCK = 0x4DFFFFFF.toInt()
    const val TEXT_PRIMARY = 0xFFFFFFFF.toInt()
    const val TEXT_SECONDARY = 0xCCFFFFFF.toInt()
    const val TEXT_TERTIARY = 0x99FFFFFF.toInt()
    const val BORDER_LIGHT = 0x66FFFFFF.toInt()
    const val BORDER_FOCUS = 0xFFFFFFFF.toInt()
    const val SHADOW = 0x33000000.toInt()
}

enum class ColorMode(val displayName: String, val colors: List<Int>) {
    SCENIC("Scenic Default", listOf(0xFF4A90D9.toInt(), 0xFF50C878.toInt(), 0xFFE6BE8A.toInt(), 0xFFD4A5A5.toInt())),
    SUNSET("Sunset Glow", listOf(0xFFFF6B6B.toInt(), 0xFFFeca57.toInt(), 0xFFFF9FF3.toInt(), 0xFF54A0FF.toInt())),
    FOREST("Forest Calm", listOf(0xFF2ECC71.toInt(), 0xFF3498DB.toInt(), 0xFF1ABC9C.toInt(), 0xFF16A085.toInt())),
    OCEAN("Ocean Blue", listOf(0xFF0984E3.toInt(), 0xFF74B9FF.toInt(), 0xFFA29BFE.toInt(), 0xFF6C5CE7.toInt()))
}

/** Scenic wallpaper background with subtle gradient overlay */
class ScenicBackground(ctx: Context) : View(ctx) {
    private var bmp: Bitmap? = null
    private val dst = Rect()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var colorMode: ColorMode = ColorMode.SCENIC

    fun setColorMode(mode: ColorMode) {
        if (colorMode != mode) {
            colorMode = mode
            bmp = null
            requestLayout()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        val bw = max(8, w / 4)
        val bh = max(8, h / 4)
        val b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)

        // Create scenic gradient background
        val colors = colorMode.colors
        val gradient = LinearGradient(0f, 0f, 0f, bh.toFloat(),
            intArrayOf(colors[0], colors[1], colors[2]), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawColor(colors[0])
        c.drawRect(0f, 0f, bw.toFloat(), bh.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = gradient })

        // Add subtle vignette
        val vignette = RadialGradient(bw.toFloat() / 2f, bh.toFloat() / 2f, max(bw, bh).toFloat() * 0.7f,
            Color.argb(0, 0, 0, 0), Color.argb(80, 0, 0, 0), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, bw.toFloat(), bh.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = vignette })

        bmp = b
    }

    override fun onDraw(canvas: Canvas) {
        val b = bmp ?: return
        dst.set(0, 0, width, height)
        canvas.drawBitmap(b, null, dst, paint)
    }
}

/** Glass card with subtle border and shadow */
class GlassCard(ctx: Context, private val radius: Float) : View(ctx) {
    private val rect = RectF()
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    override fun onBoundsChange(b: Rect) {
        super.onBoundsChange(b)
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        if (w <= 0f || h <= 0f) return

        bgPaint.shader = LinearGradient(0f, 0f, 0f, h, ScenicColors.GLASS_LIGHT, ScenicColors.GLASS_DARK, Shader.TileMode.CLAMP)
        borderPaint.color = ScenicColors.BORDER_LIGHT
        borderPaint.strokeWidth = max(1f, min(w, h) * 0.01f)

        rect.set(borderPaint.strokeWidth / 2f, borderPaint.strokeWidth / 2f, w - borderPaint.strokeWidth / 2f, h - borderPaint.strokeWidth / 2f)
    }

    override fun draw(canvas: Canvas) {
        if (rect.isEmpty) return
        canvas.drawRoundRect(rect, radius, radius, bgPaint)
        canvas.drawRoundRect(rect, radius, radius, borderPaint)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

class MainActivity : Activity() {

    private class AppEntry(val pkg: String, val cls: String, val label: String, val ri: ResolveInfo)

    private class Tile(
        val kind: Int, val title: String, val subtitle: String, val color: Int,
        val art: Bitmap?, val pkg: String = "", val cls: String = "", val n: Int = 0
    )

    private data class PrayerTime(
        val name: String,
        val time: String,
        val hour: Int,
        val minute: Int
    )

    private data class PrayerTimesData(
        val fajr: PrayerTime,
        val dhuhr: PrayerTime,
        val asr: PrayerTime,
        val maghrib: PrayerTime,
        val isha: PrayerTime,
        val date: String
    )

    private val handler = Handler(Looper.getMainLooper())
    private var W = 1000
    private var H = 600
    private var cardW = 0
    private var cardH = 0
    private var gap = 0
    private var padH = 0

    private lateinit var appsRow: LinearLayout
    private lateinit var clockText: TextView
    private lateinit var dateText: TextView
    private lateinit var prayerText: TextView
    private lateinit var prayerNextText: TextView
    private lateinit var weatherText: TextView
    private lateinit var prefs: SharedPreferences
    private var tim: TvInputManager? = null
    private var lastSig = ""
    @Volatile private var loading = false
    private var lastWeather = -1L
    private var weatherFailed = false
    private var prayerTimes: PrayerTimesData? = null
    private var lastPrayerFetch = -1L
    private var prayerFetchFailed = false
    private var pendingTile: Tile? = null
    private var lastUpdateCheck = -1L
    @Volatile private var latestBuild = 0
    @Volatile private var latestUrl: String? = null
    private var progressDlg: Dialog? = null
    private var progressTv: TextView? = null
    private val myBuild: Int by lazy { currentBuild() }
    private lateinit var scenicBg: ScenicBackground
    private var currentColorMode: ColorMode = ColorMode.SCENIC

    private val installReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    hideProgress()
                    @Suppress("DEPRECATION")
                    val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    if (confirm != null) {
                        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(confirm)
                    }
                }
                PackageInstaller.STATUS_SUCCESS -> toast("Updated!")
                else -> {
                    hideProgress()
                    toast("Update failed: " + (intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "unknown"))
                }
            }
        }
    }

    private val palette = intArrayOf(
        0xFFE50914.toInt(), 0xFF0A84FF.toInt(), 0xFF8E44AD.toInt(), 0xFF16A085.toInt(),
        0xFFE67E22.toInt(), 0xFF2E86DE.toInt(), 0xFFD63384.toInt(), 0xFF27AE60.toInt()
    )

    private val heroRunnable = Runnable { pendingTile?.let { applyHero(it) } }

    private val ticker = object : Runnable {
        override fun run() {
            updateClock()
            updatePrayerWidget()

            val weatherInterval = if (weatherFailed) 60_000L else 15 * 60 * 1000L
            if (lastWeather < 0 || SystemClock.elapsedRealtime() - lastWeather > weatherInterval) {
                lastWeather = SystemClock.elapsedRealtime()
                fetchWeather()
            }

            val prayerInterval = if (prayerFetchFailed) 30_000L else 12 * 60 * 60 * 1000L
            if (lastPrayerFetch < 0 || SystemClock.elapsedRealtime() - lastPrayerFetch > prayerInterval) {
                lastPrayerFetch = SystemClock.elapsedRealtime()
                fetchPrayerTimes()
            }

            if (lastUpdateCheck < 0 || SystemClock.elapsedRealtime() - lastUpdateCheck > 6 * 60 * 60 * 1000L) {
                lastUpdateCheck = SystemClock.elapsedRealtime()
                Thread {
                    if (fetchLatest() && latestBuild > myBuild) {
                        runOnUiThread { refreshApps(true) }
                    }
                }.start()
            }
            handler.postDelayed(this, 1000L)
        }
    }

    private fun u(f: Float): Int = (f * W / 1000f).toInt()
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dm = resources.displayMetrics
        W = dm.widthPixels
        H = dm.heightPixels
        prefs = getSharedPreferences("tvhome", MODE_PRIVATE)
        tim = getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager

        val savedMode = prefs.getString("color_mode", null)
        currentColorMode = ColorMode.values().find { it.displayName == savedMode } ?: ColorMode.SCENIC

        gap = u(16f)
        padH = u(40f)
        cardW = (W * 0.12f).toInt()
        cardH = cardW

        val root = FrameLayout(this)
        scenicBg = ScenicBackground(this)
        scenicBg.setColorMode(currentColorMode)
        root.addView(scenicBg, MATCH, MATCH)

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(buildTopBar(), LinearLayout.LayoutParams(MATCH, WRAP))
        col.addView(buildAppsRow(), LinearLayout.LayoutParams(MATCH, 0, 1f))
        root.addView(col, MATCH, MATCH)
        setContentView(root)

        val f = IntentFilter(ACTION_INSTALL)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(installReceiver, f, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(installReceiver, f)

        Thread {
            if (fetchLatest() && latestBuild > myBuild) {
                runOnUiThread { showUpdateDialog() }
            }
        }.start()
    }

    override fun onDestroy() {
        try { unregisterReceiver(installReceiver) } catch (e: Exception) { }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        refreshApps(false)
        if (lastPrayerFetch < 0) {
            lastPrayerFetch = SystemClock.elapsedRealtime()
            fetchPrayerTimes()
        }
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(heroRunnable)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        appsRow.getChildAt(0)?.requestFocus()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() { }

    private fun buildTopBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(padH, u(32f), padH, u(24f))
        }

        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        dateText = tv("", 18f, ScenicColors.TEXT_SECONDARY, false)
        clockText = tv("", 56f, ScenicColors.TEXT_PRIMARY, true)
        left.addView(dateText)
        left.addView(clockText)
        bar.addView(left, LinearLayout.LayoutParams(0, WRAP, 1f))

        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }

        // Weather
        val weatherRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(u(16f), u(8f), u(16f), u(8f))
            background = GradientDrawable().apply {
                cornerRadius = u(20f).toFloat()
                setColor(ScenicColors.GLASS_LIGHT)
            }
        }
        weatherText = tv("Loading weather…", 16f, ScenicColors.TEXT_PRIMARY, true)
        weatherRow.addView(weatherText)
        right.addView(weatherRow)

        // Prayer times
        val prayerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(u(16f), u(8f), u(16f), u(8f))
            background = GradientDrawable().apply {
                cornerRadius = u(20f).toFloat()
                setColor(ScenicColors.GLASS_LIGHT)
            }
        }
        prayerText = tv("Loading prayer…", 14f, ScenicColors.TEXT_PRIMARY, true)
        prayerNextText = tv("--:--:--", 14f, ScenicColors.TEXT_SECONDARY, true)
        prayerRow.addView(prayerText)
        prayerRow.addView(prayerNextText, LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = u(8f) })
        right.addView(prayerRow, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = u(8f) })

        bar.addView(right, LinearLayout.LayoutParams(WRAP, WRAP))
        updateClock()
        updatePrayerWidget()
        return bar
    }

    private fun buildAppsRow(): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            setPadding(padH, 0, padH, u(32f))
        }

        appsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
        }

        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(u(24f), u(20f), u(24f), u(20f))
            background = GradientDrawable().apply {
                cornerRadius = u(32f).toFloat()
                setColor(ScenicColors.GLASS_DOCK)
            }
        }
        dock.addView(appsRow, LinearLayout.LayoutParams(MATCH, WRAP))
        container.addView(dock, LinearLayout.LayoutParams(MATCH, WRAP))

        return container
    }

    private fun updateClock() {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Riyadh"))
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = cal.get(java.util.Calendar.MINUTE)
        val ampm = if (hour >= 12) "PM" else "AM"
        val hour12 = if (hour % 12 == 0) 12 else hour % 12
        val timeStr = String.format("%d:%02d %s", hour12, minute, ampm)
        if (clockText.text.toString() != timeStr) clockText.text = timeStr

        val dayNames = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        val monthNames = arrayOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
        val dateStr = "${dayNames[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]} ${cal.get(java.util.Calendar.DAY_OF_MONTH)} ${monthNames[cal.get(java.util.Calendar.MONTH)]}"
        if (dateText.text.toString() != dateStr) dateText.text = dateStr
    }

    private fun httpGet(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "TVHome")
        if (c is HttpsURLConnection) {
            c.sslSocketFactory = permissiveSslContext.socketFactory
        }
        try { return c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
    }

    private fun fetchPrayerTimes() {
        Thread {
            var ok = false
            var data: PrayerTimesData? = null
            try {
                val body = httpGet("https://api.aladhan.com/v1/timingsByCity?city=Riyadh&country=Saudi%20Arabia&method=3")
                val json = JSONObject(body)
                val timings = json.getJSONObject("data").getJSONObject("timings")
                val date = json.getJSONObject("data").getJSONObject("date").getString("readable")

                fun parseTime(timeStr: String): Pair<Int, Int> {
                    val parts = timeStr.split(":")
                    return Pair(parts[0].toInt(), parts[1].toInt())
                }

                val (fajrH, fajrM) = parseTime(timings.getString("Fajr"))
                val (dhuhrH, dhuhrM) = parseTime(timings.getString("Dhuhr"))
                val (asrH, asrM) = parseTime(timings.getString("Asr"))
                val (maghribH, maghribM) = parseTime(timings.getString("Maghrib"))
                val (ishaH, ishaM) = parseTime(timings.getString("Isha"))

                data = PrayerTimesData(
                    fajr = PrayerTime("Fajr", timings.getString("Fajr"), fajrH, fajrM),
                    dhuhr = PrayerTime("Dhuhr", timings.getString("Dhuhr"), dhuhrH, dhuhrM),
                    asr = PrayerTime("Asr", timings.getString("Asr"), asrH, asrM),
                    maghrib = PrayerTime("Maghrib", timings.getString("Maghrib"), maghribH, maghribM),
                    isha = PrayerTime("Isha", timings.getString("Isha"), ishaH, ishaM),
                    date = date
                )
                ok = true
            } catch (e: Exception) {
                val cached = prefs.getString("cached_prayer_times", null)
                if (cached != null) {
                    try {
                        val json = JSONObject(cached)
                        val timings = json.getJSONObject("timings")
                        val date = json.getString("date")

                        fun parseTime(timeStr: String): Pair<Int, Int> {
                            val parts = timeStr.split(":")
                            return Pair(parts[0].toInt(), parts[1].toInt())
                        }

                        val (fajrH, fajrM) = parseTime(timings.getString("Fajr"))
                        val (dhuhrH, dhuhrM) = parseTime(timings.getString("Dhuhr"))
                        val (asrH, asrM) = parseTime(timings.getString("Asr"))
                        val (maghribH, maghribM) = parseTime(timings.getString("Maghrib"))
                        val (ishaH, ishaM) = parseTime(timings.getString("Isha"))

                        data = PrayerTimesData(
                            fajr = PrayerTime("Fajr", timings.getString("Fajr"), fajrH, fajrM),
                            dhuhr = PrayerTime("Dhuhr", timings.getString("Dhuhr"), dhuhrH, dhuhrM),
                            asr = PrayerTime("Asr", timings.getString("Asr"), asrH, asrM),
                            maghrib = PrayerTime("Maghrib", timings.getString("Maghrib"), maghribH, maghribM),
                            isha = PrayerTime("Isha", timings.getString("Isha"), ishaH, ishaM),
                            date = date
                        )
                        ok = true
                    } catch (e2: Exception) { }
                }
            }

            if (ok && data != null) {
                prayerTimes = data
                prayerFetchFailed = false
                try {
                    val cacheJson = JSONObject()
                    val timingsJson = JSONObject()
                    timingsJson.put("Fajr", data.fajr.time)
                    timingsJson.put("Dhuhr", data.dhuhr.time)
                    timingsJson.put("Asr", data.asr.time)
                    timingsJson.put("Maghrib", data.maghrib.time)
                    timingsJson.put("Isha", data.isha.time)
                    cacheJson.put("timings", timingsJson)
                    cacheJson.put("date", data.date)
                    prefs.edit().putString("cached_prayer_times", cacheJson.toString()).apply()
                } catch (e: Exception) { }
            } else {
                prayerFetchFailed = true
            }

            runOnUiThread { updatePrayerWidget() }
        }.start()
    }

    private fun getNextPrayer(): PrayerTime? {
        val prayers = prayerTimes ?: return null
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Riyadh"))
        val currentHour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val currentMinute = cal.get(java.util.Calendar.MINUTE)
        val currentMinutes = currentHour * 60 + currentMinute

        val prayerList = listOf(prayers.fajr, prayers.dhuhr, prayers.asr, prayers.maghrib, prayers.isha)
        for (prayer in prayerList) {
            val prayerMinutes = prayer.hour * 60 + prayer.minute
            if (prayerMinutes > currentMinutes) {
                return prayer
            }
        }
        return prayers.fajr
    }

    private fun getTimeUntilNextPrayer(): String {
        val nextPrayer = getNextPrayer() ?: return "--:--:--"
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Riyadh"))
        val currentHour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val currentMinute = cal.get(java.util.Calendar.MINUTE)
        val currentSecond = cal.get(java.util.Calendar.SECOND)
        val currentSeconds = currentHour * 3600 + currentMinute * 60 + currentSecond

        var prayerSeconds = nextPrayer.hour * 3600 + nextPrayer.minute * 60
        if (prayerSeconds <= currentSeconds) {
            prayerSeconds += 24 * 3600
        }

        val diff = prayerSeconds - currentSeconds
        val hours = diff / 3600
        val minutes = (diff % 3600) / 60
        val seconds = diff % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun updatePrayerWidget() {
        val nextPrayer = getNextPrayer()
        if (nextPrayer != null) {
            prayerText.text = "Next: ${nextPrayer.name}"
            prayerNextText.text = getTimeUntilNextPrayer()
        } else {
            prayerText.text = "Loading prayer times…"
            prayerNextText.text = "--:--:--"
        }
    }

    private fun kindFor(code: Int, day: Boolean): Int = when (code) {
        0, 1 -> if (day) 0 else 1
        in 51..67, in 80..82 -> 3
        in 95..99 -> 4
        else -> 2
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

    private fun fetchWeather() {
        Thread {
            var temp = 0
            var text = ""
            var kind = 2
            var ok = false
            try {
                val body = httpGet("https://api.open-meteo.com/v1/forecast?latitude=24.7136&longitude=46.6753&current=temperature_2m,weather_code,is_day&timezone=auto")
                val cur = JSONObject(body).getJSONObject("current")
                temp = Math.round(cur.getDouble("temperature_2m")).toInt()
                val code = cur.getInt("weather_code")
                text = describe(code)
                kind = kindFor(code, cur.optInt("is_day", 1) == 1)
                ok = true
            } catch (e: Exception) { }
            val t2 = "Riyadh $temp° $text"
            runOnUiThread {
                if (ok) {
                    weatherFailed = false
                    if (weatherText.text.toString() != t2) weatherText.text = t2
                } else {
                    weatherFailed = true
                }
            }
        }.start()
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
            } catch (e: Exception) { }
            loading = false
        }.start()
    }

    private fun buildAppTile(a: AppEntry): Tile {
        val pm = packageManager
        val icon: Drawable? = try { a.ri.loadIcon(pm) } catch (e: Exception) { null }
        val color = cardColor(icon, a.pkg)
        val art = composeIconArt(icon, color, cardW, cardH)
        return Tile(0, a.label, "Press OK to open", color, art, a.pkg, a.cls)
    }

    private fun composeIconArt(icon: Drawable?, color: Int, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val wf = w.toFloat(); val hf = h.toFloat()

        val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        bg.color = Color.argb(200, Color.red(color), Color.green(color), Color.blue(color))
        c.drawRoundRect(0f, 0f, wf, hf, dp(16).toFloat(), dp(16).toFloat(), bg)

        if (icon != null) {
            val iconSize = hf * 0.5f
            val left = (wf - iconSize) / 2f
            val top = (hf - iconSize) / 2f
            icon.setBounds(left.toInt(), top.toInt(), (left + iconSize).toInt(), (top + iconSize).toInt())
            icon.draw(c)
        }
        return bmp
    }

    private fun populateApps(tiles: List<Tile>) {
        appsRow.removeAllViews()
        tiles.forEachIndexed { i, t ->
            appsRow.addView(buildDockItem(t, i < tiles.size - 1))
        }
        if (currentFocus == null) appsRow.getChildAt(0)?.requestFocus()
    }

    private fun buildDockItem(t: Tile, notLast: Boolean): View {
        val r = dp(20).toFloat()
        val frame = FrameLayout(this)

        val iv = ImageView(this)
        iv.setImageBitmap(t.art)
        frame.addView(iv, FrameLayout.LayoutParams(MATCH, MATCH))

        val label = tv(t.title, 14f, ScenicColors.TEXT_PRIMARY, true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            alpha = 0f
            setPadding(0, u(8f), 0, 0)
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = true
            isClickable = true
            clipChildren = false
            alpha = 0.85f
            pivotX = cardW / 2f
            pivotY = cardH / 2f
            if (Build.VERSION.SDK_INT >= 26) setDefaultFocusHighlightEnabled(false)
            addView(frame, LinearLayout.LayoutParams(cardW, cardH))
            addView(label, LinearLayout.LayoutParams(cardW, WRAP))
            layoutParams = LinearLayout.LayoutParams(cardW, WRAP).apply { rightMargin = if (notLast) gap else 0 }
        }

        box.setOnClickListener { launchApp(t) }
        box.setOnFocusChangeListener { v, f ->
            val s = if (f) 1.12f else 1f
            v.animate().scaleX(s).scaleY(s).alpha(if (f) 1f else 0.85f)
                .setDuration(150).setInterpolator(DecelerateInterpolator()).start()
            label.alpha = if (f) 1f else 0f
        }
        return box
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

    private fun applyHero(t: Tile) { }

    private fun onTileFocused(t: Tile) { }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

    private fun currentBuild(): Int = try {
        packageManager.getPackageInfo(packageName, 0).versionName?.substringAfterLast('.')?.toIntOrNull() ?: 0
    } catch (e: Exception) { 0 }

    private fun fetchLatest(): Boolean {
        return try {
            val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
            c.connectTimeout = 10000; c.readTimeout = 10000
            c.setRequestProperty("User-Agent", "TVHome")
            c.setRequestProperty("Accept", "application/vnd.github+json")
            if (c is HttpsURLConnection) {
                c.sslSocketFactory = permissiveSslContext.socketFactory
                c.hostnameVerifier = permissiveHostnameVerifier
            }
            val body = c.inputStream.bufferedReader().use { it.readText() }
            c.disconnect()
            val j = JSONObject(body)
            val n = Regex("(\\d+)").find(j.getString("tag_name"))?.value?.toIntOrNull() ?: 0
            val assets = j.getJSONArray("assets")
            var url: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) { url = a.getString("browser_download_url"); break }
            }
            latestBuild = n
            latestUrl = url
            true
        } catch (e: Exception) { false }
    }

    private fun showProgress(msg: String) {
        val existing = progressTv
        if (existing != null) { existing.text = msg; return }
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val t = tv(msg, 20f, ScenicColors.TEXT_PRIMARY, true).apply {
            setPadding(u(36f), u(26f), u(36f), u(26f))
            background = GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(0xF01C1C24.toInt()) }
        }
        d.setContentView(t)
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        d.show()
        progressDlg = d
        progressTv = t
    }

    private fun hideProgress() {
        try { progressDlg?.dismiss() } catch (e: Exception) { }
        progressDlg = null
        progressTv = null
    }

    private fun showUpdateDialog() {
        val dlg = Dialog(this)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(u(28f), u(24f), u(28f), u(24f))
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat(); setColor(0xF01C1C24.toInt()); setStroke(max(1, u(1f)), 0x33FFFFFF)
            }
        }
        panel.addView(tv("Update Available", 24f, ScenicColors.TEXT_PRIMARY, true))
        panel.addView(tv("Build $latestBuild is ready to install.", 16f, 0x99FFFFFF.toInt()).apply {
            setPadding(0, u(8f), 0, u(20f))
        })
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        val cancel = pill("Later", false) { dlg.dismiss() }
        buttons.addView(cancel, LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = u(12f) })
        buttons.addView(pill("Update Now", true) { dlg.dismiss(); downloadAndInstall(latestUrl ?: return@pill) })
        panel.addView(buttons, LinearLayout.LayoutParams(MATCH, WRAP))
        dlg.setContentView(panel)
        dlg.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(u(520f), WindowManager.LayoutParams.WRAP_CONTENT)
            setDimAmount(0.65f)
        }
        dlg.show()
        cancel.requestFocus()
    }

    private fun downloadAndInstall(url: String) {
        showProgress("Downloading...")
        Thread {
            val file = File(cacheDir, "update.apk")
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 15000; c.readTimeout = 30000
                c.setRequestProperty("User-Agent", "TVHome")
                if (c is HttpsURLConnection) {
                    c.sslSocketFactory = permissiveSslContext.socketFactory
                    c.hostnameVerifier = permissiveHostnameVerifier
                }
                c.inputStream.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                c.disconnect()
                installFile(file)
            } catch (e: Exception) {
                runOnUiThread { hideProgress(); toast("Download failed") }
            }
        }.start()
    }

    private fun installFile(file: File) {
        var session: PackageInstaller.Session? = null
        try {
            val installer = packageManager.packageInstaller
            val id = installer.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
            val s = installer.openSession(id)
            session = s
            java.io.FileInputStream(file).use { input ->
                s.openWrite("tvhome.apk", 0, file.length()).use { o ->
                    input.copyTo(o)
                    s.fsync(o)
                }
            }
            runOnUiThread { showProgress("Installing…") }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(this, 0, Intent(ACTION_INSTALL).setPackage(packageName), flags)
            s.commit(pi.intentSender)
            s.close()
        } catch (e: Exception) {
            try { session?.abandon() } catch (e2: Exception) { }
            runOnUiThread { hideProgress(); toast("Install failed") }
        }
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

    private fun pill(text: String, primary: Boolean, onClick: () -> Unit): TextView {
        val t = tv(text, 15f, ScenicColors.TEXT_PRIMARY, true)
        fun paint(f: Boolean) {
            t.background = GradientDrawable().apply {
                cornerRadius = u(30f).toFloat()
                setColor(if (f) ScenicColors.TEXT_PRIMARY else if (primary) 0xFF0A84FF.toInt() else 0x33FFFFFF)
            }
            t.setTextColor(if (f) Color.BLACK else ScenicColors.TEXT_PRIMARY)
        }
        t.gravity = Gravity.CENTER
        t.isFocusable = true
        t.isClickable = true
        setPadding(u(26f), u(11f), u(26f), u(11f))
        paint(false)
        t.setOnFocusChangeListener { _, f -> paint(f) }
        t.setOnClickListener { onClick() }
        return t
    }

    private fun tv(text: String, sizeU: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_PX, u(sizeU).toFloat())
            setTextColor(color)
            typeface = if (bold) Typeface.create("sans-serif-medium", Typeface.NORMAL) else Typeface.create("sans-serif", Typeface.NORMAL)
        }
}
