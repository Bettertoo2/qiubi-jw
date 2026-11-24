package cn.gdou.edu.timetable.domain

import cn.gdou.edu.timetable.data.local.db.CourseEntity
import kotlin.math.abs

/**
 * Represents a single rendered block in the weekly timetable grid with guaranteed zero text overlap.
 * If multiple courses share the same day & section range (e.g., different weeks when showing
 * non-current-week courses, or conflicting electives), [primary] is rendered cleanly and
 * [extraCourses] are surfaced via a "+N" badge and listed in the detail bottom sheet on tap.
 */
data class TimetableDisplayBlock(
    val primary: CourseEntity,
    val isActiveInSelectedWeek: Boolean,
    val extraCourses: List<CourseEntity> = emptyList()
) {
    val allCourses: List<CourseEntity> get() = listOf(primary) + extraCourses
}

object TimetableSlotResolver {

    fun resolve(
        courses: List<CourseEntity>,
        selectedWeek: Int,
        showNonCurrentWeek: Boolean
    ): List<TimetableDisplayBlock> {
        val result = mutableListOf<TimetableDisplayBlock>()

        for (day in 1..7) {
            val dayCourses = courses
                .filter { it.dayOfWeek == day }
                .sortedWith(compareBy<CourseEntity> { it.startSection }.thenBy { it.endSection })
            if (dayCourses.isEmpty()) continue

            val active = dayCourses.filter { it.isActiveInWeek(selectedWeek) }
            val inactive = if (showNonCurrentWeek) {
                dayCourses.filter { !it.isActiveInWeek(selectedWeek) }
            } else {
                emptyList()
            }

            // 1. Cluster active courses so active slots never draw garbled text over each other
            val activeClusters = clusterOverlapping(active)
            val consumedInactive = HashSet<CourseEntity>()

            for (cluster in activeClusters) {
                val primary = cluster.first()
                val clusterExtras = mutableListOf<CourseEntity>()
                clusterExtras.addAll(cluster.drop(1))

                // Absorb any non-current-week courses overlapping this active cluster so they NEVER cover or bleed through it
                val overlappingInactive = inactive.filter { cand ->
                    cand !in consumedInactive && cluster.any { overlaps(it, cand) }
                }
                consumedInactive.addAll(overlappingInactive)
                clusterExtras.addAll(sortByNearestWeek(overlappingInactive, selectedWeek))

                result.add(
                    TimetableDisplayBlock(
                        primary = primary,
                        isActiveInSelectedWeek = true,
                        extraCourses = clusterExtras
                    )
                )
            }

            // 2. For remaining inactive courses (empty slots in selectedWeek), pick 1 clean representative per overlapping cluster
            val remainingInactive = inactive.filter { it !in consumedInactive }
            val inactiveClusters = clusterOverlapping(remainingInactive)
            for (cluster in inactiveClusters) {
                val ordered = sortByNearestWeek(cluster, selectedWeek)
                result.add(
                    TimetableDisplayBlock(
                        primary = ordered.first(),
                        isActiveInSelectedWeek = false,
                        extraCourses = ordered.drop(1)
                    )
                )
            }
        }

        return result.sortedWith(
            compareBy<TimetableDisplayBlock> { it.primary.dayOfWeek }
                .thenBy { it.primary.startSection }
        )
    }

    private fun overlaps(a: CourseEntity, b: CourseEntity): Boolean =
        a.startSection <= b.endSection && b.startSection <= a.endSection

    private fun clusterOverlapping(sortedCourses: List<CourseEntity>): List<List<CourseEntity>> {
        if (sortedCourses.isEmpty()) return emptyList()
        val clusters = mutableListOf<MutableList<CourseEntity>>()
        var currentCluster = mutableListOf(sortedCourses.first())
        var clusterMaxEnd = sortedCourses.first().endSection

        for (i in 1 until sortedCourses.size) {
            val c = sortedCourses[i]
            if (c.startSection <= clusterMaxEnd) {
                currentCluster.add(c)
                if (c.endSection > clusterMaxEnd) clusterMaxEnd = c.endSection
            } else {
                clusters.add(currentCluster)
                currentCluster = mutableListOf(c)
                clusterMaxEnd = c.endSection
            }
        }
        clusters.add(currentCluster)
        return clusters
    }

    /**
     * Prefers the course whose next upcoming week (>= selectedWeek) is closest;
     * otherwise picks the course whose latest week is closest to selectedWeek.
     */
    private fun sortByNearestWeek(list: List<CourseEntity>, selectedWeek: Int): List<CourseEntity> {
        return list.sortedWith(
            compareBy<CourseEntity> { course ->
                val weeks = course.weekSet()
                val nextUpcoming = weeks.filter { it >= selectedWeek }.minOrNull()
                if (nextUpcoming != null) 0 else 1
            }.thenBy { course ->
                val weeks = course.weekSet()
                weeks.minOfOrNull { abs(it - selectedWeek) } ?: Int.MAX_VALUE
            }.thenBy { it.startSection }
                .thenBy { it.courseName }
        )
    }
}
