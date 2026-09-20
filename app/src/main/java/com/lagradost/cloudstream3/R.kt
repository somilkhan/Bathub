package com.lagradost.cloudstream3

/**
 * Resource bridge for the CloudStream player classes bundled as a jar.
 *
 * The jar's Android R class is intentionally stripped during packaging because
 * Hikari owns the application resources. These constants point the compiled
 * CloudStream player at equivalent Hikari resources/keys.
 */
object R {
    object string {
        const val autoplay_next_key = com.hikari.app.R.string.cs_autoplay_next_key
        const val go_back = com.hikari.app.R.string.cs_go_back
        const val ok = com.hikari.app.R.string.cs_ok
        const val play_torrent_button = com.hikari.app.R.string.cs_play_torrent_button
        const val prefer_media_type_key = com.hikari.app.R.string.cs_prefer_media_type_key
        const val quality_pref_key = com.hikari.app.R.string.cs_quality_pref_key
        const val quality_pref_mobile_data_key = com.hikari.app.R.string.cs_quality_pref_mobile_data_key
        const val software_decoding_key = com.hikari.app.R.string.cs_software_decoding_key
        const val torrent_info = com.hikari.app.R.string.cs_torrent_info
        const val torrent_not_accepted = com.hikari.app.R.string.cs_torrent_not_accepted
        const val torrent_preferred_media = com.hikari.app.R.string.cs_torrent_preferred_media
        const val subtitles_encoding_key = com.hikari.app.R.string.cs_subtitles_encoding_key
        const val preview_seekbar_key = com.hikari.app.R.string.cs_preview_seekbar_key
    }
}
