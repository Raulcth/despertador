package com.despertador.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private fun relText(tzId: String, now: Long): String {
    val tz = TimeZone.getTimeZone(tzId)
    val local = TimeZone.getDefault()
    val cz = Calendar.getInstance(tz).apply { timeInMillis = now }
    val cl = Calendar.getInstance().apply { timeInMillis = now }
    val kz = cz.get(Calendar.YEAR) * 1000 + cz.get(Calendar.DAY_OF_YEAR)
    val kl = cl.get(Calendar.YEAR) * 1000 + cl.get(Calendar.DAY_OF_YEAR)
    val dia = when {
        kz < kl -> "Ontem"
        kz > kl -> "Amanhã"
        else -> "Hoje"
    }
    val mins = (tz.getOffset(now) - local.getOffset(now)) / 60000
    val diff = if (mins == 0) {
        "mesmo horário"
    } else {
        val sign = if (mins > 0) "+" else "-"
        val a = Math.abs(mins)
        sign + (a / 60) + "h" + (if (a % 60 > 0) "${a % 60}min" else "")
    }
    return "$dia, $diff"
}

@Composable
fun ClockScreen(active: Boolean = true) {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // Aba escondida não atualiza o relógio (economiza processamento); ao voltar, atualiza na hora.
    LaunchedEffect(active) {
        while (active) {
            now = System.currentTimeMillis()
            delay(1000 - now % 1000)
        }
    }
    var zones by remember { mutableStateOf(WorldClocks.load(ctx)) }
    var adding by remember { mutableStateOf(false) }

    // Criar SimpleDateFormat é caro: feito uma vez só, não a cada segundo.
    val hm = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val sec = remember { SimpleDateFormat("ss", Locale.getDefault()) }
    val date = remember { SimpleDateFormat("EEEE, d 'de' MMMM", Locale.getDefault()) }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Relógio", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(hm.format(now), fontSize = 88.sp, fontWeight = FontWeight.Light)
            Text(
                sec.format(now),
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, bottom = 14.dp)
            )
        }
        Text(
            date.format(now),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(32.dp))

        LazyColumn(Modifier.weight(1f)) {
            items(zones, key = { it }) { id ->
                val f = remember(id) {
                    SimpleDateFormat("HH:mm", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone(id) }
                }
                val rel = remember(id, now / 60_000L) { relText(id, now) }
                Surface(
                    modifier = Modifier.animateItem().fillMaxWidth().padding(vertical = 6.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 3.dp
                ) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(WorldClocks.name(id), style = MaterialTheme.typography.titleMedium)
                        Text(
                            rel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(f.format(now), fontSize = 32.sp, fontWeight = FontWeight.Light)
                    IconButton(onClick = {
                        zones = zones - id
                        WorldClocks.save(ctx, zones)
                    }) { Icon(Icons.Filled.Close, "Remover") }
                }
                }
            }
        }

        Surface(
            onClick = { adding = true },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 4.dp,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 16.dp)
        ) {
            Text(
                "Adicionar cidade",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }

    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Adicionar cidade") },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(WorldClocks.CITIES.filter { it.second !in zones }) { (name, id) ->
                        Text(
                            name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    zones = zones + id
                                    WorldClocks.save(ctx, zones)
                                    adding = false
                                }
                                .padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { adding = false }) { Text("Fechar") } }
        )
    }
}
