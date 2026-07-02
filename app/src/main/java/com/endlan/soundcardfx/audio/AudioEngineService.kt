package com.endlan.soundcardfx.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
        startForeground(NOTIF_ID, buildNotification())
        engine.start()
        return START_STICKY
    }

    override fun onDestroy() {
        engine.stop()
        super.onDestroy()
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
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }
}
