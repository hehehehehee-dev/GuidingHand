package vn.guidinghand

/** Free-form goals use real labeled controls, never model-generated coordinates or final actions. */
object GeneralGuidePolicy {
    private val consequential = Regex("(^|[^a-z0-9])(send|submit|delete|remove|erase|buy|purchase|pay|confirm|transfer|publish|post|call|dial|install|uninstall|allow|accept|reset|gui|xoa|mua|tra tien|xac nhan|chuyen tien|dang|goi|cai dat ung dung|cho phep|dong y|dat lai)([^a-z0-9]|$)")
    fun sensitiveGoal(text: String) = SafetyPolicy.sensitive(ScreenState("goal", listOf(ScreenNode("goal", text, null, false))))
    fun task(screen: ScreenState, goal: String, packages: Set<String> = emptySet(), recipient: String? = null) = GuideTask("assist", goal.take(500),
        listOf(StepGoal("assistStep", emptyList(), "", emptyList(), "inspect")),
        appPackage = screen.packageName, appLabel = "", allowedPackages = packages, recipient = recipient)
    fun choices(screen: ScreenState, language: String): List<MetaChoice> {
        if (!screen.complete || SafetyPolicy.sensitive(screen) || screen.packageName == "vn.guidinghand") return emptyList()
        val candidates = screen.nodes.filter { node -> node.region != null && !node.password &&
            node.text.isNotBlank() && node.text.length <= 120 && node.clickable &&
            (node.aliases + node.text).none { consequential.containsMatchIn(folded(it)) }
        }.distinctBy { it.region }
        return candidates.filter { node -> candidates.count { folded(it.text) == folded(node.text) } == 1 }
            .take(100).mapIndexed { index, node ->
                val step = StepGoal("assistStep", listOf(node.text), "", emptyList(), "inspect")
                MetaChoice("c$index", step, node, GuidanceText.instruction(step.copy(id = "localTarget"), node, "", "", language, ""))
            }
    }
    fun question(code: String, ui: UiText) = when (code) {
        "connection" -> ui.pick("I couldn't reach the online guide. Try again or choose an on-screen control.", "Trợ lý qua mạng chưa sẵn sàng. Thử lại hoặc chọn nút trên màn hình nhé.")
        "moving" -> ui.pick("The page is still changing. Let it settle, then choose Continue.", "Trang vẫn đang thay đổi. Chờ ổn định rồi bấm Tiếp tục nhé.")
        "recipient" -> ui.pick("Who is this for? Say the name, please.", "Muốn làm việc này cho ai? Nói tên giúp mình nhé.")
        "item" -> ui.pick("Which item do you mean? Tell me a little more.", "Muốn chọn mục nào? Nói rõ hơn một chút nhé.")
        "app" -> ui.pick("Which app would you like to use?", "Muốn dùng ứng dụng nào?")
        "type" -> ui.pick("Type in the app, then choose Continue when you're ready.", "Gõ nội dung trong ứng dụng, rồi bấm Tiếp tục khi sẵn sàng nhé.")
        "handoff" -> ui.pick("Please review the final action in the app and do it yourself. I'll wait here.", "Kiểm tra thao tác cuối trong ứng dụng rồi tự thực hiện nhé. Mình đợi ở đây.")
        "scroll_down" -> ui.pick("Swipe up to see more, then choose Continue.", "Vuốt lên để xem tiếp, rồi bấm Tiếp tục nhé.")
        "scroll_up" -> ui.pick("Swipe down to go back, then choose Continue.", "Vuốt xuống để xem phía trước, rồi bấm Tiếp tục nhé.")
        else -> ui.pick("I need a little more detail. What would you like to do here?", "Mình cần hiểu rõ hơn một chút. Muốn làm gì ở đây?")
    }
}
