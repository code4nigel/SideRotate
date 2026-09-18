package com.example.siderotate.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.OrientationEventListener
import android.view.Surface
import androidx.core.app.NotificationCompat
import com.example.siderotate.MainActivity
import com.example.siderotate.R
import com.example.siderotate.core.RotationController
import com.example.siderotate.data.AppPreferences
import com.example.siderotate.overlay.OverlayWindowManager

class SideRotateService : Service() {

    private lateinit var appPreferences: AppPreferences
    private lateinit var overlayWindowManager: OverlayWindowManager
    private var orientationListener: OrientationEventListener? = null

    private var autoRotateObserver: ContentObserver? = null
    private var screenReceiver: BroadcastReceiver? = null

    private var isScreenInteractive = true
    private var lastKnownOrientation: Int? = null

    override fun onCreate() {
        super.onCreate()
        appPreferences = AppPreferences(this)
        overlayWindowManager = OverlayWindowManager(this)

        overlayWindowManager.setOnRotateClickListener { targetRotation ->
            val settings = appPreferences.getSettingsSnapshot()
            RotationController.applyRotation(this, targetRotation, settings.rotationMode) {
                updateNotification()
            }
        }

        setupNotificationChannel()
        setupAutoRotateObserver()
        setupScreenReceiver()
        setupOrientationListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val settings = appPreferences.getSettingsSnapshot()

        if (action == ACTION_STOP) {
            appPreferences.setServiceEnabled(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (action == ACTION_ROTATE_PORTRAIT) {
            RotationController.applyRotation(this, Surface.ROTATION_0, settings.rotationMode) {
                updateNotification()
            }
            overlayWindowManager.hideOverlay()
            return START_STICKY
        }

        if (action == ACTION_ROTATE_LANDSCAPE) {
            val current = RotationController.getCurrentUserRotation(this)
            val target = if (current == Surface.ROTATION_90) {
                Surface.ROTATION_270
            } else if (current == Surface.ROTATION_270) {
                Surface.ROTATION_90
            } else {
                RotationController.getOptimalLandscapeRotation(lastKnownOrientation)
            }
            RotationController.applyRotation(this, target, settings.rotationMode) {
                updateNotification()
            }
            overlayWindowManager.hideOverlay()
            return START_STICKY
        }

        if (action == ACTION_TEST_OVERLAY) {
            val current = RotationController.getCurrentUserRotation(this)
            val testTarget = if (current == Surface.ROTATION_0) {
                RotationController.getOptimalLandscapeRotation(lastKnownOrientation)
            } else {
                Surface.ROTATION_0
            }
            overlayWindowManager.showOverlay(testTarget, settings)
            return START_STICKY
        }

        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        if (settings.isServiceEnabled && isScreenInteractive) {
            orientationListener?.enable()
        }

        return START_STICKY
    }

    private fun updateNotification() {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, createNotification())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Side Rotate Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Side Rotate tilt detector running"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Quick rotate to portrait
        val portraitIntent = Intent(this, SideRotateService::class.java).apply {
            action = ACTION_ROTATE_PORTRAIT
        }
        val portraitPendingIntent = PendingIntent.getService(
            this,
            2,
            portraitIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Quick rotate to landscape
        val landscapeIntent = Intent(this, SideRotateService::class.java).apply {
            action = ACTION_ROTATE_LANDSCAPE
        }
        val landscapePendingIntent = PendingIntent.getService(
            this,
            3,
            landscapeIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, SideRotateService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val currentRot = RotationController.getCurrentUserRotation(this)
        val rotDesc = when (currentRot) {
            Surface.ROTATION_0 -> "Portrait"
            Surface.ROTATION_90 -> "Landscape"
            Surface.ROTATION_270 -> "Landscape (Inverted)"
            Surface.ROTATION_180 -> "Portrait (Inverted)"
            else -> "Portrait"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_rotate)
            .setContentTitle("Side Rotate Active")
            .setContentText("Current: $rotDesc • Auto-rotate locked")
            .setContentIntent(openAppPendingIntent)
            .addAction(R.drawable.ic_rotate_screen, "Portrait", portraitPendingIntent)
            .addAction(R.drawable.ic_rotate_screen, "Landscape", landscapePendingIntent)
            .addAction(0, "Turn Off", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun setupOrientationListener() {
        orientationListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                lastKnownOrientation = orientation

                val settings = appPreferences.getSettingsSnapshot()
                if (!settings.isServiceEnabled) {
                    overlayWindowManager.hideOverlay()
                    return
                }

                // If auto-rotate is ON (not locked), hide overlay and return
                if (!RotationController.isAutoRotateLocked(this@SideRotateService)) {
                    if (overlayWindowManager.isOverlayShowing()) {
                        overlayWindowManager.hideOverlay()
                    }
                    return
                }

                val currentRotation = RotationController.getCurrentUserRotation(this@SideRotateService)
                val targetRotation = RotationController.mapOrientationDegreesToSurface(
                    orientation,
                    settings.allow180Rotation
                )

                if (targetRotation != null && targetRotation != currentRotation) {
                    // Phone physically tilted to an orientation different from current display
                    overlayWindowManager.showOverlay(targetRotation, settings)
                } else if (targetRotation != null && targetRotation == currentRotation) {
                    // Phone returned to current orientation
                    if (overlayWindowManager.isOverlayShowing()) {
                        overlayWindowManager.hideOverlay()
                    }
                }
            }
        }
    }

    private fun setupAutoRotateObserver() {
        val uri = Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION)
        autoRotateObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                if (!RotationController.isAutoRotateLocked(this@SideRotateService)) {
                    overlayWindowManager.hideOverlay()
                }
            }
        }
        contentResolver.registerContentObserver(uri, false, autoRotateObserver!!)
    }

    private fun setupScreenReceiver() {
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        isScreenInteractive = false
                        orientationListener?.disable()
                        overlayWindowManager.hideOverlay()
                    }
                    Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                        isScreenInteractive = true
                        val settings = appPreferences.getSettingsSnapshot()
                        if (settings.isServiceEnabled) {
                            orientationListener?.enable()
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        orientationListener?.disable()
        overlayWindowManager.hideOverlay()

        autoRotateObserver?.let {
            contentResolver.unregisterContentObserver(it)
        }
        screenReceiver?.let {
            unregisterReceiver(it)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "side_rotate_foreground_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.siderotate.action.START"
        const val ACTION_STOP = "com.example.siderotate.action.STOP"
        const val ACTION_ROTATE_PORTRAIT = "com.example.siderotate.action.ROTATE_PORTRAIT"
        const val ACTION_ROTATE_LANDSCAPE = "com.example.siderotate.action.ROTATE_LANDSCAPE"
        const val ACTION_TEST_OVERLAY = "com.example.siderotate.action.TEST_OVERLAY"

        fun startService(context: Context) {
            val intent = Intent(context, SideRotateService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, SideRotateService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun testOverlay(context: Context) {
            val intent = Intent(context, SideRotateService::class.java).apply {
                action = ACTION_TEST_OVERLAY
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
