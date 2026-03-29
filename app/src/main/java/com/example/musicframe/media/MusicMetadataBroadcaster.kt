package com.example.musicframe.media

import com.example.musicframe.model.MusicMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object MusicMetadataBroadcaster {
    private val _metadata = MutableStateFlow<MusicMetadata?>(null)
    val metadata: StateFlow<MusicMetadata?> = _metadata

    fun update(metadata: MusicMetadata?) {
        _metadata.value = metadata
    }
}
