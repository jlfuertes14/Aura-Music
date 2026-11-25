package com.auramusic.app.core.media

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

data class AudioOutputDevice(
    val id: Int,
    val name: String,
    val typeLabel: String,
    val isBluetooth: Boolean,
    val isWired: Boolean,
    val isSpeaker: Boolean,
    val isActive: Boolean,
    val deviceInfo: AudioDeviceInfo?
)

object AudioOutputRouter {

    fun listOutputDevices(context: Context): List<AudioOutputDevice> {
        val audioManager = runCatching {
            context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        }.getOrNull() ?: return emptyList()

        val rawDevices = runCatching {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        }.getOrNull() ?: return emptyList()

        val selectedId = AuraPlayerManager.selectedAudioOutputId.value

        data class RawEntry(
            val info: AudioDeviceInfo,
            val id: Int,
            val name: String,
            val typeLabel: String,
            val isBluetooth: Boolean,
            val isWired: Boolean,
            val isSpeaker: Boolean,
            val priority: Int
        )

        val entries = mutableListOf<RawEntry>()
        val hasBuiltInSpeaker = rawDevices.any { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }

        for (device in rawDevices) {
            val type = device.type
            if (isInternalOrIgnoredType(type, hasBuiltInSpeaker)) continue

            val isBluetooth = isBluetoothOutputType(type)
            val isWired = isWiredOutputType(type)
            val isSpeaker = type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
                type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE

            val typeLabel = resolveTypeLabel(type, isBluetooth, isWired, isSpeaker)
            val productName = runCatching { device.productName?.toString()?.trim() }.getOrNull().orEmpty()
            val displayName = when {
                isSpeaker -> "Phone Speaker"
                productName.isNotBlank() -> productName
                else -> typeLabel
            }

            val priority = when {
                isBluetooth -> 0
                isWired -> 1
                !isSpeaker -> 2
                else -> 3
            }

            entries.add(
                RawEntry(
                    info = device,
                    id = device.id,
                    name = displayName,
                    typeLabel = typeLabel,
                    isBluetooth = isBluetooth,
                    isWired = isWired,
                    isSpeaker = isSpeaker,
                    priority = priority
                )
            )
        }

        val deduped = entries
            .sortedBy { it.priority }
            .distinctBy { "${it.name.lowercase()}|${it.typeLabel}" }

        val hasExplicitActive = selectedId != null && deduped.any { it.id == selectedId }
        val heuristicActiveId = if (!hasExplicitActive) {
            resolveHeuristicActiveId(
                audioManager = audioManager,
                devices = deduped.map {
                    AudioOutputDevice(
                        id = it.id,
                        name = it.name,
                        typeLabel = it.typeLabel,
                        isBluetooth = it.isBluetooth,
                        isWired = it.isWired,
                        isSpeaker = it.isSpeaker,
                        isActive = false,
                        deviceInfo = it.info
                    )
                }
            )
        } else {
            null
        }

        return deduped.map { entry ->
            val active = if (hasExplicitActive) {
                entry.id == selectedId
            } else {
                entry.id == heuristicActiveId
            }
            AudioOutputDevice(
                id = entry.id,
                name = entry.name,
                typeLabel = entry.typeLabel,
                isBluetooth = entry.isBluetooth,
                isWired = entry.isWired,
                isSpeaker = entry.isSpeaker,
                isActive = active,
                deviceInfo = entry.info
            )
        }.sortedWith(compareByDescending<AudioOutputDevice> { it.isActive }.thenBy {
            when {
                it.isBluetooth -> 0
                it.isWired -> 1
                !it.isSpeaker -> 2
                else -> 3
            }
        })
    }

    fun getActiveOutputName(context: Context): String {
        val devices = listOutputDevices(context)
        return devices.firstOrNull { it.isActive }?.name
            ?: devices.firstOrNull()?.name
            ?: "Phone Speaker"
    }

    private fun isInternalOrIgnoredType(type: Int, hasBuiltInSpeaker: Boolean): Boolean {
        return when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
            AudioDeviceInfo.TYPE_TELEPHONY,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_FM,
            AudioDeviceInfo.TYPE_FM_TUNER,
            AudioDeviceInfo.TYPE_TV_TUNER,
            AudioDeviceInfo.TYPE_BUS,
            AudioDeviceInfo.TYPE_REMOTE_SUBMIX,
            AudioDeviceInfo.TYPE_UNKNOWN -> true
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> hasBuiltInSpeaker
            else -> false
        }
    }

    private fun isBluetoothOutputType(type: Int): Boolean {
        if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && type == AudioDeviceInfo.TYPE_HEARING_AID) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER)
        ) {
            return true
        }
        return false
    }

    private fun isWiredOutputType(type: Int): Boolean {
        return when (type) {
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> true
            else -> false
        }
    }

    private fun resolveTypeLabel(
        type: Int,
        isBluetooth: Boolean,
        isWired: Boolean,
        isSpeaker: Boolean
    ): String {
        if (isBluetooth) return "Bluetooth"
        if (isSpeaker) return "Speaker"
        return when (type) {
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB Audio"
            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI / Display"
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && type == AudioDeviceInfo.TYPE_HDMI_EARC) {
                    "HDMI / Display"
                } else if (isWired) {
                    "Wired"
                } else {
                    "Audio Output"
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveHeuristicActiveId(
        audioManager: AudioManager,
        devices: List<AudioOutputDevice>
    ): Int? {
        if (devices.isEmpty()) return null
        val isBtA2dpOn = runCatching { audioManager.isBluetoothA2dpOn }.getOrDefault(false)
        if (isBtA2dpOn) {
            devices.firstOrNull { it.isBluetooth }?.let { return it.id }
        }
        val isWiredOn = runCatching { audioManager.isWiredHeadsetOn }.getOrDefault(false)
        if (isWiredOn) {
            devices.firstOrNull { it.isWired }?.let { return it.id }
        }
        return devices.firstOrNull { it.isWired }?.id
            ?: devices.firstOrNull { it.isBluetooth }?.id
            ?: devices.firstOrNull { it.isSpeaker }?.id
            ?: devices.firstOrNull()?.id
    }
}
