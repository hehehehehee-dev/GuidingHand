package vn.guidinghand

data class GoalRoute(val app: SpokenApp?, val action: String = "launch", val query: String = "", val question: String? = null)

object GoalRouting {
    private fun intentPart(goal: String): String {
        val contrast = Regex("(?i)\\b(?:but|instead|nhưng|nhung)\\b").findAll(goal).lastOrNull()
        return if (contrast == null) goal else goal.substring(contrast.range.last + 1)
    }
    private fun negated(goal: String) = Regex("(^|[^a-z0-9])(not|don t|don't|do not|khong)([^a-z0-9]|$)").containsMatchIn(folded(goal))
    fun requested(goal: String) = intentPart(goal).trim()
    fun fast(goal: String, apps: List<SpokenApp>, current: String, browser: String?, aliases: Map<String, List<String>> = emptyMap()): GoalRoute? {
        val intent = intentPart(goal)
        if (negated(intent)) return null
        val text = folded(intent)
        explicit(intent, apps, aliases)?.let {
            return if (it.app?.packageName == browser && Regex("(news|newspaper|doc bao|tin tuc)").containsMatchIn(text))
                GoalRoute(it.app, "search_web", intent.take(180)) else it
        }
        if (Regex("(^|[^a-z0-9])(news|newspaper|doc bao|tin tuc)([^a-z0-9]|$)").containsMatchIn(text))
            apps.firstOrNull { it.packageName == browser }?.let { return GoalRoute(it, "search_web", intent.take(180)) }
        if (Regex("(^|[^a-z0-9])(watch|videos|xem video|xem phim)([^a-z0-9]|$)").containsMatchIn(text)) {
            apps.firstOrNull { it.packageName == "com.google.android.youtube" }?.let { app ->
                val query = Regex("(?i)(?:watch|play|xem)\\s+(.+)").find(intent)?.groupValues?.get(1)?.trim().orEmpty()
                return GoalRoute(app, if (query.isBlank()) "launch" else "search_app", query.take(180))
            }
        }
        if (Regex("(^|[^a-z0-9])(message|text|nhan tin|nhan cho)([^a-z0-9]|$)").containsMatchIn(text)) {
            val messaging = setOf("com.zing.zalo", "com.instagram.android", "com.facebook.orca", "com.whatsapp", "org.telegram.messenger")
            return apps.firstOrNull { it.packageName == current && it.packageName in messaging }?.let { GoalRoute(it) }
                ?: GoalRoute(null, question = "app")
        }
        if (Regex("(^|[^a-z0-9])(calculate|tinh)([^a-z0-9]|$)").containsMatchIn(text))
            apps.firstOrNull { it.packageName == current && it.packageName.contains("calculator") }?.let { return GoalRoute(it) }
        return null
    }
    fun explicit(goal: String, apps: List<SpokenApp>, aliases: Map<String, List<String>> = emptyMap()): GoalRoute? {
        val intent = intentPart(goal)
        if (negated(intent)) return null
        val query = folded(intent)
        val found = apps.filter { app ->
            (listOf(app.label) + aliases[app.packageName].orEmpty()).any { label ->
                label.length >= 2 && Regex("(^|[^a-z0-9])${Regex.escape(folded(label))}([^a-z0-9]|$)").containsMatchIn(query)
            }
        }.distinctBy { it.packageName }
        return found.singleOrNull()?.let { app ->
            val search = if (app.packageName == "com.google.android.youtube") Regex(
                "(?i)(?:watch|search(?: for)?|find|play|xem|tìm(?: kiếm)?|tim(?: kiem)?)\\s+(.+?)\\s+(?:on|in|trên|tren)\\s+(?:youtube|you tube)"
            ).find(intent)?.groupValues?.get(1)?.trim()?.take(180) else null
            GoalRoute(app, if (search.isNullOrBlank()) "launch" else "search_app", search.orEmpty())
        }
    }
    fun resolve(apps: List<SpokenApp>, id: String?): SpokenApp? = id?.removePrefix("a")?.toIntOrNull()
        ?.takeIf { id == "a$it" }?.let { apps.getOrNull(it) }
    fun usesFamily(goal: String) = Regex("(^|[^a-z0-9])(grandson|granddaughter|grandchild|chau)([^a-z0-9]|$)")
        .containsMatchIn(folded(intentPart(goal))) && Regex("(message|text|call|nhan|goi|gui)").containsMatchIn(folded(intentPart(goal)))
}
