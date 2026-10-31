package vn.guidinghand

data class SpokenApp(val label: String, val packageName: String)
data class AppNameMatch(val app: SpokenApp, val exact: Boolean)

/** Suggestions only. Aliases and spelling similarity never authorize opening an app. */
object AppNameMatcher {
    private val aliases = mapOf(
        "com.zing.zalo" to listOf("gia lo", "za lo", "ja lo", "da lo"),
        "com.instagram.android" to listOf("in sta", "in sta gram", "insta gram"),
        "com.facebook.katana" to listOf("phay buc", "face book", "fay book"),
        "com.google.android.youtube" to listOf("du tub", "you tube"),
        "com.facebook.orca" to listOf("met sen gio", "mess en ger")
    )
    private val taskWords = setOf("photo", "picture", "pictures", "message", "messages", "call", "video", "text", "anh", "hinh", "tin nhan", "goi")
    private val actionWords = Regex("\\b(?:message|text|call|send|nhan|goi|gui)\\b")
    private val appContext = Regex("\\b(?:on|in|using|with|bang|tren|qua)\\s+(.+)$")
    private val prefixes = Regex("^(?:please|can you|could you|i want to|i would like to|i d like to|toi muon|minh muon|cho toi|giup toi|hay|find|open|launch|start|show me|where is|tim giup|tim|mo|vao|the app|the application|app called|app|application|ung dung)\\s+")
    private val suffixes = Regex("\\s+(?:please|for me|giup toi|giup minh|nhe|voi)$")

    private fun normalize(value: String) = folded(value).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().replace(Regex("\\s+"), " ")

    /** Only an app-name phrase is fuzzy matched, never a recipient or an entire arbitrary goal. */
    private fun appPhrases(utterance: String): List<String> {
        var text = normalize(utterance)
        if (actionWords.containsMatchIn(text)) {
            text = appContext.find(text)?.groupValues?.get(1) ?: return emptyList()
        }
        repeat(6) { text = text.replace(prefixes, "").replace(suffixes, "").trim() }
        return text.split(Regex("\\s+(?:or|hoac|hay)\\s+")).filter { it.isNotBlank() }
    }

    /** customAliases is keyed by installed package name; aliases never manufacture an app. */
    fun suggest(utterances: List<String>, installed: List<SpokenApp>,
                customAliases: Map<String, List<String>> = emptyMap()): List<AppNameMatch> {
        data class Ranked(val app: SpokenApp, val score: Int, val canonicalFirst: Boolean)
        val found = mutableMapOf<String, Ranked>()
        utterances.take(5).forEachIndexed { hypothesis, utterance ->
            val raw = normalize(utterance)
            // App names can themselves start with verbs, e.g. Open Camera / Find My Device.
            installed.distinctBy { it.packageName }.forEach { app ->
                val canonical = normalize(app.label)
                val names = (aliases[app.packageName].orEmpty() + customAliases[app.packageName].orEmpty()).map(::normalize)
                val kind = when {
                    raw.isNotBlank() && raw == canonical -> 0
                    raw.isNotBlank() && raw in names -> 1
                    else -> null
                }
                if (kind != null) {
                    val candidate = Ranked(app, hypothesis * 10 + kind, hypothesis == 0 && kind == 0)
                    val previous = found[app.packageName]
                    if (previous == null || candidate.score < previous.score) found[app.packageName] = candidate
                }
            }
            val phrases = appPhrases(utterance)
            phrases.forEach { phrase ->
                installed.distinctBy { it.packageName }.forEach appLoop@{ app ->
                    val canonical = normalize(app.label)
                    if (canonical.isBlank()) return@appLoop
                    val names = (aliases[app.packageName].orEmpty() + customAliases[app.packageName].orEmpty())
                        .map(::normalize).filter { it.isNotBlank() }
                    val kind = when {
                        phrase == canonical -> 0
                        phrase in names -> 1
                        fuzzyEligible(phrase) && (names + canonical).any { similar(phrase, it) } -> 2
                        else -> return@appLoop
                    }
                    val candidate = Ranked(app, hypothesis * 10 + kind,
                        hypothesis == 0 && kind == 0 && phrases.size == 1)
                    val previous = found[app.packageName]
                    if (previous == null || candidate.score < previous.score) found[app.packageName] = candidate
                }
            }
        }
        return found.values.sortedWith(compareBy<Ranked> { it.score }.thenBy { it.app.label })
            .take(3).map { AppNameMatch(it.app, it.canonicalFirst) }
    }

    private fun fuzzyEligible(phrase: String) = phrase !in taskWords && phrase.replace(" ", "").length >= 4 &&
        phrase.split(" ").size <= 3 && !actionWords.containsMatchIn(phrase)

    private fun similar(left: String, right: String): Boolean {
        val a = left.replace(" ", ""); val b = right.replace(" ", "")
        if (b.length < 4) return false
        val maxLength = maxOf(a.length, b.length)
        val limit = if (minOf(a.length, b.length) >= 8) 2 else 1
        if (kotlin.math.abs(a.length - b.length) > limit) return false
        val distance = distance(a, b)
        return distance <= limit && distance.toDouble() / maxLength <= .25
    }

    private fun distance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        a.forEachIndexed { i, char ->
            val current = IntArray(b.length + 1); current[0] = i + 1
            b.forEachIndexed { j, other -> current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + if (char == other) 0 else 1) }
            previous = current
        }
        return previous[b.length]
    }
}
