package cn.gdou.edu.timetable.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.gdou.edu.timetable.BuildConfig
import cn.gdou.edu.timetable.R
import cn.gdou.edu.timetable.data.local.AppSettings
import cn.gdou.edu.timetable.data.remote.ApkInstaller
import cn.gdou.edu.timetable.data.remote.AppUpdateClient
import cn.gdou.edu.timetable.ui.ApkDownloadStage
import cn.gdou.edu.timetable.ui.AppUpdateUiState
import java.util.Locale

@Composable
fun AppUpdateDialog(
    state: AppUpdateUiState,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstallNow: () -> Unit,
    onIgnoreVersion: () -> Unit,
    onDismiss: () -> Unit
) {
    val info = state.availableUpdate ?: return
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = {
            if (state.downloadStage != ApkDownloadStage.DOWNLOADING) {
                onDismiss()
            }
        },
        icon = {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_app_avatar),
                    contentDescription = "应用更新",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        },
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (info.isTestPreview) "发现新版本（测试预览）" else "发现新版本",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = "v${BuildConfig.VERSION_NAME} → v${info.versionName}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    val sizeText = info.formattedSize()
                    if (sizeText.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = sizeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (info.releaseTitle.isNotBlank() || info.formattedDate().isNotBlank()) {
                    val subtitle = listOfNotNull(
                        info.releaseTitle.takeIf { it.isNotBlank() },
                        info.formattedDate().takeIf { it.isNotBlank() }
                    ).joinToString(" · ")
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Scrollable changelog card
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp, max = 200.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "更新内容",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = info.changelog.ifBlank { "修复已知问题并优化使用体验。" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Download / Install status block
                when (state.downloadStage) {
                    ApkDownloadStage.DOWNLOADING -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (state.downloadPercent >= 0f) {
                                LinearProgressIndicator(
                                    progress = { state.downloadPercent.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val pctText = if (state.downloadPercent >= 0f) {
                                    "${(state.downloadPercent * 100).toInt()}%"
                                } else {
                                    "下载中..."
                                }
                                val bytesText = if (state.totalBytes > 0L) {
                                    "${formatBytes(state.downloadedBytes)} / ${formatBytes(state.totalBytes)}"
                                } else {
                                    formatBytes(state.downloadedBytes)
                                }
                                Text(
                                    text = "$pctText · $bytesText",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (state.speedBytesPerSec > 0L) {
                                    Text(
                                        text = "${formatBytes(state.speedBytesPerSec)}/s",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    ApkDownloadStage.READY_TO_INSTALL -> {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "新版本安装包已下载完成，点击下方按钮立即安装",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    ApkDownloadStage.NEEDS_PERMISSION -> {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "请在系统设置中允许「啾比教务」安装未知应用，开启后返回点击「立即安装」即可升级。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }

                    ApkDownloadStage.ERROR -> {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = state.downloadError ?: "下载失败，请重试或前往浏览器下载",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    ApkDownloadStage.IDLE -> {
                        if (info.isTestPreview) {
                            Text(
                                text = "提示：这是本地推送测试预览，不会下载真实 APK。请在「配置更新源」中填写你的 GitHub 仓库后正式使用。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Secondary actions row (browser download / ignore)
                if (state.downloadStage != ApkDownloadStage.DOWNLOADING) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!info.isTestPreview) {
                            TextButton(onClick = onIgnoreVersion) {
                                Text("忽略此版本", style = MaterialTheme.typography.labelMedium)
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        val browserUrl = info.releasePageUrl.ifBlank { info.apkDownloadUrl }
                        if (browserUrl.isNotBlank() && !info.isTestPreview) {
                            TextButton(onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(browserUrl))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }) {
                                Icon(
                                    Icons.Outlined.OpenInBrowser,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("浏览器打开", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (state.downloadStage) {
                ApkDownloadStage.IDLE, ApkDownloadStage.ERROR -> {
                    if (info.isTestPreview) {
                        Button(onClick = onDismiss) {
                            Text("知道了")
                        }
                    } else if (info.apkDownloadUrl.isNotBlank()) {
                        Button(onClick = onStartDownload) {
                            Icon(
                                Icons.Outlined.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (state.downloadStage == ApkDownloadStage.ERROR) "重新下载" else "立即下载更新")
                        }
                    } else if (info.releasePageUrl.isNotBlank()) {
                        Button(onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(info.releasePageUrl))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                            onDismiss()
                        }) {
                            Icon(
                                Icons.Outlined.OpenInBrowser,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("前往发布页下载")
                        }
                    } else {
                        Button(onClick = onDismiss) { Text("确定") }
                    }
                }

                ApkDownloadStage.DOWNLOADING -> {
                    OutlinedButton(onClick = onCancelDownload) {
                        Text("取消下载")
                    }
                }

                ApkDownloadStage.READY_TO_INSTALL -> {
                    Button(onClick = onInstallNow) {
                        Icon(
                            Icons.Outlined.InstallMobile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("立即安装")
                    }
                }

                ApkDownloadStage.NEEDS_PERMISSION -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onInstallNow) {
                            Text("已开启，安装")
                        }
                        Button(onClick = {
                            runCatching {
                                context.startActivity(ApkInstaller.createManageUnknownSourcesIntent(context))
                            }
                        }) {
                            Text("去开启权限")
                        }
                    }
                }
            }
        },
        dismissButton = {
            if (state.downloadStage != ApkDownloadStage.DOWNLOADING && !info.isTestPreview) {
                TextButton(onClick = onDismiss) {
                    Text("稍后再说")
                }
            }
        }
    )
}

@Composable
fun UpdateSourceConfigDialog(
    initialRepoSpec: String,
    initialMirrorMode: String,
    initialCustomMirror: String,
    onSave: (repoSpec: String, mirrorMode: String, customMirror: String) -> Unit,
    onDismiss: () -> Unit
) {
    var repoInput by remember(initialRepoSpec) { mutableStateOf(initialRepoSpec) }
    var mirrorMode by remember(initialMirrorMode) { mutableStateOf(initialMirrorMode) }
    var customMirror by remember(initialCustomMirror) { mutableStateOf(initialCustomMirror) }

    val resolvedPreview = remember(repoInput) {
        AppUpdateClient.resolveSource(repoInput)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Outlined.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("配置应用更新源", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "填写你的 GitHub 仓库（如 用户名/仓库名 或完整 GitHub 链接），发布 Release 并上传 APK 附件后，App 即可自动检测并推送更新；也兼容自定义 version.json 地址。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = repoInput,
                    onValueChange = { repoInput = it },
                    label = { Text("GitHub 仓库或更新源地址") },
                    placeholder = { Text("例如：yourname/qiubi-jw") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (repoInput.isNotBlank()) {
                    Surface(
                        color = if (resolvedPreview != null) {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (resolvedPreview != null) {
                                "检测地址：${resolvedPreview.apiUrl}"
                            } else {
                                "格式无法识别，请输入 owner/repo 或以 https:// 开头的完整地址"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (resolvedPreview != null) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Text(
                    text = "APK 下载通道",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = mirrorMode == AppSettings.UPDATE_MIRROR_DIRECT,
                        onClick = { mirrorMode = AppSettings.UPDATE_MIRROR_DIRECT },
                        label = { Text("直连", maxLines = 1, softWrap = false) }
                    )
                    FilterChip(
                        selected = mirrorMode == AppSettings.UPDATE_MIRROR_GHPROXY,
                        onClick = { mirrorMode = AppSettings.UPDATE_MIRROR_GHPROXY },
                        label = { Text("镜像加速", maxLines = 1, softWrap = false) }
                    )
                    FilterChip(
                        selected = mirrorMode == AppSettings.UPDATE_MIRROR_CUSTOM,
                        onClick = { mirrorMode = AppSettings.UPDATE_MIRROR_CUSTOM },
                        label = { Text("自定义", maxLines = 1, softWrap = false) }
                    )
                }

                if (mirrorMode == AppSettings.UPDATE_MIRROR_CUSTOM) {
                    OutlinedTextField(
                        value = customMirror,
                        onValueChange = { customMirror = it },
                        label = { Text("自定义加速前缀") },
                        placeholder = { Text("例如：https://gh-proxy.com/") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (mirrorMode == AppSettings.UPDATE_MIRROR_GHPROXY) {
                    Text(
                        text = "将优先通过 ${AppSettings.DEFAULT_GHPROXY_PREFIX} 加速下载，若镜像不可用会自动回退直连 GitHub。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(repoInput.trim(), mirrorMode, customMirror.trim()) },
                enabled = repoInput.isBlank() || resolvedPreview != null
            ) {
                Text("保存并检测")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val mb = bytes / (1024.0 * 1024.0)
    return when {
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
