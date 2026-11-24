package cn.gdou.edu.timetable.worker

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
import cn.gdou.edu.timetable.MainActivity
import cn.gdou.edu.timetable.domain.SyncDiffResult

object SyncNotificationHelper {
    const val CHANNEL_ID = "gdou_data_changes"
    private const val NOTIFICATION_ID_CHANGE = 202601

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "教务信息变动提醒",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "当课表、考试安排、成绩或绩点发生更新时自动通知"
        }
        manager.createNotificationChannel(channel)
    }

    fun notifyChanges(context: Context, diff: SyncDiffResult) {
        if (!diff.hasChanges) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        ensureChannel(context)
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bodyText = diff.details.take(4).joinToString("\n").ifBlank { diff.summary }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("啾比教务信息已更新")
            .setContentText(diff.summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_CHANGE, notification)
        }
    }
}
