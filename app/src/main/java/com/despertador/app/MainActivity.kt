package com.despertador.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private var tick by mutableIntStateOf(0)

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            DespertadorTheme { AppRoot(tick) }
        }
    }

    override fun onResume() {
        super.onResume()
        tick++
    }
}

@Composable
fun AppRoot(refreshTick: Int) {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var crash by remember { mutableStateOf(CrashLog.read(ctx)) }
    val stopwatch = remember { StopwatchState() }
    val tabs = listOf(
        "Alarmes" to Icons.Outlined.Alarm,
        "Relógio" to Icons.Outlined.Public,
        "Timers" to Icons.Outlined.HourglassEmpty,
        "Cronômetro" to Icons.Outlined.Timer,
    )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, maxLines = 1) },
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            Crossfade(targetState = tab, animationSpec = tween(220), label = "tabs") { t ->
                when (t) {
                    0 -> AlarmsScreen(refreshTick)
                    1 -> ClockScreen()
                    2 -> TimerScreen()
                    else -> StopwatchScreen(stopwatch)
                }
            }
        }
    }

    crash?.let { text ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text("O app fechou por um erro") },
            text = {
                Box(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text(text, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("erro", text))
                }) { Text("Copiar") }
            },
            dismissButton = {
                TextButton(onClick = {
                    CrashLog.clear(ctx)
                    crash = null
                }) { Text("Fechar") }
            }
        )
    }
}
