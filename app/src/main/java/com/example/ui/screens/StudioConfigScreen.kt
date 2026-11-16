package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.HardwareCapabilities
import com.example.model.StreamProfile
import com.example.model.VideoCodec
import com.example.model.VideoResolution
import com.example.ui.theme.AmberBroadcast
import com.example.ui.theme.EmeraldLive

@Composable
fun StudioConfigScreen(
    profile: StreamProfile,
    hardwareCaps: HardwareCapabilities,
    onSelectResolution: (VideoResolution) -> Unit,
    onSelectFps: (Int) -> Unit,
    onBitrateChange: (Int) -> Unit,
    onSelectCodec: (VideoCodec) -> Unit,
    onToggleLowLatency: (Boolean) -> Unit,
    onToggleAdaptiveBitrate: (Boolean) -> Unit,
    onToggleAudioMirroring: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("studio_config_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                VideoQualityCard(
                    profile = profile,
                    hardwareCaps = hardwareCaps,
                    onSelectResolution = onSelectResolution,
                    onSelectFps = onSelectFps,
                    onBitrateChange = onBitrateChange,
                    onSelectCodec = onSelectCodec
                )

                LowLatencyProtocolCard(
                    profile = profile,
                    onToggleLowLatency = onToggleLowLatency,
                    onToggleAdaptiveBitrate = onToggleAdaptiveBitrate,
                    onToggleAudioMirroring = onToggleAudioMirroring
                )

                HardwareDiagnosticsCard(hardwareCaps = hardwareCaps)
            }
        }
    }
}

@Composable
private fun VideoQualityCard(
    profile: StreamProfile,
    hardwareCaps: HardwareCapabilities,
    onSelectResolution: (VideoResolution) -> Unit,
    onSelectFps: (Int) -> Unit,
    onBitrateChange: (Int) -> Unit,
    onSelectCodec: (VideoCodec) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.HighQuality,
                    contentDescription = "HD Video Output",
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "High-Definition Output",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "TARGET RESOLUTION",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VideoResolution.entries.forEach { res ->
                        FilterChip(
                            selected = profile.resolution == res,
                            onClick = { onSelectResolution(res) },
                            label = { Text(res.shortBadge) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("res_${res.name.lowercase()}")
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "FRAME RATE CADENCE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(30, 60).forEach { fps ->
                        FilterChip(
                            selected = profile.fps == fps,
                            onClick = { onSelectFps(fps) },
                            label = { Text("$fps fps ${if (fps == 60) "(Silky)" else "(Cinema)"}") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("fps_$fps")
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ENCODER BITRATE",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${profile.bitrateMbps} Mbps",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = profile.bitrateMbps.toFloat(),
                    onValueChange = { onBitrateChange(it.toInt()) },
                    valueRange = 6f..30f,
                    steps = 11,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("bitrate_slider")
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "HARDWARE CODEC",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    VideoCodec.entries.forEach { codec ->
                        val enabled = codec == VideoCodec.H264_AVC || hardwareCaps.supportsHevc
                        FilterChip(
                            selected = profile.codec == codec,
                            enabled = enabled,
                            onClick = { onSelectCodec(codec) },
                            label = {
                                Text(
                                    if (codec == VideoCodec.H265_HEVC && !hardwareCaps.supportsHevc) {
                                        "HEVC (N/A)"
                                    } else {
                                        codec.shortTag
                                    }
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("codec_${codec.name.lowercase()}")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LowLatencyProtocolCard(
    profile: StreamProfile,
    onToggleLowLatency: (Boolean) -> Unit,
    onToggleAdaptiveBitrate: (Boolean) -> Unit,
    onToggleAudioMirroring: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Speed,
                    contentDescription = "Low Latency Protocol",
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Low-Latency Protocol Engine",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            ProtocolSwitchRow(
                title = "Ultra-Low Latency Mode",
                subtitle = "Sets MediaCodec KEY_LOW_LATENCY & zero-buffer frame pacing (~30ms)",
                checked = profile.lowLatencyMode,
                onCheckedChange = onToggleLowLatency,
                testTag = "switch_low_latency"
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ProtocolSwitchRow(
                title = "Adaptive Frame Pacing",
                subtitle = "Drops stale capture frames automatically on Wi-Fi congestion",
                checked = profile.adaptiveBitrate,
                onCheckedChange = onToggleAdaptiveBitrate,
                testTag = "switch_adaptive_bitrate"
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ProtocolSwitchRow(
                title = "Audio Stream Routing",
                subtitle = "Include media audio metadata for DLNA/UPnP & Web TV receivers",
                checked = profile.audioMirroring,
                onCheckedChange = onToggleAudioMirroring,
                testTag = "switch_audio_mirror"
            )
        }
    }
}

@Composable
private fun ProtocolSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun HardwareDiagnosticsCard(hardwareCaps: HardwareCapabilities) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.DeveloperBoard,
                    contentDescription = "Hardware Diagnostics",
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Device Hardware & Network",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            DiagnosticRow(
                label = "H.264 Hardware Encoder",
                value = hardwareCaps.avcEncoderName
            )
            DiagnosticRow(
                label = "H.265 HEVC Encoder",
                value = hardwareCaps.hevcEncoderName ?: "Not Available on Device"
            )
            DiagnosticRow(
                label = "Max Encoder Bounds",
                value = "${hardwareCaps.maxAvcWidth} × ${hardwareCaps.maxAvcHeight}"
            )
            DiagnosticRow(
                label = "Codec Low-Latency Flag",
                value = if (hardwareCaps.supportsLowLatencyFlag) "Supported (Hardware)" else "Frame-Paced Fallback",
                highlight = hardwareCaps.supportsLowLatencyFlag
            )
            DiagnosticRow(
                label = "Local Interface IP",
                value = "${hardwareCaps.localIpAddress} (${hardwareCaps.networkTransport})"
            )
        }
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
    highlight: Boolean = false
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = if (highlight) EmeraldLive else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
