package vn.guidinghand

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyPolicyTest {
    @Test fun sensitiveGateRecognizesBothLanguagesAndPasswordNodesWithoutBlockingLongerWords() {
        fun screen(label: String) = ScreenState("test.app", listOf(ScreenNode("label", label, null, false)))

        assertTrue(SafetyPolicy.sensitive(screen("Nhập mã xác thực")))
        assertTrue(SafetyPolicy.sensitive(screen("Enter your OTP")))
        assertTrue(SafetyPolicy.sensitive(screen("Payment details")))
        assertTrue(SafetyPolicy.sensitive(ScreenState("test.app", listOf(ScreenNode("field", "", null, true, password = true)))))
        assertFalse(SafetyPolicy.sensitive(screen("Pinterest")))
        assertFalse(SafetyPolicy.sensitive(screen("Bài báo hôm nay")))
    }
}
