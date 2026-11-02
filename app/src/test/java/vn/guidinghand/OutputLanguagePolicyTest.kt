package vn.guidinghand

import org.junit.Assert.*
import org.junit.Test

class OutputLanguagePolicyTest {
    @Test fun englishPreferenceRejectsVietnamesePlannerCopy() {
        assertFalse(OutputLanguagePolicy.accepts("Bác bấm nút Tìm kiếm nhé.", "en"))
        assertFalse(OutputLanguagePolicy.accepts("Bac bam nut tim kiem", "en"))
        assertTrue(OutputLanguagePolicy.accepts("Tap the Search button.", "en"))
        assertTrue(OutputLanguagePolicy.accepts("Zalo", "en"))
    }

    @Test fun vietnamesePreferenceRejectsEnglishInstructionsButKeepsProperNames() {
        assertFalse(OutputLanguagePolicy.accepts("Tap the Search button.", "vi"))
        assertTrue(OutputLanguagePolicy.accepts("Bác bấm nút Tìm kiếm nhé.", "vi"))
        assertTrue(OutputLanguagePolicy.accepts("Instagram", "vi"))
    }
}
