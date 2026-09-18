package com.hikari.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hikari.app.data.MediaItem
import com.hikari.app.ui.PosterLoader

private val HikariBlack = Color(0xFF050505)
private val HikariWhite = Color(0xFFF4F4F2)
private val HikariMuted = Color(0xFF9B9B99)
private val HikariLine = Color(0xFF303030)

@Composable
fun HikariFeaturedCard(
    item: MediaItem,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(HikariBlack)
            .border(BorderStroke(1.dp, HikariLine), RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(285.dp)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Color(0xFF111111))
        ) {
            AsyncImage(
                model = PosterLoader.model(item.backdropUrl, item.posterUrl),
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.22f))
            )
            Text(
                "光  HIKARI",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(22.dp),
                color = HikariWhite,
                fontSize = 13.sp,
                letterSpacing = 4.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 26.dp, vertical = 25.dp)
        ) {
            Box(
                Modifier
                    .width(72.dp)
                    .height(3.dp)
                    .background(HikariWhite)
            )
            Spacer(Modifier.height(22.dp))
            Text(
                item.title.uppercase(),
                color = HikariWhite,
                fontSize = 28.sp,
                lineHeight = 32.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Light,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "FEATURED",
                color = HikariMuted,
                fontSize = 12.sp,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .border(1.dp, HikariLine)
                        .padding(12.dp)
                ) {
                    Icon(
                        Icons.Outlined.ArrowDownward,
                        contentDescription = null,
                        tint = HikariWhite,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Row(
                    modifier = Modifier
                        .height(48.dp)
                        .border(1.dp, HikariLine)
                        .padding(horizontal = 17.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = HikariWhite,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "SEE MORE",
                        color = HikariWhite,
                        fontSize = 13.sp,
                        letterSpacing = 1.5.sp,
                    )
                }
            }
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
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 26.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                title.uppercase(),
                modifier = Modifier.weight(1f),
                color = HikariWhite,
                fontSize = 16.sp,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Medium,
            )
            if (onShowAll != null) {
                Text(
                    "SHOW ALL",
                    color = HikariMuted,
                    fontSize = 11.sp,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.clickable(onClick = onShowAll),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 26.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            itemsIndexed(
                items = items.take(12),
                key = { index, item -> "${item.providerId}|${item.type}|${item.id}|$index" },
            ) { index, item ->
                HikariPosterCard(
                    item = item,
                    index = index,
                    onClick = { onClick(item) },
                )
            }
        }
    }
}

@Composable
private fun HikariPosterCard(
    item: MediaItem,
    index: Int,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(138.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(204.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF191919))
        ) {
            AsyncImage(
                model = PosterLoader.model(item.posterUrl, item.backdropUrl),
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Text(
                "${index + 1}",
                color = HikariWhite.copy(alpha = 0.22f),
                fontSize = 74.sp,
                lineHeight = 74.sp,
                fontWeight = FontWeight.Thin,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (-2).dp, y = 17.dp)
                    .graphicsLayer(alpha = 0.9f),
            )
        }
        Spacer(Modifier.height(9.dp))
        Text(
            item.title,
            color = HikariWhite,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun HikariContinueShelf(
    entries: List<com.hikari.app.data.HistoryEntry>,
    backdropOf: (com.hikari.app.data.HistoryEntry) -> String?,
    onClick: (com.hikari.app.data.HistoryEntry) -> Unit,
    onRemove: (com.hikari.app.data.HistoryEntry) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "CONTINUE WATCHING",
            modifier = Modifier.padding(horizontal = 26.dp),
            color = HikariWhite,
            fontSize = 16.sp,
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(14.dp))
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 26.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(
                entries.take(8),
                key = { _, item -> item.uniqueKey },
            ) { _, entry ->
                Column(
                    modifier = Modifier
                        .width(238.dp)
                        .clickable { onClick(entry) }
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(134.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF171717))
                    ) {
                        AsyncImage(
                            model = PosterLoader.model(backdropOf(entry), entry.posterUrl),
                            contentDescription = entry.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .align(Alignment.BottomStart)
                                .background(HikariWhite.copy(alpha = 0.85f))
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        entry.title,
                        color = HikariWhite,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
