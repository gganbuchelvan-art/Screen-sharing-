package com.example.model

enum class TvProtocol(val displayName: String, val badge: String) {
    DLNA_UPNP("DLNA / UPnP AVTransport", "DLNA"),
    CHROMECAST_MDNS("Google Cast mDNS", "CAST"),
    AIRPLAY_MDNS("AirPlay / Mirror", "AIRPLAY"),
    MIRACAST_WFD("Miracast Wi-Fi Display", "WFD"),
    BROWSER_RECEIVER("Low-Latency Web Receiver", "WEB-TV"),
    RTSP_LOW_LATENCY("RTSP / H.264 Stream", "RTSP")
}

data class SmartTvDevice(
    val id: String,
    val name: String,
    val ipAddress: String,
    val port: Int,
    val protocol: TvProtocol,
    val manufacturer: String = "Smart TV",
    val modelName: String = "Network Renderer",
    val avTransportControlUrl: String? = null,
    val renderingControlUrl: String? = null,
    val isOnline: Boolean = true,
    val rttMs: Int? = null,
    val isManual: Boolean = false
)

enum class VideoResolution(
    val label: String,
    val shortBadge: String,
    val width: Int,
    val height: Int
) {
    HD_720P("720p Fast", "720p", 1280, 720),
    FHD_1080P("1080p Full HD", "1080p", 1920, 1080),
    QHD_1440P("1440p QHD", "1440p", 2560, 1440),
    UHD_4K("4K UHD 2160p", "4K UHD", 3840, 2160)
}

enum class VideoCodec(val label: String, val mimeType: String, val shortTag: String) {
    H264_AVC("H.264 / AVC (Universal)", "video/avc", "H.264"),
    H265_HEVC("H.265 / HEVC (4K High-Efficiency)", "video/hevc", "HEVC")
}

data class StreamProfile(
    val resolution: VideoResolution = VideoResolution.FHD_1080P,
    val fps: Int = 60,
    val bitrateMbps: Int = 14,
    val codec: VideoCodec = VideoCodec.H264_AVC,
    val lowLatencyMode: Boolean = true,
    val audioMirroring: Boolean = true,
    val adaptiveBitrate: Boolean = true
)

data class CastMediaItem(
    val uriString: String,
    val title: String,
    val mimeType: String,
    val isVideo: Boolean,
    val sizeBytes: Long = 0L
)

enum class CastSessionPhase {
    IDLE,
    REQUESTING_PERMISSION,
    MIRRORING_LIVE,
    MEDIA_CASTING,
    ERROR
}

data class LiveTelemetry(
    val phase: CastSessionPhase = CastSessionPhase.IDLE,
    val activeResolution: VideoResolution = VideoResolution.FHD_1080P,
    val currentFps: Int = 0,
    val targetFps: Int = 60,
    val actualBitrateMbps: Float = 0f,
    val encodedFrames: Long = 0L,
    val droppedFrames: Long = 0L,
    val estimatedLatencyMs: Int = 0,
    val connectedClients: Int = 0,
    val receiverUrl: String = "",
    val h264StreamUrl: String = "",
    val encoderName: String = "Hardware AVC",
    val errorMessage: String? = null
)

data class HardwareCapabilities(
    val supportsHevc: Boolean = false,
    val avcEncoderName: String = "System Default",
    val hevcEncoderName: String? = null,
    val maxAvcWidth: Int = 1920,
    val maxAvcHeight: Int = 1080,
    val supportsLowLatencyFlag: Boolean = false,
    val localIpAddress: String = "0.0.0.0",
    val networkTransport: String = "Wi-Fi",
    val linkSpeedMbps: Int? = null,
    val frequencyBandLabel: String? = null
)

data class MediaPlaybackState(
    val isPlaying: Boolean = false,
    val volumePercent: Int = 50,
    val isMuted: Boolean = false,
    val activeMediaUrl: String? = null,
    val lastControlStatus: String? = null
)
