package com.realm.tvos

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
private const val HDMI_COUNT = 4
private const val REPO = "zezo20234/tv"
private const val ACTION_INSTALL = "com.realm.tvos.app.INSTALL_RESULT"

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

// ======================= Premium Visual System =======================

enum class ThemeMode(val displayName: String, val colors: List<Int>) {
    REALM_BLUE("Realm Blue", listOf(0xFF1A237E.toInt(), 0xFF311B92.toInt(), 0xFF004D40.toInt(), 0xFFB71C1C.toInt())),
    COSMIC_PURPLE("Cosmic Purple", listOf(0xFF4A148C.toInt(), 0xFF7B1FA2.toInt(), 0xFF880E4F.toInt(), 0xFF1A237E.toInt())),
    DEEP_OCEAN("Deep Ocean", listOf(0xFF01579B.toInt(), 0xFF0277BD.toInt(), 0xFF006064.toInt(), 0xFF004D40.toInt())),
    SUNSET_GOLD("Sunset Gold", listOf(0xFFBF360C.toInt(), 0xFFE65100.toInt(), 0xFFF57F17.toInt(), 0xFF3E2723.toInt())),
    NEON_CYAN("Neon Cyan", listOf(0xFF006064.toInt(), 0xFF00838F.toInt(), 0xFF0097A7.toInt(), 0xFF00BCD4.toInt())),
    MIDNIGHT("Midnight", listOf(0xFF212121.toInt(), 0xFF263238.toInt(), 0xFF37474F.toInt(), 0xFF455A64.toInt())),
    VIBRANT_CORAL("Vibrant Coral", listOf(0xFFBF360C.toInt(), 0xFFE91E63.toInt(), 0xFFFF4081.toInt(), 0xFF880E4F.toInt())),
    ELECTRIC_VIOLET("Electric Violet", listOf(0xFF6A1B9A.toInt(), 0xFF9C27B0.toInt(), 0xFFE91E63.toInt(), 0xFF311B92.toInt())),
    MYSTIC_TEAL("Mystic Teal", listOf(0xFF004D40.toInt(), 0xFF00796B.toInt(), 0xFF00897B.toInt(), 0xFF01579B.toInt())),
    AURORA_GREEN("Aurora Green", listOf(0xFF1B5E20.toInt(), 0xFF2E7D32.toInt(), 0xFF4CAF50.toInt(), 0xFF00838F.toInt())),
    ROYAL_MAROON("Royal Maroon", listOf(0xFF4A0000.toInt(), 0xFF7B0000.toInt(), 0xFFB71C1C.toInt(), 0xFF880E4F.toInt())),
    ULTRA_VIOLET("Ultra Violet", listOf(0xFF311B92.toInt(), 0xFF4527A0.toInt(), 0xFF7B1FA2.toInt(), 0xFF004D40.toInt()))
}

class PremiumBackground(ctx: Context) : View(ctx) {
    private var bmp: Bitmap? = null
    private val dst = Rect()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var themeMode: ThemeMode = ThemeMode.REALM_BLUE
    private var animationOffset = 0f

    fun setThemeMode(mode: ThemeMode) {
        if (themeMode != mode) {
            themeMode = mode
            bmp = null
            requestLayout()
        }
    }

    private fun createGradientBlob(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int, alpha: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(cx, cy, radius, intArrayOf(
            Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)),
            Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))
        ), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius, paint)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        val bw = max(16, w / 4)
        val bh = max(16, h / 4)
        val b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(0xFF0A0E14.toInt())
        val m = max(bw, bh).toFloat()
        val colors = themeMode.colors
        createGradientBlob(c, bw * 0.15f, bh * 0.10f, m * 0.80f, colors[0], 180)
        createGradientBlob(c, bw * 0.85f, bh * 0.30f, m * 0.65f, colors[1], 140)
        createGradientBlob(c, bw * 0.50f, bh * 1.10f, m * 0.75f, colors[2], 110)
        createGradientBlob(c, bw * 0.95f, bh * 0.90f, m * 0.50f, colors[3], 80)
        bmp = b
    }

    override fun onDraw(canvas: Canvas) {
        val b = bmp ?: return
        dst.set(0, 0, width, height)
        canvas.drawBitmap(b, null, dst, paint)
    }
}

class GlassCardBackground(
    private val radius: Float,
    private val fillTop: Int,
    private val fillBottom: Int,
    private val rimStrength: Float,
    private val showSheen: Boolean
) : Drawable() {
    private val rect = RectF()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    override fun onBoundsChange(b: Rect) {
        super.onBoundsChange(b)
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        if (w <= 0f || h <= 0f) return
        fillPaint.shader = LinearGradient(0f, 0f, 0f, h, fillTop, fillBottom, Shader.TileMode.CLAMP)
        sheenPaint.shader = LinearGradient(0f, 0f, 0f, h * 0.50f, 0x3DFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        val a = (255 * rimStrength).toInt().coerceIn(0, 255)
        rimPaint.shader = LinearGradient(0f, 0f, w, h,
            intArrayOf(Color.argb((a * 0.85f).toInt(), 255, 255, 255),
                Color.argb((a * 0.15f).toInt(), 255, 255, 255),
                Color.argb((a * 0.50f).toInt(), 255, 255, 255)),
            floatArrayOf(0f, 0.50f, 1f), Shader.TileMode.CLAMP)
        rimPaint.strokeWidth = max(1.5f, min(w, h) * 0.012f)
        val inset = rimPaint.strokeWidth / 2f
        rect.set(inset, inset, w - inset, h - inset)
    }

    override fun draw(canvas: Canvas) {
        if (rect.isEmpty) return
        canvas.drawRoundRect(rect, radius, radius, fillPaint)
        if (showSheen) canvas.drawRoundRect(rect, radius, radius, sheenPaint)
        if (rimStrength > 0f) canvas.drawRoundRect(rect, radius, radius, rimPaint)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

class FastCardBox(ctx: Context) : LinearLayout(ctx) {
    override fun hasOverlappingRendering(): Boolean = false
}

class RoundedBitmapView(ctx: Context) : View(ctx) {
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

// ======================= Custom Icons =======================

class WifiIconView(ctx: Context) : View(ctx) {
    var connected: Boolean = true
        set(v) { if (field != v) { field = v; invalidate() } }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val color = if (connected) Color.WHITE else 0x4DFFFFFF
        paint.color = color
        dotPaint.color = color
        paint.strokeWidth = h * 0.080f
        val cx = w / 2f
        val cy = h * 0.90f
        for (i in 1..3) {
            val r = h * 0.28f * i
            rect.set(cx - r, cy - r, cx + r, cy + r)
            canvas.drawArc(rect, 225f, 90f, false, paint)
        }
        canvas.drawCircle(cx, cy, h * 0.065f, dotPaint)
    }
}

class SettingsIconView(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFE8EAED.toInt()
    }
    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f
        paint.strokeCap = Paint.Cap.BUTT
        paint.strokeWidth = r * 0.24f
        canvas.drawCircle(cx, cy, r * 0.50f, paint)
        paint.strokeWidth = r * 0.28f
        for (i in 0 until 8) {
            canvas.save()
            canvas.rotate(i * 45f, cx, cy)
            canvas.drawLine(cx, cy - r * 0.60f, cx, cy - r * 0.92f, paint)
            canvas.restore()
        }
    }
}

class HdmiPortIconView(ctx: Context) : View(ctx) {
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cavityPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0D0F12.toInt() }
    private val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC4C9D2.toInt() }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x88FFFFFF.toInt()
    }
    private val outerPath = Path()
    private val innerPath = Path()

    private fun createHdmiShape(path: Path, l: Float, t: Float, r: Float, b: Float) {
        val w = r - l
        val h = b - t
        path.reset()
        path.moveTo(l, t)
        path.lineTo(r, t)
        path.lineTo(r, t + h * 0.50f)
        path.lineTo(r - w * 0.12f, b)
        path.lineTo(l + w * 0.12f, b)
        path.lineTo(l, t + h * 0.50f)
        path.close()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        bodyPaint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
            intArrayOf(0xFFE8EAED.toInt(), 0xFF9AA5B1.toInt(), 0xFFD5D9E0.toInt(), 0xFF6B7280.toInt()),
            floatArrayOf(0f, 0.40f, 0.65f, 1f), Shader.TileMode.CLAMP)
        rimPaint.strokeWidth = max(1f, h * 0.025f)
        createHdmiShape(outerPath, 0f, 0f, w.toFloat(), h.toFloat())
        val inset = h * 0.16f
        createHdmiShape(innerPath, inset * 1.3f, inset, w - inset * 1.3f, h - inset * 0.85f)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawPath(outerPath, bodyPaint)
        canvas.drawPath(outerPath, rimPaint)
        canvas.drawPath(innerPath, cavityPaint)
        val pinCount = 9
        val pinW = width * 0.055f
        val pinH = height * 0.14f
        for (k in 0 until pinCount) {
            val x = width * 0.20f + k * (width * 0.60f / (pinCount - 1)) - pinW / 2f
            canvas.drawRoundRect(x, height * 0.36f, x + pinW, height * 0.36f + pinH, 2f, 2f, pinPaint)
        }
    }
}

class WeatherIconView(ctx: Context) : View(ctx) {
    var weatherType: Int = 2
        set(v) { field = v; invalidate() }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()
    private val path2 = Path()

    private fun drawCloud(canvas: Canvas, w: Float, h: Float) {
        fillPaint.color = 0xFFE8EAED.toInt()
        canvas.drawCircle(w * 0.32f, h * 0.50f, h * 0.22f, fillPaint)
        canvas.drawCircle(w * 0.50f, h * 0.38f, h * 0.28f, fillPaint)
        canvas.drawCircle(w * 0.70f, h * 0.50f, h * 0.22f, fillPaint)
        canvas.drawRoundRect(w * 0.16f, h * 0.46f, w * 0.86f, h * 0.70f, h * 0.14f, h * 0.14f, fillPaint)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        when (weatherType) {
            0 -> {
                fillPaint.color = 0xFFFFB74D.toInt()
                linePaint.color = 0xFFFFB74D.toInt()
                linePaint.strokeWidth = h * 0.08f
                canvas.drawCircle(w / 2f, h / 2f, h * 0.22f, fillPaint)
                for (i in 0 until 8) {
                    canvas.save()
                    canvas.rotate(i * 45f, w / 2f, h / 2f)
                    canvas.drawLine(w / 2f, h * 0.06f, w / 2f, h * 0.16f, linePaint)
                    canvas.restore()
                }
            }
            1 -> {
                path.reset()
                path.addCircle(w / 2f, h / 2f, h * 0.35f, Path.Direction.CW)
                path2.reset()
                path2.addCircle(w * 0.62f, h * 0.36f, h * 0.30f, Path.Direction.CW)
                path.op(path2, Path.Op.DIFFERENCE)
                fillPaint.color = 0xFFE8EAED.toInt()
                canvas.drawPath(path, fillPaint)
            }
            3 -> {
                drawCloud(canvas, w, h)
                linePaint.color = 0xFF64B5F6.toInt()
                linePaint.strokeWidth = h * 0.08f
                canvas.drawLine(w * 0.34f, h * 0.78f, w * 0.30f, h * 0.92f, linePaint)
                canvas.drawLine(w * 0.52f, h * 0.78f, w * 0.48f, h * 0.92f, linePaint)
                canvas.drawLine(w * 0.70f, h * 0.78f, w * 0.66f, h * 0.92f, linePaint)
            }
            4 -> {
                drawCloud(canvas, w, h)
                fillPaint.color = 0xFFFFB74D.toInt()
                path.reset()
                path.moveTo(w * 0.52f, h * 0.66f)
                path.lineTo(w * 0.40f, h * 0.86f)
                path.lineTo(w * 0.50f, h * 0.86f)
                path.lineTo(w * 0.44f, h * 0.98f)
                path.lineTo(w * 0.62f, h * 0.78f)
                path.lineTo(w * 0.52f, h * 0.78f)
                path.close()
                canvas.drawPath(path, fillPaint)
            }
            else -> drawCloud(canvas, w, h)
        }
    }
}

class UpdateIconView(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFFFFFFF.toInt()
        strokeCap = Paint.Cap.ROUND
    }
    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        paint.strokeWidth = h * 0.10f
        canvas.drawLine(cx, h * 0.12f, cx, h * 0.60f, paint)
        canvas.drawLine(cx - w * 0.24f, h * 0.40f, cx, h * 0.62f, paint)
        canvas.drawLine(cx + w * 0.24f, h * 0.40f, cx, h * 0.62f, paint)
        canvas.drawLine(w * 0.24f, h * 0.86f, w * 0.76f, h * 0.86f, paint)
    }
}

class GameControllerIconView(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFFFFFFF.toInt()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1A237E.toInt()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val scale = min(w, h) * 0.40f

        paint.strokeWidth = scale * 0.10f

        // Controller body
        val bodyPath = Path()
        bodyPath.moveTo(cx - scale * 0.8f, cy - scale * 0.3f)
        bodyPath.lineTo(cx + scale * 0.8f, cy - scale * 0.3f)
        bodyPath.lineTo(cx + scale * 0.9f, cy + scale * 0.2f)
        bodyPath.lineTo(cx + scale * 0.7f, cy + scale * 0.5f)
        bodyPath.lineTo(cx - scale * 0.7f, cy + scale * 0.5f)
        bodyPath.lineTo(cx - scale * 0.9f, cy + scale * 0.2f)
        bodyPath.close()
        canvas.drawPath(bodyPath, fillPaint)
        canvas.drawPath(bodyPath, paint)

        // D-pad
        paint.strokeWidth = scale * 0.08f
        canvas.drawLine(cx - scale * 0.6f, cy, cx - scale * 0.4f, cy, paint)
        canvas.drawLine(cx - scale * 0.5f, cy - scale * 0.1f, cx - scale * 0.5f, cy + scale * 0.1f, paint)

        // Buttons
        canvas.drawCircle(cx + scale * 0.5f, cy - scale * 0.05f, scale * 0.06f, paint)
        canvas.drawCircle(cx + scale * 0.6f, cy + scale * 0.05f, scale * 0.06f, paint)
        canvas.drawCircle(cx + scale * 0.4f, cy + scale * 0.05f, scale * 0.06f, paint)
    }
}

class ProfileIconView(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFE8EAED.toInt()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val scale = min(w, h) * 0.40f

        // Head
        canvas.drawCircle(cx, cy - scale * 0.15f, scale * 0.35f, paint)

        // Body
        val bodyPath = Path()
        bodyPath.addArc(cx - scale * 0.6f, cy + scale * 0.2f, cx + scale * 0.6f, cy + scale * 1.2f, 0f, 180f)
        canvas.drawPath(bodyPath, paint)
    }
}

class SidebarIconView(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFE8EAED.toInt()
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        paint.strokeWidth = w * 0.18f

        canvas.drawLine(cx - w * 0.30f, cy - h * 0.25f, cx + w * 0.30f, cy - h * 0.25f, paint)
        canvas.drawLine(cx - w * 0.30f, cy, cx + w * 0.30f, cy, paint)
        canvas.drawLine(cx - w * 0.30f, cy + h * 0.25f, cx + w * 0.30f, cy + h * 0.25f, paint)
    }
}

// ======================= Main Activity =======================

class MainActivity : Activity() {

    private class AppEntry(val pkg: String, val cls: String, val label: String, val ri: ResolveInfo)

    private class Tile(
        val kind: Int,
        val title: String,
        val subtitle: String,
        val color: Int,
        val art: Bitmap?,
        val pkg: String = "",
        val cls: String = "",
        val n: Int = 0
    )

    private val handler = Handler(Looper.getMainLooper())
    private var W = 1920
    private var H = 1080
    private var cardW = 0
    private var cardH = 0
    private var gap = 0
    private var padH = 0

    private lateinit var mainScrollView: ScrollView
    private lateinit var favRow: LinearLayout
    private lateinit var recentRow: LinearLayout
    private lateinit var appsRow: LinearLayout
    private lateinit var gamingRow: LinearLayout
    private lateinit var entertainmentRow: LinearLayout
    private lateinit var inputsRow: LinearLayout

    private lateinit var heroTitle: TextView
    private lateinit var heroSubtitle: TextView
    private lateinit var heroButton: TextView
    private lateinit var heroFrame: FrameLayout
    private lateinit var heroImage: RoundedBitmapView
    private lateinit var heroHdmi: HdmiPortIconView
    private lateinit var heroSettings: SettingsIconView
    private lateinit var heroUpdate: UpdateIconView

    private lateinit var greetingText: TextView
    private var clockText: TextView? = null
    private var dateText: TextView? = null
    private lateinit var weatherText: TextView
    private lateinit var weatherIcon: WeatherIconView
    private lateinit var wifiIcon: WifiIconView
    private lateinit var profileIcon: ProfileIconView
    private lateinit var settingsIcon: SettingsIconView

    private val dateFmt = java.text.SimpleDateFormat("EEEE, MMMM d", java.util.Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("Asia/Riyadh")
    }
    private val clockFmt = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("Asia/Riyadh")
    }

    private lateinit var prefs: SharedPreferences
    private var tim: TvInputManager? = null
    private var lastSig = ""
    @Volatile private var loading = false
    private var lastWeather = -1L
    private var weatherFailed = false
    private var pendingTile: Tile? = null
    private var lastUpdateCheck = -1L
    @Volatile private var latestBuild = 0
    @Volatile private var latestUrl: String? = null
    private var progressDlg: Dialog? = null
    private var progressTv: TextView? = null
    private val myBuild: Int by lazy { currentBuild() }
    private lateinit var premiumBg: PremiumBackground
    private var currentThemeMode: ThemeMode = ThemeMode.REALM_BLUE

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
                PackageInstaller.STATUS_SUCCESS -> toast("Updated successfully!")
                else -> {
                    hideProgress()
                    toast("Update failed: " + (intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "unknown"))
                }
            }
        }
    }

    private val palette = intArrayOf(
        0xFFE50914.toInt(), 0xFF0078D4.toInt(), 0xFF9C27B0.toInt(), 0xFF009688.toInt(),
        0xFFFF9800.toInt(), 0xFF2196F3.toInt(), 0xFFE91E63.toInt(), 0xFF4CAF50.toInt()
    )

    private val heroRunnable = Runnable { pendingTile?.let { applyHero(it) } }

    private val ticker = object : Runnable {
        override fun run() {
            val online = isOnline()
            wifiIcon.connected = online
            updateGreeting()
            updateClock()
            val weatherInterval = if (weatherFailed) 60_000L else 15 * 60 * 1000L
            if (lastWeather < 0 || SystemClock.elapsedRealtime() - lastWeather > weatherInterval) {
                lastWeather = SystemClock.elapsedRealtime()
                fetchWeather()
            }
            if (lastUpdateCheck < 0 || SystemClock.elapsedRealtime() - lastUpdateCheck > 6 * 60 * 60 * 1000L) {
                lastUpdateCheck = SystemClock.elapsedRealtime()
                Thread {
                    if (fetchLatest() && latestBuild > myBuild) {
                        runOnUiThread { if (currentFocus?.parent !== inputsRow) refreshInputs(0) }
                    }
                }.start()
            }
            handler.postDelayed(this, 5000L)
        }
    }

    private fun u(f: Float): Int = (f * W / 1920f).toInt()
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dm = resources.displayMetrics
        W = dm.widthPixels
        H = dm.heightPixels
        prefs = getSharedPreferences("realm_tvos_v22", MODE_PRIVATE)
        tim = getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager

        val savedTheme = prefs.getString("theme_mode", null)
        currentThemeMode = ThemeMode.values().find { it.displayName == savedTheme } ?: ThemeMode.REALM_BLUE

        gap = u(20f)
        padH = u(60f)
        cardW = (W * 0.18f).toInt()
        cardH = cardW * 9 / 16

        val root = FrameLayout(this)
        premiumBg = PremiumBackground(this)
        premiumBg.setThemeMode(currentThemeMode)
        root.addView(premiumBg, MATCH, MATCH)

        val mainContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        mainContainer.addView(buildPremiumHeader(), LinearLayout.LayoutParams(MATCH, WRAP))
        mainContainer.addView(buildHeroSection(), LinearLayout.LayoutParams(MATCH, (H * 0.35f).toInt()))

        favRow = createRow()
        recentRow = createRow()
        entertainmentRow = createRow()
        gamingRow = createRow()
        inputsRow = createRow()
        appsRow = createRow()

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
        }
        contentLayout.addView(sectionHeader("Favorites"))
        contentLayout.addView(createShelf(favRow))
        contentLayout.addView(sectionHeader("Recent"))
        contentLayout.addView(createShelf(recentRow))
        contentLayout.addView(sectionHeader("Entertainment"))
        contentLayout.addView(createShelf(entertainmentRow))
        contentLayout.addView(sectionHeader("Gaming"))
        contentLayout.addView(createShelf(gamingRow))
        contentLayout.addView(sectionHeader("Inputs"))
        contentLayout.addView(createShelf(inputsRow))
        contentLayout.addView(sectionHeader("All Apps"))
        contentLayout.addView(createShelf(appsRow))

        mainScrollView = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipChildren = false
            clipToPadding = false
            addView(contentLayout, MATCH, WRAP)
        }
        mainContainer.addView(mainScrollView, LinearLayout.LayoutParams(MATCH, 0, 1f))
        root.addView(mainContainer, MATCH, MATCH)
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
        mainScrollView.scrollTo(0, 0)
        favRow.getChildAt(0)?.requestFocus()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() { }

    // ======================= Header =======================

    private fun buildPremiumHeader(): View {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(padH, u(32f), padH, 0)
        }

        val leftSection = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        greetingText = tv("Welcome", 38f, Color.WHITE, true)
        leftSection.addView(greetingText)

        val weatherRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(u(16f), u(8f), u(24f), u(8f))
            background = createGlassCapsule()
        }
        weatherIcon = WeatherIconView(this)
        weatherRow.addView(weatherIcon, LinearLayout.LayoutParams(u(32f), u(32f)).apply { rightMargin = u(12f) })
        weatherText = tv("Riyadh  loading weather…", 20f, 0xF2FFFFFF.toInt(), true)
        weatherRow.addView(weatherText)
        leftSection.addView(weatherRow, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = u(12f) })
        header.addView(leftSection, LinearLayout.LayoutParams(0, WRAP, 1f))

        val rightSection = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(u(32f), u(16f), u(40f), u(16f))
            background = createGlassCapsule()
        }
        wifiIcon = WifiIconView(this)
        rightSection.addView(wifiIcon, LinearLayout.LayoutParams(u(32f), u(26f)).apply { rightMargin = u(20f) })

        val timeColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        clockText = tv("", 44f, 0xFFFFFFFF.toInt(), true)
        dateText = tv("", 18f, 0xCCFFFFFF.toInt(), false)
        timeColumn.addView(clockText!!)
        timeColumn.addView(dateText!!)
        rightSection.addView(timeColumn)

        rightSection.addView(View(this).apply { width = u(24f) }, LinearLayout.LayoutParams(WRAP, MATCH))

        profileIcon = ProfileIconView(this)
        rightSection.addView(profileIcon, LinearLayout.LayoutParams(u(36f), u(36f)).apply { rightMargin = u(16f) })

        settingsIcon = SettingsIconView(this)
        settingsIcon.isFocusable = true
        settingsIcon.isClickable = true
        settingsIcon.setOnClickListener { openSettings() }
        rightSection.addView(settingsIcon, LinearLayout.LayoutParams(u(36f), u(36f)))

        header.addView(rightSection, LinearLayout.LayoutParams(WRAP, WRAP))
        updateGreeting()
        updateClock()
        return header
    }

    private fun createGlassCapsule(): GlassCardBackground {
        return GlassCardBackground(u(44f).toFloat(),
            Color.argb(80, 255, 255, 255), Color.argb(40, 255, 255, 255), 0.95f, true)
    }

    private fun updateClock() {
        val t = clockFmt.format(java.util.Date())
        if (clockText?.text?.toString() != t) clockText?.text = t
        val d = dateFmt.format(java.util.Date())
        if (dateText?.text?.toString() != d) dateText?.text = d
    }

    private fun updateGreeting() {
        val h = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Riyadh")).get(java.util.Calendar.HOUR_OF_DAY)
        val g = when {
            h in 5..11 -> "Good morning"
            h in 12..16 -> "Good afternoon"
            h in 17..21 -> "Good evening"
            else -> "Good night"
        }
        if (greetingText.text.toString() != g) greetingText.text = g
    }

    // ======================= Hero Section =======================

    private fun buildHeroSection(): View {
        val hero = FrameLayout(this).apply {
            setPadding(padH, 0, padH, 0)
            clipChildren = false
        }

        val textLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heroTitle = tv("", 56f, Color.WHITE, true).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        heroSubtitle = tv("", 20f, 0xB3FFFFFF.toInt()).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        heroButton = tv("Open", 21f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = GlassCardBackground(u(44f).toFloat(),
                Color.argb(100, 255, 255, 255), Color.argb(50, 255, 255, 255), 1f, true)
        }
        val titleWidth = (W * 0.48f).toInt()
        textLayout.addView(heroTitle, LinearLayout.LayoutParams(titleWidth, u(64f)))
        textLayout.addView(heroSubtitle, LinearLayout.LayoutParams(titleWidth, u(28f)).apply { topMargin = u(4f) })
        textLayout.addView(heroButton, LinearLayout.LayoutParams(u(240f), u(48f)).apply { topMargin = u(16f) })
        hero.addView(textLayout, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.START).apply {
            bottomMargin = u(16f)
        })

        val heroHeight = (H * 0.35f * 0.82f).toInt()
        val heroWidth = heroHeight * 16 / 9
        heroFrame = FrameLayout(this)
        heroImage = RoundedBitmapView(this).apply { radius = dp(32).toFloat() }
        heroFrame.addView(heroImage, FrameLayout.LayoutParams(MATCH, MATCH))
        heroHdmi = HdmiPortIconView(this)
        heroFrame.addView(heroHdmi, FrameLayout.LayoutParams((heroWidth * 0.36f).toInt(), (heroWidth * 0.36f * 0.46f).toInt(), Gravity.CENTER))
        heroSettings = SettingsIconView(this)
        heroFrame.addView(heroSettings, FrameLayout.LayoutParams((heroHeight * 0.52f).toInt(), (heroHeight * 0.52f).toInt(), Gravity.CENTER))
        heroUpdate = UpdateIconView(this)
        heroFrame.addView(heroUpdate, FrameLayout.LayoutParams((heroHeight * 0.52f).toInt(), (heroHeight * 0.52f).toInt(), Gravity.CENTER))
        heroFrame.addView(View(this).apply {
            background = GlassCardBackground(dp(32).toFloat(), 0, 0, 1f, true)
        }, FrameLayout.LayoutParams(MATCH, MATCH))
        hero.addView(heroFrame, FrameLayout.LayoutParams(heroWidth, heroHeight, Gravity.END or Gravity.CENTER_VERTICAL))
        return hero
    }

    private fun applyHero(t: Tile) {
        heroTitle.text = t.title
        heroSubtitle.text = t.subtitle
        heroButton.text = when (t.kind) {
            0 -> "Open"
            1 -> "Choose input"
            2 -> "Settings"
            3 -> "Update"
            5 -> "Options"
            6 -> "Uninstall"
            else -> "Find inputs"
        }
        heroImage.visibility = if (t.kind == 0) View.VISIBLE else View.INVISIBLE
        heroHdmi.visibility = if (t.kind == 1 || t.kind == 4) View.VISIBLE else View.INVISIBLE
        heroSettings.visibility = if (t.kind == 2) View.VISIBLE else View.INVISIBLE
        heroUpdate.visibility = if (t.kind == 3) View.VISIBLE else View.INVISIBLE
        if (t.kind == 0) {
            heroImage.setBitmap(t.art)
            heroFrame.background = null
        } else {
            heroFrame.background = GlassCardBackground(dp(32).toFloat(),
                withAlpha(shade(t.color, 1.3f), 220), withAlpha(shade(t.color, 0.7f), 190), 0f, false)
        }
    }

    private fun onTileFocused(t: Tile) {
        pendingTile = t
        handler.removeCallbacks(heroRunnable)
        handler.postDelayed(heroRunnable, 50L)
    }

    // ======================= Sections =======================

    private fun sectionHeader(title: String): View {
        return tv(title, 28f, Color.WHITE, true).apply {
            setPadding(padH, u(24f), padH, 0)
        }
    }

    private fun createRow(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
    }

    private fun createShelf(row: LinearLayout): HorizontalScrollView {
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipChildren = false
            clipToPadding = false
            setPadding(padH, u(20f), padH, u(36f))
            addView(row)
        }
    }

    // ======================= Weather =======================

    private fun httpGet(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "RealmTVOS")
        if (c is HttpsURLConnection) {
            c.sslSocketFactory = permissiveSslContext.socketFactory
        }
        try { return c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
    }

    private fun weatherKindFor(code: Int, isDay: Boolean): Int = when (code) {
        0, 1 -> if (isDay) 0 else 1
        in 51..67, in 80..82 -> 3
        in 95..99 -> 4
        else -> 2
    }

    private fun fetchWeather() {
        Thread {
            var temp = 0
            var text = ""
            var kind = 2
            var ok = false
            val errors = ArrayList<String>()
            try {
                val body = httpGet("https://api.open-meteo.com/v1/forecast?latitude=24.7136&longitude=46.6753&current=temperature_2m,weather_code,is_day&timezone=auto")
                val cur = JSONObject(body).getJSONObject("current")
                temp = Math.round(cur.getDouble("temperature_2m")).toInt()
                val code = cur.getInt("weather_code")
                text = describeWeather(code)
                kind = weatherKindFor(code, cur.optInt("is_day", 1) == 1)
                ok = true
            } catch (e: Exception) { errors.add("OpenMeteo: " + e.javaClass.simpleName) }
            if (!ok) {
                for (base in listOf("https://wttr.in", "http://wttr.in")) {
                    try {
                        val parts = httpGet("$base/Riyadh?format=%t|%C").trim().split("|")
                        val num = Regex("-?[0-9]+").find(parts[0])?.value?.toIntOrNull()
                        if (num != null) {
                            temp = num
                            text = if (parts.size > 1) parts[1].trim() else ""
                            val low = text.lowercase()
                            val hr = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                            val day = hr in 6..17
                            kind = when {
                                low.contains("thunder") -> 4
                                low.contains("rain") || low.contains("drizzle") || low.contains("shower") -> 3
                                low.contains("sun") || low.contains("clear") -> if (day) 0 else 1
                                else -> 2
                            }
                            ok = true
                            break
                        }
                    } catch (e: Exception) { errors.add("wttr: " + e.javaClass.simpleName) }
                }
            }
            val weatherStr = "Riyadh  $temp°  $text"
            runOnUiThread {
                if (ok) {
                    weatherFailed = false
                    if (weatherText.text.toString() != weatherStr) weatherText.text = weatherStr
                    weatherIcon.weatherType = kind
                } else {
                    weatherFailed = true
                    val why = errors.joinToString("  /  ")
                    val cur = weatherText.text.toString()
                    if (cur.endsWith("…") || cur.contains("unavailable")) weatherText.text = "Riyadh  weather error: $why"
                }
            }
        }.start()
    }

    private fun describeWeather(code: Int): String = when (code) {
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

    // ======================= Apps =======================

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
        val banner: Drawable? = try { a.ri.activityInfo.loadBanner(pm) } catch (e: Exception) { null }
        val icon: Drawable? = try { a.ri.loadIcon(pm) } catch (e: Exception) { null }
        val color = cardColor(banner ?: icon, a.pkg)
        val art = composeAppArt(banner, icon, color, (cardW * 1.3f).toInt(), (cardH * 1.3f).toInt())
        return Tile(0, a.label, "Press OK to open  ·  Hold OK to uninstall", color, art, a.pkg, a.cls)
    }

    private fun composeAppArt(banner: Drawable?, icon: Drawable?, color: Int, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (banner != null) {
            banner.setBounds(0, 0, w, h)
            banner.draw(c)
        } else {
            val wf = w.toFloat()
            val hf = h.toFloat()
            val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            tilePaint.shader = LinearGradient(0f, 0f, 0f, hf,
                withAlpha(shade(color, 1.25f), 180), withAlpha(shade(color, 0.7f), 140), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, wf, hf, tilePaint)

            val bloomPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            bloomPaint.shader = RadialGradient(wf * 0.5f, hf * 0.5f, hf * 0.85f,
                withAlpha(shade(color, 1.6f), 130), withAlpha(color, 0), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, wf, hf, bloomPaint)

            val iconSize = hf * 0.65f
            val left = (wf - iconSize) / 2f
            val top = (hf - iconSize) / 2f - hf * 0.02f
            val radius = iconSize * 0.25f
            val plate = RectF(left, top, left + iconSize, top + iconSize)

            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            shadowPaint.color = Color.argb(75, 0, 0, 0)
            shadowPaint.setShadowLayer(hf * 0.08f, 0f, hf * 0.05f, Color.argb(125, 0, 0, 0))
            c.drawRoundRect(plate, radius, radius, shadowPaint)

            val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            basePaint.shader = LinearGradient(0f, top, 0f, top + iconSize,
                withAlpha(shade(color, 1.6f), 230), withAlpha(shade(color, 0.85f), 210), Shader.TileMode.CLAMP)
            c.drawRoundRect(plate, radius, radius, basePaint)

            if (icon != null) {
                val clipPath = Path()
                clipPath.addRoundRect(plate, radius, radius, Path.Direction.CW)
                c.save()
                c.clipPath(clipPath)
                icon.setBounds(left.toInt(), top.toInt(), (left + iconSize).toInt(), (top + iconSize).toInt())
                icon.draw(c)
                c.restore()
            }

            val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            sheenPaint.shader = LinearGradient(0f, top, 0f, top + iconSize * 0.55f,
                Color.argb(130, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
            c.drawRoundRect(plate, radius, radius, sheenPaint)

            val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            rimPaint.style = Paint.Style.STROKE
            rimPaint.strokeWidth = max(2f, hf * 0.018f)
            rimPaint.shader = LinearGradient(left, top, left + iconSize, top + iconSize,
                intArrayOf(Color.argb(240, 255, 255, 255), Color.argb(35, 255, 255, 255), Color.argb(125, 255, 255, 255)),
                floatArrayOf(0f, 0.50f, 1f), Shader.TileMode.CLAMP)
            val rimRect = RectF(plate)
            rimRect.inset(rimPaint.strokeWidth / 2f, rimPaint.strokeWidth / 2f)
            c.drawRoundRect(rimRect, radius, radius, rimPaint)
        }
        return bmp
    }

    private fun pickFavorites(tiles: List<Tile>): List<Tile> {
        val keys = listOf("netflix", "youtube", "prime", "disney", "shahid", "spotify", "hbo", "apple", "browser", "multimedia")
        val out = ArrayList<Tile>()
        for (k in keys) {
            val m = tiles.firstOrNull { !out.contains(it) && (it.title.lowercase().contains(k) || it.pkg.lowercase().contains(k)) }
            if (m != null) out.add(m)
            if (out.size == 8) return out
        }
        for (t in tiles) { if (!out.contains(t)) out.add(t); if (out.size == 8) break }
        return out
    }

    private fun pickEntertainment(tiles: List<Tile>): List<Tile> {
        val keys = listOf("netflix", "youtube", "prime", "disney", "hbo", "apple", "shahid", "hulu", "peacock", "paramount")
        val out = ArrayList<Tile>()
        for (k in keys) {
            val m = tiles.firstOrNull { !out.contains(it) && (it.title.lowercase().contains(k) || it.pkg.lowercase().contains(k)) }
            if (m != null) out.add(m)
            if (out.size == 8) return out
        }
        return out
    }

    private fun pickGaming(tiles: List<Tile>): List<Tile> {
        val keys = listOf("game", "play", "steam", "xbox", "nvidia", "geforce", "emulator", "retro")
        val out = ArrayList<Tile>()
        for (k in keys) {
            val m = tiles.firstOrNull { !out.contains(it) && (it.title.lowercase().contains(k) || it.pkg.lowercase().contains(k)) }
            if (m != null) out.add(m)
            if (out.size == 6) return out
        }
        return out
    }

    private fun populateApps(tiles: List<Tile>) {
        favRow.removeAllViews()
        recentRow.removeAllViews()
        entertainmentRow.removeAllViews()
        gamingRow.removeAllViews()
        appsRow.removeAllViews()

        val favs = pickFavorites(tiles)
        favs.forEachIndexed { i, t -> favRow.addView(buildPremiumCard(t, i < favs.size - 1)) }

        val entertainment = pickEntertainment(tiles)
        entertainment.forEachIndexed { i, t -> entertainmentRow.addView(buildPremiumCard(t, i < entertainment.size - 1)) }

        val gaming = pickGaming(tiles)
        gaming.forEachIndexed { i, t -> gamingRow.addView(buildPremiumCard(t, i < gaming.size - 1)) }

        tiles.forEachIndexed { i, t -> appsRow.addView(buildPremiumCard(t, i < tiles.size - 1)) }

        populateRecent(tiles)
        updateHeroWithRecent(tiles)
        if (currentFocus == null) favRow.getChildAt(0)?.requestFocus()
    }

    private fun populateRecent(tiles: List<Tile>) {
        val recentJson = prefs.getString("recent_apps", null) ?: ""
        val recentPkgSet = recentJson.split(",").filter { it.isNotBlank() }.take(8).toSet()
        val recentTiles = tiles.filter { it.pkg in recentPkgSet }.take(8)
        recentTiles.forEachIndexed { i, t -> recentRow.addView(buildPremiumCard(t, i < recentTiles.size - 1)) }
    }

    private fun updateHeroWithRecent(tiles: List<Tile>) {
        val recentJson = prefs.getString("recent_apps", null) ?: ""
        val recentPkgSet = recentJson.split(",").filter { it.isNotBlank() }
        if (recentPkgSet.isNotEmpty()) {
            val mostRecentTile = tiles.firstOrNull { it.pkg == recentPkgSet[0] }
            if (mostRecentTile != null) {
                handler.postDelayed({
                    applyHero(mostRecentTile)
                }, 200L)
            }
        }
    }

    private fun addToRecent(pkg: String) {
        val current = prefs.getString("recent_apps", "") ?: ""
        val parts = current.split(",").filter { it.isNotBlank() && it != pkg }.take(7)
        val updated = listOf(pkg) + parts
        prefs.edit().putString("recent_apps", updated.joinToString(",")).apply()
    }

    private fun launchApp(t: Tile) {
        addToRecent(t.pkg)
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

    // ======================= HDMI =======================

    private fun hdmiName(n: Int): String =
        prefs.getString("name_$n", null)?.takeIf { it.isNotBlank() } ?: when (n) {
            1 -> "PLAYSTATION 5 🎮"
            2 -> "TV BOX 📺"
            3 -> "Xbox 🎮"
            4 -> "PC 💻"
            else -> "HDMI $n"
        }

    private fun hwNumber(id: String): Int =
        Regex("HW(\\d+)$").find(id)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE

    private fun inputIdFor(n: Int): String {
        prefs.getString("map_$n", null)?.let { if (it.isNotBlank()) return it }
        try {
            val list = tim?.tvInputList.orEmpty()
                .filter { it.type == TvInputInfo.TYPE_HDMI && it.isPassthroughInput && it.parentId == null }
                .sortedBy { hwNumber(it.id) }
            if (list.size >= n) return list[n - 1].id
        } catch (e: Exception) { }
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
            val name = hdmiName(n)
            val color = when (n) {
                1 -> Color.argb(140, 0, 48, 135)
                2 -> Color.argb(140, 26, 26, 26)
                3 -> Color.argb(140, 16, 124, 16)
                4 -> Color.argb(140, 0, 120, 215)
                else -> 0xFF2C3550.toInt()
            }
            inputsRow.addView(buildPremiumCard(
                Tile(1, name, "HDMI $n  ·  Press OK to switch, hold OK to rename",
                    color, null, id, "", n), true))
        }
        inputsRow.addView(buildPremiumCard(Tile(2, "Settings", "System settings", 0xFF3B4254.toInt(), null), true))
        inputsRow.addView(buildPremiumCard(Tile(5, "THEME", "Color themes and customization", 0xFF7B1FA2.toInt(), null), true))
        val sub = if (latestBuild > myBuild) "Build $latestBuild is available  ·  Press OK to install"
                  else "Press OK to check for updates"
        inputsRow.addView(buildPremiumCard(Tile(3, "Update", sub, 0xFF1976D2.toInt(), null), true))
        inputsRow.addView(buildPremiumCard(Tile(6, "UNINSTALL", "Remove REALM TV OS", 0xFFD32F2F.toInt(), null), true))
        if (focusN in 1..HDMI_COUNT) inputsRow.getChildAt(focusN - 1)?.requestFocus()
    }

    private fun tifWorks(): Boolean = try { tim?.tvInputList?.isNotEmpty() == true } catch (e: Exception) { false }

    private fun openHdmi(t: Tile) {
        if (tifWorks()) {
            try {
                startActivity(Intent(this, HdmiActivity::class.java)
                    .putExtra("input_id", t.pkg).putExtra("name", t.title).putExtra("n", t.n))
                return
            } catch (e: Exception) { }
        }
        Toast.makeText(this, "HDMI switching not available", Toast.LENGTH_LONG).show()
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
        Toast.makeText(this, "Couldn't open Settings", Toast.LENGTH_LONG).show()
    }

    private fun uninstallApp() {
        val uri = Uri.parse("package:$packageName")
        val attempts = listOf(Intent(Intent.ACTION_UNINSTALL_PACKAGE, uri), Intent(Intent.ACTION_DELETE, uri))
        for (i in attempts) {
            try {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
                return
            } catch (e: Exception) { }
        }
        Toast.makeText(this, "Couldn't open uninstall screen", Toast.LENGTH_SHORT).show()
    }

    // ======================= Update System =======================

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

    private fun currentBuild(): Int = try {
        packageManager.getPackageInfo(packageName, 0).versionName?.substringAfterLast('.')?.toIntOrNull() ?: 0
    } catch (e: Exception) { 0 }

    private fun fetchLatest(): Boolean {
        return try {
            val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
            c.connectTimeout = 10000
            c.readTimeout = 10000
            c.setRequestProperty("User-Agent", "RealmTVOS")
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
        val t = tv(msg, 22f, Color.WHITE, true).apply {
            setPadding(u(40f), u(30f), u(40f), u(30f))
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setColor(0xF01C1C24.toInt())
            }
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
            setPadding(u(32f), u(28f), u(32f), u(28f))
            background = GradientDrawable().apply {
                cornerRadius = dp(32).toFloat()
                setColor(0xF01C1C24.toInt())
                setStroke(max(1, u(1.5f)), 0x3DFFFFFF)
            }
        }
        panel.addView(tv("Update Available", 28f, Color.WHITE, true))
        panel.addView(tv("Build $latestBuild is ready to install.", 18f, 0x99FFFFFF.toInt()).apply {
            setPadding(0, u(10f), 0, u(24f))
        })
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val cancel = createPillButton("Later", false) { dlg.dismiss() }
        buttons.addView(cancel, LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = u(16f) })
        buttons.addView(createPillButton("Update Now", true) { dlg.dismiss(); downloadAndInstall(latestUrl ?: return@createPillButton) })
        panel.addView(buttons, LinearLayout.LayoutParams(MATCH, WRAP))
        dlg.setContentView(panel)
        dlg.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(u(560f), WindowManager.LayoutParams.WRAP_CONTENT)
            setDimAmount(0.70f)
        }
        dlg.show()
        cancel.requestFocus()
    }

    private fun checkAndUpdate() {
        showProgress("Checking for updates…")
        Thread {
            val ok = fetchLatest()
            val url = latestUrl
            runOnUiThread {
                if (!ok || url == null) {
                    hideProgress()
                    toast("Couldn't reach GitHub")
                } else if (latestBuild <= myBuild) {
                    hideProgress()
                    toast("You're up to date (build $myBuild)")
                } else {
                    showProgress("Downloading build $latestBuild…  0%")
                    downloadAndInstall(url)
                }
            }
        }.start()
    }

    private fun httpDownload(startUrl: String, out: File) {
        var url = startUrl
        var redirects = 0
        while (true) {
            val c = URL(url).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.setRequestProperty("User-Agent", "RealmTVOS")
            if (c is HttpsURLConnection) {
                c.sslSocketFactory = permissiveSslContext.socketFactory
                c.hostnameVerifier = permissiveHostnameVerifier
            }
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: throw java.io.IOException("redirect without location")
                c.disconnect()
                redirects++
                if (redirects > 6) throw java.io.IOException("too many redirects")
                url = URL(URL(url), loc).toString()
                continue
            }
            if (code != 200) { c.disconnect(); throw java.io.IOException("HTTP $code") }
            val total = c.contentLength
            c.inputStream.use { input ->
                out.outputStream().use { os ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastPct = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        os.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastPct) {
                                lastPct = pct
                                runOnUiThread { showProgress("Downloading build $latestBuild…  $pct%") }
                            }
                        }
                    }
                }
            }
            c.disconnect()
            return
        }
    }

    private fun viaDownloadManager(url: String, out: File): Boolean {
        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val req = DownloadManager.Request(Uri.parse(url))
            .setTitle("REALM TV OS update")
            .setDestinationInExternalFilesDir(this, null, "update-dl.apk")
        val id = dm.enqueue(req)
        val deadline = SystemClock.elapsedRealtime() + 180000L
        while (SystemClock.elapsedRealtime() < deadline) {
            val cur = dm.query(DownloadManager.Query().setFilterById(id))
            try {
                if (cur != null && cur.moveToFirst()) {
                    val st = cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val so = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val tot = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    if (st == DownloadManager.STATUS_SUCCESSFUL) {
                        val pfd = dm.openDownloadedFile(id)
                        java.io.FileInputStream(pfd.fileDescriptor).use { i -> out.outputStream().use { o -> i.copyTo(o) } }
                        pfd.close()
                        dm.remove(id)
                        return true
                    }
                    if (st == DownloadManager.STATUS_FAILED) { dm.remove(id); return false }
                    if (tot > 0) {
                        val pct = (so * 100 / tot).toInt()
                        runOnUiThread { showProgress("Downloading build $latestBuild…  $pct%") }
                    }
                }
            } finally { cur?.close() }
            Thread.sleep(700)
        }
        dm.remove(id)
        return false
    }

    private fun installFile(file: File) {
        var session: PackageInstaller.Session? = null
        try {
            val installer = packageManager.packageInstaller
            val id = installer.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
            val s = installer.openSession(id)
            session = s
            java.io.FileInputStream(file).use { input ->
                s.openWrite("realm_tvos.apk", 0, file.length()).use { o ->
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
            runOnUiThread { hideProgress(); toast("Install failed — ${e.javaClass.simpleName}") }
        }
    }

    private fun downloadAndInstall(url: String) {
        Thread {
            val file = File(cacheDir, "update.apk")
            var ok = false
            var err = ""
            try {
                httpDownload(url, file)
                ok = true
            } catch (e: Exception) {
                err = e.javaClass.simpleName + ": " + (e.message ?: "").take(70)
            }
            if (!ok) {
                try {
                    runOnUiThread { showProgress("Trying alternative download…") }
                    ok = viaDownloadManager(url, file)
                    if (!ok) err += "  |  system downloader failed"
                } catch (e: Exception) {
                    err += "  |  " + e.javaClass.simpleName
                }
            }
            if (ok) installFile(file)
            else runOnUiThread { hideProgress(); toast("Download failed — $err") }
        }.start()
    }

    // ======================= Premium Cards =======================

    private fun roundOutline(r: Float) = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, r)
        }
    }

    private fun buildPremiumCard(t: Tile, notLast: Boolean): View {
        val r = dp(28).toFloat()
        val frame = FrameLayout(this)
        frame.outlineProvider = roundOutline(r)

        when (t.kind) {
            0 -> {
                val iv = RoundedBitmapView(this)
                iv.radius = r
                iv.setBitmap(t.art)
                frame.addView(iv, FrameLayout.LayoutParams(MATCH, MATCH))
            }
            1 -> {
                val isPs5 = t.title.contains("PLAYSTATION", ignoreCase = true)
                val isTvBox = t.title.contains("TV BOX", ignoreCase = true)
                val isXbox = t.title.contains("Xbox", ignoreCase = true)
                val isPc = t.title.contains("PC", ignoreCase = true)

                val bgColor = when {
                    isPs5 -> Color.argb(140, 0, 48, 135)
                    isTvBox -> Color.argb(140, 26, 26, 26)
                    isXbox -> Color.argb(140, 16, 124, 16)
                    isPc -> Color.argb(140, 0, 120, 215)
                    else -> Color.argb(140, 150, 165, 215)
                }
                val bgEnd = when {
                    isPs5 -> Color.argb(90, 0, 20, 80)
                    isTvBox -> Color.argb(90, 10, 10, 10)
                    isXbox -> Color.argb(90, 8, 62, 8)
                    isPc -> Color.argb(90, 0, 60, 107)
                    else -> Color.argb(90, 70, 85, 135)
                }
                frame.background = GlassCardBackground(r, bgColor, bgEnd, 0f, false)
                val iconW = (cardW * 0.42f).toInt()

                if (isPs5) {
                    frame.addView(GameControllerIconView(this),
                        FrameLayout.LayoutParams((cardH * 0.48f).toInt(), (cardH * 0.48f).toInt(), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                            topMargin = (cardH * 0.18f).toInt()
                        })
                } else {
                    frame.addView(HdmiPortIconView(this),
                        FrameLayout.LayoutParams(iconW, (iconW * 0.46f).toInt(), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                            topMargin = (cardH * 0.24f).toInt()
                        })
                }

                frame.addView(tv(t.title, 16f, Color.WHITE, true).apply {
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER
                }, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM).apply {
                    bottomMargin = u(12f)
                    leftMargin = u(10f)
                    rightMargin = u(10f)
                })

                val live = isInputLive(t.n)
                val badge = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(u(10f), u(5f), u(12f), u(5f))
                    background = GradientDrawable().apply {
                        cornerRadius = u(16f).toFloat()
                        setColor(0x5D000000)
                    }
                }
                badge.addView(View(this).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(if (live) 0xFF34C759.toInt() else 0xFF6B7280.toInt())
                    }
                }, LinearLayout.LayoutParams(u(8f), u(8f)).apply { rightMargin = u(8f) })
                val badgeText = when {
                    isPs5 -> "PS5"
                    isTvBox -> "TV"
                    isXbox -> "XBOX"
                    isPc -> "PC"
                    else -> "PORT ${t.n}"
                }
                badge.addView(tv(badgeText, 11f, 0xCCFFFFFF.toInt(), true))
                frame.addView(badge, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START).apply {
                    leftMargin = u(10f)
                    topMargin = u(10f)
                })
            }
            3 -> {
                frame.background = GlassCardBackground(r, Color.argb(220, 80, 150, 255), Color.argb(190, 35, 90, 210), 0f, false)
                val gs = (cardH * 0.42f).toInt()
                frame.addView(UpdateIconView(this),
                    FrameLayout.LayoutParams(gs, gs, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                        topMargin = (cardH * 0.16f).toInt()
                    })
                frame.addView(tv("Update", 16f, Color.WHITE, true).apply { gravity = Gravity.CENTER },
                    FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM).apply { bottomMargin = u(12f) })
                if (latestBuild > myBuild) {
                    frame.addView(tv("NEW", 11f, Color.WHITE, true).apply {
                        setPadding(u(10f), u(4f), u(10f), u(4f))
                        background = GradientDrawable().apply {
                            cornerRadius = u(14f).toFloat()
                            setColor(0xFFFF3B30.toInt())
                        }
                    }, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.END).apply {
                        topMargin = u(10f)
                        rightMargin = u(10f)
                    })
                }
            }
            5 -> {
                frame.background = GlassCardBackground(r, Color.argb(210, 123, 31, 162), Color.argb(170, 94, 23, 130), 0f, false)
                val gs = (cardH * 0.44f).toInt()
                frame.addView(SidebarIconView(this),
                    FrameLayout.LayoutParams(gs, gs, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                        topMargin = (cardH * 0.18f).toInt()
                    })
                frame.addView(tv("THEME", 16f, Color.WHITE, true).apply { gravity = Gravity.CENTER },
                    FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM).apply { bottomMargin = u(12f) })
            }
            6 -> {
                frame.background = GlassCardBackground(r, Color.argb(230, 230, 38, 38), Color.argb(190, 190, 28, 28), 0f, false)
                val gs = (cardH * 0.44f).toInt()
                val deleteIcon = TextView(this).apply {
                    text = "×"
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, u(64f).toFloat())
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                }
                frame.addView(deleteIcon, FrameLayout.LayoutParams(gs, gs, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                    topMargin = (cardH * 0.14f).toInt()
                })
                frame.addView(tv("UNINSTALL", 16f, Color.WHITE, true).apply { gravity = Gravity.CENTER },
                    FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM).apply { bottomMargin = u(12f) })
            }
            else -> {
                frame.background = GlassCardBackground(r, Color.argb(140, 160, 165, 180), Color.argb(90, 75, 80, 100), 0f, false)
                val gs = (cardH * 0.44f).toInt()
                frame.addView(SettingsIconView(this),
                    FrameLayout.LayoutParams(gs, gs, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                        topMargin = (cardH * 0.18f).toInt()
                    })
                frame.addView(tv("Settings", 16f, Color.WHITE, true).apply { gravity = Gravity.CENTER },
                    FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM).apply { bottomMargin = u(12f) })
            }
        }

        val border = View(this).apply {
            background = GlassCardBackground(r, 0, 0, 1f, true)
            alpha = 0.65f
        }
        frame.addView(border, FrameLayout.LayoutParams(MATCH, MATCH))

        val label = tv(t.title, 17f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            alpha = 0f
            setPadding(0, u(14f), 0, 0)
        }

        val box = FastCardBox(this)
        box.orientation = LinearLayout.VERTICAL
        box.isFocusable = true
        box.isClickable = true
        box.clipChildren = false
        box.alpha = 0.75f
        box.pivotX = cardW / 2f
        box.pivotY = cardH / 2f
        if (Build.VERSION.SDK_INT >= 26) box.setDefaultFocusHighlightEnabled(false)
        box.addView(frame, LinearLayout.LayoutParams(cardW, cardH))
        box.addView(label, LinearLayout.LayoutParams(cardW, WRAP))
        box.layoutParams = LinearLayout.LayoutParams(cardW, WRAP).apply { rightMargin = if (notLast) gap else 0 }

        box.setOnClickListener {
            when (t.kind) {
                0 -> launchApp(t)
                1 -> openHdmi(t)
                3 -> checkAndUpdate()
                5 -> showThemeDialog()
                6 -> uninstallApp()
                else -> openSettings()
            }
        }
        if (t.kind == 1) {
            box.isLongClickable = true
            box.setOnLongClickListener { showRenameDialog(t.n); true }
        } else if (t.kind == 0) {
            box.isLongClickable = true
            box.setOnLongClickListener { confirmUninstall(t); true }
        }

        box.setOnFocusChangeListener { v, f ->
            val s = if (f) 1.12f else 1f
            v.animate().scaleX(s).scaleY(s).alpha(if (f) 1f else 0.75f)
                .setDuration(120).setInterpolator(DecelerateInterpolator()).start()
            border.alpha = if (f) 1f else 0.65f
            label.alpha = if (f && t.kind == 0) 1f else 0f
            if (f) onTileFocused(t)
        }
        return box
    }

    // ======================= Uninstall =======================

    private fun confirmUninstall(t: Tile) {
        val ai = try { packageManager.getApplicationInfo(t.pkg, 0) } catch (e: Exception) { null }
        val isSystem = ai != null &&
            (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
            (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
        if (isSystem) {
            Toast.makeText(this, "${t.title} is a system app and can't be uninstalled.", Toast.LENGTH_LONG).show()
            return
        }
        val dlg = Dialog(this)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(u(32f), u(28f), u(32f), u(28f))
            background = GradientDrawable().apply {
                cornerRadius = dp(32).toFloat()
                setColor(0xF01C1C24.toInt())
                setStroke(max(1, u(1.5f)), 0x3DFFFFFF)
            }
        }
        panel.addView(tv("Uninstall ${t.title}?", 26f, Color.WHITE, true))
        panel.addView(tv("This will remove the app from your TV.", 18f, 0x99FFFFFF.toInt()).apply {
            setPadding(0, u(10f), 0, u(24f))
        })
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val cancel = createPillButton("Cancel", false) { dlg.dismiss() }
        buttons.addView(cancel, LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = u(16f) })
        buttons.addView(createPillButton("Uninstall", true) { dlg.dismiss(); uninstall(t.pkg) })
        panel.addView(buttons, LinearLayout.LayoutParams(MATCH, WRAP))
        dlg.setContentView(panel)
        dlg.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(u(560f), WindowManager.LayoutParams.WRAP_CONTENT)
            setDimAmount(0.70f)
        }
        dlg.show()
        cancel.requestFocus()
    }

    private fun uninstall(pkg: String) {
        val uri = Uri.parse("package:$pkg")
        val attempts = listOf(Intent(Intent.ACTION_UNINSTALL_PACKAGE, uri), Intent(Intent.ACTION_DELETE, uri))
        for (i in attempts) {
            try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); return } catch (e: Exception) { }
        }
        Toast.makeText(this, "Couldn't open uninstall screen", Toast.LENGTH_LONG).show()
    }

    // ======================= Theme Dialog =======================

    private fun showThemeDialog() {
        val dlg = Dialog(this)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(u(32f), u(28f), u(32f), u(28f))
            background = GradientDrawable().apply {
                cornerRadius = dp(32).toFloat()
                setColor(0xF01C1C24.toInt())
                setStroke(max(1, u(1.5f)), 0x3DFFFFFF)
            }
        }
        panel.addView(tv("Color Theme", 28f, Color.WHITE, true))
        panel.addView(tv("Choose your preferred theme", 16f, 0x99FFFFFF.toInt()).apply {
            setPadding(0, u(8f), 0, u(24f))
        })

        val themeRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, u(10f))
        }
        val themeRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, u(20f))
        }
        val themeButtons = ArrayList<TextView>()
        val themes = ThemeMode.values()
        for (i in themes.indices) {
            val theme = themes[i]
            val btn = TextView(this).apply {
                text = theme.displayName
                setTextSize(TypedValue.COMPLEX_UNIT_PX, u(12f).toFloat())
                setTextColor(Color.WHITE)
                setPadding(u(12f), u(10f), u(12f), u(10f))
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    cornerRadius = u(14f).toFloat()
                    setColor(theme.colors[0])
                }
                isFocusable = true
                isClickable = true
            }
            btn.setOnClickListener {
                themeButtons.forEach { it.alpha = 0.5f }
                btn.alpha = 1f
                currentThemeMode = theme
                premiumBg.setThemeMode(theme)
                prefs.edit().putString("theme_mode", theme.displayName).apply()
            }
            if (theme == currentThemeMode) btn.alpha = 1f else btn.alpha = 0.5f
            themeButtons.add(btn)
            if (i < 6) {
                themeRow1.addView(btn, LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = u(8f) })
            } else {
                themeRow2.addView(btn, LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = u(8f) })
            }
        }
        panel.addView(themeRow1)
        panel.addView(themeRow2)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, u(20f), 0, 0)
        }
        buttons.addView(createPillButton("Close", false) { dlg.dismiss() })
        panel.addView(buttons, LinearLayout.LayoutParams(MATCH, WRAP))
        dlg.setContentView(panel)
        dlg.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(u(800f), WindowManager.LayoutParams.WRAP_CONTENT)
            setDimAmount(0.70f)
        }
        dlg.show()
        themeButtons.find { it.text == currentThemeMode.displayName }?.requestFocus()
    }

    // ======================= Rename Dialog =======================

    private fun showRenameDialog(n: Int) {
        val dlg = Dialog(this)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(u(32f), u(28f), u(32f), u(28f))
            background = GradientDrawable().apply {
                cornerRadius = dp(32).toFloat()
                setColor(0xF01C1C24.toInt())
                setStroke(max(1, u(1.5f)), 0x3DFFFFFF)
            }
        }
        panel.addView(tv("Rename HDMI $n", 24f, Color.WHITE, true))
        panel.addView(tv("Choose a preset or enter a custom name.", 14f, 0x99FFFFFF.toInt()).apply {
            setPadding(0, u(8f), 0, u(20f))
        })

        val edit = EditText(this).apply {
            setText(prefs.getString("name_$n", "") ?: "")
            hint = "HDMI $n"
            setHintTextColor(0x66FFFFFF)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, u(20f).toFloat())
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_DONE
            filters = arrayOf(InputFilter.LengthFilter(28))
            setPadding(u(20f), u(14f), u(20f), u(14f))
            background = GradientDrawable().apply {
                cornerRadius = u(18f).toFloat()
                setColor(0x22FFFFFF)
                setStroke(max(2, u(2f)), 0x66FFFFFF)
            }
            setSelection(text.length)
        }

        val presets = listOf(
            "PLAYSTATION 5 🎮" to 0xFF003087.toInt(),
            "TV BOX 📺" to 0xFF1A1A1A.toInt(),
            "Xbox 🎮" to 0xFF107C10.toInt(),
            "Nintendo Switch 🎮" to 0xFFE60012.toInt(),
            "PC 💻" to 0xFF0078D7.toInt(),
            "Custom" to 0xFF3B4254.toInt()
        )
        val presetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, u(20f))
        }
        val presetButtons = ArrayList<TextView>()
        for ((name, color) in presets) {
            val btn = TextView(this).apply {
                text = name
                setTextSize(TypedValue.COMPLEX_UNIT_PX, u(15f).toFloat())
                setTextColor(Color.WHITE)
                setPadding(u(14f), u(10f), u(14f), u(10f))
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    cornerRadius = u(14f).toFloat()
                    setColor(color)
                }
                isFocusable = true
                isClickable = true
            }
            btn.setOnClickListener {
                presetButtons.forEach { it.alpha = 0.5f }
                btn.alpha = 1f
                edit.setText(if (name == "Custom") "" else name)
                edit.setSelection(edit.text.length)
            }
            presetButtons.add(btn)
            presetRow.addView(btn, LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = u(10f) })
        }
        presetButtons.first().alpha = 1f
        panel.addView(presetRow)

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
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, u(20f), 0, 0)
        }
        buttons.addView(createPillButton("Cancel", false) { dlg.dismiss() },
            LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = u(16f) })
        buttons.addView(createPillButton("Save", true) { save() })
        panel.addView(buttons, LinearLayout.LayoutParams(MATCH, WRAP))
        dlg.setContentView(panel)
        dlg.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(u(560f), WindowManager.LayoutParams.WRAP_CONTENT)
            setDimAmount(0.70f)
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

    // ======================= UI Helpers =======================

    private fun createPillButton(text: String, primary: Boolean, onClick: () -> Unit): TextView {
        val t = tv(text, 16f, Color.WHITE, true)
        fun paint(f: Boolean) {
            t.background = GradientDrawable().apply {
                cornerRadius = u(32f).toFloat()
                setColor(if (f) Color.WHITE else if (primary) 0xFF0A84FF.toInt() else 0x3DFFFFFF)
            }
            t.setTextColor(if (f) Color.BLACK else Color.WHITE)
        }
        t.gravity = Gravity.CENTER
        t.isFocusable = true
        t.isClickable = true
        t.setPadding(u(28f), u(12f), u(28f), u(12f))
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

    // ======================= Colors =======================

    private fun withAlpha(c: Int, a: Int): Int = Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))

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
