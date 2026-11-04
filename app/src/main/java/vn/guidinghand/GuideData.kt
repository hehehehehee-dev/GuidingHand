package vn.guidinghand

import android.graphics.RectF
import java.text.Normalizer

fun folded(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").replace('đ', 'd').trim()

data class Region(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init { require(left >= 0 && top >= 0 && right <= 1 && bottom <= 1 && left < right && top < bottom) }
    fun rectF() = RectF(left, top, right, bottom)
    fun containsCenter(other: Region): Boolean {
        val x = (other.left + other.right) / 2; val y = (other.top + other.bottom) / 2
        return x in left..right && y in top..bottom
    }
}
data class ScreenNode(
    val id: String, val text: String, val region: Region?, val clickable: Boolean,
    val selected: Boolean = false, val password: Boolean = false,
    val className: String = "", val aliases: List<String> = emptyList(), val focused: Boolean = false,
    val launcherFolder: Boolean = false, val inLauncherFolder: Boolean = false, val heading: Boolean = false,
    val launchPackage: String? = null
) { val labels get() = (aliases + text).filter { it.isNotBlank() }.distinct() }
data class ScreenState(
    val packageName: String, val nodes: List<ScreenNode>, val complete: Boolean = true,
    val isLauncher: Boolean = false, val isLocked: Boolean = false,
    val windowId: Int = -1, val navigationGeneration: Long = 0, val readIssue: String? = null
) {
    val fingerprint get() = "$packageName:$windowId:$navigationGeneration:$complete:$isLauncher:$isLocked:$readIssue:" + nodes.joinToString { "${it.id}:${it.text}:${it.region}:${it.selected}:${it.password}:${it.focused}:${it.aliases}:${it.launcherFolder}:${it.inLauncherFolder}:${it.heading}" }
    /** Strict state identity: window recreation remains a real state change for verification. */
    val planningFingerprint get() = "$packageName:$windowId:$navigationGeneration:$complete:$isLauncher:$isLocked:$readIssue:" +
        nodes.filter { it.clickable || it.focused || it.password || it.heading || it.className.contains("EditText") }
            .joinToString { "${it.id}:${it.region}:${it.clickable}:${it.selected}:${it.password}:${it.focused}:${it.className}:${it.heading}" }
    /** Capture identity: same-package window recreation is harmless when actionable state is unchanged. */
    val captureFingerprint get() = "$packageName:$navigationGeneration:$complete:$isLauncher:$isLocked:$readIssue:" +
        nodes.filter { it.clickable || it.focused || it.password || it.heading || it.className.contains("EditText") }
            .joinToString { "${it.id}:${it.region}:${it.clickable}:${it.selected}:${it.password}:${it.focused}:${it.className}:${it.heading}" }
    fun hasExact(value: String) = value.isNotBlank() && nodes.any { node -> node.labels.any { folded(it) == folded(value) } }
    fun has(vararg words: String) = nodes.any { node -> node.labels.any { label -> words.any { folded(label).contains(folded(it)) } } }
}
enum class ScreenReadVerdict { SAFE, CANNOT_READ, SENSITIVE, LOCKED }

/** Local classification before any provider call. Missing data is not evidence of danger or safety. */
object ScreenReadPolicy {
    fun verdict(screen: ScreenState?): ScreenReadVerdict = when {
        screen == null -> ScreenReadVerdict.CANNOT_READ
        screen.isLocked -> ScreenReadVerdict.LOCKED
        SafetyPolicy.sensitive(screen) -> ScreenReadVerdict.SENSITIVE
        !screen.complete || screen.readIssue != null || screen.packageName.isBlank() ||
            screen.nodes.isEmpty() || screen.nodes.none { it.labels.isNotEmpty() } -> ScreenReadVerdict.CANNOT_READ
        else -> ScreenReadVerdict.SAFE
    }
}
object SafetyPolicy {
    private val browserPackages = setOf(
        "com.android.chrome", "org.mozilla.firefox", "com.microsoft.emmx",
        "com.sec.android.app.sbrowser", "com.brave.browser", "com.opera.browser"
    )
    private val credentialTerms = listOf("mat khau", "password", "passcode", "otp", "ma xac thuc", "ma xac nhan", "verification code", "pin", "cvv")
    private val paymentTerms = listOf("thanh toan", "payment", "pay now", "checkout", "card number", "so the")
    private fun patterns(values: List<String>) = values.map { Regex("(^|[^a-z0-9])${Regex.escape(it)}([^a-z0-9]|$)") }
    private val credentialPatterns = patterns(credentialTerms)
    private val paymentPatterns = patterns(paymentTerms)
    private val explicitScreenPromptPatterns = patterns(listOf(
        "enter your otp", "nhap ma xac thuc", "nhap ma xac nhan", "enter password", "nhap mat khau",
        "verification code", "payment details", "card details", "enter pin"
    ))
    private val paymentActionPatterns = patterns(listOf(
        "pay now", "confirm payment", "checkout", "thanh toan ngay", "xac nhan thanh toan"
    ))
    private fun matches(label: String, patterns: List<Regex>) = patterns.any { it.containsMatchIn(folded(label)) }
    private fun editable(node: ScreenNode) = node.focused || node.className.contains("EditText", true) ||
        node.className.contains("AutoCompleteTextView", true)
    private fun browserPageEditable(node: ScreenNode): Boolean {
        if (!editable(node)) return false
        val id = folded(node.id)
        if (listOf("url_bar", "location_bar", "omnibox").any(id::contains)) return false
        return node.region?.top?.let { it >= .12f } == true
    }

    fun sensitive(screen: ScreenState): Boolean {
        if (screen.nodes.any { it.password }) return true
        val hasEditable = screen.nodes.any(::editable)
        val exactSensitiveLabel = screen.nodes.any { node -> node.labels.any { label ->
            folded(label) in credentialTerms || folded(label) in paymentTerms
        } }
        val explicitScreenPrompt = screen.nodes.any { node -> node.labels.any { matches(it, explicitScreenPromptPatterns) } }
        val credentialContext = screen.nodes.any { node -> node.labels.any { matches(it, credentialPatterns) } }
        val paymentContext = screen.nodes.any { node -> node.labels.any { matches(it, paymentPatterns) } }
        val sensitiveControl = screen.nodes.any { node -> (editable(node) || node.clickable) && node.labels.any { label ->
            matches(label, credentialPatterns) || matches(label, paymentPatterns)
        } }
        if (screen.packageName in browserPackages) {
            // Browser chrome often contributes an always-present editable address bar. Do not
            // combine that field with words such as “password” in an article and call the whole
            // page sensitive. Real password fields still expose isPassword; other web forms must
            // have a page input or an explicit transaction action before the hard stop fires.
            val directSensitiveField = screen.nodes.any { node -> browserPageEditable(node) &&
                node.labels.any { label -> matches(label, credentialPatterns) || matches(label, paymentPatterns) } }
            val pageInput = screen.nodes.any(::browserPageEditable)
            val transactionAction = screen.nodes.any { node -> node.clickable &&
                node.labels.any { matches(it, paymentActionPatterns) } }
            return directSensitiveField || (pageInput && explicitScreenPrompt) || transactionAction
        }
        // A plain article mentioning passwords or payments is not a credential screen. A
        // matching label plus an input control is; so is an explicit sensitive action.
        return exactSensitiveLabel || explicitScreenPrompt || sensitiveControl || (hasEditable && (credentialContext || paymentContext))
    }

    /** Spoken goals and draft content have no UI-role context, so use the strict lexical gate. */
    fun sensitiveGoal(goal: String): Boolean {
        val normalized = folded(goal)
        return (credentialPatterns + paymentPatterns).any { it.containsMatchIn(normalized) }
    }
}
enum class PhotoStage { LAUNCHER, RECIPIENT, ATTACH, PHOTO, SEND, DELIVERY, RECOVER, ACTION }
/** Task data for the one planner; the legacy name preserves the strict photo evidence implementation. */
data class PhotoTask(val recipient: String, val photo: String, val id: String = "send_photo_zalo", val targetPackage: String = "",
                     val content: String = "") {
    val appPackage get() = if (id == "send_photo_zalo") "com.zing.zalo" else targetPackage
    val isPhotoTask get() = id == "send_photo_zalo"
    val valid get() = id in setOf("send_photo_zalo", "guide_app") &&
        (isPhotoTask || Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+").matches(targetPackage)) && recipient.length <= 100 && photo.length <= 200 &&
        content.length <= 500 && !SafetyPolicy.sensitiveGoal(recipient) && !SafetyPolicy.sensitiveGoal(photo) && !SafetyPolicy.sensitiveGoal(content)
    val ready get() = valid && recipient.isNotBlank() && photo.isNotBlank()
    fun acceptsRefinement(next: PhotoTask) = valid && next.valid && id == next.id && appPackage == next.appPackage &&
        (recipient.isBlank() || recipient == next.recipient) && (photo.isBlank() || photo == next.photo) &&
        (content.isBlank() || content == next.content)
}
data class FamilyContext(val recipient: String = "", val photo: String = "")
data class GuideObservation(val target: String, val matchedTap: Boolean, val screenChanged: Boolean,
    val instruction: String = "", val targetLabel: String = "", val fromPackage: String = "")
data class ClarificationTurn(val question: String, val answer: String)
data class GuideInput(
    val screen: ScreenState, val goal: String, val answers: List<String>, val task: PhotoTask?,
    val verifiedStepIds: Set<String>, val observations: List<GuideObservation>, val family: FamilyContext = FamilyContext(),
    val dialogue: List<ClarificationTurn> = emptyList(), val allowTaskUpdate: Boolean = false,
    val userUpdates: List<String> = emptyList()
)
data class SamRegion(val region: Region, val frameFingerprint: String, val source: String = "sam-3.1",
                     val rawRegion: Region? = null)
data class VoiceOutput(val audioBytes: ByteArray = byteArrayOf(), val mimeType: String = "text/plain",
                       val provider: String = "Android system TTS", val rate: Float = .85f)
data class GuidanceStep(val id: String, val stage: PhotoStage, val instructionVi: String, val target: ScreenNode,
                        val sam: SamRegion, val voice: VoiceOutput)
enum class NavigationCue { NONE, HOME_SWIPE_UP }
sealed interface GuideDecision {
    data object StateChanged : GuideDecision
    data class Step(val step: GuidanceStep, val task: PhotoTask? = null) : GuideDecision
    data class Clarify(val questionVi: String, val options: List<String> = emptyList(),
                       val speechPurpose: SpeechPurpose = SpeechPurpose.GENERAL) : GuideDecision
    data class Unsupported(val messageVi: String, val options: List<String> = emptyList()) : GuideDecision
    data class Unavailable(val component: String, val messageVi: String) : GuideDecision
    /** Server independently detected sensitive content; this must latch the same native hard stop. */
    data class Sensitive(val messageVi: String) : GuideDecision
    data class Await(val messageVi: String, val reasonCode: String = "model_ask") : GuideDecision
    /** A real user navigation/gesture instruction; no invented tappable target or ring. */
    data class Navigate(val messageVi: String, val task: PhotoTask? = null,
                        val navigationCue: NavigationCue = NavigationCue.NONE) : GuideDecision
    data class DoneProposal(val messageVi: String) : GuideDecision
}
interface GuideModel {
    val developmentStandIn: Boolean get() = false
    suspend fun next(input: GuideInput): GuideDecision
    suspend fun repeat(step: GuidanceStep)
    suspend fun speakMessage(text: String, screen: ScreenState) {}
    fun cancel()
    /** Forget a model step that failed native validation; a retry must not replay it. */
    fun rejectStep(decision: GuideDecision.Step) {}
    /** Clear RAM-only task/model memory on Stop, a new goal, lock, or a sensitive screen. */
    fun clearSessionMemory() {}
    fun close() = cancel()
}
enum class GuideStatus { IDLE, READING, PLANNING, CLARIFYING, CONFIRMING_INPUT, CONFIRMING_COPY, WAITING_TAP, WAITING_NAVIGATION, VERIFYING, RECOVERING, SAFETY_PAUSED, ASKING, UNAVAILABLE, UNSUPPORTED, DONE }
data class GuideViewState(val status: GuideStatus, val instructionVi: String, val step: GuidanceStep? = null,
    val options: List<String> = emptyList(), val reasonCode: String? = null,
    val speechPurpose: SpeechPurpose = SpeechPurpose.GENERAL, val draftText: String? = null,
    val navigationCue: NavigationCue = NavigationCue.NONE)
data class GuideTransition(val sequence: Long, val from: GuideStatus, val to: GuideStatus,
                           val reasonCode: String? = null, val stage: PhotoStage? = null,
                           val taskKind: String? = null)

/** Native evidence only: planner success claims do not complete a task. */
object PhotoVerification {
    private val composer = listOf("message", "type a message", "nhap tin nhan", "tin nhan", "message…", "nhan tin")
    fun hasComposer(screen: ScreenState) = screen.nodes.any { it.className.contains("EditText") &&
        it.labels.any { label -> folded(label) in composer } }
    fun composerContains(screen: ScreenState, task: PhotoTask) = task.content.isNotBlank() && screen.nodes.any { node ->
        node.className.contains("EditText") && node.labels.any { folded(it) == folded(task.content) }
    }
    private val headerId = Regex("(^|[_-])(title|header|toolbar|contact_name|chat_name|user_name|conversation_name)($|[_-])")
    private fun recipientHeader(node: ScreenNode, task: PhotoTask): Boolean {
        val bounds = node.region ?: return false
        if (bounds.top >= .3f || node.password || node.className.contains("EditText") || node.className.contains("AutoCompleteTextView")) return false
        if (node.labels.none { folded(it) == folded(task.recipient) }) return false
        val realId = node.id.substringBefore('@')
        return node.heading || (realId.contains(":id/") && headerId.containsMatchIn(realId.substringAfterLast('/').lowercase()))
    }
    fun recipientTitle(screen: ScreenState, task: PhotoTask) = screen.packageName == task.appPackage &&
        screen.nodes.filter { recipientHeader(it, task) }.singleOrNull() != null
    fun conversation(screen: ScreenState, task: PhotoTask) = hasComposer(screen) && recipientTitle(screen, task)
    fun gallery(screen: ScreenState) = screen.has("Thư viện", "Gần đây", "Gallery", "Recent", "Chọn ảnh", "Select photos") ||
        (screen.packageName in setOf("com.google.android.providers.media.module", "com.android.providers.media.module", "com.google.android.documentsui", "com.android.documentsui") &&
            screen.has("Photos", "Ảnh", "Images"))
    fun selectedPhoto(screen: ScreenState, task: PhotoTask) = screen.nodes.any { node -> node.selected && node.labels.any { folded(it) == folded(task.photo) } }
    /** A visible selected gallery tile is media evidence; a count or an old chat image alone is not. */
    fun hasBottomMediaPreview(screen: ScreenState) = screen.nodes.any { node ->
        val box = node.region
        node.className.endsWith("ImageView") && box != null &&
            box.top >= .8f && box.right - box.left >= .035f && box.bottom - box.top >= .035f
    }
    fun selectedMedia(screen: ScreenState): Boolean {
        val images = screen.nodes.filter { node ->
            val box = node.region
            node.className.endsWith("ImageView") && box != null && box.top in .2f.. .85f &&
                box.right - box.left >= .12f && box.bottom - box.top >= .08f
        }
        val bottomPreview = hasBottomMediaPreview(screen)
        // Instagram exposes a checked gallery tile as CheckBox, not as a selected ImageView.
        // Its large bounds and separate bottom preview distinguish it from a small toggle.
        if (bottomPreview && screen.nodes.any { node ->
            val box = node.region
            node.selected && node.className.endsWith("CheckBox") && box != null &&
                box.top in .2f.. .85f && box.right - box.left >= .12f && box.bottom - box.top >= .08f
        }) return true
        return screen.nodes.any { node ->
            val box = node.region ?: return@any false
            val numberedBadge = node.labels.any { Regex("[1-9][0-9]?").matches(it.trim()) }
            if (!node.selected && !(numberedBadge && bottomPreview)) return@any false
            images.any { image ->
                val imageBox = image.region ?: return@any false
                image === node || imageBox.containsCenter(box)
            }
        }
    }
    /** A thumbnail is often labelled by date, not by the person's spoken description.
     * Bind the actual elder-tapped thumbnail to a newly selected instance, never to a generic count. */
    fun selectedTarget(before: ScreenState, after: ScreenState, target: ScreenNode): Boolean {
        if (target.selected || target.region == null || before.nodes.none { it == target } ||
            before.packageName != after.packageName || before.windowId != after.windowId) return false
        return after.nodes.any { node ->
            val box = node.region
            node.selected && box != null && target.region.containsCenter(box) && box.containsCenter(target.region) &&
                node.className == target.className && node.id.substringBefore('@') == target.id.substringBefore('@') &&
                node.labels.map(::folded).intersect(target.labels.map(::folded).toSet()).isNotEmpty()
        }
    }
    fun hasSelectedTarget(screen: ScreenState, target: ScreenNode?) = target != null && screen.nodes.any { node ->
        node.selected && node.region != null && target.region?.containsCenter(node.region) == true &&
            node.id.substringBefore('@') == target.id.substringBefore('@') && node.className == target.className &&
            node.labels.map(::folded).intersect(target.labels.map(::folded).toSet()).isNotEmpty()
    }
    fun photoPreview(screen: ScreenState, task: PhotoTask) = screen.nodes.any { node -> node.className.endsWith("ImageView") && node.labels.any { folded(it) == folded(task.photo) } }
    private fun outgoing(node: ScreenNode) = node.className.endsWith("ImageView") && node.labels.any {
        val value = folded(it)
        value.contains("anh da gui") || value.contains("sent photo") || value.contains("outgoing photo") || value.contains("anh ban da gui")
    }
    private fun delivery(node: ScreenNode) = node.labels.any { folded(it) in setOf("da gui", "da nhan", "sent", "delivered") }
    private fun newEvidence(before: ScreenState, after: ScreenState, predicate: (ScreenNode) -> Boolean): Boolean {
        // Tree positions/resource IDs are not message identity: old bubbles can move or be recycled.
        val old = before.nodes.filter(predicate).groupingBy { it.labels.map(::folded).distinct().sorted() }.eachCount()
        val current = after.nodes.filter(predicate).groupingBy { it.labels.map(::folded).distinct().sorted() }.eachCount()
        return current.any { (key, count) -> count > (old[key] ?: 0) }
    }
    fun delivered(before: ScreenState, after: ScreenState, task: PhotoTask) = before.complete && after.complete &&
        !before.isLocked && !after.isLocked && !SafetyPolicy.sensitive(before) && !SafetyPolicy.sensitive(after) &&
        before.packageName == task.appPackage && conversation(after, task) && before.fingerprint != after.fingerprint && !gallery(after) &&
        newEvidence(before, after, ::outgoing) && newEvidence(before, after, ::delivery)
}
