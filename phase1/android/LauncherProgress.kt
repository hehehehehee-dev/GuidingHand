package vn.guidinghand

/** Actual accessible page indicator, not an estimate based on swipe count or icon layout. */
data class LauncherPage(val index: Int, val count: Int) {
    companion object {
        fun read(screen: ScreenState): LauncherPage? {
            if (!screen.complete || SafetyPolicy.sensitive(screen)) return null
            val pattern = Regex("^(?:page|trang) (\\d+) (?:of|tren|/) (\\d+)(?: pages| trang)?$")
            return screen.nodes.asSequence().filter { it.className.endsWith("SeekBar") && !it.password }
                .flatMap { (it.aliases + it.text).asSequence() }
                .mapNotNull { pattern.matchEntire(folded(it).replace(Regex("\\s+"), " ")) }
                .mapNotNull {
                    val page = it.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                    val count = it.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                    if (count in 1..100 && page in 1..count) LauncherPage(page - 1, count) else null
                }.distinct().toList().singleOrNull()
        }
    }
}

/** A bounded search, not an endlessly replayed direction. No elapsed time counts as success. */
class LauncherProgress {
    var direction = LauncherSwipe.initial; private set
    var exhausted = false; private set
    private val blocked = mutableSetOf<LauncherSwipe>()
    private val visited = mutableMapOf<LauncherSwipe, MutableSet<String>>()
    private var lastPage: String? = null
    private var pagesInDirection = 0
    private var scrollSettled = false
    private var pageIndex: Int? = null
    private var targetIndex: Int? = null
    private var targetPage: String? = null
    private var lastScroll: LauncherSwipe? = null
    private var returnDirection: LauncherSwipe? = null
    private var atStart = false
    private var atEnd = false
    fun reset(preferAppList: Boolean = false) {
        direction = if (preferAppList) LauncherSwipe.ALL_APPS_UP else LauncherSwipe.initial
        exhausted = false; blocked.clear(); visited.clear()
        lastPage = null; pagesInDirection = 0; scrollSettled = false
        pageIndex = null; targetIndex = null; targetPage = null; lastScroll = null; returnDirection = null
        atStart = false; atEnd = false
    }
    fun noteBoundary(atStart: Boolean, atEnd: Boolean, index: Int?, deltaX: Int) {
        val previousIndex = pageIndex
        this.atStart = atStart; this.atEnd = atEnd
        if (index != null) pageIndex = index
        if (deltaX != 0) lastScroll = if (deltaX > 0) LauncherSwipe.NEXT_PAGE_LEFT else LauncherSwipe.PREVIOUS_PAGE_RIGHT
        else if (index != null && previousIndex != null && index != previousIndex)
            lastScroll = if (index > previousIndex) LauncherSwipe.NEXT_PAGE_LEFT else LauncherSwipe.PREVIOUS_PAGE_RIGHT
        scrollSettled = deltaX != 0
    }
    private fun readPosition(screen: ScreenState) {
        if (LauncherNavigation.drawer(screen)) { pageIndex = null; atStart = false; atEnd = false; return }
        val position = LauncherPage.read(screen) ?: return
        val previous = pageIndex
        pageIndex = position.index
        atStart = position.index == 0; atEnd = position.index == position.count - 1
        if (previous != null && previous != position.index)
            lastScroll = if (position.index > previous) LauncherSwipe.NEXT_PAGE_LEFT else LauncherSwipe.PREVIOUS_PAGE_RIGHT
    }
    fun rememberTarget(screen: ScreenState) {
        readPosition(screen)
        blocked.clear(); exhausted = false; pagesInDirection = 0; visited.clear(); lastPage = null; scrollSettled = false
        targetPage = pageKey(screen); targetIndex = pageIndex; returnDirection = null
        observe(screen)
    }
    fun observe(screen: ScreenState, attempted: Boolean = false) {
        readPosition(screen)
        val page = pageKey(screen)
        val previous = lastPage
        var returningToTarget = false
        if (targetPage != null && page != targetPage) {
            val currentIndex = pageIndex; val knownIndex = targetIndex
            val towardTarget = if (currentIndex != null && knownIndex != null && currentIndex != knownIndex)
                if (currentIndex > knownIndex) LauncherSwipe.PREVIOUS_PAGE_RIGHT else LauncherSwipe.NEXT_PAGE_LEFT
            else {
                if (previous == targetPage && returnDirection == null) returnDirection = when (lastScroll) {
                    LauncherSwipe.NEXT_PAGE_LEFT -> LauncherSwipe.PREVIOUS_PAGE_RIGHT
                    LauncherSwipe.PREVIOUS_PAGE_RIGHT -> LauncherSwipe.NEXT_PAGE_LEFT
                    else -> null
                }
                returnDirection
            }
            returningToTarget = towardTarget != null && towardTarget !in blocked
            if (returningToTarget && direction != towardTarget) { direction = towardTarget!!; pagesInDirection = 0 }
        }
        val seen = visited.getOrPut(direction) { linkedSetOf() }
        if (previous != null && page != previous) {
            pagesInDirection++
            // Revisiting the route back to the known icon is expected, not a search loop.
            if (!returningToTarget && (page in seen || pagesInDirection >= 6)) blockCurrent()
        } else if (previous != null && page == previous &&
            (attempted || (scrollSettled && LauncherPage.read(screen) == null))) blockCurrent()
        visited.getOrPut(direction) { linkedSetOf() } += page
        lastPage = page; scrollSettled = false; selectAvailable()
    }
    fun blockCurrent() { blocked += direction; pagesInDirection = 0; selectAvailable() }
    val appListAvailable get() = LauncherSwipe.ALL_APPS_UP !in blocked
    fun preferAppList() {
        if (LauncherSwipe.ALL_APPS_UP !in blocked) { direction = LauncherSwipe.ALL_APPS_UP; exhausted = false; pagesInDirection = 0 }
    }
    private fun selectAvailable() {
        fun available(candidate: LauncherSwipe) = candidate !in blocked &&
            !(atStart && candidate == LauncherSwipe.PREVIOUS_PAGE_RIGHT) && !(atEnd && candidate == LauncherSwipe.NEXT_PAGE_LEFT)
        if (available(direction)) return
        val next = listOf(LauncherSwipe.NEXT_PAGE_LEFT, LauncherSwipe.PREVIOUS_PAGE_RIGHT, LauncherSwipe.ALL_APPS_UP).firstOrNull(::available)
        if (next == null) exhausted = true else { direction = next; pagesInDirection = 0 }
    }
    private fun pageKey(screen: ScreenState) = screen.packageName + ":" + LauncherPage.read(screen)?.index + ":" + screen.nodes.asSequence()
        .filter { it.clickable && it.region != null && it.text.isNotBlank() && !it.password &&
            (it.region.right - it.region.left) * (it.region.bottom - it.region.top) < .2f &&
            !it.text.matches(Regex("[\\d\\s:./]+")) }
        .map { folded(it.text) }.distinct().sorted().joinToString("|")
}
