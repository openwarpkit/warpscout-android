package io.github.openwarpkit.warpscout.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class UpdateApkVerifierTest {
    @Test
    fun calculatesSha256ForDownloadedFile() {
        val file = File.createTempFile("warpscout-update", ".apk")
        try {
            file.writeText("abc")

            assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                calculateSha256(file)
            )
        } finally {
            file.delete()
        }
    }
}
