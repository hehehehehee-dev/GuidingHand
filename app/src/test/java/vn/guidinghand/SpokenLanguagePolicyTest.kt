package vn.guidinghand

import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenLanguagePolicyTest {
    @Test fun vietnameseSpeechSwitchesAnEnglishSessionToVietnamese() {
        assertEquals("vi", SpokenLanguagePolicy.detect("Cháu giúp bác mở Chrome", "en"))
        assertEquals("vi", SpokenLanguagePolicy.detect("giup toi mo Chrome", "en"))
    }

    @Test fun englishSpeechSwitchesAVietnameseSessionToEnglish() {
        assertEquals("en", SpokenLanguagePolicy.detect("Please open Chrome", "vi"))
        assertEquals("en", SpokenLanguagePolicy.detect("Help me find Instagram", "vi"))
    }

    @Test fun namesAndAppLabelsDoNotChangeTheSelectedLanguage() {
        assertEquals("vi", SpokenLanguagePolicy.detect("Minh", "vi"))
        assertEquals("en", SpokenLanguagePolicy.detect("Instagram", "en"))
    }
}
