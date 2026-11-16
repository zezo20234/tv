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
// How many HDMI cards to always show
private const val HDMI_COUNT = 3
// GitHub repo that holds your releases
private const val REPO = "zezo20234/tv"
private const val ACTION_INSTALL = "com.tvhome.app.INSTALL_RESULT"

// Permissive trust manager for HTTPS (fixes SSLHandshakeException on TVs with outdated CA stores)
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

// Permissive hostname verifier (fixes SSL hostname validation issues)
private val permissiveHostnameVerifier = javax.net.ssl.HostnameVerifier { hostname: String, session: javax.net.ssl.SSLSession -> true }

// ======================= Custom views (all cheap to draw) =======================

// Premium Dark UI Color System
object PremiumColors {
    // Background colors
    const val BACKGROUND_DEEP = 0xFF0A0C10.toInt()
    const val BACKGROUND_CHARCOAL = 0xFF14161C.toInt()
    const val BACKGROUND_CARD = 0xFF1A1D24.toInt()
    const val BACKGROUND_CARD_HOVER = 0xFF22262E.toInt()

    // Text colors
    const val TEXT_PRIMARY = 0xFFFFFFFF.toInt()
    const val TEXT_SECONDARY = 0x99FFFFFF.toInt()
    const val TEXT_TERTIARY = 0x66FFFFFF.toInt()

    // Emerald Green accent (primary)
    const val ACCENT_EMERALD = 0xFF10B981.toInt()
    const val ACCENT_EMERALD_DARK = 0xFF059669.toInt()
    const val ACCENT_EMERALD_LIGHT = 0xFF34D399.toInt()

    // Secondary accents
    const val ACCENT_GOLD = 0xFFF59E0B.toInt()
    const val ACCENT_AMETHYST = 0xFF8B5CF6.toInt()
    const val ACCENT_ROSE = 0xFFF43F5E.toInt()

    // Surface colors
    const val SURFACE_GLASS = 0x33FFFFFF.toInt()
    const val SURFACE_GLASS_DARK = 0x1AFFFFFF.toInt()
    const val BORDER_SUBTLE = 0x1AFFFFFF.toInt()
    const val BORDER_FOCUS = 0x40FFFFFF.toInt()
}

enum class ColorMode(val displayName: String, val colors: List<Int>) {
    EMERALD("Emerald Premium", listOf(PremiumColors.ACCENT_EMERALD, PremiumColors.ACCENT_EMERALD_DARK, PremiumColors.ACCENT_EMERALD_LIGHT, 0xFF065F46.toInt())),
    GOLD("Golden Luxe", listOf(PremiumColors.ACCENT_GOLD, 0xFFD97706.toInt(), 0xFFFBBF24.toInt(), 0xFF92400E.toInt())),
    AMETHYST("Amethyst Night", listOf(PremiumColors.ACCENT_AMETHYST, 0xFF7C3AED.toInt(), 0xFFA78BFA.toInt(), 0xFF5B21B6.toInt())),
    ROSE("Rose Glow", listOf(PremiumColors.ACCENT_ROSE, 0xFFE11D48.toInt(), 0xFFFB7185.toInt(), 0xBE185D.toInt())),
    OCEAN("Ocean Deep", listOf(0xFF0EA5E9.toInt(), 0xFF0284C7.toInt(), 0xFF38BDF8.toInt(), 0xFF075985.toInt())),
    CHARCOAL("Pure Dark", listOf(0xFF374151.toInt(), 0xFF1F2937.toInt(), 0xFF4B5563.toInt(), 0xFF111827.toInt()))
}

/** Premium dark background with subtle accent gradients, pre-rendered once into a small bitmap. */
class StaticBackground(ctx: Context) : View(ctx) {
    private var bmp: Bitmap? = null
    private val dst = Rect()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var colorMode: ColorMode = ColorMode.EMERALD

    fun setColorMode(mode: ColorMode) {
        if (colorMode != mode) {
            colorMode = mode
            bmp = null
            requestLayout()
        }
    }

    private fun blob(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, alpha: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(cx, cy, r, intArrayOf(
            Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)),
            Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))
        ), null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, p)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        val bw = max(8, w / 3); val bh = max(8, h / 3)
        val b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        // Deep charcoal background
        c.drawColor(PremiumColors.BACKGROUND_DEEP)

        // Subtle gradient overlay
        val gradient = Paint(Paint.ANTI_ALIAS_FLAG)
        gradient.shader = LinearGradient(0f, 0f, 0f, bh.toFloat(),
            intArrayOf(PremiumColors.BACKGROUND_CHARCOAL, PremiumColors.BACKGROUND_DEEP),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, bw.toFloat(), bh.toFloat(), gradient)

        // Very subtle accent blobs for premium feel
        val m = max(bw, bh).toFloat()
        val colors = colorMode.colors
        // Much more subtle than before - premium design shouldn't be flashy
        blob(c, bw * 0.15f, bh * 0.10f, m * 0.45f, colors[0], 35)
        blob(c, bw * 0.85f, bh * 0.20f, m * 0.35f, colors[1], 25)
        blob(c, bw * 0.50f, bh * 0.90f, m * 0.40f, colors[2], 20)
        blob(c, bw * 0.92f, bh * 0.88f, m * 0.25f, colors[3], 15)
        bmp = b
    }

    override fun onDraw(canvas: Canvas) {
        val b = bmp ?: return
        dst.set(0, 0, width, height)
        canvas.drawBitmap(b, null, dst, paint)
    }
}

/** Premium glass surface: refined translucent fill, subtle sheen and elegant rim. */
class GlassDrawable(
    private val radius: Float,
    private val fillTop: Int,
    private val fillBottom: Int,
    private val rimStrength: Float,
    private val sheen: Boolean
) : Drawable() {
    private val rect = RectF()
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheenP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    override fun onBoundsChange(b: Rect) {
        super.onBoundsChange(b)
        val w = b.width().toFloat(); val h = b.height().toFloat()
        if (w <= 0f || h <= 0f) return
        fillP.shader = LinearGradient(0f, 0f, 0f, h, fillTop, fillBottom, Shader.TileMode.CLAMP)
        // More subtle sheen for premium feel
        sheenP.shader = LinearGradient(0f, 0f, 0f, h * 0.45f, 0x25FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        val a = (255 * rimStrength).toInt().coerceIn(0, 255)
        // More elegant rim gradient
        rimP.shader = LinearGradient(0f, 0f, w, h,
            intArrayOf(Color.argb((a * 0.7f).toInt(), 255, 255, 255),
                Color.argb((a * 0.15f).toInt(), 255, 255, 255),
                Color.argb((a * 0.35f).toInt(), 255, 255, 255)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        rimP.strokeWidth = max(1f, min(w, h) * 0.012f)
        val inset = rimP.strokeWidth / 2f
        rect.set(inset, inset, w - inset, h - inset)
    }

    override fun draw(canvas: Canvas) {
        if (rect.isEmpty) return
        canvas.drawRoundRect(rect, radius, radius, fillP)
        if (sheen) canvas.drawRoundRect(rect, radius, radius, sheenP)
        if (rimStrength > 0f) canvas.drawRoundRect(rect, radius, radius, rimP)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** Card container: no offscreen layer when dimmed (this was a big source of lag). */
class CardBox(ctx: Context) : LinearLayout(ctx) {
    override fun hasOverlappingRendering(): Boolean = false
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
        p.color = color; dot.color = color; p.strokeWidth = h * 0.085f
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

class UpdateView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = 0xFFFFFFFF.toInt(); strokeCap = Paint.Cap.ROUND
    }
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val cx = w / 2f
        p.strokeWidth = h * 0.09f
        c.drawLine(cx, h * 0.10f, cx, h * 0.62f, p)
        c.drawLine(cx - w * 0.22f, h * 0.42f, cx, h * 0.64f, p)
        c.drawLine(cx + w * 0.22f, h * 0.42f, cx, h * 0.64f, p)
        c.drawLine(w * 0.20f, h * 0.88f, w * 0.80f, h * 0.88f, p)
    }
}

/** PS5 Logo: stylized PlayStation shape with Sony blue color */
class Ps5LogoView(ctx: Context) : View(ctx) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 0, 48, 135)
        style = Paint.Style.FILL
    }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val scale = min(w, h) * 0.38f

        outline.strokeWidth = scale * 0.12f

        // P shape - simplified PlayStation logo
        c.save()
        c.translate(cx, cy)
        c.scale(scale / 100f, scale / 100f)

        // Vertical bar of P
        c.drawRoundRect(-30f, -50f, -10f, 50f, 8f, 8f, fill)
        c.drawRoundRect(-30f, -50f, -10f, 50f, 8f, 8f, outline)

        // Top curve of P
        c.drawRoundRect(-10f, -50f, 40f, -10f, 8f, 8f, fill)
        c.drawRoundRect(-10f, -50f, 40f, -10f, 8f, 8f, outline)

        // Middle curve of P
        c.drawRoundRect(-10f, -15f, 30f, 15f, 8f, 8f, fill)
        c.drawRoundRect(-10f, -15f, 30f, 15f, 8f, 8f, outline)

        // S shape below
        val sPath = Path()
        sPath.moveTo(-25f, 30f)
        sPath.quadTo(-45f, 30f, -45f, 45f)
        sPath.quadTo(-45f, 60f, -20f, 60f)
        sPath.quadTo(10f, 60f, 10f, 75f)
        sPath.quadTo(10f, 90f, -15f, 90f)
        c.drawPath(sPath, fill)
        c.drawPath(sPath, outline)

        c.restore()
    }
}

/** Options/Settings button icon - hamburger menu */
class OptionsView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFE6E9F0.toInt()
        strokeCap = Paint.Cap.ROUND
    }
    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f

        // Three horizontal lines (hamburger menu style)
        p.strokeWidth = r * 0.20f
        canvas.drawLine(cx - r * 0.5f, cy - r * 0.25f, cx + r * 0.5f, cy - r * 0.25f, p)
        canvas.drawLine(cx - r * 0.5f, cy, cx + r * 0.5f, cy, p)
        canvas.drawLine(cx - r * 0.5f, cy + r * 0.25f, cx + r * 0.5f, cy + r * 0.25f, p)
    }
}

/** Profile circle icon (like iOS user profile) */
class ProfileView(ctx: Context) : View(ctx) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF5AC8FA.toInt() }
    private val face = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val r = min(w, h) / 2f

        // Circle background
        canvas.drawCircle(cx, cy, r, fill)

        // Simple face (head + shoulders)
        val headR = r * 0.35f
        canvas.drawCircle(cx, cy - r * 0.15f, headR, face)
        // Shoulders (arc)
        val shoulderRect = RectF(cx - r * 0.6f, cy + r * 0.15f, cx + r * 0.6f, cy + r * 1.2f)
        canvas.drawArc(shoulderRect, 0f, 180f, false, face)
    }
}

/** Moon icon (like iOS Do Not Disturb) */
class MoonView(ctx: Context) : View(ctx) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFD60A.toInt() }
    private val path = Path()
    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val r = min(w, h) / 2f

        path.reset()
        // Crescent moon shape
        path.addCircle(cx, cy, r * 0.85f, Path.Direction.CW)
        val cutout = Path()
        cutout.addCircle(cx + r * 0.3f, cy - r * 0.1f, r * 0.7f, Path.Direction.CW)
        path.op(cutout, Path.Op.DIFFERENCE)
        canvas.drawPath(path, fill)
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

    /** Prayer times data */
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
        val date: String,
        val hijri: String = ""
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
    private lateinit var recentRow: LinearLayout
    private lateinit var inputsRow: LinearLayout
    private lateinit var appsRow: LinearLayout
    private lateinit var clockText: TextView
    private lateinit var dateText: TextView
    private lateinit var weatherIcon: WeatherView
    private lateinit var dockRow: LinearLayout
    private lateinit var profile1View: View
    private lateinit var profile2View: View
    private lateinit var moonView: View

    // Prayer times - DISABLED FOR iPHONE UI
    private var prayerTimes: PrayerTimesData? = null
    private var lastPrayerFetch = -1L
    private var prayerFetchFailed = false
    private lateinit var prayerText: TextView
    private lateinit var prayerNextText: TextView
    private lateinit var prayerText2: TextView
    private lateinit var prayerNextText2: TextView
    private val dateFmt = java.text.SimpleDateFormat("EEEE, MMMM d", java.util.Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("Asia/Riyadh")
    }
    private val clockFmt = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("Asia/Riyadh")
    }
    private lateinit var weatherIcon: WeatherView
    private lateinit var wifi: WifiView
    private lateinit var prefs: SharedPreferences
    private var tim: TvInputManager? = null
    private var lastSig = ""
    @Volatile private var loading = false
    private var pendingTile: Tile? = null
    private lateinit var heroUpdate: UpdateView
    private var lastUpdateCheck = -1L
    @Volatile private var latestBuild = 0
    @Volatile private var latestUrl: String? = null
    private var progressDlg: Dialog? = null
    private var progressTv: TextView? = null
    private val myBuild: Int by lazy { currentBuild() }
    private lateinit var staticBg: StaticBackground
    private var currentColorMode: ColorMode = ColorMode.EMERALD

    // Prayer times
    private var prayerTimes: PrayerTimesData? = null
    private var lastPrayerFetch = -1L
    private var prayerFetchFailed = false
    private lateinit var prayerText: TextView
    private lateinit var prayerNextText: TextView
    private lateinit var prayerText2: TextView
    private lateinit var prayerNextText2: TextView

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
        PremiumColors.ACCENT_EMERALD, PremiumColors.ACCENT_GOLD, PremiumColors.ACCENT_AMETHYST,
        PremiumColors.ACCENT_ROSE, 0xFF0EA5E9.toInt(), 0xFF8B5CF6.toInt(), 0xFFF43F5E.toInt(), 0xFF10B981.toInt()
    )

    private val ticker = object : Runnable {
        override fun run() {
            val online = isOnline()
            wifi.on = online
            updateClock()

            if (lastUpdateCheck < 0 || SystemClock.elapsedRealtime() - lastUpdateCheck > 6 * 60 * 60 * 1000L) {
                lastUpdateCheck = SystemClock.elapsedRealtime()
                Thread {
                    if (fetchLatest() && latestBuild > myBuild) {
                        runOnUiThread { if (currentFocus?.parent !== inputsRow) refreshInputs(0) }
                    }
                }.start()
            }
            handler.postDelayed(this, 1000L)
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

        // Load saved color mode
        val savedMode = prefs.getString("color_mode", null)
        currentColorMode = ColorMode.values().find { it.displayName == savedMode } ?: ColorMode.EMERALD

        gap = u(16f)
        padH = u(46f)
        cardW = (W * 0.19f).toInt()
        cardH = cardW * 9 / 16

        val root = FrameLayout(this)
        staticBg = StaticBackground(this)
        staticBg.setColorMode(currentColorMode)
        root.addView(staticBg, MATCH, MATCH)

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(buildTopBar(), LinearLayout.LayoutParams(MATCH, WRAP))

        favRow = row(); recentRow = row(); inputsRow = row(); appsRow = row()
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
        content.addView(header("Keep watching")); content.addView(shelf(favRow))
        content.addView(header("Recent")); content.addView(shelf(recentRow))
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

        // Dock at bottom (iPhone-style)
        dockRow = row()
        dockRow.setPadding(padH, u(20f), padH, u(24f))
        dockRow.background = GlassDrawable(u(34f).toFloat(),
            Color.argb(60, 255, 255, 255), Color.argb(30, 255, 255, 255), 0.8f, true)
        col.addView(dockRow, LinearLayout.LayoutParams(MATCH, WRAP))

        root.addView(col, MATCH, MATCH)
        setContentView(root)

        val f = IntentFilter(ACTION_INSTALL)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(installReceiver, f, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(installReceiver, f)

        // Auto-check for updates on launch
        Thread {
            if (fetchLatest() && latestBuild > myBuild) {
                runOnUiThread {
                    showUpdateDialog()
                }
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

    private fun capsule(): GlassDrawable = GlassDrawable(u(40f).toFloat(),
        Color.argb(45, 255, 255, 255), Color.argb(20, 255, 255, 255), 0.7f, true)

    private fun buildTopBar(): View {
        val bar = FrameLayout(this).apply {
            setPadding(padH, u(32f), padH, 0)
        }

        // Left side: Profile icons
        val leftSide = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        profile1View = ProfileView(this)
        leftSide.addView(profile1View, LinearLayout.LayoutParams(u(48f), u(48f)).apply { rightMargin = u(12f) })
        profile2View = ProfileView(this).apply { alpha = 0.5f }
        leftSide.addView(profile2View, LinearLayout.LayoutParams(u(48f), u(48f)))
        bar.addView(leftSide, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.START or Gravity.TOP))

        // Center: Large clock and date (iPhone lock screen style)
        val centerCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        clockText = tv("", 90f, PremiumColors.TEXT_PRIMARY, true).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.05f
        }
        dateText = tv("", 22f, PremiumColors.TEXT_SECONDARY, false).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.02f
        }
        centerCol.addView(clockText)
        centerCol.addView(dateText, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = u(4f) })
        bar.addView(centerCol, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER or Gravity.TOP))

        // Right side: Moon icon + wifi
        val rightSide = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        moonView = MoonView(this)
        rightSide.addView(moonView, LinearLayout.LayoutParams(u(44f), u(44f)).apply { rightMargin = u(16f) })
        wifi = WifiView(this)
        rightSide.addView(wifi, LinearLayout.LayoutParams(u(32f), u(26f)))
        bar.addView(rightSide, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.END or Gravity.TOP))

        updateClock()
        return bar
    }

    private fun updateClock() {
        val t = clockFmt.format(java.util.Date())
        if (clockText.text.toString() != t) clockText.text = t
        val d = dateFmt.format(java.util.Date())
        if (dateText.text.toString() != d) dateText.text = d
    }

    // Hero section - DISABLED FOR iPHONE UI

    private fun buildHero(): View {
        return View(this)
    }

    private fun applyHero(t: Tile) {
        // Hero disabled for iPhone-style UI
    }

    /** 1 = PlayStation, 2 = TV box, 0 = plain HDMI. Based on the name, so renaming a port changes its picture. */
    private fun deviceOf(title: String): Int {
        val u = title.uppercase()
        return when {
            u.contains("PLAYSTATION") || u.contains("PS5") -> 1
            u.contains("TV BOX") || u.contains("TVBOX") -> 2
            else -> 0
        }
    }

    private fun onTileFocused(t: Tile) {
        // Hero focus disabled for iPhone-style UI
    }

    // ---------- prayer times (Aladhan API, no key) - DISABLED FOR iPHONE UI ----------

    private fun httpGet(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "TVHome")
        if (c is HttpsURLConnection) {
            c.sslSocketFactory = permissiveSslContext.socketFactory
        }
        try { return c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
    }

    private val riyadhTz: java.util.TimeZone = java.util.TimeZone.getTimeZone("Asia/Riyadh")
    private var prayerDay = ""

    private val hijriMonths = arrayOf("Muharram", "Safar", "Rabi al-Awwal", "Rabi al-Thani",
        "Jumada al-Ula", "Jumada al-Thani", "Rajab", "Shaban", "Ramadan", "Shawwal", "Dhul Qadah", "Dhul Hijjah")

    private fun todayKey(): String {
        val f = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.US)
        f.timeZone = riyadhTz
        return f.format(java.util.Date())
    }

    private fun parseHm(s: String): Pair<Int, Int> {
        val m = Regex("(\\d{1,2}):(\\d{2})").find(s) ?: throw IllegalArgumentException("bad time")
        return Pair(m.groupValues[1].toInt(), m.groupValues[2].toInt())
    }

    private fun fmt12(h: Int, m: Int): String {
        val hh = if (h % 12 == 0) 12 else h % 12
        return String.format(java.util.Locale.US, "%d:%02d %s", hh, m, if (h < 12) "AM" else "PM")
    }

    private fun buildPrayerData(timings: JSONObject, date: String, hijri: String): PrayerTimesData {
        fun one(name: String): PrayerTime {
            val hm = parseHm(timings.getString(name))
            return PrayerTime(name, String.format(java.util.Locale.US, "%02d:%02d", hm.first, hm.second), hm.first, hm.second)
        }
        return PrayerTimesData(one("Fajr"), one("Dhuhr"), one("Asr"), one("Maghrib"), one("Isha"), date, hijri)
    }

    private fun fetchPrayerTimes() {
        // Prayer times fetch disabled for simplified iPhone-style UI
    }

    private fun nowSeconds(): Int {
        val c = java.util.Calendar.getInstance(riyadhTz)
        return c.get(java.util.Calendar.HOUR_OF_DAY) * 3600 + c.get(java.util.Calendar.MINUTE) * 60 + c.get(java.util.Calendar.SECOND)
    }

    /** The next prayer and the one after it (wraps past Isha to tomorrow's Fajr). */
    private fun nextTwoPrayers(): List<PrayerTime>? {
        val p = prayerTimes ?: return null
        val list = listOf(p.fajr, p.dhuhr, p.asr, p.maghrib, p.isha)
        val now = nowSeconds()
        var i = list.indexOfFirst { it.hour * 3600 + it.minute * 60 > now }
        if (i < 0) i = 0
        return listOf(list[i], list[(i + 1) % list.size])
    }

    private fun secondsUntil(pt: PrayerTime): Int {
        var diff = pt.hour * 3600 + pt.minute * 60 - nowSeconds()
        if (diff <= 0) diff += 24 * 3600
        return diff
    }

    private fun updatePrayerWidget() {
        // Prayer widget update disabled for simplified iPhone-style UI
    }

    // ---------- weather (Open-Meteo, no key) - DISABLED FOR iPHONE UI ----------

    private fun kindFor(code: Int, day: Boolean): Int = when (code) {
        0, 1 -> if (day) 0 else 1
        in 51..67, in 80..82 -> 3
        in 95..99 -> 4
        else -> 2
    }

    private fun fetchWeather() {
        // Weather fetch disabled for simplified iPhone-style UI
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
        return Tile(0, a.label, "Press OK to open   ·   Hold OK to uninstall", color, art, a.pkg, a.cls)
    }

    private fun composeArt(banner: Drawable?, icon: Drawable?, color: Int, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (banner != null) {
            banner.setBounds(0, 0, w, h); banner.draw(c)
        } else {
            val wf = w.toFloat(); val hf = h.toFloat()
            // Premium dark glass tile
            val tile = Paint(Paint.ANTI_ALIAS_FLAG)
            tile.shader = LinearGradient(0f, 0f, 0f, hf,
                withAlpha(shade(color, 1.25f), 140), withAlpha(shade(color, 0.7f), 100), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, wf, hf, tile)
            // Subtle light bloom behind the icon
            val bloom = Paint(Paint.ANTI_ALIAS_FLAG)
            bloom.shader = RadialGradient(wf * 0.5f, hf * 0.5f, hf * 0.8f,
                withAlpha(shade(color, 1.6f), 90), withAlpha(color, 0), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, wf, hf, bloom)

            // the icon becomes a glass squircle
            val ps = hf * 0.62f
            val left = (wf - ps) / 2f
            val top = (hf - ps) / 2f - hf * 0.02f
            val pr = ps * 0.235f
            val plate = RectF(left, top, left + ps, top + ps)

            val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
            shadow.color = Color.argb(70, 0, 0, 0)
            shadow.setShadowLayer(hf * 0.07f, 0f, hf * 0.04f, Color.argb(120, 0, 0, 0))
            c.drawRoundRect(plate, pr, pr, shadow)

            val base = Paint(Paint.ANTI_ALIAS_FLAG)
            base.shader = LinearGradient(0f, top, 0f, top + ps,
                withAlpha(shade(color, 1.6f), 180), withAlpha(shade(color, 0.85f), 160), Shader.TileMode.CLAMP)
            c.drawRoundRect(plate, pr, pr, base)

            if (icon != null) {
                val clip = Path()
                clip.addRoundRect(plate, pr, pr, Path.Direction.CW)
                c.save()
                c.clipPath(clip)
                icon.setBounds(left.toInt(), top.toInt(), (left + ps).toInt(), (top + ps).toInt())
                icon.draw(c)
                c.restore()
            }

            // subtle sheen across the upper half
            val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
            sheen.shader = LinearGradient(0f, top, 0f, top + ps * 0.55f,
                Color.argb(80, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
            c.drawRoundRect(plate, pr, pr, sheen)

            // elegant diagonal glass rim
            val rim = Paint(Paint.ANTI_ALIAS_FLAG)
            rim.style = Paint.Style.STROKE
            rim.strokeWidth = max(1.5f, hf * 0.012f)
            rim.shader = LinearGradient(left, top, left + ps, top + ps,
                intArrayOf(Color.argb(180, 255, 255, 255), Color.argb(25, 255, 255, 255), Color.argb(90, 255, 255, 255)),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            val rr = RectF(plate)
            rr.inset(rim.strokeWidth / 2f, rim.strokeWidth / 2f)
            c.drawRoundRect(rr, pr, pr, rim)
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
        favRow.removeAllViews(); recentRow.removeAllViews(); appsRow.removeAllViews(); dockRow.removeAllViews()
        val favs = pickFavorites(tiles)
        favs.forEachIndexed { i, t -> favRow.addView(buildCard(t, i < favs.size - 1)) }
        tiles.forEachIndexed { i, t -> appsRow.addView(buildCard(t, i < tiles.size - 1)) }
        populateRecent(tiles)
        populateDock(tiles)
        if (currentFocus == null) favRow.getChildAt(0)?.requestFocus()
    }

    private fun populateRecent(tiles: List<Tile>) {
        val recentJson = prefs.getString("recent_apps", null) ?: ""
        val recentPkgSet = recentJson.split(",").filter { it.isNotBlank() }.take(6).toSet()
        val recentTiles = tiles.filter { it.pkg in recentPkgSet }.take(6)
        recentTiles.forEachIndexed { i, t -> recentRow.addView(buildCard(t, i < recentTiles.size - 1)) }
    }

    private fun populateDock(tiles: List<Tile>) {
        // Add up to 10 favorite apps to the dock (like iOS dock)
        val dockKeys = listOf("netflix", "youtube", "prime", "disney", "shahid", "spotify", "hbo", "apple", "browser", "multimedia")
        val dockTiles = ArrayList<Tile>()
        for (k in dockKeys) {
            val m = tiles.firstOrNull { !dockTiles.contains(it) && (it.title.lowercase().contains(k) || it.pkg.lowercase().contains(k)) }
            if (m != null) dockTiles.add(m)
            if (dockTiles.size == 10) break
        }
        // Fill remaining slots with any apps
        for (t in tiles) { if (!dockTiles.contains(t)) dockTiles.add(t); if (dockTiles.size == 10) break }

        dockTiles.forEachIndexed { i, t ->
            val dockItem = buildDockItem(t)
            dockRow.addView(dockItem, LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = if (i < dockTiles.size - 1) u(16f) else 0 })
        }
    }

    private fun buildDockItem(t: Tile): View {
        val size = u(56f)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            setPadding(u(8f), u(8f), u(8f), u(8f))
        }

        val iconBg = FrameLayout(this).apply {
            background = GlassDrawable(u(16f).toFloat(),
                withAlpha(t.color, 180), withAlpha(t.color, 100), 0.6f, true)
        }
        iconBg.addView(View(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = u(14f).toFloat()
                setColor(withAlpha(t.color, 120))
            }
        }, FrameLayout.LayoutParams(MATCH, MATCH))
        iconBg.addView(tv(t.title.first().toString(), 28f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
        }, FrameLayout.LayoutParams(MATCH, MATCH))
        box.addView(iconBg, LinearLayout.LayoutParams(size, size))

        val label = tv(t.title.take(10), 12f, PremiumColors.TEXT_SECONDARY, true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        box.addView(label, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = u(6f) })

        box.setOnClickListener { launchApp(t) }
        box.setOnFocusChangeListener { _, f ->
            box.animate().cancel()
            if (f) {
                box.animate().scaleX(1.15f).scaleY(1.15f).setDuration(200).start()
                label.setTextColor(PremiumColors.TEXT_PRIMARY)
            } else {
                box.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                label.setTextColor(PremiumColors.TEXT_SECONDARY)
            }
        }
        return box
    }

    private fun addToRecent(pkg: String) {
        val current = prefs.getString("recent_apps", "") ?: ""
        val parts = current.split(",").filter { it.isNotBlank() && it != pkg }.take(5)
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

    // ---------- HDMI ----------

    private fun hdmiName(n: Int): String =
        prefs.getString("name_$n", null)?.takeIf { it.isNotBlank() } ?: when (n) {
            2 -> "PLAYSTATION 5 🎮"
            3 -> "TV BOX 📺"
            else -> "HDMI $n"
        }

    private fun hwNumber(id: String): Int =
        Regex("HW(\\d+)$").find(id)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE

    /** Finds the TV's real input id for physical HDMI port n (falls back to the MediaTek default id). */
    private fun inputIdFor(n: Int): String {
        prefs.getString("map_$n", null)?.let { if (it.isNotBlank()) return it }
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
            val name = hdmiName(n)
            val color = when (n) {
                2 -> Color.argb(130, 0, 48, 135) // Sony blue for PS5
                3 -> Color.argb(130, 26, 26, 26) // Dark for TV BOX
                else -> 0xFF2C3550.toInt()
            }
            inputsRow.addView(buildCard(
                Tile(1, name, "HDMI $n   ·   OK: inputs   ·   Hold OK: rename",
                    color, null, id, "", n), true))
        }
        inputsRow.addView(buildCard(Tile(2, "Settings", "System settings", PremiumColors.BACKGROUND_CARD and 0xFFFFFF, null), true))
        inputsRow.addView(buildCard(Tile(5, "OPTIONS", "Appearance, prayer times, and launcher settings", PremiumColors.ACCENT_AMETHYST, null), true))
        val sub = if (latestBuild > myBuild) "Build $latestBuild is available   ·   Press OK to install"
                  else "Press OK to download the latest version from GitHub"
        inputsRow.addView(buildCard(Tile(3, "Update", sub, 0xFF1F6FEB.toInt(), null), true))
        inputsRow.addView(buildCard(Tile(6, "DELETE UI", "Uninstall TV Home app", 0xFFDC2626.toInt(), null), true))
        inputsRow.addView(buildCard(Tile(4, "Find inputs", "Find the app that switches your TV's HDMI inputs", 0xFF2F4A3C.toInt(), null), false))
        if (focusN in 1..HDMI_COUNT) inputsRow.getChildAt(focusN - 1)?.requestFocus()
    }

    private fun tifWorks(): Boolean = try { tim?.tvInputList?.isNotEmpty() == true } catch (e: Exception) { false }

    private fun openHdmi(t: Tile) {
        // 1) Try the built-in HDMI viewer directly (no input menu)
        if (tifWorks()) {
            try {
                startActivity(Intent(this, HdmiActivity::class.java)
                    .putExtra("input_id", t.pkg).putExtra("name", t.title).putExtra("n", t.n))
                return
            } catch (e: Exception) { /* fall through */ }
        }
        // 2) Fallback to saved input app from Input Finder
        val saved = prefs.getString("input_app", null)
        if (!saved.isNullOrBlank() && saved.contains("/")) {
            try {
                startActivity(Intent(Intent.ACTION_MAIN)
                    .setClassName(saved.substringBefore('/'), saved.substringAfter('/'))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: Exception) { /* fall through */ }
        }
        // 3) Last resort: show Finder to help user set up input app
        Toast.makeText(this, "Please use Find Inputs to set up HDMI switching.", Toast.LENGTH_LONG).show()
        startActivity(Intent(this, FinderActivity::class.java))
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

    // ---------- in-app updater (GitHub releases) ----------

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
        val t = tv(msg, 20f, Color.WHITE, true).apply {
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
        panel.addView(tv("Update Available", 24f, Color.WHITE, true))
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

    private fun checkAndUpdate() {
        showProgress("Checking for updates…")
        Thread {
            val ok = fetchLatest()
            val url = latestUrl
            runOnUiThread {
                if (!ok || url == null) {
                    hideProgress(); toast("Couldn't reach GitHub. Check the internet and that the repo is public.")
                } else if (latestBuild <= myBuild) {
                    hideProgress(); toast("You're up to date (build $myBuild).")
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
            c.connectTimeout = 15000; c.readTimeout = 30000
            c.setRequestProperty("User-Agent", "TVHome")
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
            .setTitle("TV Home update")
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
            runOnUiThread { hideProgress(); toast("Install failed — ${e.javaClass.simpleName}: ${(e.message ?: "").take(90)}") }
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
                    runOnUiThread { showProgress("Trying another download method…") }
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

    // ---------- cards ----------

    private fun roundOutline(r: Float) = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, r)
        }
    }

    private fun buildCard(t: Tile, notLast: Boolean): View {
        val r = dp(26).toFloat()
        val frame = FrameLayout(this)
        frame.outlineProvider = roundOutline(r)
        var artView: View? = null

        when (t.kind) {
            0 -> {
                val iv = RoundedImageView(this)
                iv.radius = r
                iv.setBitmap(t.art)
                frame.addView(iv, FrameLayout.LayoutParams(MATCH, MATCH))
            }
            1 -> {
                val dev = deviceOf(t.title)
                val isPs5 = dev == 1
                val isTvBox = dev == 2
                val bgColor = when {
                    isPs5 -> Color.argb(110, 0, 48, 135)
                    isTvBox -> Color.argb(110, 26, 26, 26)
                    else -> withAlpha(PremiumColors.BACKGROUND_CARD, 110)
                }
                val bgEnd = when {
                    isPs5 -> Color.argb(70, 0, 20, 80)
                    isTvBox -> Color.argb(70, 10, 10, 10)
                    else -> withAlpha(PremiumColors.BACKGROUND_CHARCOAL, 70)
                }
                frame.background = GlassDrawable(r, bgColor, bgEnd, 0f, false)
                val pw = (cardW * 0.40f).toInt()

                // Device picture: PS5 console, TV box, or the plain HDMI port
                when (dev) {
                    1 -> {
                        val v = Ps5ConsoleView(this)
                        artView = v
                        frame.addView(v, FrameLayout.LayoutParams((cardH * 0.80f).toInt(), (cardH * 0.64f).toInt(), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                            topMargin = (cardH * 0.10f).toInt()
                        })
                    }
                    2 -> {
                        val v = TvBoxView(this)
                        artView = v
                        frame.addView(v, FrameLayout.LayoutParams((cardW * 0.50f).toInt(), (cardH * 0.46f).toInt(), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                            topMargin = (cardH * 0.20f).toInt()
                        })
                    }
                    else -> {
                        val v = HdmiPortView(this)
                        artView = v
                        frame.addView(v, FrameLayout.LayoutParams(pw, (pw * 0.46f).toInt(), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                            topMargin = (cardH * 0.26f).toInt()
                        })
                    }
                }

                frame.addView(tv(t.title, 15f, PremiumColors.TEXT_PRIMARY, true).apply {
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
                        setColor(if (live) PremiumColors.ACCENT_EMERALD else 0xFF6B7280.toInt())
                    }
                }, LinearLayout.LayoutParams(u(7f), u(7f)).apply { rightMargin = u(6f) })
                val badgeText = when {
                    isPs5 -> "PS5"
                    isTvBox -> "TV"
                    else -> "PORT ${t.n}"
                }
                badge.addView(tv(badgeText, 10f, PremiumColors.TEXT_SECONDARY, true))
                frame.addView(badge, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START).apply {
                    leftMargin = u(9f); topMargin = u(9f)
                })
            }
            4 -> {
                frame.background = GlassDrawable(r, withAlpha(PremiumColors.ACCENT_EMERALD, 120), Color.argb(80, 35, 100, 75), 0f, false)
                val pw = (cardW * 0.40f).toInt()
                frame.addView(HdmiPortView(this),
                    FrameLayout.LayoutParams(pw, (pw * 0.46f).toInt(), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                        topMargin = (cardH * 0.26f).toInt()
                    })
                frame.addView(tv("Find inputs", 15f, PremiumColors.TEXT_PRIMARY, true).apply { gravity = Gravity.CENTER },
                    FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM).apply { bottomMargin = u(10f) })
            }
            3 -> {
                frame.background = GlassDrawable(r, withAlpha(PremiumColors.ACCENT_EMERALD, 180), Color.argb(150, 25, 80, 200), 0f, false)
                val gs = (cardH * 0.40f).toInt()
                frame.addView(UpdateView(this),
                    FrameLayout.LayoutParams(gs, gs, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                        topMargin = (cardH * 0.14f).toInt()
                    })
                frame.addView(tv("Update", 15f, PremiumColors.TEXT_PRIMARY, true).apply { gravity = Gravity.CENTER },
                    FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM).apply { bottomMargin = u(10f) })
                if (latestBuild > myBuild) {
                    frame.addView(tv("NEW", 10f, PremiumColors.TEXT_PRIMARY, true).apply {
                        setPadding(u(8f), u(3f), u(8f), u(3f))
                        background = GradientDrawable().apply { cornerRadius = u(12f).toFloat(); setColor(PremiumColors.ACCENT_EMERALD) }
                    }, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.END).apply {
                        topMargin = u(9f); rightMargin = u(9f)
                    })
                }
            }
            5 -> {
                // OPTIONS card
                frame.background = GlassDrawable(r, withAlpha(PremiumColors.ACCENT_AMETHYST, 170), Color.argb(130, 88, 28, 135), 0f, false)
                val gs = (cardH * 0.42f).toInt()
                frame.addView(OptionsView(this),
                    FrameLayout.LayoutParams(gs, gs, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                        topMargin = (cardH * 0.16f).toInt()
                    })
                frame.addView(tv("OPTIONS", 15f, PremiumColors.TEXT_PRIMARY, true).apply { gravity = Gravity.CENTER },
                    FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM).apply { bottomMargin = u(10f) })
            }
            6 -> {
                // DELETE UI card
                frame.background = GlassDrawable(r, withAlpha(PremiumColors.ACCENT_ROSE, 190), Color.argb(160, 185, 28, 28), 0f, false)
                val gs = (cardH * 0.42f).toInt()
                val deleteIcon = TextView(this).apply {
                    text = "×"
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, u(60f).toFloat())
                    setTextColor(PremiumColors.TEXT_PRIMARY)
                    gravity = Gravity.CENTER
                }
                frame.addView(deleteIcon, FrameLayout.LayoutParams(gs, gs, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                    topMargin = (cardH * 0.12f).toInt()
                })
                frame.addView(tv("DELETE UI", 15f, PremiumColors.TEXT_PRIMARY, true).apply { gravity = Gravity.CENTER },
                    FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM).apply { bottomMargin = u(10f) })
            }
            else -> {
                frame.background = GlassDrawable(r, withAlpha(PremiumColors.BACKGROUND_CARD, 110), withAlpha(PremiumColors.BACKGROUND_CHARCOAL, 70), 0f, false)
                val gs = (cardH * 0.42f).toInt()
                frame.addView(GearView(this),
                    FrameLayout.LayoutParams(gs, gs, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                        topMargin = (cardH * 0.16f).toInt()
                    })
                frame.addView(tv("Settings", 15f, PremiumColors.TEXT_PRIMARY, true).apply { gravity = Gravity.CENTER },
                    FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM).apply { bottomMargin = u(10f) })
            }
        }

        // liquid-glass specular rim + top sheen (brighter when focused)
        val border = View(this).apply {
            background = GlassDrawable(r, 0, 0, 0.7f, true)
            alpha = 0.5f
        }
        frame.addView(border, FrameLayout.LayoutParams(MATCH, MATCH))

        // bright focus ring: a soft glowing halo plus a crisp white line
        val ringGlow = View(this).apply {
            background = GradientDrawable().apply { cornerRadius = r; setStroke(dp(9), 0x66FFFFFF) }
            alpha = 0f
        }
        val ringLine = View(this).apply {
            background = GradientDrawable().apply { cornerRadius = r; setStroke(dp(3), Color.WHITE) }
            alpha = 0f
        }
        frame.addView(ringGlow, FrameLayout.LayoutParams(MATCH, MATCH))
        frame.addView(ringLine, FrameLayout.LayoutParams(MATCH, MATCH))

        val label = tv(t.title, 16f, PremiumColors.TEXT_PRIMARY, true).apply {
            gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            alpha = 0f; setPadding(0, u(12f), 0, 0)
        }

        val box = CardBox(this)
        box.orientation = LinearLayout.VERTICAL
        box.isFocusable = true
        box.isClickable = true
        box.clipChildren = false
        box.alpha = 0.78f
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
                4 -> startActivity(Intent(this, FinderActivity::class.java))
                5 -> startActivity(Intent(this, SettingsActivity::class.java))
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
        // Focus animation: springy pop, bright ring with a gentle breathing glow, picture bounce
        val pop = android.view.animation.OvershootInterpolator(1.5f)
        val bounce = android.view.animation.OvershootInterpolator(2.4f)
        var breathe: android.animation.ObjectAnimator? = null
        box.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { }
            override fun onViewDetachedFromWindow(v: View) { breathe?.cancel() }
        })
        box.setOnFocusChangeListener { v, f ->
            v.animate().cancel()
            breathe?.cancel()
            breathe = null
            ringGlow.animate().cancel()
            if (f) {
                v.animate().scaleX(1.10f).scaleY(1.10f).alpha(1f).setDuration(300).setInterpolator(pop).start()
                artView?.animate()?.scaleX(1.12f)?.scaleY(1.12f)?.setDuration(340)?.setInterpolator(bounce)?.start()
                ringLine.animate().alpha(1f).setDuration(150).start()
                val anim = android.animation.ObjectAnimator.ofFloat(ringGlow, View.ALPHA, 0.25f, 1f)
                anim.setDuration(850L)
                anim.repeatMode = android.animation.ValueAnimator.REVERSE
                anim.repeatCount = android.animation.ValueAnimator.INFINITE
                anim.start()
                breathe = anim
            } else {
                v.animate().scaleX(1f).scaleY(1f).alpha(0.78f).setDuration(180).setInterpolator(DecelerateInterpolator()).start()
                artView?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(180)?.setInterpolator(DecelerateInterpolator())?.start()
                ringLine.animate().alpha(0f).setDuration(120).start()
                ringGlow.animate().alpha(0f).setDuration(120).start()
            }
            border.alpha = if (f) 0.8f else 0.5f
            label.alpha = if (f && t.kind == 0) 1f else 0f
        }
        return box
    }

    // ---------- uninstall apps ----------

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
            setPadding(u(28f), u(24f), u(28f), u(24f))
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat(); setColor(0xF01C1C24.toInt()); setStroke(max(1, u(1f)), 0x33FFFFFF)
            }
        }
        panel.addView(tv("Uninstall ${t.title}?", 24f, Color.WHITE, true))
        panel.addView(tv("This removes the app from your TV.", 16f, 0x99FFFFFF.toInt()).apply {
            setPadding(0, u(8f), 0, u(20f))
        })
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        val cancel = pill("Cancel", false) { dlg.dismiss() }
        buttons.addView(cancel, LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = u(12f) })
        buttons.addView(pill("Uninstall", true) { dlg.dismiss(); uninstall(t.pkg) })
        panel.addView(buttons, LinearLayout.LayoutParams(MATCH, WRAP))
        dlg.setContentView(panel)
        dlg.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(u(520f), WindowManager.LayoutParams.WRAP_CONTENT)
            setDimAmount(0.65f)
        }
        dlg.show()
        cancel.requestFocus()   // safer default
    }

    private fun uninstall(pkg: String) {
        val uri = Uri.parse("package:$pkg")
        val attempts = listOf(Intent(Intent.ACTION_UNINSTALL_PACKAGE, uri), Intent(Intent.ACTION_DELETE, uri))
        for (i in attempts) {
            try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); return } catch (e: Exception) { }
        }
        Toast.makeText(this, "Couldn't open the uninstall screen on this TV.", Toast.LENGTH_LONG).show()
    }

    // ---------- rename dialog with presets ----------

    private fun showColorModeDialog() {
        val dlg = Dialog(this)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(u(28f), u(24f), u(28f), u(24f))
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat(); setColor(0xF01C1C24.toInt()); setStroke(max(1, u(1f)), 0x33FFFFFF)
            }
        }
        panel.addView(tv("Color Theme", 24f, Color.WHITE, true))
        panel.addView(tv("Choose your preferred color mode", 14f, 0x99FFFFFF.toInt()).apply {
            setPadding(0, u(6f), 0, u(20f))
        })

        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, u(16f))
        }
        val modeButtons = ArrayList<TextView>()
        for (mode in ColorMode.values()) {
            val btn = TextView(this).apply {
                text = mode.displayName
                setTextSize(TypedValue.COMPLEX_UNIT_PX, u(13f).toFloat())
                setTextColor(Color.WHITE)
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
                currentColorMode = mode
                staticBg.setColorMode(mode)
                prefs.edit().putString("color_mode", mode.displayName).apply()
            }
            if (mode == currentColorMode) btn.alpha = 1f else btn.alpha = 0.5f
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
        modeButtons.find { it.text == currentColorMode.displayName }?.requestFocus()
    }

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
        panel.addView(tv("Choose a preset or enter a custom name.", 13f, 0x99FFFFFF.toInt()).apply {
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

        // Presets
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
            setPadding(0, 0, 0, u(16f))
        }
        val presetButtons = ArrayList<TextView>()
        for ((name, color) in presets) {
            val btn = TextView(this).apply {
                text = name
                setTextSize(TypedValue.COMPLEX_UNIT_PX, u(14f).toFloat())
                setTextColor(Color.WHITE)
                setPadding(u(12f), u(8f), u(12f), u(8f))
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    cornerRadius = u(12f).toFloat(); setColor(color)
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
            presetRow.addView(btn, LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = u(8f) })
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
            typeface = if (bold) Typeface.create("sans-serif-medium", Typeface.NORMAL) else Typeface.create("sans-serif", Typeface.NORMAL)
        }

    private fun row(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        clipChildren = false
        clipToPadding = false
    }

    private fun header(title: String): View = tv(title, 24f, PremiumColors.TEXT_PRIMARY, true).apply {
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
