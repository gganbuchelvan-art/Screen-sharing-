package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.SmartTvDevice
import com.example.model.StreamProfile
import com.example.model.TvProtocol
import com.example.model.VideoCodec
import com.example.model.VideoResolution
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.castDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "mirracast_studio_prefs"
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val RESOLUTION = stringPreferencesKey("resolution")
        val FPS = intPreferencesKey("fps")
        val BITRATE_MBPS = intPreferencesKey("bitrate_mbps")
        val CODEC = stringPreferencesKey("codec")
        val LOW_LATENCY = booleanPreferencesKey("low_latency")
        val AUDIO_MIRROR = booleanPreferencesKey("audio_mirror")
        val ADAPTIVE_BITRATE = booleanPreferencesKey("adaptive_bitrate")
        val MANUAL_DEVICES = stringSetPreferencesKey("manual_devices")
        val LAST_SELECTED_TV_ID = stringPreferencesKey("last_selected_tv_id")
    }

    val streamProfileFlow: Flow<StreamProfile> = context.castDataStore.data.map { prefs ->
        val res = prefs[Keys.RESOLUTION]?.let { name ->
            VideoResolution.entries.firstOrNull { it.name == name }
        } ?: VideoResolution.FHD_1080P

        val codec = prefs[Keys.CODEC]?.let { name ->
            VideoCodec.entries.firstOrNull { it.name == name }
        } ?: VideoCodec.H264_AVC

        StreamProfile(
            resolution = res,
            fps = prefs[Keys.FPS] ?: 60,
            bitrateMbps = prefs[Keys.BITRATE_MBPS] ?: 14,
            codec = codec,
            lowLatencyMode = prefs[Keys.LOW_LATENCY] ?: true,
            audioMirroring = prefs[Keys.AUDIO_MIRROR] ?: true,
            adaptiveBitrate = prefs[Keys.ADAPTIVE_BITRATE] ?: true
        )
    }

    val manualDevicesFlow: Flow<List<SmartTvDevice>> = context.castDataStore.data.map { prefs ->
        val rawSet = prefs[Keys.MANUAL_DEVICES].orEmpty()
        rawSet.mapNotNull { deserializeDevice(it) }.sortedBy { it.name }
    }

    val lastSelectedTvIdFlow: Flow<String?> = context.castDataStore.data.map { prefs ->
        prefs[Keys.LAST_SELECTED_TV_ID]
    }

    suspend fun saveStreamProfile(profile: StreamProfile) {
        context.castDataStore.edit { prefs ->
            prefs[Keys.RESOLUTION] = profile.resolution.name
            prefs[Keys.FPS] = profile.fps
            prefs[Keys.BITRATE_MBPS] = profile.bitrateMbps
            prefs[Keys.CODEC] = profile.codec.name
            prefs[Keys.LOW_LATENCY] = profile.lowLatencyMode
            prefs[Keys.AUDIO_MIRROR] = profile.audioMirroring
            prefs[Keys.ADAPTIVE_BITRATE] = profile.adaptiveBitrate
        }
    }

    suspend fun addManualDevice(device: SmartTvDevice) {
        context.castDataStore.edit { prefs ->
            val current = prefs[Keys.MANUAL_DEVICES].orEmpty().toMutableSet()
            current.removeAll { it.startsWith("${device.id}|") }
            current.add(serializeDevice(device))
            prefs[Keys.MANUAL_DEVICES] = current
        }
    }

    suspend fun removeManualDevice(deviceId: String) {
        context.castDataStore.edit { prefs ->
            val current = prefs[Keys.MANUAL_DEVICES].orEmpty().toMutableSet()
            current.removeAll { it.startsWith("${deviceId}|") }
            prefs[Keys.MANUAL_DEVICES] = current
        }
    }

    suspend fun saveLastSelectedTvId(deviceId: String?) {
        context.castDataStore.edit { prefs ->
            if (deviceId == null) {
                prefs.remove(Keys.LAST_SELECTED_TV_ID)
            } else {
                prefs[Keys.LAST_SELECTED_TV_ID] = deviceId
            }
        }
    }

    private fun serializeDevice(d: SmartTvDevice): String {
        val safeName = d.name.replace("|", " ").trim()
        val safeModel = d.modelName.replace("|", " ").trim()
        return listOf(d.id, safeName, d.ipAddress, d.port.toString(), d.protocol.name, safeModel)
            .joinToString("|")
    }

    private fun deserializeDevice(raw: String): SmartTvDevice? {
        val parts = raw.split("|")
        if (parts.size < 5) return null
        val protocol = TvProtocol.entries.firstOrNull { it.name == parts[4] }
            ?: TvProtocol.BROWSER_RECEIVER
        return SmartTvDevice(
            id = parts[0],
            name = parts[1],
            ipAddress = parts[2],
            port = parts[3].toIntOrNull() ?: 8080,
            protocol = protocol,
            manufacturer = "Paired Manually",
            modelName = parts.getOrNull(5) ?: "Custom Receiver",
            isOnline = true,
            isManual = true
        )
    }
}
