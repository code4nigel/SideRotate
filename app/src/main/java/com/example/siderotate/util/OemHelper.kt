package com.example.siderotate.util

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

enum class OemType {
    VIVO,
    TECNO,
    SAMSUNG,
    XIAOMI,
    GENERIC
}

object OemHelper {

    fun getDeviceOem(): OemType {
        val man = Build.MANUFACTURER.lowercase()
        return when {
            man.contains("vivo") -> OemType.VIVO
            man.contains("tecno") || man.contains("transsion") || man.contains("infinix") || man.contains("itel") -> OemType.TECNO
            man.contains("samsung") -> OemType.SAMSUNG
            man.contains("xiaomi") || man.contains("redmi") || man.contains("poco") -> OemType.XIAOMI
            else -> OemType.GENERIC
        }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                openAppDetailsSettings(context)
            }
        }
    }

    fun openAppDetailsSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun openOverlaySettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun openWriteSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Opens manufacturer-specific Autostart or Background Management screen.
     */
    fun openOemBackgroundSettings(context: Context): Boolean {
        val oem = getDeviceOem()
        val intents = mutableListOf<Intent>()

        when (oem) {
            OemType.VIVO -> {
                // Vivo Funtouch OS / OriginOS
                intents.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.iqoo.secure",
                            "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                        )
                    )
                )
                intents.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.PurviewTabActivity"
                        )
                    )
                )
                intents.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.iqoo.secure",
                            "com.iqoo.secure.MainGuideActivity"
                        )
                    )
                )
            }
            OemType.TECNO -> {
                // Tecno HiOS / Infinix XOS (Phone Master)
                intents.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.transsion.phonemaster",
                            "com.transsion.phonemaster.MainActivity"
                        )
                    )
                )
                intents.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.transsion.phonemanager",
                            "com.transsion.phonemanager.MainActivity"
                        )
                    )
                )
            }
            OemType.SAMSUNG -> {
                // Samsung One UI Device Care -> Battery
                intents.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.samsung.android.lool",
                            "com.samsung.android.sm.ui.battery.BatteryActivity"
                        )
                    )
                )
                intents.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.samsung.android.sm",
                            "com.samsung.android.sm.ui.battery.BatteryActivity"
                        )
                    )
                )
            }
            OemType.XIAOMI -> {
                intents.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"
                        )
                    )
                )
            }
            OemType.GENERIC -> {}
        }

        for (intent in intents) {
            try {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return true
            } catch (ignored: Exception) {}
        }

        // Fallback to app details
        openAppDetailsSettings(context)
        return false
    }

    fun getOemGuidanceText(): String {
        return when (getDeviceOem()) {
            OemType.VIVO -> "Vivo Funtouch OS requires enabling 'Autostart' and setting Battery to 'High background power consumption' so the rotation listener isn't frozen when using full-screen gestures."
            OemType.TECNO -> "Tecno HiOS requires enabling 'Auto-start management' in Phone Master so the tilt sensor stays active in the background."
            OemType.SAMSUNG -> "Samsung One UI requires disabling 'Put unused apps to sleep' or adding Side Rotate to 'Never sleeping apps'."
            OemType.XIAOMI -> "MIUI / HyperOS requires enabling 'Autostart' and setting Battery Saver to 'No restrictions'."
            OemType.GENERIC -> "Ensure battery optimization is disabled so Android does not kill the background tilt sensor."
        }
    }
}
