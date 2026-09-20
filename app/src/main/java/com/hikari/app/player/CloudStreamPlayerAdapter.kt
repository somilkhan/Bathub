@file:OptIn(com.lagradost.cloudstream3.Prerelease::class)

package com.hikari.app.player

import com.hikari.app.data.DrmSpec
import com.hikari.app.data.StreamSource
import com.hikari.app.data.SubtitleSource
import com.lagradost.cloudstream3.ui.player.SubtitleData
import com.lagradost.cloudstream3.ui.player.SubtitleOrigin
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newDrmExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.util.UUID
import kotlin.uuid.toKotlinUuid

/**
 * Boundary adapter between Hikari's provider-neutral StreamSource contract
 * and CloudStream's player contract.
 *
 * Providers never see these types. The conversion happens only when the
 * selected global player engine is CloudStream.
 */
object CloudStreamPlayerAdapter {

    data class Input(
        val link: ExtractorLink,
        val subtitles: Set<SubtitleData>,
    )

    suspend fun adapt(source: StreamSource): Input {
        require(source.url.isNotBlank()) {
            "CloudStream player requires a playable URL for " + source.name
        }

        val type = sourceType(source)
        val referer = source.headers.entries
            .firstOrNull { it.key.equals("referer", ignoreCase = true) }
            ?.value
            .orEmpty()

        val link = source.drm?.let { drm ->
            newDrmExtractorLink(
                source = source.providerName.ifBlank {
                    source.provider.ifBlank { source.name }
                },
                name = source.name,
                url = source.url,
                type = type,
                uuid = drmUuid(drm),
            ) {
                this.referer = referer
                this.quality = 400
                this.headers = source.headers
                this.kid = drm.kid?.takeIf { it.isNotBlank() }
                this.key = drm.key?.takeIf { it.isNotBlank() }
                this.kty = drm.kty?.takeIf { it.isNotBlank() }
                this.licenseUrl = drm.licenseUrl?.takeIf { it.isNotBlank() }
                this.keyRequestParameters = HashMap(drm.keyRequestParameters)
            }
        } ?: newExtractorLink(
            source = source.providerName.ifBlank {
                source.provider.ifBlank { source.name }
            },
            name = source.name,
            url = source.url,
            type = type,
        ) {
            this.referer = referer
            this.quality = 400
            this.headers = source.headers
        }

        return Input(link, source.subtitles.map(::adaptSubtitle).toSet())
    }

    private fun adaptSubtitle(source: SubtitleSource): SubtitleData {
        return SubtitleData(
            originalName = source.lang.ifBlank { "Subtitle" },
            nameSuffix = "",
            url = source.url,
            origin = SubtitleOrigin.URL,
            mimeType = subtitleMimeType(source.url),
            headers = emptyMap(),
            languageCode = source.lang.ifBlank { null },
        )
    }

    private fun subtitleMimeType(url: String): String {
        return when {
            url.contains(".vtt", true) -> androidx.media3.common.MimeTypes.TEXT_VTT
            url.contains(".ttml", true) || url.contains(".xml", true) ->
                androidx.media3.common.MimeTypes.APPLICATION_TTML
            else -> androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
        }
    }

    private fun sourceType(source: StreamSource): ExtractorLinkType {
        if (source.isTorrent) {
            val url = source.url.lowercase()
            if (url.startsWith("magnet:")) return ExtractorLinkType.MAGNET
            if (url.substringBefore('?').endsWith(".torrent")) return ExtractorLinkType.TORRENT

            // TorrServer-backed sources are already HTTP streams. Do not feed
            // those URLs into CloudStream's torrent dialog; play the resolved
            // stream as normal media while preserving torrent metadata.
        }
        return when {
            source.isM3u8 || source.url.substringBefore('?').endsWith(".m3u8", true) ->
                ExtractorLinkType.M3U8
            source.isMpd || source.url.substringBefore('?').endsWith(".mpd", true) ->
                ExtractorLinkType.DASH
            else -> ExtractorLinkType.VIDEO
        }
    }

    private fun drmUuid(drm: DrmSpec): kotlin.uuid.Uuid {
        val raw = drm.uuid.orEmpty().lowercase()
        return when (raw) {
            "edef8ba9-79d6-4ace-a3c8-27dcd51d21ed" ->
                com.lagradost.cloudstream3.utils.WIDEVINE_DRM_UUID
            "9a04f079-9840-4286-ab92-e65be0885f95" ->
                com.lagradost.cloudstream3.utils.PLAYREADY_DRM_UUID
            "e2719d58-a985-b3c9-781a-b030af78d30e" ->
                com.lagradost.cloudstream3.utils.CLEARKEY_DRM_UUID
            else -> runCatching {
                UUID.fromString(drm.uuid).toKotlinUuid()
            }.getOrElse {
                throw IllegalArgumentException("Unsupported DRM UUID: " + drm.uuid)
            }
        }
    }
}
