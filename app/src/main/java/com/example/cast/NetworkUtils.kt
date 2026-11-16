package com.example.cast

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import com.example.model.HardwareCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

object NetworkUtils {

    fun probeHardwareCapabilities(context: Context): HardwareCapabilities {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        var avcName = "Hardware AVC"
        var maxAvcW = 1920
        var maxAvcH = 1080
        var lowLatencySupported = false

        var hevcName: String? = null

        for (info in codecList.codecInfos) {
            if (!info.isEncoder) continue
            val isHardware = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                info.isHardwareAccelerated
            } else {
                !info.name.contains("google", ignoreCase = true) &&
                    !info.name.contains("sw", ignoreCase = true)
            }

            for (type in info.supportedTypes) {
                if (type.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true)) {
                    val caps = runCatching { info.getCapabilitiesForType(type) }.getOrNull()
                    val videoCaps = caps?.videoCapabilities
                    if (videoCaps != null && (isHardware || avcName == "Hardware AVC")) {
                        avcName = info.name
                        maxAvcW = maxOf(maxAvcW, videoCaps.supportedWidths.upper)
                        maxAvcH = maxOf(maxAvcH, videoCaps.supportedHeights.upper)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            if (caps.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency)) {
                                lowLatencySupported = true
                            }
                        }
                    }
                } else if (type.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true)) {
                    if (isHardware || hevcName == null) {
                        hevcName = info.name
                    }
                }
            }
        }

        val ipAddress = resolveLocalIpv4Address(context)
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        val networkCaps = activeNetwork?.let { cm.getNetworkCapabilities(it) }

        val transportLabel = when {
            networkCaps == null -> "Offline"
            networkCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            networkCaps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            networkCaps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular (Use Wi-Fi for TV)"
            else -> "Local Network"
        }

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        @Suppress("DEPRECATION")
        val connInfo = runCatching { wifiManager?.connectionInfo }.getOrNull()
        val linkSpeed = connInfo?.linkSpeed?.takeIf { it > 0 }
        val freqMhz = connInfo?.frequency?.takeIf { it > 0 }
        val bandLabel = when {
            freqMhz == null -> null
            freqMhz in 2400..2500 -> "2.4 GHz"
            freqMhz in 4900..5900 -> "5 GHz"
            freqMhz > 5925 -> "6 GHz (Wi-Fi 6E)"
            else -> "${freqMhz} MHz"
        }

        return HardwareCapabilities(
            supportsHevc = hevcName != null,
            avcEncoderName = avcName,
            hevcEncoderName = hevcName,
            maxAvcWidth = maxAvcW,
            maxAvcHeight = maxAvcH,
            supportsLowLatencyFlag = lowLatencySupported,
            localIpAddress = ipAddress,
            networkTransport = transportLabel,
            linkSpeedMbps = linkSpeed,
            frequencyBandLabel = bandLabel
        )
    }

    fun resolveLocalIpv4Address(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        val linkProps = activeNetwork?.let { cm.getLinkProperties(it) }
        val linkIp = linkProps?.linkAddresses
            ?.map { it.address }
            ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress && it.isSiteLocalAddress }
            ?.hostAddress
        if (!linkIp.isNullOrBlank()) return linkIp

        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            val prioritized = interfaces.sortedByDescending { iface ->
                when {
                    iface.name.startsWith("wlan") -> 3
                    iface.name.startsWith("eth") -> 2
                    else -> 1
                }
            }
            for (iface in prioritized) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
                        return@runCatching addr.hostAddress ?: "0.0.0.0"
                    }
                }
            }
            "127.0.0.1"
        }.getOrDefault("127.0.0.1")
    }

    suspend fun measureTcpRttMs(ip: String, port: Int, timeoutMs: Int = 900): Int? =
        withContext(Dispatchers.IO) {
            runCatching {
                val start = System.nanoTime()
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), timeoutMs)
                }
                val elapsedMs = ((System.nanoTime() - start) / 1_000_000L).toInt()
                maxOf(1, elapsedMs)
            }.getOrNull()
        }
}
