package com.despertador.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Seletor de rolagem circular (0 até count-1). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Wheel(count: Int, initial: Int, onChange: (Int) -> Unit) {
    val ctx = LocalContext.current
    val itemHeight = 60.dp
    val itemPx = with(LocalDensity.current) { itemHeight.toPx() }
    val total = count * 1000
    val base = total / 2 + initial
    val state = rememberLazyListState(initialFirstVisibleItemIndex = base - 1)
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val center by remember {
        derivedStateOf {
            state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > itemPx / 2) 2 else 1
        }
    }
    LaunchedEffect(state) {
        var last = -1
        snapshotFlow { center }.collect {
            val v = it % count
            if (last != -1 && v != last) Haptics.tick(ctx)
            last = v
            onChange(v)
        }
    }
    val on = MaterialTheme.colorScheme.onSurface
    Box(Modifier.height(itemHeight * 3).width(84.dp)) {
        LazyColumn(state = state, flingBehavior = fling, modifier = Modifier.fillMaxSize()) {
            items(total) { i ->
                Box(Modifier.height(itemHeight).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        two(i % count),
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Light,
                        color = if (i == center) on else on.copy(alpha = 0.25f)
                    )
                }
            }
        }
    }
}
