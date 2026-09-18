package com.example.siderotate.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ButtonPosition {
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

enum class RotationMode {
    SYSTEM_SETTING, // Writes Settings.System.USER_ROTATION directly
    QUICK_PULSE     // Briefly enables auto-rotate to turn, then immediately locks
}

data class AppSettings(
    val isServiceEnabled: Boolean = true,
    val buttonPosition: ButtonPosition = ButtonPosition.BOTTOM_LEFT,
    val buttonSizeDp: Int = 58,
    val overlayTimeoutMs: Long = 4000L,
    val hapticFeedback: Boolean = true,
    val rotationMode: RotationMode = RotationMode.SYSTEM_SETTING,
    val allow180Rotation: Boolean = false,
    val marginHorizontalDp: Int = 20,
    val marginVerticalDp: Int = 40
)

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        val posName = prefs.getString(KEY_BUTTON_POSITION, ButtonPosition.BOTTOM_LEFT.name)
        val pos = runCatching { ButtonPosition.valueOf(posName ?: "") }.getOrDefault(ButtonPosition.BOTTOM_LEFT)

        val modeName = prefs.getString(KEY_ROTATION_MODE, RotationMode.SYSTEM_SETTING.name)
        val mode = runCatching { RotationMode.valueOf(modeName ?: "") }.getOrDefault(RotationMode.SYSTEM_SETTING)

        return AppSettings(
            isServiceEnabled = prefs.getBoolean(KEY_SERVICE_ENABLED, true),
            buttonPosition = pos,
            buttonSizeDp = prefs.getInt(KEY_BUTTON_SIZE_DP, 58),
            overlayTimeoutMs = prefs.getLong(KEY_OVERLAY_TIMEOUT_MS, 4000L),
            hapticFeedback = prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true),
            rotationMode = mode,
            allow180Rotation = prefs.getBoolean(KEY_ALLOW_180, false),
            marginHorizontalDp = prefs.getInt(KEY_MARGIN_H, 20),
            marginVerticalDp = prefs.getInt(KEY_MARGIN_V, 40)
        )
    }

    fun setServiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
        _settings.value = _settings.value.copy(isServiceEnabled = enabled)
    }

    fun setButtonPosition(position: ButtonPosition) {
        prefs.edit().putString(KEY_BUTTON_POSITION, position.name).apply()
        _settings.value = _settings.value.copy(buttonPosition = position)
    }

    fun setButtonSizeDp(sizeDp: Int) {
        prefs.edit().putInt(KEY_BUTTON_SIZE_DP, sizeDp).apply()
        _settings.value = _settings.value.copy(buttonSizeDp = sizeDp)
    }

    fun setOverlayTimeoutMs(timeoutMs: Long) {
        prefs.edit().putLong(KEY_OVERLAY_TIMEOUT_MS, timeoutMs).apply()
        _settings.value = _settings.value.copy(overlayTimeoutMs = timeoutMs)
    }

    fun setHapticFeedback(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTIC_FEEDBACK, enabled).apply()
        _settings.value = _settings.value.copy(hapticFeedback = enabled)
    }

    fun setRotationMode(mode: RotationMode) {
        prefs.edit().putString(KEY_ROTATION_MODE, mode.name).apply()
        _settings.value = _settings.value.copy(rotationMode = mode)
    }

    fun setAllow180Rotation(allow: Boolean) {
        prefs.edit().putBoolean(KEY_ALLOW_180, allow).apply()
        _settings.value = _settings.value.copy(allow180Rotation = allow)
    }

    fun getSettingsSnapshot(): AppSettings = _settings.value

    companion object {
        private const val PREFS_NAME = "side_rotate_prefs"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_BUTTON_POSITION = "button_position"
        private const val KEY_BUTTON_SIZE_DP = "button_size_dp"
        private const val KEY_OVERLAY_TIMEOUT_MS = "overlay_timeout_ms"
        private const val KEY_HAPTIC_FEEDBACK = "haptic_feedback"
        private const val KEY_ROTATION_MODE = "rotation_mode"
        private const val KEY_ALLOW_180 = "allow_180"
        private const val KEY_MARGIN_H = "margin_h"
        private const val KEY_MARGIN_V = "margin_v"
    }
}
