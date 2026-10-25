package com.despertador.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Seletor de rolagem circular (0 até count-1).
 * - Arrastar/rolar: gira o seletor.
 * - Tocar no número do meio: abre o teclado numérico para digitar o valor.
 * - Tocar num número de cima/baixo: rola até ele.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Wheel(count: Int, initial: Int, onChange: (Int) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
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
    var editing by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        var last = -1
        snapshotFlow { center }.collect {
            val v = it % count
            if (last != -1 && v != last) Haptics.tick(ctx)
            last = v
            onChange(v)
        }
    }
    // Se o usuário voltar a arrastar, fecha a digitação.
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { if (it) editing = false }
    }

    fun goTo(v: Int) {
        val c = center
        val cur = c % count
        var d = ((v - cur) % count + count) % count
        if (d > count / 2) d -= count
        scope.launch { state.animateScrollToItem(c + d - 1) }
    }

    val on = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .height(itemHeight * 3)
            .width(84.dp)
            // faixa suave atrás do número selecionado
            .drawBehind {
                drawRoundRect(
                    color = on.copy(alpha = 0.08f),
                    topLeft = Offset(0f, itemPx),
                    size = Size(size.width, itemPx),
                    cornerRadius = CornerRadius(18.dp.toPx())
                )
            }
    ) {
        LazyColumn(state = state, flingBehavior = fling, modifier = Modifier.fillMaxSize()) {
            items(total) { i ->
                val isCenter = i == center
                Box(
                    Modifier
                        .height(itemHeight)
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (i == center) editing = true
                            else scope.launch { state.animateScrollToItem(i - 1) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        two(i % count),
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Light,
                        color = when {
                            isCenter && editing -> Color.Transparent
                            isCenter -> on
                            else -> on.copy(alpha = 0.25f)
                        }
                    )
                }
            }
        }
        if (editing) {
            WheelEditor(
                current = center % count,
                count = count,
                modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
                onDone = { v ->
                    editing = false
                    if (v != null) goTo(v.coerceIn(0, count - 1))
                }
            )
        }
    }
}

@Composable
private fun WheelEditor(current: Int, count: Int, modifier: Modifier, onDone: (Int?) -> Unit) {
    val focusManager = LocalFocusManager.current
    val fr = remember { FocusRequester() }
    var tf by remember { mutableStateOf(TextFieldValue(two(current), TextRange(0, 2))) }
    var gotFocus by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    val color = MaterialTheme.colorScheme.onSurface

    fun finish(text: String) {
        if (finished) return
        finished = true
        onDone(text.toIntOrNull())
        focusManager.clearFocus()
    }

    LaunchedEffect(Unit) { fr.requestFocus() }

    BasicTextField(
        value = tf,
        onValueChange = { n ->
            val d = n.text.filter { it.isDigit() }.take(2)
            tf = n.copy(text = d)
            // Fecha sozinho quando não dá mais para digitar outro dígito (ex.: "7" nas horas).
            if (d.length == 2 || (d.length == 1 && d.toInt() * 10 >= count)) finish(d)
        },
        modifier = modifier
            .focusRequester(fr)
            .onFocusChanged {
                if (it.isFocused) gotFocus = true
                else if (gotFocus) finish(tf.text)
            },
        textStyle = TextStyle(
            fontSize = 46.sp,
            fontWeight = FontWeight.Light,
            color = color,
            textAlign = TextAlign.Center
        ),
        cursorBrush = SolidColor(color),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { finish(tf.text) })
    )
}
