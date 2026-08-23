package io.github.openwarpkit.warpscout.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun returnsNullWhenAndroidReleaseDoesNotExist() {
        val releases = listOf(
            AndroidRelease("v0.15.0", "cli", draft = false, prerelease = false),
            AndroidRelease("android-v1.0.0", "draft", draft = true, prerelease = false)
        )

        assertNull(selectAndroidRelease(releases))
    }

    @Test
    fun selectsFirstPublishedAndroidRelease() {
        val releases = listOf(
            AndroidRelease("v0.15.0", "cli", draft = false, prerelease = false),
            AndroidRelease("android-v1.2.0", "android", draft = false, prerelease = false)
        )

        assertEquals("android-v1.2.0", selectAndroidRelease(releases)?.tag)
    }

    @Test
    fun selectsFirstCompatibleAbiApk() {
        val arm = ReleaseAsset("warpscout_arm64-v8a.apk", "arm", 10)
        val x64 = ReleaseAsset("warpscout_x86_64.apk", "x64", 20)
        val universal = ReleaseAsset("warpscout_universal.apk", "all", 30)

        assertEquals(
            arm,
            selectAndroidApkAsset(listOf(x64, universal, arm), listOf("arm64-v8a", "x86_64"))
        )
    }

    @Test
    fun fallsBackToUniversalApk() {
        val universal = ReleaseAsset("warpscout_universal.apk", "all", 30)

        assertEquals(
            universal,
            selectAndroidApkAsset(listOf(universal), listOf("arm64-v8a"))
        )
    }

    @Test
    fun comparesVersionsByNumericComponents() {
        assertTrue(compareVersions("1.10.0", "1.9.9") > 0)
        assertEquals(0, compareVersions("1.2", "1.2.0"))
    }
}
