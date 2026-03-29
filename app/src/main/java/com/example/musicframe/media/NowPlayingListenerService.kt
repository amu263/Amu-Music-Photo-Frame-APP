package com.example.musicframe.media

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.annotation.RequiresApi
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import com.example.musicframe.model.MusicMetadata

class NowPlayingListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        // 当服务连接时，遍历所有活跃的通知，查找媒体播放通知
        // 这样可以捕获在 app 启动前就已经在播放的音乐
        val mediaNotifications = activeNotifications.filter { 
            it.notification.isMediaStyle() 
        }
        // 只更新最后一个媒体通知（通常是当前正在播放的）
        mediaNotifications.lastOrNull()?.let { updateFromNotification(it) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        updateFromNotification(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.notification.isMediaStyle()) {
            MusicMetadataBroadcaster.update(null)
        }
    }

    private fun updateFromNotification(statusBarNotification: StatusBarNotification) {
        val notification = statusBarNotification.notification
        if (!notification.isMediaStyle()) return
        val extras = notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val artist = extras.getString(Notification.EXTRA_TEXT) ?: ""
        val album = extras.getString(Notification.EXTRA_SUB_TEXT) ?: ""
        val duration = extras.getLong("android.media.metadata.DURATION", 0L)
        val position = extras.getLong("android.media.metadata.PLAYBACK_POSITION", 0L)

        val artwork = notification.loadAlbumArt(this)
        val dominantColor = artwork?.let { bmp ->
            Palette.from(bmp).generate().getDominantColor(DEFAULT_FALLBACK_COLOR)
        } ?: DEFAULT_FALLBACK_COLOR

        val metadata = MusicMetadata(
            title = title,
            artist = artist,
            album = album,
            durationMs = duration,
            positionMs = position,
            dominantColor = dominantColor,
            hasAlbumArt = artwork != null,
            albumArt = artwork,
            appPackageName = statusBarNotification.packageName,
            appName = loadAppName(statusBarNotification),
            appIcon = loadAppIcon(statusBarNotification)
        )
        MusicMetadataBroadcaster.update(metadata)
    }

    private fun Notification.isMediaStyle(): Boolean {
        val template = extras.getString("android.template") ?: return false
        return template.contains("MediaStyle", ignoreCase = true)
    }

    private fun Notification.loadAlbumArt(context: Context): Bitmap? {
        @Suppress("DEPRECATION")
        val rawExtra = extras.get(Notification.EXTRA_LARGE_ICON)
        val extraBitmap = when {
            rawExtra is Bitmap -> rawExtra
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && rawExtra is Icon ->
                loadIconBitmap(rawExtra, context)
            else -> null
        }
        if (extraBitmap != null) return extraBitmap

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // 用 getLargeIcon() 拿到 Icon?
            loadIconBitmap(this.getLargeIcon(), context)
        } else {
            @Suppress("DEPRECATION")
            this.largeIcon // 低版本直接返回 Bitmap 兜底
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun loadIconBitmap(icon: Icon?, context: Context): Bitmap? {
        icon ?: return null
        return runCatching {
            icon.loadDrawable(context)?.safeToBitmap()
        }.getOrNull()
    }

    private fun Drawable.safeToBitmap(): Bitmap? {
        val width = intrinsicWidth.coerceAtLeast(1)
        val height = intrinsicHeight.coerceAtLeast(1)
        return runCatching { toBitmap(width, height, Bitmap.Config.ARGB_8888) }.getOrNull()
    }

    private fun loadAppIcon(statusBarNotification: StatusBarNotification): Bitmap? {
        val packageName = statusBarNotification.packageName
        val pm = runCatching { packageManager }.getOrNull() ?: return null
        val drawable = runCatching { pm.getApplicationIcon(packageName) }.getOrNull()
            ?: statusBarNotification.notification.smallIcon?.loadDrawable(this)
        return drawable?.safeToBitmap()
    }

    private fun loadAppName(statusBarNotification: StatusBarNotification): String? {
        val pm = runCatching { packageManager }.getOrNull() ?: return null
        val result = runCatching {
            pm.getApplicationInfo(statusBarNotification.packageName, 0)
        }.getOrNull()?.let { appInfo ->
            pm.getApplicationLabel(appInfo)?.toString()
        }
        return result
    }

    companion object {
        private const val DEFAULT_FALLBACK_COLOR = 0xFFEFEFEF.toInt()
    }
}
