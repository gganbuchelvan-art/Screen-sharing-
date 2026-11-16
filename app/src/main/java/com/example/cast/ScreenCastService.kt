package com.example.cast

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.IntentCompat
import com.example.MainActivity
import com.example.R
import com.example.model.CastSessionPhase
import com.example.model.LiveTelemetry
import com.example.model.StreamProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicLong

class ScreenCastService : Service() {

    companion object {
        const val ACTION_START = "com.example.cast.ACTION_START"
        const val ACTION_STOP = "com.example.cast.ACTION_STOP"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        private const val CHANNEL_ID = "mirracast_live_channel"
        private const val NOTIF_ID = 4091
    }

    private val tag = "ScreenCastService"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var mediaCodec: MediaCodec? = null
    private var codecInputSurface: Surface? = null

    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private var codecDrainJob: Job? = null
    private var telemetryJob: Job? = null

    private var captureWidth = 1280
    private var captureHeight = 720
    private var captureDpi = 320
    private var currentProfile = StreamProfile()

    private val encodedFrameCounter = AtomicLong(0L)
    private val droppedFrameCounter = AtomicLong(0L)
    private val bytesInWindow = AtomicLong(0L)
    private val framesInWindow = AtomicLong(0L)

    @Volatile
    private var lastFrameTimestampNs = 0L

    @Volatile
    private var activeEncoderName = "Hardware AVC"

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.i(tag, "MediaProjection stopped by system or status bar chip")
            stopMirroringAndSelf()
        }

        override fun onCapturedContentResize(width: Int, height: Int) {
            if (width > 0 && height > 0) {
                Log.d(tag, "Captured content resized: ${width}x${height}")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMirroringAndSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData = IntentCompat.getParcelableExtra(
                    intent,
                    EXTRA_RESULT_DATA,
                    Intent::class.java
                )
                if (resultCode != Activity.RESULT_OK || resultData == null) {
                    CastSessionBus.updateTelemetry(
                        LiveTelemetry(
                            phase = CastSessionPhase.ERROR,
                            errorMessage = "Screen capture permission was not granted."
                        )
                    )
                    stopSelf()
                    return START_NOT_STICKY
                }

                startForegroundCompat()
                startProjectionPipeline(resultCode, resultData)
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        val notification = buildNotification("Initializing low-latency stream...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun startProjectionPipeline(resultCode: Int, resultData: Intent) {
        releasePipeline()
        currentProfile = CastSessionBus.activeProfile.value
        val server = CastSessionBus.ensureServer(this)
        server.isMirroringActive = true

        val projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = projectionManager.getMediaProjection(resultCode, resultData)
        if (projection == null) {
            CastSessionBus.updateTelemetry(
                LiveTelemetry(
                    phase = CastSessionPhase.ERROR,
                    errorMessage = "Unable to acquire MediaProjection session."
                )
            )
            stopSelf()
            return
        }
        mediaProjection = projection

        val (targetW, targetH, dpi) = computeAlignedCaptureSize(currentProfile)
        captureWidth = targetW
        captureHeight = targetH
        captureDpi = dpi

        server.currentResolutionLabel = currentProfile.resolution.shortBadge

        captureThread = HandlerThread("MirraCaptureThread").apply { start() }
        captureHandler = Handler(captureThread!!.looper)

        // Android 14+ requires registering MediaProjection.Callback before createVirtualDisplay
        projection.registerCallback(projectionCallback, captureHandler)

        setupHardwareEncoder(currentProfile, targetW, targetH)
        setupImageReaderAndVirtualDisplay(projection, targetW, targetH, dpi)

        val localIp = NetworkUtils.resolveLocalIpv4Address(this)
        val receiverUrl = "http://$localIp:${server.port}/"
        val h264Url = "http://$localIp:${server.port}/stream.h264"

        updateNotification("Mirroring ${currentProfile.resolution.shortBadge} • Open $receiverUrl on TV")
        startTelemetryLoop(server, receiverUrl, h264Url)
    }

    private fun computeAlignedCaptureSize(profile: StreamProfile): Triple<Int, Int, Int> {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val screenW: Int
        val screenH: Int
        val dpi: Int

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.maximumWindowMetrics.bounds
            screenW = bounds.width().coerceAtLeast(720)
            screenH = bounds.height().coerceAtLeast(1280)
            dpi = resources.displayMetrics.densityDpi
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            screenW = dm.widthPixels.coerceAtLeast(720)
            screenH = dm.heightPixels.coerceAtLeast(1280)
            dpi = dm.densityDpi
        }

        val maxDim = profile.resolution.width // e.g., 1280, 1920, 2560, 3840
        val longEdge = maxOf(screenW, screenH)
        val scale = if (longEdge > maxDim) maxDim.toFloat() / longEdge.toFloat() else 1f

        val alignedW = ((screenW * scale).toInt() / 16) * 16
        val alignedH = ((screenH * scale).toInt() / 16) * 16
        return Triple(alignedW.coerceAtLeast(480), alignedH.coerceAtLeast(480), dpi)
    }

    private fun setupHardwareEncoder(profile: StreamProfile, width: Int, height: Int) {
        val mime = profile.codec.mimeType
        val bitrateBps = profile.bitrateMbps * 1_000_000
        val created = runCatching {
            createConfiguredCodec(mime, width, height, profile.fps, bitrateBps, profile.lowLatencyMode)
        }.getOrElse {
            // Safe fallback to universal H.264 1080p if device doesn't support HEVC or 4K encoding
            runCatching {
                createConfiguredCodec(
                    MediaFormat.MIMETYPE_VIDEO_AVC,
                    minOf(width, 1920),
                    minOf(height, 1080),
                    minOf(profile.fps, 30),
                    8_000_000,
                    profile.lowLatencyMode
                )
            }.getOrNull()
        }

        if (created == null) {
            activeEncoderName = "Low-Latency Frame Encoder"
            return
        }

        mediaCodec = created
        activeEncoderName = created.name
        codecInputSurface = created.createInputSurface()
        created.start()

        val server = CastSessionBus.ensureServer(this)
        codecDrainJob = serviceScope.launch(Dispatchers.Default) {
            val bufferInfo = MediaCodec.BufferInfo()
            while (isActive) {
                val codec = mediaCodec ?: break
                val index = runCatching { codec.dequeueOutputBuffer(bufferInfo, 15_000) }
                    .getOrElse { break }
                when {
                    index >= 0 -> {
                        val outBuf = codec.getOutputBuffer(index)
                        if (outBuf != null && bufferInfo.size > 0) {
                            val bytes = ByteArray(bufferInfo.size)
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            outBuf.get(bytes)

                            val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                            server.publishH264Nal(bytes, isConfig)
                            if (!isConfig) {
                                encodedFrameCounter.incrementAndGet()
                                framesInWindow.incrementAndGet()
                                bytesInWindow.addAndGet(bufferInfo.size.toLong())
                            }
                        }
                        codec.releaseOutputBuffer(index, false)
                    }
                    index == MediaCodec.INFO_TRY_AGAIN_LATER -> delay(4)
                }
            }
        }
    }

    private fun createConfiguredCodec(
        mime: String,
        width: Int,
        height: Int,
        fps: Int,
        bitrateBps: Int,
        lowLatency: Boolean
    ): MediaCodec {
        val format = MediaFormat.createVideoFormat(mime, width, height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_BIT_RATE, bitrateBps)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(MediaFormat.KEY_PRIORITY, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && lowLatency) {
                setInteger(MediaFormat.KEY_LATENCY, 1)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && lowLatency) {
                setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            }
        }
        return MediaCodec.createEncoderByType(mime).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }
    }

    private fun setupImageReaderAndVirtualDisplay(
        projection: MediaProjection,
        width: Int,
        height: Int,
        dpi: Int
    ) {
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        val server = CastSessionBus.ensureServer(this)
        val minFrameIntervalNs = 1_000_000_000L / currentProfile.fps.coerceIn(15, 60)

        var reusableBitmap: Bitmap? = null
        val jpegStream = ByteArrayOutputStream(65536)

        reader.setOnImageAvailableListener({ r ->
            val image = runCatching { r.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
            try {
                val now = System.nanoTime()
                if (now - lastFrameTimestampNs < minFrameIntervalNs) {
                    droppedFrameCounter.incrementAndGet()
                    return@setOnImageAvailableListener
                }
                lastFrameTimestampNs = now

                val plane = image.planes[0]
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width
                val bmpWidth = width + rowPadding / pixelStride

                val bmp = reusableBitmap?.takeIf { it.width == bmpWidth && it.height == height }
                    ?: Bitmap.createBitmap(bmpWidth, height, Bitmap.Config.ARGB_8888).also {
                        reusableBitmap = it
                    }

                plane.buffer.rewind()
                bmp.copyPixelsFromBuffer(plane.buffer)

                // 1. Feed hardware MediaCodec surface for real H.264/HEVC encoding
                codecInputSurface?.let { surface ->
                    runCatching {
                        val canvas = surface.lockHardwareCanvas()
                        try {
                            canvas.drawBitmap(
                                bmp,
                                Rect(0, 0, width, height),
                                Rect(0, 0, canvas.width, canvas.height),
                                null
                            )
                        } finally {
                            surface.unlockCanvasAndPost(canvas)
                        }
                    }
                }

                // 2. Push low-latency JPEG frame to MJPEG Web Receiver if clients connected or periodically
                jpegStream.reset()
                val quality = if (currentProfile.lowLatencyMode) 76 else 86
                val cropped = if (rowPadding == 0) {
                    bmp
                } else {
                    Bitmap.createBitmap(bmp, 0, 0, width, height)
                }
                cropped.compress(Bitmap.CompressFormat.JPEG, quality, jpegStream)
                if (cropped !== bmp) cropped.recycle()
                server.publishJpegFrame(jpegStream.toByteArray())

                if (mediaCodec == null) {
                    encodedFrameCounter.incrementAndGet()
                    framesInWindow.incrementAndGet()
                    bytesInWindow.addAndGet(jpegStream.size().toLong())
                }
            } catch (e: Exception) {
                Log.w(tag, "Frame capture error: ${e.message}")
            } finally {
                image.close()
            }
        }, captureHandler)

        virtualDisplay = projection.createVirtualDisplay(
            "MirraCastDisplay",
            width,
            height,
            dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            captureHandler
        )
    }

    private fun startTelemetryLoop(
        server: LocalCastServer,
        receiverUrl: String,
        h264Url: String
    ) {
        telemetryJob?.cancel()
        telemetryJob = serviceScope.launch {
            while (isActive) {
                delay(500)
                val framesHalfSec = framesInWindow.getAndSet(0L)
                val bytesHalfSec = bytesInWindow.getAndSet(0L)
                val fps = (framesHalfSec * 2).toInt().coerceAtMost(currentProfile.fps)
                val mbps = ((bytesHalfSec * 8f * 2f) / 1_000_000f)

                server.currentFpsMetric = fps
                val estLatency = if (currentProfile.lowLatencyMode) {
                    when {
                        fps >= 50 -> 28
                        fps >= 25 -> 42
                        else -> 58
                    }
                } else {
                    95
                }

                CastSessionBus.updateTelemetry(
                    LiveTelemetry(
                        phase = CastSessionPhase.MIRRORING_LIVE,
                        activeResolution = currentProfile.resolution,
                        currentFps = fps,
                        targetFps = currentProfile.fps,
                        actualBitrateMbps = mbps,
                        encodedFrames = encodedFrameCounter.get(),
                        droppedFrames = droppedFrameCounter.get(),
                        estimatedLatencyMs = estLatency,
                        connectedClients = server.connectedClients,
                        receiverUrl = receiverUrl,
                        h264StreamUrl = h264Url,
                        encoderName = activeEncoderName
                    )
                )
            }
        }
    }

    private fun stopMirroringAndSelf() {
        releasePipeline()
        val ip = NetworkUtils.resolveLocalIpv4Address(this)
        val port = CastSessionBus.ensureServer(this).port
        CastSessionBus.updateTelemetry(
            LiveTelemetry(
                phase = CastSessionPhase.IDLE,
                receiverUrl = "http://$ip:$port/"
            )
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releasePipeline() {
        telemetryJob?.cancel()
        telemetryJob = null
        codecDrainJob?.cancel()
        codecDrainJob = null

        CastSessionBus.ensureServer(this).isMirroringActive = false

        runCatching { virtualDisplay?.release() }
        virtualDisplay = null

        runCatching { imageReader?.close() }
        imageReader = null

        runCatching { codecInputSurface?.release() }
        codecInputSurface = null

        runCatching {
            mediaCodec?.stop()
            mediaCodec?.release()
        }
        mediaCodec = null

        runCatching {
            mediaProjection?.unregisterCallback(projectionCallback)
            mediaProjection?.stop()
        }
        mediaProjection = null

        runCatching { captureThread?.quitSafely() }
        captureThread = null
        captureHandler = null
    }

    override fun onDestroy() {
        releasePipeline()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ScreenCastService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notif_mirroring_title))
            .setContentText(contentText)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .addAction(0, getString(R.string.notif_stop_action), stopIntent)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(contentText))
    }
}
