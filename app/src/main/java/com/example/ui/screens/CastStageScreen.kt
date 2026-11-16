package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.StopScreenShare
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.SettingsInputAntenna
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.model.CastMediaItem
import com.example.model.CastSessionPhase
import com.example.model.HardwareCapabilities
import com.example.model.LiveTelemetry
import com.example.model.MediaPlaybackState
import com.example.model.SmartTvDevice
import com.example.model.StreamProfile
import com.example.ui.theme.AmberBroadcast
import com.example.ui.theme.CoralStop
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldLive

@Composable
fun CastStageScreen(
    telemetry: LiveTelemetry,
    profile: StreamProfile,
    selectedDevice: SmartTvDevice?,
    activeMedia: CastMediaItem?,
    mediaPlayback: MediaPlaybackState,
    hardwareCaps: HardwareCapabilities,
    onToggleMirroring: () -> Unit,
    onPickVideo: () -> Unit,
    onPickPhoto: () -> Unit,
    onSelectDeviceClick: () -> Unit,
    onOpenWirelessDisplay: () -> Unit,
    onCopyReceiverUrl: (String) -> Unit,
    onToggleMediaPlayPause: () -> Unit,
    onStopMedia: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isMirroring = telemetry.phase == CastSessionPhase.MIRRORING_LIVE

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("cast_stage_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HeroBroadcastStageCard(
                    isMirroring = isMirroring,
                    telemetry = telemetry,
                    profile = profile,
                    selectedDevice = selectedDevice,
                    onToggleMirroring = onToggleMirroring,
                    onSelectDeviceClick = onSelectDeviceClick
                )

                TelemetryStripCard(
                    isMirroring = isMirroring,
                    telemetry = telemetry,
                    profile = profile,
                    hardwareCaps = hardwareCaps
                )

                ReceiverEndpointCard(
                    receiverUrl = telemetry.receiverUrl,
                    connectedClients = telemetry.connectedClients,
                    onCopyUrl = onCopyReceiverUrl,
                    onOpenWirelessDisplay = onOpenWirelessDisplay
                )

                MediaCastDeckCard(
                    activeMedia = activeMedia,
                    mediaPlayback = mediaPlayback,
                    hasDlnaTarget = selectedDevice?.avTransportControlUrl != null,
                    onPickVideo = onPickVideo,
                    onPickPhoto = onPickPhoto,
                    onTogglePlayPause = onToggleMediaPlayPause,
                    onStopMedia = onStopMedia,
                    onVolumeChange = onVolumeChange
                )
            }
        }
    }
}

@Composable
private fun HeroBroadcastStageCard(
    isMirroring: Boolean,
    telemetry: LiveTelemetry,
    profile: StreamProfile,
    selectedDevice: SmartTvDevice?,
    onToggleMirroring: () -> Unit,
    onSelectDeviceClick: () -> Unit
) {
    val pulseTransition = rememberInfiniteTransition(label = "live_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isMirroring) EmeraldLive.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline,
        label = "stage_border"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(196.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_cast_stage),
                    contentDescription = "MirraCast Smart TV stage artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.25f),
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                                )
                            )
                        )
                )

                // Top status badge row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        border = BorderStroke(
                            1.dp,
                            if (isMirroring) EmeraldLive.copy(alpha = 0.8f)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .alpha(if (isMirroring) pulseAlpha else 1f)
                                    .clip(CircleShape)
                                    .background(if (isMirroring) EmeraldLive else CyanPulse)
                            )
                            Text(
                                text = if (isMirroring) "LIVE BROADCAST" else "LOW-LATENCY READY",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isMirroring) EmeraldLive else MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.Black.copy(alpha = 0.65f)
                    ) {
                        Text(
                            text = "${profile.resolution.shortBadge} • ${profile.fps}fps • ${profile.codec.shortTag}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                // Bottom overlay headline
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = if (isMirroring) "Screen Mirroring Active" else "Stream to Any Smart TV",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isMirroring) {
                            "Encoding via ${telemetry.encoderName}"
                        } else {
                            "Hardware-accelerated H.264/HEVC mirror & DLNA media cast"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Target TV selector row
                Surface(
                    onClick = onSelectDeviceClick,
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .testTag("target_tv_selector")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (selectedDevice != null) Icons.Filled.CastConnected else Icons.Filled.Tv,
                                contentDescription = "Target TV",
                                tint = if (selectedDevice != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column {
                                Text(
                                    text = selectedDevice?.name ?: "No Smart TV Selected",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (selectedDevice != null) {
                                        "${selectedDevice.protocol.badge} • ${selectedDevice.ipAddress}"
                                    } else {
                                        "Tap to scan network or use Web TV Receiver below"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = if (selectedDevice != null) "Switch" else "Scan",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Primary Mirror Screen CTA
                Button(
                    onClick = onToggleMirroring,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("mirror_toggle_button"),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isMirroring) CoralStop else MaterialTheme.colorScheme.primary,
                        contentColor = if (isMirroring) Color.White else MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = if (isMirroring) Icons.Filled.StopScreenShare else Icons.Filled.ScreenShare,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isMirroring) {
                            stringResource(R.string.action_stop_mirror)
                        } else {
                            stringResource(R.string.action_mirror_screen)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                AnimatedVisibility(visible = telemetry.phase == CastSessionPhase.ERROR && !telemetry.errorMessage.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = telemetry.errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryStripCard(
    isMirroring: Boolean,
    telemetry: LiveTelemetry,
    profile: StreamProfile,
    hardwareCaps: HardwareCapabilities
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Speed,
                        contentDescription = "Telemetry",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "PROTOCOL TELEMETRY",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = buildString {
                        append(hardwareCaps.networkTransport)
                        hardwareCaps.frequencyBandLabel?.let { append(" • $it") }
                        hardwareCaps.linkSpeedMbps?.let { append(" • ${it}Mbps") }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryMetricTile(
                    label = "FRAME RATE",
                    value = if (isMirroring) "${telemetry.currentFps} fps" else "${profile.fps} fps",
                    accentColor = if (isMirroring) EmeraldLive else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                TelemetryMetricTile(
                    label = "BITRATE",
                    value = if (isMirroring) {
                        String.format("%.1f Mbps", telemetry.actualBitrateMbps)
                    } else {
                        "${profile.bitrateMbps} Mbps"
                    },
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                TelemetryMetricTile(
                    label = "LATENCY",
                    value = if (isMirroring) {
                        "${telemetry.estimatedLatencyMs} ms"
                    } else if (profile.lowLatencyMode) {
                        "~30 ms"
                    } else {
                        "~95 ms"
                    },
                    accentColor = if (profile.lowLatencyMode) EmeraldLive else AmberBroadcast,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TelemetryMetricTile(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = accentColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ReceiverEndpointCard(
    receiverUrl: String,
    connectedClients: Int,
    onCopyUrl: (String) -> Unit,
    onOpenWirelessDisplay: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Universal Smart TV Web Receiver",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (connectedClients > 0) EmeraldLive.copy(alpha = 0.16f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (connectedClients > 0) "$connectedClients Connected" else "Standby",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (connectedClients > 0) EmeraldLive else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "Open this address in any Smart TV browser (Samsung, LG, Android TV, Fire TV) for instant low-latency mirroring:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = receiverUrl.ifBlank { "Starting receiver..." },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { if (receiverUrl.isNotBlank()) onCopyUrl(receiverUrl) },
                        modifier = Modifier.testTag("copy_receiver_url_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copy Receiver URL",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onOpenWirelessDisplay,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("open_wireless_display_button"),
                shape = MaterialTheme.shapes.small
            ) {
                Icon(
                    imageVector = Icons.Outlined.SettingsInputAntenna,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "System Miracast / Wireless Display Settings",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun MediaCastDeckCard(
    activeMedia: CastMediaItem?,
    mediaPlayback: MediaPlaybackState,
    hasDlnaTarget: Boolean,
    onPickVideo: () -> Unit,
    onPickPhoto: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onStopMedia: () -> Unit,
    onVolumeChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Cast,
                    contentDescription = "Cast local media",
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = "Direct HD Media Cast",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Stream local videos & photos over HTTP Byte-Range / DLNA",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onPickVideo,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("cast_video_button"),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(
                        imageVector = Icons.Filled.Movie,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.action_cast_video))
                }

                FilledTonalButton(
                    onClick = onPickPhoto,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("cast_photo_button"),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.action_cast_photo))
                }
            }

            if (activeMedia != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AsyncImage(
                        model = activeMedia.uriString,
                        contentDescription = activeMedia.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeMedia.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (hasDlnaTarget) "Casting via DLNA AVTransport" else "Ready on Web Receiver /media",
                            style = MaterialTheme.typography.bodySmall,
                            color = EmeraldLive
                        )
                    }
                    if (activeMedia.isVideo) {
                        IconButton(
                            onClick = onTogglePlayPause,
                            modifier = Modifier.testTag("media_play_pause_button")
                        ) {
                            Icon(
                                imageVector = if (mediaPlayback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (mediaPlayback.isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(
                        onClick = onStopMedia,
                        modifier = Modifier.testTag("media_stop_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = "Stop Media Cast",
                            tint = CoralStop
                        )
                    }
                }

                if (hasDlnaTarget && activeMedia.isVideo) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "TV Volume",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Slider(
                            value = mediaPlayback.volumePercent.toFloat(),
                            onValueChange = { onVolumeChange(it.toInt()) },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tv_volume_slider")
                        )
                        Text(
                            text = "${mediaPlayback.volumePercent}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
