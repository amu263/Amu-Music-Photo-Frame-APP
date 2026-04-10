package com.example.musicframe.model

import android.graphics.Bitmap

/**
 * 描述当前播放歌曲的信息及其封面颜色。
 */
data class MusicMetadata(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val dominantColor: Int,
    val hasAlbumArt: Boolean,
    val albumArt: Bitmap? = null,
    val appPackageName: String? = null,
    val appName: String? = null,
    val appIcon: Bitmap? = null
) {
    val formattedPosition: String
        get() {
            if (durationMs <= 0) return positionMs.toTime()
            val position = positionMs.coerceAtLeast(0L).coerceAtMost(durationMs)
            return position.toTime() + "/" + durationMs.toTime()
        }

    private fun Long.toTime(): String {
        val totalSeconds = this / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
    }
}
