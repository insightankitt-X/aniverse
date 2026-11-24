package app.anilab.local.domain

/**
 * Dotted version comparison that tolerates non-numeric segments (`1.4.0-beta`, `v2`, `3.0.0.1`).
 * Numeric parts compare numerically, a numeric part always outranks a pre-release word, and the
 * shorter version is padded with zeroes so `1.4` equals `1.4.0`.
 */
object VersionComparator : Comparator<String> {
    override fun compare(first: String, second: String): Int = compareVersions(first, second)

    fun compareVersions(first: String?, second: String?): Int {
        val left = tokenize(first.orEmpty())
        val right = tokenize(second.orEmpty())
        val size = maxOf(left.size, right.size)
        for (index in 0 until size) {
            val a = left.getOrElse(index) { "0" }
            val b = right.getOrElse(index) { "0" }
            val aNumber = a.toIntOrNull()
            val bNumber = b.toIntOrNull()
            val result = when {
                aNumber != null && bNumber != null -> aNumber.compareTo(bNumber)
                aNumber != null -> 1
                bNumber != null -> -1
                else -> a.compareTo(b)
            }
            if (result != 0) return result
        }
        return 0
    }

    fun isNewer(candidate: String?, current: String?): Boolean = compareVersions(candidate, current) > 0

    /**
     * An update needs a higher integer version code; the version name breaks ties so a republished
     * fix with the same code is still offered when its semver is higher.
     */
    fun isUpdate(
        newVersionCode: Int,
        newVersionName: String?,
        installedVersionCode: Int,
        installedVersionName: String?,
    ): Boolean = when {
        newVersionCode > installedVersionCode -> true
        newVersionCode < installedVersionCode -> false
        else -> isNewer(newVersionName, installedVersionName)
    }

    private fun tokenize(version: String): List<String> =
        version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .split('.', '-', '_', '+')
            .filter { it.isNotEmpty() }
            .map { it.lowercase() }
}
