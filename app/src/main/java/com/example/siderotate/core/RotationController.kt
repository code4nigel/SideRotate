package com.example.siderotate.core

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.OrientationEventListener
import android.view.Surface
import com.example.siderotate.data.RotationMode

object RotationController {

    fun canWriteSettings(context: Context): Boolean {
        return Settings.System.canWrite(context)
    }

    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * Checks whether auto-rotate is currently turned off (i.e. rotation is locked).
     */
    fun isAutoRotateLocked(context: Context): Boolean {
        return try {
            val autoRotate = Settings.System.getInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                0
            )
            autoRotate == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Retrieves the current rotation index locked in system settings.
     * 0 = ROTATION_0 (Portrait)
     * 1 = ROTATION_90 (Landscape)
     * 2 = ROTATION_180 (Reverse Portrait)
     * 3 = ROTATION_270 (Reverse Landscape)
     */
    fun getCurrentUserRotation(context: Context): Int {
        return try {
            Settings.System.getInt(
                context.contentResolver,
                Settings.System.USER_ROTATION,
                Surface.ROTATION_0
            )
        } catch (e: Exception) {
            Surface.ROTATION_0
        }
    }

    /**
     * Maps physical sensor orientation degrees (0..359) to Android Surface.ROTATION_* constant.
     * 
     * In Android:
     * - 0 deg (device upright portrait) -> Surface.ROTATION_0
     * - 90 deg (device tilted 90 deg clockwise, left side is UP) -> Surface.ROTATION_270
     * - 180 deg (device upside down) -> Surface.ROTATION_180 (if allowed, else Surface.ROTATION_0)
     * - 270 deg (device tilted 90 deg counter-clockwise, right side is UP) -> Surface.ROTATION_90
     */
    fun mapOrientationDegreesToSurface(
        orientationDegrees: Int,
        allow180: Boolean = false,
        toleranceDegrees: Int = 40
    ): Int? {
        if (orientationDegrees == OrientationEventListener.ORIENTATION_UNKNOWN) {
            return null
        }

        // Upright Portrait (around 0 deg)
        if (orientationDegrees in (360 - toleranceDegrees)..359 || orientationDegrees in 0..toleranceDegrees) {
            return Surface.ROTATION_0
        }

        // Phone tilted clockwise 90 deg (left side is facing UP) -> target Surface.ROTATION_270
        if (orientationDegrees in (90 - toleranceDegrees)..(90 + toleranceDegrees)) {
            return Surface.ROTATION_270
        }

        // Upside down Portrait (around 180 deg)
        if (orientationDegrees in (180 - toleranceDegrees)..(180 + toleranceDegrees)) {
            return if (allow180) Surface.ROTATION_180 else Surface.ROTATION_0
        }

        // Phone tilted counter-clockwise 90 deg (right side is facing UP) -> target Surface.ROTATION_90
        if (orientationDegrees in (270 - toleranceDegrees)..(270 + toleranceDegrees)) {
            return Surface.ROTATION_90
        }

        return null
    }

    /**
     * Determines optimal landscape rotation based on last known physical orientation degrees.
     * If held with left edge up (around 90 deg) -> ROTATION_270.
     * If held with right edge up (around 270 deg) -> ROTATION_90.
     * Default when upright or flat -> ROTATION_90.
     */
    fun getOptimalLandscapeRotation(orientationDegrees: Int?): Int {
        if (orientationDegrees == null || orientationDegrees == OrientationEventListener.ORIENTATION_UNKNOWN) {
            return Surface.ROTATION_90
        }
        return if (orientationDegrees in 45..180) {
            Surface.ROTATION_270
        } else {
            Surface.ROTATION_90
        }
    }

    /**
     * Applies the requested rotation to the entire system.
     */
    fun applyRotation(
        context: Context,
        targetRotation: Int,
        mode: RotationMode = RotationMode.SYSTEM_SETTING,
        onComplete: (() -> Unit)? = null
    ): Boolean {
        if (!canWriteSettings(context)) {
            return false
        }

        return try {
            when (mode) {
                RotationMode.SYSTEM_SETTING -> {
                    // Ensure accelerometer rotation is locked
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.ACCELEROMETER_ROTATION,
                        0
                    )
                    // Set target user rotation
                    val success = Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.USER_ROTATION,
                        targetRotation
                    )
                    onComplete?.invoke()
                    success
                }
                RotationMode.QUICK_PULSE -> {
                    // Temporarily unlock auto-rotate for a moment, then restore lock with target rotation
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.ACCELEROMETER_ROTATION,
                        1
                    )
                    Handler(Looper.getMainLooper()).postDelayed({
                        try {
                            Settings.System.putInt(
                                context.contentResolver,
                                Settings.System.ACCELEROMETER_ROTATION,
                                0
                            )
                            Settings.System.putInt(
                                context.contentResolver,
                                Settings.System.USER_ROTATION,
                                targetRotation
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        onComplete?.invoke()
                    }, 450)
                    true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
