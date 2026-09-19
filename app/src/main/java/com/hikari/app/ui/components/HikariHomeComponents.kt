package com.hikari.app.ui.components

import android.provider.Settings
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.AbsoluteCutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hikari.app.data.MediaItem
import com.hikari.app.ui.PosterLoader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val HikariBlack = Color(0xFF050505)
private val HikariWhite = Color(0xFFF4F4F2)
private val HikariMuted = Color(0xFF9B9B99)
private val HikariLine = Color(0xFF444440)
private val HikariCutShape = AbsoluteCutCornerShape(bottomRight = 12.dp)

@Composable
fun HikariFeaturedCarousel(items: List<MediaItem>, onClick: (MediaItem) -> Unit) {
    val slides = remember(items) {
        items.distinctBy { "${it.providerId}|${it.type}|${it.id}" }.take(8)
    }
    if (slides.isEmpty()) return

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val pageWidth = maxWidth
        val state = rememberLazyListState()
        val fling = rememberSnapFlingBehavior(state)
        val scope = rememberCoroutineScope()
        LaunchedEffect(slides.size) {
            if (slides.size < 2) return@LaunchedEffect
            while (true) {
                delay(6500)
                if (!state.isScrollInProgress) {
                    scope.launch {
                        state.animateScrollToItem((state.firstVisibleItemIndex + 1) % slides.size)
                    }
                }
            }
        }
        LazyRow(
            state = state,
            flingBehavior = fling,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(0.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            itemsIndexed(
                slides,
                key = { _, item -> "${item.providerId}|${item.type}|${item.id}" }
            ) { _, item ->
                HikariFeaturedCard(item, pageWidth, onClick)
            }
        }
    }
}

@Composable
private fun HikariFeaturedCard(
    item: MediaItem,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .width(width)
            .height(610.dp)
            .background(HikariBlack)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = PosterLoader.model(item.backdropUrl, item.posterUrl),
            contentDescription = item.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        HikariAmbientMotion()
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.02f),
                        0.34f to Color.Black.copy(alpha = 0.10f),
                        0.62f to Color.Black.copy(alpha = 0.52f),
                        0.82f to Color.Black.copy(alpha = 0.90f),
                        1f to HikariBlack,
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(width.value * 0.5f, 70f),
                        radius = width.value * 0.9f,
                    )
                )
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 26.dp, vertical = 28.dp)
        ) {
            Box(
                Modifier
                    .width(58.dp)
                    .height(2.dp)
                    .background(HikariWhite)
            )
            Spacer(Modifier.height(14.dp))
            Text(
                item.title.uppercase(),
                color = HikariWhite,
                fontSize = 30.sp,
                lineHeight = 34.sp,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(9.dp))
            Text(
                buildString {
                    item.year?.let { append(it) }
                    if (item.year != null && item.genres.isNotEmpty()) append("  ·  ")
                    append(item.genres.take(2).joinToString("  ·  ").uppercase())
                }.ifBlank { "FEATURED" },
                color = HikariMuted,
                fontSize = 11.sp,
                letterSpacing = 0.8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                Spacer(Modifier.height(10.dp))
                Text(
                    overview,
                    color = HikariWhite.copy(alpha = 0.78f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(13.dp))
            Text(
                "SOURCE  \${item.providerId.uppercase()}",
                color = HikariMuted,
                fontSize = 9.sp,
                letterSpacing = 1.1.sp,
                modifier = Modifier
                    .border(0.5.dp, HikariLine)
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HikariActionButton("PLAY", true, Icons.Outlined.PlayArrow, onClick, Modifier.width(104.dp))
                HikariActionButton("SEE MORE", false, Icons.Outlined.ArrowForward, onClick, Modifier.width(128.dp))
            }
        }
    }
}

@Composable
private fun HikariAmbientMotion() {
    val context = LocalContext.current
    val reducedMotion = remember {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }
    if (reducedMotion) return

    val transition = rememberInfiniteTransition(label = "gotham_ambient")
    val sweep by transition.animateFloat(
        initialValue = -18f,
        targetValue = 18f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(7000),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "searchlight"
    )
    val fog by transition.animateFloat(
        initialValue = -24f,
        targetValue = 24f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(9000),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "fog"
    )
    val rain by transition.animateFloat(
        initialValue = 0f,
        targetValue = 46f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1100)
        ),
        label = "rain"
    )

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .offset(x = sweep.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.075f), Color.Transparent),
                        radius = 420f
                    )
                )
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .align(Alignment.BottomCenter)
                .offset(x = fog.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.055f), Color.Transparent),
                        radius = 360f
                    )
                )
        )
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val spacing = 22.dp.toPx()
            val length = 14.dp.toPx()
            var x = -size.height
            while (x < size.width + size.height) {
                val y = ((x + rain.dp.toPx()) % (size.height + 80.dp.toPx())) - 40.dp.toPx()
                drawLine(
                    color = Color.White.copy(alpha = 0.085f),
                    start = Offset(x, y),
                    end = Offset(x - length, y + length * 2.2f),
                    strokeWidth = 1.dp.toPx()
                )
                x += spacing
            }
        }
        Text(
            "🦇",
            color = Color.White.copy(alpha = 0.20f),
            fontSize = 20.sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 116.dp, end = 24.dp)
        )
    }
}

@Composable
private fun HikariActionButton(
    text: String,
    primary: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .height(38.dp)
            .clip(HikariCutShape)
            .background(if (primary) HikariBlack else Color.Transparent)
            .border(0.5.dp, if (primary) HikariWhite else HikariLine, HikariCutShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (primary) {
                Text("🦇", color = HikariWhite.copy(alpha = 0.18f), fontSize = 17.sp)
                Spacer(Modifier.width(2.dp))
            }
            Icon(icon, contentDescription = null, tint = HikariWhite, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
            Text(text, color = HikariWhite, fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun HikariCatalogShelf(
    title: String,
    items: List<MediaItem>,
    onClick: (MediaItem) -> Unit,
    onShowAll: (() -> Unit)? = null,
) {
    val trending = title.contains("trend", ignoreCase = true)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 26.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                title.uppercase(),
                Modifier.weight(1f),
                color = HikariWhite,
                fontSize = 16.sp,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Medium,
            )
            if (onShowAll != null) {
                Text(
                    "SHOW ALL",
                    color = HikariMuted,
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.clickable(onClick = onShowAll),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 26.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            itemsIndexed(
                items.take(12),
                key = { index, item -> "${item.providerId}|${item.type}|${item.id}|$index" }
            ) { index, item ->
                HikariPosterCard(
                    item = item,
                    rank = if (trending) index + 1 else null,
                    onClick = { onClick(item) },
                )
            }
        }
    }
}

@Composable
private fun HikariPosterCard(
    item: MediaItem,
    rank: Int?,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        if (rank != null) {
            Text(
                rank.toString(),
                style = TextStyle(
                    color = Color.Transparent,
                    fontSize = 58.sp,
                    lineHeight = 58.sp,
                    fontWeight = FontWeight.Bold,
                    drawStyle = Stroke(width = 1.5f),
                ),
                modifier = Modifier.width(34.dp),
            )
        }
        Spacer(Modifier.width(if (rank != null) 6.dp else 0.dp))
        Column(Modifier.width(120.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF191919))
            ) {
                AsyncImage(
                    model = PosterLoader.model(item.posterUrl, item.backdropUrl),
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Spacer(Modifier.height(9.dp))
            Text(
                item.title,
                color = HikariWhite,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            item.year?.let {
                Text(
                    "YEAR  \${it}",
                    color = HikariMuted,
                    fontSize = 9.sp,
                    letterSpacing = 0.5.sp,
                )
            }
        }
    }
}
