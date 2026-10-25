package com.despertador.app

import android.content.Context
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator

object Haptics {
    private var last = 0L

    @Suppress("DEPRECATION")
    private fun vib(c: Context): Vibrator? =
        c.applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    private fun pulse(c: Context, ms: Long, amp: Int) {
        val v = vib(c) ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createOneShot(ms, amp))
    }

    /** Toque bem leve (arrastar dias, girar o seletor). */
    fun tick(c: Context) {
        val now = SystemClock.uptimeMillis()
        if (now - last < 30) return
        last = now
        pulse(c, 18, 110)
    }

    /** Toque um pouco mais firme (segurar um alarme). */
    fun click(c: Context) = pulse(c, 28, 180)
}
