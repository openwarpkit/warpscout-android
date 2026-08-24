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
        val sha256 = "a".repeat(64)
        val result = UpdateResult(
            latestVersion = "1.2.0",
            releaseUrl = "https://github.com/openwarpkit/warpscout-android/releases/tag/android-v1.2.0",
            updateAvailable = true,
            releaseFound = true,
            apkAsset = ReleaseAsset("app.apk", "download", 42, "sha256:$sha256")
        )

        assertEquals(
            AvailableUpdate(
                "1.2.0",
                "https://github.com/openwarpkit/warpscout-android/releases/tag/android-v1.2.0",
                "download",
                "app.apk",
                42,
                sha256
            ),
            result.toAvailableUpdate()
        )
    }

    @Test
    fun updateWithoutDigestRemainsAvailableForManualInstallation() {
        val result = UpdateResult(
            latestVersion = "1.2.0",
            releaseUrl = "release",
            updateAvailable = true,
            releaseFound = true,
            apkAsset = ReleaseAsset("app.apk", "download", 42)
        )

        assertNull(result.toAvailableUpdate()?.apkSha256)
    }

    @Test
    fun currentResultDoesNotCreateAvailableUpdate() {
        val result = UpdateResult("1.0.0", "release", false, true)

        assertNull(result.toAvailableUpdate())
    }
}
