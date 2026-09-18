package com.example.siderotate.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.example.siderotate.R
import com.example.siderotate.data.AppSettings
import com.example.siderotate.data.ButtonPosition

class OverlayWindowManager(private val context: Context) {

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var rootView: FrameLayout? = null
    private var buttonView: ImageView? = null
    private var isShowing = false
    private var currentTargetRotation: Int = 0
    private var onRotateClickListener: ((targetRotation: Int) -> Unit)? = null

    private val autoDismissRunnable = Runnable {
        hideOverlay()
    }

    fun setOnRotateClickListener(listener: (targetRotation: Int) -> Unit) {
        this.onRotateClickListener = listener
    }

    @SuppressLint("ClickableViewAccessibility")
    fun showOverlay(targetRotation: Int, settings: AppSettings) {
        if (!Settings.canDrawOverlays(context)) {
            return
        }

        this.currentTargetRotation = targetRotation
        handler.removeCallbacks(autoDismissRunnable)

        if (isShowing && rootView != null) {
            // Already showing, just refresh timeout
            handler.postDelayed(autoDismissRunnable, settings.overlayTimeoutMs)
            return
        }

        val density = context.resources.displayMetrics.density
        val sizePx = (settings.buttonSizeDp * density).toInt()
        val marginXPx = (settings.marginHorizontalDp * density).toInt()
        val marginYPx = (settings.marginVerticalDp * density).toInt()

        val gravity = when (settings.buttonPosition) {
            ButtonPosition.BOTTOM_LEFT -> Gravity.BOTTOM or Gravity.START
            ButtonPosition.BOTTOM_RIGHT -> Gravity.BOTTOM or Gravity.END
        }

        val windowLayoutParams = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            this.x = marginXPx
            this.y = marginYPx
        }

        val root = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }

        val button = ImageView(context).apply {
            this.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            background = ContextCompat.getDrawable(context, R.drawable.bg_overlay_button)
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_rotate_screen))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = (14 * density).toInt()
            setPadding(pad, pad, pad, pad)
            elevation = 12f * density

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(100).start()
                    }
                    MotionEvent.ACTION_UP -> {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                        performTapHaptic(settings.hapticFeedback)
                        onRotateClickListener?.invoke(currentTargetRotation)
                        hideOverlay()
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                    }
                }
                true
            }
        }

        root.addView(button)
        this.rootView = root
        this.buttonView = button

        try {
            windowManager.addView(root, windowLayoutParams)
            isShowing = true

            // Smooth entrance animation
            button.alpha = 0f
            button.scaleX = 0.4f
            button.scaleY = 0.4f
            button.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(260)
                .setInterpolator(OvershootInterpolator(1.3f))
                .start()

            // Schedule auto-hide
            handler.postDelayed(autoDismissRunnable, settings.overlayTimeoutMs)
        } catch (e: Exception) {
            e.printStackTrace()
            isShowing = false
        }
    }

    fun hideOverlay() {
        handler.removeCallbacks(autoDismissRunnable)
        val currentRoot = rootView ?: return
        val currentButton = buttonView

        if (!isShowing) return
        isShowing = false

        if (currentButton != null) {
            currentButton.animate()
                .alpha(0f)
                .scaleX(0.5f)
                .scaleY(0.5f)
                .setDuration(180)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        try {
                            if (currentRoot.isAttachedToWindow) {
                                windowManager.removeView(currentRoot)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                })
                .start()
        } else {
            try {
                if (currentRoot.isAttachedToWindow) {
                    windowManager.removeView(currentRoot)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        rootView = null
        buttonView = null
    }

    private fun performTapHaptic(enabled: Boolean) {
        if (!enabled) return
        try {
            buttonView?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                ?: run {
                    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                        manager?.defaultVibrator
                    } else {
                        @Suppress("DEPRECATION")
                        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(35)
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isOverlayShowing(): Boolean = isShowing
}
