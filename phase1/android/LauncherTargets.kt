package vn.guidinghand

/** Grounds launcher/search targets in the current accessible tree; never supplies coordinates. */
object LauncherTargets {
    private val searchLabels = setOf(
        "search", "search apps", "search for apps", "search your phone", "search apps and content",
        "tim kiem", "tim kiem ung dung"
    )
    private fun normalized(value: String) = folded(value).replace(Regex("\\s+"), " ")
    private fun safe(screen: ScreenState) = screen.complete && screen.packageName != "vn.guidinghand" &&
        !SafetyPolicy.sensitive(screen)
    private fun editable(node: ScreenNode) = node.className.contains("EditText", ignoreCase = true) ||
        node.className.contains("AutoCompleteTextView", ignoreCase = true)
    private fun labels(node: ScreenNode) = (node.aliases + node.text).map(::normalized)

    fun icon(screen: ScreenState, label: String): ScreenNode? {
        if (!safe(screen)) return null
        val wanted = normalized(label)
        if (wanted.isBlank()) return null
        val candidates = screen.nodes.filter { node -> node.region != null && node.clickable && !node.password &&
            (node.region.right - node.region.left) * (node.region.bottom - node.region.top) <= .2f &&
            !node.launcherFolder && !node.inLauncherFolder &&
            !editable(node) && wanted in labels(node) && screen.nodes.none { child ->
                val outer = node.region; val inner = child.region
                editable(child) && outer != null && inner != null && outer.left <= inner.left && outer.top <= inner.top &&
                    outer.right >= inner.right && outer.bottom >= inner.bottom && (child.focused || wanted in labels(child))
            } }.distinctBy { it.region }
        // A clickable workspace/folder can inherit the child's label. Prefer the actual inner icon.
        return candidates.filter { outer -> candidates.none { inner ->
            if (inner === outer) false else {
                val a = outer.region!!; val b = inner.region!!
                a.left <= b.left && a.top <= b.top && a.right >= b.right && a.bottom >= b.bottom && a != b
            }
        } }.singleOrNull()
    }

    private fun hasSearchIdentity(node: ScreenNode): Boolean {
        if (labels(node).any { it in searchLabels }) return true
        if (node.className.substringAfterLast('.').equals("SearchView", ignoreCase = true)) return true
        // Resource IDs are an additional semantic cue only for actual editable controls.
        // Do not confuse a package containing "search" or an unrelated focused composer with Search.
        if (!editable(node)) return false
        val id = node.id.substringAfterLast('/').replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
        return Regex("(^|[_-])(search|searchbox|searchbar|query|querytext)($|[_-])").containsMatchIn(id)
    }

    fun search(screen: ScreenState): ScreenNode? {
        if (!safe(screen)) return null
        val candidates = screen.nodes.filter { node -> node.region != null && !node.password &&
            (node.clickable || editable(node)) && hasSearchIdentity(node) }
            .sortedWith(compareByDescending<ScreenNode> { it.focused && editable(it) }.thenByDescending { editable(it) })
            .distinctBy { it.region }
        val focused = candidates.filter { it.focused && editable(it) }
        return if (focused.isNotEmpty()) focused.singleOrNull() else candidates.singleOrNull()
    }

    fun hasTarget(screen: ScreenState, appLabel: String) = icon(screen, appLabel) != null || search(screen) != null ||
        LauncherNavigation.folder(screen, appLabel) != null || LauncherNavigation.drawerButton(screen) != null
}
