package com.example.musicframe.media

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.example.musicframe.model.HeadphoneInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HeadphoneInfoRepository(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val _headphoneInfo = MutableStateFlow<HeadphoneInfo?>(null)
    val headphoneInfo: StateFlow<HeadphoneInfo?> = _headphoneInfo.asStateFlow()

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            refreshHeadphones()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            refreshHeadphones()
        }
    }

    init {
        refreshHeadphones()
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
    }

    fun refreshHeadphones() {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.isSink }
        val target = devices.minByOrNull { preferredRank(it.type) }
        _headphoneInfo.value = target?.let { HeadphoneInfo.fromAudioDevice(it) }
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.unregisterAudioDeviceCallback(callback)
        }
    }

    private fun preferredRank(type: Int): Int {
        return when (type) {
            AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER -> 0
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> 1
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> 2
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY -> 3
            else -> 4
        }
    }
}
