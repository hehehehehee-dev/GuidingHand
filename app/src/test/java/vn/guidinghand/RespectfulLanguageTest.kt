package vn.guidinghand

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RespectfulLanguageTest {
    @Test fun modelAndSystemSelfReferencesUseChosenVoice() {
        val language = RespectfulLanguage(UserAddress.BA, AssistantAddress.CHAU)
        assertEquals("Cháu chưa nghe rõ. Bà nói lại giúp cháu nhé.",
            language.message("Tôi chưa nghe rõ. Bác nói lại giúp tôi nhé."))
        assertEquals("Bà giữ nguyên màn hình, cháu sẽ kiểm tra lại.",
            language.message("Bác giữ nguyên màn hình, tôi sẽ kiểm tra lại."))
        val text = language.message("Tôi sẽ chờ. Bác muốn gửi ảnh nào?")
        assertEquals(text, language.message(text))
        for (assistant in AssistantAddress.entries) {
            val wording = RespectfulLanguage(assistant = assistant)
            assertTrue(wording.message("Tôi đang đọc màn hình.").startsWith(wording.assistantLabel))
        }
    }
    @Test fun customAddressesAreLiteralBoundedAndHaveSafeDefaults() {
        val language = RespectfulLanguage(UserAddress.CUSTOM, AssistantAddress.CUSTOM,
            customUser = "Ông Sáu", customAssistant = "Em")
        assertEquals("Ông Sáu muốn được hỗ trợ việc gì ạ?", language.goalQuestion)
        assertEquals("Em chưa nghe rõ. Ông Sáu nói lại giúp em nhé.",
            language.message("Tôi chưa nghe rõ. Bác nói lại giúp tôi nhé."))
        val message = language.message("Tôi sẽ chờ.")
        assertEquals(message, language.message(message))
        assertEquals("Bác", language.copy(customUser = "").userLabel)
        assertEquals("Tôi", language.copy(customAssistant = "").assistantLabel)
        assertEquals("Ông Sáu", RespectfulLanguage.cleanAddress("  Ông  Sáu\n!"))
        assertEquals(32, RespectfulLanguage.cleanAddress("a".repeat(100)).length)
    }
    @Test fun quotedContentAndRecipientAreNeverRewritten() {
        val language = RespectfulLanguage(UserAddress.ONG, AssistantAddress.CHAU)
        assertEquals("Cháu sẽ tìm “Bác nói”.", language.message("Tôi sẽ tìm “Bác nói”.", listOf("Bác nói")))
        assertEquals("Cháu sẽ mở Con Cần.", language.message("Tôi sẽ mở Con Cần.", listOf("Con Cần")))
    }
    @Test fun chosenAddressAndToneAreUsedWithoutGenderAssumptions() {
        assertEquals("Bà vui lòng bấm nút được đánh dấu ạ.", RespectfulLanguage(UserAddress.BA).message("Bấm nút được đánh dấu."))
        assertEquals("Ông, bấm nút được đánh dấu ạ.", RespectfulLanguage(UserAddress.ONG, tone = GuidanceTone.CONCISE).message("Bấm nút được đánh dấu."))
        assertEquals("Vui lòng bấm nút được đánh dấu ạ.", RespectfulLanguage(UserAddress.NONE).message("Bấm nút được đánh dấu."))
        assertEquals("Ông, bấm nút được đánh dấu ạ.", RespectfulLanguage(UserAddress.ONG, tone = GuidanceTone.CONCISE)
            .message("Bà vui lòng bấm nút được đánh dấu ạ."))
    }
    @Test fun literalNamesAndPhotoLabelsRemainExactlyAsSelected() {
        val language = RespectfulLanguage(UserAddress.BA, AssistantAddress.CON)
        val name = "Ông bà Mình"; val filename = "Bấm Mình.jpg"
        assertEquals("Bà vui lòng bấm tên $name ạ.", language.message("Bấm tên $name.", listOf(name)))
        assertEquals("Bà vui lòng chọn ảnh $filename ạ.", language.message("Chọn ảnh $filename.", listOf(filename)))
    }
    @Test fun assistantSelfReferenceAndQuestionsFollowThePreference() {
        val language = RespectfulLanguage(UserAddress.CHU, AssistantAddress.CHAU)
        assertEquals("Chú muốn gửi ảnh nào ạ?", language.message("Ông bà muốn gửi ảnh nào?"))
        assertEquals("Cháu sẽ tiếp tục hướng dẫn từ màn hình hiện tại.", language.message("Mình sẽ hướng dẫn tiếp từ màn hình này."))
        assertEquals("Chưa đọc được. Cháu sẽ chờ.", language.message("Chưa đọc được. Mình sẽ chờ."))
        assertTrue(language.message("Đã tạm dừng để bảo vệ thông tin. Ông bà có thể chọn Gọi người thân hoặc Dừng.").contains("Chú có thể"))
    }
    @Test fun unknownSavedKeysFallBackToRespectfulDefaults() {
        assertEquals(UserAddress.BAC, UserAddress.from("unknown"))
        assertEquals(AssistantAddress.TOI, AssistantAddress.from("unknown"))
        assertEquals(GuidanceTone.GENTLE, GuidanceTone.from("unknown"))
    }
    @Test fun cardAndSpeechMatchAndChangingAddressDoesNotRestartTheTask() = runBlocking {
        val node = ScreenNode("icon", "Zalo", Region(.1f,.2f,.3f,.3f), true)
        val screen = ScreenState("launcher", listOf(node), isLauncher = true)
        val task = PhotoTask("An", "Photo")
        var planned = 0
        val spoken = mutableListOf<GuidanceStep>()
        val model = object : GuideModel {
            override suspend fun next(input: GuideInput): GuideDecision {
                planned++
                return GuideDecision.Step(GuidanceStep("launcher", PhotoStage.LAUNCHER, "Bấm Zalo.", node,
                    SamRegion(node.region!!, screen.planningFingerprint), VoiceOutput()), task)
            }
            override suspend fun repeat(step: GuidanceStep) { spoken += step }
            override fun cancel() {}
        }
        val views = mutableListOf<GuideViewState>()
        val engine = GuideEngine(model, views::add, language = RespectfulLanguage(UserAddress.BA))
        engine.startGoal("Gửi ảnh"); engine.onScreen(screen)
        assertEquals(views.last().instructionVi, spoken.last().instructionVi)
        val oldSam = spoken.last().sam
        engine.updateLanguage(RespectfulLanguage(UserAddress.ONG, AssistantAddress.CHAU))
        assertEquals(GuideStatus.WAITING_TAP, engine.status)
        assertEquals(task, engine.task)
        engine.repeat(screen)
        assertEquals("Ông vui lòng bấm Zalo ạ.", spoken.last().instructionVi)
        assertEquals(views.last().instructionVi, spoken.last().instructionVi)
        assertEquals(oldSam, spoken.last().sam)
        assertEquals(node, spoken.last().target)
        assertEquals(1, planned)
        assertTrue(engine.verifiedStepIds.isEmpty())
    }
}
