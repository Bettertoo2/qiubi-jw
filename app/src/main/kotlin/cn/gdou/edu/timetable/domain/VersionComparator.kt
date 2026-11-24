package cn.gdou.edu.timetable.domain

/**
 * Semantic version parser and comparator supporting tags like `v1.2.3`, `1.10.0`,
 * `v2.0-beta.1`, and optional integer Android versionCode comparison.
 */
object VersionComparator {

    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("v", ignoreCase = true) && trimmed.length > 1 && trimmed[1].isDigit()) {
            trimmed.substring(1)
        } else {
            trimmed
        }
    }

    fun isNewer(
        remoteVersion: String,
        currentVersion: String,
        remoteVersionCode: Int? = null,
        currentVersionCode: Int? = null
    ): Boolean {
        if (remoteVersionCode != null && currentVersionCode != null && remoteVersionCode != currentVersionCode) {
            return remoteVersionCode > currentVersionCode
        }
        return compare(remoteVersion, currentVersion) > 0
    }

    fun compare(a: String, b: String): Int {
        val normA = normalize(a)
        val normB = normalize(b)
        if (normA.equals(normB, ignoreCase = true)) return 0

        val (coreA, preA) = splitCoreAndPreRelease(normA)
        val (coreB, preB) = splitCoreAndPreRelease(normB)

        val numsA = parseNumericCore(coreA)
        val numsB = parseNumericCore(coreB)
        val maxLen = maxOf(numsA.size, numsB.size)
        for (i in 0 until maxLen) {
            val va = numsA.getOrElse(i) { 0 }
            val vb = numsB.getOrElse(i) { 0 }
            if (va != vb) return va.compareTo(vb)
        }

        // Release version (no pre-release suffix) is newer than pre-release of the same core version
        if (preA.isEmpty() && preB.isEmpty()) return 0
        if (preA.isEmpty()) return 1
        if (preB.isEmpty()) return -1

        return comparePreRelease(preA, preB)
    }

    private fun splitCoreAndPreRelease(version: String): Pair<String, String> {
        val withoutBuildMeta = version.substringBefore('+')
        val dashIdx = withoutBuildMeta.indexOf('-')
        return if (dashIdx < 0) {
            withoutBuildMeta to ""
        } else {
            withoutBuildMeta.substring(0, dashIdx) to withoutBuildMeta.substring(dashIdx + 1)
        }
    }

    private fun parseNumericCore(core: String): List<Int> {
        return core.split('.')
            .mapNotNull { seg ->
                val digits = seg.takeWhile { it.isDigit() }
                digits.toIntOrNull()
            }
            .ifEmpty { listOf(0) }
    }

    private fun comparePreRelease(a: String, b: String): Int {
        val tokensA = a.split('.', '-', '_')
        val tokensB = b.split('.', '-', '_')
        val maxLen = maxOf(tokensA.size, tokensB.size)
        for (i in 0 until maxLen) {
            val ta = tokensA.getOrNull(i) ?: return -1
            val tb = tokensB.getOrNull(i) ?: return 1
            val na = ta.toIntOrNull()
            val nb = tb.toIntOrNull()
            val cmp = when {
                na != null && nb != null -> na.compareTo(nb)
                else -> preReleaseWeight(ta).compareTo(preReleaseWeight(tb)).let { weightCmp ->
                    if (weightCmp != 0) weightCmp else ta.lowercase().compareTo(tb.lowercase())
                }
            }
            if (cmp != 0) return cmp
        }
        return 0
    }

    private fun preReleaseWeight(token: String): Int = when (token.lowercase()) {
        "alpha", "a" -> 1
        "beta", "b" -> 2
        "rc", "pre" -> 3
        else -> 4
    }
}
