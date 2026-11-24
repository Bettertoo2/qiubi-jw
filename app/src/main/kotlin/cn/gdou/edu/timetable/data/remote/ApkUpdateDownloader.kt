package cn.gdou.edu.timetable.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import cn.gdou.edu.timetable.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

data class ApkDownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val percent: Float,
    val speedBytesPerSec: Long
)

/**
 * Streams APK downloads into `cacheDir/updates/` with mirror-to-direct fallback,
 * cancellation support, and ZIP/APK magic-byte validation.
 */
class ApkUpdateDownloader(
    private val appContext: Context
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun downloadApk(
        candidateUrls: List<String>,
        versionName: String,
        onProgress: (ApkDownloadProgress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        if (candidateUrls.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("该版本未提供 APK 下载链接"))
        }

        val updatesDir = File(appContext.cacheDir, "updates").apply { mkdirs() }
        // Clean previous cached APKs so cache never grows unbounded
        updatesDir.listFiles()?.forEach { old ->
            runCatching { old.delete() }
        }

        val safeVer = versionName.replace(Regex("""[^A-Za-z0-9._-]"""), "_")
        val finalFile = File(updatesDir, "qiubi-jw-$safeVer.apk")
        val partFile = File(updatesDir, "qiubi-jw-$safeVer.apk.part")

        var lastError: Throwable? = null
        for (url in candidateUrls) {
            currentCoroutineContext().ensureActive()
            val attempt = runCatching {
                downloadSingle(url, partFile, finalFile, onProgress)
            }
            if (attempt.isSuccess) {
                return@withContext attempt
            }
            lastError = attempt.exceptionOrNull()
            runCatching { partFile.delete() }
        }

        Result.failure(lastError ?: IllegalStateException("APK 下载失败，请检查网络后重试"))
    }

    private suspend fun downloadSingle(
        url: String,
        partFile: File,
        finalFile: File,
        onProgress: (ApkDownloadProgress) -> Unit
    ): File {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "QiubiJW-Android/${BuildConfig.VERSION_NAME}")
            .header("Accept", "application/vnd.android.package-archive, application/octet-stream, */*")
            .get()
            .build()

        val call = client.newCall(req)
        val cancelHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause != null) call.cancel()
        }
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("下载失败（HTTP ${resp.code}）")
                }
                val body = resp.body ?: throw IllegalStateException("响应内容为空")
                val total = body.contentLength()
                var downloaded = 0L
                val buffer = ByteArray(16 * 1024)
                var lastEmitMs = 0L
                var speedWindowBytes = 0L
                var speedWindowStartMs = System.currentTimeMillis()
                var currentSpeed = 0L

                body.byteStream().use { input ->
                    FileOutputStream(partFile).use { output ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            speedWindowBytes += read

                            val now = System.currentTimeMillis()
                            val elapsed = now - speedWindowStartMs
                            if (elapsed >= 500L) {
                                currentSpeed = (speedWindowBytes * 1000L) / elapsed.coerceAtLeast(1L)
                                speedWindowBytes = 0L
                                speedWindowStartMs = now
                            }
                            if (now - lastEmitMs >= 150L || (total > 0L && downloaded >= total)) {
                                lastEmitMs = now
                                val pct = if (total > 0L) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else -1f
                                onProgress(
                                    ApkDownloadProgress(
                                        downloadedBytes = downloaded,
                                        totalBytes = total,
                                        percent = pct,
                                        speedBytesPerSec = currentSpeed
                                    )
                                )
                            }
                        }
                        output.flush()
                    }
                }
            }
        } catch (e: Throwable) {
            call.cancel()
            throw e
        } finally {
            cancelHandle?.dispose()
        }

        if (!isValidApkZipHeader(partFile)) {
            throw IllegalStateException("下载的文件不是有效的 APK 安装包（可能被代理或错误页拦截）")
        }

        if (finalFile.exists()) finalFile.delete()
        if (!partFile.renameTo(finalFile)) {
            partFile.copyTo(finalFile, overwrite = true)
            partFile.delete()
        }
        return finalFile
    }

    private fun isValidApkZipHeader(file: File): Boolean {
        if (!file.exists() || file.length() < 4L) return false
        return runCatching {
            val header = ByteArray(4)
            RandomAccessFile(file, "r").use { it.readFully(header) }
            header[0] == 0x50.toByte() &&
                header[1] == 0x4B.toByte() &&
                header[2] == 0x03.toByte() &&
                header[3] == 0x04.toByte()
        }.getOrDefault(false)
    }
}

object ApkInstaller {
    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun createManageUnknownSourcesIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun createInstallIntent(context: Context, apkFile: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
