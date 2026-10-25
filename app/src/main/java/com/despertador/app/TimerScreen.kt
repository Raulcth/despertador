package com.despertador.app

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private fun fmtTimer(ms: Long): String {
    val sec = (ms + 999L) / 1000L
    val h = (sec / 3600).toInt()
    val m = ((sec % 3600) / 60).toInt()
    val s = (sec % 60).toInt()
    return if (h > 0) "$h:${two(m)}:${two(s)}" else "${two(m)}:${two(s)}"
}

@Composable
fun TimerScreen() {
    val ctx = LocalContext.current
    var st by remember { mutableStateOf(TimerStore.read(ctx)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var h by remember { mutableIntStateOf(0) }
    var m by remember { mutableIntStateOf(5) }
    var s by remember { mutableIntStateOf(0) }

    // Rodando: acompanha cada quadro da tela (anel suave). Parado: quase não gasta nada.
    LaunchedEffect(Unit) {
        var lastPoll = 0L
        while (true) {
            if (st.mode == 1) withFrameNanos { } else delay(250)
            val t = System.currentTimeMillis()
            now = t
            if (t - lastPoll >= 250L) {
                lastPoll = t
                val cur = TimerStore.read(ctx)
                if (cur.mode == 1 && cur.endAt - t < -5000L) {
                    TimerStore.clear(ctx)
                } else if (cur != st) {
                    st = cur
                }
            }
        }
    }

    // Só avisa a tela quando o resultado muda (o texto muda 1x por segundo, não a cada quadro).
    val remainingMs by remember {
        derivedStateOf { if (st.mode == 1) (st.endAt - now).coerceAtLeast(0L) else st.remaining }
    }
    val remainingText by remember { derivedStateOf { fmtTimer(remainingMs) } }
    val canStart by remember { derivedStateOf { h + m + s > 0 } }

    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Timers", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.weight(1f))

        Crossfade(targetState = st.mode == 0, animationSpec = tween(260), label = "timer") { idle ->
            if (idle) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Wheel(24, h) { h = it }
                        Text("h", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Wheel(60, m) { m = it }
                        Text("min", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Wheel(60, s) { s = it }
                        Text("s", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(32.dp))
                    Button(
                        onClick = {
                            val total = ((h * 60L + m) * 60L + s) * 1000L
                            if (total > 0) {
                                Haptics.click(ctx)
                                TimerStore.start(ctx, total)
                                st = TimerStore.read(ctx)
                            }
                        },
                        enabled = canStart,
                        modifier = Modifier.height(56.dp).widthIn(min = 180.dp)
                    ) { Text("Iniciar", fontSize = 18.sp) }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { if (st.total > 0) remainingMs.toFloat() / st.total.toFloat() else 0f },
                            modifier = Modifier.size(260.dp),
                            strokeWidth = 6.dp,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Text(remainingText, fontSize = 52.sp, fontWeight = FontWeight.Light)
                    }
                    Spacer(Modifier.height(32.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedButton(
                            onClick = {
                                TimerStore.clear(ctx)
                                st = TimerStore.read(ctx)
                            },
                            modifier = Modifier.height(56.dp)
                        ) { Text("Cancelar") }
                        Button(
                            onClick = {
                                if (st.mode == 1) TimerStore.pause(ctx) else TimerStore.resume(ctx)
                                st = TimerStore.read(ctx)
                            },
                            modifier = Modifier.height(56.dp)
                        ) { Text(if (st.mode == 1) "Pausar" else "Continuar") }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
    }
}
