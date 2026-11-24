package cn.gdou.edu.timetable.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GdouParsersTest {

    @Test
    fun parseWeeks_handlesRangesOddEvenAndCompoundSpecs() {
        assertEquals((1..16).toList(), GdouParsers.parseWeeks("1-16周"))
        assertEquals(listOf(1, 3, 5, 7, 9, 11, 13, 15), GdouParsers.parseWeeks("1-15周(单)"))
        assertEquals(listOf(2, 4, 6, 8), GdouParsers.parseWeeks("2-8周(双)"))
        assertEquals(listOf(1, 2, 3, 7, 9, 11, 15), GdouParsers.parseWeeks("1-3周,7-11周(单),第15周"))
    }

    @Test
    fun parseSections_extractsStartAndEnd() {
        assertEquals(1 to 2, GdouParsers.parseSections("1-2"))
        assertEquals(9 to 11, GdouParsers.parseSections("第9-11节"))
        assertEquals(5 to 5, GdouParsers.parseSections("5"))
    }

    @Test
    fun parseExamStartTimestamp_parsesStandardZhengfangFormat() {
        val ts1 = GdouParsers.parseExamStartTimestamp("2026-12-28(08:30-10:30)")
        val ts2 = GdouParsers.parseExamStartTimestamp("2026-12-28 08:30-10:30")
        assertNotNull(ts1)
        assertEquals(ts1, ts2)
    }

    @Test
    fun parseTimetableJson_extractsProfileHintAndCourses() {
        val sampleJson = """
            {
              "xsxx": {
                "XH": "202411701208",
                "XM": "陈同学",
                "JGMC": "数学与计算机学院",
                "ZYMC": "软件工程",
                "BJMC": "软件1242",
                "NJDM_ID": "2024",
                "XNM": "2026",
                "XQM": "3"
              },
              "kbList": [
                {
                  "kch_id": "CS301",
                  "kcmc": "数据结构与算法",
                  "xm": "陈教授",
                  "cdmc": "广学楼304",
                  "xqmc": "湖光校区",
                  "xqj": "1",
                  "jcs": "1-2",
                  "zcd": "1-16周",
                  "xf": "4.0",
                  "kcxz": "专业必修",
                  "jxbmc": "软件1242-01",
                  "khfsmc": "考试"
                }
              ]
            }
        """.trimIndent()

        val result = GdouParsers.parseTimetableJson(sampleJson, "2026", "3")
        assertEquals("陈同学", result.profileHint?.name)
        assertEquals("数学与计算机学院", result.profileHint?.college)
        assertEquals(1, result.courses.size)
        val c = result.courses.first()
        assertEquals("数据结构与算法", c.courseName)
        assertEquals(1, c.dayOfWeek)
        assertEquals(1, c.startSection)
        assertEquals(2, c.endSection)
        assertTrue(c.isActiveInWeek(8))
    }

    @Test
    fun parseExamsAndGradesJson_parsesFieldsAccurately() {
        val examJson = """
            {
              "items": [
                {
                  "xnm": "2026",
                  "xqm": "3",
                  "kch": "CS301",
                  "kcmc": "数据结构与算法",
                  "ksmc": "期末考试",
                  "kssj": "2026-12-28(08:30-10:30)",
                  "cdmc": "钟海楼02020",
                  "cdxqmc": "湖光校区",
                  "zwh": "18",
                  "xf": "4.0",
                  "ksfs": "闭卷"
                }
              ]
            }
        """.trimIndent()
        val exams = GdouParsers.parseExamsJson(examJson, "2026", "3")
        assertEquals(1, exams.size)
        assertEquals("18", exams.first().seatNumber)
        assertNotNull(exams.first().startTimestamp)

        val gradeJson = """
            {
              "items": [
                {
                  "xnm": "2025",
                  "xqm": "3",
                  "xnmmc": "2025-2026",
                  "xqmmc": "1",
                  "kch": "MA101",
                  "kcmc": "高等数学A",
                  "xf": "5.0",
                  "cj": "92",
                  "bfzcj": "92",
                  "jd": "4.2",
                  "kcxzmc": "公共必修",
                  "ksxz": "正常考试",
                  "jsxm": "林老师",
                  "jxb_id": "JXB20250001",
                  "xh_id": "202411701208"
                }
              ]
            }
        """.trimIndent()
        val grades = GdouParsers.parseGradesJson(gradeJson)
        assertEquals(1, grades.size)
        assertEquals(4.2, grades.first().gradePoint, 0.001)
        assertEquals(5.0, grades.first().credits, 0.001)
        assertEquals("JXB20250001", grades.first().jxbId)
        assertEquals("202411701208", grades.first().xhId)
    }

    @Test
    fun parseGradeBreakdownResponse_handlesThreeColTwoColThTdAndJson() {
        val threeColHtml = """
            <table id="subtab" class="table">
              <thead><tr><th>成绩分项</th><th>成绩分项比例</th><th>成绩</th></tr></thead>
              <tbody>
                <tr><td>平时</td><td>30%</td><td>92</td></tr>
                <tr><td>实验</td><td>10</td><td>95</td></tr>
                <tr><td>期末</td><td>60%</td><td>88</td></tr>
                <tr><td>总评</td><td></td><td>90</td></tr>
              </tbody>
            </table>
        """.trimIndent()
        val threeCol = GdouParsers.parseGradeBreakdownResponse(threeColHtml)
        assertEquals(4, threeCol.size)
        assertEquals(GradeScoreComponent("平时", "30%", "92"), threeCol[0])
        assertEquals(GradeScoreComponent("实验", "10%", "95"), threeCol[1])
        assertEquals(GradeScoreComponent("期末", "60%", "88"), threeCol[2])
        assertEquals(GradeScoreComponent("总评", "", "90"), threeCol[3])

        val twoColHtml = """
            <div class="table-responsive">
              <table class="table table-bordered">
                <tbody>
                  <tr><td>【 平时(30%) 】</td><td>92</td></tr>
                  <tr><td>【 期末（70%） 】</td><td>88</td></tr>
                  <tr><td>【 总评 】</td><td>89</td></tr>
                </tbody>
              </table>
            </div>
        """.trimIndent()
        val twoCol = GdouParsers.parseGradeBreakdownResponse(twoColHtml)
        assertEquals(3, twoCol.size)
        assertEquals(GradeScoreComponent("平时", "30%", "92"), twoCol[0])
        assertEquals(GradeScoreComponent("期末", "70%", "88"), twoCol[1])
        assertEquals(GradeScoreComponent("总评", "", "89"), twoCol[2])

        val thTdHtml = """
            <table>
              <tr><th>平时成绩(40%)</th><td>90</td></tr>
              <tr><th>期末成绩(60%)</th><td>85</td></tr>
            </table>
        """.trimIndent()
        val thTd = GdouParsers.parseGradeBreakdownResponse(thTdHtml)
        assertEquals(2, thTd.size)
        assertEquals(GradeScoreComponent("平时成绩", "40%", "90"), thTd[0])
        assertEquals(GradeScoreComponent("期末成绩", "60%", "85"), thTd[1])

        val jsonPayload = """
            {
              "items": [
                {"xmblmc": "平时(30%)", "xmcj": "94"},
                {"xmblmc": "期末(70%)", "xmcj": "86"},
                {"xmblmc": "总评", "xmcj": "88"}
              ]
            }
        """.trimIndent()
        val fromJson = GdouParsers.parseGradeBreakdownResponse(jsonPayload)
        assertEquals(3, fromJson.size)
        assertEquals(GradeScoreComponent("平时", "30%", "94"), fromJson[0])
        assertEquals(GradeScoreComponent("期末", "70%", "86"), fromJson[1])

        // Round-trip serialization
        val encoded = GdouParsers.encodeBreakdown(threeCol)
        assertEquals(threeCol, GdouParsers.decodeBreakdown(encoded))
    }

    @Test
    fun parseOfficialGpaAndProfileHtml_extractsValues() {
        val gpaHtml = "<div>当前平均学分绩点(GPA)：3.84，已获得学分：72.5</div>"
        val stats = GdouParsers.parseOfficialGpaHtml(gpaHtml)
        assertEquals(3.84, stats.gpa ?: 0.0, 0.001)
        assertEquals(72.5, stats.earnedCredits ?: 0.0, 0.001)

        val profileHtml = """
            <div class="form-group"><label>学号</label><p id="col_xh" class="form-control-static">202411701208</p></div>
            <div class="form-group"><label>姓名</label><p id="col_xm" class="form-control-static">李明</p></div>
            <div class="form-group"><label>学院</label><p id="col_jg_id" class="form-control-static">海洋与气象学院</p></div>
            <div class="form-group"><label>专业</label><p id="col_zyh_id" class="form-control-static">海洋科学</p></div>
        """.trimIndent()
        val profile = GdouParsers.parseStudentProfileHtml(profileHtml, "202411701208")
        assertEquals("李明", profile.name)
        assertEquals("海洋与气象学院", profile.college)
        assertEquals("海洋科学", profile.major)
    }

    @Test
    fun parseTermOptionsHtml_preservesCurrentPastAndFutureYears() {
        val html = """
            <select id="xnm">
                <option value="2031">2031-2032</option>
                <option value="2030">2030-2031</option>
                <option value="2029">2029-2030</option>
                <option value="2028">2028-2029</option>
                <option value="2027">2027-2028</option>
                <option value="2026" selected="selected">2026-2027</option>
                <option value="2025">2025-2026</option>
                <option value="2024">2024-2025</option>
            </select>
            <select id="xqm">
                <option value="3" selected="selected">1</option>
                <option value="12">2</option>
            </select>
        """.trimIndent()
        val meta = GdouParsers.parseTermOptionsHtml(html)
        assertEquals("2026", meta.defaultXnm)
        assertEquals("3", meta.defaultXqm)
        assertTrue(meta.availableTerms.contains("2026" to "3"))
        assertTrue(meta.availableTerms.contains("2025" to "12"))
        assertTrue(meta.availableTerms.contains("2027" to "3"))
        assertTrue(meta.availableTerms.contains("2031" to "12"))
    }
}
