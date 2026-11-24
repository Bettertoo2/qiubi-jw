package cn.gdou.edu.timetable.data.remote

import cn.gdou.edu.timetable.data.local.AppSettings
import cn.gdou.edu.timetable.domain.VersionComparator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateClientTest {

    @Test
    fun versionComparator_handlesSemverNumericAndPreReleaseOrdering() {
        assertTrue(VersionComparator.isNewer("v1.0.1", "1.0.0"))
        assertTrue(VersionComparator.isNewer("1.10.0", "1.2.0"))
        assertTrue(VersionComparator.isNewer("v2.0", "1.9.9"))
        assertTrue(VersionComparator.isNewer("1.2.0", "1.2.0-rc.1"))
        assertTrue(VersionComparator.isNewer("1.2.0-rc.1", "1.2.0-beta.2"))
        assertTrue(VersionComparator.isNewer("1.2.0-beta.2", "1.2.0-beta.1"))
        assertTrue(VersionComparator.isNewer("1.2.0-beta.1", "1.2.0-alpha.9"))
        assertFalse(VersionComparator.isNewer("v1.0.0", "1.0.0"))
        assertFalse(VersionComparator.isNewer("0.9.9", "1.0.0"))
        assertTrue(
            VersionComparator.isNewer(
                remoteVersion = "1.0.0",
                currentVersion = "1.0.0",
                remoteVersionCode = 2,
                currentVersionCode = 1
            )
        )
    }

    @Test
    fun resolveSource_acceptsShorthandGithubUrlsAndCustomJson() {
        val short = AppUpdateClient.resolveSource("alice/qiubi-jw")
        assertNotNull(short)
        assertEquals(UpdateSourceType.GITHUB_RELEASES, short!!.type)
        assertEquals("https://api.github.com/repos/alice/qiubi-jw/releases/latest", short.apiUrl)
        assertEquals("alice/qiubi-jw", short.displayLabel)

        val web = AppUpdateClient.resolveSource("https://github.com/alice/qiubi-jw/releases/")
        assertNotNull(web)
        assertEquals(UpdateSourceType.GITHUB_RELEASES, web!!.type)
        assertEquals("https://api.github.com/repos/alice/qiubi-jw/releases/latest", web.apiUrl)

        val api = AppUpdateClient.resolveSource("https://api.github.com/repos/alice/qiubi-jw/releases/latest")
        assertNotNull(api)
        assertEquals(UpdateSourceType.GITHUB_RELEASES, api!!.type)

        val custom = AppUpdateClient.resolveSource("https://cdn.example.com/app/version.json")
        assertNotNull(custom)
        assertEquals(UpdateSourceType.CUSTOM_JSON, custom!!.type)
        assertEquals("https://cdn.example.com/app/version.json", custom.apiUrl)

        assertNull(AppUpdateClient.resolveSource("not a valid repo"))
        assertNull(AppUpdateClient.resolveSource("   "))
    }

    @Test
    fun parseGitHubReleaseJson_prefersReleaseApkOverDebugAndNonApkAssets() {
        val sampleJson = """
            {
              "tag_name": "v1.2.0",
              "name": "啾比教务 v1.2.0",
              "body": "修复退出重登问题\nversionCode: 3",
              "published_at": "2026-11-24T12:00:00Z",
              "html_url": "https://github.com/alice/qiubi-jw/releases/tag/v1.2.0",
              "assets": [
                {
                  "name": "checksums.txt",
                  "size": 128,
                  "content_type": "text/plain",
                  "browser_download_url": "https://github.com/alice/qiubi-jw/releases/download/v1.2.0/checksums.txt"
                },
                {
                  "name": "app-debug.apk",
                  "size": 14000000,
                  "content_type": "application/vnd.android.package-archive",
                  "browser_download_url": "https://github.com/alice/qiubi-jw/releases/download/v1.2.0/app-debug.apk"
                },
                {
                  "name": "qiubi-jw-v1.2.0-release-universal.apk",
                  "size": 12500000,
                  "content_type": "application/vnd.android.package-archive",
                  "browser_download_url": "https://github.com/alice/qiubi-jw/releases/download/v1.2.0/qiubi-jw-v1.2.0-release-universal.apk"
                }
              ]
            }
        """.trimIndent()

        val parsed = AppUpdateClient.parseGitHubReleaseJson(sampleJson, "alice/qiubi-jw")
        assertEquals("1.2.0", parsed.versionName)
        assertEquals(3, parsed.versionCode)
        assertEquals("qiubi-jw-v1.2.0-release-universal.apk", parsed.apkFileName)
        assertEquals(12500000L, parsed.apkSizeBytes)
        assertTrue(parsed.apkDownloadUrl.endsWith("qiubi-jw-v1.2.0-release-universal.apk"))
        assertEquals("2026-11-24", parsed.formattedDate())
    }

    @Test
    fun parseCustomManifestJson_parsesFlexibleFields() {
        val sample = """
            {
              "versionName": "1.3.0",
              "versionCode": 4,
              "title": "春季更新",
              "changelog": "新增自定义日程便签",
              "apkUrl": "https://cdn.example.com/qiubi-1.3.0.apk",
              "size": 10485760
            }
        """.trimIndent()

        val parsed = AppUpdateClient.parseCustomManifestJson(sample, "cdn.example.com")
        assertEquals("1.3.0", parsed.versionName)
        assertEquals(4, parsed.versionCode)
        assertEquals("春季更新", parsed.releaseTitle)
        assertEquals("https://cdn.example.com/qiubi-1.3.0.apk", parsed.apkDownloadUrl)
        assertEquals("10.0 MB", parsed.formattedSize())
    }

    @Test
    fun buildDownloadUrls_appliesMirrorWithDirectFallbackOnlyForGithub() {
        val ghUrl = "https://github.com/alice/qiubi-jw/releases/download/v1.1.0/app.apk"
        val mirrored = AppUpdateClient.buildDownloadUrls(ghUrl, AppSettings.UPDATE_MIRROR_GHPROXY, "")
        assertEquals(2, mirrored.size)
        assertEquals("${AppSettings.DEFAULT_GHPROXY_PREFIX}$ghUrl", mirrored[0])
        assertEquals(ghUrl, mirrored[1])

        val custom = AppUpdateClient.buildDownloadUrls(ghUrl, AppSettings.UPDATE_MIRROR_CUSTOM, "https://mirror.example.com")
        assertEquals("https://mirror.example.com/$ghUrl", custom[0])
        assertEquals(ghUrl, custom[1])

        val direct = AppUpdateClient.buildDownloadUrls(ghUrl, AppSettings.UPDATE_MIRROR_DIRECT, "")
        assertEquals(listOf(ghUrl), direct)

        val nonGh = "https://cdn.example.com/app.apk"
        assertEquals(listOf(nonGh), AppUpdateClient.buildDownloadUrls(nonGh, AppSettings.UPDATE_MIRROR_GHPROXY, ""))
    }
}
