package com.endlan.soundcardfx.audio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.endlan.soundcardfx.R

/**
 * Service kecil khusus buat nampilin floating bubble (kayak "chat head") yang bisa
 * di-tap buat toggle Live/Standby dan di-drag ke bawah buat dihilangkan.
 * Sengaja dipisah dari AudioEngineService supaya kode audio yang udah jalan gak keutak-atik.
 */
class OverlayControlService : Service() {

    companion object {
        private const val CHANNEL_ID = "soundcardfx_overlay"
        private const val NOTIF_ID = 2
        const val ACTION_REMOVE = "com.endlan.soundcardfx.action.REMOVE_OVERLAY"
    }

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var dismissZoneView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    private val engineStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val running = intent?.getBooleanExtra(AudioEngineService.EXTRA_RUNNING, false) ?: false
            updateBubbleIcon(running)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        addBubbleIfNeeded()
        ContextCompat.registerReceiver(
            this, engineStateReceiver,
            IntentFilter(AudioEngineService.ACTION_ENGINE_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        updateBubbleIcon(AudioEngineService.isRunning)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_REMOVE) {
            removeOverlayAndStopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIF_ID, buildNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        try { unregisterReceiver(engineStateReceiver) } catch (e: IllegalArgumentException) { /* belum terdaftar */ }
        removeAllOverlayViews()
        super.onDestroy()
    }

    private fun addBubbleIfNeeded() {
        if (bubbleView != null) return

        val inflater = LayoutInflater.from(this)
        val bubble = inflater.inflate(R.layout.overlay_bubble, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 300

        bubble.setOnTouchListener { view, event -> handleBubbleTouch(view, event, params) }

        windowManager.addView(bubble, params)
        bubbleView = bubble
        bubbleParams = params
    }

    private fun handleBubbleTouch(view: View, event: MotionEvent, params: WindowManager.LayoutParams): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()
                if (!isDragging && (Math.abs(dx) > 12 || Math.abs(dy) > 12)) {
                    isDragging = true
                    showDismissZone()
                }
                if (isDragging) {
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(view, params)
                    updateDismissZoneHighlight(params)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    if (isOverDismissZone(params)) {
                        removeOverlayAndStopSelf()
                    } else {
                        hideDismissZone()
                    }
                } else {
                    // Tap biasa (gak digeser) -> toggle Live/Standby
                    toggleEngine()
                }
                isDragging = false
                return true
            }
        }
        return false
    }

    private fun toggleEngine() {
        val intent = Intent(this, AudioEngineService::class.java)
        if (AudioEngineService.isRunning) {
            intent.action = AudioEngineService.ACTION_STOP
            startService(intent)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }

    private fun updateBubbleIcon(running: Boolean) {
        val bubble = bubbleView ?: return
        val icon = bubble.findViewById<android.widget.ImageView>(R.id.bubbleIcon)
        bubble.setBackgroundResource(if (running) R.drawable.power_ring_on else R.drawable.power_ring_off)
        icon.imageTintList = ContextCompat.getColorStateList(
            this, if (running) R.color.power_on else R.color.power_off
        )
    }

    private fun showDismissZone() {
        if (dismissZoneView != null) return
        val inflater = LayoutInflater.from(this)
        val zone = inflater.inflate(R.layout.overlay_dismiss_zone, null)

        val displayMetrics = resources.displayMetrics
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        params.y = (displayMetrics.density * 48).toInt()

        windowManager.addView(zone, params)
        dismissZoneView = zone
    }

    private fun hideDismissZone() {
        dismissZoneView?.let {
            try { windowManager.removeView(it) } catch (e: IllegalArgumentException) { /* udah kehapus */ }
        }
        dismissZoneView = null
    }

    private fun updateDismissZoneHighlight(bubbleParams: WindowManager.LayoutParams) {
        val zone = dismissZoneView ?: return
        val scale = if (isOverDismissZone(bubbleParams)) 1.25f else 1f
        zone.scaleX = scale
        zone.scaleY = scale
    }

    private fun isOverDismissZone(bubbleParams: WindowManager.LayoutParams): Boolean {
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val screenWidth = displayMetrics.widthPixels
        val bubbleCenterY = bubbleParams.y + 40 * displayMetrics.density
        val bubbleCenterX = bubbleParams.x + 40 * displayMetrics.density

        val zoneRadius = 60 * displayMetrics.density
        val zoneCenterX = screenWidth / 2f
        val zoneCenterY = screenHeight - (48 * displayMetrics.density) - zoneRadius

        val dx = bubbleCenterX - zoneCenterX
        val dy = bubbleCenterY - zoneCenterY
        return Math.sqrt((dx * dx + dy * dy).toDouble()) < zoneRadius
    }

    private fun removeOverlayAndStopSelf() {
        removeAllOverlayViews()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun removeAllOverlayViews() {
        bubbleView?.let {
            try { windowManager.removeView(it) } catch (e: IllegalArgumentException) { /* udah kehapus */ }
        }
        bubbleView = null
        hideDismissZone()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_overlay_channel_name),
                NotificationManager.IMPORTANCE_MIN
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): android.app.Notification {
        val removeIntent = Intent(this, OverlayControlService::class.java).apply { action = ACTION_REMOVE }
        val removePendingIntent = PendingIntent.getService(
            this, 0, removeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_overlay_title))
            .setContentText(getString(R.string.notif_overlay_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .addAction(0, getString(R.string.notif_action_hide), removePendingIntent)
            .build()
    }
}
