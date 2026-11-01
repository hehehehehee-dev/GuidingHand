package vn.guidinghand

import org.junit.Assert.*
import org.junit.Test

class SpeechLanguagePolicyTest {
    private fun classify(installed: List<String> = emptyList(), pending: List<String> = emptyList(),
                         available: List<String> = emptyList(), online: List<String> = emptyList(), tag: String = "vi-VN") =
        SpeechLanguagePolicy.classify(tag, installed, pending, available, online)
    @Test fun downloadableDoesNotMeanReadyToListen() {
        assertEquals(OfflineLanguageState.DOWNLOADABLE, classify(available = listOf("vi-VN")))
        assertEquals(OfflineLanguageState.READY, classify(installed = listOf("vi-VN"), available = listOf("vi-VN")))
    }
    @Test fun pendingDownloadDoesNotBecomeAnInstalledModel() {
        assertEquals(OfflineLanguageState.PENDING, classify(pending = listOf("vi-VN"), available = listOf("vi-VN")))
    }
    @Test fun onlineAvailabilityDoesNotAdvertiseOfflineSupport() {
        assertEquals(OfflineLanguageState.UNSUPPORTED, classify(installed = listOf("en-US"), online = listOf("vi-VN")))
    }
    @Test fun missingMetadataDoesNotFalselyDeclareLanguageUnsupported() {
        assertEquals(OfflineLanguageState.UNKNOWN, classify())
    }
    @Test fun genericLanguageTagsMatchButDifferentRegionalModelsDoNot() {
        assertEquals(OfflineLanguageState.READY, classify(installed = listOf("vi")))
        assertEquals(OfflineLanguageState.READY, classify(installed = listOf("VI_vn")))
        assertEquals(OfflineLanguageState.READY, classify(installed = listOf(" vi-VN ")))
        assertEquals(OfflineLanguageState.UNSUPPORTED, classify(installed = listOf("en-GB"), tag = "en-US"))
    }
}
