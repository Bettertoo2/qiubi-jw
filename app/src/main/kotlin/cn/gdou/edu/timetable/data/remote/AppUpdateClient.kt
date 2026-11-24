package cn.gdou.edu.timetable.data.remote

import cn.gdou.edu.timetable.BuildConfig
import cn.gdou.edu.timetable.data.local.AppSettings
import cn.gdou.edu.timetable.domain.VersionComparator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Serializable
data class AppUpdateInfo(
    val versionName: String,
    val versionCode: Int? = null,
    val releaseTitle: String = "",
    val changelog: String = "",
    val publishedAt: String = "",
    val releasePageUrl: String = "",
    val apkDownloadUrl: String = "",
    val apkFileName: String = "",
    val apkSizeBytes: Long = 0L,
    val sourceLabel: String = "",
    val isTestPreview: Boolean = false
) {
    fun isNewerThanCurrent(): Boolean = VersionComparator.isNewer(
        remoteVersion = versionName,
        currentVersion = BuildConfig.VERSION_NAME,
        remoteVersionCode = versionCode,
        currentVersionCode = BuildConfig.VERSION_CODE
    )

    fun formattedSize(): String {
        if (apkSizeBytes <= 0L) return ""
        val mb = apkSizeBytes / (1024.0 * 1024.0)
        return if (mb >= 1.0) String.format(java.util.Locale.US, "%.1f MB", mb)
        else String.format(java.util.Locale.US, "%.0f KB", apkSizeBytes / 1024.0)
    }

    fun formattedDate(): String = publishedAt.take(10)
}

enum class UpdateSourceType {
    GITHUB_RELEASES,
    CUSTOM_JSON
}

data class ResolvedUpdateSource(
    val apiUrl: String,
    val fallbackApiUrl: String? = null,
    val type: UpdateSourceType,
    val displayLabel: String
)

/**
 * Fetches latest release metadata from GitHub Releases API (primary) or a custom version.json manifest.
 * Uses a dedicated cookie-free OkHttpClient so campus auth cookies are never sent to third-party hosts.
 */
class AppUpdateClient {
    private val json = Json { ignoreUnknownKeys = true }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun encodeInfo(info: AppUpdateInfo): String = json.encodeToString(info)

    fun decodeInfo(raw: String): AppUpdateInfo? = runCatching {
        if (raw.isBlank()) null else json.decodeFromString<AppUpdateInfo>(raw)
    }.getOrNull()

    suspend fun checkLatestRelease(rawSourceInput: String): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        val resolved = resolveSource(rawSourceInput)
            ?: return@withContext Result.failure(
                IllegalArgumentException("更新源格式不正确，请输入 GitHub 仓库（如 owner/repo）或有效链接")
            )

        runCatching {
            when (resolved.type) {
                UpdateSourceType.GITHUB_RELEASES -> fetchGitHubRelease(resolved)
                UpdateSourceType.CUSTOM_JSON -> fetchCustomJsonRelease(resolved)
            }
        }
    }

    private fun fetchGitHubRelease(source: ResolvedUpdateSource): AppUpdateInfo {
        val req = Request.Builder()
            .url(source.apiUrl)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "QiubiJW-Android/${BuildConfig.VERSION_NAME}")
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) {
                val body = resp.body?.string().orEmpty()
                return parseGitHubReleaseJson(body, source.displayLabel)
            }
            if (resp.code == 403) {
                throw IllegalStateException("GitHub API 请求受限（HTTP 403），请稍后再试")
            }
            if (resp.code == 404 && !source.fallbackApiUrl.isNullOrBlank()) {
                // Fallback to /releases?per_page=1 in case only pre-releases have been published so far
                val fallbackReq = Request.Builder()
                    .url(source.fallbackApiUrl)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "QiubiJW-Android/${BuildConfig.VERSION_NAME}")
                    .get()
                    .build()
                client.newCall(fallbackReq).execute().use { fbResp ->
                    if (!fbResp.isSuccessful) {
                        throw IllegalStateException("未找到该仓库或尚未发布 Release（HTTP ${fbResp.code}）")
                    }
                    val fbBody = fbResp.body?.string().orEmpty()
                    val arr = json.parseToJsonElement(fbBody) as? JsonArray
                    val first = arr?.firstOrNull()?.jsonObject
                        ?: throw IllegalStateException("仓库 ${source.displayLabel} 尚未发布任何 Release 版本")
                    return parseGitHubReleaseObject(first, source.displayLabel)
                }
            }
            throw IllegalStateException("获取版本信息失败（HTTP ${resp.code}）")
        }
    }

    private fun fetchCustomJsonRelease(source: ResolvedUpdateSource): AppUpdateInfo {
        val req = Request.Builder()
            .url(source.apiUrl)
            .header("Accept", "application/json")
            .header("User-Agent", "QiubiJW-Android/${BuildConfig.VERSION_NAME}")
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("获取自定义更新清单失败（HTTP ${resp.code}）")
            }
            val body = resp.body?.string().orEmpty()
            return parseCustomManifestJson(body, source.displayLabel)
        }
    }

    companion object {
        private val GITHUB_REPO_SHORTHAND = Regex("""^([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)$""")
        private val GITHUB_WEB_URL = Regex(
            """^https?://(?:www\.)?github\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+?)(?:\.git)?(?:/.*)?$""",
            RegexOption.IGNORE_CASE
        )
        private val GITHUB_API_URL = Regex(
            """^https?://api\.github\.com/repos/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)/releases(?:/latest)?/?$""",
            RegexOption.IGNORE_CASE
        )
        private val VERSION_CODE_HINT = Regex("""(?i)\b(?:versionCode|build)\s*[:=]\s*(\d+)""")

        private val parserJson = Json { ignoreUnknownKeys = true }

        fun resolveSource(rawInput: String): ResolvedUpdateSource? {
            val trimmed = rawInput.trim().trimEnd('/')
            if (trimmed.isEmpty()) return null

            GITHUB_REPO_SHORTHAND.matchEntire(trimmed)?.let { m ->
                val owner = m.groupValues[1]
                val repo = m.groupValues[2].removeSuffix(".git")
                return ResolvedUpdateSource(
                    apiUrl = "https://api.github.com/repos/$owner/$repo/releases/latest",
                    fallbackApiUrl = "https://api.github.com/repos/$owner/$repo/releases?per_page=1",
                    type = UpdateSourceType.GITHUB_RELEASES,
                    displayLabel = "$owner/$repo"
                )
            }

            GITHUB_API_URL.matchEntire(trimmed)?.let { m ->
                val owner = m.groupValues[1]
                val repo = m.groupValues[2]
                return ResolvedUpdateSource(
                    apiUrl = "https://api.github.com/repos/$owner/$repo/releases/latest",
                    fallbackApiUrl = "https://api.github.com/repos/$owner/$repo/releases?per_page=1",
                    type = UpdateSourceType.GITHUB_RELEASES,
                    displayLabel = "$owner/$repo"
                )
            }

            GITHUB_WEB_URL.matchEntire(trimmed)?.let { m ->
                val owner = m.groupValues[1]
                val repo = m.groupValues[2]
                return ResolvedUpdateSource(
                    apiUrl = "https://api.github.com/repos/$owner/$repo/releases/latest",
                    fallbackApiUrl = "https://api.github.com/repos/$owner/$repo/releases?per_page=1",
                    type = UpdateSourceType.GITHUB_RELEASES,
                    displayLabel = "$owner/$repo"
                )
            }

            if (trimmed.startsWith("https://", ignoreCase = true) ||
                trimmed.startsWith("http://", ignoreCase = true)
            ) {
                return ResolvedUpdateSource(
                    apiUrl = trimmed,
                    type = UpdateSourceType.CUSTOM_JSON,
                    displayLabel = trimmed.substringAfter("://").substringBefore('/')
                )
            }

            return null
        }

        fun parseGitHubReleaseJson(rawJson: String, sourceLabel: String): AppUpdateInfo {
            val obj = parserJson.parseToJsonElement(rawJson).jsonObject
            return parseGitHubReleaseObject(obj, sourceLabel)
        }

        private fun parseGitHubReleaseObject(obj: JsonObject, sourceLabel: String): AppUpdateInfo {
            val tagName = obj["tag_name"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
            val name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
            val versionName = VersionComparator.normalize(tagName.ifBlank { name })
            if (versionName.isBlank()) {
                throw IllegalStateException("Release 中未解析到有效版本号")
            }

            val body = obj["body"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
            val publishedAt = obj["published_at"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val htmlUrl = obj["html_url"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val codeHint = VERSION_CODE_HINT.find(body)?.groupValues?.getOrNull(1)?.toIntOrNull()

            val assets = obj["assets"]?.jsonArray.orEmpty().mapNotNull { runCatching { it.jsonObject }.getOrNull() }
            val apkAssets = assets.filter { asset ->
                val assetName = asset["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val contentType = asset["content_type"]?.jsonPrimitive?.contentOrNull.orEmpty()
                assetName.endsWith(".apk", ignoreCase = true) ||
                    contentType.equals("application/vnd.android.package-archive", ignoreCase = true)
            }
            // Prefer release / universal / arm64-v8a APKs over debug or specialty builds
            val selectedApk = apkAssets.maxByOrNull { asset ->
                val n = asset["name"]?.jsonPrimitive?.contentOrNull.orEmpty().lowercase()
                var score = 0
                if (n.contains("release")) score += 4
                if (n.contains("universal")) score += 3
                if (n.contains("arm64")) score += 2
                if (n.contains("debug")) score -= 1
                score
            }

            val downloadUrl = selectedApk?.get("browser_download_url")?.jsonPrimitive?.contentOrNull.orEmpty()
            val apkName = selectedApk?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()
            val apkSize = selectedApk?.get("size")?.jsonPrimitive?.longOrNull ?: 0L

            return AppUpdateInfo(
                versionName = versionName,
                versionCode = codeHint,
                releaseTitle = name.ifBlank { "v$versionName" },
                changelog = body.ifBlank { "修复已知问题并优化使用体验。" },
                publishedAt = publishedAt,
                releasePageUrl = htmlUrl,
                apkDownloadUrl = downloadUrl,
                apkFileName = apkName,
                apkSizeBytes = apkSize,
                sourceLabel = sourceLabel
            )
        }

        fun parseCustomManifestJson(rawJson: String, sourceLabel: String): AppUpdateInfo {
            val obj = parserJson.parseToJsonElement(rawJson).jsonObject
            val rawVer = obj["versionName"]?.jsonPrimitive?.contentOrNull
                ?: obj["version"]?.jsonPrimitive?.contentOrNull
                ?: obj["tag_name"]?.jsonPrimitive?.contentOrNull
                ?: ""
            val versionName = VersionComparator.normalize(rawVer)
            if (versionName.isBlank()) {
                throw IllegalStateException("更新清单缺少 versionName / version 字段")
            }

            val versionCode = obj["versionCode"]?.jsonPrimitive?.intOrNull
                ?: obj["build"]?.jsonPrimitive?.intOrNull
            val title = obj["title"]?.jsonPrimitive?.contentOrNull
                ?: obj["name"]?.jsonPrimitive?.contentOrNull
                ?: "v$versionName"
            val changelog = obj["changelog"]?.jsonPrimitive?.contentOrNull
                ?: obj["notes"]?.jsonPrimitive?.contentOrNull
                ?: obj["body"]?.jsonPrimitive?.contentOrNull
                ?: "修复已知问题并优化体验。"
            val apkUrl = obj["apkUrl"]?.jsonPrimitive?.contentOrNull
                ?: obj["downloadUrl"]?.jsonPrimitive?.contentOrNull
                ?: obj["url"]?.jsonPrimitive?.contentOrNull
                ?: ""
            val pageUrl = obj["releasePageUrl"]?.jsonPrimitive?.contentOrNull
                ?: obj["htmlUrl"]?.jsonPrimitive?.contentOrNull
                ?: apkUrl
            val apkName = obj["apkFileName"]?.jsonPrimitive?.contentOrNull
                ?: apkUrl.substringAfterLast('/').substringBefore('?').ifBlank { "update-$versionName.apk" }
            val apkSize = obj["apkSizeBytes"]?.jsonPrimitive?.longOrNull
                ?: obj["size"]?.jsonPrimitive?.longOrNull
                ?: 0L
            val publishedAt = obj["publishedAt"]?.jsonPrimitive?.contentOrNull
                ?: obj["date"]?.jsonPrimitive?.contentOrNull
                ?: ""

            return AppUpdateInfo(
                versionName = versionName,
                versionCode = versionCode,
                releaseTitle = title,
                changelog = changelog,
                publishedAt = publishedAt,
                releasePageUrl = pageUrl,
                apkDownloadUrl = apkUrl,
                apkFileName = apkName,
                apkSizeBytes = apkSize,
                sourceLabel = sourceLabel
            )
        }

        /**
         * Builds ordered candidate download URLs (mirrored URL first if enabled, then direct fallback)
         * so users in Mainland China can accelerate GitHub Release downloads without sacrificing reliability.
         */
        fun buildDownloadUrls(
            directUrl: String,
            mirrorMode: String,
            customMirrorPrefix: String
        ): List<String> {
            val clean = directUrl.trim()
            if (clean.isBlank()) return emptyList()
            val isGitHubAsset = clean.startsWith("https://github.com/", ignoreCase = true) ||
                clean.startsWith("https://objects.githubusercontent.com/", ignoreCase = true)
            if (!isGitHubAsset) return listOf(clean)

            val prefix = when (mirrorMode) {
                AppSettings.UPDATE_MIRROR_GHPROXY -> AppSettings.DEFAULT_GHPROXY_PREFIX
                AppSettings.UPDATE_MIRROR_CUSTOM -> customMirrorPrefix.trim()
                else -> ""
            }
            if (prefix.isBlank() || (!prefix.startsWith("https://") && !prefix.startsWith("http://"))) {
                return listOf(clean)
            }
            val normalizedPrefix = if (prefix.endsWith("/")) prefix else "$prefix/"
            return listOf("$normalizedPrefix$clean", clean).distinct()
        }
    }
}
