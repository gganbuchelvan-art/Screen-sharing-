package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.SmartTvDevice
import com.example.model.TvProtocol
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldLive

@Composable
fun DevicesScreen(
    devices: List<SmartTvDevice>,
    selectedDevice: SmartTvDevice?,
    isScanning: Boolean,
    protocolFilter: TvProtocol?,
    onSetProtocolFilter: (TvProtocol?) -> Unit,
    onRescan: () -> Unit,
    onSelectDevice: (SmartTvDevice) -> Unit,
    onAddManualDevice: (name: String, ip: String, port: Int, protocol: TvProtocol) -> Unit,
    onRemoveManualDevice: (String) -> Unit,
    onOpenWirelessDisplay: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPairDialog by remember { mutableStateOf(false) }

    val filteredDevices = remember(devices, protocolFilter) {
        if (protocolFilter == null) devices
        else devices.filter { it.protocol == protocolFilter }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("devices_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DiscoveryHeaderCard(
                    isScanning = isScanning,
                    deviceCount = devices.size,
                    onRescan = onRescan,
                    onOpenManualPair = { showPairDialog = true }
                )

                // Protocol filter chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = protocolFilter == null,
                        onClick = { onSetProtocolFilter(null) },
                        label = { Text("All (${devices.size})") },
                        modifier = Modifier.testTag("filter_all")
                    )
                    listOf(
                        TvProtocol.DLNA_UPNP,
                        TvProtocol.CHROMECAST_MDNS,
                        TvProtocol.AIRPLAY_MDNS,
                        TvProtocol.MIRACAST_WFD,
                        TvProtocol.BROWSER_RECEIVER
                    ).forEach { proto ->
                        FilterChip(
                            selected = protocolFilter == proto,
                            onClick = {
                                onSetProtocolFilter(if (protocolFilter == proto) null else proto)
                            },
                            label = { Text(proto.badge) },
                            modifier = Modifier.testTag("filter_${proto.name.lowercase()}")
                        )
                    }
                }
            }
        }

        if (filteredDevices.isEmpty()) {
            item {
                EmptyDevicesCard(
                    isScanning = isScanning,
                    onRescan = onRescan,
                    onOpenManualPair = { showPairDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp)
                )
            }
        } else {
            items(filteredDevices, key = { it.id }) { device ->
                SmartTvDeviceCard(
                    device = device,
                    isSelected = selectedDevice?.id == device.id,
                    onSelect = { onSelectDevice(device) },
                    onRemove = if (device.isManual) {
                        { onRemoveManualDevice(device.id) }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp)
                )
            }
        }

        item {
            WirelessDisplayBridgeCard(
                onOpenWirelessDisplay = onOpenWirelessDisplay,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
            )
        }
    }

    if (showPairDialog) {
        PairManualTvDialog(
            onDismiss = { showPairDialog = false },
            onConfirm = { name, ip, port, protocol ->
                onAddManualDevice(name, ip, port, protocol)
                showPairDialog = false
            }
        )
    }
}

@Composable
private fun DiscoveryHeaderCard(
    isScanning: Boolean,
    deviceCount: Int,
    onRescan: () -> Unit,
    onOpenManualPair: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar_scan")
    val radarScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_scale"
    )
    val radarAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_alpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(48.dp)
                ) {
                    if (isScanning) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .scale(radarScale)
                                .alpha(radarAlpha)
                                .clip(CircleShape)
                                .background(CyanPulse)
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Radar,
                                contentDescription = "Discovery Radar",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isScanning) "Scanning Local Wi-Fi..." else "Smart TV Discovery",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isScanning) {
                            "Probing NSD mDNS, SSDP/UPnP & MediaRouter"
                        } else {
                            "$deviceCount display endpoint(s) available"
                        },
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
                    onClick = onRescan,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("scan_network_button"),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.action_scan_tvs))
                }

                OutlinedButton(
                    onClick = onOpenManualPair,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("pair_manual_button"),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.action_pair_manual))
                }
            }
        }
    }
}

@Composable
private fun SmartTvDeviceCard(
    device: SmartTvDevice,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onSelect,
        modifier = modifier.testTag("tv_device_card_${device.id}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isSelected) Icons.Filled.CastConnected else Icons.Filled.Tv,
                        contentDescription = device.name,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${device.manufacturer} • ${device.ipAddress}:${device.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = device.protocol.badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    device.rttMs?.let { rtt ->
                        Text(
                            text = "${rtt} ms RTT",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldLive
                        )
                    }
                }
            }

            if (onRemove != null) {
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Remove paired TV",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Selected TV",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun EmptyDevicesCard(
    isScanning: Boolean,
    onRescan: () -> Unit,
    onOpenManualPair: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Tv,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = if (isScanning) "Searching for Smart TVs..." else "No Smart TVs Detected Yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Ensure your phone and Smart TV are on the same Wi-Fi network (5 GHz recommended for 1080p/4K low-latency streaming), or open the Cast Stage Web Receiver URL in your TV browser.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onRescan) {
                    Text("Scan Again")
                }
                TextButton(onClick = onOpenManualPair) {
                    Text("Pair by IP")
                }
            }
        }
    }
}

@Composable
private fun WirelessDisplayBridgeCard(
    onOpenWirelessDisplay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.SettingsInputAntenna,
                contentDescription = "Hardware Miracast",
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Hardware Wi-Fi Direct / Miracast",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Open Android system Wireless Display picker for native WFD sinks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = onOpenWirelessDisplay,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("devices_wireless_display_btn")
            ) {
                Text("Open")
            }
        }
    }
}

@Composable
private fun PairManualTvDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, ip: String, port: Int, protocol: TvProtocol) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var ip by remember { mutableStateOf("") }
    var portStr by remember { mutableStateOf("8080") }
    var selectedProto by remember { mutableStateOf(TvProtocol.BROWSER_RECEIVER) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pair Smart TV Manually") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("TV Friendly Name") },
                    placeholder = { Text("Living Room OLED TV") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_tv_name_input")
                )
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("TV IP Address") },
                    placeholder = { Text("192.168.1.45") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_tv_ip_input")
                )
                OutlinedTextField(
                    value = portStr,
                    onValueChange = { portStr = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_tv_port_input")
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        TvProtocol.BROWSER_RECEIVER,
                        TvProtocol.DLNA_UPNP,
                        TvProtocol.RTSP_LOW_LATENCY
                    ).forEach { proto ->
                        FilterChip(
                            selected = selectedProto == proto,
                            onClick = { selectedProto = proto },
                            label = { Text(proto.badge) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(name, ip, portStr.toIntOrNull() ?: 8080, selectedProto)
                },
                modifier = Modifier.testTag("confirm_manual_pair_button")
            ) {
                Text("Pair TV")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
