package com.hikari.app.player

import android.app.Dialog
import android.os.Bundle
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import com.hikari.app.R
import com.hikari.app.data.DrmSpec
import com.hikari.app.data.StreamSource
import com.hikari.app.data.SubtitleSource
import com.hikari.app.net.Http
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.ui.player.CS3IPlayer
import com.lagradost.cloudstream3.ui.player.CSPlayerLoading
import com.lagradost.cloudstream3.ui.player.ErrorEvent
import com.lagradost.cloudstream3.ui.player.PlayerAttachedEvent
import com.lagradost.cloudstream3.ui.player.PlayerEvent
import com.lagradost.cloudstream3.ui.player.ResizedEvent
import com.lagradost.cloudstream3.ui.player.StatusEvent
import com.lagradost.cloudstream3.ui.player.VideoEndedEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * CloudStream player engine host.
 *
 * Provider-neutral StreamSource data is converted only at this boundary into
 * CloudStream's ExtractorLink/SubtitleData contracts. CS3IPlayer owns actual
 * playback, networking, subtitles, DRM, track selection and player events.
 */
class Cs3PlayerActivity : ComponentActivity() {

    private val cloudPlayer = CS3IPlayer()

    private var mediaSession: MediaSession? = null
    private var attachedExo: ExoPlayer? = null
    private var liveJob: Job? = null
    private var doneJob: Job? = null

    private var sources: List<StreamSource> = emptyList()
    private val tried = LinkedHashSet<String>()
    private var currentIndex = -1
    private var loading = false
    private var destroyed = false

    private val playerView: PlayerView by lazy { findViewById(R.id.cs_player_view) }
    private val titleView: TextView by lazy { findViewById(R.id.cs_player_title) }
    private val sourceView: TextView by lazy { findViewById(R.id.cs_player_source) }
    private val errorView: TextView by lazy { findViewById(R.id.cs_player_error) }
    private val nextButton: Button by lazy { findViewById(R.id.cs_player_next) }
    private val sourcesButton: Button by lazy { findViewById(R.id.cs_player_sources) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cs3_player)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // The bundled CloudStream runtime uses this host context for its
        // player/network helpers. It is deliberately not provider-specific.
        CloudStreamApp.setContext(applicationContext)

        titleView.text = intent.getStringExtra("title").orEmpty()
        nextButton.setOnClickListener { playNextAvailable() }
        sourcesButton.setOnClickListener { showSourceChooser() }

        cloudPlayer.initCallbacks(::onCloudPlayerEvent)
        cloudPlayer.cacheSize = 0L
        cloudPlayer.simpleCacheSize = 0L

        sources = parseSources(intent.getStringExtra("sources").orEmpty())

        val sessionId = intent.getStringExtra("streamsLiveId").orEmpty()
        if (sessionId.isNotBlank()) {
            liveJob = lifecycleScope.launch {
                StreamsLive.flow(sessionId).collect { incoming ->
                    mergeSources(incoming)
                    if (currentIndex < 0 && sources.isNotEmpty()) {
                        playNextAvailable()
                    }
                }
            }

            doneJob = lifecycleScope.launch {
                StreamsLive.doneFlow(sessionId).collect { done ->
                    if (done && sources.isEmpty() && !loading) {
                        showFatal("No playable sources received.")
                    }
                }
            }
        }

        log("dispatch engine=cloudstream sourceCount=" + sources.size)

        if (sources.isEmpty() && sessionId.isBlank()) {
            showFatal("No playable sources received.")
        } else if (sources.isNotEmpty()) {
            playPreferred()
        }
    }

    private fun parseSources(raw: String): List<StreamSource> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                array.optJSONObject(i)?.let { add(it.toStreamSource(i)) }
            }
        }
    }.getOrDefault(emptyList())

    private fun JSONObject.toStreamSource(index: Int): StreamSource {
        val headers = buildMap {
            val obj = optJSONObject("headers") ?: JSONObject()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, obj.optString(key))
            }
        }

        val subtitles = buildList {
            val array = optJSONArray("subtitles") ?: JSONArray()
            for (i in 0 until array.length()) {
                val sub = array.optJSONObject(i) ?: continue
                add(
                    SubtitleSource(
                        lang = sub.optString("lang"),
                        url = sub.optString("url"),
                    )
                )
            }
        }

        val trackers = buildList {
            val array = optJSONArray("trackers") ?: JSONArray()
            for (i in 0 until array.length()) {
                array.optString(i).takeIf { it.isNotBlank() }?.let(::add)
            }
        }

        return StreamSource(
            name = optString("name", "Source " + (index + 1)),
            url = Http.normalizeDriveUrl(optString("url")),
            headers = headers,
            subtitles = subtitles,
            isTorrent = optBoolean("isTorrent"),
            infoHash = optString("infoHash").ifBlank { null },
            isM3u8 = optBoolean("isM3u8"),
            isMpd = optBoolean("isMpd"),
            fileIdx = optInt("fileIdx", -1).takeIf { it >= 0 },
            trackers = trackers,
            ytId = optString("ytId").ifBlank { null },
            externalUrl = optBoolean("externalUrl"),
            drm = parseDrm(optJSONObject("drm")),
            provider = optString("provider"),
            providerId = optString("providerId"),
            providerName = optString("providerName"),
        )
    }

    private fun parseDrm(json: JSONObject?): DrmSpec? {
        if (json == null || json == JSONObject.NULL) return null

        val params = buildMap {
            val obj = json.optJSONObject("keyRequestParameters") ?: JSONObject()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, obj.optString(key))
            }
        }

        return DrmSpec(
            kid = json.optString("kid").ifBlank { null },
            key = json.optString("key").ifBlank { null },
            uuid = json.optString("uuid").ifBlank { null },
            kty = json.optString("kty").ifBlank { null },
            licenseUrl = json.optString("licenseUrl").ifBlank { null },
            keyRequestParameters = params,
        )
    }

    private fun mergeSources(incoming: List<StreamSource>) {
        if (incoming.isEmpty()) return

        val merged = LinkedHashMap<String, StreamSource>()
        for (source in sources + incoming) {
            val key = source.infoHash?.ifBlank { null }
                ?: source.url.ifBlank { source.providerId + ":" + source.name }
            merged.putIfAbsent(key, source)
        }
        sources = merged.values.toList()
    }

    private fun playPreferred() {
        val index = sources.indexOfFirst { sourceKey(it) !in tried }
            .takeIf { it >= 0 } ?: 0
        playSource(index)
    }

    private fun playNextAvailable() {
        val index = sources.indexOfFirst { sourceKey(it) !in tried }
        if (index < 0) {
            showFatal("All available CloudStream sources failed.")
            return
        }
        playSource(index)
    }

    private fun playSource(index: Int) {
        if (destroyed || loading) return

        val source = sources.getOrNull(index) ?: return
        if (source.externalUrl && source.url.isBlank()) {
            markFailed(source, "External URL is not directly playable.")
            return
        }
        if (source.url.isBlank()) {
            markFailed(source, "Source has no playable URL.")
            return
        }

        loading = true
        currentIndex = index
        tried += sourceKey(source)
        errorView.text = ""

        val providerLabel = source.providerName
            .ifBlank { source.provider.ifBlank { source.name } }
        sourceView.text = "CloudStream  •  " + providerLabel

        log(
            "source " + (index + 1) + "/" + sources.size +
                " name=" + source.name +
                " provider=" + source.providerId
        )

        lifecycleScope.launch {
            runCatching {
                val input = withContext(Dispatchers.Default) {
                    CloudStreamPlayerAdapter.adapt(source)
                }

                cloudPlayer.setActiveSubtitles(input.subtitles)
                cloudPlayer.loadPlayer(
                    this@Cs3PlayerActivity,
                    sameEpisode = false,
                    link = input.link,
                    data = null,
                    startPosition = 0L,
                    subtitles = input.subtitles,
                    subtitle = null,
                    autoPlay = true,
                    preview = false,
                )
            }.onFailure { error ->
                onCloudPlayerError(error)
            }
        }
    }

    private fun onCloudPlayerEvent(event: PlayerEvent) {
        if (destroyed) return

        when (event) {
            is PlayerAttachedEvent -> {
                loading = false
                attachedExo = event.player as? ExoPlayer

                attachedExo?.let { exo ->
                    mediaSession?.release()
                    mediaSession = MediaSession.Builder(this, exo)
                        .setId("cloudstream-" + System.currentTimeMillis())
                        .build()

                    playerView.player = exo
                    log(
                        "attached source " + (currentIndex + 1) +
                            "/" + sources.size
                    )
                }
            }

            is ErrorEvent -> onCloudPlayerError(event.error)

            is StatusEvent -> {
                if (event.isPlaying != CSPlayerLoading.IsBuffering) {
                    loading = false
                }
            }

            is ResizedEvent -> {
                if (event.width > 0 && event.height > 0) {
                    playerView.resizeMode =
                        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            }

            is VideoEndedEvent -> playNextAvailable()

            else -> Unit
        }
    }

    private fun onCloudPlayerError(error: Throwable) {
        if (destroyed) return

        loading = false
        val message = error.message ?: error.javaClass.simpleName
        log(
            "source failed " + (currentIndex + 1) + "/" + sources.size +
                " -> " + message.take(180)
        )

        attachedExo = null
        playerView.player = null
        mediaSession?.release()
        mediaSession = null

        val next = sources.indexOfFirst { sourceKey(it) !in tried }
        if (next >= 0) {
            playSource(next)
        } else {
            showFatal(
                "CloudStream could not play this source.\n" +
                    message
            )
        }
    }

    private fun markFailed(source: StreamSource, reason: String) {
        tried += sourceKey(source)
        log("source failed -> " + reason)
        loading = false
        playNextAvailable()
    }

    private fun sourceKey(source: StreamSource): String =
        source.infoHash?.ifBlank { null }
            ?: source.url.ifBlank {
                source.providerId + ":" + source.name
            }

    private fun showSourceChooser() {
        if (sources.isEmpty()) return

        val dialog = Dialog(this)
        val items = sources.mapIndexed { index, source ->
            val provider = source.providerName
                .ifBlank { source.provider.ifBlank { "Unknown provider" } }
            (if (index == currentIndex) "▶ " else "") +
                source.name + "  •  " + provider
        }.toTypedArray()

        val list = ListView(this)
        list.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            items,
        )
        list.setOnItemClickListener { _, _, which, _ ->
            dialog.dismiss()
            tried.remove(sourceKey(sources[which]))
            playSource(which)
        }

        dialog.setTitle("CloudStream sources")
        dialog.setContentView(list)
        dialog.show()
    }

    private fun showFatal(message: String) {
        loading = false
        errorView.text = message
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun log(message: String) {
        com.hikari.app.data.Logs.log("Player", "CloudStreamPlayer: " + message)
    }

    override fun onStop() {
        cloudPlayer.onStop()
        super.onStop()
    }

    override fun onPause() {
        cloudPlayer.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (!destroyed) cloudPlayer.onResume(this)
    }

    override fun onDestroy() {
        destroyed = true
        liveJob?.cancel()
        doneJob?.cancel()

        playerView.player = null
        mediaSession?.release()
        mediaSession = null

        cloudPlayer.release()
        cloudPlayer.releaseCallbacks()
        attachedExo = null

        super.onDestroy()
    }
}
