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

    @Test fun browserArticleDoesNotPairSensitiveWordsWithTheAddressBar() {
        val chrome = ScreenState("com.android.chrome", listOf(
            ScreenNode("com.android.chrome:id/url_bar", "How to create a password", Region(.02f, .02f, .98f, .09f),
                true, focused = true, className = "android.widget.EditText"),
            ScreenNode("article", "Payment details explained", Region(.05f, .25f, .95f, .55f), false,
                className = "android.widget.TextView")
        ))

        assertFalse(SafetyPolicy.sensitive(chrome))
    }

    @Test fun browserStillBlocksRealSensitiveFormsAndPaymentActions() {
        val otp = ScreenState("com.android.chrome", listOf(
            ScreenNode("prompt", "Enter your OTP", Region(.1f, .3f, .9f, .4f), false),
            ScreenNode("otp_field", "", Region(.1f, .42f, .9f, .52f), true,
                focused = true, className = "android.widget.EditText")
        ))
        val payment = ScreenState("com.android.chrome", listOf(
            ScreenNode("pay", "Pay now", Region(.1f, .7f, .9f, .82f), true,
                className = "android.widget.Button")
        ))

        assertTrue(SafetyPolicy.sensitive(otp))
        assertTrue(SafetyPolicy.sensitive(payment))
    }
}
