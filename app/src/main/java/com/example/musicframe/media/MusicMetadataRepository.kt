package com.example.musicframe.media

import com.example.musicframe.model.MusicMetadata
import kotlinx.coroutines.flow.StateFlow

class MusicMetadataRepository {
    val nowPlaying: StateFlow<MusicMetadata?> = MusicMetadataBroadcaster.metadata
}
