package vn.guidinghand

import android.content.Context

/** PHASE 1 DEV STAND-IN ONLY. Selected once; never called as a Spark fast path/fallback. */
class MockModel(private val freshScreen: suspend () -> ScreenState?, private val speech: SpeechOutput) : GuideModel {
    constructor(context: Context, freshScreen: suspend () -> ScreenState?) : this(freshScreen, SystemSpeechOutput(context))
    override val developmentStandIn = true
    override suspend fun next(input: GuideInput): GuideDecision {
        val screen = input.screen
        if (!screen.complete || screen.isLocked || SafetyPolicy.sensitive(screen)) return GuideDecision.Await("Chưa đọc rõ màn hình. Đã dừng chỉ dẫn.")
        val task = input.task ?: return GuideDecision.Clarify("Đây là bản phát triển Phase 1. Chọn tác vụ gửi ảnh đã thiết lập để kiểm tra.")
        if (!task.ready) return GuideDecision.Await("Cần thiết lập đúng tên người nhận và nhãn ảnh trước.")
        fun find(vararg labels: String): ScreenNode? = screen.nodes.filter { it.clickable && it.region != null && !it.password &&
            !it.inLauncherFolder && !it.launcherFolder && it.labels.any { label -> labels.any { folded(label) == folded(it) } } }
            .distinctBy { it.region }.singleOrNull()
        fun step(node: ScreenNode, stage: PhotoStage, text: String) = GuideDecision.Step(GuidanceStep(stage.name.lowercase(), stage, text,
            node, SamRegion(node.region!!, screen.planningFingerprint, "phase1-mock"), VoiceOutput()), task)
        if (screen.isLauncher) find("Zalo")?.let { return step(it, PhotoStage.LAUNCHER, "Bấm biểu tượng Zalo được đánh dấu.") }
        if (screen.packageName == task.appPackage) {
            if (PhotoVerification.conversation(screen, task)) {
                if (PhotoVerification.selectedPhoto(screen, task) || PhotoVerification.photoPreview(screen, task))
                    find("Send", "Gửi", "Gửi ảnh", "Send photo")?.let { return step(it, PhotoStage.SEND, "Kiểm tra ảnh và người nhận rồi tự bấm Gửi.") }
                find("Photos", "Ảnh", "Hình ảnh", "Gửi ảnh", "Photo")?.let { return step(it, PhotoStage.ATTACH, "Bấm nút Ảnh được đánh dấu.") }
            } else if (!PhotoVerification.hasComposer(screen)) find(task.recipient)?.let {
                return step(it, PhotoStage.RECIPIENT, "Bấm tên ${task.recipient} được đánh dấu.")
            }
        }
        if (PhotoVerification.gallery(screen)) {
            if (PhotoVerification.selectedPhoto(screen, task)) {
                if (screen.packageName == task.appPackage) find("Gửi", "Send", "Gửi ảnh")?.let {
                    return step(it, PhotoStage.SEND, "Kiểm tra ảnh đã chọn rồi tự bấm Gửi.")
                }
                find("Add", "Thêm", "Done", "Xong")?.let { return step(it, PhotoStage.RECOVER, "Bấm nút được đánh dấu để trở lại Zalo với ảnh đã chọn.") }
            } else find(task.photo)?.let { return step(it, PhotoStage.PHOTO, "Bấm ảnh ${task.photo} được đánh dấu.") }
        }
        find("Search", "Tìm kiếm", "Search apps")?.let {
            val query = if (screen.isLauncher) "Zalo" else task.recipient
            return step(it, if (screen.isLauncher) PhotoStage.LAUNCHER else PhotoStage.RECOVER,
                if (it.focused) "Tự gõ $query vào ô tìm kiếm." else "Bấm ô Tìm kiếm được đánh dấu.")
        }
        find("Back", "Quay lại", "Navigate up")?.let { return step(it, PhotoStage.RECOVER, "Bấm Quay lại được đánh dấu để tìm đúng màn hình.") }
        return GuideDecision.Await("Bản phát triển chưa xác minh được nút cần bấm. Mình dừng để hỏi, không đoán.")
    }
    override suspend fun repeat(step: GuidanceStep) = speech.speak(step.instructionVi) {
        freshScreen()?.let { it.complete && !it.isLocked && !SafetyPolicy.sensitive(it) && it.planningFingerprint == step.sam.frameFingerprint } == true
    }
    override suspend fun speakMessage(text: String, screen: ScreenState) = speech.speak(text) {
        freshScreen()?.let { it.complete && !it.isLocked && !SafetyPolicy.sensitive(it) && it.planningFingerprint == screen.planningFingerprint } == true
    }
    override fun cancel() = speech.stop()
    override fun close() = speech.close()
}
