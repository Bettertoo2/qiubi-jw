package cn.gdou.edu.timetable.data.remote

import android.util.Log
import cn.gdou.edu.timetable.data.local.db.CourseEntity
import cn.gdou.edu.timetable.data.local.db.ExamEntity
import cn.gdou.edu.timetable.data.local.db.GradeEntity
import cn.gdou.edu.timetable.data.local.db.StudentProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

class SessionExpiredException(message: String = "教务系统登录会话已过期") : Exception(message)

data class RemoteStudentBundle(
    val profile: StudentProfileEntity,
    val courses: List<CourseEntity>,
    val exams: List<ExamEntity>,
    val grades: List<GradeEntity>,
    val termMetadata: ParsedTermMetadata,
    val effectiveXnm: String,
    val effectiveXqm: String
)

/**
 * Fetches student timetable, exam schedule, grades/GPA, and profile from GDOU Zhengfang JW:
 * https://jw.gdou.edu.cn/ (mounted directly at root path `/`).
 */
class GdouJwApiService(
    private val httpClient: OkHttpClient
) {
    suspend fun fetchAllStudentData(
        requestedXnm: String,
        requestedXqm: String,
        fallbackStudentId: String,
        autoDetectCurrentTerm: Boolean
    ): RemoteStudentBundle = withContext(Dispatchers.IO) {
        // 1. Verify active session & discover default term/options via timetable index page
        val termIndexHtml = getText("$BASE_URL/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default")
        val termMeta = GdouParsers.parseTermOptionsHtml(termIndexHtml)

        val effectiveXnm = if (autoDetectCurrentTerm && !termMeta.defaultXnm.isNullOrBlank()) {
            termMeta.defaultXnm
        } else {
            requestedXnm.ifBlank { termMeta.defaultXnm.orEmpty() }
        }
        val effectiveXqm = if (autoDetectCurrentTerm && !termMeta.defaultXqm.isNullOrBlank()) {
            termMeta.defaultXqm
        } else {
            requestedXqm.ifBlank { termMeta.defaultXqm.orEmpty() }
        }

        // 2. Fetch Timetable (primary + fallback Zhengfang V9 endpoints)
        val timetableJson = fetchFirstValidJson(
            endpoints = listOf(
                "$BASE_URL/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151",
                "$BASE_URL/kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151"
            ),
            formParams = mapOf(
                "xnm" to effectiveXnm,
                "xqm" to effectiveXqm,
                "kzlx" to "ck",
                "xsdm" to ""
            )
        )
        val parsedTimetable = if (timetableJson != null) {
            GdouParsers.parseTimetableJson(timetableJson, effectiveXnm, effectiveXqm)
        } else {
            ParsedTimetableResult(null, emptyList())
        }

        // 3. Fetch Exam Schedule for selected semester
        val examsJson = fetchFirstValidJson(
            endpoints = listOf(
                "$BASE_URL/kwgl/kscx_cxXsksxxIndex.html?doType=query&gnmkdm=N358105",
                "$BASE_URL/kwgl/kscx_cxXsksxxIndex.html?doType=query&gnmkdm=N358105&su=$fallbackStudentId"
            ),
            formParams = mapOf(
                "xnm" to effectiveXnm,
                "xqm" to effectiveXqm,
                "ksmcdmb_id" to "",
                "kch" to "",
                "kc" to "",
                "ksrq" to "",
                "kkbm_id" to "",
                "_search" to "false",
                "nd" to System.currentTimeMillis().toString(),
                "queryModel.showCount" to "100",
                "queryModel.currentPage" to "1",
                "queryModel.sortName" to "",
                "queryModel.sortOrder" to "asc",
                "time" to "0"
            )
        )
        val exams = if (examsJson != null) {
            GdouParsers.parseExamsJson(examsJson, effectiveXnm, effectiveXqm)
        } else {
            emptyList()
        }

        // 4. Fetch ALL Grades (empty xnm/xqm returns full academic history for accurate cumulative GPA)
        val gradesJson = fetchFirstValidJson(
            endpoints = listOf(
                "$BASE_URL/cjcx/cjcx_cxXsgrcj.html?doType=query&gnmkdm=N305005",
                "$BASE_URL/cjcx/cjcx_cxDgXscj.html?doType=query&gnmkdm=N305005",
                "$BASE_URL/cjcx/cjcx_cxXsKccjList.html?doType=query&gnmkdm=N305007"
            ),
            formParams = mapOf(
                "xnm" to "",
                "xqm" to "",
                "_search" to "false",
                "nd" to System.currentTimeMillis().toString(),
                "queryModel.showCount" to "500",
                "queryModel.currentPage" to "1",
                "queryModel.sortName" to "",
                "queryModel.sortOrder" to "asc",
                "time" to "0"
            )
        )
        val grades = if (gradesJson != null) {
            GdouParsers.parseGradesJson(gradesJson)
        } else {
            emptyList()
        }

        // 5. Fetch detailed Student Profile & optional official GPA page (best-effort)
        val profileHtml = runCatching {
            getText("$BASE_URL/xsxxxggl/xsgrxxwh_cxXsgrxx.html?gnmkdm=N100801&layout=default")
        }.getOrElse {
            runCatching {
                getText("$BASE_URL/xtgl/index_cxYhxxIndex.html?xt=jw&localeKey=zh_CN")
            }.getOrDefault("")
        }

        val officialStats = runCatching {
            val xyHtml = getText("$BASE_URL/xsxy/xsxyqk_cxXsxyqkIndex.html?gnmkdm=N105515&layout=default")
            GdouParsers.parseOfficialGpaHtml(xyHtml)
        }.getOrNull()

        val mergedProfile = GdouParsers.parseStudentProfileHtml(
            html = profileHtml,
            fallbackStudentId = fallbackStudentId,
            existingHint = parsedTimetable.profileHint
        ).copy(
            officialGpa = officialStats?.gpa,
            officialEarnedCredits = officialStats?.earnedCredits,
            currentXnm = effectiveXnm,
            currentXqm = effectiveXqm,
            updatedAt = System.currentTimeMillis()
        )

        Log.d(
            "GdouSync",
            "Sync bundle parsed: term=$effectiveXnm/$effectiveXqm courses=${parsedTimetable.courses.size} exams=${exams.size} grades=${grades.size} (withJxbId=${grades.count { it.jxbId.isNotBlank() }}) officialGpa=${officialStats?.gpa} profileFound=${mergedProfile.name.isNotBlank()}"
        )

        RemoteStudentBundle(
            profile = mergedProfile,
            courses = parsedTimetable.courses,
            exams = exams,
            grades = grades,
            termMetadata = termMeta,
            effectiveXnm = effectiveXnm,
            effectiveXqm = effectiveXqm
        )
    }

    /**
     * Fetches the per-component score breakdown (平时/实验/期中/期末/总评 + weights) for a single grade
     * from GDOU Zhengfang V9 endpoints (`cjcx_cxCjxqGjh.html` with fallback `cjcx_cxCjxq.html`).
     */
    suspend fun fetchGradeBreakdown(
        grade: GradeEntity,
        fallbackStudentId: String
    ): List<GradeScoreComponent> = withContext(Dispatchers.IO) {
        val ts = System.currentTimeMillis()
        val suParam = if (fallbackStudentId.isNotBlank()) "&su=$fallbackStudentId" else ""
        val effectiveXhId = grade.xhId.ifBlank { fallbackStudentId }

        val endpoints = listOf(
            "$BASE_URL/cjcx/cjcx_cxCjxqGjh.html?time=$ts&gnmkdm=N305005$suParam",
            "$BASE_URL/cjcx/cjcx_cxCjxq.html?time=$ts&gnmkdm=N305005$suParam"
        )

        val formParams = mapOf(
            "xh_id" to effectiveXhId,
            "xnm" to grade.xnm,
            "xqm" to grade.xqm,
            "jxb_id" to grade.jxbId,
            "kcmc" to grade.courseName,
            "kch_id" to grade.courseCode
        )

        var anyHttpSuccess = false
        var lastException: Exception? = null

        for (url in endpoints) {
            val formBuilder = FormBody.Builder()
            for ((k, v) in formParams) {
                formBuilder.add(k, v)
            }
            val req = Request.Builder()
                .url(url)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "text/html, application/json, text/javascript, */*; q=0.01")
                .header("Referer", "$BASE_URL/cjcx/cjcx_cxDgXscj.html?gnmkdm=N305005&layout=default")
                .post(formBuilder.build())
                .build()

            try {
                httpClient.newCall(req).execute().use { resp ->
                    val finalUrl = resp.request.url.toString()
                    val body = resp.body?.string().orEmpty()
                    checkSessionNotExpired(finalUrl, body, resp.code)

                    if (resp.isSuccessful) {
                        anyHttpSuccess = true
                        val parsed = GdouParsers.parseGradeBreakdownResponse(body)
                        Log.d(
                            "GdouSync",
                            "Grade breakdown POST $url -> http=${resp.code} len=${body.length} jxbIdLen=${grade.jxbId.length} parsed=${parsed.size} hasSubtab=${body.contains("subtab")}"
                        )
                        if (parsed.isNotEmpty()) {
                            return@withContext parsed
                        }
                    } else {
                        Log.w(
                            "GdouSync",
                            "Grade breakdown POST $url -> http=${resp.code} len=${body.length}"
                        )
                    }
                }
            } catch (expired: SessionExpiredException) {
                throw expired
            } catch (e: Exception) {
                lastException = e
                Log.w("GdouSync", "Grade breakdown POST $url failed: ${e.javaClass.simpleName}: ${e.message}")
            }
        }

        if (!anyHttpSuccess && lastException != null) {
            throw lastException
        }
        emptyList()
    }

    private fun getText(url: String): String {
        val req = Request.Builder()
            .url(url)
            .header("Referer", "$BASE_URL/xtgl/index_initMenu.html")
            .get()
            .build()
        httpClient.newCall(req).execute().use { resp ->
            val finalUrl = resp.request.url.toString()
            val body = resp.body?.string().orEmpty()
            checkSessionNotExpired(finalUrl, body, resp.code)
            return body
        }
    }

    private fun fetchFirstValidJson(
        endpoints: List<String>,
        formParams: Map<String, String>
    ): String? {
        for (url in endpoints) {
            val formBuilder = FormBody.Builder()
            for ((k, v) in formParams) {
                formBuilder.add(k, v)
            }
            val req = Request.Builder()
                .url(url)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("Referer", "$BASE_URL/xtgl/index_initMenu.html")
                .post(formBuilder.build())
                .build()

            try {
                httpClient.newCall(req).execute().use { resp ->
                    val finalUrl = resp.request.url.toString()
                    val body = resp.body?.string().orEmpty().trim()
                    checkSessionNotExpired(finalUrl, body, resp.code)
                    Log.d("GdouSync", "POST $url -> http=${resp.code} final=$finalUrl len=${body.length} isJson=${body.startsWith("{")}")
                    if (resp.isSuccessful && body.startsWith("{") && body.endsWith("}")) {
                        return body
                    }
                }
            } catch (expired: SessionExpiredException) {
                throw expired
            } catch (e: Exception) {
                Log.w("GdouSync", "POST $url failed: ${e.javaClass.simpleName}: ${e.message}")
                // Try next fallback endpoint
            }
        }
        return null
    }

    private fun checkSessionNotExpired(finalUrl: String, body: String, statusCode: Int) {
        if (statusCode == 401 ||
            finalUrl.contains("login_slogin.html") ||
            finalUrl.contains("authserver.gdou.edu.cn") ||
            body.contains("pwdEncryptSalt") ||
            body.contains("用户登录") && body.contains("login_slogin")
        ) {
            throw SessionExpiredException("教务系统会话已过期，正在尝试重新认证")
        }
    }

    companion object {
        const val BASE_URL = "https://jw.gdou.edu.cn"
    }
}
