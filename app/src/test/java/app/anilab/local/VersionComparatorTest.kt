package app.anilab.local

import app.anilab.local.domain.VersionComparator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {

    @Test
    fun numericSegmentsCompareNumerically() {
        assertTrue(VersionComparator.compareVersions("1.10.0", "1.9.9") > 0)
        assertTrue(VersionComparator.compareVersions("2.0", "2.0.1") < 0)
    }

    @Test
    fun shorterVersionIsPaddedWithZeroes() {
        assertEquals(0, VersionComparator.compareVersions("1.4", "1.4.0"))
        assertEquals(0, VersionComparator.compareVersions("v2", "2.0.0"))
    }

    @Test
    fun prereleaseRanksBelowTheRelease() {
        assertTrue(VersionComparator.compareVersions("1.4.0", "1.4.0-beta") > 0)
        assertTrue(VersionComparator.compareVersions("1.4.0-beta2", "1.4.0-beta10") < 0)
    }

    @Test
    fun updateNeedsHigherCodeOrHigherNameOnATie() {
        assertTrue(VersionComparator.isUpdate(12, "1.2.3", 11, "1.2.2"))
        assertFalse(VersionComparator.isUpdate(11, "1.2.2", 12, "1.2.3"))
        assertTrue(VersionComparator.isUpdate(11, "1.2.3", 11, "1.2.2"))
        assertFalse(VersionComparator.isUpdate(11, "1.2.2", 11, "1.2.2"))
    }

    @Test
    fun nullVersionsAreTreatedAsEmpty() {
        assertEquals(0, VersionComparator.compareVersions(null, null))
        assertTrue(VersionComparator.isNewer("1.0", null))
    }
}
