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

// REALM TV OS - Premium Color Palette
object RealmColors {
    const val BACKGROUND = 0xFF0A0A0F.toInt()
    const val SURFACE = 0xFF12121A.toInt()
    const val SURFACE_LIGHT = 0xFF1A1A24.toInt()
    const val PRIMARY = 0xFF6366F1.toInt()
    const val PRIMARY_DARK = 0xFF4F46E5.toInt()
    const val ACCENT_PURPLE = 0xFF8B5CF6.toInt()
    const val ACCENT_BLUE = 0xFF3B82F6.toInt()
    const val ACCENT_PINK = 0xFFEC4899.toInt()
    const val ACCENT_CYAN = 0xFF06B6D4.toInt()
    const val TEXT_PRIMARY = 0xFFFFFFFF.toInt()
    const val TEXT_SECONDARY = 0xCCFFFFFF.toInt()
    const val TEXT_TERTIARY = 0x99FFFFFF.toInt()
    const val GLASS_OVERLAY = 0x33FFFFFF.toInt()
    const val GLASS_BORDER = 0x26FFFFFF.toInt()
}

// Permissive SSL
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

// ======================= Premium Custom Views =======================

/** Cinematic animated background with premium gradients */
class RealmBackground(ctx: Context) : View(ctx) {
    private var bmp: Bitmap? = null
    private val dst = Rect()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var animationOffset = 0f

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
        renderBackground()
    }

    private fun renderBackground() {
        val bw = max(8, width / 3)
        val bh = max(8, height / 3)
        val b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(RealmColors.BACKGROUND)
        val m = max(bw, bh).toFloat()
        
        // Premium purple/blue gradient
        blob(c, bw * 0.15f, bh * 0.10f, m * 0.80f, RealmColors.PRIMARY, 140)
        blob(c, bw * 0.85f, bh * 0.30f, m * 0.65f, RealmColors.ACCENT_PURPLE, 110)
        blob(c, bw * 0.50f, bh * 0.90f, m * 0.75f, RealmColors.ACCENT_BLUE, 95)
        blob(c, bw * 0.95f, bh * 0.85f, m * 0.50f, RealmColors.ACCENT_PINK, 70)
        
        bmp = b
    }

    override fun onDraw(canvas: Canvas) {
        val b = bmp ?: return
        dst.set(0, 0, width, height)
        canvas.drawBitmap(b, null, dst, paint)
    }
}

/** Premium glass card with blur effect */
class GlassCard(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    var cornerRadius = 24f
    var backgroundColor = RealmColors.SURFACE

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        
        // Background
        paint.color = backgroundColor
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        
        // Glass overlay
        paint.color = RealmColors.GLASS_OVERLAY
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        
        // Border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = RealmColors.GLASS_BORDER
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        paint.style = Paint.Style.FILL
    }
}

/** Premium sidebar navigation */
class RealmSidebar(ctx: Context) : LinearLayout(ctx) {
    private val items = ArrayList<SidebarItem>()
    private var selectedIndex = 0
    
    init {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            colors = intArrayOf(RealmColors.SURFACE, RealmColors.BACKGROUND)
            orientation = GradientDrawable.Orientation.RIGHT_LEFT
            cornerRadii = floatArrayOf(24f, 0f, 0f, 24f, 24f, 0f, 0f, 24f)
        }
        setPadding(dp(16), dp(32), dp(16), dp(32))
    }
    
    private fun dp(px: Int): Int = (px * resources.displayMetrics.density).toInt()
    
    fun addItem(icon: String, label: String, onClick: () -> Unit) {
        val item = SidebarItem(context, icon, label, onClick)
        val params = LinearLayout.LayoutParams(MATCH, dp(64)).apply { bottomMargin = dp(8) }
        addView(item, params)
        items.add(item)
    }
    
    fun setSelected(index: Int) {
        if (index in items.indices) {
            items[selectedIndex].isSelected = false
            selectedIndex = index
            items[selectedIndex].isSelected = true
        }
    }
    
    class SidebarItem(ctx: Context, icon: String, label: String, private val onClick: () -> Unit) : FrameLayout(ctx) {
        private val iconView = TextView(ctx).apply {
            text = icon
            textSize = 24f
            setTextColor(RealmColors.TEXT_SECONDARY)
            gravity = Gravity.CENTER
        }
        private val labelView = TextView(ctx).apply {
            text = label
            textSize = 12f
            setTextColor(RealmColors.TEXT_TERTIARY)
            gravity = Gravity.CENTER
        }
        private val bg = View(ctx).apply {
            background = GradientDrawable().apply {
                setColor(RealmColors.PRIMARY)
                cornerRadius = 12f
            }
            alpha = 0f
        }
        
        var isSelected = false
            set(v) {
                field = v
                bg.alpha = if (v) 0.3f else 0f
                iconView.setTextColor(if (v) RealmColors.TEXT_PRIMARY else RealmColors.TEXT_SECONDARY)
                labelView.setTextColor(if (v) RealmColors.TEXT_PRIMARY else RealmColors.TEXT_TERTIARY)
            }
        
        init {
            addView(bg, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(iconView, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER or Gravity.TOP).apply { topMargin = dp(8) })
            addView(labelView, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER or Gravity.BOTTOM).apply { bottomMargin = dp(8) })
            
            isFocusable = true
            isClickable = true
            setOnClickListener { onClick() }
            
            setOnFocusChangeListener { _, focused ->
                bg.alpha = if (focused || isSelected) 0.3f else 0f
            }
        }
        
        private fun dp(px: Int): Int = (px * resources.displayMetrics.density).toInt()
    }
}

/** Premium app card with glassmorphism */
class RealmAppCard(ctx: Context) : FrameLayout(ctx) {
    private val glassCard = GlassCard(ctx)
    private val iconView = ImageView(ctx)
    private val labelView = TextView(ctx)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    init {
        glassCard.cornerRadius = 20f
        addView(glassCard, FrameLayout.LayoutParams(MATCH, MATCH))
        
        iconView.scaleType = ImageView.ScaleType.FIT_CENTER
        iconView.setPadding(dp(24), dp(24), dp(24), dp(24))
        addView(iconView, FrameLayout.LayoutParams(dp(80), dp(80), Gravity.CENTER).apply { topMargin = dp(16) })
        
        labelView.textSize = 14f
        labelView.setTextColor(RealmColors.TEXT_PRIMARY)
        labelView.gravity = Gravity.CENTER
        labelView.maxLines = 2
        labelView.ellipsize = TextUtils.TruncateAt.END
        addView(labelView, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER).apply { 
            leftMargin = dp(8); rightMargin = dp(8); bottomMargin = dp(12) 
        })
        
        isFocusable = true
        isClickable = true
        
        setOnFocusChangeListener { _, focused ->
            val scale = if (focused) 1.08f else 1f
            animate().scaleX(scale).scaleY(scale).setDuration(200).setInterpolator(DecelerateInterpolator()).start()
            glassCard.backgroundColor = if (focused) RealmColors.SURFACE_LIGHT else RealmColors.SURFACE
        }
    }
    
    private fun dp(px: Int): Int = (px * resources.displayMetrics.density).toInt()
    
    fun setAppInfo(icon: Drawable?, label: String) {
        iconView.setImageDrawable(icon)
        labelView.text = label
    }
}

// ======================= Main Activity =======================

class MainActivity : Activity() {
    
    private class AppEntry(val pkg: String, val cls: String, val label: String, val ri: ResolveInfo)
    
    private val handler = Handler(Looper.getMainLooper())
    private var W = 1920
    private var H = 1080
    
    private lateinit var sidebar: RealmSidebar
    private lateinit var contentArea: FrameLayout
    private lateinit var homeContent: LinearLayout
    private lateinit var appsContent: LinearLayout
    private lateinit var settingsContent: LinearLayout
    
    private lateinit var prefs: SharedPreferences
    private lateinit var staticBg: RealmBackground
    
    private var lastWeather = -1L
    private var weatherFailed = false
    
    private val ticker = object : Runnable {
        override fun run() {
            updateClock()
            val weatherInterval = if (weatherFailed) 60_000L else 15 * 60 * 1000L
            if (lastWeather < 0 || SystemClock.elapsedRealtime() - lastWeather > weatherInterval) {
                lastWeather = SystemClock.elapsedRealtime()
                fetchWeather()
            }
            handler.postDelayed(this, 5000L)
        }
    }
    
    private fun dp(px: Int): Int = (px * resources.displayMetrics.density).toInt()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val dm = resources.displayMetrics
        W = dm.widthPixels
        H = dm.heightPixels
        prefs = getSharedPreferences("realm_tv_os", MODE_PRIVATE)
        
        val root = FrameLayout(this)
        staticBg = RealmBackground(this)
        root.addView(staticBg, MATCH, MATCH)
        
        // Main layout with sidebar and content
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        
        // Sidebar
        sidebar = RealmSidebar(this)
        sidebar.layoutParams = LinearLayout.LayoutParams(dp(120), MATCH)
        mainLayout.addView(sidebar)
        
        // Content area
        contentArea = FrameLayout(this)
        mainLayout.addView(contentArea, LinearLayout.LayoutParams(0, MATCH, 1f))
        
        root.addView(mainLayout, MATCH, MATCH)
        setContentView(root)
        
        // Setup sidebar items
        setupSidebar()
        
        // Build screens
        buildHomeScreen()
        buildAppsScreen()
        buildSettingsScreen()
        
        // Show home by default
        showHome()
        
        // Start ticker
        handler.post(ticker)
    }
    
    private fun setupSidebar() {
        sidebar.addItem("🏠", "Home") { showHome() }
        sidebar.addItem("📱", "Apps") { showApps() }
        sidebar.addItem("🎮", "Games") { showGames() }
        sidebar.addItem("🎬", "Movies") { showMovies() }
        sidebar.addItem("⚙️", "Settings") { showSettings() }
        sidebar.setSelected(0)
    }
    
    private fun buildHomeScreen() {
        homeContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }
        
        // Header with time, weather, etc.
        val header = buildHeader()
        homeContent.addView(header, LinearLayout.LayoutParams(MATCH, WRAP))
        
        // Hero section
        val hero = buildHeroSection()
        homeContent.addView(hero, LinearLayout.LayoutParams(MATCH, dp(400)).apply { topMargin = dp(24) })
        
        // Recent apps
        val recentLabel = TextView(this).apply {
            text = "Recently Used"
            textSize = 20f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(RealmColors.TEXT_PRIMARY)
        }
        homeContent.addView(recentLabel, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(32) })
        
        val recentApps = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        homeContent.addView(recentApps, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(16) })
        
        contentArea.addView(homeContent, MATCH, MATCH)
    }
    
    private fun buildHeader(): View {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        
        val timeView = TextView(this).apply {
            id = View.generateViewId()
            textSize = 32f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(RealmColors.TEXT_PRIMARY)
        }
        header.addView(timeView, LinearLayout.LayoutParams(WRAP, WRAP))
        
        val dateView = TextView(this).apply {
            id = View.generateViewId()
            textSize = 16f
            setTextColor(RealmColors.TEXT_SECONDARY)
        }
        header.addView(dateView, LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(16) })
        
        val spacer = View(this)
        header.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))
        
        val weatherView = TextView(this).apply {
            id = View.generateViewId()
            textSize = 16f
            setTextColor(RealmColors.TEXT_SECONDARY)
        }
        header.addView(weatherView, LinearLayout.LayoutParams(WRAP, WRAP))
        
        return header
    }
    
    private fun buildHeroSection(): View {
        val hero = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                colors = intArrayOf(RealmColors.PRIMARY, RealmColors.PRIMARY_DARK)
                cornerRadius = dp(24).toFloat()
            }
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }
        
        val title = TextView(this).apply {
            text = "Welcome to REALM TV OS"
            textSize = 36f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(RealmColors.TEXT_PRIMARY)
        }
        hero.addView(title, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START))
        
        val subtitle = TextView(this).apply {
            text = "Your premium entertainment experience"
            textSize = 18f
            setTextColor(RealmColors.TEXT_SECONDARY)
        }
        hero.addView(subtitle, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START).apply { topMargin = dp(8) })
        
        return hero
    }
    
    private fun buildAppsScreen() {
        appsContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }
        
        val title = TextView(this).apply {
            text = "All Applications"
            textSize = 32f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(RealmColors.TEXT_PRIMARY)
        }
        appsContent.addView(title, LinearLayout.LayoutParams(MATCH, WRAP))
        
        val appsGrid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        appsContent.addView(appsGrid, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(24) })
        
        contentArea.addView(appsGrid, MATCH, MATCH)
    }
    
    private fun buildSettingsScreen() {
        settingsContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }
        
        val title = TextView(this).apply {
            text = "Settings"
            textSize = 32f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(RealmColors.TEXT_PRIMARY)
        }
        settingsContent.addView(title, LinearLayout.LayoutParams(MATCH, WRAP))
        
        contentArea.addView(settingsContent, MATCH, MATCH)
    }
    
    private fun showHome() {
        contentArea.removeAllViews()
        contentArea.addView(homeContent, MATCH, MATCH)
        sidebar.setSelected(0)
    }
    
    private fun showApps() {
        contentArea.removeAllViews()
        contentArea.addView(appsContent, MATCH, MATCH)
        sidebar.setSelected(1)
        loadApps()
    }
    
    private fun showGames() {
        Toast.makeText(this, "Games coming soon", Toast.LENGTH_SHORT).show()
    }
    
    private fun showMovies() {
        Toast.makeText(this, "Movies coming soon", Toast.LENGTH_SHORT).show()
    }
    
    private fun showSettings() {
        contentArea.removeAllViews()
        contentArea.addView(settingsContent, MATCH, MATCH)
        sidebar.setSelected(4)
    }
    
    private fun updateClock() {
        val calendar = java.util.Calendar.getInstance()
        val timeFormat = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US)
        val dateFormat = java.text.SimpleDateFormat("EEEE, MMMM d", java.util.Locale.US)
        
        // Update header views
        val timeView = homeContent.findViewById<TextView>(homeContent.getChildAt(0).findViewById<TextView>(homeContent.getChildAt(0).findViewById<TextView>(0).id).id)
        val dateView = homeContent.findViewById<TextView>(homeContent.getChildAt(0).findViewById<TextView>(homeContent.getChildAt(0).findViewById<TextView>(1).id).id)
        
        timeView?.text = timeFormat.format(calendar.time)
        dateView?.text = dateFormat.format(calendar.time)
    }
    
    private fun fetchWeather() {
        Thread {
            try {
                val url = URL("https://api.open-meteo.com/v1/forecast?latitude=24.7136&longitude=46.6753&current=temperature_2m,weather_code&timezone=auto")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("User-Agent", "RealmTVOS")
                
                if (conn is HttpsURLConnection) {
                    conn.sslSocketFactory = permissiveSslContext.socketFactory
                    conn.hostnameVerifier = permissiveHostnameVerifier
                }
                
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val current = json.getJSONObject("current")
                val temp = current.getDouble("temperature_2m").toInt()
                
                runOnUiThread {
                    val weatherView = homeContent.findViewById<TextView>(homeContent.getChildAt(0).findViewById<TextView>(homeContent.getChildAt(0).findViewById<TextView>(2).id).id)
                    weatherView?.text = "$temp°C"
                    weatherFailed = false
                }
            } catch (e: Exception) {
                weatherFailed = true
            }
        }.start()
    }
    
    private fun loadApps() {
        Thread {
            val pm = packageManager
            val leanback = pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)
            val launcher = pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            
            val seen = HashSet<String>()
            val leanPkgs = HashSet<String>()
            val apps = ArrayList<AppEntry>()
            
            for (ri in leanback) {
                val p = ri.activityInfo.packageName
                if (p == packageName || !seen.add(p + "/" + ri.activityInfo.name)) continue
                leanPkgs.add(p)
                apps.add(AppEntry(p, ri.activityInfo.name, ri.loadLabel(pm).toString(), ri))
            }
            
            for (ri in launcher) {
                val p = ri.activityInfo.packageName
                if (p == packageName || leanPkgs.contains(p)) continue
                if (!seen.add(p + "/" + ri.activityInfo.name)) continue
                apps.add(AppEntry(p, ri.activityInfo.name, ri.loadLabel(pm).toString(), ri))
            }
            
            val sortedApps = apps.sortedBy { it.label.lowercase() }
            
            runOnUiThread {
                val appsGrid = appsContent.getChildAt(1) as LinearLayout
                appsGrid.removeAllViews()
                
                var currentRow: LinearLayout? = null
                var appsInRow = 0
                val appsPerRow = 6
                
                for (app in sortedApps) {
                    if (currentRow == null || appsInRow >= appsPerRow) {
                        currentRow = LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                        }
                        appsGrid.addView(currentRow, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(16) })
                        appsInRow = 0
                    }
                    
                    val card = RealmAppCard(this@MainActivity)
                    val icon = try { app.ri.loadIcon(pm) } catch (e: Exception) { null }
                    card.setAppInfo(icon, app.label)
                    card.layoutParams = LinearLayout.LayoutParams(0, dp(160), 1f).apply { rightMargin = dp(16) }
                    card.setOnClickListener {
                        try {
                            startActivity(Intent(Intent.ACTION_MAIN).setClassName(app.pkg, app.cls)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "Can't open ${app.label}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    
                    currentRow?.addView(card)
                    appsInRow++
                }
            }
        }.start()
    }
    
    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }
    
    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }
    
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        // Launcher: stay home
    }
}
