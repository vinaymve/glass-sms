package com.glasssms.otp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import com.glasssms.MainActivity
import com.glasssms.R

object Notifications {
    private const val CHANNEL_MESSAGES = "messages"
    private const val CHANNEL_CODES = "codes"
    private const val ACCENT = 0xFF007AFF.toInt()

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH),
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_CODES, "Verification codes", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "One-time codes with a quick Copy button"
                // Codes are sensitive; keep them off the lock screen.
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
    }

    fun showMessage(context: Context, threadId: Long, address: String, title: String, body: String) {
        val n = Notification.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setColor(ACCENT)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setContentIntent(openThread(context, threadId, address))
            .setAutoCancel(true)
            .build()
        notify(context, idFor(threadId), n)
    }

    fun showCode(context: Context, threadId: Long, messageId: Long, address: String, title: String, otp: Otp, body: String) {
        val id = idFor(threadId)
        val copy = PendingIntent.getBroadcast(
            context,
            id,
            Intent(context, CopyCodeReceiver::class.java)
                .putExtra(CopyCodeReceiver.EXTRA_CODE, otp.code)
                .putExtra(CopyCodeReceiver.EXTRA_MESSAGE_ID, messageId)
                .putExtra(CopyCodeReceiver.EXTRA_NOTIFICATION_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val heading = otp.service?.let { "$it code" } ?: "Verification code"
        val public = Notification.Builder(context, CHANNEL_CODES)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setContentTitle(heading)
            .setContentText("From $title")
            .build()
        val n = Notification.Builder(context, CHANNEL_CODES)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setColor(ACCENT)
            .setContentTitle("${otp.pretty}  ·  $heading")
            .setContentText("From $title")
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setContentIntent(openThread(context, threadId, address))
            .addAction(Notification.Action.Builder(null as Icon?, "Copy ${otp.code}", copy).build())
            .setTimeoutAfter(otp.validForMillis)
            .setPublicVersion(public)
            .setAutoCancel(true)
            .build()
        notify(context, id, n)
    }

    fun cancel(context: Context, threadId: Long) = cancelId(context, idFor(threadId))

    fun cancelId(context: Context, id: Int) {
        context.getSystemService(NotificationManager::class.java).cancel(id)
    }

    private fun notify(context: Context, id: Int, n: Notification) {
        val nm = context.getSystemService(NotificationManager::class.java)
        // Without POST_NOTIFICATIONS on Android 13+ this is a silent no-op.
        if (nm.areNotificationsEnabled()) nm.notify(id, n)
    }

    private fun idFor(threadId: Long) = threadId.toInt()

    private fun openThread(context: Context, threadId: Long, address: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            idFor(threadId),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_THREAD_ID, threadId)
                .putExtra(MainActivity.EXTRA_ADDRESS, address)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
