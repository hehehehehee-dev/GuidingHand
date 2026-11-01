package vn.guidinghand

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendHealthTest {
    @Test fun readinessRequiresTheExactSafeBackendContract() {
        val valid = BackendHealth.compatibleContract("single-image-v1", listOf("guide_app", "send_photo_zalo"),
            "muse-voice-transcribe-1.0", "speech_to_text", false)
        assertTrue(valid)
        assertFalse(BackendHealth.compatibleContract("legacy", listOf("guide_app"), "muse-voice-transcribe-1.0", "speech_to_text", false))
        assertFalse(BackendHealth.compatibleContract("single-image-v1", listOf("send_photo_zalo"), "muse-voice-transcribe-1.0", "speech_to_text", false))
        assertFalse(BackendHealth.compatibleContract("single-image-v1", listOf("guide_app"), "invented-tts", "speech_output", true))
    }
}
