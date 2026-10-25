package com.despertador.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val svc = Intent(context, AlarmService::class.java).apply {
            action = AlarmService.ACTION_START
            putExtras(intent)
        }
        ContextCompat.startForegroundService(context, svc)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmRepo.rescheduleAll(context)
        val t = TimerStore.read(context)
        if (t.mode == 1) {
            if (t.endAt > System.currentTimeMillis()) AlarmScheduler.scheduleTimer(context, t.endAt)
            else TimerStore.clear(context)
        }
    }
}

/** Recebe o horário do aviso (mostra a notificação) e o toque em "Dispensar". */
class PreAlertReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_SHOW = "com.despertador.app.PRE_SHOW"
        const val ACTION_DISMISS = "com.despertador.app.PRE_DISMISS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, 0)
        val alarmTime = intent.getLongExtra(EXTRA_ALARM_TIME, 0L)
        when (intent.action) {
            ACTION_SHOW -> PreAlert.show(context, id, alarmTime)
            ACTION_DISMISS -> {
                AlarmRepo.skipOccurrence(context, id, alarmTime)
                PreAlert.cancel(context, id)
            }
        }
    }
}

object PreAlert {
    private const val CHANNEL = "pre_alert"

    private fun notifId(id: Int) = 100_000 + id

    private fun nm(c: Context) = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun cancel(c: Context, id: Int) {
        nm(c).cancel(notifId(id))
    }

    fun show(c: Context, id: Int, alarmTime: Long) {
        val a = AlarmRepo.load(c).firstOrNull { it.id == id } ?: return
        if (!a.enabled) return
        val remaining = alarmTime - System.currentTimeMillis()
        if (remaining <= 0L) return

        val manager = nm(c)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            val ch = NotificationChannel(CHANNEL, "Aviso antes do alarme", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            manager.createNotificationChannel(ch)
        }

        val cal = Calendar.getInstance().apply { timeInMillis = alarmTime }
        val hm = "${two(cal.get(Calendar.HOUR_OF_DAY))}:${two(cal.get(Calendar.MINUTE))}"

        val dismissIntent = Intent(c, PreAlertReceiver::class.java).apply {
            action = PreAlertReceiver.ACTION_DISMISS
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_ALARM_TIME, alarmTime)
        }
        val dismissPi = PendingIntent.getBroadcast(
            c, 4_000_000 + id, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openPi = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )

        val text = if (a.label.isBlank()) "Ainda quer que ele toque?" else "${a.label} · Ainda quer que ele toque?"
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Alarme às $hm")
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setWhen(alarmTime)
            .setShowWhen(true)
            .setTimeoutAfter(remaining)
            .setContentIntent(openPi)
            .addAction(0, "Dispensar", dismissPi)
            .build()
        manager.notify(notifId(id), n)
    }
}
