package com.example.cast

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.model.CastMediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class LocalCastServer(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val tag = "LocalCastServer"
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    val port: Int
        get() = serverSocket?.localPort ?: 8899

    private val clientCounter = AtomicInteger(0)
    val connectedClients: Int
        get() = clientCounter.get()

    private val frameLock = Object()
    @Volatile
    private var latestJpegFrame: ByteArray? = null
    private val frameSeq = AtomicLong(0L)

    @Volatile
    private var codecConfigPacket: ByteArray? = null
    private val h264Subscribers = CopyOnWriteArrayList<H264ClientQueue>()

    @Volatile
    var isMirroringActive: Boolean = false

    @Volatile
    var activeMediaItem: CastMediaItem? = null

    @Volatile
    var currentFpsMetric: Int = 0

    @Volatile
    var currentResolutionLabel: String = "1080p"

    class H264ClientQueue {
        val queue = java.util.concurrent.LinkedBlockingDeque<ByteArray>(30)
        fun offerDropOldest(data: ByteArray) {
            if (!queue.offer(data)) {
                queue.pollFirst()
                queue.offer(data)
            }
        }
    }

    fun start(preferredPort: Int = 8899): Int {
        if (serverSocket != null && serverSocket?.isClosed == false) {
            return port
        }
        val socket = runCatching { ServerSocket(preferredPort) }
            .getOrElse { ServerSocket(0) }
        serverSocket = socket

        acceptJob = scope.launch(Dispatchers.IO) {
            while (isActive && !socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                launch(Dispatchers.IO) {
                    handleClient(client)
                }
            }
        }
        Log.i(tag, "LocalCastServer listening on port ${socket.localPort}")
        return socket.localPort
    }

    fun stop() {
        isMirroringActive = false
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        h264Subscribers.clear()
        latestJpegFrame = null
        codecConfigPacket = null
        clientCounter.set(0)
    }

    fun publishJpegFrame(jpegBytes: ByteArray) {
        synchronized(frameLock) {
            latestJpegFrame = jpegBytes
            frameSeq.incrementAndGet()
            frameLock.notifyAll()
        }
    }

    fun publishH264Nal(data: ByteArray, isCodecConfig: Boolean) {
        if (isCodecConfig) {
            codecConfigPacket = data
        }
        for (sub in h264Subscribers) {
            sub.offerDropOldest(data)
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 10_000
            socket.tcpNoDelay = true
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            val path = parts.getOrNull(1)?.substringBefore("?") ?: "/"

            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) break
                val idx = line.indexOf(':')
                if (idx > 0) {
                    headers[line.substring(0, idx).trim().lowercase()] =
                        line.substring(idx + 1).trim()
                }
            }

            val out = BufferedOutputStream(socket.getOutputStream(), 65536)
            when (path) {
                "/" -> serveReceiverHtml(out)
                "/status.json" -> serveStatusJson(out)
                "/stream.mjpeg" -> serveMjpegStream(socket, out)
                "/stream.h264" -> serveH264Stream(socket, out)
                "/media" -> serveActiveMedia(out, headers["range"])
                else -> serveNotFound(out)
            }
        } catch (e: Exception) {
            Log.d(tag, "Client disconnected: ${e.message}")
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun serveReceiverHtml(out: OutputStream) {
        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width,initial-scale=1" />
              <title>MirraCast TV Receiver</title>
              <style>
                :root { color-scheme: dark; }
                body {
                  margin: 0; background: #090B10; color: #F3F6FC;
                  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                  display: flex; flex-direction: column; height: 100vh; overflow: hidden;
                }
                header {
                  display: flex; align-items: center; justify-content: space-between;
                  padding: 12px 20px; background: rgba(16,20,30,0.92);
                  border-bottom: 1px solid #222B3E; z-index: 10;
                }
                .brand { display: flex; align-items: center; gap: 10px; font-weight: 700; letter-spacing: 0.3px; }
                .dot { width: 10px; height: 10px; border-radius: 50%; background: #00E5FF; box-shadow: 0 0 12px #00E5FF; }
                .hud { display: flex; align-items: center; gap: 14px; font-size: 13px; color: #9AA7C0; }
                .pill { background: #171D2B; border: 1px solid #2A344B; padding: 5px 10px; border-radius: 999px; color: #00E5FF; }
                button {
                  background: #00E5FF; color: #002026; border: none; padding: 7px 14px;
                  border-radius: 999px; font-weight: 700; cursor: pointer;
                }
                main {
                  flex: 1; display: flex; align-items: center; justify-content: center;
                  position: relative; background: radial-gradient(circle at center, #11192A 0%, #090B10 75%);
                }
                #mirrorView, #videoView, #photoView {
                  max-width: 100%; max-height: 100%; object-fit: contain; display: none;
                }
                #standby { text-align: center; padding: 32px; color: #9AA7C0; }
                #standby h2 { color: #F3F6FC; margin-bottom: 8px; }
              </style>
            </head>
            <body>
              <header>
                <div class="brand"><span class="dot"></span><span>MirraCast Low-Latency TV Receiver</span></div>
                <div class="hud">
                  <span id="statusPill" class="pill">Ready</span>
                  <button onclick="goFullscreen()">Fullscreen</button>
                </div>
              </header>
              <main id="stage">
                <div id="standby">
                  <h2>Connected to MirraCast</h2>
                  <p>Start Screen Mirroring or pick a Video / Photo on your phone to stream here in HD.</p>
                </div>
                <img id="mirrorView" alt="Live Mirrored Screen" />
                <video id="videoView" controls autoplay playsinline></video>
                <img id="photoView" alt="Casted Photo" />
              </main>
              <script>
                let currentMode = 'idle';
                let currentMedia = '';
                function goFullscreen() {
                  const el = document.getElementById('stage');
                  if (el.requestFullscreen) el.requestFullscreen();
                }
                async function pollStatus() {
                  try {
                    const res = await fetch('/status.json', { cache: 'no-store' });
                    const st = await res.json();
                    const pill = document.getElementById('statusPill');
                    const standby = document.getElementById('standby');
                    const mirror = document.getElementById('mirrorView');
                    const video = document.getElementById('videoView');
                    const photo = document.getElementById('photoView');
                    if (st.mirroring) {
                      pill.textContent = 'LIVE • ' + st.resolution + ' • ' + st.fps + ' fps';
                      if (currentMode !== 'mirror') {
                        currentMode = 'mirror';
                        standby.style.display = 'none';
                        video.style.display = 'none'; video.pause();
                        photo.style.display = 'none';
                        mirror.src = '/stream.mjpeg?t=' + Date.now();
                        mirror.style.display = 'block';
                      }
                    } else if (st.mediaTitle) {
                      pill.textContent = 'CASTING • ' + st.mediaTitle;
                      if (currentMode !== 'media' || currentMedia !== st.mediaTitle) {
                        currentMode = 'media';
                        currentMedia = st.mediaTitle;
                        standby.style.display = 'none';
                        mirror.style.display = 'none'; mirror.src = '';
                        if (st.isVideo) {
                          photo.style.display = 'none';
                          video.src = '/media?t=' + Date.now();
                          video.style.display = 'block';
                          video.play().catch(() => {});
                        } else {
                          video.style.display = 'none'; video.pause();
                          photo.src = '/media?t=' + Date.now();
                          photo.style.display = 'block';
                        }
                      }
                    } else {
                      pill.textContent = 'Standby';
                      if (currentMode !== 'idle') {
                        currentMode = 'idle';
                        mirror.style.display = 'none'; mirror.src = '';
                        video.style.display = 'none'; video.pause();
                        photo.style.display = 'none';
                        standby.style.display = 'block';
                      }
                    }
                  } catch (e) {}
                }
                setInterval(pollStatus, 1200);
                pollStatus();
              </script>
            </body>
            </html>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val header = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: text/html; charset=utf-8\r\n")
            append("Content-Length: ${html.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n\r\n")
        }.toByteArray(Charsets.UTF_8)
        out.write(header)
        out.write(html)
        out.flush()
    }

    private fun serveStatusJson(out: OutputStream) {
        val media = activeMediaItem
        val safeTitle = (media?.title ?: "").replace("\"", "\\\"")
        val json = """
            {"mirroring":$isMirroringActive,"fps":$currentFpsMetric,"resolution":"$currentResolutionLabel","mediaTitle":"$safeTitle","isVideo":${media?.isVideo == true}}
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val header = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: application/json\r\n")
            append("Content-Length: ${json.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n\r\n")
        }.toByteArray(Charsets.UTF_8)
        out.write(header)
        out.write(json)
        out.flush()
    }

    private fun serveMjpegStream(socket: Socket, out: OutputStream) {
        clientCounter.incrementAndGet()
        try {
            socket.soTimeout = 0
            val boundary = "mirraframe"
            val initHeader = buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: multipart/x-mixed-replace; boundary=$boundary\r\n")
                append("Cache-Control: no-store, no-cache, must-revalidate, pre-check=0, post-check=0, max-age=0\r\n")
                append("Pragma: no-cache\r\n")
                append("Connection: close\r\n\r\n")
            }.toByteArray(Charsets.UTF_8)
            out.write(initHeader)
            out.flush()

            var lastSentSeq = -1L
            while (!socket.isClosed && isMirroringActive) {
                var frame: ByteArray?
                synchronized(frameLock) {
                    while (isMirroringActive && frameSeq.get() == lastSentSeq) {
                        frameLock.wait(250)
                    }
                    lastSentSeq = frameSeq.get()
                    frame = latestJpegFrame
                }
                val bytes = frame ?: continue
                val partHeader = buildString {
                    append("--$boundary\r\n")
                    append("Content-Type: image/jpeg\r\n")
                    append("Content-Length: ${bytes.size}\r\n\r\n")
                }.toByteArray(Charsets.UTF_8)
                out.write(partHeader)
                out.write(bytes)
                out.write("\r\n".toByteArray(Charsets.UTF_8))
                out.flush()
            }
        } finally {
            clientCounter.decrementAndGet()
        }
    }

    private fun serveH264Stream(socket: Socket, out: OutputStream) {
        clientCounter.incrementAndGet()
        val clientQueue = H264ClientQueue()
        h264Subscribers.add(clientQueue)
        try {
            socket.soTimeout = 0
            val header = buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: video/h264\r\n")
                append("Cache-Control: no-store\r\n")
                append("Connection: close\r\n\r\n")
            }.toByteArray(Charsets.UTF_8)
            out.write(header)
            codecConfigPacket?.let { out.write(it) }
            out.flush()

            while (!socket.isClosed && isMirroringActive) {
                val nal = clientQueue.queue.poll(300, java.util.concurrent.TimeUnit.MILLISECONDS)
                    ?: continue
                out.write(nal)
                out.flush()
            }
        } finally {
            h264Subscribers.remove(clientQueue)
            clientCounter.decrementAndGet()
        }
    }

    private fun serveActiveMedia(out: OutputStream, rangeHeader: String?) {
        val item = activeMediaItem
        if (item == null) {
            serveNotFound(out)
            return
        }
        val uri = Uri.parse(item.uriString)
        val resolver = context.contentResolver
        val pfd = runCatching { resolver.openFileDescriptor(uri, "r") }.getOrNull()
        val totalLen = pfd?.statSize?.takeIf { it > 0 } ?: item.sizeBytes
        pfd?.close()

        var start = 0L
        var end = if (totalLen > 0) totalLen - 1 else -1L
        var isPartial = false

        if (!rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=") && totalLen > 0) {
            val rangeSpec = rangeHeader.removePrefix("bytes=").trim()
            val dash = rangeSpec.indexOf('-')
            if (dash >= 0) {
                val startStr = rangeSpec.substring(0, dash).trim()
                val endStr = rangeSpec.substring(dash + 1).trim()
                start = startStr.toLongOrNull() ?: 0L
                end = endStr.toLongOrNull()?.coerceAtMost(totalLen - 1) ?: (totalLen - 1)
                if (start <= end) {
                    isPartial = true
                }
            }
        }

        val mime = item.mimeType.ifBlank { if (item.isVideo) "video/mp4" else "image/jpeg" }
        val input = resolver.openInputStream(uri)
        if (input == null) {
            serveNotFound(out)
            return
        }

        clientCounter.incrementAndGet()
        try {
            input.use { stream ->
                val buffered = BufferedInputStream(stream, 65536)
                if (isPartial && start > 0) {
                    var remainingSkip = start
                    while (remainingSkip > 0) {
                        val skipped = buffered.skip(remainingSkip)
                        if (skipped <= 0) break
                        remainingSkip -= skipped
                    }
                }

                val contentLength = if (totalLen > 0 && end >= start) (end - start + 1) else -1L
                val statusLine = if (isPartial) "HTTP/1.1 206 Partial Content" else "HTTP/1.1 200 OK"
                val respHeader = buildString {
                    append("$statusLine\r\n")
                    append("Content-Type: $mime\r\n")
                    append("Accept-Ranges: bytes\r\n")
                    append("Access-Control-Allow-Origin: *\r\n")
                    append("transferMode.dlna.org: Streaming\r\n")
                    if (contentLength >= 0) {
                        append("Content-Length: $contentLength\r\n")
                    }
                    if (isPartial && totalLen > 0) {
                        append("Content-Range: bytes $start-$end/$totalLen\r\n")
                    }
                    append("Connection: close\r\n\r\n")
                }.toByteArray(Charsets.UTF_8)
                out.write(respHeader)

                val buffer = ByteArray(65536)
                var remaining = contentLength
                while (true) {
                    val toRead = if (remaining >= 0) minOf(buffer.size.toLong(), remaining).toInt() else buffer.size
                    if (toRead <= 0) break
                    val read = buffered.read(buffer, 0, toRead)
                    if (read == -1) break
                    out.write(buffer, 0, read)
                    if (remaining >= 0) remaining -= read
                }
                out.flush()
            }
        } finally {
            clientCounter.decrementAndGet()
        }
    }

    private fun serveNotFound(out: OutputStream) {
        val body = "Not Found".toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 404 Not Found\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
            .toByteArray(Charsets.UTF_8)
        out.write(header)
        out.write(body)
        out.flush()
    }
}
