package vn.guidinghand

/** Local label grounding, not AI planning. Never invents an unlabeled target. */
object LocalScreenGuide {
    fun candidates(screen: ScreenState): List<ScreenNode> {
        if (!screen.complete || SafetyPolicy.sensitive(screen) || screen.packageName == "vn.guidinghand") return emptyList()
        return screen.nodes.filter { it.clickable && it.region != null && !it.password &&
            it.text.isNotBlank() && it.text.length <= 80 && !it.className.contains("EditText") }
            .distinctBy { folded(it.text) to it.region }
            .filter { node -> screen.nodes.count { other -> other.clickable && folded(other.text) == folded(node.text) && other.region != node.region } == 0 }
    }
    fun match(screen: ScreenState, utterance: String): ScreenNode? {
        if (SafetyPolicy.sensitive(screen)) return null
        val query = folded(utterance).replace(Regex("^(?:please\\s+)?(?:tap|press|find|show me|where is|open|bam|tim|mo)\\s+"), "").trim(' ', '.', '?')
        if (query.length < 2) return null
        val candidates = candidates(screen)
        fun labels(node: ScreenNode) = node.aliases + node.text + GuidanceText.localizedControlLabels(node)
        val exact = candidates.filter { labels(it).any { label -> folded(label) == query } }.distinctBy { it.region }
        if (exact.size == 1) return exact.single()
        if (exact.isNotEmpty()) return null
        val partial = candidates.filter { labels(it).any { label -> folded(label).contains(query) } }.distinctBy { it.region }
        return partial.singleOrNull()
    }
    fun task(screen: ScreenState, node: ScreenNode) = GuideTask("screen", "Find ${node.text}",
        listOf(StepGoal("localTarget", listOf(node.text), "", emptyList(), "inspect")),
        allowedPackages = setOf(screen.packageName), appPackage = screen.packageName, appLabel = "")
}
