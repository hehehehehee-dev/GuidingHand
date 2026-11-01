package vn.guidinghand

import org.junit.Assert.*
import org.junit.Test

class GuidanceTextTest {
    private val top = ScreenNode("target", "Settings", Region(.1f, .1f, .3f, .2f), true)
    private fun instruction(id: String, language: String, label: String = "Settings", completion: String = "text",
                            address: String = "") = GuidanceText.instruction(
        StepGoal(id, listOf(label), "LEGACY TEXT MUST NOT BE SHOWN", emptyList(), completion),
        top.copy(text = label), "Instagram", "Mai", language, address)

    @Test fun languageSelectorsUseOnlyTheInterfaceLanguageEvenForDifferentSpeechLanguage() {
        assertEquals(listOf("Tiếng Anh", "Tiếng Việt"), listOf("en", "vi").map { UiText("vi").languageName(it) })
        assertEquals(listOf("English", "Vietnamese"), listOf("en", "vi").map { UiText("en").languageName(it) })
    }
    @Test fun vietnameseGuidanceDoesNotRepeatEnglishTargetLabelsOrLegacyInstructions() {
        assertEquals("Bấm Cài đặt ở phía trên.", instruction("settings", "vi"))
        assertEquals("Bấm Tin nhắn ở phía trên.", instruction("chat", "vi", "Messages"))
        assertEquals("Bấm Gọi hình ở phía trên.", instruction("video", "vi", "Video call"))
    }
    @Test fun englishGuidanceDoesNotRepeatVietnameseLabelsOrFormsOfAddress() {
        assertEquals("Tap Settings near the top.", instruction("settings", "en", "Cài đặt", address = "bà"))
        assertEquals("Tap Messages near the top.", instruction("inbox", "en", "Tin nhắn"))
        assertEquals("Place the code inside the frame.", instruction("frame", "en", "Khung quét", "scan"))
    }
    @Test fun properAppAndRecipientNamesRemainIntactInEitherLanguage() {
        assertEquals("Bấm Instagram ở phía trên.", instruction("openApp", "vi", "Instagram"))
        assertEquals("Tap Instagram near the top.", instruction("openApp", "en", "Instagram"))
        assertEquals("Bấm Mai ở phía trên.", instruction("conversation", "vi", "Mai"))
        assertEquals("Tap Mai near the top.", instruction("conversation", "en", "Mai"))
    }
    @Test fun unsupportedForeignControlLabelsUseLocalizedNeutralTextWithoutGuessingATranslation() {
        val foreign = top.copy(text = "Untranslated action")
        assertEquals("Nút 3", GuidanceText.controlLabel(foreign, UiText("vi"), 3))
        assertEquals("Control 3", GuidanceText.controlLabel(foreign.copy(text = "Tác vụ chưa dịch"), UiText("en"), 3))
        assertEquals("Bấm nút được đánh dấu ở phía trên.", instruction("localTarget", "vi", foreign.text))
        assertEquals("Tap the highlighted control near the top.", instruction("localTarget", "en", "Tác vụ chưa dịch"))
    }
    @Test fun knownControlsAreLocalizedAcrossAppLanguageAndAliasesWithoutChangingTheirIdentity() {
        assertEquals("Cài đặt", GuidanceText.controlLabel(top, UiText("vi"), 1))
        assertEquals("Settings", GuidanceText.controlLabel(top.copy(text = "Cài đặt"), UiText("en"), 1))
        assertEquals("Tìm kiếm", GuidanceText.controlLabel(top.copy(text = "", aliases = listOf("Search apps")), UiText("vi"), 1))
        assertEquals("Settings", top.text)
    }
    @Test fun vietnameseScanAndFamiliarAddressStayVietnamese() {
        assertEquals("Đặt mã vào giữa khung.", instruction("frame", "vi", "QR scanner", "scan"))
        assertEquals("Bà, bấm Cài đặt ở phía trên.", instruction("settings", "vi", address = "bà"))
        assertEquals("Nhận trợ giúp", UiText("vi").task("unknown_internal_id"))
        assertEquals("Get help", UiText("en").task("unknown_internal_id"))
    }
}
