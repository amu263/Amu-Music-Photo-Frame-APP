package com.example.musicframe

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class MusicFrameViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MusicFrameViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MusicFrameViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
