package vn.guidinghand

data class GuideFamily(val contact: String, val network: String, val photo: String, val address: String)
data class MetaChoice(val id: String, val goal: StepGoal, val node: ScreenNode, val instruction: String)

/** The model selects a listed choice. It cannot invent a target, completion rule or instruction. */
object MetaChoices {
    fun build(screen: ScreenState, task: GuideTask, family: GuideFamily, language: String): List<MetaChoice> {
        if (!screen.complete || SafetyPolicy.sensitive(screen)) return emptyList()
        if (screen.packageName != task.appPackage && screen.packageName !in task.allowedPackages &&
            !(task.id == "wifi" && screen.packageName.startsWith("com.android.settings"))) return emptyList()
        val recipient = task.recipient ?: family.contact
        val result = mutableListOf<MetaChoice>()
        fun add(step: StepGoal, node: ScreenNode, instruction: String? = null) {
            if (result.size >= 100 || node.region == null || node.password) return
            result += MetaChoice("c${result.size}", step, node, instruction ?: GuidanceText.instruction(
                step, node, task.appLabel, recipient, language, family.address))
        }
        for (step in task.steps.asReversed()) {
            if (step.id in listOf("video", "attach", "compose") &&
                (recipient.isBlank() || !screen.has(recipient))) continue
            if (step.id == "send" && screen.nodes.none { node -> node.selected && family.photo.isNotBlank() &&
                    (node.aliases + node.text).any { folded(it) == folded(family.photo) } }) continue
            val labels = step.labels.map { when (it) {
                "{family}" -> recipient; "{network}" -> family.network; "{photo}" -> family.photo; else -> it
            } }.filter { it.isNotBlank() }
            val matches = if (step.id == "openApp") listOfNotNull(LauncherTargets.icon(screen, task.appLabel))
            else screen.nodes.filter { node -> node.region != null && !node.password &&
                (node.clickable || step.completion == "scan" || (step.completion == "focus" && node.className.contains("EditText"))) &&
                labels.any { label -> (node.aliases + node.text).any { folded(it) == folded(label) } }
            }.distinctBy { it.region }
            if (step.id == "conversation" && matches.size != 1) continue
            matches.forEach { add(step, it) }
        }
        val query = if (task.id == "open") task.appLabel else recipient
        if (query.isNotBlank() && task.id in listOf("open", "message", "video", "photo")) {
            val search = if (task.id == "open") LauncherTargets.search(screen) else screen.nodes.filter { node ->
                node.region != null && !node.password && (node.clickable || node.className.contains("EditText")) &&
                    (node.aliases + node.text).any { folded(it) in listOf("search", "search zalo", "tim kiem") }
            }.distinctBy { it.region }.singleOrNull()
            search?.let {
                val ui = UiText(language)
                val sentence = if (it.focused) ui.pick("Type $query in this search box.", "Gõ $query vào ô tìm kiếm.")
                    else ui.pick("Tap the highlighted search box.", "Bấm ô tìm kiếm được đánh dấu.")
                add(StepGoal(if (it.focused) "enterRecipient" else "search", listOf(it.text), sentence,
                    listOf(query), if (it.focused) "text" else "focus"), it, sentence)
            }
        }
        val back = screen.nodes.filter { it.clickable && it.region != null && !it.password &&
            folded(it.text) in listOf("back", "navigate up", "quay lai", "tro lai") }.distinctBy { it.region }.singleOrNull()
        back?.let {
            val sentence = UiText(language).pick("Tap the highlighted Back button.", "Bấm nút Quay lại được đánh dấu.")
            add(StepGoal("recover", listOf(it.text), sentence, task.steps.flatMap { goal -> goal.labels }), it, sentence)
        }
        return result
    }

    fun resolve(choices: List<MetaChoice>, status: String, choiceId: String?, requestId: String,
                responseId: String, fingerprint: String, responseFingerprint: String): MetaChoice? {
        if (status != "guide" || requestId != responseId || fingerprint != responseFingerprint) return null
        return choices.singleOrNull { it.id == choiceId }
    }
}
