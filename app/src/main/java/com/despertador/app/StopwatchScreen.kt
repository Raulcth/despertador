package com.despertador.app

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class StopwatchState {
    var running by mutableStateOf(false)
    var accumulated by mutableLongStateOf(0L)
    var startedAt by mutableLongStateOf(0L)
    val laps = mutableStateListOf<Long>()

    fun elapsed(nowElapsed: Long): Long = accumulated + if (running) nowElapsed - startedAt else 0L

    fun start() {
        startedAt = SystemClock.elapsedRealtime()
        running = true
    }

    fun stop() {
        accumulated += SystemClock.elapsedRealtime() - startedAt
        running = false
    }

    fun reset() {
        running = false
        accumulated = 0L
        laps.clear()
    }

    fun lap(nowElapsed: Long) {
        laps.add(0, elapsed(nowElapsed))
    }
}

private fun fmtSw(ms: Long): String {
    val m = (ms / 60000L).toInt()
    val s = ((ms / 1000L) % 60L).toInt()
    val c = ((ms % 1000L) / 10L).toInt()
    return "${two(m)}:${two(s)},${two(c)}"
}

@Composable
fun StopwatchScreen(sw: StopwatchState) {
    var tickNow by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(sw.running) {
        while (sw.running) {
            tickNow = SystemClock.elapsedRealtime()
            delay(30)
        }
    }
    val elapsed = sw.elapsed(tickNow)

    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Cronômetro", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(48.dp))
        Text(fmtSw(elapsed), fontSize = 64.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (sw.running) {
                OutlinedButton(
                    onClick = { sw.lap(SystemClock.elapsedRealtime()) },
                    modifier = Modifier.height(56.dp)
                ) { Text("Volta") }
            } else if (elapsed > 0L) {
                OutlinedButton(onClick = { sw.reset() }, modifier = Modifier.height(56.dp)) { Text("Zerar") }
            }
            Button(
                onClick = { if (sw.running) sw.stop() else sw.start() },
                modifier = Modifier.height(56.dp).widthIn(min = 140.dp)
            ) { Text(if (sw.running) "Parar" else "Iniciar") }
        }
        Spacer(Modifier.height(24.dp))
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(sw.laps) { i, total ->
                val prev = sw.laps.getOrNull(i + 1) ?: 0L
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text("Volta ${sw.laps.size - i}", modifier = Modifier.weight(1f))
                    Text(fmtSw(total - prev), modifier = Modifier.weight(1f))
                    Text(fmtSw(total), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
