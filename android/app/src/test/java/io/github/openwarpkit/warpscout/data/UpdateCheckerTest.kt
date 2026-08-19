package io.github.openwarpkit.warpscout.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
