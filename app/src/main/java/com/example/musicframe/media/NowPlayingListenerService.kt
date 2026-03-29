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
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logBuilder = StringBuilder()
        logBuilder.appendLine("[$timestamp] 尝试加载 app 图标：$packageName")
        logBuilder.appendLine("[$timestamp] cachedPackageManager: ${cachedPackageManager != null}")
        
        // 使用缓存的 packageManager，如果为空则使用 applicationContext 的
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
            logBuilder.appendLine("[$timestamp] 异常堆栈：${android.util.Log.getStackTraceString(e)}")
            
            // 尝试方案 2：从通知小图标加载
            logBuilder.appendLine("[$timestamp] 尝试方案 2：从通知小图标加载...")
            try {
                val smallIcon = statusBarNotification.notification.smallIcon?.loadDrawable(this)
                if (smallIcon != null) {
                    val bitmap = smallIcon.safeToBitmap()
                    logBuilder.appendLine("[$timestamp] ✓ 从通知小图标加载成功")
                    writeDebugLog(logBuilder.toString())
                    bitmap
                } else {
                    logBuilder.appendLine("[$timestamp] ✗ 通知小图标为 null")
                    
                    // 尝试方案 3：使用包名作为备用图标标识
                    logBuilder.appendLine("[$timestamp] 备用方案：使用包名 $packageName 作为标识")
                    
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
        logBuilder.appendLine("[$timestamp] cachedPackageManager: ${cachedPackageManager != null}")
        
        // 使用缓存的 packageManager，如果为空则使用当前实例的
        val pm = cachedPackageManager ?: packageManager
        logBuilder.appendLine("[$timestamp] 使用 packageManager: ${pm != null}")
        
        return try {
            // 尝试使用 GET_UNINSTALLED_PACKAGES 标志
            val appInfo = pm.getApplicationInfo(packageName, PackageManager.GET_UNINSTALLED_PACKAGES)
            val appName = pm.getApplicationLabel(appInfo)?.toString()
            logBuilder.appendLine("[$timestamp] ✓ 成功获取应用名称：$appName")
            writeDebugLog(logBuilder.toString())
            appName
        } catch (e: Exception) {
            logBuilder.appendLine("[$timestamp] ✗ 获取应用名称失败：${e.message}")
            logBuilder.appendLine("[$timestamp] 异常类型：${e.javaClass.simpleName}")
            
            // 备用方案：返回包名作为应用名称
            logBuilder.appendLine("[$timestamp] 使用备用方案：返回包名 $packageName")
            writeDebugLog(logBuilder.toString())
            packageName
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

    companion object {
        private const val DEFAULT_FALLBACK_COLOR = 0xFFEFEFEF.toInt()
    }
}
