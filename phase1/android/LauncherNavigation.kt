package vn.guidinghand

/** Launcher navigation uses actual visible controls. A folder preview is never an app icon. */
object LauncherNavigation {
    private val drawerLabels = setOf("all apps", "apps", "app drawer", "all applications", "tat ca ung dung", "danh sach ung dung")
    fun folderIcon(id: String, className: String): Boolean = className.substringAfterLast('.').contains("FolderIcon", true) ||
        id.substringAfterLast('/') in setOf("folder_icon", "folder_icon_view")
    fun drawer(screen: ScreenState): Boolean = screen.complete && !SafetyPolicy.sensitive(screen) && screen.nodes.any {
        it.id.substringAfterLast('/') in setOf("all_apps_category_container", "all_apps_container", "all_apps_viewpager", "apps_view", "apps_list_view")
    }
    fun openFolder(screen: ScreenState) = screen.nodes.any {
        it.className.substringAfterLast('.') == "Folder" || it.id.substringAfterLast('/') in setOf("folder_content", "folder_content_list", "folder_content_container")
    }
    fun drawerButton(screen: ScreenState): ScreenNode? {
        if (!screen.complete || SafetyPolicy.sensitive(screen)) return null
        return screen.nodes.filter { it.clickable && it.region != null && !it.password && !it.inLauncherFolder &&
            !it.launcherFolder && (it.aliases + it.text).any { label -> folded(label) in drawerLabels } }
            .distinctBy { it.region }.singleOrNull()
    }
    fun folder(screen: ScreenState, appLabel: String): ScreenNode? {
        if (!screen.complete || SafetyPolicy.sensitive(screen)) return null
        return screen.nodes.filter { it.launcherFolder && !it.inLauncherFolder && it.clickable && it.region != null &&
            !it.password && (it.aliases + it.text).any { label -> folded(label) == folded(appLabel) } }
            .distinctBy { it.region }.singleOrNull()
    }
    fun step(node: ScreenNode, appLabel: String, language: String, folder: Boolean): ModelStep {
        val ui = UiText(language)
        val sentence = if (folder) ui.pick("Tap this folder to find $appLabel.", "Bấm thư mục này để tìm $appLabel.")
            else ui.pick("Tap here to see all apps.", "Bấm vào đây để xem tất cả ứng dụng.")
        return ModelStep(StepGoal(if (folder) "launcherFolder" else "launcherDrawer", emptyList(), sentence, emptyList(), "inspect"), node, sentence)
    }
    fun choices(screen: ScreenState, task: GuideTask, apps: List<SpokenApp>, language: String): List<MetaChoice> {
        if (!screen.complete || SafetyPolicy.sensitive(screen) || screen.packageName !in task.allowedPackages) return emptyList()
        val appLabels = apps.map { folded(it.label) }.toSet()
        return GeneralGuidePolicy.choices(screen, language).filter { choice ->
            val node = choice.node
            !node.inLauncherFolder && (node.launcherFolder || (node.aliases + node.text).none { folded(it) in appLabels })
        }.mapIndexed { index, choice ->
            val sentence = if (choice.node.launcherFolder) step(choice.node, task.appLabel, language, true).instruction
                else UiText(language).pick("Tap the highlighted control to find ${task.appLabel}.", "Bấm nút được đánh dấu để tìm ${task.appLabel}.")
            MetaChoice("c$index", StepGoal("launcherNavigate", emptyList(), sentence, emptyList(), "inspect"), choice.node, sentence)
        }
    }
}
