package com.despertador.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.util.Calendar

class AlarmRingActivity : ComponentActivity() {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        ContextCompat.registerReceiver(
            this, receiver, IntentFilter(AlarmService.ACTION_RING_ENDED), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        val label = intent.getStringExtra("label") ?: "Alarme"
        val canSnooze = intent.getBooleanExtra("can_snooze", false)
        setContent {
            DespertadorTheme {
                RingScreen(
                    label = label,
                    canSnooze = canSnooze,
                    onStop = { send(AlarmService.ACTION_STOP) },
                    onSnooze = { send(AlarmService.ACTION_SNOOZE) },
                )
            }
        }
    }

    private fun send(action: String) {
        startService(Intent(this, AlarmService::class.java).setAction(action))
        finish()
    }

    override fun onDestroy() {
        try { unregisterReceiver(receiver) } catch (e: Exception) { }
        super.onDestroy()
    }
}

@Composable
private fun RingScreen(label: String, canSnooze: Boolean, onStop: () -> Unit, onSnooze: () -> Unit) {
    var time by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val c = Calendar.getInstance()
            time = "${two(c.get(Calendar.HOUR_OF_DAY))}:${two(c.get(Calendar.MINUTE))}"
            delay(1000)
        }
    }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))
            Text(time, fontSize = 96.sp, fontWeight = FontWeight.Light)
            Text(label, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            if (canSnooze) {
                OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                    Text("Adiar", fontSize = 20.sp)
                }
                Spacer(Modifier.height(16.dp))
            }
            Button(onClick = onStop, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                Text("Parar", fontSize = 20.sp)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
