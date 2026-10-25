@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.despertador.app

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.AlarmOff
import androidx.compose.material.icons.outlined.AlarmOn
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

private val DAY_NAMES = mapOf(1 to "Dom", 2 to "Seg", 3 to "Ter", 4 to "Qua", 5 to "Qui", 6 to "Sex", 7 to "Sáb")

fun repeatText(days: Set<Int>, prefix: String = ""): String = when {
    days.isEmpty() -> "Uma vez"
    days.size == 7 -> "Todos os dias"
    else -> prefix + days.sorted().joinToString(", ") { DAY_NAMES[it] ?: "" }
}

// ------------------------------------------------------------------
// Tela principal
// ------------------------------------------------------------------

@Composable
fun AlarmsScreen(refreshTick: Int) {
    val ctx = LocalContext.current
    var alarms by remember { mutableStateOf(AlarmRepo.load(ctx)) }
    var dismissed by remember { mutableStateOf(AlarmRepo.isDismissedToday(ctx)) }
    var editing by remember { mutableStateOf<Alarm?>(null) }
    var selected by remember { mutableStateOf(setOf<Int>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()

    fun reload() {
        alarms = AlarmRepo.load(ctx)
        dismissed = AlarmRepo.isDismissedToday(ctx)
        selected = selected.filter { id -> alarms.any { it.id == id } }.toSet()
    }

    fun toggleSelect(id: Int) {
        selected = if (id in selected) selected - id else selected + id
    }

    LaunchedEffect(refreshTick) { reload() }
    BackHandler(enabled = selecting) { selected = emptySet() }

    val allEnabled = alarms.filter { it.id in selected }.all { it.enabled }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = selecting,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "header"
            ) { sel ->
                if (sel) {
                    SelectionBar(
                        count = selected.size.coerceAtLeast(1),
                        allEnabled = allEnabled,
                        onClose = { selected = emptySet() },
                        onDuplicate = {
                            alarms.filter { it.id in selected }.forEach {
                                AlarmRepo.save(ctx, it.copy(id = AlarmRepo.nextId(ctx), skipUntil = 0L))
                            }
                            selected = emptySet()
                            reload()
                        },
                        onToggle = {
                            val turnOn = !allEnabled
                            alarms.filter { it.id in selected }.forEach { AlarmRepo.setEnabled(ctx, it.id, turnOn) }
                            selected = emptySet()
                            reload()
                        },
                        onDelete = { confirmDelete = true }
                    )
                } else {
                    TitleBar(menuOpen = menuOpen, onMenu = { menuOpen = it })
                }
            }

            AnimatedVisibility(
                visible = !selecting && (dismissed || alarms.any { it.enabled }),
                enter = fadeIn(tween(220)) + expandVertically(tween(220)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(180))
            ) {
                Surface(
                    onClick = {
                        Haptics.click(ctx)
                        if (dismissed) AlarmRepo.undoDismiss(ctx) else AlarmRepo.dismissAllToday(ctx)
                        reload()
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    AnimatedContent(
                        targetState = dismissed,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                        label = "pill"
                    ) { d ->
                        Text(
                            if (d) "Dispensados hoje · Desfazer" else "Dispensar todos",
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            AnimatedVisibility(visible = alarms.isEmpty(), enter = fadeIn(tween(250)), exit = fadeOut(tween(100))) {
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
                        a = a,
                        selected = a.id in selected,
                        selecting = selecting,
                        onClick = { if (selecting) toggleSelect(a.id) else editing = a },
                        onLongClick = {
                            Haptics.click(ctx)
                            toggleSelect(a.id)
                        },
                        onToggle = { on ->
                            AlarmRepo.setEnabled(ctx, a.id, on)
                            reload()
                        },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = !selecting,
            enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.85f),
            exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.85f),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            FloatingActionButton(
                onClick = {
                    val c = Calendar.getInstance()
                    editing = Alarm(0, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
                },
                modifier = Modifier.padding(24.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(24.dp)
            ) { Icon(Icons.Filled.Add, "Novo alarme") }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (selected.size == 1) "Excluir alarme?" else "Excluir ${selected.size} alarmes?") },
            confirmButton = {
                TextButton(onClick = {
                    selected.forEach { AlarmRepo.delete(ctx, it) }
                    selected = emptySet()
                    confirmDelete = false
                    reload()
                }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
        )
    }

    editing?.let { a ->
        val del = {
            AlarmRepo.delete(ctx, a.id)
            reload()
            editing = null
        }
        AlarmEditSheet(
            initial = a,
            onSave = { n, interval, count ->
                val now = System.currentTimeMillis()
                val msg: String
                if (n.id == 0) {
                    val created = AlarmRepo.createSeries(ctx, n, interval, count)
                    val t = nextTrigger(created.first(), now, AlarmRepo.dismissedUntil(ctx))
                    msg = if (created.size > 1) {
                        "${created.size} alarmes criados · o primeiro em ${untilText(t - now)}"
                    } else {
                        "Alarme em ${untilText(t - now)}"
                    }
                } else {
                    AlarmRepo.save(ctx, n)
                    val t = nextTrigger(n, now, AlarmRepo.dismissedUntil(ctx))
                    msg = "Alarme em ${untilText(t - now)}"
                }
                reload()
                editing = null
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
            },
            onDelete = if (a.id != 0) del else null,
            onDismiss = { editing = null }
        )
    }
}

// ------------------------------------------------------------------
// Barras do topo
// ------------------------------------------------------------------

@Composable
private fun TitleBar(menuOpen: Boolean, onMenu: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(start = 24.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Alarmes", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        Box {
            IconButton(onClick = { onMenu(true) }) { Icon(Icons.Filled.MoreVert, "Menu") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenu(false) }) {
                DropdownMenuItem(
                    text = { Text("Otimização de bateria") },
                    onClick = {
                        onMenu(false)
                        ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                )
                if (Build.VERSION.SDK_INT >= 34) {
                    DropdownMenuItem(
                        text = { Text("Permitir tela cheia") },
                        onClick = {
                            onMenu(false)
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
}

@Composable
private fun SelectionBar(
    count: Int,
    allEnabled: Boolean,
    onClose: () -> Unit,
    onDuplicate: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(start = 8.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Fechar seleção") }
        Text(
            if (count == 1) "1 selecionado" else "$count selecionados",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            modifier = Modifier.weight(1f).padding(start = 4.dp)
        )
        IconButton(onClick = onDuplicate) { Icon(Icons.Outlined.ContentCopy, "Duplicar") }
        IconButton(onClick = onToggle) {
            Icon(
                if (allEnabled) Icons.Outlined.AlarmOff else Icons.Outlined.AlarmOn,
                if (allEnabled) "Desativar" else "Ativar"
            )
        }
        IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Excluir") }
    }
}

// ------------------------------------------------------------------
// Cartão de alarme
// ------------------------------------------------------------------

@Composable
private fun AlarmCard(
    a: Alarm,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, tween(120), label = "press")
    val container by animateColorAsState(
        if (selected) cs.primaryContainer.copy(alpha = 0.55f) else cs.surfaceVariant,
        tween(220), label = "bg"
    )
    val contentAlpha by animateFloatAsState(if (a.enabled) 1f else 0.45f, tween(260), label = "alpha")
    val textColor = cs.onSurface.copy(alpha = contentAlpha)
    val shape = RoundedCornerShape(28.dp)

    Box(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(container)
            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick
            )
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
                AnimatedContent(
                    targetState = selecting,
                    transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) },
                    label = "trailing"
                ) { sel ->
                    Box(Modifier.width(52.dp), contentAlignment = Alignment.CenterEnd) {
                        if (sel) {
                            SelectMark(selected)
                        } else {
                            Switch(checked = a.enabled, onCheckedChange = onToggle)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectMark(selected: Boolean) {
    val cs = MaterialTheme.colorScheme
    val fill by animateColorAsState(if (selected) cs.primary else Color.Transparent, tween(180), label = "fill")
    val line by animateColorAsState(if (selected) cs.primary else cs.outline, tween(180), label = "line")
    Box(
        Modifier.size(30.dp).clip(CircleShape).background(fill).border(1.5.dp, line, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(visible = selected, enter = fadeIn(tween(150)) + scaleIn(tween(150)), exit = fadeOut(tween(100))) {
            Icon(Icons.Filled.Check, null, tint = cs.onPrimary, modifier = Modifier.size(18.dp))
        }
    }
}

// ------------------------------------------------------------------
// Folha de edição / criação
// ------------------------------------------------------------------

private fun pickedUri(data: Intent?): Uri? {
    if (data == null) return null
    return if (Build.VERSION.SDK_INT >= 33) {
        data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
    }
}

private fun seriesPreview(hour: Int, minute: Int, interval: Int, count: Int): String {
    val times = (0 until count).map {
        val total = (hour * 60 + minute + it * interval) % 1440
        "${two(total / 60)}:${two(total % 60)}"
    }
    return if (count <= 4) times.joinToString(", ") else times.take(2).joinToString(", ") + " … " + times.last()
}

@Composable
fun AlarmEditSheet(
    initial: Alarm,
    onSave: (Alarm, Int, Int) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val isNew = initial.id == 0
    var hour by remember { mutableIntStateOf(initial.hour) }
    var minute by remember { mutableIntStateOf(initial.minute) }
    var days by remember { mutableStateOf(initial.days) }
    var label by remember { mutableStateOf(initial.label) }
    var soundOn by remember { mutableStateOf(initial.soundOn) }
    var soundUri by remember { mutableStateOf(initial.soundUri) }
    var vibrate by remember { mutableStateOf(initial.vibrate) }
    var snoozeOn by remember { mutableStateOf(initial.snoozeOn) }
    var seriesOn by remember { mutableStateOf(false) }
    var interval by remember { mutableIntStateOf(10) }
    var count by remember { mutableIntStateOf(7) }

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

            AnimatedContent(
                targetState = repeatText(days, "A cada "),
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) },
                label = "repeat"
            ) { t ->
                Text(t, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DaySelector(days) { days = it }

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

            if (isNew) {
                SettingRow(
                    "Sequência de alarmes",
                    if (seriesOn) "$count alarmes · a cada $interval min" else "Criar vários de uma vez",
                    seriesOn,
                    { seriesOn = it },
                    null
                )
                AnimatedVisibility(
                    visible = seriesOn,
                    enter = fadeIn(tween(200)) + expandVertically(tween(220)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(160))
                ) {
                    Column {
                        Stepper(
                            "Intervalo", "$interval min",
                            onMinus = { interval = (interval - 5).coerceAtLeast(5) },
                            onPlus = { interval = (interval + 5).coerceAtMost(60) }
                        )
                        Stepper(
                            "Quantidade", "$count alarmes",
                            onMinus = { count = (count - 1).coerceAtLeast(2) },
                            onPlus = { count = (count + 1).coerceAtMost(24) }
                        )
                        Text(
                            seriesPreview(hour, minute, interval, count),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                        )
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = {
                    val series = isNew && seriesOn
                    onSave(
                        initial.copy(
                            hour = hour, minute = minute, days = days, label = label.trim(), enabled = true,
                            soundOn = soundOn, soundUri = soundUri, vibrate = vibrate, snoozeOn = snoozeOn,
                            skipUntil = 0L
                        ),
                        if (series) interval else 0,
                        if (series) count else 1
                    )
                }) { Text(if (isNew && seriesOn) "Criar $count alarmes" else "Salvar") }
            }
        }
    }
}

// ------------------------------------------------------------------
// Dias da semana: toque ou arraste para marcar / desmarcar
// ------------------------------------------------------------------

@Composable
private fun DaySelector(days: Set<Int>, onChange: (Set<Int>) -> Unit) {
    val ctx = LocalContext.current
    val letters = listOf("D", "S", "T", "Q", "Q", "S", "S")
    val currentDays by rememberUpdatedState(days)
    val onDaysChange by rememberUpdatedState(onChange)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val slot = size.width / 7f
                    var working = currentDays
                    var last = (down.position.x / slot).toInt().coerceIn(0, 6)
                    // o primeiro dia tocado define se este gesto marca ou desmarca
                    val paint = (last + 1) !in working

                    fun paintDay(i: Int) {
                        val d = i + 1
                        if ((d in working) != paint) {
                            working = if (paint) working + d else working - d
                            onDaysChange(working)
                            Haptics.tick(ctx)
                        }
                    }

                    paintDay(last)
                    down.consume()
                    do {
                        val event = awaitPointerEvent()
                        val ch = event.changes.firstOrNull() ?: break
                        val now = (ch.position.x / slot).toInt().coerceIn(0, 6)
                        if (now != last) {
                            val step = if (now > last) 1 else -1
                            var i = last
                            while (i != now) {
                                i += step
                                paintDay(i)
                            }
                            last = now
                        }
                        ch.consume()
                    } while (ch.pressed)
                }
            }
    ) {
        letters.forEachIndexed { i, l ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                DayDot(l, (i + 1) in days)
            }
        }
    }
}

@Composable
private fun DayDot(letter: String, selected: Boolean) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) cs.primary else Color.Transparent, tween(160), label = "dayBg")
    val line by animateColorAsState(if (selected) cs.primary else cs.outline, tween(160), label = "dayLine")
    val txt by animateColorAsState(if (selected) cs.onPrimary else cs.onSurface, tween(160), label = "dayTxt")
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(bg).border(1.dp, line, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(letter, color = txt)
    }
}

// ------------------------------------------------------------------
// Linhas de configuração
// ------------------------------------------------------------------

@Composable
private fun Stepper(title: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = {
            Haptics.tick(ctx)
            onMinus()
        }) { Icon(Icons.Filled.Remove, "Diminuir") }
        Text(value, modifier = Modifier.widthIn(min = 92.dp), textAlign = TextAlign.Center)
        FilledTonalIconButton(onClick = {
            Haptics.tick(ctx)
            onPlus()
        }) { Icon(Icons.Filled.Add, "Aumentar") }
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
            .then(if (onClick != null) Modifier.combinedClickable(onClick = onClick) else Modifier)
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
