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
        @JvmField val autoplay_next_key = com.hikari.app.R.string.cs_autoplay_next_key
        @JvmField val go_back = com.hikari.app.R.string.cs_go_back
        @JvmField val ok = com.hikari.app.R.string.cs_ok
        @JvmField val play_torrent_button = com.hikari.app.R.string.cs_play_torrent_button
        @JvmField val prefer_media_type_key = com.hikari.app.R.string.cs_prefer_media_type_key
        @JvmField val quality_pref_key = com.hikari.app.R.string.cs_quality_pref_key
        @JvmField val quality_pref_mobile_data_key = com.hikari.app.R.string.cs_quality_pref_mobile_data_key
        @JvmField val software_decoding_key = com.hikari.app.R.string.cs_software_decoding_key
        @JvmField val torrent_info = com.hikari.app.R.string.cs_torrent_info
        @JvmField val torrent_not_accepted = com.hikari.app.R.string.cs_torrent_not_accepted
        @JvmField val torrent_preferred_media = com.hikari.app.R.string.cs_torrent_preferred_media
        @JvmField val subtitles_encoding_key = com.hikari.app.R.string.cs_subtitles_encoding_key
        @JvmField val preview_seekbar_key = com.hikari.app.R.string.cs_preview_seekbar_key
    }
}
