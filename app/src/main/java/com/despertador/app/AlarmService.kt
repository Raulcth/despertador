package com.despertador.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import java.util.Calendar

class AlarmService : Service() {

    companion object {
        const val ACTION_START = "com.despertador.app.START"
        const val ACTION_STOP = "com.despertador.app.STOP"
        const val ACTION_SNOOZE = "com.despertador.app.SNOOZE"
        const val ACTION_RING_ENDED = "com.despertador.app.RING_ENDED"
        const val CHANNEL = "alarm_ring"
        const val NOTIF_ID = 42

        /** Segundos para o volume do alarme subir até o máximo. */
        const val RAMP_SECONDS = 30

        /** Para sozinho depois de 10 minutos. */
        const val MAX_RING_MS = 10 * 60 * 1000L
    }

    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var originalVolume = -1
    private var current: Alarm? = null
    private var snoozeCount = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopRinging(false)
            ACTION_SNOOZE -> stopRinging(true)
            else -> startRinging(intent)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseSound()
        super.onDestroy()
    }

    private fun startRinging(intent: Intent?) {
        val id = intent?.getIntExtra(EXTRA_ID, TIMER_ID) ?: TIMER_ID
        val isSnooze = intent?.getBooleanExtra(EXTRA_IS_SNOOZE, false) ?: false
        snoozeCount = intent?.getIntExtra(EXTRA_SNOOZE_COUNT, 0) ?: 0

        val alarm: Alarm? = if (id == TIMER_ID) {
            Alarm(TIMER_ID, 0, 0, label = "Timer", snoozeOn = false)
        } else {
            AlarmRepo.load(this).firstOrNull { it.id == id }
        }
        val canSnooze = alarm != null && alarm.snoozeOn && snoozeCount < alarm.snoozeTimes

        val notif = buildNotification(alarm, canSnooze)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, notif)
        }
        if (alarm == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        releaseSound()
        current = alarm

        if (id == TIMER_ID) {
            TimerStore.clear(this)
        } else if (!isSnooze) {
            AlarmRepo.afterFire(this, alarm)
        }

        startSound(alarm)
        handler.postDelayed({ stopRinging(false) }, MAX_RING_MS)
    }

    private fun stopRinging(snooze: Boolean) {
        val a = current
        releaseSound()
        restoreVolume()
        if (snooze && a != null && a.id != TIMER_ID && a.snoozeOn && snoozeCount < a.snoozeTimes) {
            AlarmScheduler.scheduleSnooze(this, a.id, a.snoozeMinutes, snoozeCount + 1)
        }
        current = null
        sendBroadcast(Intent(ACTION_RING_ENDED).setPackage(packageName))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---------- som, volume e vibração ----------

    private fun play(uri: Uri): Boolean = try {
        val mp = MediaPlayer()
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        mp.setDataSource(this, uri)
        mp.isLooping = true
        mp.prepare()
        mp.start()
        player = mp
        true
    } catch (e: Exception) {
        false
    }

    private fun startSound(a: Alarm) {
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (a.soundOn) {
            val chosen = a.soundUri?.let { Uri.parse(it) }
            val ok = (chosen != null && play(chosen)) ||
                play(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)) ||
                play(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
            if (ok) rampVolume(audio)
        }
        if (a.vibrate) {
            @Suppress("DEPRECATION")
            val vib = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            @Suppress("DEPRECATION")
            vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 600), 0), attrs)
        }
    }

    private fun setAlarmVolume(audio: AudioManager, level: Int) {
        try {
            audio.setStreamVolume(AudioManager.STREAM_ALARM, level, 0)
        } catch (e: SecurityException) {
            // alguns modos (Não perturbe) podem bloquear; segue sem mexer
        }
    }

    /** Sobe o volume do alarme aos poucos, do nível atual até o máximo. */
    private fun rampVolume(audio: AudioManager) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        if (originalVolume < 0) originalVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        val start = originalVolume.coerceAtLeast(1)
        setAlarmVolume(audio, start)
        val steps = max - start
        if (steps <= 0) return
        val stepMs = RAMP_SECONDS * 1000L / steps
        var level = start
        val step = object : Runnable {
            override fun run() {
                level++
                setAlarmVolume(audio, level)
                if (level < max) handler.postDelayed(this, stepMs)
            }
        }
        handler.postDelayed(step, stepMs)
    }

    private fun restoreVolume() {
        if (originalVolume >= 0) {
            val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            setAlarmVolume(audio, originalVolume)
            originalVolume = -1
        }
    }

    private fun releaseSound() {
        handler.removeCallbacksAndMessages(null)
        player?.let {
            try { it.stop() } catch (e: Exception) { }
            it.release()
        }
        player = null
        @Suppress("DEPRECATION")
        (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).cancel()
    }

    // ---------- notificação ----------

    private fun buildNotification(alarm: Alarm?, canSnooze: Boolean): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) == null) {
            val ch = NotificationChannel(CHANNEL, "Alarme tocando", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(ch)
        }

        val title = when {
            alarm == null -> "Alarme"
            alarm.label.isNotBlank() -> alarm.label
            alarm.id == TIMER_ID -> "Timer"
            else -> "Alarme"
        }
        val cal = Calendar.getInstance()
        val timeText = "${two(cal.get(Calendar.HOUR_OF_DAY))}:${two(cal.get(Calendar.MINUTE))}"

        val ring = Intent(this, AlarmRingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("label", title)
            putExtra("can_snooze", canSnooze)
        }
        val fullPi = PendingIntent.getActivity(
            this, 1, ring, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getService(
            this, 2, Intent(this, AlarmService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozePi = PendingIntent.getService(
            this, 3, Intent(this, AlarmService::class.java).setAction(ACTION_SNOOZE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(timeText)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(fullPi)
            .setFullScreenIntent(fullPi, true)
            .addAction(0, "Parar", stopPi)
        if (canSnooze) b.addAction(0, "Adiar", snoozePi)
        return b.build()
    }
}
