package vn.guidinghand

import org.junit.Assert.*
import org.junit.Test

class SafetyAndVerificationTest {
    private fun screen(vararg nodes: ScreenNode) = ScreenState("com.zing.zalo", nodes.toList())
    private fun node(text: String, id: String = text, selected: Boolean = false, password: Boolean = false) =
        ScreenNode(id, text, Region(.1f, .2f, .3f, .4f), true, selected, password)
    private val goal = StepGoal("video", listOf("Gọi video"), "Bấm Gọi video.", listOf("Đang gọi"))
    private val target = node("Gọi video")
    private val step = ModelStep(goal, target, goal.instruction)

    @Test fun passwordAndPaymentAlwaysStop() {
        listOf("Mật khẩu", "PASSWORD", "Xác nhận thanh toán", "Payment", "OTP", "Mã xác nhận", "Nhập PIN").forEach {
            assertTrue(it, SafetyPolicy.sensitive(screen(node(it))))
        }
        assertTrue(SafetyPolicy.sensitive(screen(node("", password = true))))
    }
    @Test fun unrelatedWordsDoNotTriggerOtpOrPin() {
        assertFalse(SafetyPolicy.sensitive(screen(node("Shopping", id = "shop"), node("Ghim tin nhắn"))))
    }
    @Test fun noChangeNeverCompletes() {
        val before = screen(target)
        assertFalse(Verification.confirms(step, before, before))
    }
    @Test fun unrelatedChangeNeverCompletes() {
        assertFalse(Verification.confirms(step, screen(target), screen(target, node("Thông báo mới"))))
    }
    @Test fun newlyExpectedStateConfirms() {
        assertTrue(Verification.confirms(step, screen(target), screen(node("Đang gọi"))))
    }
    @Test fun existingExpectedTextCannotConfirm() {
        assertFalse(Verification.confirms(step, screen(target, node("Đang gọi")), screen(node("Đang gọi"), node("Tin nhắn"))))
    }
    @Test fun safetyOverridesExpectedState() {
        assertFalse(Verification.confirms(step, screen(target), screen(node("Đang gọi"), node("Thanh toán"))))
    }
    @Test fun selectionRequiresTheActualTarget() {
        val selection = step.copy(goal = goal.copy(completion = "selection"))
        assertFalse(Verification.confirms(selection, screen(target), screen(target, node("Khác", selected = true))))
        assertTrue(Verification.confirms(selection, screen(target), screen(target.copy(selected = true))))
    }
    @Test fun photoPickerClosingIsNotSuccess() {
        val photo = step.copy(goal = goal.copy(completion = "photoSent", expected = listOf("Đã gửi")))
        assertFalse(Verification.confirms(photo, screen(node("Thư viện")), screen(node("Đã gửi"))))
    }
    @Test fun wifiNeedsSelectedNetworkAndNewConnectedState() {
        val network = node("Mang nha")
        val wifi = step.copy(node = network, goal = goal.copy(completion = "wifi", expected = listOf("Đã kết nối")))
        assertFalse(Verification.confirms(wifi, screen(network), screen(node("Mang khac"), node("Đã kết nối"))))
        assertTrue(Verification.confirms(wifi, screen(network), screen(network, node("Đã kết nối"))))
    }
    @Test fun regionsRejectPixelsAndOutOfBounds() {
        assertThrows(IllegalArgumentException::class.java) { Region(10f, 20f, 30f, 40f) }
        assertThrows(IllegalArgumentException::class.java) { Region(.5f, .2f, .1f, .3f) }
    }
    @Test fun partialScreenEvidenceCannotConfirmCompletion() {
        assertFalse(Verification.confirms(step, screen(target), screen(node("Đang gọi")).copy(complete = false)))
    }
}
