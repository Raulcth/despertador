package com.despertador.app

import android.content.Context
import org.json.JSONArray

object AlarmRepo {
    private fun prefs(c: Context) =
        c.applicationContext.getSharedPreferences("despertador", Context.MODE_PRIVATE)

    // Cache em memória: evita reler e interpretar o JSON a cada chamada (antes era a cada toque).
    private val lock = Any()
    @Volatile
    private var cache: List<Alarm>? = null

    fun load(c: Context): List<Alarm> {
        cache?.let { return it }
        return synchronized(lock) {
            cache ?: run {
                val s = prefs(c).getString("alarms", "[]") ?: "[]"
                val arr = JSONArray(s)
                val list = (0 until arr.length())
                    .map { Alarm.fromJson(arr.getJSONObject(it)) }
                    .sortedWith(compareBy({ it.hour }, { it.minute }))
                cache = list
                list
            }
        }
    }

    private fun toJsonString(list: List<Alarm>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        return arr.toString()
    }

    private fun store(c: Context, list: List<Alarm>) {
        val sorted = list.sortedWith(compareBy({ it.hour }, { it.minute }))
        synchronized(lock) {
            cache = sorted
            prefs(c).edit().putString("alarms", toJsonString(sorted)).apply()
        }
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

    /** Tira a marca de "dispensado" de um alarme repetido (volta a tocar na próxima ocorrência). */
    fun clearSkip(c: Context, id: Int) {
        load(c).firstOrNull { it.id == id }?.let { save(c, it.copy(skipUntil = 0L)) }
    }

    /** Cria vários alarmes em sequência: base, base+intervalo, base+2*intervalo... */
    fun createSeries(c: Context, base: Alarm, intervalMin: Int, count: Int): List<Alarm> {
        val out = mutableListOf<Alarm>()
        for (i in 0 until count.coerceAtLeast(1)) {
            val total = (base.hour * 60 + base.minute + i * intervalMin) % 1440
            val a = base.copy(id = nextId(c), hour = total / 60, minute = total % 60)
            save(c, a)
            out.add(a)
        }
        return out
    }

    /** "Dispensar" do aviso: pula só esta ocorrência (alarme de uma vez só é desligado). */
    fun skipOccurrence(c: Context, id: Int, alarmTime: Long) {
        val a = load(c).firstOrNull { it.id == id } ?: return
        if (a.days.isEmpty()) save(c, a.copy(enabled = false))
        else save(c, a.copy(skipUntil = alarmTime))
    }

    fun rescheduleAll(c: Context) {
        load(c).forEach { AlarmScheduler.schedule(c, it) }
    }

    /** Chamado quando um alarme dispara: desliga os de uma vez só e agenda o próximo dos repetidos. */
    fun afterFire(c: Context, a: Alarm) {
        if (a.days.isEmpty()) save(c, a.copy(enabled = false)) else AlarmScheduler.schedule(c, a)
    }

    /**
     * "Dispensar todos": dispensa DE VERDADE todos os alarmes ativos do dia em questão.
     * - O dia é o do próximo alarme a tocar (hoje, ou amanhã se já não resta nada hoje).
     * - Alarme de uma vez só: é desligado.
     * - Alarme repetido: continua ligado, mas só volta a tocar no dia seguinte (skipUntil).
     * Tudo é gravado de uma vez e cada alarme afetado é cancelado e reagendado do zero
     * (inclusive adiar/aviso pendentes). Devolve quantos alarmes foram dispensados.
     */
    fun dismissAllToday(c: Context): Int {
        val now = System.currentTimeMillis()
        val all = load(c)
        val active = all.filter { it.enabled }
        if (active.isEmpty()) return 0
        val first = active.minOf { nextTrigger(it, now) }
        val horizon = startOfNextDay(first)
        val ids = HashSet<String>()
        val updated = all.map { a ->
            if (!a.enabled || nextTrigger(a, now) >= horizon) {
                a
            } else {
                ids.add(a.id.toString())
                if (a.days.isEmpty()) a.copy(enabled = false) else a.copy(skipUntil = horizon - 1)
            }
        }
        store(c, updated)
        prefs(c).edit()
            .putLong("dismissedUntil", horizon)
            .putStringSet("dismissedIds", HashSet(ids))
            .apply()
        updated.filter { it.id.toString() in ids }.forEach {
            AlarmScheduler.cancel(c, it.id)
            AlarmScheduler.schedule(c, it)
        }
        return ids.size
    }

    fun undoDismiss(c: Context) {
        val ids: Set<String> = prefs(c).getStringSet("dismissedIds", emptySet<String>())?.toSet() ?: emptySet()
        val restored = mutableListOf<Alarm>()
        val updated = load(c).map { a ->
            if (a.id.toString() !in ids) {
                a
            } else {
                val r = if (a.days.isEmpty()) a.copy(enabled = true) else a.copy(skipUntil = 0L)
                restored.add(r)
                r
            }
        }
        store(c, updated)
        prefs(c).edit()
            .putLong("dismissedUntil", 0L)
            .putStringSet("dismissedIds", emptySet<String>())
            .apply()
        restored.forEach { AlarmScheduler.schedule(c, it) }
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
