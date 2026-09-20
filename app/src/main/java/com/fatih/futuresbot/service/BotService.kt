package com.fatih.futuresbot.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.fatih.futuresbot.FuturesBotApp
import com.fatih.futuresbot.MainActivity
import com.fatih.futuresbot.domain.model.BotStatus
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Bot açıkken uygulamayı canlı tutan ön plan servisi.
 * Kalıcı bildirim zorunludur; Android arka planda çalışan uygulamayı aksi halde durdurur.
 */
class BotService : LifecycleService() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundNotification(BotStatus.STOPPED, "Başlatılıyor")

        val container = (application as FuturesBotApp).container
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_MS)
        }

        lifecycleScope.launch {
            combine(
                container.botEngine.status,
                container.botSettingsStore.settings,
            ) { status, settings ->
                val mode = if (settings.autoTrade) "otomatik işlem" else "yalnızca sinyal"
                status to "$mode · ${settings.scanCount} parite"
            }.collect { (status, text) ->
                startForegroundNotification(status, text)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    private fun startForegroundNotification(status: BotStatus, text: String) {
        val notification = buildNotification(status, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, 0)
        }
    }

    private fun buildNotification(status: BotStatus, text: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Bot: ${status.label}")
            .setContentText(text)
            .setContentIntent(pending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bot servisi",
                NotificationManager.IMPORTANCE_LOW,
            )
            channel.description = "Bot arka planda çalışırken gösterilen kalıcı bildirim"
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "bot_service"
        private const val NOTIFICATION_ID = 42
        private const val WAKE_LOCK_TAG = "futuresbot:bot"
        private const val WAKE_LOCK_MS = 6L * 60 * 60 * 1000
    }
}
