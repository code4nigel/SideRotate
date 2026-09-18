package com.example.siderotate

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Surface
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.siderotate.core.RotationController
import com.example.siderotate.data.AppPreferences
import com.example.siderotate.data.AppSettings
import com.example.siderotate.data.ButtonPosition
import com.example.siderotate.data.RotationMode
import com.example.siderotate.service.SideRotateService
import com.example.siderotate.theme.AmberWarning
import com.example.siderotate.theme.CyanAccent
import com.example.siderotate.theme.DarkBackground
import com.example.siderotate.theme.DarkBorder
import com.example.siderotate.theme.DarkSurface
import com.example.siderotate.theme.DarkSurfaceVariant
import com.example.siderotate.theme.EmeraldSuccess
import com.example.siderotate.theme.IndigoPrimary
import com.example.siderotate.theme.IndigoSecondary
import com.example.siderotate.theme.SideRotateTheme
import com.example.siderotate.update.DownloadCallback
import com.example.siderotate.update.GitHubUpdateManager
import com.example.siderotate.update.UpdateCheckCallback
import com.example.siderotate.update.UpdateInfo
import com.example.siderotate.util.OemHelper
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var appPreferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appPreferences = AppPreferences(this)
        enableEdgeToEdge()

        setContent {
            SideRotateTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    SideRotateDashboard(appPreferences = appPreferences)
                }
            }
        }
    }
}

@Composable
fun SideRotateDashboard(appPreferences: AppPreferences) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings by appPreferences.settings.collectAsState()

    val updateManager = remember { GitHubUpdateManager(context) }
    val currentVersion = remember { updateManager.getCurrentVersionName() }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(-1) }
    var downloadedApkFile by remember { mutableStateOf<File?>(null) }

    // Dynamic permission states updated on lifecycle resume
    var hasOverlayPermission by remember { mutableStateOf(RotationController.canDrawOverlays(context)) }
    var hasWriteSettingsPermission by remember { mutableStateOf(RotationController.canWriteSettings(context)) }
    var isBatteryWhitelisted by remember { mutableStateOf(OemHelper.isIgnoringBatteryOptimizations(context)) }
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    var isAutoRotateLocked by remember { mutableStateOf(RotationController.isAutoRotateLocked(context)) }
    var currentRotation by remember { mutableStateOf(RotationController.getCurrentUserRotation(context)) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    // Check updates silently on launch
    LaunchedEffect(Unit) {
        updateManager.checkForUpdates(isManual = false, object : UpdateCheckCallback {
            override fun onResult(info: UpdateInfo) {
                if (info.hasUpdate) {
                    updateInfo = info
                }
            }
            override fun onError(message: String) {}
        })
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = RotationController.canDrawOverlays(context)
                hasWriteSettingsPermission = RotationController.canWriteSettings(context)
                isBatteryWhitelisted = OemHelper.isIgnoringBatteryOptimizations(context)
                hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                } else true
                isAutoRotateLocked = RotationController.isAutoRotateLocked(context)
                currentRotation = RotationController.getCurrentUserRotation(context)

                // Start service if enabled and permissions are present
                if (settings.isServiceEnabled && hasOverlayPermission && hasWriteSettingsPermission) {
                    SideRotateService.startService(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val allPermissionsGranted = hasOverlayPermission && hasWriteSettingsPermission

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBarModern(
                currentVersion = currentVersion,
                isCheckingUpdate = isCheckingUpdate,
                onCheckUpdateClick = {
                    if (isCheckingUpdate) return@TopAppBarModern
                    isCheckingUpdate = true
                    Toast.makeText(context, "Checking for updates...", Toast.LENGTH_SHORT).show()
                    updateManager.checkForUpdates(isManual = true, object : UpdateCheckCallback {
                        override fun onResult(info: UpdateInfo) {
                            isCheckingUpdate = false
                            if (info.hasUpdate) {
                                updateInfo = info
                                Toast.makeText(context, "New version v${info.cleanVersion} found!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Side Rotate is up to date (v$currentVersion)", Toast.LENGTH_SHORT).show()
                            }
                        }

                        override fun onError(message: String) {
                            isCheckingUpdate = false
                            Toast.makeText(context, "Update check failed: $message", Toast.LENGTH_LONG).show()
                        }
                    })
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // New Update Available Banner
            if (updateInfo?.hasUpdate == true) {
                UpdateBannerCard(
                    updateInfo = updateInfo!!,
                    isDownloading = isDownloading,
                    downloadProgress = downloadProgress,
                    downloadedApkFile = downloadedApkFile,
                    onDownloadClick = {
                        val apkUrl = updateInfo!!.apkDownloadUrl
                        if (apkUrl.isNullOrEmpty()) {
                            Toast.makeText(context, "No APK asset found in release", Toast.LENGTH_SHORT).show()
                            return@UpdateBannerCard
                        }
                        isDownloading = true
                        downloadProgress = 0
                        updateManager.downloadApk(apkUrl, updateInfo!!.apkFileName, object : DownloadCallback {
                            override fun onProgress(percent: Int, downloadedBytes: Long, totalBytes: Long) {
                                downloadProgress = percent
                            }

                            override fun onComplete(apkFile: File) {
                                isDownloading = false
                                downloadedApkFile = apkFile
                                Toast.makeText(context, "Download complete! Ready to install.", Toast.LENGTH_SHORT).show()
                                activity?.let { updateManager.installApk(it, apkFile) }
                            }

                            override fun onError(message: String) {
                                isDownloading = false
                                Toast.makeText(context, "Download error: $message", Toast.LENGTH_LONG).show()
                            }
                        })
                    },
                    onInstallClick = { file ->
                        activity?.let { updateManager.installApk(it, file) }
                    }
                )
            }

            // Master Switch & Live Status Card
            MasterStatusCard(
                settings = settings,
                allPermissionsGranted = allPermissionsGranted,
                isAutoRotateLocked = isAutoRotateLocked,
                currentRotation = currentRotation,
                onToggleService = { enabled ->
                    appPreferences.setServiceEnabled(enabled)
                    if (enabled) {
                        if (allPermissionsGranted) {
                            SideRotateService.startService(context)
                            Toast.makeText(context, "Side Rotate activated", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Please grant required permissions first", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        SideRotateService.stopService(context)
                        Toast.makeText(context, "Side Rotate stopped", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            // Permissions Section
            PermissionsCard(
                hasOverlay = hasOverlayPermission,
                hasWriteSettings = hasWriteSettingsPermission,
                isBatteryWhitelisted = isBatteryWhitelisted,
                hasNotification = hasNotificationPermission,
                onRequestOverlay = { OemHelper.openOverlaySettings(context) },
                onRequestWriteSettings = { OemHelper.openWriteSettings(context) },
                onRequestBattery = { OemHelper.requestIgnoreBatteryOptimizations(context) },
                onRequestNotification = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )

            // Test Overlay Button Card
            TestOverlayCard(
                hasOverlay = hasOverlayPermission,
                onTestClick = {
                    if (hasOverlayPermission) {
                        SideRotateService.testOverlay(context)
                        Toast.makeText(context, "Showing floating button for 4 seconds...", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Grant 'Display over other apps' first", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            // OEM Optimization Card (Vivo, Tecno, Samsung)
            OemOptimizationCard(
                onOpenOemSettings = {
                    OemHelper.openOemBackgroundSettings(context)
                }
            )

            // Appearance & Customization Settings
            AppearanceSettingsCard(
                settings = settings,
                appPreferences = appPreferences
            )

            // Rotation Behavior Settings
            RotationBehaviorCard(
                settings = settings,
                appPreferences = appPreferences
            )

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun TopAppBarModern(
    currentVersion: String,
    isCheckingUpdate: Boolean,
    onCheckUpdateClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(IndigoPrimary, CyanAccent)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_rotate_screen),
                    contentDescription = "App Icon",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = "Side Rotate",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Smart Tilt Orientation Helper",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = DarkSurfaceVariant,
            border = BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.clickable(onClick = onCheckUpdateClick)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (isCheckingUpdate) "Checking..." else "v$currentVersion",
                    color = CyanAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun UpdateBannerCard(
    updateInfo: UpdateInfo,
    isDownloading: Boolean,
    downloadProgress: Int,
    downloadedApkFile: File?,
    onDownloadClick: () -> Unit,
    onInstallClick: (File) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyanAccent.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(CyanAccent)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NEW VERSION AVAILABLE",
                        color = CyanAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Text(
                    text = "v${updateInfo.cleanVersion}",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = updateInfo.releaseTitle ?: "Side Rotate v${updateInfo.cleanVersion}",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            if (!updateInfo.changelogBody.isNullOrBlank()) {
                Text(
                    text = updateInfo.changelogBody,
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp,
                    maxLines = 4,
                    lineHeight = 18.sp
                )
            }

            if (isDownloading) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Downloading update...",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                        Text(
                            text = if (downloadProgress >= 0) "$downloadProgress%" else "Connecting...",
                            color = CyanAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    LinearProgressIndicator(
                        progress = { if (downloadProgress >= 0) downloadProgress / 100f else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = CyanAccent,
                        trackColor = DarkSurface
                    )
                }
            } else if (downloadedApkFile != null) {
                Button(
                    onClick = { onInstallClick(downloadedApkFile) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Install Update Now",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                val sizeText = if (updateInfo.apkSize > 0) " (${String.format("%.1f", updateInfo.apkSize / (1024f * 1024f))} MB)" else ""
                Button(
                    onClick = onDownloadClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Download & Install Update$sizeText",
                        color = Color(0xFF0F172A),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun MasterStatusCard(
    settings: AppSettings,
    allPermissionsGranted: Boolean,
    isAutoRotateLocked: Boolean,
    currentRotation: Int,
    onToggleService: (Boolean) -> Unit
) {
    val isRunning = settings.isServiceEnabled && allPermissionsGranted

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (isRunning) IndigoPrimary.copy(alpha = 0.5f) else DarkBorder, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .scale(if (isRunning) pulseScale else 1f)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isRunning -> EmeraldSuccess
                                    !allPermissionsGranted -> AmberWarning
                                    else -> Color.Gray
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = when {
                            isRunning -> "SERVICE RUNNING"
                            !allPermissionsGranted -> "SETUP REQUIRED"
                            else -> "SERVICE PAUSED"
                        },
                        color = when {
                            isRunning -> EmeraldSuccess
                            !allPermissionsGranted -> AmberWarning
                            else -> Color.Gray
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Switch(
                    checked = settings.isServiceEnabled,
                    onCheckedChange = onToggleService,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = IndigoPrimary,
                        uncheckedThumbColor = Color(0xFF64748B),
                        uncheckedTrackColor = DarkSurfaceVariant
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (isRunning) "Active & Listening" else "Tilt Monitor Inactive",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isRunning)
                    "Whenever your phone is tilted and rotation lock is ON, a rotation button will appear at the bottom."
                else
                    "Turn on the switch to detect phone tilt and show the floating rotation button.",
                color = Color(0xFF94A3B8),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(color = DarkBorder, thickness = 1.dp)
            Spacer(modifier = Modifier.height(14.dp))

            // Diagnostic Status Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatusBadge(
                    label = "Auto-Rotate",
                    value = if (isAutoRotateLocked) "Locked (Ready)" else "Auto-ON (Inactive)",
                    isGood = isAutoRotateLocked
                )

                StatusBadge(
                    label = "Screen Rotation",
                    value = when (currentRotation) {
                        Surface.ROTATION_0 -> "Portrait (0°)"
                        Surface.ROTATION_90 -> "Landscape (90°)"
                        Surface.ROTATION_180 -> "Reverse (180°)"
                        Surface.ROTATION_270 -> "Landscape (270°)"
                        else -> "Unknown"
                    },
                    isGood = true
                )
            }
        }
    }
}

@Composable
fun StatusBadge(label: String, value: String, isGood: Boolean) {
    Column {
        Text(
            text = label,
            color = Color(0xFF64748B),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = if (isGood) Color.White else AmberWarning,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun PermissionsCard(
    hasOverlay: Boolean,
    hasWriteSettings: Boolean,
    isBatteryWhitelisted: Boolean,
    hasNotification: Boolean,
    onRequestOverlay: () -> Unit,
    onRequestWriteSettings: () -> Unit,
    onRequestBattery: () -> Unit,
    onRequestNotification: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Required Permissions",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            PermissionItem(
                title = "Display Over Other Apps",
                subtitle = "Required to display the floating rotation button on screen.",
                isGranted = hasOverlay,
                onGrantClick = onRequestOverlay
            )

            HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)

            PermissionItem(
                title = "Modify System Settings",
                subtitle = "Required to change screen orientation without turning off lock.",
                isGranted = hasWriteSettings,
                onGrantClick = onRequestWriteSettings
            )

            HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)

            PermissionItem(
                title = "Ignore Battery Optimizations",
                subtitle = "Prevents Vivo/Samsung/Tecno from killing the tilt detector.",
                isGranted = isBatteryWhitelisted,
                onGrantClick = onRequestBattery
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)
                PermissionItem(
                    title = "Post Notifications",
                    subtitle = "Required for background service persistence.",
                    isGranted = hasNotification,
                    onGrantClick = onRequestNotification
                )
            }
        }
    }
}

@Composable
fun PermissionItem(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onGrantClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        if (isGranted) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(EmeraldSuccess.copy(alpha = 0.15f))
                    .border(1.dp, EmeraldSuccess.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 11.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "✓",
                        color = EmeraldSuccess,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Granted",
                        color = EmeraldSuccess,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        } else {
            Button(
                onClick = onGrantClick,
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Grant", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

@Composable
fun TestOverlayCard(
    hasOverlay: Boolean,
    onTestClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Test Overlay Button",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Pop up the button now to preview position & haptics.",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = onTestClick,
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Preview",
                    color = Color(0xFF0F172A),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun OemOptimizationCard(onOpenOemSettings: () -> Unit) {
    val oemType = OemHelper.getDeviceOem()
    val brandName = when (oemType) {
        com.example.siderotate.util.OemType.VIVO -> "Vivo Funtouch OS"
        com.example.siderotate.util.OemType.TECNO -> "Tecno HiOS"
        com.example.siderotate.util.OemType.SAMSUNG -> "Samsung One UI"
        com.example.siderotate.util.OemType.XIAOMI -> "Xiaomi MIUI/HyperOS"
        com.example.siderotate.util.OemType.GENERIC -> "Background Battery Optimization"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(CyanAccent)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "OEM Setup: $brandName",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = OemHelper.getOemGuidanceText(),
                color = Color(0xFF94A3B8),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            OutlinedButton(
                onClick = onOpenOemSettings,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Open Background Settings for $brandName", color = CyanAccent, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun AppearanceSettingsCard(
    settings: AppSettings,
    appPreferences: AppPreferences
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Button Appearance & Position",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            // Button Placement Selector
            Text(
                text = "Screen Corner",
                color = Color(0xFFCBD5E1),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PositionChoiceChip(
                    title = "Bottom-Left",
                    subtitle = "Recommended",
                    isSelected = settings.buttonPosition == ButtonPosition.BOTTOM_LEFT,
                    modifier = Modifier.weight(1f),
                    onClick = { appPreferences.setButtonPosition(ButtonPosition.BOTTOM_LEFT) }
                )

                PositionChoiceChip(
                    title = "Bottom-Right",
                    subtitle = "Standard",
                    isSelected = settings.buttonPosition == ButtonPosition.BOTTOM_RIGHT,
                    modifier = Modifier.weight(1f),
                    onClick = { appPreferences.setButtonPosition(ButtonPosition.BOTTOM_RIGHT) }
                )
            }

            HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)

            // Button Size Slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Button Size",
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.sp
                    )
                    Text(
                        text = "${settings.buttonSizeDp} dp",
                        color = IndigoSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = settings.buttonSizeDp.toFloat(),
                    onValueChange = { appPreferences.setButtonSizeDp(it.toInt()) },
                    valueRange = 48f..72f,
                    steps = 5,
                    colors = SliderDefaults.colors(
                        thumbColor = IndigoPrimary,
                        activeTrackColor = IndigoPrimary,
                        inactiveTrackColor = DarkSurfaceVariant
                    )
                )
            }

            HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)

            // Auto-Dismiss Timeout Slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Auto-Dismiss Timeout",
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.sp
                    )
                    Text(
                        text = "${settings.overlayTimeoutMs / 1000}s",
                        color = IndigoSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = (settings.overlayTimeoutMs / 1000).toFloat(),
                    onValueChange = { appPreferences.setOverlayTimeoutMs((it.toLong()) * 1000L) },
                    valueRange = 2f..8f,
                    steps = 5,
                    colors = SliderDefaults.colors(
                        thumbColor = IndigoPrimary,
                        activeTrackColor = IndigoPrimary,
                        inactiveTrackColor = DarkSurfaceVariant
                    )
                )
            }

            HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)

            // Haptic Vibration Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Haptic Vibration",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Vibrate softly on rotation button tap",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }

                Switch(
                    checked = settings.hapticFeedback,
                    onCheckedChange = { appPreferences.setHapticFeedback(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = IndigoPrimary
                    )
                )
            }
        }
    }
}

@Composable
fun PositionChoiceChip(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) IndigoPrimary.copy(alpha = 0.15f) else DarkSurfaceVariant)
            .border(
                1.5.dp,
                if (isSelected) IndigoPrimary else DarkBorder,
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                color = if (isSelected) Color.White else Color(0xFF94A3B8),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = if (isSelected) IndigoSecondary else Color(0xFF64748B),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun RotationBehaviorCard(
    settings: AppSettings,
    appPreferences: AppPreferences
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Rotation Engine",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            // Direct System Setting option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { appPreferences.setRotationMode(RotationMode.SYSTEM_SETTING) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = settings.rotationMode == RotationMode.SYSTEM_SETTING,
                    onClick = { appPreferences.setRotationMode(RotationMode.SYSTEM_SETTING) },
                    colors = RadioButtonDefaults.colors(selectedColor = IndigoPrimary)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Direct System Rotation (Recommended)",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Instantly updates USER_ROTATION while keeping auto-rotate locked.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
            }

            // Quick Pulse option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { appPreferences.setRotationMode(RotationMode.QUICK_PULSE) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = settings.rotationMode == RotationMode.QUICK_PULSE,
                    onClick = { appPreferences.setRotationMode(RotationMode.QUICK_PULSE) },
                    colors = RadioButtonDefaults.colors(selectedColor = IndigoPrimary)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Quick Auto-Rotate Pulse",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Unlocks auto-rotate for 400ms to trigger sensor orientation, then relocks.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
            }

            HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)

            // 180 Rotation Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Enable Upside-Down (180°)",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Allow rotating screen when holding phone upside down",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }

                Switch(
                    checked = settings.allow180Rotation,
                    onCheckedChange = { appPreferences.setAllow180Rotation(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = IndigoPrimary
                    )
                )
            }
        }
    }
}
