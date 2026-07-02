package com.endlan.soundcardfx.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.endlan.soundcardfx.R

/**
 * Foreground service supaya proses audio tetap jalan walau layar mati / app di-background.
 * Wajib foreground service karena ini akses mikrofon terus-menerus (kebijakan Android 10+).
 */
class AudioEngineService : Service() {

    companion object {
        private const val CHANNEL_ID = "soundcardfx_engine"
        private const val NOTIF_ID = 1
        const val ACTION_STOP = "com.endlan.soundcardfx.action.STOP"
        const val ACTION_ENGINE_STATE_CHANGED = "com.endlan.soundcardfx.action.ENGINE_STATE_CHANGED"
        const val EXTRA_RUNNING = "running"

        /** Dicek dari Activity buat tau apakah service masih jalan di background, tanpa nge-start service baru. */
        @Volatile var isRunning: Boolean = false
    }

    val engine = AudioEngine()

    inner class LocalBinder : Binder() {
        fun getService(): AudioEngineService = this@AudioEngineService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEngineAndSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIF_ID, buildNotification())
        engine.start()
        isRunning = true
        broadcastEngineState()
        return START_STICKY
    }

    private fun stopEngineAndSelf() {
        engine.stop()
        isRunning = false
        broadcastEngineState()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        engine.stop()
        isRunning = false
        broadcastEngineState()
        super.onDestroy()
    }

    private fun broadcastEngineState() {
        val intent = Intent(ACTION_ENGINE_STATE_CHANGED).apply {
            putExtra(EXTRA_RUNNING, isRunning)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, AudioEngineService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .addAction(0, getString(R.string.notif_action_stop), stopPendingIntent)
            .build()
    }
}
