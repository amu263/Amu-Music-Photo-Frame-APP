package com.example.musicframe.media

import com.example.musicframe.model.MusicMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object MusicMetadataBroadcaster {
    private val _metadata = MutableStateFlow<MusicMetadata?>(null)
    val metadata: StateFlow<MusicMetadata?> = _metadata
    
    // 记录上次元数据更新时间
    private var lastUpdateTimestamp: Long = 0
    private const val METADATA_VALID_DURATION_MS = 10000 // 10秒内有更新认为服务正常

    fun update(metadata: MusicMetadata?) {
        _metadata.value = metadata
        if (metadata != null) {
            lastUpdateTimestamp = System.currentTimeMillis()
        }
    }
    
    /**
     * 检查是否有最近的元数据更新
     * 用于判断 NotificationListenerService 是否真正在工作
     */
    fun hasRecentMetadata(): Boolean {
        if (_metadata.value == null) return false
        val timeSinceLastUpdate = System.currentTimeMillis() - lastUpdateTimestamp
        return timeSinceLastUpdate < METADATA_VALID_DURATION_MS
    }
    
    /**
     * 获取距上次元数据更新的时间（毫秒）
     */
    fun getTimeSinceLastUpdate(): Long {
        if (lastUpdateTimestamp == 0L) return Long.MAX_VALUE
        return System.currentTimeMillis() - lastUpdateTimestamp
    }
}
