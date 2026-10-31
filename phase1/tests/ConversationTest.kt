// MOCK: Language and intent fixtures; no microphone, contact list or AI service.
package vn.guidinghand

import org.junit.Assert.*
import org.junit.Test

class ConversationTest {
    @Test fun englishIsDefaultAndVietnameseIsOptional() {
        assertEquals("What would you like to do?", UiText().ask)
        assertEquals("Muốn làm việc gì?", UiText("vi").ask)
        assertEquals("Find Zalo", UiText().task("open"))
        assertEquals("Tìm Zalo", UiText("vi").task("open"))
    }
    @Test fun englishAndVietnameseRouteToTheSameTasks() {
        mapOf("Find Zalo" to "open", "Tìm Zalo" to "open", "message Alice" to "message", "nhắn tin cho mẹ" to "message",
            "video call grandma" to "video", "gọi cho bà" to "call", "send a photo" to "photo", "gửi ảnh" to "photo",
            "increase text size" to "font", "Wi-Fi" to "wifi", "scan QR" to "qr").forEach { (text, id) ->
            assertEquals(text, id, Tasks.fromGoal(text)?.id)
        }
    }
    @Test fun recipientsAreExtractedForConfirmation() {
        assertEquals("Alice", Conversation.recipient("message Alice"))
        assertEquals("Grandma", Conversation.recipient("send a photo to Grandma"))
        assertEquals("mẹ", Conversation.recipient("nhắn tin cho mẹ"))
        assertNull(Conversation.recipient("Find Zalo"))
    }
    @Test fun unknownGoalsAreNotPretendedToBeSupported() {
        assertNull(Tasks.fromGoal("book a flight"))
        assertNull(Tasks.fromGoal("Instagram"))
        assertNull(Tasks.fromGoal("Richard"))
        assertEquals("message", Tasks.fromGoal("message Anh")?.id)
        assertEquals("Alice", Conversation.recipient("message Alice on Instagram"))
        assertEquals("mẹ", Conversation.recipient("nhắn tin cho mẹ qua Zalo"))
    }
    @Test fun openingAnAppRequiresTheActualPackageTransition() {
        val node = ScreenNode("zalo", "Zalo", Region(.1f, .2f, .3f, .4f), true)
        val step = ModelStep(Tasks.openZalo.steps.single(), node, "Tap Zalo.")
        val launcher = ScreenState("com.miui.home", listOf(node))
        assertFalse(Verification.confirms(step, launcher, launcher.copy(nodes = listOf(node.copy(text = "Zalo badge")))))
        assertTrue(Verification.confirms(step, launcher, ScreenState("com.zing.zalo", emptyList())))
        assertFalse(Verification.confirms(step, launcher, ScreenState("some.other.app", emptyList())))
    }
    @Test fun appContextsAreRemovedWithoutTruncatingRealNames() {
        assertEquals("Alice", Conversation.recipient("message Alice with Instagram"))
        assertNull(Conversation.recipient("message in Zalo"))
        assertNull(Conversation.recipient("call on Zalo"))
        assertEquals("mẹ", Conversation.recipient("nhắn cho mẹ bang Zalo"))
        assertEquals("Alice in Wonderland", Conversation.recipient("message Alice in Wonderland"))
    }
    @Test fun appLabelsContainingTaskWordsStillOpenAsApps() {
        val appNames = listOf("Mi Video", "Photos", "Phone Call", "gọi cho cháu")
        assertTrue(Conversation.isAppOpenRequest("open Mi Video", appNames))
        assertTrue(Conversation.isAppOpenRequest("Mi Video", appNames))
        assertTrue(Conversation.isAppOpenRequest("tôi muốn mở Photos", appNames))
        assertTrue(Conversation.isAppOpenRequest("gọi cho cháu", appNames))
        assertFalse(Conversation.isAppOpenRequest("video call Alice in Zalo", appNames))
        assertFalse(Conversation.isAppOpenRequest("message Alice in Instagram", appNames))
    }
    @Test fun fuzzyAppSuffixOnlyDropsAfterAnAppWasMatchedForConfirmation() {
        assertEquals("Alice on Instagran", Conversation.recipient("message Alice on Instagran"))
        assertEquals("Alice", Conversation.recipient("message Alice on Instagran", matchedAppContext = true))
        assertEquals("mẹ", Conversation.recipient("nhắn cho mẹ bằng ja lô", matchedAppContext = true))
    }
    @Test fun messageFocusRequiresTheCorrectEditableField() {
        val target = ScreenNode("message", "Message", Region(.1f, .2f, .3f, .4f), true, className = "android.widget.EditText")
        val step = ModelStep(Tasks.message.steps.last(), target, "Tap Message.")
        val before = ScreenState("com.zing.zalo", listOf(target))
        assertFalse(Verification.confirms(step, before, before.copy(nodes = listOf(target.copy(id = "search", focused = true)))))
        assertTrue(Verification.confirms(step, before, before.copy(nodes = listOf(target.copy(focused = true)))))
    }
}
