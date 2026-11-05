package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.data.database.AppDatabase
import com.example.data.repository.ChatRepository

class CipherisApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { ChatRepository(database.chatDao(), database.vaultDao(), database.stickerDao(), this) }

    companion object {
        const val NOTIFICATION_ID_CALL = 1001
        const val CHANNEL_ID_CALLS = "incoming_calls"
        const val CHANNEL_ID_MESSAGES = "incoming_messages"
        lateinit var instance: CipherisApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "مكالمات واردة مشفرة"
            val descriptionText = "تنبيهات المكالمات الصوتية والمرئية الواردة نظير-إلى-نظير"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID_CALLS, name, importance).apply {
                description = descriptionText
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(1000, 1000, 1000, 1000, 1000)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            
            val msgName = "رسائل واردة مشفرة"
            val msgDesc = "تنبيهات الرسائل والوسائط الجديدة الواردة"
            val msgChannel = NotificationChannel(CHANNEL_ID_MESSAGES, msgName, NotificationManager.IMPORTANCE_HIGH).apply {
                description = msgDesc
                enableLights(true)
                enableVibration(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            notificationManager.createNotificationChannel(msgChannel)
        }
    }

    fun showMessageNotification(senderName: String, messageText: String) {
        val cleanText = if (messageText.startsWith("RECEIPT_") || messageText.startsWith("CALL_SIGNAL:") || messageText.startsWith("INVITE_GROUP:")) {
            return // Skip internal signals
        } else {
            messageText
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(senderName)
            .setContentText(cleanText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))

        val notificationId = senderName.hashCode()
        notificationManager.notify(notificationId, builder.build())
    }

    fun showCallNotification(senderName: String, isVideo: Boolean) {
        val title = if (isVideo) "📞 مكالمة فيديو مشفرة E2EE واردة" else "📞 مكالمة صوتية مشفرة E2EE واردة"
        val body = "يتصل بك $senderName الآن..."

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID_CALLS)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setOngoing(true)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
            .setVibrate(longArrayOf(1000, 1000, 1000, 1000, 1000))

        notificationManager.notify(NOTIFICATION_ID_CALL, builder.build())
    }

    fun cancelCallNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID_CALL)
    }
}
