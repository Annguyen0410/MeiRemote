package com.zhiend.meiremote.bubble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.zhiend.meiremote.MainActivity
import com.zhiend.meiremote.MeiApplication
import com.zhiend.meiremote.R
import com.zhiend.meiremote.data.AppState
import com.zhiend.meiremote.data.ConnStatus
import com.zhiend.meiremote.ui.theme.DarkMeiColors
import com.zhiend.meiremote.ui.theme.LightMeiColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Floating quick-note bubble. Shows a small draggable circle over any app;
 * tapping it opens the 75%-sized quick-note overlay ([BubbleOverlayActivity]).
 *
 * The little dot on the bubble mirrors the desktop connection state in real time:
 *  - green  = desktop online
 *  - red    = desktop offline
 *  - amber  = checking
 *  - grey   = unknown / not configured
 * The phone vibrates briefly whenever the connection comes back online.
 *
 * Drag the bubble onto the ✕ target at the bottom-center to dismiss it
 * (this also switches the bubble off in Settings so it stays gone).
 */
class BubbleService : Service() {

    private val state: AppState by lazy { (application as MeiApplication).state }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var dotView: View? = null
    private var deleteZoneView: View? = null

    private val handler = Handler(Looper.getMainLooper())
    private var tickCount = 0
    private var wasOnline = false

    private val layoutParams: WindowManager.LayoutParams by lazy {
        WindowManager.LayoutParams(
            dp(56),
            dp(56),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(220)
        }
    }

    private val deleteZoneParams: WindowManager.LayoutParams by lazy {
        WindowManager.LayoutParams(
            dp(72),
            dp(72),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(28)
        }
    }

    private val statusTick = object : Runnable {
        override fun run() {
            updateStatusUi()
            tickCount++
            // The app's own auto-sync keeps the connection state fresh; only
            // ping from here when auto-sync is off, and even then rarely.
            if (!state.prefs.autoSync && tickCount % STATUS_POLL_EVERY_N_TICKS == 0) {
                refreshConnection()
            }
            handler.postDelayed(this, STATUS_TICK_MS)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> pauseStatusLoop()
                Intent.ACTION_SCREEN_ON -> resumeStatusLoop()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        try {
            registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF).apply {
                addAction(Intent.ACTION_SCREEN_ON)
            })
        } catch (_: Exception) {
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Settings.canDrawOverlays(this)) {
            showBubble()
        } else {
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        pauseStatusLoop()
        runCatching { unregisterReceiver(screenReceiver) }
        scope.cancel()
        hideDeleteZone()
        bubbleView?.let { runCatching { windowManager?.removeView(it) } }
        bubbleView = null
        dotView = null
        super.onDestroy()
    }

    private fun showBubble() {
        if (bubbleView != null) return

        val accent = if (state.themeMode == "dark") DarkMeiColors.accent.value.toInt() else LightMeiColors.accent.value.toInt()

        val root = FrameLayout(this).apply {
            setOnTouchListener(bubbleTouchListener())
            setOnClickListener { openOverlay() }
        }

        val circle = TextView(this).apply {
            text = "✎"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(0xFF1C1612.toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(accent)
            }
        }
        root.addView(circle, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val dot = View(this)
        val dotParams = FrameLayout.LayoutParams(dp(16), dp(16), Gravity.BOTTOM or Gravity.END).apply {
            marginEnd = dp(1)
            bottomMargin = dp(1)
        }
        root.addView(dot, dotParams)
        dotView = dot

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager?.addView(root, layoutParams)
        bubbleView = root

        // Fresh dot color. Seed the transition state so the reconnect buzz only
        // fires on a real change, not on service start.
        wasOnline = state.status == ConnStatus.ONLINE
        updateStatusUi()
        if (isScreenOn()) {
            handler.post(statusTick)
        }
    }

    private fun isScreenOn(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        return pm?.isInteractive != false
    }

    private fun pauseStatusLoop() {
        handler.removeCallbacks(statusTick)
    }

    private fun resumeStatusLoop() {
        handler.removeCallbacks(statusTick)
        updateStatusUi()
        handler.post(statusTick)
    }

    /** Color the status dot from the latest connection state. */
    private fun updateStatusUi() {
        val colors = if (state.themeMode == "dark") DarkMeiColors else LightMeiColors
        val color = when (state.status) {
            ConnStatus.ONLINE -> colors.success
            ConnStatus.CHECKING -> colors.accentHover
            ConnStatus.OFFLINE -> colors.danger
            ConnStatus.UNKNOWN -> colors.textMuted
        }
        dotView?.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color.value.toInt())
        }

        // Vibrate once when the desktop becomes reachable again.
        val online = state.status == ConnStatus.ONLINE
        if (online && !wasOnline) {
            vibrateBriefly()
        }
        wasOnline = online
    }

    /** Ping the desktop so the dot stays accurate when auto-sync is disabled. */
    private fun refreshConnection() {
        if (!state.prefs.isConfigured()) return
        scope.launch { state.testConnection(silent = true) }
    }

    @Suppress("DEPRECATION")
    private fun vibrateBriefly() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                (getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.vibrate(120)
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Free drag anywhere on screen: hold and move the bubble. The position is
     * clamped to the screen bounds so the bubble can never be lost off-screen.
     * While dragging, an ✕ target appears at the bottom-center; dropping the
     * bubble there dismisses it. A quick tap (no drag) still opens the overlay.
     */
    private fun bubbleTouchListener(): View.OnTouchListener {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var dragging = false
        var overDeleteZone = false
        val bubbleSize = dp(56)

        return View.OnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    dragging = false
                    overDeleteZone = false
                    showDeleteZone()
                    // Slight grow while held so the user feels the grab.
                    view.animate().scaleX(1.12f).scaleY(1.12f).setDuration(120).start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val desiredX = initialX + (event.rawX - initialTouchX).toInt()
                    val desiredY = initialY + (event.rawY - initialTouchY).toInt()
                    layoutParams.x = desiredX.coerceIn(0, screenWidth() - bubbleSize)
                    layoutParams.y = desiredY.coerceIn(0, screenHeight() - bubbleSize)
                    windowManager?.updateViewLayout(view, layoutParams)
                    if (abs(event.rawX - initialTouchX) > 8 || abs(event.rawY - initialTouchY) > 8) {
                        dragging = true
                    }
                    val over = isOverDeleteZone()
                    if (over != overDeleteZone) {
                        overDeleteZone = over
                        highlightDeleteZone(over)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    if (overDeleteZone) {
                        hideDeleteZone()
                        dismissBubble()
                    } else {
                        hideDeleteZone()
                        if (!dragging) {
                            view.performClick()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    hideDeleteZone()
                    true
                }
                else -> false
            }
        }
    }

    private fun showDeleteZone() {
        if (deleteZoneView != null) return
        val zone = FrameLayout(this)
        val label = TextView(this).apply {
            text = "✕"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xDDC0533F.toInt())
            }
        }
        zone.addView(label, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER))
        deleteZoneView = zone
        windowManager?.addView(zone, deleteZoneParams)
    }

    private fun hideDeleteZone() {
        deleteZoneView?.let { runCatching { windowManager?.removeView(it) } }
        deleteZoneView = null
    }

    private fun highlightDeleteZone(active: Boolean) {
        deleteZoneView?.animate()
            ?.scaleX(if (active) 1.25f else 1f)
            ?.scaleY(if (active) 1.25f else 1f)
            ?.setDuration(100)
            ?.start()
    }

    private fun isOverDeleteZone(): Boolean {
        if (deleteZoneView == null) return false
        val bubbleSize = dp(56)
        val zoneSize = dp(72)
        val bubbleCenterX = layoutParams.x + bubbleSize / 2f
        val bubbleCenterY = layoutParams.y + bubbleSize / 2f
        val zoneCenterX = screenWidth() / 2f
        val zoneCenterY = screenHeight() - deleteZoneParams.y - zoneSize / 2f
        val dx = bubbleCenterX - zoneCenterX
        val dy = bubbleCenterY - zoneCenterY
        val radius = dp(60).toFloat()
        return dx * dx + dy * dy <= radius * radius
    }

    private fun dismissBubble() {
        // Turn the bubble off entirely so it stays dismissed (and won't
        // auto-restart the next time the app opens).
        state.prefs.bubbleEnabled = false
        stopSelf()
    }

    private fun screenWidth(): Int = resources.displayMetrics.widthPixels

    private fun screenHeight(): Int = resources.displayMetrics.heightPixels

    private fun openOverlay() {
        val intent = Intent(this, BubbleOverlayActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun buildNotification(): Notification {
        val channelId = "mei_bubble"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Bubble ghi chú nhanh", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Mei Remote")
            .setContentText("Bubble ghi chú nhanh đang bật — chấm màu là trạng thái desktop")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val NOTIFICATION_ID = 4101
        private const val STATUS_TICK_MS = 4_000L
        private const val STATUS_POLL_EVERY_N_TICKS = 15 // ~60s, auto-sync off only

        fun start(context: Context) {
            val intent = Intent(context, BubbleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BubbleService::class.java))
        }
    }
}
