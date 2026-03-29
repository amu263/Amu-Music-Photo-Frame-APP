package com.example.musicframe.media

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import com.example.musicframe.model.MusicMetadata

class NowPlayingListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        // 延迟检查活跃通知，确保 activeNotifications 已完全加载
        // 这样可以捕获在 app 启动前就已经在播放的音乐
        Handler(Looper.getMainLooper()).postDelayed({
            val mediaNotifications = activeNotifications.filter { 
                it.notification.isMediaStyle() 
            }
            // 只更新最后一个媒体通知（通常是当前正在播放的）
            mediaNotifications.lastOrNull()?.let { updateFromNotification(it) }
        }, 500) // 延迟 500ms
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
        Log.d("NowPlayingListener", "加载 app 图标：$packageName")
        return try {
            val pm = packageManager
            val drawable = pm.getApplicationIcon(packageName)
            Log.d("NowPlayingListener", "成功获取应用图标：${drawable.javaClass}")
            drawable.safeToBitmap()
        } catch (e: Exception) {
            Log.e("NowPlayingListener", "获取应用图标失败：${e.message}")
            // 如果无法获取 app 图标，尝试从通知的小图标加载
            try {
                statusBarNotification.notification.smallIcon?.loadDrawable(this)?.safeToBitmap()
            } catch (e2: Exception) {
                Log.e("NowPlayingListener", "从通知图标加载也失败：${e2.message}")
                null
            }
        }
    }

    private fun loadAppName(statusBarNotification: StatusBarNotification): String? {
        val packageName = statusBarNotification.packageName
        Log.d("NowPlayingListener", "加载 app 名称：$packageName")
        return try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val appName = pm.getApplicationLabel(appInfo)?.toString()
            Log.d("NowPlayingListener", "成功获取应用名称：$appName")
            appName
        } catch (e: Exception) {
            Log.e("NowPlayingListener", "获取应用名称失败：${e.message}")
            null
        }
    }

    companion object {
        private const val DEFAULT_FALLBACK_COLOR = 0xFFEFEFEF.toInt()
    }
}
