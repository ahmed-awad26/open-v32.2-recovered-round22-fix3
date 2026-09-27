package com.opencontacts.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.net.toUri
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FloatingIncomingCallService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var dismissRunnable: Runnable? = null
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + serviceJob)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceJob.cancel()
        removeOverlay()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                val call = intent.toIncomingUiState()
                serviceScope.launch {
                    val settings = EntryPointAccessors.fromApplication(applicationContext, IncomingCallEntryPoint::class.java)
                        .appLockRepository()
                        .settings
                        .first()
                    showOverlay(call, settings)
                }
            }
            ACTION_HIDE -> stopSelfSafely()
        }
        return START_NOT_STICKY
    }

    private fun showOverlay(call: IncomingCallUiState, settings: com.opencontacts.core.crypto.AppLockSettings) {
        val view = overlayView ?: LayoutInflater.from(this)
            .inflate(com.opencontacts.app.R.layout.floating_incoming_call, null, false)
            .also { inflated ->
                val params = WindowManager.LayoutParams(
                    widthFor(settings.incomingCallWindowSize),
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.TYPE_PHONE
                    },
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = when (settings.incomingCallWindowPosition.uppercase()) {
                        "TOP" -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
                        "BOTTOM" -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                        else -> Gravity.CENTER
                    }
                    y = dp(28)
                }
                windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
                windowManager?.addView(inflated, params)
                overlayView = inflated
            }

        bind(view, call, settings)
    }

    private fun bind(view: View, call: IncomingCallUiState, settings: com.opencontacts.core.crypto.AppLockSettings) {
        val label = view.findViewById<TextView>(com.opencontacts.app.R.id.callLabel)
        val name = view.findViewById<TextView>(com.opencontacts.app.R.id.callName)
        val number = view.findViewById<TextView>(com.opencontacts.app.R.id.callNumber)
        val meta = view.findViewById<TextView>(com.opencontacts.app.R.id.callMeta)
        val avatar = view.findViewById<ImageView>(com.opencontacts.app.R.id.callAvatar)
        val backgroundPhoto = view.findViewById<ImageView>(com.opencontacts.app.R.id.backgroundPhoto)
        val backgroundScrim = view.findViewById<View>(com.opencontacts.app.R.id.backgroundScrim)
        val rootCard = view.findViewById<View>(com.opencontacts.app.R.id.rootCard)
        val dismissButton = view.findViewById<ImageButton>(com.opencontacts.app.R.id.closeButton)
        val dismissAction = view.findViewById<Button>(com.opencontacts.app.R.id.dismissButton)
        val declineAction = view.findViewById<Button>(com.opencontacts.app.R.id.declineButton)
        val answerAction = view.findViewById<Button>(com.opencontacts.app.R.id.answerButton)

        val bitmap = runCatching {
            call.photoUri?.let { uri -> contentResolver.openInputStream(uri.toUri())?.use(BitmapFactory::decodeStream) }
        }.getOrNull()
        val usePhotoBackground = settings.incomingCallPhotoBackgroundEnabled && settings.showPhotoInNotifications && bitmap != null

        label.text = if (call.blockMode.equals("SILENT_RING", ignoreCase = true)) {
            getString(com.opencontacts.app.R.string.incoming_call_label) + " • Silent"
        } else {
            getString(com.opencontacts.app.R.string.incoming_call_label)
        }
        name.text = call.displayName
        number.text = call.number
        number.visibility = if (settings.incomingCallShowNumber && call.number.isNotBlank()) View.VISIBLE else View.GONE
        val metaText = buildList {
            if (settings.incomingCallShowGroup) call.folderName?.takeIf { it.isNotBlank() }?.let(::add)
            if (settings.incomingCallShowTag) addAll(call.tags.take(if (settings.incomingCallCompactMode) 2 else 4))
        }.joinToString(" • ")
        meta.text = metaText
        meta.visibility = if (metaText.isBlank()) View.GONE else View.VISIBLE

        if (usePhotoBackground) {
            backgroundPhoto.setImageBitmap(bitmap)
            backgroundPhoto.visibility = View.VISIBLE
            backgroundScrim.visibility = View.VISIBLE
            backgroundScrim.alpha = (settings.incomingCallWindowTransparency.coerceIn(20, 100) / 100f)
            avatar.visibility = View.GONE
        } else {
            backgroundPhoto.visibility = View.GONE
            backgroundScrim.visibility = View.GONE
            avatar.visibility = View.VISIBLE
            if (bitmap != null) {
                avatar.setImageBitmap(bitmap)
            } else {
                avatar.setImageResource(com.opencontacts.app.R.drawable.ic_app_logo_vector)
            }
        }

        (rootCard.background as? GradientDrawable)?.apply {
            cornerRadius = dp(settings.callCardCornerRadius).toFloat()
            if (!usePhotoBackground) {
                setColor(Color.argb((255 * (settings.incomingCallWindowTransparency.coerceIn(20, 100) / 100f)).toInt(), 18, 24, 38))
            }
        }
        dismissAction.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F59E0B"))
        declineAction.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DC2626"))
        answerAction.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#16A34A"))

        dismissButton.setOnClickListener {
            dismissIncomingUi(this)
            stopSelfSafely()
        }
        dismissAction.setOnClickListener {
            dismissIncomingUi(this)
            stopSelfSafely()
        }
        declineAction.setOnClickListener {
            TelecomCallCoordinator.decline()
            dismissIncomingUi(this)
            stopSelfSafely()
        }
        answerAction.setOnClickListener {
            TelecomCallCoordinator.answer()
            dismissIncomingUi(this)
            stopSelfSafely()
            launchActiveCallControls(this, call.toActiveCallUiState(), forceShow = true)
        }
        rootCard.setOnClickListener {
            launchActiveCallControls(this, call.toActiveCallUiState(), forceShow = true)
        }

        dismissRunnable?.let(view::removeCallbacks)
        dismissRunnable = null
        if (settings.incomingCallAutoDismissSeconds > 0) {
            dismissRunnable = Runnable {
                dismissIncomingUi(this)
                stopSelfSafely()
            }.also { view.postDelayed(it, settings.incomingCallAutoDismissSeconds * 1000L) }
        }
    }

    private fun widthFor(size: String): Int {
        val compact = size.equals("COMPACT", ignoreCase = true)
        val displayMetrics = resources.displayMetrics
        val preferred = if (compact) dp(356) else dp(412)
        return minOf(preferred, displayMetrics.widthPixels - dp(28))
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        resources.displayMetrics,
    ).toInt()

    private fun stopSelfSafely() {
        removeOverlay()
        stopSelf()
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            dismissRunnable?.let(view::removeCallbacks)
            runCatching { windowManager?.removeView(view) }
        }
        dismissRunnable = null
        overlayView = null
        windowManager = null
    }

    companion object {
        private const val ACTION_SHOW = "com.opencontacts.app.action.SHOW_FLOATING_INCOMING_CALL"
        private const val ACTION_HIDE = "com.opencontacts.app.action.HIDE_FLOATING_INCOMING_CALL"

        fun show(context: Context, call: IncomingCallUiState) {
            val intent = Intent(context, FloatingIncomingCallService::class.java)
                .setAction(ACTION_SHOW)
                .putExtra("displayName", call.displayName)
                .putExtra("number", call.number)
                .putExtra("photoUri", call.photoUri)
                .putExtra("folderName", call.folderName)
                .putStringArrayListExtra("tags", ArrayList(call.tags))
                .putExtra("contactId", call.contactId)
                .putExtra("blockMode", call.blockMode)
            context.startService(intent)
        }

        fun hide(context: Context) {
            context.startService(Intent(context, FloatingIncomingCallService::class.java).setAction(ACTION_HIDE))
        }
    }
}

private fun Intent.toIncomingUiState(): IncomingCallUiState = IncomingCallUiState(
    displayName = getStringExtra("displayName").orEmpty(),
    number = getStringExtra("number").orEmpty(),
    photoUri = getStringExtra("photoUri"),
    folderName = getStringExtra("folderName"),
    tags = getStringArrayListExtra("tags")?.toList().orEmpty(),
    contactId = getStringExtra("contactId"),
    blockMode = getStringExtra("blockMode") ?: "NONE",
)

internal fun dismissFloatingIncomingCall(context: Context) {
    FloatingIncomingCallService.hide(context)
}
