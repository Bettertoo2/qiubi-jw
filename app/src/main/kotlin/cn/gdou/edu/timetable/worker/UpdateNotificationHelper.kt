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
import cn.gdou.edu.timetable.R
import cn.gdou.edu.timetable.data.remote.ApkInstaller
import cn.gdou.edu.timetable.data.remote.AppUpdateInfo
import java.io.File

object UpdateNotificationHelper {
    const val CHANNEL_ID = "app_version_updates"
    const val EXTRA_OPEN_UPDATE_DIALOG = "extra_open_update_dialog"
    private const val NOTIFICATION_ID_UPDATE_AVAILABLE = 202610
    private const val NOTIFICATION_ID_UPDATE_READY = 202611

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "应用版本更新推送",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "当检测到新版本 APK 发布或更新包下载完成时推送通知提醒"
        }
        manager.createNotificationChannel(channel)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun notifyUpdateAvailable(context: Context, info: AppUpdateInfo): Boolean {
        if (!hasNotificationPermission(context)) return false
        ensureChannel(context)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_UPDATE_DIALOG, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_UPDATE_AVAILABLE,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val titlePrefix = if (info.isTestPreview) "[测试推送] " else ""
        val title = "${titlePrefix}啾比教务发现新版本 v${info.versionName}"
        val sizeSuffix = info.formattedSize().takeIf { it.isNotBlank() }?.let { "（$it）" }.orEmpty()
        val summaryLine = "${info.releaseTitle.ifBlank { "新版本已发布" }}$sizeSuffix · 点击查看并下载更新"
        val bigText = buildString {
            append(summaryLine)
            if (info.changelog.isNotBlank()) {
                append("\n\n")
                append(info.changelog.take(260))
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle(title)
            .setContentText(summaryLine)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_UPDATE_AVAILABLE, notification)
            true
        }.getOrDefault(false)
    }

    fun notifyDownloadReady(context: Context, versionName: String, apkFile: File): Boolean {
        if (!hasNotificationPermission(context)) return false
        ensureChannel(context)

        val tapIntent = if (ApkInstaller.canInstallPackages(context)) {
            runCatching { ApkInstaller.createInstallIntent(context, apkFile) }.getOrElse {
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(EXTRA_OPEN_UPDATE_DIALOG, true)
                }
            }
        } else {
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OPEN_UPDATE_DIALOG, true)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_UPDATE_READY,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle("啾比教务 v$versionName 更新包已就绪")
            .setContentText("安装包已下载完成，点击立即安装升级")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_UPDATE_READY, notification)
            true
        }.getOrDefault(false)
    }
}
