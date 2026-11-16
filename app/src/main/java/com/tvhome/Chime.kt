package com.tvhome

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** A soft, low, two-note bell. Generated in code, so no audio files are needed. */
object SoftChime {
    private const val RATE = 22050
    @Volatile private var playing = false

    private fun addNote(buf: DoubleArray, freq: Double, startSec: Double, amp: Double) {
        val start = (startSec * RATE).toInt()
        for (i in start until buf.size) {
            val t = (i - start).toDouble() / RATE
            val env = (1.0 - exp(-t * 60.0)) * exp(-t * 2.0)
            val w = sin(2.0 * PI * freq * t) + 0.18 * sin(2.0 * PI * freq * 2.0 * t)
            buf[i] += amp * env * w
        }
    }

    private fun build(): ShortArray {
        val n = (RATE * 3.2).toInt()
        val buf = DoubleArray(n)
        addNote(buf, 523.25, 0.0, 0.22)   // C5
        addNote(buf, 659.25, 0.55, 0.22)  // E5
        val fade = (RATE * 0.4).toInt()
        val out = ShortArray(n)
        for (i in 0 until n) {
            var v = buf[i]
            if (i > n - fade) v *= (n - i).toDouble() / fade
            out[i] = (v * 32767.0).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    fun play(onDone: (() -> Unit)? = null) {
        if (playing) { onDone?.invoke(); return }
        playing = true
        Thread {
            var track: AudioTrack? = null
            try {
                val data = build()
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val fmt = AudioFormat.Builder()
                    .setSampleRate(RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
                val t = AudioTrack(attrs, fmt, data.size * 2, AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE)
                track = t
                t.write(data, 0, data.size)
                t.setVolume(0.6f)
                t.play()
                Thread.sleep(3400)
            } catch (e: Exception) {
            } finally {
                try { track?.release() } catch (e: Exception) { }
                playing = false
                onDone?.invoke()
            }
        }.start()
    }
}

/** Schedules the next prayer chime with the system alarm clock, so it works even when the launcher is not on screen. */
object PrayerAlarm {
    private const val REQ = 4711

    private fun pending(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(ctx, REQ, Intent(ctx, ChimeReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun schedule(ctx: Context) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val pi = pending(ctx)
            val prefs = ctx.getSharedPreferences("tvhome", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("chime_on", true)) { am.cancel(pi); return }
            val cached = prefs.getString("cached_prayer_times", null) ?: return
            val timings = JSONObject(cached).getJSONObject("timings")
            val tz = java.util.TimeZone.getTimeZone("Asia/Riyadh")
            val now = System.currentTimeMillis()
            var best = Long.MAX_VALUE
            for (name in listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")) {
                val m = Regex("(\\d{1,2}):(\\d{2})").find(timings.getString(name)) ?: continue
                val cal = java.util.Calendar.getInstance(tz)
                cal.timeInMillis = now
                cal.set(java.util.Calendar.HOUR_OF_DAY, m.groupValues[1].toInt())
                cal.set(java.util.Calendar.MINUTE, m.groupValues[2].toInt())
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                if (cal.timeInMillis <= now) cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                if (cal.timeInMillis < best) best = cal.timeInMillis
            }
            if (best == Long.MAX_VALUE) return
            val show = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            am.setAlarmClock(AlarmManager.AlarmClockInfo(best, show), pi)
        } catch (e: Exception) { }
    }
}

class ChimeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val on = app.getSharedPreferences("tvhome", Context.MODE_PRIVATE).getBoolean("chime_on", true)
        val pending = goAsync()
        PrayerAlarm.schedule(app)   // queue the next one first so the chain never breaks
        if (on) {
            SoftChime.play { try { pending.finish() } catch (e: Exception) { } }
        } else {
            pending.finish()
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PrayerAlarm.schedule(context.applicationContext)
    }
}
