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
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import com.example.siderotate.theme.PaletteNavyDark
import com.example.siderotate.theme.PaletteNavyCard
import com.example.siderotate.theme.PaletteNavyBlue
import com.example.siderotate.theme.PaletteWarmCream
import com.example.siderotate.theme.TextPrimary
import com.example.siderotate.theme.TextSecondary
import com.example.siderotate.theme.TextMuted
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

            // Developer Section
            DeveloperSectionCard()

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
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
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
                            listOf(PaletteNavyBlue, PaletteNavyCard)
                        )
                    )
                    .border(1.dp, PaletteWarmCream.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_rotate_screen),
                    contentDescription = "App Icon",
                    tint = PaletteWarmCream,
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
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = PaletteNavyCard,
            border = BorderStroke(1.5.dp, PaletteWarmCream.copy(alpha = 0.85f)),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onCheckUpdateClick)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (isCheckingUpdate) "Checking..." else "v$currentVersion",
                    color = PaletteWarmCream,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = if (isCheckingUpdate) "⏳" else "↻",
                    color = PaletteWarmCream,
                    fontSize = 13.sp,
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
            .border(1.dp, PaletteWarmCream.copy(alpha = 0.6f), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = PaletteNavyCard)
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
                            .background(EmeraldSuccess)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NEW VERSION AVAILABLE",
                        color = EmeraldSuccess,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PaletteNavyBlue.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, PaletteWarmCream.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "v${updateInfo.cleanVersion}",
                        color = PaletteWarmCream,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = updateInfo.releaseTitle ?: "Side Rotate v${updateInfo.cleanVersion}",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            if (!updateInfo.changelogBody.isNullOrBlank()) {
                val cleanChangelog = updateInfo.changelogBody
                    .lines()
                    .filter { line ->
                        val trimmed = line.trim()
                        !trimmed.startsWith("#### Installation") &&
                                !trimmed.startsWith("1. Download") &&
                                !trimmed.startsWith("2. Open the file") &&
                                !trimmed.startsWith("3. Grant required")
                    }
                    .joinToString("\n")
                    .trim()
                    .removePrefix("### ")

                if (cleanChangelog.isNotBlank()) {
                    Text(
                        text = cleanChangelog,
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.sp,
                        maxLines = 6,
                        lineHeight = 18.sp
                    )
                }
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
                            color = PaletteWarmCream,
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
                        color = PaletteWarmCream,
                        trackColor = PaletteNavyBlue
                    )
                }
            } else if (downloadedApkFile != null) {
                Button(
                    onClick = { onInstallClick(downloadedApkFile) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldSuccess,
                        contentColor = Color.White
                    ),
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PaletteWarmCream,
                        contentColor = PaletteNavyDark
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Download & Install Update$sizeText",
                        color = PaletteNavyDark,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun sideRotateSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = PaletteWarmCream,
    checkedTrackColor = Color(0xFF244485),
    checkedBorderColor = PaletteNavyBlue,
    uncheckedThumbColor = Color(0xFF64748B),
    uncheckedTrackColor = Color(0xFF070E24),
    uncheckedBorderColor = Color(0xFF1B2D5A)
)

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
                    colors = sideRotateSwitchColors()
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

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = onTestClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PaletteWarmCream,
                    contentColor = PaletteNavyDark
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Preview",
                    color = PaletteNavyDark,
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
                        .background(PaletteNavyBlue)
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
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            OutlinedButton(
                onClick = onOpenOemSettings,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, PaletteNavyBlue)
            ) {
                Text(
                    text = "Open Background Settings for $brandName",
                    color = PaletteWarmCream,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun AppearanceSettingsCard(
    settings: AppSettings,
    appPreferences: AppPreferences
) {
    val context = LocalContext.current
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Button Size",
                            color = Color(0xFFCBD5E1),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Live preview • Updates dynamically in real time",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    // Live in-card scale preview circle
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(PaletteNavyBlue, PaletteNavyCard)
                                )
                            )
                            .border(1.dp, PaletteWarmCream, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_rotate_screen),
                            contentDescription = "Size Preview",
                            tint = PaletteWarmCream,
                            modifier = Modifier.size((settings.buttonSizeDp * 0.38f).dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = "${settings.buttonSizeDp} dp",
                        color = PaletteWarmCream,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Slider(
                    value = settings.buttonSizeDp.toFloat(),
                    onValueChange = { appPreferences.setButtonSizeDp(it.toInt()) },
                    onValueChangeFinished = {
                        if (RotationController.canDrawOverlays(context)) {
                            SideRotateService.testOverlay(context)
                        }
                    },
                    valueRange = 48f..72f,
                    steps = 5,
                    colors = SliderDefaults.colors(
                        thumbColor = PaletteWarmCream,
                        activeTrackColor = PaletteWarmCream,
                        inactiveTrackColor = PaletteNavyBlue
                    )
                )
            }

            HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)

            // Auto-Dismiss Timeout Slider
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Dismiss Timeout",
                            color = Color(0xFFCBD5E1),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Hides automatically or swipe away anytime",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = "${settings.overlayTimeoutMs / 1000}s",
                        color = PaletteWarmCream,
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
                        thumbColor = PaletteWarmCream,
                        activeTrackColor = PaletteWarmCream,
                        inactiveTrackColor = PaletteNavyBlue
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Haptic Vibration",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Vibrate softly on rotation button tap",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Switch(
                    checked = settings.hapticFeedback,
                    onCheckedChange = { appPreferences.setHapticFeedback(it) },
                    colors = sideRotateSwitchColors()
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Enable Upside-Down (180°)",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Allow rotating screen when holding phone upside down",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Switch(
                    checked = settings.allow180Rotation,
                    onCheckedChange = { appPreferences.setAllow180Rotation(it) },
                    colors = sideRotateSwitchColors()
                )
            }
        }
    }
}

@Composable
fun DeveloperSectionCard() {
    val context = LocalContext.current
    val facts = remember {
        listOf(
            "Nigel built SideRotate after seeing a reddit request for a tilt button on Vivo phones — dedicating an entire afternoon just to save someone a second of auto-rotate hassle!",
            "Legend has it Nigel spent an entire weekend building SideRotate instead of doing actual important work... Absolute dedication to solving random problems!",
            "Nigel believes that if smartphone manufacturers won't provide a clean feature, an open-source Android dev will build it within 48 hours.",
            "Did you know? Nigel built SideRotate, Caspian, and Lsync with pure bespoke aesthetics and zero bloated libraries.",
            "SideRotate was crafted with mathematical precision — because crooked rotation arrow tips were bothering Nigel way too much to leave alone!"
        )
    }

    var factIndex by remember { mutableIntStateOf(0) }

    val infiniteTransition = rememberInfiniteTransition(label = "float_avatar")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "avatar_y"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Section Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Text(
                text = "Developer",
                color = PaletteWarmCream,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(PaletteNavyBlue.copy(alpha = 0.5f))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Creator",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Developer Profile Card
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
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top row: Avatar + Identity
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Floating Avatar with badge
                    Box(
                        modifier = Modifier
                            .offset(y = floatOffset.dp)
                            .size(62.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.developer),
                            contentDescription = "NigelWeb Developer Profile",
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .border(2.dp, PaletteNavyBlue, CircleShape)
                                .align(Alignment.Center)
                        )
                        // Floating mini sparkle badge
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(PaletteNavyCard)
                                .border(1.dp, PaletteWarmCream, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "✨", fontSize = 10.sp)
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Developer Name & Github link
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "NigelWeb",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "• Lead Architect",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // GitHub chip link
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(PaletteNavyBlue.copy(alpha = 0.45f))
                                .border(1.dp, PaletteNavyBlue, RoundedCornerShape(8.dp))
                                .clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/code4nigel"))
                                    context.startActivity(intent)
                                }
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "🐙", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "github.com/code4nigel",
                                color = PaletteWarmCream,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)

                // Interactive Nigel Facts Bubble
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF070E24))
                        .border(1.dp, PaletteNavyBlue.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                        .clickable {
                            factIndex = (factIndex + 1) % facts.size
                        }
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "💡 Tap for a Nigel Fact!",
                            color = PaletteWarmCream,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${factIndex + 1}/${facts.size}",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }

                    Crossfade(
                        targetState = facts[factIndex],
                        animationSpec = tween(220),
                        label = "fact_crossfade"
                    ) { factText ->
                        Text(
                            text = factText,
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }
    }
}
