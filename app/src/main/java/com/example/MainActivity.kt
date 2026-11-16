package com.example

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.model.CastSessionPhase
import com.example.ui.MainViewModel
import com.example.ui.screens.CastStageScreen
import com.example.ui.screens.DevicesScreen
import com.example.ui.screens.StudioConfigScreen
import com.example.ui.theme.MirraCastTheme
import kotlinx.coroutines.launch

const val ROUTE_CAST_STAGE = "cast_stage"
const val ROUTE_DEVICES = "devices"
const val ROUTE_STUDIO_CONFIG = "studio_config"

private data class NavTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MirraCastTheme {
                MirraCastApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MirraCastApp(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()

    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val profile by viewModel.streamProfile.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val selectedDevice by viewModel.selectedDevice.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val activeMedia by viewModel.activeMedia.collectAsStateWithLifecycle()
    val mediaPlayback by viewModel.mediaPlayback.collectAsStateWithLifecycle()
    val hardwareCaps by viewModel.hardwareCapabilities.collectAsStateWithLifecycle()
    val protocolFilter by viewModel.protocolFilter.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.uiMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            viewModel.startScreenMirroring(result.resultCode, result.data!!)
        } else {
            uiScope.launch {
                snackbarHostState.showSnackbar("Screen capture permission declined.")
            }
        }
    }

    val launchScreenCapture = {
        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        launchScreenCapture()
    }

    val requestMirrorStart = {
        if (telemetry.phase == CastSessionPhase.MIRRORING_LIVE) {
            viewModel.stopScreenMirroring()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    launchScreenCapture()
                }
            } else {
                launchScreenCapture()
            }
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.castPickedMedia(uri, isVideo = true)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.castPickedMedia(uri, isVideo = false)
        }
    }

    val openWirelessDisplaySettings = {
        val intent = Intent(Settings.ACTION_CAST_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launched = runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
        if (!launched) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }.onFailure {
                uiScope.launch {
                    snackbarHostState.showSnackbar("Wireless Display settings unavailable on this device.")
                }
            }
        }
    }

    val copyReceiverUrl: (String) -> Unit = { url ->
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("MirraCast TV Receiver URL", url))
        uiScope.launch {
            snackbarHostState.showSnackbar("Copied TV Receiver URL: $url")
        }
    }

    val tabs = remember {
        listOf(
            NavTab(
                route = ROUTE_CAST_STAGE,
                labelRes = R.string.nav_stage,
                selectedIcon = Icons.Filled.Cast,
                unselectedIcon = Icons.Outlined.Cast,
                testTag = "nav_tab_stage"
            ),
            NavTab(
                route = ROUTE_DEVICES,
                labelRes = R.string.nav_devices,
                selectedIcon = Icons.Filled.Devices,
                unselectedIcon = Icons.Outlined.Devices,
                testTag = "nav_tab_devices"
            ),
            NavTab(
                route = ROUTE_STUDIO_CONFIG,
                labelRes = R.string.nav_studio,
                selectedIcon = Icons.Filled.Tune,
                unselectedIcon = Icons.Outlined.Tune,
                testTag = "nav_tab_studio"
            )
        )
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_CAST_STAGE

    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = when (currentRoute) {
                                ROUTE_DEVICES -> "Smart TV Discovery"
                                ROUTE_STUDIO_CONFIG -> "Broadcast Studio"
                                else -> stringResource(R.string.app_name)
                            },
                            style = MaterialTheme.typography.headlineSmall
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        tabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(text = stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                        tabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(text = stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_CAST_STAGE
                    ) {
                        composable(ROUTE_CAST_STAGE) {
                            CastStageScreen(
                                telemetry = telemetry,
                                profile = profile,
                                selectedDevice = selectedDevice,
                                activeMedia = activeMedia,
                                mediaPlayback = mediaPlayback,
                                hardwareCaps = hardwareCaps,
                                onToggleMirroring = requestMirrorStart,
                                onPickVideo = {
                                    videoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                },
                                onPickPhoto = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                onSelectDeviceClick = { navigateToTab(ROUTE_DEVICES) },
                                onOpenWirelessDisplay = openWirelessDisplaySettings,
                                onCopyReceiverUrl = copyReceiverUrl,
                                onToggleMediaPlayPause = viewModel::toggleMediaPlayPause,
                                onStopMedia = viewModel::clearActiveMedia,
                                onVolumeChange = viewModel::setTvVolume
                            )
                        }

                        composable(ROUTE_DEVICES) {
                            DevicesScreen(
                                devices = devices,
                                selectedDevice = selectedDevice,
                                isScanning = isScanning,
                                protocolFilter = protocolFilter,
                                onSetProtocolFilter = viewModel::setProtocolFilter,
                                onRescan = viewModel::startNetworkScan,
                                onSelectDevice = viewModel::selectDevice,
                                onAddManualDevice = viewModel::addManualDevice,
                                onRemoveManualDevice = viewModel::removeManualDevice,
                                onOpenWirelessDisplay = openWirelessDisplaySettings
                            )
                        }

                        composable(ROUTE_STUDIO_CONFIG) {
                            StudioConfigScreen(
                                profile = profile,
                                hardwareCaps = hardwareCaps,
                                onSelectResolution = viewModel::updateResolution,
                                onSelectFps = viewModel::updateFps,
                                onBitrateChange = viewModel::updateBitrate,
                                onSelectCodec = viewModel::updateCodec,
                                onToggleLowLatency = viewModel::updateLowLatency,
                                onToggleAdaptiveBitrate = viewModel::updateAdaptiveBitrate,
                                onToggleAudioMirroring = viewModel::updateAudioMirroring
                            )
                        }
                    }
                }
            }
        }
    }
}
