package com.hikari.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun HikariSplash(onFinished: () -> Unit) {
    var finished by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(2800)
        if (!finished) onFinished()
    }

    val transition = rememberInfiniteTransition(label = "hikari_splash")
    val beam by transition.animateFloat(
        initialValue = -22f,
        targetValue = 22f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beam"
    )
    val batX by transition.animateFloat(
        initialValue = -18f,
        targetValue = 118f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bat"
    )
    val wordmark by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, delayMillis = 1250),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wordmark"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF050505))
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .offset(x = beam.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.11f), Color.Transparent),
                        radius = 520f
                    )
                )
        )

        Canvas(Modifier.fillMaxSize()) {
            val base = size.height * 0.82f
            var x = 0f
            var i = 0
            while (x < size.width) {
                val width = size.width / 14f
                val height = size.height * (0.07f + (i % 5) * 0.025f)
                drawRect(
                    color = Color(0xFF101010),
                    topLeft = Offset(x, base - height),
                    size = androidx.compose.ui.geometry.Size(width - 3f, height)
                )
                x += width
                i++
            }
        }

        Text(
            "🦇",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 30.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = batX.dp, y = 150.dp)
        )

        Text(
            "光  HIKARI",
            color = Color.White.copy(alpha = wordmark),
            fontSize = 24.sp,
            letterSpacing = 5.sp,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}
