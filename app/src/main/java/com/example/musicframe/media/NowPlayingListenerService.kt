package com.example.musicframe.media

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
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
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NowPlayingListenerService : NotificationListenerService() {

    // 缓存 packageManager 引用，避免在延迟回调中无法访问
    private var cachedPackageManager: PackageManager? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        // 缓存 packageManager 引用
        cachedPackageManager = packageManager
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        writeDebugLog("[$timestamp] onListenerConnected 被调用\n")
        writeDebugLog("[$timestamp] packageManager 已缓存：${cachedPackageManager != null}\n")
        Log.d("NowPlayingListener", "packageManager 已缓存：${cachedPackageManager != null}")
        
        // 延迟检查活跃通知，确保 activeNotifications 已完全加载
        // 这样可以捕获在 app 启动前就已经在播放的音乐
        Handler(Looper.getMainLooper()).postDelayed({
            val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            writeDebugLog("[$ts] 延迟回调开始，activeNotifications 数量：${activeNotifications?.size ?: 0}\n")
            writeDebugLog("[$ts] cachedPackageManager 状态：${cachedPackageManager != null}\n")
            Log.d("NowPlayingListener", "延迟回调开始，activeNotifications 数量：${activeNotifications?.size ?: 0}")
            val mediaNotifications = activeNotifications.filter { 
                it.notification.isMediaStyle() 
            }
            writeDebugLog("[$ts] 媒体通知数量：${mediaNotifications.size}\n")
            Log.d("NowPlayingListener", "媒体通知数量：${mediaNotifications.size}")
            // 只更新最后一个媒体通知（通常是当前正在播放的）
            mediaNotifications.lastOrNull()?.let { 
                writeDebugLog("[$ts] 处理媒体通知：${it.packageName}\n")
                Log.d("NowPlayingListener", "处理媒体通知：${it.packageName}")
                updateFromNotification(it) 
            }
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
            extractPremiumColor(bmp)
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

    // 常见音乐播放器包名映射表
    private val musicAppNames = mapOf(
        "ink.trantor.coneplayer.gp" to "光锥音乐",
        "com.netease.cloudmusic" to "网易云音乐",
        "com.netease.cloudmusic.lite" to "网易云音乐概念版",
        "com.tencent.qqmusic" to "QQ 音乐",
        "com.tencent.qqmusiclocal" to "QQ 音乐本地版",
        "com.xiami.music" to "虾米音乐",
        "fm.xiami.main" to "虾米音乐",
        "com.kuwo.player" to "酷我音乐",
        "com.kugou.android" to "酷狗音乐",
        "com.spotify.music" to "Spotify",
        "com.google.android.apps.music" to "Google Play 音乐",
        "com.amazon.mp3" to "Amazon Music",
        "com.apple.android.music" to "Apple Music"
    )

    private fun loadAppIcon(statusBarNotification: StatusBarNotification): Bitmap? {
        val packageName = statusBarNotification.packageName
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logBuilder = StringBuilder()
        logBuilder.appendLine("[$timestamp] 尝试加载 app 图标：$packageName")
        
        // 方案 1：优先从通知的 largeIcon 获取（通知自带的图标）
        logBuilder.appendLine("[$timestamp] 尝试方案 1：从通知 largeIcon 获取...")
        try {
            val largeIcon = statusBarNotification.notification.largeIcon
            if (largeIcon != null) {
                // 尝试从 extras 获取 ICON_BITMAP
                val extras = statusBarNotification.notification.extras
                val bitmap = extras.getParcelable<Bitmap>(Notification.EXTRA_LARGE_ICON)
                    ?: extras.getParcelable<Bitmap>("android.largeIcon")
                if (bitmap != null) {
                    logBuilder.appendLine("[$timestamp] ✓ 从 largeIcon 获取成功")
                    writeDebugLog(logBuilder.toString())
                    return bitmap
                }
            }
            logBuilder.appendLine("[$timestamp] ✗ largeIcon 为 null 或转换失败")
        } catch (e: Exception) {
            logBuilder.appendLine("[$timestamp] ✗ largeIcon 加载失败：${e.message}")
        }
        
        // 方案 2：使用 packageManager 获取应用图标
        logBuilder.appendLine("[$timestamp] 尝试方案 2：从 packageManager 获取...")
        logBuilder.appendLine("[$timestamp] cachedPackageManager: ${cachedPackageManager != null}")
        
        val pm = cachedPackageManager ?: applicationContext.packageManager
        logBuilder.appendLine("[$timestamp] 使用 packageManager: ${pm != null}")
        
        return try {
            logBuilder.appendLine("[$timestamp] 调用 getApplicationIcon($packageName)")
            val drawable = pm.getApplicationIcon(packageName)
            logBuilder.appendLine("[$timestamp] ✓ 成功获取应用图标：${drawable.javaClass}")
            val bitmap = drawable.safeToBitmap()
            logBuilder.appendLine("[$timestamp] ✓ 图标转换为 Bitmap 成功")
            writeDebugLog(logBuilder.toString())
            bitmap
        } catch (e: Exception) {
            logBuilder.appendLine("[$timestamp] ✗ 获取应用图标失败：${e.message}")
            logBuilder.appendLine("[$timestamp] 异常类型：${e.javaClass.simpleName}")
            
            // 方案 3：从通知小图标加载
            logBuilder.appendLine("[$timestamp] 尝试方案 3：从通知小图标加载...")
            try {
                val smallIcon = statusBarNotification.notification.smallIcon?.loadDrawable(this)
                if (smallIcon != null) {
                    val bitmap = smallIcon.safeToBitmap()
                    logBuilder.appendLine("[$timestamp] ✓ 从通知小图标加载成功")
                    writeDebugLog(logBuilder.toString())
                    bitmap
                } else {
                    logBuilder.appendLine("[$timestamp] ✗ 通知小图标为 null")
                    writeDebugLog(logBuilder.toString())
                    null
                }
            } catch (e2: Exception) {
                logBuilder.appendLine("[$timestamp] ✗ 从通知图标加载失败：${e2.message}")
                writeDebugLog(logBuilder.toString())
                null
            }
        }
    }

    private fun loadAppName(statusBarNotification: StatusBarNotification): String? {
        val packageName = statusBarNotification.packageName
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logBuilder = StringBuilder()
        logBuilder.appendLine("[$timestamp] 尝试加载 app 名称：$packageName")
        
        // 方案 1：从映射表获取
        val mappedName = musicAppNames[packageName]
        if (mappedName != null) {
            logBuilder.appendLine("[$timestamp] ✓ 从映射表获取：$mappedName")
            writeDebugLog(logBuilder.toString())
            return mappedName
        }
        logBuilder.appendLine("[$timestamp] ✗ 映射表中未找到")
        
        // 方案 2：使用 packageManager 获取应用名称
        logBuilder.appendLine("[$timestamp] 尝试方案 2：从 packageManager 获取...")
        logBuilder.appendLine("[$timestamp] cachedPackageManager: ${cachedPackageManager != null}")
        
        val pm = cachedPackageManager ?: packageManager
        logBuilder.appendLine("[$timestamp] 使用 packageManager: ${pm != null}")
        
        return try {
            val appInfo = pm.getApplicationInfo(packageName, PackageManager.GET_UNINSTALLED_PACKAGES)
            val appName = pm.getApplicationLabel(appInfo)?.toString()
            logBuilder.appendLine("[$timestamp] ✓ 成功获取应用名称：$appName")
            writeDebugLog(logBuilder.toString())
            appName
        } catch (e: Exception) {
            logBuilder.appendLine("[$timestamp] ✗ 获取应用名称失败：${e.message}")
            logBuilder.appendLine("[$timestamp] 异常类型：${e.javaClass.simpleName}")
            
            // 方案 3：从通知的 tickerText 获取（有些播放器会在这里显示应用名）
            logBuilder.appendLine("[$timestamp] 尝试方案 3：从通知 tickerText 获取...")
            val tickerText = statusBarNotification.notification.tickerText?.toString()
            if (!tickerText.isNullOrBlank()) {
                logBuilder.appendLine("[$timestamp] ✓ 从 tickerText 获取：$tickerText")
                writeDebugLog(logBuilder.toString())
                return tickerText
            }
            logBuilder.appendLine("[$timestamp] ✗ tickerText 为空")
            
            writeDebugLog(logBuilder.toString())
            null
        }
    }

    private fun writeDebugLog(content: String) {
        try {
            val logDir = File(getExternalFilesDir(null), "debug_logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            val logFile = File(logDir, "music_player_debug.txt")
            FileWriter(logFile, true).use { writer ->
                writer.appendLine(content)
                writer.appendLine("-".repeat(50))
            }
        } catch (e: Exception) {
            Log.e("NowPlayingListener", "写入日志失败：${e.message}")
        }
    }

    /**
     * 从专辑封面提取高级感主色调。
     * 优先使用 Vibrant 色板（鲜艳/大胆），而非平庸的 Dominant 均值色。
     * 提取后应用基础 Vibrance 提升，去除灰感。
     */
    private fun extractPremiumColor(bitmap: Bitmap): Int {
        val palette = Palette.from(bitmap).generate()
        // 优先取鲜艳色板：暗鲜 > 鲜 > 亮鲜 > 主色
        val rawColor = when {
            palette.darkVibrantSwatch != null -> palette.darkVibrantSwatch!!.rgb
            palette.vibrantSwatch != null -> palette.vibrantSwatch!!.rgb
            palette.lightVibrantSwatch != null -> palette.lightVibrantSwatch!!.rgb
            palette.dominantSwatch != null -> palette.dominantSwatch!!.rgb
            else -> DEFAULT_FALLBACK_COLOR
        }
        // 基础 Vibrance 提升：去除 Raw Palette 可能残留的灰感
        return boostBaseVibrancy(rawColor)
    }

    /**
     * 基础 Vibrance 提升：适度增加饱和度，低饱和区多提、高饱和区少提。
     */
    private fun boostBaseVibrancy(color: Int): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)
        val s = hsv[1]
        // 渐进式饱和度提升：低饱和多提（2x），中饱和适度（1.5x），高饱和少提（1.2x）
        hsv[1] = when {
            s < 0.25f -> (s * 2.0f).coerceAtMost(0.9f)
            s < 0.5f -> (s * 1.5f).coerceAtMost(0.9f)
            s < 0.7f -> (s * 1.2f).coerceAtMost(0.92f)
            else -> s
        }
        // 如果颜色过暗（V < 0.3），适当提亮避免沉闷
        if (hsv[2] < 0.3f) {
            hsv[2] = (hsv[2] + 0.15f).coerceAtMost(0.7f)
        }
        return android.graphics.Color.HSVToColor(hsv)
    }

    companion object {
        private const val DEFAULT_FALLBACK_COLOR = 0xFFEFEFEF.toInt()
    }
}
