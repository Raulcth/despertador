package com.despertador.app

import android.app.Application
import android.content.Context
import android.util.Log

object CrashLog {
    private fun p(c: Context) =
        c.getSharedPreferences("despertador_crash", Context.MODE_PRIVATE)

    fun save(c: Context, e: Throwable) {
        val full = Log.getStackTraceString(e)
        val text = if (full.length > 3800) full.take(1200) + "\n...\n" + full.takeLast(2500) else full
        p(c).edit().putString("last", text).commit()
    }

    fun read(c: Context): String? = p(c).getString("last", null)

    fun clear(c: Context) {
        p(c).edit().remove("last").apply()
    }
}

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                CrashLog.save(this, e)
            } catch (x: Throwable) {
            }
            prev?.uncaughtException(t, e)
        }
    }
}
