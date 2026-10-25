package com.despertador.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

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
