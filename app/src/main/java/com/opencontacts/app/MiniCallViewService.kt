package com.opencontacts.app

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * MiniCallViewService — a lightweight SYSTEM_ALERT_WINDOW overlay that appears
 * when the user leaves the active call screen while a call is still active.
 *
 * Responsibilities:
 *  - Show caller name/number, call duration, mute/speaker toggles
 *  - Double-tap to return to full call controls
 *  - Drag to reposition
 *  - Dismiss automatically when call ends (TelecomCallCoordinator.clearAll())
 *  - Respect miniCallViewEnabled, opacity, size, and position settings
 *
 * Falls back to no-op when SYSTEM_ALERT_WINDOW is not granted; in that case the
 * persistent ongoing-call notification acts as the mini view fallback.
 */
class MiniCallViewService : Service() {

    private var windowManager: WindowManager? = null
    private var rootView: View? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var callObserverJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showMini()
            ACTION_HIDE -> stopSelfSafely()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        callObserverJob?.cancel()
        scope.cancel()
        removeView()
        super.onDestroy()
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun showMini() {
        if (rootView != null) return // already showing

        // Check overlay permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
            // Gracefully degrade — the ongoing notification handles this case
            stopSelf()
            return
        }

        scope.launch {
            val settings = runCatching {
                EntryPointAccessors.fromApplication(applicationContext, IncomingCallEntryPoint::class.java)
                    .appLockRepository().settings.first()
            }.getOrDefault(com.opencontacts.core.crypto.AppLockSettings.DEFAULT)
            showMiniWithSettings(settings)
        }
    }

    private fun showMiniWithSettings(settings: com.opencontacts.core.crypto.AppLockSettings) {
        if (rootView != null) return // a second call arrived while settings were loading

        if (!settings.miniCallViewEnabled) {
            stopSelf()
            return
        }

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val view = buildMiniView(settings)
        rootView = view

        val size = if (settings.miniCallViewSize.equals("EXPANDED", ignoreCase = true)) dp(280) else dp(220)
        val gravity = when (settings.miniCallViewPosition.uppercase()) {
            "TOP_END" -> Gravity.TOP or Gravity.END
            "BOTTOM_START" -> Gravity.BOTTOM or Gravity.START
            else -> Gravity.BOTTOM or Gravity.END
        }

        val params = WindowManager.LayoutParams(
            size,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT,
        ).apply {
            this.gravity = gravity
            x = dp(12)
            y = dp(80)
        }

        // Drag to reposition
        var startX = 0; var startY = 0; var initX = 0; var initY = 0
        view.setOnTouchListener(object : View.OnTouchListener {
            private var lastDownTime = 0L
            private var tapCount = 0

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX.toInt()
                        startY = event.rawY.toInt()
                        initX = params.x
                        initY = params.y
                        val now = System.currentTimeMillis()
                        if (now - lastDownTime < 400) tapCount++ else tapCount = 1
                        lastDownTime = now
                        if (tapCount >= 2) {
                            tapCount = 0
                            launchActiveCallControls(applicationContext)
                            stopSelfSafely()
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX.toInt() - startX
                        val dy = event.rawY.toInt() - startY
                        params.x = initX + dx
                        params.y = initY + dy
                        runCatching { wm.updateViewLayout(view, params) }
                    }
                }
                return true
            }
        })

        wm.addView(view, params)

        // Observe call state — auto-remove when call ends
        callObserverJob = scope.launch {
            TelecomCallCoordinator.activeCall.collect { activeCall ->
                if (activeCall == null) {
                    stopSelfSafely()
                }
            }
        }

        // Live-update mute/speaker state
        scope.launch {
            TelecomCallCoordinator.telecomState.collect { state ->
                val muteBtn = view.findViewWithTag<ImageButton>("btn_mute") ?: return@collect
                val speakerBtn = view.findViewWithTag<ImageButton>("btn_speaker") ?: return@collect
                muteBtn.alpha = if (state.isMuted) 1f else 0.45f
                speakerBtn.alpha = if (state.isSpeakerOn) 1f else 0.45f
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun buildMiniView(settings: com.opencontacts.core.crypto.AppLockSettings): View {
        val ctx = this
        val alpha = settings.miniCallViewOpacity.coerceIn(40, 100) / 100f
        val activeCall = TelecomCallCoordinator.activeCall.value

        val container = FrameLayout(ctx).apply {
            background = ContextCompat.getDrawable(ctx, android.R.drawable.dialog_holo_dark_frame)
            alpha = alpha
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }

        val root = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            container.addView(this)
        }

        // Name/number row
        val nameText = TextView(ctx).apply {
            text = activeCall?.displayName?.ifBlank { activeCall.number } ?: "Active call"
            textSize = 13f
            setTextColor(android.graphics.Color.WHITE)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        root.addView(nameText)

        // Duration chronometer
        val chrono = Chronometer(ctx).apply {
            base = SystemClock.elapsedRealtime()
            start()
            textSize = 11f
            setTextColor(android.graphics.Color.LTGRAY)
        }
        root.addView(chrono)

        // Controls row
        val controls = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val muteBtn = ImageButton(ctx).apply {
            tag = "btn_mute"
            setImageResource(android.R.drawable.ic_lock_silent_mode)
            background = null
            setColorFilter(android.graphics.Color.WHITE)
            alpha = if (TelecomCallCoordinator.telecomState.value.isMuted) 1f else 0.45f
            setPadding(dp(6), dp(4), dp(6), dp(4))
            setOnClickListener {
                val muted = !TelecomCallCoordinator.telecomState.value.isMuted
                TelecomCallCoordinator.setMuted(muted)
            }
        }

        val speakerBtn = ImageButton(ctx).apply {
            tag = "btn_speaker"
            setImageResource(android.R.drawable.ic_lock_silent_mode_off)
            background = null
            setColorFilter(android.graphics.Color.WHITE)
            alpha = if (TelecomCallCoordinator.telecomState.value.isSpeakerOn) 1f else 0.45f
            setPadding(dp(6), dp(4), dp(6), dp(4))
            setOnClickListener {
                val speaker = !TelecomCallCoordinator.telecomState.value.isSpeakerOn
                TelecomCallCoordinator.setSpeaker(speaker)
            }
        }

        controls.addView(muteBtn)
        controls.addView(speakerBtn)

        val hint = TextView(ctx).apply {
            text = "  ×2 expand"
            textSize = 9f
            setTextColor(android.graphics.Color.GRAY)
        }
        controls.addView(hint)

        root.addView(controls)
        return container
    }

    private fun removeView() {
        runCatching { rootView?.let { windowManager?.removeView(it) } }
        rootView = null
    }

    private fun stopSelfSafely() {
        removeView()
        stopSelf()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        const val ACTION_SHOW = "com.opencontacts.app.MINI_CALL_SHOW"
        const val ACTION_HIDE = "com.opencontacts.app.MINI_CALL_HIDE"

        fun show(context: Context) {
            val intent = Intent(context, MiniCallViewService::class.java).setAction(ACTION_SHOW)
            runCatching { context.startService(intent) }
        }

        fun hide(context: Context) {
            val intent = Intent(context, MiniCallViewService::class.java).setAction(ACTION_HIDE)
            runCatching { context.startService(intent) }
        }
    }
}

/** Launches the full active call controls screen from any context. */
private fun launchActiveCallControls(context: Context) {
    runCatching {
        context.startActivity(
            Intent(context, ActiveCallControlsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
    }
}
