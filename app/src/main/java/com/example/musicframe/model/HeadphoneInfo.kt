package com.example.musicframe.model

import android.media.AudioDeviceInfo

/**
 * 描述当前正在输出音频的耳机/耳机线缆信息。
 */
data class HeadphoneInfo(
    val modelName: String,
    val protocol: String,
    val isBluetooth: Boolean,
    val typeLabel: String
) {
    fun asDisplayLine(): String {
        val parts = listOf(protocol, modelName).filter { it.isNotBlank() }.distinct()
        return parts.joinToString(" · ").ifBlank { typeLabel }
    }

    companion object {
        @Suppress("CyclomaticComplexMethod")
        fun fromAudioDevice(device: AudioDeviceInfo): HeadphoneInfo {
            val protocol = when (device.type) {
                AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER -> "LE Audio"
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "蓝牙 A2DP"
                AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "有线接口"
                AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB 音频"
                else -> "音频输出"
            }
            val isBluetooth = device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER

            val modelName = device.productName?.toString()?.takeIf { it.isNotBlank() }
                ?: when (device.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER -> "蓝牙耳机"
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "有线耳机"
                    AudioDeviceInfo.TYPE_USB_HEADSET -> "USB 耳机"
                    else -> "音频设备"
                }

            val typeLabel = when (device.type) {
                AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER -> "蓝牙 LE"
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "蓝牙"
                AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "有线"
                AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB"
                else -> "音频设备"
            }

            return HeadphoneInfo(
                modelName = modelName,
                protocol = protocol,
                isBluetooth = isBluetooth,
                typeLabel = typeLabel
            )
        }
    }
}
