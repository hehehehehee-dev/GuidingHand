package vn.guidinghand

/** Ignore read-only counters/body churn, but preserve navigation, focused fields and top-of-screen identity. */
object PlanningContext {
    private val volatile = Regex("^(?:\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\s*/\\s*\\d{1,2}:\\d{2})?|[\\d\\s.,]+(?:k|m|b)?\\s*(?:views?|watching|likes?|comments?|seconds?|minutes?|hours?|giay|phut|gio|luot xem)(?:\\s+ago)?)$")
    private val mathValue = Regex("^[\\d\\s.,+−×÷*/%=()\\-]+$")
    private val counterId = Regex("(?i)(view.?count|like.?count|comment.?count|elapsed|timestamp|duration)")
    private fun label(text: String) = folded(text).trim().take(180)
    private fun labels(node: ScreenNode) = (node.aliases + node.text).map(::label).filter { it.isNotBlank() && !volatile.matches(it) }.distinct().sorted()
    private fun stableId(node: ScreenNode) = if (node.className.isNotBlank() && node.id.startsWith(node.className + ":")) node.className else node.id
    private fun bounds(region: Region?) = region?.let { listOf(it.left, it.top, it.right, it.bottom).joinToString { coordinate -> kotlin.math.round(coordinate * 200).toInt().toString() } }.orEmpty()
    fun key(screen: ScreenState): String = "${screen.packageName}:${screen.complete}:" + screen.nodes.mapNotNull { node ->
        val significant = node.clickable || node.focused || node.selected || node.password || node.className.contains("EditText")
        val isValue = mathValue.matches(node.text.trim()) && node.text.isNotBlank() && !counterId.containsMatchIn(node.id) && !volatile.matches(label(node.text))
        if (!significant && !isValue && (node.region == null || node.region.top > .3f || labels(node).isEmpty() || counterId.containsMatchIn(node.id))) null
        else "${stableId(node)}:${bounds(node.region)}:${node.clickable}:${node.focused}:${node.selected}:${node.password}:${node.launcherFolder}:${node.inLauncherFolder}:${if (significant) label(node.text) else labels(node)}"
    }.sorted().joinToString("|")
    fun compatible(before: ScreenState, after: ScreenState?) = after != null && before.complete && after.complete &&
        before.packageName == after.packageName && !SafetyPolicy.sensitive(before) && !SafetyPolicy.sensitive(after) && key(before) == key(after)
    fun rebind(node: ScreenNode, after: ScreenState): ScreenNode? = after.nodes.filter {
        it.clickable && !it.password && it.region != null && stableId(it) == stableId(node) &&
            it.launcherFolder == node.launcherFolder && it.inLauncherFolder == node.inLauncherFolder &&
            label(it.text) == label(node.text) && labels(it) == labels(node)
    }.distinctBy { it.region }.singleOrNull()
}
