package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cast.CastSessionBus
import com.example.cast.DlnaSoapClient
import com.example.cast.NetworkUtils
import com.example.cast.ScreenCastService
import com.example.cast.TvDiscoveryEngine
import com.example.data.PreferencesRepository
import com.example.model.CastMediaItem
import com.example.model.CastSessionPhase
import com.example.model.HardwareCapabilities
import com.example.model.LiveTelemetry
import com.example.model.MediaPlaybackState
import com.example.model.SmartTvDevice
import com.example.model.StreamProfile
import com.example.model.TvProtocol
import com.example.model.VideoCodec
import com.example.model.VideoResolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext: Context = application.applicationContext
    private val prefsRepo = PreferencesRepository(appContext)
    private val discoveryEngine = TvDiscoveryEngine(appContext, viewModelScope)

    private val _hardwareCapabilities = MutableStateFlow(HardwareCapabilities())
    val hardwareCapabilities: StateFlow<HardwareCapabilities> = _hardwareCapabilities.asStateFlow()

    val streamProfile: StateFlow<StreamProfile> = prefsRepo.streamProfileFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, StreamProfile())

    val telemetry: StateFlow<LiveTelemetry> = CastSessionBus.telemetry

    val activeMedia: StateFlow<CastMediaItem?> = CastSessionBus.activeMedia

    private val _selectedTvId = MutableStateFlow<String?>(null)

    val devices: StateFlow<List<SmartTvDevice>> = combine(
        discoveryEngine.discoveredDevices,
        prefsRepo.manualDevicesFlow
    ) { discovered, manual ->
        (discovered + manual).distinctBy { "${it.ipAddress}:${it.port}" }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selectedDevice: StateFlow<SmartTvDevice?> = combine(
        devices,
        _selectedTvId,
        prefsRepo.lastSelectedTvIdFlow
    ) { allDevices, activeId, savedId ->
        val targetId = activeId ?: savedId
        allDevices.firstOrNull { it.id == targetId } ?: allDevices.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isScanning: StateFlow<Boolean> = discoveryEngine.isScanning

    private val _mediaPlayback = MutableStateFlow(MediaPlaybackState())
    val mediaPlayback: StateFlow<MediaPlaybackState> = _mediaPlayback.asStateFlow()

    private val _uiMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val uiMessages: SharedFlow<String> = _uiMessages.asSharedFlow()

    private val _protocolFilter = MutableStateFlow<TvProtocol?>(null)
    val protocolFilter: StateFlow<TvProtocol?> = _protocolFilter.asStateFlow()

    init {
        val server = CastSessionBus.ensureServer(appContext)
        refreshDiagnostics(server.port)

        viewModelScope.launch {
            prefsRepo.streamProfileFlow.collect { profile ->
                CastSessionBus.updateProfile(profile)
            }
        }
        startNetworkScan()
    }

    fun refreshDiagnostics(serverPort: Int = CastSessionBus.ensureServer(appContext).port) {
        viewModelScope.launch(Dispatchers.IO) {
            val caps = NetworkUtils.probeHardwareCapabilities(appContext)
            _hardwareCapabilities.value = caps
            val current = CastSessionBus.telemetry.value
            if (current.receiverUrl.isBlank() || current.phase == CastSessionPhase.IDLE) {
                CastSessionBus.updateTelemetry(
                    current.copy(
                        receiverUrl = "http://${caps.localIpAddress}:$serverPort/",
                        h264StreamUrl = "http://${caps.localIpAddress}:$serverPort/stream.h264"
                    )
                )
            }
        }
    }

    fun startNetworkScan() {
        discoveryEngine.startDiscovery()
        refreshDiagnostics()
    }

    fun setProtocolFilter(protocol: TvProtocol?) {
        _protocolFilter.value = protocol
    }

    fun selectDevice(device: SmartTvDevice) {
        _selectedTvId.value = device.id
        viewModelScope.launch {
            prefsRepo.saveLastSelectedTvId(device.id)
            _uiMessages.emit("Target TV set to ${device.name}")
        }
    }

    fun addManualDevice(
        name: String,
        ipAddress: String,
        port: Int,
        protocol: TvProtocol
    ) {
        val cleanIp = ipAddress.trim()
        val cleanName = name.trim().ifBlank { "Smart TV ($cleanIp)" }
        if (cleanIp.isBlank()) {
            _uiMessages.tryEmit("Enter a valid TV IP address.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val rtt = NetworkUtils.measureTcpRttMs(cleanIp, port)
            val newDevice = SmartTvDevice(
                id = "MANUAL_${cleanIp}_$port",
                name = cleanName,
                ipAddress = cleanIp,
                port = port,
                protocol = protocol,
                manufacturer = "Paired Manually",
                modelName = "${protocol.badge} • Port $port",
                isOnline = rtt != null || protocol == TvProtocol.BROWSER_RECEIVER,
                rttMs = rtt,
                isManual = true
            )
            prefsRepo.addManualDevice(newDevice)
            _selectedTvId.value = newDevice.id
            prefsRepo.saveLastSelectedTvId(newDevice.id)
            _uiMessages.emit("Paired $cleanName ($cleanIp)")
        }
    }

    fun removeManualDevice(deviceId: String) {
        viewModelScope.launch {
            prefsRepo.removeManualDevice(deviceId)
            if (_selectedTvId.value == deviceId) {
                _selectedTvId.value = null
            }
            _uiMessages.emit("Removed paired TV")
        }
    }

    fun startScreenMirroring(resultCode: Int, data: Intent) {
        CastSessionBus.updateProfile(streamProfile.value)
        val serviceIntent = Intent(appContext, ScreenCastService::class.java).apply {
            action = ScreenCastService.ACTION_START
            putExtra(ScreenCastService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCastService.EXTRA_RESULT_DATA, data)
        }
        ContextCompat.startForegroundService(appContext, serviceIntent)
    }

    fun stopScreenMirroring() {
        val stopIntent = Intent(appContext, ScreenCastService::class.java).apply {
            action = ScreenCastService.ACTION_STOP
        }
        appContext.startService(stopIntent)
    }

    fun castPickedMedia(uri: Uri, isVideo: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val resolver = appContext.contentResolver
            val mimeType = resolver.getType(uri)
                ?: if (isVideo) "video/mp4" else "image/jpeg"

            var displayName = if (isVideo) "Local Video" else "Local Photo"
            var fileSize = 0L
            runCatching {
                resolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIdx >= 0) displayName = cursor.getString(nameIdx) ?: displayName
                        if (sizeIdx >= 0) fileSize = cursor.getLong(sizeIdx)
                    }
                }
            }

            val item = CastMediaItem(
                uriString = uri.toString(),
                title = displayName,
                mimeType = mimeType,
                isVideo = isVideo,
                sizeBytes = fileSize
            )
            val mediaUrl = CastSessionBus.setActiveMedia(appContext, item)
            _mediaPlayback.value = _mediaPlayback.value.copy(
                isPlaying = true,
                activeMediaUrl = mediaUrl
            )

            val targetTv = selectedDevice.value
            val dlnaCtrl = targetTv?.avTransportControlUrl
            if (dlnaCtrl != null && mediaUrl != null) {
                val res = DlnaSoapClient.setAvTransportUriAndPlay(
                    controlUrl = dlnaCtrl,
                    mediaUrl = mediaUrl,
                    title = displayName,
                    mimeType = mimeType,
                    isVideo = isVideo
                )
                when (res) {
                    is DlnaSoapClient.SoapResult.Success ->
                        _uiMessages.emit("Streaming \"$displayName\" to ${targetTv.name}")
                    is DlnaSoapClient.SoapResult.Failure ->
                        _uiMessages.emit("Ready at TV Receiver (${res.reason})")
                }
            } else {
                _uiMessages.emit("Media ready on TV Receiver URL: \"${displayName}\"")
            }
        }
    }

    fun clearActiveMedia() {
        viewModelScope.launch(Dispatchers.IO) {
            val ctrl = selectedDevice.value?.avTransportControlUrl
            if (ctrl != null) {
                DlnaSoapClient.stop(ctrl)
            }
            CastSessionBus.setActiveMedia(appContext, null)
            _mediaPlayback.value = MediaPlaybackState()
        }
    }

    fun toggleMediaPlayPause() {
        val currentlyPlaying = _mediaPlayback.value.isPlaying
        val ctrl = selectedDevice.value?.avTransportControlUrl
        viewModelScope.launch(Dispatchers.IO) {
            if (ctrl != null) {
                if (currentlyPlaying) DlnaSoapClient.pause(ctrl) else DlnaSoapClient.play(ctrl)
            }
            _mediaPlayback.value = _mediaPlayback.value.copy(isPlaying = !currentlyPlaying)
        }
    }

    fun setTvVolume(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        _mediaPlayback.value = _mediaPlayback.value.copy(volumePercent = clamped)
        val renderUrl = selectedDevice.value?.renderingControlUrl ?: return
        viewModelScope.launch(Dispatchers.IO) {
            DlnaSoapClient.setVolume(renderUrl, clamped)
        }
    }

    fun updateResolution(res: VideoResolution) {
        val updated = streamProfile.value.copy(resolution = res)
        saveProfile(updated)
    }

    fun updateFps(fps: Int) {
        saveProfile(streamProfile.value.copy(fps = fps))
    }

    fun updateBitrate(mbps: Int) {
        saveProfile(streamProfile.value.copy(bitrateMbps = mbps))
    }

    fun updateCodec(codec: VideoCodec) {
        saveProfile(streamProfile.value.copy(codec = codec))
    }

    fun updateLowLatency(enabled: Boolean) {
        saveProfile(streamProfile.value.copy(lowLatencyMode = enabled))
    }

    fun updateAudioMirroring(enabled: Boolean) {
        saveProfile(streamProfile.value.copy(audioMirroring = enabled))
    }

    fun updateAdaptiveBitrate(enabled: Boolean) {
        saveProfile(streamProfile.value.copy(adaptiveBitrate = enabled))
    }

    private fun saveProfile(profile: StreamProfile) {
        CastSessionBus.updateProfile(profile)
        viewModelScope.launch {
            prefsRepo.saveStreamProfile(profile)
        }
    }

    override fun onCleared() {
        discoveryEngine.stopDiscovery()
        super.onCleared()
    }
}
