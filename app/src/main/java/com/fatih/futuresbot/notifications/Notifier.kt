package com.fatih.futuresbot.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fatih.futuresbot.data.settings.NotificationSettingsStore
import java.util.concurrent.atomic.AtomicInteger

enum class NotifyChannel(val id: String, val title: String, val description: String) {
    TRADES("trades", "İşlemler", "Pozisyon açma, kapanma, SL/TP"),
    BOT("bot", "Bot", "Sinyal ve bot durumu"),
    ALERTS("alerts", "Uyarılar", "Bağlantı ve limit uyarıları"),
}

/** Android bildirimleri. İzin yoksa ya da ayarlardan kapalıysa sessizce atlanır. */
class Notifier(
    private val context: Context,
    private val settings: NotificationSettingsStore,
) {
    private val counter = AtomicInteger(1000)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            NotifyChannel.entries.forEach { channel ->
                val importance = if (channel == NotifyChannel.BOT) {
                    NotificationManager.IMPORTANCE_DEFAULT
                } else {
                    NotificationManager.IMPORTANCE_HIGH
                }
                val notificationChannel = NotificationChannel(channel.id, channel.title, importance)
                notificationChannel.description = channel.description
                manager?.createNotificationChannel(notificationChannel)
            }
        }
    }

    fun hasPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun notify(channel: NotifyChannel, title: String, text: String) {
        if (!settings.enabled.value || !hasPermission()) return
        val notification = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(
                if (channel == NotifyChannel.BOT) {
                    NotificationCompat.PRIORITY_DEFAULT
                } else {
                    NotificationCompat.PRIORITY_HIGH
                }
            )
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(counter.incrementAndGet(), notification)
        }
    }

    fun trade(title: String, text: String) = notify(NotifyChannel.TRADES, title, text)

    fun bot(title: String, text: String) = notify(NotifyChannel.BOT, title, text)

    fun alert(title: String, text: String) = notify(NotifyChannel.ALERTS, title, text)
}
