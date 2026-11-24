package cn.gdou.edu.timetable.data.remote

import cn.gdou.edu.timetable.data.local.db.CourseEntity
import cn.gdou.edu.timetable.data.local.db.ExamEntity
import cn.gdou.edu.timetable.data.local.db.GradeEntity
import cn.gdou.edu.timetable.data.local.db.StudentProfileEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

data class ParsedTermMetadata(
    val defaultXnm: String?,
    val defaultXqm: String?,
    val availableTerms: List<Pair<String, String>>
)

data class ParsedTimetableResult(
    val profileHint: StudentProfileEntity?,
    val courses: List<CourseEntity>
)

data class OfficialAcademicStats(
    val gpa: Double?,
    val earnedCredits: Double?
)

@Serializable
data class GradeScoreComponent(
    val name: String,
    val weight: String = "",
    val score: String
)

/**
 * Pure Kotlin parsers for GDOU Zhengfang JW (V9) JSON responses and HTML pages.
 * Designed to handle field variations gracefully and remain 100% JVM unit-testable.
 */
object GdouParsers {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private const val COLOR_COUNT = 12

    /**
     * Parses Zhengfang week descriptions such as:
     * "1-16周", "1-15周(单)", "2-16周(双)", "1-4周,7-12周", "3周,5-9周(单)", "第6周"
     */
    fun parseWeeks(zcd: String): List<Int> {
        if (zcd.isBlank()) return emptyList()
        val result = sortedSetOf<Int>()
        val normalized = zcd
            .replace("，", ",")
            .replace("、", ",")
            .replace("（", "(")
            .replace("）", ")")
            .replace("～", "-")
            .replace("~", "-")

        for (rawPart in normalized.split(",")) {
            val part = rawPart.trim()
            if (part.isEmpty()) continue
            val oddOnly = part.contains("单")
            val evenOnly = part.contains("双")

            val rangeMatch = Regex("""(\d+)\s*-\s*(\d+)""").find(part)
            if (rangeMatch != null) {
                val start = rangeMatch.groupValues[1].toIntOrNull() ?: continue
                val end = rangeMatch.groupValues[2].toIntOrNull() ?: continue
                if (start <= end) {
                    for (w in start..end) {
                        if (oddOnly && w % 2 == 0) continue
                        if (evenOnly && w % 2 != 0) continue
                        result.add(w)
                    }
                }
            } else {
                val single = Regex("""(\d+)""").find(part)?.groupValues?.get(1)?.toIntOrNull()
                if (single != null) {
                    result.add(single)
                }
            }
        }
        return result.toList()
    }

    /**
     * Parses section range strings like "1-2", "3-4", "9-11", "5", "第1-2节".
     */
    fun parseSections(jcs: String): Pair<Int, Int> {
        val nums = Regex("""\d+""").findAll(jcs).mapNotNull { it.value.toIntOrNull() }.toList()
        return when {
            nums.isEmpty() -> 1 to 2
            nums.size == 1 -> nums[0] to nums[0]
            else -> nums.first().coerceAtMost(nums.last()) to nums.first().coerceAtLeast(nums.last())
        }
    }

    /**
     * Parses Zhengfang exam time strings such as:
     * "2026-12-28(08:30-10:30)", "2026-12-28 08:30-10:30", "2026年12月28日 14:30-16:30"
     */
    fun parseExamStartTimestamp(examTimeRaw: String, zoneId: ZoneId = ZoneId.of("Asia/Shanghai")): Long? {
        if (examTimeRaw.isBlank()) return null
        val match = Regex("""(\d{4})[-/年.](\d{1,2})[-/月.](\d{1,2})\D+(\d{1,2}):(\d{2})""")
            .find(examTimeRaw) ?: return null
        val year = match.groupValues[1].toIntOrNull() ?: return null
        val month = match.groupValues[2].toIntOrNull() ?: return null
        val day = match.groupValues[3].toIntOrNull() ?: return null
        val hour = match.groupValues[4].toIntOrNull() ?: return null
        val minute = match.groupValues[5].toIntOrNull() ?: return null
        return runCatching {
            LocalDateTime.of(year, month, day, hour, minute)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }

    fun parseTimetableJson(rawJson: String, xnm: String, xqm: String): ParsedTimetableResult {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val xsxx = root["xsxx"]?.takeIf { it is JsonObject }?.jsonObject
        val profileHint = if (xsxx != null) {
            val studentId = xsxx.str("XH", "xh", "XH_ID")
            val name = xsxx.str("XM", "xm")
            if (studentId.isNotBlank() || name.isNotBlank()) {
                StudentProfileEntity(
                    studentId = studentId,
                    name = name,
                    college = xsxx.str("JGMC", "jgmc", "XYMC"),
                    major = xsxx.str("ZYMC", "zymc"),
                    className = xsxx.str("BJMC", "bjmc"),
                    grade = xsxx.str("NJDM_ID", "njdm_id", "NJMC"),
                    campus = xsxx.str("XQMC", "xqmc"),
                    currentXnm = xsxx.str("XNM", "xnm").ifBlank { xnm },
                    currentXqm = xsxx.str("XQM", "xqm").ifBlank { xqm }
                )
            } else null
        } else null

        val kbList = root["kbList"]?.jsonArray ?: emptyList()
        val colorMap = LinkedHashMap<String, Int>()
        val courses = mutableListOf<CourseEntity>()

        for (element in kbList) {
            val obj = element.jsonObject
            val courseName = obj.str("kcmc", "KCMC").ifBlank { continue }
            val courseCode = obj.str("kch_id", "kch", "KCH_ID")
            val teacher = obj.str("xm", "jsxm", "XM")
            val classroom = obj.str("cdmc", "CDMC")
            val campus = obj.str("xqmc", "cdxqmc", "XQMC")
            val dayOfWeek = obj.str("xqj", "XQJ").toIntOrNull()?.coerceIn(1, 7) ?: 1
            val jcs = obj.str("jcs", "jc", "JCS")
            val (startSec, endSec) = parseSections(jcs)
            val weeksRaw = obj.str("zcd", "ZCD")
            val weeksList = parseWeeks(weeksRaw)
            val credits = obj.str("xf", "XF")
            val courseNature = obj.str("kcxz", "kcxzmc", "KCXZ")
            val classGroup = obj.str("jxbmc", "JXBMC")
            val assessmentType = obj.str("khfsmc", "khfs", "KHFSMC")

            val colorIndex = colorMap.getOrPut(courseName) {
                abs(courseName.hashCode()) % COLOR_COUNT
            }

            courses.add(
                CourseEntity(
                    xnm = xnm,
                    xqm = xqm,
                    courseCode = courseCode,
                    courseName = courseName,
                    teacher = teacher,
                    classroom = classroom,
                    campus = campus,
                    dayOfWeek = dayOfWeek,
                    startSection = startSec,
                    endSection = endSec,
                    weeksRaw = weeksRaw,
                    weeksCsv = weeksList.joinToString(","),
                    credits = credits,
                    courseNature = courseNature,
                    classGroup = classGroup,
                    assessmentType = assessmentType,
                    colorIndex = colorIndex
                )
            )
        }

        return ParsedTimetableResult(
            profileHint = profileHint,
            courses = courses.sortedWith(compareBy<CourseEntity> { it.dayOfWeek }.thenBy { it.startSection })
        )
    }

    fun parseExamsJson(rawJson: String, xnm: String, xqm: String): List<ExamEntity> {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val items = root["items"]?.jsonArray ?: emptyList()
        val exams = mutableListOf<ExamEntity>()

        for (element in items) {
            val obj = element.jsonObject
            val courseName = obj.str("kcmc", "KCMC").ifBlank { continue }
            val examTimeRaw = obj.str("kssj", "KSSJ")
            exams.add(
                ExamEntity(
                    xnm = obj.str("xnm", "XNM").ifBlank { xnm },
                    xqm = obj.str("xqm", "XQM").ifBlank { xqm },
                    courseCode = obj.str("kch", "kch_id", "KCH"),
                    courseName = courseName,
                    examName = obj.str("ksmc", "KSMC").ifBlank { "课程考试" },
                    examTimeRaw = examTimeRaw,
                    startTimestamp = parseExamStartTimestamp(examTimeRaw),
                    classroom = obj.str("cdmc", "CDMC"),
                    campus = obj.str("cdxqmc", "xqmc", "CDXQMC"),
                    seatNumber = obj.str("zwh", "ZWH"),
                    credits = obj.str("xf", "XF"),
                    examMode = obj.str("ksfs", "khfs", "KSFS"),
                    classGroup = obj.str("jxbmc", "JXBMC")
                )
            )
        }

        return exams.sortedWith(
            compareBy<ExamEntity> { it.startTimestamp == null }
                .thenBy { it.startTimestamp ?: Long.MAX_VALUE }
                .thenBy { it.courseName }
        )
    }

    fun parseGradesJson(rawJson: String): List<GradeEntity> {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val items = root["items"]?.jsonArray ?: emptyList()
        val grades = mutableListOf<GradeEntity>()

        for (element in items) {
            val obj = element.jsonObject
            val courseName = obj.str("kcmc", "KCMC").ifBlank { continue }
            val xnm = obj.str("xnm", "XNM")
            val xqm = obj.str("xqm", "XQM")
            val yearLabel = obj.str("xnmmc", "XNMMC").ifBlank {
                val yr = xnm.toIntOrNull()
                if (yr != null) "$yr-${yr + 1}" else xnm
            }
            val termLabel = obj.str("xqmmc", "XQMMC").ifBlank {
                when (xqm) {
                    "3" -> "1"
                    "12" -> "2"
                    "16" -> "3"
                    else -> xqm
                }
            }
            val credits = obj.str("xf", "XF").toDoubleOrNull() ?: 0.0
            val scoreRaw = obj.str("cj", "CJ")
            val numericScore = obj.str("bfzcj", "BFZCJ", "cjbdz").toDoubleOrNull()
                ?: scoreRaw.toDoubleOrNull()
                ?: mapLevelScoreToNumeric(scoreRaw)
            val gradePoint = obj.str("jd", "JD").toDoubleOrNull()
                ?: estimateGpaFromScore(numericScore)
            val invalid = obj.str("cjzf", "CJZF").equals("是", ignoreCase = true) ||
                obj.str("sfxwkc").equals("作废", ignoreCase = true)

            val jxbId = obj.str("jxb_id", "JXB_ID")
            val xhId = obj.str("xh_id", "XH_ID", "xh", "XH")

            // If summary JSON already includes inline sub-scores (pscj/qzcj/sycj/qmcj), capture as initial breakdown
            val inlineComponents = buildList {
                val pscj = obj.str("pscj", "PSCJ")
                val qzcj = obj.str("qzcj", "QZCJ")
                val sycj = obj.str("sycj", "SYCJ")
                val qmcj = obj.str("qmcj", "QMCJ")
                if (pscj.isNotBlank()) add(GradeScoreComponent("平时成绩", "", pscj))
                if (qzcj.isNotBlank()) add(GradeScoreComponent("期中成绩", "", qzcj))
                if (sycj.isNotBlank()) add(GradeScoreComponent("实验成绩", "", sycj))
                if (qmcj.isNotBlank()) add(GradeScoreComponent("期末成绩", "", qmcj))
            }

            grades.add(
                GradeEntity(
                    xnm = xnm,
                    xqm = xqm,
                    yearLabel = yearLabel,
                    termLabel = termLabel,
                    courseCode = obj.str("kch", "kch_id", "KCH"),
                    courseName = courseName,
                    credits = credits,
                    scoreRaw = scoreRaw.ifBlank { numericScore?.toString() ?: "--" },
                    numericScore = numericScore,
                    gradePoint = gradePoint,
                    courseNature = obj.str("kcxzmc", "kcxz", "KCXZMC"),
                    examNature = obj.str("ksxz", "ksxzmc", "KSXZ").ifBlank { "正常考试" },
                    teacher = obj.str("jsxm", "xm", "JSXM"),
                    invalidFlag = invalid,
                    jxbId = jxbId,
                    xhId = xhId,
                    breakdownJson = if (inlineComponents.isNotEmpty()) encodeBreakdown(inlineComponents) else ""
                )
            )
        }

        return grades.sortedWith(
            compareByDescending<GradeEntity> { it.xnm }
                .thenByDescending { it.xqm.toIntOrNull() ?: 0 }
                .thenBy { it.courseName }
        )
    }

    fun parseStudentProfileHtml(
        html: String,
        fallbackStudentId: String,
        existingHint: StudentProfileEntity? = null
    ): StudentProfileEntity {
        val doc = Jsoup.parse(html)

        fun fieldByIdOrLabel(ids: List<String>, labels: List<String>): String {
            for (id in ids) {
                val el = doc.selectFirst("#$id")
                val v = el?.let {
                    if (it.tagName() == "input") it.attr("value") else it.text()
                }?.trim()
                if (!v.isNullOrBlank()) return v
            }
            // Scan Zhengfang Bootstrap form groups (<label>学院</label><p class="form-control-static">...</p>)
            for (group in doc.select(".form-group, tr")) {
                val labelText = group.selectFirst("label, th")?.text()?.replace("：", "")?.replace(":", "")?.trim()
                    ?: continue
                if (labels.any { labelText.contains(it) }) {
                    val valueEl = group.selectFirst("p.form-control-static, td, span.form-control-static, div.col-sm-8 p")
                    val text = valueEl?.text()?.trim()
                    if (!text.isNullOrBlank() && text != labelText) return text
                }
            }
            return ""
        }

        val studentId = fieldByIdOrLabel(
            ids = listOf("col_xh", "xh", "xh_id"),
            labels = listOf("学号")
        ).ifBlank { existingHint?.studentId.orEmpty() }.ifBlank { fallbackStudentId }

        val name = fieldByIdOrLabel(
            ids = listOf("col_xm", "xm"),
            labels = listOf("姓名")
        ).ifBlank {
            doc.selectFirst(".media-heading")?.text()?.trim().orEmpty()
        }.ifBlank { existingHint?.name.orEmpty() }.ifBlank { studentId }

        val gender = fieldByIdOrLabel(
            ids = listOf("col_xbm", "xbm", "xb"),
            labels = listOf("性别")
        ).ifBlank { existingHint?.gender.orEmpty() }

        val college = fieldByIdOrLabel(
            ids = listOf("col_jg_id", "jgmc", "col_xy", "xy"),
            labels = listOf("学院", "院系")
        ).ifBlank { existingHint?.college.orEmpty() }

        val major = fieldByIdOrLabel(
            ids = listOf("col_zyh_id", "zymc", "col_zy"),
            labels = listOf("专业名称", "专业")
        ).ifBlank { existingHint?.major.orEmpty() }

        val className = fieldByIdOrLabel(
            ids = listOf("col_bh_id", "bjmc", "col_bj"),
            labels = listOf("班级", "行政班")
        ).ifBlank { existingHint?.className.orEmpty() }

        val grade = fieldByIdOrLabel(
            ids = listOf("col_njdm_id", "njdm_id", "njmc"),
            labels = listOf("年级")
        ).ifBlank { existingHint?.grade.orEmpty() }

        val campus = fieldByIdOrLabel(
            ids = listOf("col_xqh_id", "xqmc"),
            labels = listOf("校区")
        ).ifBlank { existingHint?.campus.orEmpty() }

        val status = fieldByIdOrLabel(
            ids = listOf("col_xjztm", "xjzt"),
            labels = listOf("学籍状态")
        ).ifBlank { existingHint?.studentStatus.orEmpty() }.ifBlank { "在籍" }

        return StudentProfileEntity(
            studentId = studentId,
            name = name,
            gender = gender,
            college = college,
            major = major,
            className = className,
            grade = grade,
            campus = campus,
            studentStatus = status,
            officialGpa = existingHint?.officialGpa,
            officialEarnedCredits = existingHint?.officialEarnedCredits,
            currentXnm = existingHint?.currentXnm.orEmpty(),
            currentXqm = existingHint?.currentXqm.orEmpty(),
            updatedAt = System.currentTimeMillis()
        )
    }

    fun parseOfficialGpaHtml(html: String): OfficialAcademicStats {
        val text = Jsoup.parse(html).text()
        val gpaMatch = Regex("""(?:平均学分绩点|GPA)[^0-9]{0,12}(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
            .find(text)
        val creditMatch = Regex("""(?:已获(?:得)?学分|获得总学分|已修学分)[^0-9]{0,12}(\d+(?:\.\d+)?)""")
            .find(text)
        return OfficialAcademicStats(
            gpa = gpaMatch?.groupValues?.getOrNull(1)?.toDoubleOrNull(),
            earnedCredits = creditMatch?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        )
    }

    fun parseTermOptionsHtml(html: String): ParsedTermMetadata {
        val doc = Jsoup.parse(html)
        val xnmSelect = doc.selectFirst("#xnm")
        val xqmSelect = doc.selectFirst("#xqm")

        val defaultXnm = xnmSelect?.selectFirst("option[selected]")?.attr("value")
            ?.takeIf { it.isNotBlank() }
        val defaultXqm = xqmSelect?.selectFirst("option[selected]")?.attr("value")
            ?.takeIf { it.isNotBlank() }

        val anchorYear = defaultXnm?.toIntOrNull()
            ?: cn.gdou.edu.timetable.data.local.AppSettings.defaultXnm().toIntOrNull()
            ?: java.time.LocalDate.now().year

        // Keep both upcoming/future academic years (for course selection) and past academic years
        val years = xnmSelect?.select("option")
            ?.mapNotNull { opt -> opt.attr("value").trim().toIntOrNull() }
            ?.filter { y -> y in (anchorYear - 6)..(anchorYear + 5) }
            ?.distinct()
            ?.sortedDescending()
            ?.map { it.toString() }
            .orEmpty()
            .ifEmpty { ((anchorYear + 2) downTo (anchorYear - 4)).map { it.toString() } }

        val terms = xqmSelect?.select("option")
            ?.mapNotNull { opt -> opt.attr("value").trim().takeIf { it.isNotBlank() } }
            ?.distinct()
            .orEmpty()
            .ifEmpty { listOf("3", "12") }

        val pairs = mutableListOf<Pair<String, String>>()
        for (y in years) {
            for (t in terms) {
                pairs.add(y to t)
            }
        }
        return ParsedTermMetadata(
            defaultXnm = defaultXnm,
            defaultXqm = defaultXqm,
            availableTerms = pairs
        )
    }

    fun encodeBreakdown(components: List<GradeScoreComponent>): String =
        json.encodeToString(components)

    fun decodeBreakdown(rawJson: String): List<GradeScoreComponent> {
        if (rawJson.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<GradeScoreComponent>>(rawJson) }.getOrDefault(emptyList())
    }

    /**
     * Parses Zhengfang V9 grade breakdown responses from cjcx_cxCjxqGjh.html / cjcx_cxCjxq.html.
     * Supports:
     * 1) 3-column HTML tables: 成绩分项 | 成绩分项比例 | 成绩
     * 2) 2-column HTML tables: 【 平时(30%) 】 | 92
     * 3) JSON payloads containing items with xmblmc / xmcj
     */
    fun parseGradeBreakdownResponse(rawBody: String): List<GradeScoreComponent> {
        val trimmed = rawBody.trim()
        if (trimmed.isEmpty()) return emptyList()

        // JSON fallback
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            val fromJson = runCatching {
                val root = json.parseToJsonElement(trimmed).jsonObject
                val items = root["items"]?.jsonArray ?: emptyList()
                items.mapNotNull { el ->
                    val o = el.jsonObject
                    val rawLabel = o.str("xmblmc", "cjfx", "xmmc", "XMBLMC")
                    val weight = o.str("xmbl", "bl", "CJFXBL")
                    val score = o.str("xmcj", "cj", "XMCJ")
                    if (rawLabel.isBlank() && score.isBlank()) null
                    else normalizeComponent(rawLabel, weight, score)
                }
            }.getOrDefault(emptyList())
            if (fromJson.isNotEmpty()) return fromJson
        }

        val doc = Jsoup.parse(trimmed)
        val candidateTables = buildList {
            doc.selectFirst("#subtab")?.let { add(it) }
            doc.select("table").forEach { table ->
                if (table.id() != "subtab") add(table)
            }
        }
        if (candidateTables.isEmpty()) return emptyList()

        val sortedTables = candidateTables.sortedByDescending { table ->
            val t = table.text()
            var score = 0
            if (table.id() == "subtab") score += 100
            if (t.contains("成绩分项") || t.contains("成绩分项比例")) score += 50
            if (t.contains("平时") || t.contains("期末") || t.contains("总评") || t.contains("实验")) score += 20
            score
        }

        for (table in sortedTables) {
            val parsed = parseBreakdownTable(table)
            if (parsed.isNotEmpty()) return parsed
        }
        return emptyList()
    }

    private fun parseBreakdownTable(table: org.jsoup.nodes.Element): List<GradeScoreComponent> {
        val rows = table.select("tbody tr").ifEmpty { table.select("tr") }
        val result = mutableListOf<GradeScoreComponent>()

        for (row in rows) {
            // Skip pure header rows (all <th>, no <td>)
            if (row.select("td").isEmpty()) continue
            val cells = row.select("th, td")
            if (cells.size < 2) continue

            val component = if (cells.size >= 3) {
                val rawName = cells[0].text().trim()
                val rawWeight = cells[1].text().trim()
                val rawScore = cells[2].text().trim()
                if (isBreakdownHeader(rawName)) continue
                normalizeComponent(rawName, rawWeight, rawScore)
            } else {
                val rawName = cells[0].text().trim()
                val rawScore = cells[1].text().trim()
                if (isBreakdownHeader(rawName)) continue
                normalizeComponent(rawName, "", rawScore)
            }
            if (component.name.isNotBlank() && component.score.isNotBlank()) {
                result.add(component)
            }
        }
        return result
    }

    private fun isBreakdownHeader(rawName: String): Boolean =
        rawName.contains("成绩分项") || rawName == "名称" || rawName == "项目" || rawName == "分项"

    private fun normalizeComponent(rawName: String, rawWeight: String, rawScore: String): GradeScoreComponent {
        val cleaned = rawName
            .replace("【", "")
            .replace("】", "")
            .replace("[", "")
            .replace("]", "")
            .replace("：", "")
            .replace(":", "")
            .trim()

        var finalName = cleaned
        var finalWeight = rawWeight.trim()

        // Extract inline percentage e.g. "平时(30%)" or "期末（60%）"
        val pctMatch = Regex("""[（(]\s*(\d+(?:\.\d+)?%?)\s*[)）]""").find(cleaned)
        if (pctMatch != null) {
            val extracted = pctMatch.groupValues[1].let { if (it.endsWith("%")) it else "$it%" }
            if (finalWeight.isBlank()) finalWeight = extracted
            finalName = cleaned.replace(pctMatch.value, "").trim()
        } else if (finalWeight.isNotBlank() && finalWeight.matches(Regex("""\d+(?:\.\d+)?"""))) {
            finalWeight = "$finalWeight%"
        }

        return GradeScoreComponent(
            name = finalName.ifBlank { cleaned },
            weight = finalWeight,
            score = rawScore.trim()
        )
    }

    private fun mapLevelScoreToNumeric(scoreRaw: String): Double? = when (scoreRaw.trim()) {
        "优秀", "优", "A", "A+" -> 95.0
        "良好", "良", "B", "B+" -> 85.0
        "中等", "中", "C", "C+" -> 75.0
        "及格", "合格", "通过", "D", "P" -> 65.0
        "不及格", "不合格", "不通过", "F", "旷考", "缺考" -> 0.0
        else -> null
    }

    private fun estimateGpaFromScore(score: Double?): Double {
        if (score == null) return 0.0
        return if (score < 60.0) 0.0 else ((score - 50.0) / 10.0).coerceAtLeast(0.0)
    }

    private fun JsonObject.str(vararg keys: String): String {
        for (k in keys) {
            val v = this[k]?.jsonPrimitive?.contentOrNull?.trim()
            if (!v.isNullOrEmpty() && !v.equals("null", ignoreCase = true)) {
                return v
            }
        }
        return ""
    }
}
