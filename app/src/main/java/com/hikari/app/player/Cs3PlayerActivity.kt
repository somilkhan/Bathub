package com.hikari.app.player

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.hikari.app.R
import com.hikari.app.net.Http
import com.hikari.app.net.PlayerHttp
import org.json.JSONArray
import org.json.JSONObject

class Cs3PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cs3_player)
        findViewById<TextView>(R.id.cs_player_title).text = intent.getStringExtra("title").orEmpty()
        val source = runCatching {
            val a = JSONArray(intent.getStringExtra("sources").orEmpty())
            if (a.length() == 0) null else a.getJSONObject(0)
        }.getOrNull() ?: run { finish(); return }
        val url = Http.normalizeDriveUrl(source.optString("url"))
        if (url.isBlank()) { finish(); return }
        val headers = buildMap<String, String> {
            val o = source.optJSONObject("headers") ?: JSONObject()
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                o.optString(k).takeIf { it.isNotBlank() }?.let { put(k, it) }
            }
        }
        val mime = when {
            source.optBoolean("isM3u8") || url.substringBefore('?').endsWith(".m3u8", true) -> MimeTypes.APPLICATION_M3U8
            source.optBoolean("isMpd") || url.substringBefore('?').endsWith(".mpd", true) -> MimeTypes.APPLICATION_MPD
            else -> MimeTypes.VIDEO_MP4
        }
        val ds = OkHttpDataSource.Factory(PlayerHttp.client).setDefaultRequestProperties(headers)
        val ms = DefaultMediaSourceFactory(ds).createMediaSource(
            MediaItem.Builder().setUri(url).setMimeType(mime)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(intent.getStringExtra("title").orEmpty()).build())
                .build()
        )
        player = ExoPlayer.Builder(this).build().also { exo ->
            findViewById<androidx.media3.ui.PlayerView>(R.id.cs_player_view).player = exo
            exo.setMediaSource(ms); exo.prepare(); exo.playWhenReady = true
        }
    }
    override fun onDestroy() {
        findViewById<androidx.media3.ui.PlayerView>(R.id.cs_player_view).player = null
        player?.release(); player = null
        super.onDestroy()
    }
}