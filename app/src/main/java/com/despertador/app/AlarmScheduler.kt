package com.despertador.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object AlarmScheduler {
    private const val SNOOZE_OFFSET = 1_000_000
    private const val TIMER_CODE = 2_000_000
    private const val PRE_CODE = 3_000_000

    private fun am(c: Context) = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun firePi(c: Context, code: Int, id: Int, snooze: Boolean, count: Int): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java).apply {
            action = "com.despertador.app.FIRE"
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_IS_SNOOZE, snooze)
            putExtra(EXTRA_SNOOZE_COUNT, count)
        }
        return PendingIntent.getBroadcast(
            c, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun prePi(c: Context, id: Int, alarmTime: Long): PendingIntent {
        val i = Intent(c, PreAlertReceiver::class.java).apply {
            action = PreAlertReceiver.ACTION_SHOW
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_ALARM_TIME, alarmTime)
        }
        return PendingIntent.getBroadcast(
            c, PRE_CODE + id, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun showPi(c: Context): PendingIntent =
        PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    fun schedule(c: Context, a: Alarm) {
        if (!a.enabled) {
            cancel(c, a.id)
            return
        }
        val now = System.currentTimeMillis()
        val t = nextTrigger(a, now)
        am(c).setAlarmClock(AlarmManager.AlarmClockInfo(t, showPi(c)), firePi(c, a.id, a.id, false, 0))

        // aviso silencioso antes do alarme (só se ainda der tempo)
        val pre = t - PRE_ALERT_MIN * 60_000L
        if (pre > now) {
            am(c).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, pre, prePi(c, a.id, t))
        } else {
            am(c).cancel(prePi(c, a.id, t))
        }
        PreAlert.cancel(c, a.id)
    }

    fun cancel(c: Context, id: Int) {
        am(c).cancel(firePi(c, id, id, false, 0))
        am(c).cancel(firePi(c, id + SNOOZE_OFFSET, id, true, 0))
        am(c).cancel(prePi(c, id, 0L))
        PreAlert.cancel(c, id)
    }

    fun scheduleSnooze(c: Context, id: Int, minutes: Int, count: Int) {
        val t = System.currentTimeMillis() + minutes * 60_000L
        am(c).setAlarmClock(
            AlarmManager.AlarmClockInfo(t, showPi(c)),
            firePi(c, id + SNOOZE_OFFSET, id, true, count)
        )
    }

    fun scheduleTimer(c: Context, endAt: Long) {
        am(c).setAlarmClock(
            AlarmManager.AlarmClockInfo(endAt, showPi(c)),
            firePi(c, TIMER_CODE, TIMER_ID, false, 0)
        )
    }

    fun cancelTimer(c: Context) {
        am(c).cancel(firePi(c, TIMER_CODE, TIMER_ID, false, 0))
    }
}
