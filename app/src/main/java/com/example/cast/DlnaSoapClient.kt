package com.example.cast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object DlnaSoapClient {

    sealed class SoapResult {
        data object Success : SoapResult()
        data class Failure(val reason: String) : SoapResult()
    }

    suspend fun setAvTransportUriAndPlay(
        controlUrl: String,
        mediaUrl: String,
        title: String,
        mimeType: String,
        isVideo: Boolean
    ): SoapResult = withContext(Dispatchers.IO) {
        val protocolInfo = "http-get:*:$mimeType:*"
        val upnpClass = if (isVideo) "object.item.videoItem" else "object.item.imageItem.photo"
        val escapedTitle = escapeXml(title)
        val escapedUrl = escapeXml(mediaUrl)

        val didl = """
            <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"
                       xmlns:dc="http://purl.org/dc/elements/1.1/"
                       xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
              <item id="0" parentID="-1" restricted="1">
                <dc:title>$escapedTitle</dc:title>
                <upnp:class>$upnpClass</upnp:class>
                <res protocolInfo="$protocolInfo">$escapedUrl</res>
              </item>
            </DIDL-Lite>
        """.trimIndent()

        val setUriBody = """
            <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
              <InstanceID>0</InstanceID>
              <CurrentURI>$escapedUrl</CurrentURI>
              <CurrentURIMetaData>${escapeXml(didl)}</CurrentURIMetaData>
            </u:SetAVTransportURI>
        """.trimIndent()

        val setResult = sendSoapAction(
            controlUrl = controlUrl,
            serviceType = "urn:schemas-upnp-org:service:AVTransport:1",
            action = "SetAVTransportURI",
            bodyXml = setUriBody
        )
        if (setResult is SoapResult.Failure) return@withContext setResult

        play(controlUrl)
    }

    suspend fun play(controlUrl: String): SoapResult = sendAvTransportCommand(
        controlUrl = controlUrl,
        action = "Play",
        innerXml = "<InstanceID>0</InstanceID><Speed>1</Speed>"
    )

    suspend fun pause(controlUrl: String): SoapResult = sendAvTransportCommand(
        controlUrl = controlUrl,
        action = "Pause",
        innerXml = "<InstanceID>0</InstanceID>"
    )

    suspend fun stop(controlUrl: String): SoapResult = sendAvTransportCommand(
        controlUrl = controlUrl,
        action = "Stop",
        innerXml = "<InstanceID>0</InstanceID>"
    )

    suspend fun seek(controlUrl: String, targetHhMmSs: String): SoapResult = sendAvTransportCommand(
        controlUrl = controlUrl,
        action = "Seek",
        innerXml = "<InstanceID>0</InstanceID><Unit>REL_TIME</Unit><Target>$targetHhMmSs</Target>"
    )

    suspend fun setVolume(renderingControlUrl: String, volumePercent: Int): SoapResult {
        val clamped = volumePercent.coerceIn(0, 100)
        val body = """
            <u:SetVolume xmlns:u="urn:schemas-upnp-org:service:RenderingControl:1">
              <InstanceID>0</InstanceID>
              <Channel>Master</Channel>
              <DesiredVolume>$clamped</DesiredVolume>
            </u:SetVolume>
        """.trimIndent()
        return sendSoapAction(
            controlUrl = renderingControlUrl,
            serviceType = "urn:schemas-upnp-org:service:RenderingControl:1",
            action = "SetVolume",
            bodyXml = body
        )
    }

    suspend fun setMute(renderingControlUrl: String, mute: Boolean): SoapResult {
        val body = """
            <u:SetMute xmlns:u="urn:schemas-upnp-org:service:RenderingControl:1">
              <InstanceID>0</InstanceID>
              <Channel>Master</Channel>
              <DesiredMute>${if (mute) 1 else 0}</DesiredMute>
            </u:SetMute>
        """.trimIndent()
        return sendSoapAction(
            controlUrl = renderingControlUrl,
            serviceType = "urn:schemas-upnp-org:service:RenderingControl:1",
            action = "SetMute",
            bodyXml = body
        )
    }

    private suspend fun sendAvTransportCommand(
        controlUrl: String,
        action: String,
        innerXml: String
    ): SoapResult {
        val body = """
            <u:$action xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
              $innerXml
            </u:$action>
        """.trimIndent()
        return sendSoapAction(
            controlUrl = controlUrl,
            serviceType = "urn:schemas-upnp-org:service:AVTransport:1",
            action = action,
            bodyXml = body
        )
    }

    private suspend fun sendSoapAction(
        controlUrl: String,
        serviceType: String,
        action: String,
        bodyXml: String
    ): SoapResult = withContext(Dispatchers.IO) {
        runCatching {
            val envelope = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                            s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                  <s:Body>
                    $bodyXml
                  </s:Body>
                </s:Envelope>
            """.trimIndent().toByteArray(Charsets.UTF_8)

            val conn = (URL(controlUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 3000
                readTimeout = 4000
                doOutput = true
                setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
                setRequestProperty("SOAPACTION", "\"$serviceType#$action\"")
                setRequestProperty("Content-Length", envelope.size.toString())
            }
            conn.outputStream.use { it.write(envelope) }
            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..299) {
                SoapResult.Success
            } else {
                SoapResult.Failure("TV returned HTTP $code for $action")
            }
        }.getOrElse { ex ->
            SoapResult.Failure(ex.localizedMessage ?: "Network error contacting TV")
        }
    }

    private fun escapeXml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
