@file:OptIn(ExperimentalMaterial3Api::class)

package com.despertador.app

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

private val DAY_NAMES = mapOf(1 to "Dom", 2 to "Seg", 3 to "Ter", 4 to "Qua", 5 to "Qui", 6 to "Sex", 7 to "Sáb")

fun repeatText(days: Set<Int>, prefix: String = ""): String = when {
    days.isEmpty() -> "Uma vez"
    days.size == 7 -> "Todos os dias"
    else -> prefix + days.sorted().joinToString(", ") { DAY_NAMES[it] ?: "" }
}

@Composable
fun AlarmsScreen(refreshTick: Int) {
    val ctx = LocalContext.current
    var alarms by remember { mutableStateOf(AlarmRepo.load(ctx)) }
    var dismissed by remember { mutableStateOf(AlarmRepo.isDismissedToday(ctx)) }
    var editing by remember { mutableStateOf<Alarm?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    fun reload() {
        alarms = AlarmRepo.load(ctx)
        dismissed = AlarmRepo.isDismissedToday(ctx)
    }
    LaunchedEffect(refreshTick) { reload() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Alarmes", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "Menu") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Otimização de bateria") },
                            onClick = {
                                menuOpen = false
                                ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            }
                        )
                        if (Build.VERSION.SDK_INT >= 34) {
                            DropdownMenuItem(
                                text = { Text("Permitir tela cheia") },
                                onClick = {
                                    menuOpen = false
                                    ctx.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                            Uri.parse("package:${ctx.packageName}")
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }

            if (dismissed || alarms.any { it.enabled }) {
                Surface(
                    onClick = {
                        if (dismissed) AlarmRepo.undoDismiss(ctx) else AlarmRepo.dismissAllToday(ctx)
                        reload()
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        if (dismissed) "Dispensados hoje · Desfazer" else "Dispensar todos",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            if (alarms.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    Text("Nenhum alarme", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(alarms, key = { it.id }) { a ->
                    AlarmCard(
                        a,
                        onClick = { editing = a },
                        onToggle = { on ->
                            AlarmRepo.setEnabled(ctx, a.id, on)
                            reload()
                        }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = {
                val c = Calendar.getInstance()
                editing = Alarm(0, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(24.dp)
        ) { Icon(Icons.Filled.Add, "Novo alarme") }
    }

    editing?.let { a ->
        val del = {
            AlarmRepo.delete(ctx, a.id)
            reload()
            editing = null
        }
        AlarmEditSheet(
            initial = a,
            onSave = { n ->
                val s = if (n.id == 0) n.copy(id = AlarmRepo.nextId(ctx)) else n
                AlarmRepo.save(ctx, s)
                reload()
                editing = null
                val now = System.currentTimeMillis()
                val t = nextTrigger(s, now, AlarmRepo.dismissedUntil(ctx))
                Toast.makeText(ctx, "Alarme em " + untilText(t - now), Toast.LENGTH_SHORT).show()
            },
            onDelete = if (a.id != 0) del else null,
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun AlarmCard(a: Alarm, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val textColor = if (a.enabled) onSurface else onSurface.copy(alpha = 0.45f)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            val sub = repeatText(a.days) + if (a.label.isNotBlank()) " · ${a.label}" else ""
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = textColor)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${two(a.hour)}:${two(a.minute)}",
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Light,
                    color = textColor,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = a.enabled, onCheckedChange = onToggle)
            }
        }
    }
}

private fun pickedUri(data: Intent?): Uri? {
    if (data == null) return null
    return if (Build.VERSION.SDK_INT >= 33) {
        data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
    }
}

@Composable
fun AlarmEditSheet(
    initial: Alarm,
    onSave: (Alarm) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    var hour by remember { mutableIntStateOf(initial.hour) }
    var minute by remember { mutableIntStateOf(initial.minute) }
    var days by remember { mutableStateOf(initial.days) }
    var label by remember { mutableStateOf(initial.label) }
    var soundOn by remember { mutableStateOf(initial.soundOn) }
    var soundUri by remember { mutableStateOf(initial.soundUri) }
    var vibrate by remember { mutableStateOf(initial.vibrate) }
    var snoozeOn by remember { mutableStateOf(initial.snoozeOn) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            soundUri = pickedUri(r.data)?.toString()
        }
    }
    val soundName = remember(soundUri) {
        try {
            val u = soundUri?.let { Uri.parse(it) } ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            RingtoneManager.getRingtone(ctx, u)?.getTitle(ctx) ?: "Padrão"
        } catch (e: Exception) {
            "Padrão"
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Wheel(24, initial.hour) { hour = it }
                Text(":", fontSize = 46.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(horizontal = 8.dp))
                Wheel(60, initial.minute) { minute = it }
            }

            Text(repeatText(days, "A cada "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                val letters = listOf("D", "S", "T", "Q", "Q", "S", "S")
                letters.forEachIndexed { i, l ->
                    val d = i + 1
                    val sel = d in days
                    val prim = MaterialTheme.colorScheme.primary
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (sel) prim else Color.Transparent)
                            .border(1.dp, if (sel) prim else MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable { days = if (sel) days - d else days + d },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(l, color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            OutlinedTextField(
                value = label,
                onValueChange = { label = it.take(40) },
                placeholder = { Text("Rótulo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            SettingRow("Som do alarme", soundName, soundOn, { soundOn = it }) {
                val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    val existing = soundUri?.let { Uri.parse(it) }
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
                }
                picker.launch(i)
            }
            SettingRow("Vibração", null, vibrate, { vibrate = it }, null)
            SettingRow("Adiar", "${initial.snoozeMinutes} minutos, ${initial.snoozeTimes} vezes", snoozeOn, { snoozeOn = it }, null)

            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = {
                    onSave(
                        initial.copy(
                            hour = hour, minute = minute, days = days, label = label.trim(), enabled = true,
                            soundOn = soundOn, soundUri = soundUri, vibrate = vibrate, snoozeOn = snoozeOn
                        )
                    )
                }) { Text("Salvar") }
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    onClick: (() -> Unit)?
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
