package vn.guidinghand

import android.graphics.Bitmap
import android.graphics.RectF
import java.text.Normalizer

fun folded(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").replace('đ', 'd').trim()

data class Region(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init { require(left >= 0 && top >= 0 && right <= 1 && bottom <= 1 && left < right && top < bottom) }
    fun rectF() = RectF(left, top, right, bottom)
}
data class ScreenNode(
    val id: String, val text: String, val region: Region?, val clickable: Boolean,
    val selected: Boolean = false, val password: Boolean = false,
    val className: String = "", val aliases: List<String> = emptyList(), val focused: Boolean = false
)
data class ScreenState(val packageName: String, val nodes: List<ScreenNode>, val complete: Boolean = true) {
    val fingerprint: String get() = "$packageName:$complete:" + nodes.joinToString { "${it.id}:${it.text}:${it.region}:${it.selected}:${it.password}:${it.focused}:${it.aliases}" }
    fun has(vararg words: String) = nodes.any { node -> words.any { word -> (node.aliases + node.text).any { folded(it).contains(folded(word)) } } }
}
data class GuideFrame(val screen: ScreenState, val screenshot: Bitmap?)
data class StepGoal(val id: String, val labels: List<String>, val instruction: String,
                    val expected: List<String>, val completion: String = "text")
data class GuideTask(val id: String, val label: String, val steps: List<StepGoal>,
                     val recipient: String? = null, val allowedPackages: Set<String> = emptySet(),
                     val appPackage: String = "com.zing.zalo", val appLabel: String = "Zalo")
data class ModelStep(val goal: StepGoal, val node: ScreenNode, val instruction: String) {
    val target: RectF get() = requireNotNull(node.region).rectF()
}

/** The only backend seam. Frames remain in memory on this phone. */
interface GuideModel {
    suspend fun getNextStep(screenshot: GuideFrame, goal: GuideTask): ModelStep?
    fun speak(text: String)
    fun silence()
    fun close()
}

object Tasks {
    private fun step(id: String, labels: List<String>, instruction: String, vararg expected: String, completion: String = "text") =
        StepGoal(id, labels, instruction, expected.toList(), completion)
    val all = listOf(
        GuideTask("video", "Gọi hình cho người thân", listOf(
            step("chat", listOf("Tin nhắn", "Messages"), "Bấm Tin nhắn ở dưới.", "Tìm kiếm", "Search"),
            step("conversation", listOf("{family}"), "Bấm tên người thân ở giữa.", "Gọi video", "Video call"),
            step("video", listOf("Gọi video", "Video call"), "Bấm Gọi video ở phía trên.", "Kết thúc", "Đang gọi", "End call", "Calling")
        )),
        GuideTask("photo", "Gửi ảnh cho người thân", listOf(
            step("conversation", listOf("{family}"), "Bấm tên người thân ở giữa.", "Gửi ảnh", "Hình ảnh", "Photo"),
            step("attach", listOf("Gửi ảnh", "Hình ảnh", "Ảnh", "Photo"), "Bấm Hình ảnh ở phía dưới.", "Thư viện", "Gần đây", "Gallery", "Recent"),
            step("choose", listOf("{photo}"), "Bấm ảnh cần gửi ở giữa.", "Gửi", "Send", completion = "selection"),
            step("send", listOf("Gửi", "Send"), "Bấm Gửi ở phía dưới, bên phải.", "Đã gửi", "Đã nhận", "Sent", "Delivered", completion = "photoSent")
        )),
        GuideTask("font", "Phóng to chữ trong Zalo", listOf(
            step("personal", listOf("Cá nhân", "Personal"), "Bấm Cá nhân ở dưới, bên phải.", "Cài đặt", "Settings"),
            step("settings", listOf("Cài đặt", "Settings"), "Bấm Cài đặt ở phía trên.", "Giao diện", "Cỡ chữ", "Appearance"),
            step("font", listOf("Cỡ chữ", "Giao diện", "Font size", "Appearance"), "Bấm Cỡ chữ ở giữa.", "Rất lớn", "Lớn", "Very large", "Large"),
            step("large", listOf("Rất lớn", "Very large", "Lớn", "Large"), "Bấm chữ Lớn ở giữa.", completion = "selection")
        )),
        GuideTask("wifi", "Kết nối mạng không dây", listOf(
            step("network", listOf("{network}"), "Bấm tên mạng ở giữa.", "Đã kết nối", "Connected", completion = "wifi")
        )),
        GuideTask("qr", "Quét mã bằng Zalo", listOf(
            step("scanner", listOf("Quét mã QR", "Quét QR", "Scan QR"), "Bấm Quét mã ở phía trên.", "Đưa mã", "Di chuyển mã", "Quét mã QR", "Scan QR"),
            step("frame", listOf("Khung quét", "Vùng quét", "QR scanner"), "Đặt mã vào giữa khung.", "Kết quả quét", "QR result", completion = "scan")
        ))
    )
    fun openApp(label: String, packageName: String) = GuideTask("open", "Open $label", listOf(
        step("openApp", listOf(label), "Bấm $label trên màn hình.", packageName, completion = "app")
    ), appPackage = packageName, appLabel = label)
    val openZalo = openApp("Zalo", "com.zing.zalo")
    val openInstagram = openApp("Instagram", "com.instagram.android")
    val message = GuideTask("message", "Send a message", listOf(
        step("conversation", listOf("{family}"), "Bấm tên người cần nhắn.", "Tin nhắn", "Message", "Gọi video", "Video call"),
        step("compose", listOf("Tin nhắn", "Nhập tin nhắn", "Message", "Type a message"), "Bấm ô tin nhắn ở dưới.", completion = "focus")
    ))
    fun messageIn(packageName: String, label: String): GuideTask {
        if (packageName == "com.zing.zalo") return message
        return GuideTask("message", "Message in $label", listOf(
            step("inbox", listOf("Messages", "Your messages", "Direct messages", "Direct messaging", "Open messages", "Open inbox", "Direct", "Messenger", "Inbox", "Tin nhắn"), "Bấm Tin nhắn ở trên.", "Search", "New message", "Tìm kiếm", "New Message"),
            step("conversation", listOf("{family}"), "Bấm tên người cần nhắn.", "Message", "Message…", "Nhắn tin", "Gọi thoại", "Audio call"),
            step("compose", listOf("Message", "Message…", "Message...", "Type a message", "Nhắn tin", "Tin nhắn"), "Bấm ô tin nhắn ở dưới.", completion = "focus")
        ), appPackage = packageName, appLabel = label)
    }
    fun fromGoal(text: String): GuideTask? {
        val query = folded(text)
        fun has(vararg terms: String) = terms.any { term ->
            Regex("(^|[^a-z0-9])${Regex.escape(term)}([^a-z0-9]|$)").containsMatchIn(query)
        }
        return when {
            has("video", "goi hinh", "goi video") -> all[0]
            has("call", "goi") -> GuideTask("call", "Call someone", emptyList())
            has("font", "text size", "larger", "phong to", "co chu") -> all[2]
            has("message", "text", "nhan tin", "nhan cho") -> message
            has("photo", "picture", "gui anh", "gui hinh") -> all[1]
            has("wifi", "wi-fi", "network", "mang") -> all[3]
            has("qr", "scan", "quet") -> all[4]
            has("zalo") -> openZalo
            else -> null
        }
    }
}

object SafetyPolicy {
    private val terms = listOf("mat khau", "password", "thanh toan", "payment", "otp", "ma xac thuc", "ma xac nhan", "pin", "cvv")
    fun sensitive(screen: ScreenState) = screen.nodes.any { node ->
        node.password || terms.any { term -> (node.aliases + node.text).any { Regex("(^|[^a-z0-9])${Regex.escape(term)}([^a-z0-9]|$)").containsMatchIn(folded(it)) } }
    }
}

object Verification {
    fun confirms(step: ModelStep, before: ScreenState, after: ScreenState): Boolean {
        if (!before.complete || !after.complete || before.fingerprint == after.fingerprint || SafetyPolicy.sensitive(after)) return false
        return when (step.goal.completion) {
            "app" -> after.packageName in step.goal.expected && before.packageName != after.packageName
            "focus" -> after.nodes.any { it.focused && it.id == step.node.id && it.className.contains("EditText") } &&
                before.nodes.none { it.focused && it.id == step.node.id }
            "selection" -> after.nodes.any { it.selected && !before.nodes.any { old -> old.id == it.id && old.selected } &&
                (it.id == step.node.id || folded(it.text) == folded(step.node.text)) }
            "wifi" -> after.has(step.node.text) && after.has(*step.goal.expected.toTypedArray()) &&
                !before.has(*step.goal.expected.toTypedArray())
            "photoSent" -> after.has(*step.goal.expected.toTypedArray()) &&
                !before.has(*step.goal.expected.toTypedArray()) &&
                after.nodes.any { node -> node.className.endsWith("ImageView") &&
                    folded(node.text).let { it.contains("anh da gui") || it.contains("sent photo") } &&
                    before.nodes.none { it.id == node.id && it.text == node.text } } &&
                !after.has("Thư viện", "Gallery", "Gần đây", "Recent")
            "scan" -> after.has(*step.goal.expected.toTypedArray()) && !before.has(*step.goal.expected.toTypedArray())
            else -> after.has(*step.goal.expected.toTypedArray()) &&
                !before.has(*step.goal.expected.toTypedArray())
        }
    }
}
