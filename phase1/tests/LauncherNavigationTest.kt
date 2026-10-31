package vn.guidinghand

import org.junit.Assert.*
import org.junit.Test

class LauncherNavigationTest {
    private val area = Region(.1f, .2f, .25f, .3f)
    private val folder = ScreenNode("folder", "Family", area, true, aliases = listOf("Family", "Zalo"), launcherFolder = true)
    private fun screen(vararg nodes: ScreenNode) = ScreenState("test.launcher", nodes.toList())
    private val task = Tasks.openApp("Zalo", "com.zing.zalo").copy(allowedPackages = setOf("test.launcher"))

    @Test fun closedFolderPreviewIsNotAnAppIcon() {
        val preview = ScreenNode("preview", "Zalo", Region(.12f, .21f, .16f, .25f), true, inLauncherFolder = true)
        val current = screen(folder, preview)
        assertNull(LauncherTargets.icon(current, "Zalo"))
        assertEquals(folder, LauncherNavigation.folder(current, "Zalo"))
        assertTrue(LauncherTargets.hasTarget(current, "Zalo"))
    }
    @Test fun realIconCanBePointedAfterTheFolderOpens() {
        val icon = ScreenNode("actual", "Zalo", area, true)
        assertEquals(icon, LauncherTargets.icon(screen(icon), "Zalo"))
        assertNull(LauncherNavigation.folder(screen(icon), "Zalo"))
    }
    @Test fun workspaceCannotMasqueradeAsAnIconByInheritingFolderLabels() {
        val workspace = ScreenNode("workspace", "Zalo", Region(0f, 0f, 1f, .9f), true)
        assertNull(LauncherTargets.icon(screen(workspace, folder), "Zalo"))
    }
    @Test fun unknownFolderContentsAreNotInvented() {
        assertNull(LauncherNavigation.folder(screen(folder.copy(aliases = listOf("Family"))), "Zalo"))
        assertNull(LauncherNavigation.folder(screen(folder, folder.copy(region = Region(.6f, .2f, .75f, .3f))), "Zalo"))
    }
    @Test fun drawerDetectionUsesStructureRatherThanAnAppNamedApps() {
        assertFalse(LauncherNavigation.drawer(screen(ScreenNode("icon", "Apps", area, true))))
        val drawer = ScreenNode("test.launcher:id/all_apps_category_container", "", null, false)
        assertTrue(LauncherNavigation.drawer(screen(drawer)))
        assertFalse(LauncherNavigation.drawer(screen(drawer).copy(complete = false)))
    }
    @Test fun folderAndDrawerInstructionsAreLocalizedAndCannotCompleteAppOpening() {
        val vi = LauncherNavigation.step(folder, "Zalo", "vi", true)
        assertEquals("inspect", vi.goal.completion)
        assertEquals("Bấm thư mục này để tìm Zalo.", vi.instruction)
        assertFalse(Verification.confirms(vi, screen(folder), screen(ScreenNode("changed", "Zalo", area, true))))
        assertEquals("Tap here to see all apps.", LauncherNavigation.step(folder, "Zalo", "en", false).instruction)
    }
    @Test fun aiCannotChooseAnotherInstalledAppOrTheFolderPreview() {
        val instagram = ScreenNode("ig", "Instagram", Region(.6f, .2f, .75f, .3f), true)
        val preview = ScreenNode("preview", "Zalo", Region(.12f, .21f, .16f, .25f), true, inLauncherFolder = true)
        val choices = LauncherNavigation.choices(screen(folder, instagram, preview), task,
            listOf(SpokenApp("Instagram", "com.instagram.android"), SpokenApp("Zalo", "com.zing.zalo")), "en")
        assertEquals(listOf(folder), choices.map { it.node })
        assertTrue(choices.all { it.goal.completion == "inspect" })
    }
    @Test fun sensitiveIncompleteAndForeignScreensNeverOfferNavigation() {
        for (current in listOf(screen(folder).copy(complete = false), screen(folder).copy(packageName = "other.app"),
            screen(folder, ScreenNode("secret", "", null, false, password = true)))) {
            assertTrue(LauncherNavigation.choices(current, task, emptyList(), "vi").isEmpty())
        }
    }
}
