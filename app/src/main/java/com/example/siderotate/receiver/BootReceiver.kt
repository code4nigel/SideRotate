package com.example.siderotate.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.siderotate.core.RotationController
import com.example.siderotate.data.AppPreferences
import com.example.siderotate.service.SideRotateService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val prefs = AppPreferences(context)
            val settings = prefs.getSettingsSnapshot()

            if (settings.isServiceEnabled &&
                RotationController.canDrawOverlays(context) &&
                RotationController.canWriteSettings(context)
            ) {
                SideRotateService.startService(context)
            }
        }
    }
}
