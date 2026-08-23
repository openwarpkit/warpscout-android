package io.github.openwarpkit.warpscout.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateModelsTest {
    @Test
    fun automaticCheckResumesAtCooldownBoundary() {
        assertFalse(shouldAutomaticallyCheck(nowMillis = 999, dismissedUntilMillis = 1_000))
        assertTrue(shouldAutomaticallyCheck(nowMillis = 1_000, dismissedUntilMillis = 1_000))
    }

    @Test
    fun downloadProgressIsBounded() {
        val update = AvailableUpdate("1.0.0", "release", "apk", "app.apk", 100)

        assertEquals(0f, UpdateDownloadState.Downloading(update, -5, 100).progress)
        assertEquals(1f, UpdateDownloadState.Downloading(update, 150, 100).progress)
        assertNull(UpdateDownloadState.Downloading(update, 50, 0).progress)
    }

    @Test
    fun updateResultRetainsExactApkAsset() {
        val result = UpdateResult(
            latestVersion = "1.2.0",
            releaseUrl = "release",
            updateAvailable = true,
            releaseFound = true,
            apkAsset = ReleaseAsset("app.apk", "download", 42)
        )

        assertEquals(
            AvailableUpdate("1.2.0", "release", "download", "app.apk", 42),
            result.toAvailableUpdate()
        )
    }

    @Test
    fun currentResultDoesNotCreateAvailableUpdate() {
        val result = UpdateResult("1.0.0", "release", false, true)

        assertNull(result.toAvailableUpdate())
    }
}
