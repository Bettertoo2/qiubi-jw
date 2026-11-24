package cn.gdou.edu.timetable.domain

import cn.gdou.edu.timetable.data.local.db.GradeEntity

data class TermGpaPoint(
    val xnm: String,
    val xqm: String,
    val label: String,
    val gpa: Double,
    val credits: Double,
    val courseCount: Int
)

data class GpaSummary(
    val weightedGpa: Double,
    val weightedAverageScore: Double,
    val totalCreditsAttempted: Double,
    val totalCreditsEarned: Double,
    val totalCourseCount: Int,
    val passedCourseCount: Int,
    val termPoints: List<TermGpaPoint>
)

/**
 * Computes credit-weighted GPA, weighted average score, earned credits, and per-semester GPA trends.
 */
object GpaCalculator {
    fun summarize(grades: List<GradeEntity>): GpaSummary {
        val valid = grades.filter { !it.invalidFlag }
        if (valid.isEmpty()) {
            return GpaSummary(0.0, 0.0, 0.0, 0.0, 0, 0, emptyList())
        }

        val totalCreditAttempted = valid.sumOf { it.credits }
        val weightedGpaSum = valid.sumOf { it.credits * it.gradePoint }
        val weightedGpa = if (totalCreditAttempted > 0.0) weightedGpaSum / totalCreditAttempted else 0.0

        val scoredCourses = valid.filter { it.numericScore != null && it.credits > 0.0 }
        val scoreCredits = scoredCourses.sumOf { it.credits }
        val weightedScore = if (scoreCredits > 0.0) {
            scoredCourses.sumOf { (it.numericScore ?: 0.0) * it.credits } / scoreCredits
        } else 0.0

        val passed = valid.filter { isPassed(it) }
        val earnedCredits = passed.sumOf { it.credits }

        val termPoints = valid
            .groupBy { it.xnm to it.xqm }
            .entries
            .sortedWith(
                compareBy<Map.Entry<Pair<String, String>, List<GradeEntity>>> { it.key.first }
                    .thenBy { it.key.second.toIntOrNull() ?: 0 }
            )
            .map { (key, list) ->
                val credits = list.sumOf { it.credits }
                val gpa = if (credits > 0.0) list.sumOf { it.credits * it.gradePoint } / credits else 0.0
                val sample = list.first()
                val shortYear = sample.yearLabel.takeIf { it.isNotBlank() } ?: key.first
                val shortTerm = sample.termLabel.takeIf { it.isNotBlank() } ?: key.second
                TermGpaPoint(
                    xnm = key.first,
                    xqm = key.second,
                    label = "${shortYear.takeLast(7)}-${shortTerm}",
                    gpa = gpa,
                    credits = credits,
                    courseCount = list.size
                )
            }

        return GpaSummary(
            weightedGpa = weightedGpa,
            weightedAverageScore = weightedScore,
            totalCreditsAttempted = totalCreditAttempted,
            totalCreditsEarned = earnedCredits,
            totalCourseCount = valid.size,
            passedCourseCount = passed.size,
            termPoints = termPoints
        )
    }

    fun isPassed(grade: GradeEntity): Boolean {
        if (grade.gradePoint > 0.0) return true
        val num = grade.numericScore
        if (num != null) return num >= 60.0
        val raw = grade.scoreRaw.trim()
        return raw in setOf("及格", "合格", "中等", "良好", "优秀", "通过", "A", "B", "C", "D", "P")
    }
}
