package com.hikari.app.player

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.hikari.app.R
import com.hikari.app.data.StreamSource
import com.hikari.app.net.Http
import com.hikari.app.net.PlayerHttp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import org.json.JSONArray
import org.json.JSONObject

/**
 * CS3 playback engine.
 *
 * This is intentionally a separate Activity/engine from Hikari Player, but it
 * consumes the SAME generic StreamSource payload. Provider identity never
 * selects the player: the global playerEngine setting does.
 *
 * Sources are live: the detail screen can open this Activity as soon as the
 * first server arrives and continue appending servers from any extension.
 */
class Cs3PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private var liveJob: Job? = null
    private var doneJob: Job? = null
    private val tried = LinkedHashSet<String>()
    private var sources = emptyList<JSONObject>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cs3_player)

        val title = intent.getStringExtra("title").orEmpty()
        findViewById<TextView>(R.id.cs_player_title).text = title

        mergeSources(parseSources(intent.getStringExtra("sources").orEmpty()))

        val sessionId = intent.getStringExtra("streamsLiveId").orEmpty()
        if (sessionId.isBlank()) {
            if (sources.isEmpty()) {
                Toast.makeText(this, "No playable sources found", Toast.LENGTH_LONG).show()
                finish()
            } else {
                playNext()
            }
            return
        }

        liveJob = lifecycleScope.launch {
            StreamsLive.flow(sessionId).collect { incoming ->
                mergeSources(incoming.map { it.toJson() })
                if (player == null) playNext()
            }
        }

        doneJob = lifecycleScope.launch {
            StreamsLive.doneFlow(sessionId).collect { done ->
                if (done && player == null) {
                    if (sources.isEmpty()) {
                        Toast.makeText(
                            this@Cs3PlayerActivity,
                            "No playable sources found",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    finish()
                }
            }
        }

        if (sources.isNotEmpty()) playNext()
    }

    private fun StreamSource.toJson() = JSONObject()
        .put("name", name)
        .put("url", url)
        .put("headers", JSONObject(headers))
        .put("isM3u8", isM3u8)
        .put("isMpd", isMpd)
        .put("isTorrent", isTorrent)
        .put("infoHash", infoHash ?: "")
        .put("fileIdx", fileIdx ?: -1)
        .put("trackers", JSONArray(trackers))

    private fun parseSources(raw: String): List<JSONObject> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val source = array.optJSONObject(i) ?: continue
                if (Http.normalizeDriveUrl(source.optString("url")).isNotBlank()) add(source)
            }
        }
    }.getOrDefault(emptyList())

    private fun mergeSources(incoming: List<JSONObject>) {
        if (incoming.isEmpty()) return
        val merged = LinkedHashMap<String, JSONObject>()
        for (source in sources + incoming) {
            val url = Http.normalizeDriveUrl(source.optString("url"))
            if (url.isNotBlank()) merged.putIfAbsent(url, source)
        }
        sources = merged.values.toList()
    }

    private fun playNext() {
        val source = sources.firstOrNull {
            Http.normalizeDriveUrl(it.optString("url")) !in tried
        } ?: return

        val url = Http.normalizeDriveUrl(source.optString("url"))
        tried += url

        val headers = buildMap<String, String> {
            val objectHeaders = source.optJSONObject("headers") ?: JSONObject()
            val keys = objectHeaders.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                objectHeaders.optString(key).takeIf { it.isNotBlank() }?.let { put(key, it) }
            }
        }

        val mime = when {
            source.optBoolean("isM3u8") ||
                url.substringBefore('?').endsWith(".m3u8", true) ->
                MimeTypes.APPLICATION_M3U8
            source.optBoolean("isMpd") ||
                url.substringBefore('?').endsWith(".mpd", true) ->
                MimeTypes.APPLICATION_MPD
            else -> MimeTypes.VIDEO_MP4
        }

        val dataSource = OkHttpDataSource.Factory(PlayerHttp.client)
            .setDefaultRequestProperties(headers)

        val mediaSource = DefaultMediaSourceFactory(dataSource).createMediaSource(
            MediaItem.Builder()
                .setUri(url)
                .setMimeType(mime)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(intent.getStringExtra("title").orEmpty())
                        .build()
                )
                .build()
        )

        player?.release()
        player = ExoPlayer.Builder(this).build().also { exo ->
            findViewById<androidx.media3.ui.PlayerView>(R.id.cs_player_view).player = exo
            exo.addListener(object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    player?.release()
                    player = null
                    playNext()
                }
            })
            exo.setMediaSource(mediaSource)
            exo.prepare()
            exo.playWhenReady = true
        }
    }

    override fun onDestroy() {
        liveJob?.cancel()
        doneJob?.cancel()
        findViewById<androidx.media3.ui.PlayerView>(R.id.cs_player_view).player = null
        player?.release()
        player = null
        super.onDestroy()
    }
}
