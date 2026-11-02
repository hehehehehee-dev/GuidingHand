package vn.guidinghand

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CoreGuideEngineTest {
    @Test fun spokenCorrectionAfterUnsupportedKeepsOriginalGoalAndPairsTheExplanation() = runBlocking {
        val explanation = "I understood a text message. This build guides photo sharing. What did you want to send?"
        val model = Model { input ->
            if (input.dialogue.isEmpty()) GuideDecision.Unsupported(explanation, listOf("A photo"))
            else GuideDecision.Navigate("Find Zalo.", PhotoTask("", ""))
        }
        val views = mutableListOf<GuideViewState>(); val engine = GuideEngine(model, views::add)
        val original = "I want to share something with An on Zalo"
        engine.startGoal(original); engine.onScreen(home)
        assertEquals(GuideStatus.UNSUPPORTED, engine.status)
        assertTrue(engine.acceptsSpokenRefinement)
        assertEquals(listOf("A photo"), views.last().options)
        val calls = model.inputs.size
        engine.onScreen(home.copy(windowId = 42))
        assertEquals(calls, model.inputs.size) // No automatic rejection/retry loop.
        engine.answer("A photo, not a text message"); engine.onScreen(home)
        assertEquals(original, model.inputs.last().goal)
        assertEquals(ClarificationTurn(explanation, "A photo, not a text message"), model.inputs.last().dialogue.single())
        assertEquals(GuideStatus.WAITING_NAVIGATION, engine.status)
        engine.stop(); assertFalse(engine.acceptsSpokenRefinement)
    }
    @Test fun choosingSomeoneInZaloKeepsTheQuestionAnswerPairAndAllowsNavigationBeforeTheName() = runBlocking {
        val browser = ScreenState("browser", listOf(ScreenNode("page", "News", null, false)))
        val partial = PhotoTask("", "")
        val model = Model { input ->
            if (input.dialogue.isEmpty()) GuideDecision.Clarify("Who should receive the photo?", listOf("Choose someone in Zalo"))
            else if (input.screen.isLauncher) GuideDecision.Step(step(input.screen, icon, PhotoStage.LAUNCHER), partial)
            else GuideDecision.Navigate("Go to Home, then choose Zalo.", partial)
        }
        val engine = GuideEngine(model, {}, locale = "en")
        engine.startGoal("Send a photo using Zalo"); engine.onScreen(browser)
        engine.answer("Choose someone in Zalo"); engine.onScreen(browser)
        assertEquals(GuideStatus.WAITING_NAVIGATION, engine.status)
        assertEquals(partial, engine.task)
        assertEquals(ClarificationTurn("Who should receive the photo?", "Choose someone in Zalo"), model.inputs.last().dialogue.single())
        assertEquals("Send a photo using Zalo", model.inputs.last().goal)
        engine.onScreen(home)
        assertEquals(GuideStatus.WAITING_TAP, engine.status)
        assertNotNull(engine.launcherStep)
    }

    @Test fun missingRecipientCannotAuthorizeAttachmentAndKnownRecipientCannotBeSilentlyChanged() = runBlocking {
        val partial = PhotoTask("", "Family photo")
        val screen = zalo(attach)
        val model = Model { GuideDecision.Step(step(screen, attach, PhotoStage.ATTACH), partial) }
        val engine = GuideEngine(model, {})
        engine.startGoal("Send a photo", partial); engine.onScreen(screen)
        assertEquals(GuideStatus.ASKING, engine.status)
        assertTrue(partial.acceptsRefinement(task))
        assertFalse(task.acceptsRefinement(task.copy(recipient = "Other person")))
    }
    @Test fun continuouslyChangingScreensAutoWaitWithoutAskingOrLosingTheTask() = runBlocking {
        val model = Model { GuideDecision.StateChanged }
        val engine = GuideEngine(model, {})
        engine.startGoal("Share my photo", task)
        repeat(4) { engine.onScreen(home) }
        assertEquals(GuideStatus.READING, engine.status)
        assertTrue(engine.hasGoal); assertEquals(task, engine.task)
        engine.waitForStableScreen()
        assertEquals(GuideStatus.READING, engine.status)
    }
    @Test fun aDatedThumbnailNeedsAnActualMatchedTapAndNewSelection() = runBlocking {
        val before = zalo(attach)
        val dated = photo.copy(text = "Photo, 28 October 2026", selected = false)
        val gallery = ScreenState(task.appPackage, listOf(ScreenNode("gallery", "Gallery", null, false), dated))
        val ready = gallery.copy(nodes = listOf(gallery.nodes.first(), dated.copy(selected = true), send))
        val engine = GuideEngine(sendFlowModel(before, gallery, ready), {})
        reachSend(engine, before, gallery, ready)
        assertEquals(task, engine.task)
        assertTrue(engine.verifiedStepIds.contains("photo"))
    }

    @Test fun leavingTheAppInvalidatesAttachmentAuthorityWithoutLosingTheGoal() = runBlocking {
        val before = zalo(attach); val gallery = picker(); val ready = zalo(photo, send)
        val model = sendFlowModel(before, gallery, ready)
        val engine = GuideEngine(model, {})
        reachSend(engine, before, gallery, ready)
        engine.onScreen(ScreenState("other.browser", listOf(ScreenNode("page", "News", null, false))))
        assertTrue(engine.hasGoal); assertEquals(task, engine.task)
        engine.answer("Continue")
        engine.onScreen(ready)
        assertEquals(GuideStatus.ASKING, engine.status)
    }

    @Test fun aSelectionCountOrDifferentThumbnailDoesNotVerifyTheChosenPhoto() {
        val dated = photo.copy(text = "Photo, 28 October 2026", selected = false)
        val before = ScreenState(task.appPackage, listOf(dated))
        val countOnly = before.copy(nodes = listOf(ScreenNode("count", "1 selected", null, false, selected = true)))
        assertFalse(PhotoVerification.selectedTarget(before, countOnly, dated))
        val other = dated.copy(selected = true, region = Region(.7f, .7f, .9f, .9f))
        assertFalse(PhotoVerification.selectedTarget(before, before.copy(nodes = listOf(other)), dated))
    }
    @Test fun clarificationIsSpokenAndRepeatDoesNotRequireTypingOrANewPlan() = runBlocking {
        val spoken = mutableListOf<String>(); var plans = 0
        val model = object : GuideModel {
            override suspend fun next(input: GuideInput): GuideDecision { plans++; return GuideDecision.Clarify("Ông bà muốn gửi ảnh nào?") }
            override suspend fun repeat(step: GuidanceStep) {}
            override suspend fun speakMessage(text: String,screen: ScreenState) { spoken += text }
            override fun cancel() {}
        }
        val engine = GuideEngine(model,{})
        engine.startGoal("Gửi ảnh"); engine.onScreen(home)
        assertEquals(GuideStatus.CLARIFYING,engine.status); assertEquals(1,spoken.size)
        engine.repeat(home); assertEquals(2,spoken.size); assertEquals(1,plans)
        engine.repeat(protected()); assertEquals(2,spoken.size); assertEquals(GuideStatus.SAFETY_PAUSED,engine.status)
    }

    @Test fun structuredSpeechPurposeReachesTheViewWithoutCopyInference() = runBlocking {
        val views = mutableListOf<GuideViewState>()
        val model = Model { GuideDecision.Clarify("Bác muốn viết nội dung gì?", speechPurpose = SpeechPurpose.MESSAGE_CONTENT) }
        val engine = GuideEngine(model, views::add)
        engine.startGoal("Nhắn Minh"); engine.onScreen(home)
        assertEquals(GuideStatus.CLARIFYING, engine.status)
        assertEquals(SpeechPurpose.MESSAGE_CONTENT, views.last().speechPurpose)
    }
    private val task = PhotoTask("An", "Family photo")
    private val bounds = Region(.1f, .2f, .3f, .3f)
    private val icon = ScreenNode("zalo", "Zalo", bounds, true, launchPackage = "com.zing.zalo")
    private val home = ScreenState("test.launcher", listOf(icon), isLauncher = true)
    private val recipient = ScreenNode("com.zing.zalo:id/chat_title@0", "An", Region(.3f, .05f, .7f, .1f), false)
    private val composer = ScreenNode("composer", "Nhập tin nhắn", Region(.1f, .8f, .7f, .9f), true, className = "android.widget.EditText")
    private val attach = ScreenNode("attach", "Photos", bounds, true)
    private val send = ScreenNode("send", "Gửi", bounds, true)
    private val photo = ScreenNode("photo", "Family photo", Region(.4f, .4f, .6f, .6f), true, selected = true, className = "android.widget.ImageView")
    private fun zalo(vararg extra: ScreenNode) = ScreenState("com.zing.zalo", listOf(recipient, composer) + extra)
    private fun protected() = home.copy(nodes = listOf(ScreenNode("secret", "", bounds, true, password = true)))
    private fun step(screen: ScreenState, target: ScreenNode, stage: PhotoStage) = GuidanceStep(stage.name.lowercase(), stage,
        "Bấm nút được đánh dấu.", target, SamRegion(target.region!!, screen.planningFingerprint), VoiceOutput(byteArrayOf(1), "audio/wav"))
    private class Model(var respond: suspend (GuideInput) -> GuideDecision) : GuideModel {
        val inputs = mutableListOf<GuideInput>(); var spoken = 0; var cancelled = 0
        override suspend fun next(input: GuideInput): GuideDecision { inputs += input; return respond(input) }
        override suspend fun repeat(step: GuidanceStep) { spoken++ }
        override fun cancel() { cancelled++ }
    }
    private fun model() = Model { GuideDecision.Clarify("Ông bà muốn gửi ảnh nào?") }
    private fun picker() = ScreenState("com.zing.zalo", listOf(ScreenNode("gallery", "Gallery", null, false), photo.copy(selected = false)))
    private fun sendFlowModel(before: ScreenState, gallery: ScreenState, ready: ScreenState) = Model { input ->
        when (input.screen.fingerprint) {
            before.fingerprint -> GuideDecision.Step(step(before, attach, PhotoStage.ATTACH), task)
            gallery.fingerprint -> GuideDecision.Step(step(gallery, gallery.nodes.last(), PhotoStage.PHOTO), task)
            ready.fingerprint -> GuideDecision.Step(step(ready, send, PhotoStage.SEND), task)
            else -> GuideDecision.DoneProposal("Xong rồi.")
        }
    }
    private suspend fun reachSend(engine: GuideEngine, before: ScreenState, gallery: ScreenState, ready: ScreenState) {
        engine.startGoal("Gửi ảnh cho An"); engine.onScreen(before); engine.onTap(attach)
        engine.onScreen(gallery); engine.onTap(gallery.nodes.last()); engine.onScreen(ready)
        assertEquals(GuideStatus.WAITING_TAP, engine.status)
    }

    @Test fun safetyGateRunsBeforeEveryModelCallAndRequiresStopBeforeANewGoal() = runBlocking {
        val model = model(); val views = mutableListOf<GuideViewState>(); val engine = GuideEngine(model, views::add)
        engine.startGoal("Gửi ảnh cho An")
        engine.onScreen(protected())
        assertEquals(GuideStatus.SAFETY_PAUSED, engine.status); assertTrue(model.inputs.isEmpty()); assertEquals(0, model.spoken)
        assertNull(views.last().step)
        engine.onScreen(home); assertTrue(model.inputs.isEmpty())
        engine.resume(); engine.onScreen(home); assertTrue(model.inputs.isEmpty())
        engine.stop(); engine.startGoal("Gửi ảnh cho An"); engine.onScreen(home)
        assertEquals(1, model.inputs.size); assertEquals(GuideStatus.CLARIFYING, engine.status)
    }
    @Test fun incompleteAndLockedFramesCannotReachPlanner() = runBlocking {
        for (screen in listOf(home.copy(complete = false), home.copy(isLocked = true), home.copy(nodes = emptyList()))) {
            val model = model(); val engine = GuideEngine(model, {})
            engine.startGoal("Gửi ảnh cho An"); engine.onScreen(screen)
            assertTrue(model.inputs.isEmpty()); assertEquals(0, model.spoken)
        }
    }
    @Test fun staleResponseCannotRestoreRingOrSpeakAfterSafetyPause() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val response = CompletableDeferred<GuideDecision>()
        val model = Model { entered.complete(Unit); response.await() }
        val views = mutableListOf<GuideViewState>(); val engine = GuideEngine(model, views::add)
        engine.startGoal("Gửi ảnh cho An")
        val pending = async { engine.onScreen(home) }
        entered.await(); engine.onScreen(protected())
        response.complete(GuideDecision.Step(step(home, icon, PhotoStage.LAUNCHER), task)); pending.await()
        assertEquals(GuideStatus.SAFETY_PAUSED, engine.status); assertNull(views.last().step); assertEquals(0, model.spoken)
    }
    @Test fun accessibilityBoundsCannotBePresentedAsSamOutput() = runBlocking {
        for (region in listOf(SamRegion(bounds, home.planningFingerprint, "accessibility"), SamRegion(bounds, "old-frame"),
            SamRegion(Region(.7f, .7f, .9f, .9f), home.planningFingerprint))) {
            val model = Model { GuideDecision.Step(step(home, icon, PhotoStage.LAUNCHER).copy(sam = region), task) }
            val views = mutableListOf<GuideViewState>(); val engine = GuideEngine(model, views::add)
            engine.startGoal("Gửi ảnh cho An"); engine.onScreen(home)
            assertEquals(GuideStatus.ASKING, engine.status); assertNull(views.last().step); assertEquals(0, model.spoken)
        }
    }
    @Test fun androidOfflineSpeechDoesNotRequireMuseVoiceAudio() = runBlocking {
        val model = Model { GuideDecision.Step(step(home, icon, PhotoStage.LAUNCHER).copy(
            voice = VoiceOutput()), task) }
        val engine = GuideEngine(model, {})
        engine.startGoal("Gửi ảnh cho An"); engine.onScreen(home)
        assertEquals(GuideStatus.WAITING_TAP, engine.status); assertEquals(1, model.spoken)
    }
    @Test fun developmentBoundsRequireAnExplicitDevelopmentModelAndEngine() = runBlocking {
        val fake = object : GuideModel {
            override val developmentStandIn = true
            override suspend fun next(input: GuideInput) = GuideDecision.Step(step(home, icon, PhotoStage.LAUNCHER)
                .copy(sam = SamRegion(bounds, home.planningFingerprint, "phase1-mock")), task)
            override suspend fun repeat(step: GuidanceStep) {}
            override fun cancel() {}
        }
        val production = GuideEngine(fake, {})
        production.startGoal("Gửi ảnh cho An"); production.onScreen(home)
        assertEquals(GuideStatus.ASKING, production.status)
        val development = GuideEngine(fake, {}, allowDevStandIn = true)
        development.startGoal("Gửi ảnh cho An"); development.onScreen(home)
        assertEquals(GuideStatus.WAITING_TAP, development.status)
    }
    @Test fun wrongTapReplansFromNewScreenWithoutResettingTaskOrVerifiedProgress() = runBlocking {
        val contact = ScreenNode("contact", "An", bounds, true)
        val list = ScreenState("com.zing.zalo", listOf(contact))
        val first = zalo(attach)
        var count = 0
        val model = Model { when (count++) {
            0 -> GuideDecision.Step(step(list, contact, PhotoStage.RECIPIENT), task)
            1 -> GuideDecision.Step(step(first, attach, PhotoStage.ATTACH), task)
            else -> GuideDecision.Clarify("Ông bà muốn quay lại cuộc trò chuyện?")
        } }
        val views = mutableListOf<GuideViewState>(); val engine = GuideEngine(model, views::add)
        engine.startGoal("Gửi ảnh cho An"); engine.onScreen(list); engine.onTap(contact); engine.onScreen(first)
        engine.onTap(attach.copy(id = "wrong"))
        val next = ScreenState("com.zing.zalo", listOf(ScreenNode("profile", "An", bounds, true)))
        engine.onScreen(next)
        assertEquals(task, engine.task); assertEquals(setOf("recipient"), engine.verifiedStepIds)
        assertEquals(next, model.inputs.last().screen); assertFalse(model.inputs.last().observations.last().matchedTap)
        assertTrue(views.any { it.status == GuideStatus.RECOVERING })
    }
    @Test fun existingConversationIsContextForAttachButNotAVerifiedTransition() = runBlocking {
        val screen = zalo(attach)
        val model = Model { GuideDecision.Step(step(screen, attach, PhotoStage.ATTACH), task) }
        val engine = GuideEngine(model, {})
        engine.startGoal("Gửi ảnh cho An"); engine.onScreen(screen)
        assertEquals(GuideStatus.WAITING_TAP, engine.status)
        assertTrue(engine.verifiedStepIds.isEmpty())
        assertTrue(model.inputs.single().verifiedStepIds.isEmpty())
    }
    @Test fun existingSelectedPhotoWithoutPreAttachmentConversationCannotAuthorizeSend() = runBlocking {
        val screen = zalo(photo, send)
        val model = Model { GuideDecision.Step(step(screen, send, PhotoStage.SEND), task) }
        val engine = GuideEngine(model, {})
        engine.startGoal("Gửi ảnh cho An"); engine.onScreen(screen)
        assertEquals(GuideStatus.ASKING, engine.status)
        assertTrue(engine.verifiedStepIds.isEmpty())
    }
    @Test fun wrongConversationClearsSendContextWithoutErasingVerifiedHistory() = runBlocking {
        val contact = ScreenNode("contact", "An", bounds, true)
        val list = ScreenState("com.zing.zalo", listOf(contact))
        val conversation = zalo(attach)
        val wrongChat = zalo(photo, send).copy(nodes = listOf(recipient.copy(text = "Bình"), composer, photo, send,
            ScreenNode("com.zing.zalo:id/message_text@4", "An", Region(.1f, .4f, .5f, .5f), false)))
        var count = 0
        val model = Model { when (count++) {
            0 -> GuideDecision.Step(step(list, contact, PhotoStage.RECIPIENT), task)
            1 -> GuideDecision.Step(step(conversation, attach, PhotoStage.ATTACH), task)
            else -> GuideDecision.Step(step(wrongChat, send, PhotoStage.SEND), task)
        } }
        val engine = GuideEngine(model, {})
        engine.startGoal("Gửi ảnh cho An"); engine.onScreen(list); engine.onTap(contact); engine.onScreen(conversation)
        engine.onTap(attach.copy(id = "other-chat")); engine.onScreen(wrongChat)
        assertEquals(setOf("recipient"), engine.verifiedStepIds)
        assertEquals(GuideStatus.ASKING, engine.status)
    }
    @Test fun correctPackageMustActuallyOpenBeforeLauncherIsVerified() = runBlocking {
        var count = 0
        val model = Model { if (count++ == 0) GuideDecision.Step(step(home, icon, PhotoStage.LAUNCHER), task)
            else GuideDecision.Clarify("Kiểm tra lại ứng dụng nhé.") }
        val engine = GuideEngine(model, {})
        engine.startGoal("Gửi ảnh cho An"); engine.onScreen(home); engine.onTap(icon)
        engine.onScreen(home.copy(packageName = "different.app", isLauncher = false))
        assertFalse("launcher" in engine.verifiedStepIds)
    }
    @Test fun modelCannotDeclareDoneWithoutNewOutgoingPhotoAndDeliveryEvidence() = runBlocking {
        val model = Model { GuideDecision.DoneProposal("Xong rồi.") }; val engine = GuideEngine(model, {})
        engine.startGoal("Gửi ảnh cho An", task); engine.onScreen(zalo(send))
        assertEquals(GuideStatus.ASKING, engine.status); assertFalse("delivery" in engine.verifiedStepIds)
    }
    @Test fun sendingRequiresRealRecipientAndSelectedPhotoAndARealSendControl() = runBlocking {
        for (screen in listOf(zalo(send), zalo(photo, send).copy(nodes = listOf(composer, photo, send)))) {
            val model = Model { GuideDecision.Step(step(screen, send, PhotoStage.SEND), task) }; val engine = GuideEngine(model, {})
            engine.startGoal("Gửi ảnh cho An"); engine.onScreen(screen)
            assertEquals(GuideStatus.ASKING, engine.status); assertEquals(0, model.spoken)
        }
        val wrong = send.copy(text = "Delete")
        val screen = zalo(photo, wrong)
        val model = Model { GuideDecision.Step(step(screen, wrong, PhotoStage.SEND), task) }; val engine = GuideEngine(model, {})
        engine.startGoal("Gửi ảnh cho An"); engine.onScreen(screen)
        assertEquals(GuideStatus.ASKING, engine.status)
    }
    @Test fun finalSendCompletesOnlyAfterMatchedTapAndBothNewEvidenceTypes() = runBlocking {
        val first = zalo(attach); val gallery = picker(); val ready = zalo(photo, send)
        val model = sendFlowModel(first, gallery, ready)
        val engine = GuideEngine(model, {})
        reachSend(engine, first, gallery, ready); engine.onTap(send)
        val after = zalo(ScreenNode("outgoing-new", "Ảnh đã gửi", null, false, className = "android.widget.ImageView"),
            ScreenNode("delivery-new", "Đã nhận", null, false))
        engine.onScreen(after)
        assertEquals(GuideStatus.DONE, engine.status); assertTrue("delivery" in engine.verifiedStepIds)
        assertEquals(3, model.inputs.size)
    }
    @Test fun oldDeliveryLabelsAndAnIncomingPhotoCannotFakeSuccess() = runBlocking {
        val first = zalo(attach, ScreenNode("old", "Đã nhận", null, false))
        val gallery = picker(); val ready = zalo(photo, send)
        val model = sendFlowModel(first, gallery, ready)
        val engine = GuideEngine(model, {})
        reachSend(engine, first, gallery, ready); engine.onTap(send)
        engine.onScreen(zalo(ScreenNode("old", "Đã nhận", null, false),
            ScreenNode("incoming", "Ảnh từ An", null, false, className = "android.widget.ImageView")))
        assertNotEquals(GuideStatus.DONE, engine.status)
    }
    @Test fun oldOutgoingPhotoAndReceiptMovingTreePathsAreNotNewEvidence() {
        val before = zalo(ScreenNode("photo@0.1", "Ảnh đã gửi", null, false, className = "android.widget.ImageView"),
            ScreenNode("receipt@0.2", "Đã nhận", null, false))
        val after = zalo(ScreenNode("photo@0.8", "Ảnh đã gửi", null, false, className = "android.widget.ImageView"),
            ScreenNode("receipt@0.9", "Đã nhận", null, false))
        assertFalse(PhotoVerification.delivered(before, after, task))
    }
    @Test fun closingPickerIntoOldChatCannotFakeANewSend() = runBlocking {
        val oldPhoto = ScreenNode("photo@0.1", "Ảnh đã gửi", null, false, className = "android.widget.ImageView")
        val oldReceipt = ScreenNode("receipt@0.2", "Đã nhận", null, false)
        val before = zalo(attach, oldPhoto, oldReceipt)
        val gallery = picker(); val ready = zalo(photo, send)
        val model = sendFlowModel(before, gallery, ready); val engine = GuideEngine(model, {})
        reachSend(engine, before, gallery, ready); engine.onTap(send)
        engine.onScreen(zalo(oldPhoto.copy(id = "photo@0.8"), oldReceipt.copy(id = "receipt@0.9")))
        assertNotEquals(GuideStatus.DONE, engine.status)
        assertFalse("send" in engine.verifiedStepIds); assertFalse("delivery" in engine.verifiedStepIds)
    }
    @Test fun genericClarificationUsesModelOptionsAndDoesNotInventAnExecutableTask() = runBlocking {
        val model = Model { input -> if (input.answers.isEmpty()) GuideDecision.Clarify("Ông bà muốn chia sẻ ảnh hay tài liệu?", listOf("Ảnh", "Tài liệu"))
            else GuideDecision.Unsupported("Bản này mới hỗ trợ gửi ảnh qua Zalo.") }
        val views = mutableListOf<GuideViewState>(); val engine = GuideEngine(model, views::add)
        engine.startGoal("Tôi muốn chia sẻ thứ này"); engine.onScreen(home)
        assertEquals(listOf("Ảnh", "Tài liệu"), views.last().options); assertNull(engine.task)
        engine.answer("Tài liệu"); engine.onScreen(home)
        assertEquals(GuideStatus.UNSUPPORTED, engine.status); assertNull(engine.task)
    }

    @Test fun aNewSpokenRequestExtendsTheCurrentAppTaskWithoutRestartingTheGoal() = runBlocking {
        val instagramTask = PhotoTask("", "", "guide_app", "com.instagram.android")
        val updatedTask = instagramTask.copy(recipient = "Minh")
        val search = ScreenNode("search", "Search", Region(.1f, .1f, .3f, .2f), true)
        val inbox = ScreenNode("inbox", "Messages", Region(.7f, .1f, .9f, .2f), true)
        val instagram = ScreenState(instagramTask.appPackage, listOf(search, inbox))
        var plans = 0
        val model = Model { input ->
            when (plans++) {
                0 -> GuideDecision.Step(step(instagram, search, PhotoStage.ACTION), instagramTask)
                else -> GuideDecision.Step(step(instagram, inbox, PhotoStage.ACTION), updatedTask)
            }
        }
        val engine = GuideEngine(model, {})
        engine.startGoal("Mở Instagram", instagramTask)
        engine.onScreen(instagram)
        assertEquals(GuideStatus.WAITING_TAP, engine.status)

        engine.answer("Gửi tin nhắn cho Minh")
        engine.onScreen(instagram)

        assertEquals(GuideStatus.WAITING_TAP, engine.status)
        assertEquals(updatedTask, engine.task)
        assertEquals("Mở Instagram", model.inputs.last().goal)
        assertEquals("Gửi tin nhắn cho Minh", model.inputs.last().answers.last())
        assertEquals("Gửi tin nhắn cho Minh", model.inputs.last().userUpdates.last())
        assertTrue(model.inputs.last().allowTaskUpdate)
    }

    @Test fun aMatchedGeneralActionReplansWithoutCallingTheCorrectTapRecovery() = runBlocking {
        val appTask = PhotoTask("Minh", "", "guide_app", "com.instagram.android")
        val messages = ScreenNode("messages", "Messages", Region(.7f, .1f, .9f, .2f), true)
        val inbox = ScreenState(appTask.appPackage, listOf(messages))
        val search = ScreenNode("search", "Search Minh", Region(.1f, .1f, .8f, .2f), true)
        val next = ScreenState(appTask.appPackage, listOf(search), navigationGeneration = 1)
        var plans = 0
        val model = Model { input -> if (plans++ == 0)
            GuideDecision.Step(step(inbox, messages, PhotoStage.ACTION), appTask)
        else GuideDecision.Step(step(next, search, PhotoStage.ACTION), appTask) }
        val views = mutableListOf<GuideViewState>()
        val engine = GuideEngine(model, views::add)

        engine.startGoal("Gửi tin nhắn cho Minh", appTask)
        engine.onScreen(inbox)
        engine.onTap(messages)
        engine.onScreen(next)

        assertEquals(GuideStatus.WAITING_TAP, engine.status)
        assertTrue(model.inputs.last().observations.last().matchedTap)
        assertFalse(views.any { it.status == GuideStatus.RECOVERING })
        assertTrue(engine.verifiedStepIds.isEmpty())
    }
    @Test fun repeatRequiresAFreshSafeFrame() = runBlocking {
        val model = Model { GuideDecision.Step(step(home, icon, PhotoStage.LAUNCHER), task) }; val engine = GuideEngine(model, {})
        engine.startGoal("Gửi ảnh cho An"); engine.onScreen(home)
        assertEquals(1, model.spoken)
        engine.repeat(protected())
        assertEquals(1, model.spoken); assertEquals(GuideStatus.SAFETY_PAUSED, engine.status)
    }
    @Test fun offlineSpeechFailureNamesTheInstalledVoiceInsteadOfAConnection() = runBlocking {
        val views = mutableListOf<GuideViewState>()
        var repeats = 0
        val model = object : GuideModel {
            override suspend fun next(input: GuideInput) = GuideDecision.Step(step(home, icon, PhotoStage.LAUNCHER), task)
            override suspend fun repeat(step: GuidanceStep) {
                if (++repeats > 1) throw IllegalStateException("offline voice missing")
            }
            override fun cancel() = Unit
        }
        val engine = GuideEngine(model, views::add)
        engine.startGoal("Gửi ảnh cho An"); engine.onScreen(home); engine.repeat(home)
        assertEquals(GuideStatus.ASKING, engine.status)
        assertTrue(views.last().instructionVi.contains("giọng đọc ngoại tuyến"))
        assertFalse(views.last().instructionVi.contains("kết nối", ignoreCase = true))
    }
    @Test fun aSearchMessagesFieldIsNotConversationEvidence() {
        val search = composer.copy(text = "Search messages")
        assertFalse(PhotoVerification.conversation(zalo().copy(nodes = listOf(recipient, search)), task))
    }

    @Test fun generalSendRequiresTheExpectedDraftInTheVerifiedConversationComposer() = runBlocking {
        val messageTask = PhotoTask("An", "", "guide_app", "com.instagram.android", "Cháu khỏe không?")
        val title = recipient.copy(text = "An")
        val emptyComposer = composer.copy(text = "Message", className = "android.widget.EditText")
        val filledComposer = emptyComposer.copy(text = "Cháu khỏe không?")
        val generalSend = send.copy(text = "Send")
        val missing = ScreenState(messageTask.appPackage, listOf(title, emptyComposer, generalSend))
        val ready = ScreenState(messageTask.appPackage, listOf(title, filledComposer, generalSend))
        val rejected = GuideEngine(Model { GuideDecision.Step(step(missing, generalSend, PhotoStage.ACTION), messageTask) }, {})
        rejected.startGoal("Nhắn An", messageTask); rejected.onScreen(missing)
        assertEquals(GuideStatus.ASKING, rejected.status)
        val accepted = GuideEngine(Model { GuideDecision.Step(step(ready, generalSend, PhotoStage.ACTION), messageTask) }, {})
        accepted.startGoal("Nhắn An", messageTask); accepted.onScreen(ready)
        assertEquals(GuideStatus.WAITING_TAP, accepted.status)
    }
    @Test fun recipientMentionInMessageBodyCannotProveTheConversation() {
        val body = ScreenNode("com.zing.zalo:id/message_text@3", "An", Region(.1f, .4f, .6f, .5f), false)
        assertFalse(PhotoVerification.conversation(zalo().copy(nodes = listOf(body, composer)), task))
        assertFalse(PhotoVerification.conversation(zalo().copy(nodes = listOf(recipient.copy(text = "Bình"), body, composer)), task))
        assertTrue(PhotoVerification.conversation(zalo(body), task))
    }
    @Test fun recipientHeadingMustBeAtTheTopNonEditableAndUnique() {
        val heading = ScreenNode("android.widget.TextView@0", "An", Region(.2f, .05f, .7f, .1f), false,
            className = "android.widget.TextView", heading = true)
        assertTrue(PhotoVerification.conversation(zalo().copy(nodes = listOf(heading, composer)), task))
        for (invalid in listOf(heading.copy(heading = false), heading.copy(region = Region(.2f, .4f, .7f, .5f)),
            heading.copy(className = "android.widget.EditText"))) {
            assertFalse(PhotoVerification.conversation(zalo().copy(nodes = listOf(invalid, composer)), task))
        }
        assertFalse(PhotoVerification.conversation(zalo().copy(nodes = listOf(recipient, heading, composer)), task))
    }
    @Test fun stateChangedDuringPlanningRetainsHistoryAndPlansTheNextFreshFrame() = runBlocking {
        val contact = ScreenNode("contact", "An", bounds, true)
        val list = ScreenState("com.zing.zalo", listOf(contact))
        val conversation = zalo(attach)
        val fresh = zalo(attach.copy(id = "new-attach"))
        var count = 0
        val model = Model { when (count++) {
            0 -> GuideDecision.Step(step(list, contact, PhotoStage.RECIPIENT), task)
            1 -> GuideDecision.StateChanged
            else -> GuideDecision.Step(step(fresh, fresh.nodes.last(), PhotoStage.ATTACH), task)
        } }
        val views = mutableListOf<GuideViewState>(); val engine = GuideEngine(model, views::add)
        engine.startGoal("Gửi ảnh cho An"); engine.onScreen(list); engine.onTap(contact); engine.onScreen(conversation)
        assertEquals(GuideStatus.READING, engine.status); assertNull(views.last().step)
        assertEquals(setOf("recipient"), engine.verifiedStepIds); assertEquals(task, engine.task)
        engine.onScreen(fresh)
        assertEquals(GuideStatus.WAITING_TAP, engine.status)
        assertEquals(setOf("recipient"), model.inputs.last().verifiedStepIds)
        assertEquals(fresh, model.inputs.last().screen)
    }

    @Test fun screenReadVerdictKeepsUnavailableAndSensitiveSeparate() {
        assertEquals(ScreenReadVerdict.SAFE, ScreenReadPolicy.verdict(home))
        for (unreadable in listOf<ScreenState?>(null, home.copy(complete = false), home.copy(nodes = emptyList()),
            home.copy(readIssue = "traversal_limit"), home.copy(nodes = listOf(ScreenNode("unlabelled", "", bounds, true))))) {
            assertEquals(ScreenReadVerdict.CANNOT_READ, ScreenReadPolicy.verdict(unreadable))
        }
        assertEquals(ScreenReadVerdict.SENSITIVE, ScreenReadPolicy.verdict(protected().copy(complete = false)))
        assertEquals(ScreenReadVerdict.LOCKED, ScreenReadPolicy.verdict(home.copy(isLocked = true)))
        for (label in listOf("Password", "Payment", "OTP", "Mã xác thực")) {
            assertEquals(ScreenReadVerdict.SENSITIVE,
                ScreenReadPolicy.verdict(home.copy(nodes = listOf(icon.copy(text = label)))))
        }
    }

    @Test fun unreadableFrameCannotDowngradeALatchedSensitivePause() = runBlocking {
        val model = model(); val views = mutableListOf<GuideViewState>(); val engine = GuideEngine(model, views::add)
        engine.startGoal("Gửi ảnh cho An"); engine.onScreen(protected())
        engine.onScreen(home.copy(complete = false)); engine.onScreen(home)
        assertEquals(GuideStatus.SAFETY_PAUSED, engine.status)
        assertNull(views.last().step); assertTrue(model.inputs.isEmpty())
        engine.resume(); engine.onScreen(home); assertTrue(model.inputs.isEmpty())
        engine.stop(); engine.startGoal("Gửi ảnh cho An"); engine.onScreen(home)
        assertEquals(1, model.inputs.size)
    }

    @Test fun anUnreadableScreenAsksWithoutCallingItSensitive() = runBlocking {
        val model = model(); val views = mutableListOf<GuideViewState>(); val engine = GuideEngine(model, views::add)
        engine.startGoal("Gửi ảnh cho An"); engine.onScreen(home.copy(readIssue = "missing_root"))
        assertEquals(GuideStatus.ASKING, engine.status)
        assertTrue(views.last().instructionVi.contains("đọc rõ"))
        assertFalse(views.last().instructionVi.contains("riêng tư")); assertTrue(model.inputs.isEmpty())
    }

    @Test fun userNavigationFromAnotherAppKeepsGoalAndWaitsForARealScreenChange() = runBlocking {
        val browser = ScreenState("test.browser", listOf(ScreenNode("article", "Garden news", null, false)))
        val model = Model { input -> if (input.screen.packageName == browser.packageName)
            GuideDecision.Navigate("Về màn hình chính để tìm Zalo nhé.", task)
            else GuideDecision.Step(step(home, icon, PhotoStage.LAUNCHER), task) }
        val views = mutableListOf<GuideViewState>(); val engine = GuideEngine(model, views::add)
        val goal = "Gửi ảnh cho An"
        engine.startGoal(goal); engine.onScreen(browser)
        assertEquals(GuideStatus.WAITING_NAVIGATION, engine.status); assertNull(views.last().step)
        assertEquals(task, engine.task); engine.onScreen(browser); engine.repeat(browser)
        assertEquals(1, model.inputs.size)
        engine.onScreen(home)
        assertEquals(GuideStatus.WAITING_TAP, engine.status)
        assertEquals(goal, model.inputs.last().goal); assertEquals(task, model.inputs.last().task)
        assertTrue(model.inputs.last().observations.last().screenChanged)
    }

    @Test fun aRecoveryRingCanPointAtAnActualAffordanceInAnotherApp() = runBlocking {
        val back = ScreenNode("browser-back", "Back", bounds, true)
        val browser = ScreenState("test.browser", listOf(back))
        val model = Model { GuideDecision.Step(step(browser, back, PhotoStage.RECOVER), task) }
        val engine = GuideEngine(model, {})
        engine.startGoal("Gửi ảnh cho An"); engine.onScreen(browser)
        assertEquals(GuideStatus.WAITING_TAP, engine.status)
        assertEquals(browser, model.inputs.single().screen)
        assertNull(engine.launcherStep)
    }

    @Test fun launchHelpIsOnlyAvailableForTheRealZaloLauncherIcon() = runBlocking {
        for (target in listOf(icon.copy(text = "Search apps", launchPackage = null), icon.copy(className = "android.widget.EditText"),
            icon.copy(launcherFolder = true))) {
            val launcher = home.copy(nodes = listOf(target))
            val model = Model { GuideDecision.Step(step(launcher, target, PhotoStage.LAUNCHER), task) }
            val engine = GuideEngine(model, {})
            engine.startGoal("Gửi ảnh cho An"); engine.onScreen(launcher)
            assertEquals(GuideStatus.WAITING_TAP, engine.status)
            assertNull(engine.launcherStep)
        }
        val model = Model { GuideDecision.Step(step(home, icon, PhotoStage.LAUNCHER), task) }
        val engine = GuideEngine(model, {})
        engine.startGoal("Gửi ảnh cho An"); engine.onScreen(home)
        assertNotNull(engine.launcherStep)
    }

    @Test fun windowAndNavigationIdentityBindOtherwiseIdenticalFrames() {
        assertNotEquals(home.fingerprint, home.copy(windowId = 9).fingerprint)
        assertNotEquals(home.copy(windowId = 9).fingerprint, home.copy(windowId = 9, navigationGeneration = 1).fingerprint)
        assertNotEquals(home.planningFingerprint, home.copy(windowId = 9).planningFingerprint)
        assertNotEquals(home.copy(windowId = 9).planningFingerprint, home.copy(windowId = 9, navigationGeneration = 1).planningFingerprint)
    }

    @Test fun decorativeAnimationDoesNotInvalidateCapturedPlanningFrame() {
        val decoration = ScreenNode("animation", "frame 1", Region(.1f,.1f,.9f,.3f), false)
        val first = home.copy(nodes = home.nodes + decoration)
        val second = first.copy(nodes = home.nodes + decoration.copy(text = "frame 2", region = Region(.1f,.11f,.9f,.31f)))
        assertNotEquals(first.fingerprint, second.fingerprint)
        assertEquals(first.planningFingerprint, second.planningFingerprint)
        val movedControl = first.copy(nodes = listOf(icon.copy(region = Region(.2f,.2f,.4f,.4f))))
        assertNotEquals(home.planningFingerprint, movedControl.planningFingerprint)
    }

    @Test fun latePlanFromAReplacedWindowCannotRestoreItsRing() = runBlocking {
        val old = home.copy(windowId = 10)
        val new = home.copy(windowId = 11, navigationGeneration = 1)
        val entered = CompletableDeferred<Unit>(); val response = CompletableDeferred<GuideDecision>()
        val model = Model { input -> if (input.screen.windowId == 10) { entered.complete(Unit); response.await() }
            else GuideDecision.Clarify("Ông bà muốn gửi ảnh nào?") }
        val views = mutableListOf<GuideViewState>(); val engine = GuideEngine(model, views::add)
        engine.startGoal("Gửi ảnh cho An")
        val pending = async { engine.onScreen(old) }; entered.await()
        engine.onScreen(new)
        response.complete(GuideDecision.Step(step(old, icon, PhotoStage.LAUNCHER), task)); pending.await()
        assertEquals(GuideStatus.CLARIFYING, engine.status); assertNull(views.last().step); assertEquals(0, model.spoken)
        assertEquals(new, model.inputs.last().screen)
    }

    @Test fun fixedEnglishMessagesDoNotUseVietnamesePronouns() = runBlocking {
        val views = mutableListOf<GuideViewState>(); val model = model()
        val engine = GuideEngine(model, views::add, locale = "en")
        engine.startGoal("Send a photo"); assertEquals("Reading this screen…", views.last().instructionVi)
        engine.onScreen(protected())
        assertEquals(GuideStatus.SAFETY_PAUSED, engine.status)
        assertEquals("Guidance is paused to protect private information. Choose Call family or Stop.", views.last().instructionVi)
        assertFalse(views.last().instructionVi.contains("Bác"))
    }

    @Test fun sendPreviewRequiresTheCurrentRecipientTitleNotOnlyAnEarlierConversation() = runBlocking {
        val before = zalo(attach); val gallery = picker()
        // A preview may omit the composer; its current title must still identify the recipient.
        val wrongPreview = ScreenState(task.appPackage, listOf(recipient.copy(text = "Bình"), photo, send))
        val model = sendFlowModel(before, gallery, wrongPreview)
        val engine = GuideEngine(model, {})
        engine.startGoal("Gửi ảnh cho An"); engine.onScreen(before); engine.onTap(attach)
        engine.onScreen(gallery); engine.onTap(gallery.nodes.last()); engine.onScreen(wrongPreview)
        assertEquals(GuideStatus.ASKING, engine.status)
        assertFalse("send" in engine.verifiedStepIds)
        assertFalse(PhotoVerification.recipientTitle(wrongPreview, task))
        assertTrue(PhotoVerification.recipientTitle(wrongPreview.copy(nodes = listOf(recipient, photo, send)), task))
    }

    @Test fun backendSensitiveVerdictLatchesNativePauseAndClearsTheRing() = runBlocking {
        val model = Model { GuideDecision.Sensitive("Sensitive screen") }
        val views = mutableListOf<GuideViewState>(); val engine = GuideEngine(model, views::add)
        engine.startGoal("Gửi ảnh cho An"); engine.onScreen(home)
        assertEquals(GuideStatus.SAFETY_PAUSED, engine.status)
        assertNull(views.last().step); assertEquals(0, model.spoken)
        engine.onScreen(home.copy(windowId = 12)); engine.onScreen(home.copy(complete = false))
        assertEquals(GuideStatus.SAFETY_PAUSED, engine.status)
        assertEquals(1, model.inputs.size)
    }

    @Test fun askingAfterAReadFailureDoesNotInventAnActiveGoal() {
        val engine = GuideEngine(model(), {})
        assertFalse(engine.hasGoal)
        engine.inspectBeforeSpeech(home.copy(readIssue = "no_window"))
        assertEquals(GuideStatus.ASKING, engine.status); assertFalse(engine.hasGoal)
        engine.startGoal("Gửi ảnh cho An"); assertTrue(engine.hasGoal)
        engine.stop(); assertFalse(engine.hasGoal)
    }
}
