package io.github.openwarpkit.warpscout.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
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
    fun ignoresMalformedAndroidReleaseTag() {
        val releases = listOf(
            AndroidRelease("android-v1.2.0-rc1", "rc", draft = false, prerelease = false),
            AndroidRelease("android-v1.2", "short", draft = false, prerelease = false)
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

    @Test
    fun parsesGitHubSha256Digest() {
        val digest = "A".repeat(64)

        assertEquals("a".repeat(64), parseSha256Digest("sha256:$digest"))
        assertNull(parseSha256Digest(digest))
        assertNull(parseSha256Digest("sha256:1234"))
    }

    @Test
    fun acceptsOnlyRepositoryReleaseAssetUrls() {
        val trusted = "https://github.com/openwarpkit/warpscout-android/releases/download/android-v1.2.0/app.apk"

        assertTrue(isTrustedUpdateAssetUrl(trusted, "app.apk"))
        assertFalse(isTrustedUpdateAssetUrl("https://example.com/app.apk", "app.apk"))
        assertFalse(isTrustedUpdateAssetUrl("https://github.com/other/repo/releases/download/v1/app.apk", "app.apk"))
        assertFalse(isTrustedUpdateAssetUrl("https://github.com/openwarpkit/warpscout-android/releases/download/v1/app.apk", "app.apk"))
        assertFalse(isTrustedUpdateAssetUrl("$trusted?token=value", "app.apk"))
        assertFalse(isTrustedUpdateAssetUrl(trusted, "../app.apk"))
    }

    @Test
    fun fallsBackWhenReleasePageUrlIsUntrusted() {
        val trusted = "https://github.com/openwarpkit/warpscout-android/releases/tag/android-v1.2.0"

        assertEquals(trusted, trustedReleasePageUrl(trusted))
        assertEquals(
            "https://github.com/openwarpkit/warpscout-android/releases",
            trustedReleasePageUrl("https://example.com/fake")
        )
    }

    @Test
    fun automaticDownloadRequiresTrustedUrlAndDigest() {
        val update = AvailableUpdate(
            version = "1.2.0",
            releaseUrl = "release",
            apkUrl = "https://github.com/openwarpkit/warpscout-android/releases/download/android-v1.2.0/app.apk",
            apkName = "app.apk",
            apkSize = 42,
            apkSha256 = "a".repeat(64)
        )

        assertTrue(canAutomaticallyDownloadUpdate(update))
        assertFalse(canAutomaticallyDownloadUpdate(update.copy(apkSha256 = null)))
        assertFalse(canAutomaticallyDownloadUpdate(update.copy(apkSha256 = "invalid")))
        assertFalse(canAutomaticallyDownloadUpdate(update.copy(apkUrl = "https://example.com/app.apk")))
    }
}
