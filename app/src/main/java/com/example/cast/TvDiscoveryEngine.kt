package com.example.cast

import android.content.Context
import android.media.MediaRouter
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import android.util.Xml
import com.example.model.SmartTvDevice
import com.example.model.TvProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class TvDiscoveryEngine(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val tag = "TvDiscoveryEngine"
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val discoveredMap = ConcurrentHashMap<String, SmartTvDevice>()
    private val _discoveredDevices = MutableStateFlow<List<SmartTvDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<SmartTvDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val activeNsdListeners = mutableListOf<NsdManager.DiscoveryListener>()
    private val resolveQueue = Channel<NsdServiceInfo>(Channel.UNLIMITED)
    private var scanJob: Job? = null
    private var resolveWorkerJob: Job? = null
    private val resolveExecutor = Executors.newSingleThreadExecutor()

    private val serviceTypes = listOf(
        "_googlecast._tcp." to TvProtocol.CHROMECAST_MDNS,
        "_airplay._tcp." to TvProtocol.AIRPLAY_MDNS,
        "_dlna._tcp." to TvProtocol.DLNA_UPNP,
        "_rtsp._tcp." to TvProtocol.RTSP_LOW_LATENCY
    )

    fun startDiscovery() {
        stopDiscovery()
        _isScanning.value = true
        startResolveWorker()
        startNsdListeners()
        scanMediaRouterDisplays()

        scanJob = scope.launch(Dispatchers.IO) {
            runSsdpDiscovery()
            delay(6500)
            _isScanning.value = false
        }
    }

    fun stopDiscovery() {
        scanJob?.cancel()
        scanJob = null
        resolveWorkerJob?.cancel()
        resolveWorkerJob = null

        synchronized(activeNsdListeners) {
            activeNsdListeners.forEach { listener ->
                runCatching { nsdManager.stopServiceDiscovery(listener) }
            }
            activeNsdListeners.clear()
        }
        _isScanning.value = false
    }

    private fun startResolveWorker() {
        resolveWorkerJob = scope.launch(Dispatchers.IO) {
            for (serviceInfo in resolveQueue) {
                resolveSingleService(serviceInfo)
                delay(150)
            }
        }
    }

    private fun startNsdListeners() {
        for ((serviceType, protocol) in serviceTypes) {
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {
                    Log.d(tag, "NSD started: $regType")
                }

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    resolveQueue.trySend(serviceInfo)
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    val keyPrefix = "${protocol.name}_${serviceInfo.serviceName}"
                    discoveredMap.keys.filter { it.startsWith(keyPrefix) }.forEach {
                        discoveredMap.remove(it)
                    }
                    publishDevices()
                }

                override fun onDiscoveryStopped(serviceType: String) {}

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.w(tag, "NSD start failed ($serviceType): $errorCode")
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            }

            runCatching {
                nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
                synchronized(activeNsdListeners) { activeNsdListeners.add(listener) }
            }.onFailure {
                Log.w(tag, "Failed to start NSD for $serviceType: ${it.message}")
            }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun resolveSingleService(serviceInfo: NsdServiceInfo) {
        val protocol = when {
            serviceInfo.serviceType.contains("googlecast") -> TvProtocol.CHROMECAST_MDNS
            serviceInfo.serviceType.contains("airplay") -> TvProtocol.AIRPLAY_MDNS
            serviceInfo.serviceType.contains("dlna") -> TvProtocol.DLNA_UPNP
            serviceInfo.serviceType.contains("rtsp") -> TvProtocol.RTSP_LOW_LATENCY
            else -> TvProtocol.BROWSER_RECEIVER
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            var callbackRef: NsdManager.ServiceInfoCallback? = null
            val callback = object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {}
                override fun onServiceUpdated(resolved: NsdServiceInfo) {
                    val hostAddr = resolved.hostAddresses.firstOrNull()?.hostAddress ?: return
                    upsertResolvedNsdDevice(resolved.serviceName, hostAddr, resolved.port, protocol)
                    callbackRef?.let { runCatching { nsdManager.unregisterServiceInfoCallback(it) } }
                }
                override fun onServiceLost() {}
                override fun onServiceInfoCallbackUnregistered() {}
            }
            callbackRef = callback
            runCatching {
                nsdManager.registerServiceInfoCallback(serviceInfo, resolveExecutor, callback)
            }
        } else {
            val latch = kotlinx.coroutines.CompletableDeferred<Unit>()
            nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                    latch.complete(Unit)
                }

                override fun onServiceResolved(resolved: NsdServiceInfo) {
                    val hostAddr = resolved.host?.hostAddress
                    if (!hostAddr.isNullOrBlank()) {
                        upsertResolvedNsdDevice(resolved.serviceName, hostAddr, resolved.port, protocol)
                    }
                    latch.complete(Unit)
                }
            })
            runCatching {
                kotlinx.coroutines.withTimeoutOrNull(1800) { latch.await() }
            }
        }
    }

    private fun upsertResolvedNsdDevice(
        serviceName: String,
        ip: String,
        port: Int,
        protocol: TvProtocol
    ) {
        val id = "${protocol.name}_${ip}_$port"
        val cleanName = serviceName
            .substringBefore("._")
            .replace("-", " ")
            .ifBlank { "Smart TV ($ip)" }

        scope.launch {
            val rtt = NetworkUtils.measureTcpRttMs(ip, port)
            discoveredMap[id] = SmartTvDevice(
                id = id,
                name = cleanName,
                ipAddress = ip,
                port = port,
                protocol = protocol,
                manufacturer = when (protocol) {
                    TvProtocol.CHROMECAST_MDNS -> "Google Cast / Android TV"
                    TvProtocol.AIRPLAY_MDNS -> "AirPlay Smart Display"
                    TvProtocol.DLNA_UPNP -> "DLNA Media Renderer"
                    TvProtocol.RTSP_LOW_LATENCY -> "RTSP Receiver"
                    else -> "Network TV"
                },
                modelName = "${protocol.badge} • Port $port",
                isOnline = true,
                rttMs = rtt
            )
            publishDevices()
        }
    }

    private fun scanMediaRouterDisplays() {
        runCatching {
            val router = context.getSystemService(Context.MEDIA_ROUTER_SERVICE) as? MediaRouter
                ?: return
            val count = router.routeCount
            for (i in 0 until count) {
                val route = router.getRouteAt(i) ?: continue
                if (route.isEnabled && (route.supportedTypes and MediaRouter.ROUTE_TYPE_LIVE_VIDEO) != 0) {
                    val routeName = route.name?.toString().orEmpty()
                    if (routeName.isNotBlank() && !routeName.equals("Phone", ignoreCase = true)) {
                        val id = "WFD_${routeName.hashCode()}"
                        discoveredMap[id] = SmartTvDevice(
                            id = id,
                            name = routeName,
                            ipAddress = "Wi-Fi Direct",
                            port = 7236,
                            protocol = TvProtocol.MIRACAST_WFD,
                            manufacturer = "Miracast Sink",
                            modelName = route.description?.toString() ?: "Wireless Display",
                            isOnline = true,
                            rttMs = 12
                        )
                    }
                }
            }
            publishDevices()
        }
    }

    private suspend fun runSsdpDiscovery() = withContext(Dispatchers.IO) {
        val multicastLock = wifiManager?.createMulticastLock("mirracast_ssdp_lock")?.apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }
        val seenLocations = mutableSetOf<String>()
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket().apply {
                soTimeout = 1400
                broadcast = true
            }
            val searchTargets = listOf(
                "urn:schemas-upnp-org:device:MediaRenderer:1",
                "urn:dial-multiscreen-org:service:dial:1",
                "ssdp:all"
            )
            val group = InetAddress.getByName("239.255.255.250")
            for (st in searchTargets) {
                val payload = buildString {
                    append("M-SEARCH * HTTP/1.1\r\n")
                    append("HOST: 239.255.255.250:1900\r\n")
                    append("MAN: \"ssdp:discover\"\r\n")
                    append("MX: 2\r\n")
                    append("ST: $st\r\n\r\n")
                }.toByteArray(Charsets.UTF_8)
                val packet = DatagramPacket(payload, payload.size, group, 1900)
                runCatching { socket.send(packet) }
            }

            val buf = ByteArray(4096)
            val deadline = System.currentTimeMillis() + 4500L
            while (System.currentTimeMillis() < deadline) {
                val rx = DatagramPacket(buf, buf.size)
                val received = runCatching {
                    socket.receive(rx)
                    true
                }.getOrDefault(false)
                if (!received) continue

                val response = String(rx.data, 0, rx.length, Charsets.UTF_8)
                val location = extractHeader(response, "LOCATION") ?: continue
                if (!seenLocations.add(location)) continue

                scope.launch(Dispatchers.IO) {
                    parseUpnpDescription(location)
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "SSDP discovery error: ${e.message}")
        } finally {
            runCatching { socket?.close() }
            runCatching {
                if (multicastLock?.isHeld == true) multicastLock.release()
            }
        }
    }

    private fun extractHeader(raw: String, headerName: String): String? {
        return raw.lineSequence()
            .firstOrNull { it.startsWith("$headerName:", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()
    }

    private suspend fun parseUpnpDescription(locationUrl: String) {
        runCatching {
            val url = URL(locationUrl)
            val ip = url.host ?: return
            val port = if (url.port > 0) url.port else 80
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 1800
                readTimeout = 1800
            }
            val xmlText = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            var friendlyName = ""
            var manufacturer = ""
            var modelName = ""
            var currentServiceType = ""
            var avTransportUrl: String? = null
            var renderingUrl: String? = null
            var tagName = ""

            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xmlText))
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> tagName = parser.name
                    XmlPullParser.TEXT -> {
                        val txt = parser.text.trim()
                        if (txt.isNotEmpty()) {
                            when (tagName) {
                                "friendlyName" -> if (friendlyName.isEmpty()) friendlyName = txt
                                "manufacturer" -> if (manufacturer.isEmpty()) manufacturer = txt
                                "modelName" -> if (modelName.isEmpty()) modelName = txt
                                "serviceType" -> currentServiceType = txt
                                "controlURL" -> {
                                    val resolvedCtrl = resolveControlUrl(url, txt)
                                    if (currentServiceType.contains("AVTransport", ignoreCase = true)) {
                                        avTransportUrl = resolvedCtrl
                                    } else if (currentServiceType.contains("RenderingControl", ignoreCase = true)) {
                                        renderingUrl = resolvedCtrl
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> tagName = ""
                }
                event = parser.next()
            }

            if (avTransportUrl != null || friendlyName.isNotBlank()) {
                val id = "DLNA_${ip}_$port"
                val rtt = NetworkUtils.measureTcpRttMs(ip, port)
                discoveredMap[id] = SmartTvDevice(
                    id = id,
                    name = friendlyName.ifBlank { "Smart TV ($ip)" },
                    ipAddress = ip,
                    port = port,
                    protocol = TvProtocol.DLNA_UPNP,
                    manufacturer = manufacturer.ifBlank { "UPnP Renderer" },
                    modelName = modelName.ifBlank { "DLNA Media TV" },
                    avTransportControlUrl = avTransportUrl,
                    renderingControlUrl = renderingUrl,
                    isOnline = true,
                    rttMs = rtt
                )
                publishDevices()
            }
        }
    }

    private fun resolveControlUrl(baseUrl: URL, path: String): String {
        return if (path.startsWith("http://") || path.startsWith("https://")) {
            path
        } else {
            URL(baseUrl, path).toString()
        }
    }

    private fun publishDevices() {
        _discoveredDevices.value = discoveredMap.values.sortedBy { it.name.lowercase() }
    }
}
