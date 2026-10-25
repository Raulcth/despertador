package com.despertador.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

const val EXTRA_ID = "alarm_id"
const val EXTRA_IS_SNOOZE = "is_snooze"
const val EXTRA_SNOOZE_COUNT = "snooze_count"
const val TIMER_ID = -1

fun two(n: Int): String = n.toString().padStart(2, '0')

/** days usa Calendar.SUNDAY(1)..SATURDAY(7). Vazio = toca uma vez. */
data class Alarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val days: Set<Int> = emptySet(),
    val label: String = "",
    val enabled: Boolean = true,
    val soundOn: Boolean = true,
    val soundUri: String? = null,
    val vibrate: Boolean = true,
    val snoozeOn: Boolean = true,
    val snoozeMinutes: Int = 5,
    val snoozeTimes: Int = 3,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("hour", hour)
        put("minute", minute)
        put("days", JSONArray(days.toList()))
        put("label", label)
        put("enabled", enabled)
        put("soundOn", soundOn)
        put("soundUri", soundUri ?: JSONObject.NULL)
        put("vibrate", vibrate)
        put("snoozeOn", snoozeOn)
        put("snoozeMinutes", snoozeMinutes)
        put("snoozeTimes", snoozeTimes)
    }

    companion object {
        fun fromJson(o: JSONObject): Alarm {
            val d = o.optJSONArray("days") ?: JSONArray()
            val days = (0 until d.length()).map { d.getInt(it) }.toSet()
            return Alarm(
                id = o.getInt("id"),
                hour = o.getInt("hour"),
                minute = o.getInt("minute"),
                days = days,
                label = o.optString("label", ""),
                enabled = o.optBoolean("enabled", true),
                soundOn = o.optBoolean("soundOn", true),
                soundUri = if (o.isNull("soundUri")) null else o.getString("soundUri"),
                vibrate = o.optBoolean("vibrate", true),
                snoozeOn = o.optBoolean("snoozeOn", true),
                snoozeMinutes = o.optInt("snoozeMinutes", 5),
                snoozeTimes = o.optInt("snoozeTimes", 3),
            )
        }
    }
}

/** Próximo disparo depois de [now] e não antes de [notBefore] (usado pelo "Dispensar todos"). */
fun nextTrigger(a: Alarm, now: Long, notBefore: Long = 0L): Long {
    for (offset in 0..14) {
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, offset)
            set(Calendar.HOUR_OF_DAY, a.hour)
            set(Calendar.MINUTE, a.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val t = c.timeInMillis
        if (t <= now || t < notBefore) continue
        if (a.days.isEmpty() || c.get(Calendar.DAY_OF_WEEK) in a.days) return t
    }
    return now + 24L * 3600L * 1000L
}

fun untilText(ms: Long): String {
    val total = ((ms + 59_999L) / 60_000L).toInt()
    val d = total / 1440
    val h = (total % 1440) / 60
    val m = total % 60
    val parts = mutableListOf<String>()
    if (d > 0) parts.add("${d}d")
    if (h > 0) parts.add("${h}h")
    if (m > 0) parts.add("${m}min")
    return if (parts.isEmpty()) "menos de 1 min" else parts.joinToString(" ")
}
