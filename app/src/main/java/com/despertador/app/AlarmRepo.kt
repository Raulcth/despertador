package com.despertador.app

import android.content.Context
import org.json.JSONArray
import java.util.Calendar

object AlarmRepo {
    private fun prefs(c: Context) =
        c.applicationContext.getSharedPreferences("despertador", Context.MODE_PRIVATE)

    fun load(c: Context): List<Alarm> {
        val s = prefs(c).getString("alarms", "[]") ?: "[]"
        val arr = JSONArray(s)
        return (0 until arr.length())
            .map { Alarm.fromJson(arr.getJSONObject(it)) }
            .sortedWith(compareBy({ it.hour }, { it.minute }))
    }

    private fun store(c: Context, list: List<Alarm>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs(c).edit().putString("alarms", arr.toString()).apply()
    }

    fun nextId(c: Context): Int {
        val n = prefs(c).getInt("nextId", 1)
        prefs(c).edit().putInt("nextId", n + 1).apply()
        return n
    }

    fun dismissedUntil(c: Context): Long = prefs(c).getLong("dismissedUntil", 0L)

    fun isDismissedToday(c: Context): Boolean = System.currentTimeMillis() < dismissedUntil(c)

    fun save(c: Context, alarm: Alarm) {
        store(c, load(c).filter { it.id != alarm.id } + alarm)
        AlarmScheduler.schedule(c, alarm)
    }

    fun delete(c: Context, id: Int) {
        store(c, load(c).filter { it.id != id })
        AlarmScheduler.cancel(c, id)
    }

    fun setEnabled(c: Context, id: Int, on: Boolean) {
        load(c).firstOrNull { it.id == id }?.let { save(c, it.copy(enabled = on)) }
    }

    fun rescheduleAll(c: Context) {
        load(c).forEach { AlarmScheduler.schedule(c, it) }
    }

    /** Chamado quando um alarme dispara: desliga os de uma vez só e agenda o próximo dos repetidos. */
    fun afterFire(c: Context, a: Alarm) {
        if (a.days.isEmpty()) save(c, a.copy(enabled = false)) else AlarmScheduler.schedule(c, a)
    }

    /** "Dispensar todos": nenhum alarme toca até a meia-noite; o próximo dia volta ao normal. */
    fun dismissAllToday(c: Context) {
        val now = System.currentTimeMillis()
        val midnight = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val disabledIds = mutableSetOf<String>()
        val updated = load(c).map { a ->
            if (a.enabled && a.days.isEmpty() && nextTrigger(a, now) < midnight) {
                disabledIds.add(a.id.toString())
                a.copy(enabled = false)
            } else a
        }
        store(c, updated)
        prefs(c).edit().putLong("dismissedUntil", midnight).putStringSet("dismissedIds", disabledIds).apply()
        rescheduleAll(c)
    }

    fun undoDismiss(c: Context) {
        val ids = prefs(c).getStringSet("dismissedIds", emptySet()) ?: emptySet()
        val updated = load(c).map { if (it.id.toString() in ids) it.copy(enabled = true) else it }
        store(c, updated)
        prefs(c).edit().putLong("dismissedUntil", 0L).putStringSet("dismissedIds", emptySet()).apply()
        rescheduleAll(c)
    }
}

object TimerStore {
    /** mode: 0 parado, 1 rodando, 2 pausado */
    data class State(val mode: Int, val endAt: Long, val remaining: Long, val total: Long)

    private fun p(c: Context) =
        c.applicationContext.getSharedPreferences("despertador_timer", Context.MODE_PRIVATE)

    fun read(c: Context): State = p(c).let {
        State(it.getInt("mode", 0), it.getLong("endAt", 0L), it.getLong("remaining", 0L), it.getLong("total", 0L))
    }

    fun start(c: Context, total: Long) {
        val end = System.currentTimeMillis() + total
        p(c).edit().putInt("mode", 1).putLong("endAt", end).putLong("remaining", total).putLong("total", total).apply()
        AlarmScheduler.scheduleTimer(c, end)
    }

    fun pause(c: Context) {
        val s = read(c)
        if (s.mode != 1) return
        val rem = (s.endAt - System.currentTimeMillis()).coerceAtLeast(0L)
        p(c).edit().putInt("mode", 2).putLong("remaining", rem).apply()
        AlarmScheduler.cancelTimer(c)
    }

    fun resume(c: Context) {
        val s = read(c)
        if (s.mode != 2) return
        val end = System.currentTimeMillis() + s.remaining
        p(c).edit().putInt("mode", 1).putLong("endAt", end).apply()
        AlarmScheduler.scheduleTimer(c, end)
    }

    fun clear(c: Context) {
        p(c).edit().putInt("mode", 0).putLong("endAt", 0L).putLong("remaining", 0L).putLong("total", 0L).apply()
        AlarmScheduler.cancelTimer(c)
    }
}

object WorldClocks {
    val CITIES = listOf(
        "São Paulo" to "America/Sao_Paulo",
        "Lisboa" to "Europe/Lisbon",
        "Londres" to "Europe/London",
        "Paris" to "Europe/Paris",
        "Madri" to "Europe/Madrid",
        "Moscou" to "Europe/Moscow",
        "Nova York" to "America/New_York",
        "Los Angeles" to "America/Los_Angeles",
        "Cidade do México" to "America/Mexico_City",
        "Buenos Aires" to "America/Argentina/Buenos_Aires",
        "Luanda" to "Africa/Luanda",
        "Cairo" to "Africa/Cairo",
        "Dubai" to "Asia/Dubai",
        "Nova Délhi" to "Asia/Kolkata",
        "Pequim" to "Asia/Shanghai",
        "Tóquio" to "Asia/Tokyo",
        "Sydney" to "Australia/Sydney",
    )

    fun load(c: Context): List<String> =
        (c.getSharedPreferences("despertador", Context.MODE_PRIVATE).getString("zones", "") ?: "")
            .split(",").filter { it.isNotBlank() }

    fun save(c: Context, list: List<String>) {
        c.getSharedPreferences("despertador", Context.MODE_PRIVATE)
            .edit().putString("zones", list.joinToString(",")).apply()
    }

    fun name(id: String): String = CITIES.firstOrNull { it.second == id }?.first ?: id
}
