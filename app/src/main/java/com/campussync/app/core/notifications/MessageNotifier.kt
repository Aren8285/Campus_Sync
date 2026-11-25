package com.campussync.app.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.campussync.app.MainActivity
import com.campussync.app.R

/**
 * Builds chat notifications and opens the conversation when one is tapped.
 *
 * Notifications are shown from an incoming FCM message. The Spark plan has no
 * Cloud Functions, so nothing in the app can send a push; the client only
 * registers its token and displays incoming messages sent by a trusted sender.
 */
object MessageNotifier {
    const val CHANNEL_ID = "messages"

    private const val MAX_TITLE_LENGTH = 80
    private const val MAX_BODY_LENGTH = 200

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Messages",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "New chat messages from your college."
        }
        manager.createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun show(context: Context, chatId: String, title: String, body: String) {
        if (!canNotify(context)) return
        createChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_CHAT_ID, chatId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            chatId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Push data is untrusted input even when the sender is trusted, so cap it.
        val safeTitle = title.take(MAX_TITLE_LENGTH)
        val safeBody = body.take(MAX_BODY_LENGTH)

        // Message text stays off the lock screen: the public version is generic and
        // only the notification shown after unlocking reveals the sender and text.
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("New message")
            .setContentText("Open Campus Sync to read it")
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(safeTitle)
            .setContentText(safeBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(safeBody))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .build()

        NotificationManagerCompat.from(context).notify(chatId.hashCode(), notification)
    }
}
