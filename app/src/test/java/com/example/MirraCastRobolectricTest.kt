package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.cast.LocalCastServer
import com.example.data.PreferencesRepository
import com.example.model.SmartTvDevice
import com.example.model.StreamProfile
import com.example.model.TvProtocol
import com.example.model.VideoCodec
import com.example.model.VideoResolution
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.HttpURLConnection
import java.net.URL

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MirraCastRobolectricTest {

    @Test
    fun `local cast server serves html receiver and status json`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val server = LocalCastServer(context, scope)
        try {
            val port = server.start(preferredPort = 0)
            assertTrue(port > 0)

            val htmlConn = URL("http://127.0.0.1:$port/").openConnection() as HttpURLConnection
            assertEquals(200, htmlConn.responseCode)
            val html = htmlConn.inputStream.bufferedReader().use { it.readText() }
            assertTrue(html.contains("MirraCast TV Receiver"))
            htmlConn.disconnect()

            server.isMirroringActive = true
            server.currentFpsMetric = 60
            server.currentResolutionLabel = "4K UHD"

            val statusConn = URL("http://127.0.0.1:$port/status.json").openConnection() as HttpURLConnection
            assertEquals(200, statusConn.responseCode)
            val json = statusConn.inputStream.bufferedReader().use { it.readText() }
            assertTrue(json.contains("\"mirroring\":true"))
            assertTrue(json.contains("\"fps\":60"))
            assertTrue(json.contains("\"resolution\":\"4K UHD\""))
            statusConn.disconnect()
        } finally {
            server.stop()
            scope.cancel()
        }
    }

    @Test
    fun `preferences repository persists stream profile and manual tv`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PreferencesRepository(context)
        val expected = StreamProfile(
            resolution = VideoResolution.UHD_4K,
            fps = 60,
            bitrateMbps = 24,
            codec = VideoCodec.H265_HEVC,
            lowLatencyMode = true,
            audioMirroring = false,
            adaptiveBitrate = true
        )
        repo.saveStreamProfile(expected)
        val loaded = repo.streamProfileFlow.first()
        assertEquals(expected, loaded)

        val manualTv = SmartTvDevice(
            id = "MANUAL_192.168.1.99_8080",
            name = "Studio OLED",
            ipAddress = "192.168.1.99",
            port = 8080,
            protocol = TvProtocol.DLNA_UPNP,
            isManual = true
        )
        repo.addManualDevice(manualTv)
        val savedList = repo.manualDevicesFlow.first()
        assertTrue(savedList.any { it.ipAddress == "192.168.1.99" && it.name == "Studio OLED" })
    }
}
