package com.example.cast

import android.content.Context
import com.example.model.CastMediaItem
import com.example.model.CastSessionPhase
import com.example.model.LiveTelemetry
import com.example.model.StreamProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CastSessionBus {
    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var serverInstance: LocalCastServer? = null

    private val _telemetry = MutableStateFlow(LiveTelemetry())
    val telemetry: StateFlow<LiveTelemetry> = _telemetry.asStateFlow()

    private val _activeProfile = MutableStateFlow(StreamProfile())
    val activeProfile: StateFlow<StreamProfile> = _activeProfile.asStateFlow()

    private val _activeMedia = MutableStateFlow<CastMediaItem?>(null)
    val activeMedia: StateFlow<CastMediaItem?> = _activeMedia.asStateFlow()

    fun ensureServer(context: Context): LocalCastServer {
        val existing = serverInstance
        if (existing != null) {
            existing.start()
            return existing
        }
        return synchronized(this) {
            serverInstance ?: LocalCastServer(context.applicationContext, serverScope).also {
                it.start()
                serverInstance = it
            }
        }
    }

    fun updateProfile(profile: StreamProfile) {
        _activeProfile.value = profile
    }

    fun updateTelemetry(newTelemetry: LiveTelemetry) {
        _telemetry.value = newTelemetry
    }

    fun setActiveMedia(context: Context, mediaItem: CastMediaItem?): String? {
        val server = ensureServer(context)
        server.activeMediaItem = mediaItem
        _activeMedia.value = mediaItem
        val ip = NetworkUtils.resolveLocalIpv4Address(context)
        return if (mediaItem != null) {
            val current = _telemetry.value
            if (current.phase != CastSessionPhase.MIRRORING_LIVE) {
                _telemetry.value = current.copy(
                    phase = CastSessionPhase.MEDIA_CASTING,
                    receiverUrl = "http://$ip:${server.port}/",
                    connectedClients = server.connectedClients,
                    errorMessage = null
                )
            }
            "http://$ip:${server.port}/media"
        } else {
            if (_telemetry.value.phase == CastSessionPhase.MEDIA_CASTING) {
                _telemetry.value = LiveTelemetry(
                    receiverUrl = "http://$ip:${server.port}/"
                )
            }
            null
        }
    }
}
