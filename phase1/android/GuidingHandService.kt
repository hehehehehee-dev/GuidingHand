package vn.guidinghand

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.Path
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.coroutines.resume

class GuidingHandService : AccessibilityService() {
    companion object { var instance: GuidingHandService? = null; private set }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val updates = Channel<Unit>(Channel.CONFLATED)
    private lateinit var engine: GuideEngine
    private lateinit var model: MetaGuideModel
    private lateinit var overlay: GuideOverlay
    private lateinit var preferences: FamilyPreferences
    private lateinit var catalog: InstalledAppCatalog
    private lateinit var conversation: FloatingConversation
    private var lastScreenshotAt = 0L
    private var taskId: String? = null
    private var activeTask: GuideTask? = null
    private var cachedScreen: ScreenState? = null
    @Volatile private var contextRequest = 0L
    @Volatile private var displayBounds = Rect()
    private var pendingStart: Job? = null
    private var activeSwipe: Long? = null
    private var launcherScrollAt = 0L
    private var screenSuspended = false
    private var screenReceiverRegistered = false
    private var packageReceiverRegistered = false
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { refreshApps() }
    }
    private fun refreshApps() { scope.launch {
        withContext(Dispatchers.IO) { try { catalog.refresh() } catch (_: Exception) { } }
        if (::conversation.isInitialized) conversation.appsUpdated()
    } }
    fun installedApps() = catalog.snapshot()
    fun goalPackages() = installedApps().map { it.packageName }.toSet() + setOfNotNull(homePackage()) +
        setOf("com.android.intentresolver", "android", "com.google.android.documentsui", "com.android.documentsui",
            "com.google.android.providers.media.module", "com.android.providers.media.module")
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            syncDeviceVisibility(forceHide = intent?.action == Intent.ACTION_SCREEN_OFF)
        }
    }
    private fun syncDeviceVisibility(forceHide: Boolean = false): Boolean {
        if (forceHide || !DeviceVisibility.allowed(this)) {
            if (::overlay.isInitialized) overlay.suspendForLock()
            if (!screenSuspended) {
                screenSuspended = true; contextRequest++; activeSwipe = null
                pendingStart?.cancel(); cachedScreen = null; taskId = null; activeTask = null
                AssistantActivity.stopForDeviceLock()
                if (::engine.isInitialized) engine.stop()
            }
            if (::overlay.isInitialized) overlay.hideAll()
            return false
        }
        if (screenSuspended) {
            screenSuspended = false
            if (::overlay.isInitialized) overlay.resumeAfterUnlock()
        }
        return true
    }
    val sensitive: Boolean get() = ::engine.isInitialized && engine.status == GuideStatus.SENSITIVE
    private val ui get() = UiText(preferences.language)
    override fun onServiceConnected() {
        instance = this; preferences = FamilyPreferences(this)
        catalog = InstalledAppCatalog(this)
        if (preferences.metaConsentRevision < 1) preferences.metaGuideEnabled = false
        // The saved choice never restores an active task or starts observing screens on its own.
        model = MetaGuideModel(this, preferences, { readScreenTree() }, { contextRequest }, { installedApps() }, { screen -> captureForModel(screen) })
        AppLanguage.apply(this, preferences.language)
        displayBounds = getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
        overlay = GuideOverlay(this, preferences, { stopGuidance() }, {
            if (getSystemService(android.view.accessibility.AccessibilityManager::class.java).isTouchExplorationEnabled) overlay.announceReplay()
            else engine.replay()
        }, { dialFamily() }, { openAssistant() }, {
            engine.cannotGuide(ui.pick("The target is covered. Ask family to help.", "Chỗ cần bấm bị che. Nhờ người thân giúp."))
        }, { contextRequest++; activeSwipe = null }, { updates.trySend(Unit) }, { openLauncherAppForMe() }, { openVoice() })
        engine = GuideEngine(model, preferences, { step, message, status ->
            overlay.show(step, message, status)
            if (status == GuideStatus.PLANNING && model.feedback()?.question == "screen_changed") scope.launch {
                delay(150)
                if (engine.status == GuideStatus.PLANNING && preferences.bubbleVisible) updates.trySend(Unit)
            }
            if (status == GuideStatus.UNCONFIRMED && taskId == "open") scope.launch {
                yield()
                if (engine.status != GuideStatus.UNCONFIRMED || !preferences.bubbleVisible) return@launch
                val opening = activeTask ?: return@launch
                val preferApps = model.feedback()?.question == "scroll_down"
                engine.stop(); conversation.findAppSearch(opening, preferApps)
            }
            if (status == GuideStatus.UNCONFIRMED && taskId == "assist") scope.launch {
                yield()
                if (engine.status != status || !preferences.bubbleVisible) return@launch
                val goal = activeTask ?: return@launch
                engine.stop(); conversation.recoverGoal(goal)
            }
            if (status == GuideStatus.DONE && taskId == "open") scope.launch {
                yield()
                if (engine.status != GuideStatus.DONE || !preferences.bubbleVisible) return@launch
                val completed = activeTask ?: return@launch
                engine.stop(); taskId = null; activeTask = null; conversation.afterOpen(completed)
            }
            if (status == GuideStatus.NEEDS_INPUT) scope.launch {
                yield()
                if (engine.status != GuideStatus.NEEDS_INPUT || !preferences.bubbleVisible) return@launch
                engine.stop(); conversation.showScreen(changed = true)
            }
            if (status in listOf(GuideStatus.MODEL_QUESTION, GuideStatus.REVIEW)) scope.launch {
                yield()
                if (engine.status != status || !preferences.bubbleVisible) return@launch
                val goal = activeTask ?: return@launch
                val feedback = model.feedback() ?: return@launch
                engine.stop(); conversation.modelQuestion(goal, feedback)
            }
        }, {
            getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
        }, { ui })
        conversation = FloatingConversation(this, overlay, preferences) { cachedScreen }
        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_CHANGED); addDataScheme("package")
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(packageReceiver, packageFilter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(packageReceiver, packageFilter)
        packageReceiverRegistered = true
        refreshApps()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(screenReceiver, filter)
        screenReceiverRegistered = true
        if (syncDeviceVisibility()) overlay.showBubble()
        scope.launch {
            for (ignored in updates) {
                // Debounce rendering events, not the user's waiting time.
                delay(100)
                if (conversation.launcherActive) {
                    val settle = launcherScrollAt + 220 - android.os.SystemClock.uptimeMillis()
                    if (settle > 0) delay(settle)
                }
                while (updates.tryReceive().isSuccess) { /* coalesce a burst */ }
                if (!syncDeviceVisibility()) continue
                if (AssistantActivity.isVoiceVisible) continue
                if (engine.status == GuideStatus.IDLE && !conversation.followsScreen) continue
                val screen = withContext(Dispatchers.IO) { readScreenTree() } ?: continue
                if (!syncDeviceVisibility()) continue
                if (rootInActiveWindow?.packageName?.toString() != screen.packageName) { updates.trySend(Unit); continue }
                cachedScreen = screen
                rememberLauncher(screen)
                if (screen.packageName == packageName) continue
                if (engine.status == GuideStatus.IDLE) { conversation.screenUpdated(screen); continue }
                val bitmap = if (activeTask?.let { model.needsScreenshot(screen, it) } == true && screen.complete && !SafetyPolicy.sensitive(screen) && android.os.SystemClock.elapsedRealtime() - lastScreenshotAt > 500) {
                    try { capture() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
                } else null
                try {
                    val fresh = if (bitmap == null) screen else withContext(Dispatchers.IO) { readScreenTree() }
                    if (!syncDeviceVisibility()) continue
                    if (fresh?.fingerprint == screen.fingerprint) engine.onFrame(GuideFrame(screen, bitmap))
                    else updates.trySend(Unit) // Never plan against a frame superseded during capture.
                } finally { bitmap?.recycle() }
            }
        }
    }
    fun begin(task: GuideTask, preserveContext: Boolean = false) {
        if (!syncDeviceVisibility()) return
        contextRequest++; activeSwipe = null; pendingStart?.cancel()
        if (task.id == "open") conversation.trackLauncher(task) else conversation.leaveLauncherSearch()
        taskId = task.id
        activeTask = task
        if (task.id == "open" && cachedScreen?.packageName == task.appPackage && cachedScreen?.complete == true &&
            cachedScreen?.let { SafetyPolicy.sensitive(it) } == false) {
            engine.stop(); taskId = null; activeTask = null; conversation.afterOpen(task); return
        }
        engine.start(task, preserveContext)
        model.voiceReady() // Missing TTS must not block readable, touchable step guidance.
        updates.trySend(Unit)
    }
    fun launchTask(task: GuideTask): String? {
        if (!syncDeviceVisibility()) return ui.pick("Unlock your phone to continue.", "Mở khóa điện thoại để tiếp tục.")
        if (sensitive || cachedScreen?.let { SafetyPolicy.sensitive(it) } == true) {
            overlay.show(null, ui.sensitive, GuideStatus.SENSITIVE); return ui.sensitive
        }
        if (task.id == "open" && cachedScreen?.complete == true && cachedScreen?.packageName == task.appPackage &&
            rootInActiveWindow?.packageName?.toString() == task.appPackage) {
            begin(task); return null
        }
        if (task.id != "open") {
            if (cachedScreen?.packageName != task.appPackage)
                return ui.pick("Find ${task.appLabel} first.", "Tìm ${task.appLabel} trước nhé.")
            begin(task); return null
        }
        val destination = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        if (packageManager.getLaunchIntentForPackage(task.appPackage) == null)
            return ui.pick("${task.appLabel} isn't installed yet.", "Máy chưa cài ${task.appLabel}.")
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).resolveActivity(packageManager)?.packageName
        // Launcher Search may live in a separate system app (including Xiaomi's search).
        val searchPackages = setOf("com.mi.appfinder", "com.miui.personalassistant", "com.android.quicksearchbox", "com.google.android.googlequicksearchbox")
        val prepared = task.copy(allowedPackages = if (task.id == "open") setOfNotNull(launcher) + searchPackages else task.allowedPackages)
        taskId = "open"; activeTask = prepared; conversation.trackLauncher(prepared)
        preferences.bubbleVisible = true
        if (cachedScreen?.complete == true && cachedScreen?.packageName == launcher && rootInActiveWindow?.packageName?.toString() == launcher) {
            begin(prepared); return null
        }
        destination.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            // Default app finding navigates Home only. The elder opens the real icon.
            startActivity(destination)
            pendingStart?.cancel()
            pendingStart = scope.launch { delay(650); begin(prepared) }
            null
        } catch (_: Exception) { ui.pick("This app could not be opened.", "Chưa mở được ứng dụng.") }
    }
    fun openAssistant(askRecipient: Boolean = false) {
        showAssistant(if (askRecipient) activeTask?.let { Tasks.messageIn(it.appPackage, it.appLabel) } else null)
    }
    fun showAssistant(task: GuideTask? = null) {
        if (!syncDeviceVisibility()) return
        activeSwipe = null
        pendingStart?.cancel()
        if (!sensitive) engine.stop()
        preferences.bubbleVisible = true; overlay.showBubble(); prepareContext { conversation.open(task) }
    }
    /** Binder/tree work never runs in a tap callback or the Android main looper. */
    fun prepareContext(action: () -> Unit) {
        if (!syncDeviceVisibility() || !preferences.bubbleVisible) return
        val request = ++contextRequest
        overlay.showConversation(ui.thinking, emptyList())
        scope.launch {
            val fresh = withContext(Dispatchers.IO) { try { readScreenTree() } catch (_: Exception) { null } }
            if (!syncDeviceVisibility() || request != contextRequest || !preferences.bubbleVisible) return@launch
            cachedScreen = fresh; fresh?.let { rememberLauncher(it) }; action()
        }
    }
    fun openVoice() {
        if (AssistantActivity.isVoiceVisible) return
        prepareContext {
            val screen = cachedScreen
            if (sensitive || (screen != null && SafetyPolicy.sensitive(screen))) { overlay.show(null, ui.sensitive, GuideStatus.SENSITIVE) }
            else {
                activeSwipe = null; pendingStart?.cancel(); engine.suspendForInput(); overlay.clearGuide()
                startActivity(Intent(this, AssistantActivity::class.java).putExtra("voiceRequested", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK))
            }
        }
    }
    fun voiceResult(text: String) = voiceResults(listOf(text))
    fun voiceResults(utterances: List<String>) {
        val request = contextRequest
        scope.launch { delay(150); if (syncDeviceVisibility() && request == contextRequest && preferences.bubbleVisible) prepareContext { conversation.heardCandidates(utterances) } }
    }
    fun voiceFallback() {
        val request = contextRequest
        scope.launch { delay(150); if (syncDeviceVisibility() && request == contextRequest && preferences.bubbleVisible) prepareContext { conversation.chooseInstead() } }
    }
    fun voiceCancelled(message: String? = null) {
        val request = contextRequest
        scope.launch { delay(150); if (syncDeviceVisibility() && request == contextRequest && preferences.bubbleVisible) prepareContext {
            if (message != null) conversation.showNotice(message)
            else if (cachedScreen?.let { engine.reshow(it, speak = false) } != true) conversation.resume()
        } }
    }
    fun repeatCurrentStep() = cachedScreen?.let { engine.reshow(it) } == true
    fun resumeCurrentTask(): Boolean {
        val task = activeTask ?: return false
        if (engine.status == GuideStatus.DONE) return false
        conversation.restoreSession(); conversation.resumeTask(task); return true
    }
    fun pauseGuidance() {
        contextRequest++; activeSwipe = null; pendingStart?.cancel(); engine.stop()
    }
    fun stopGuidance() {
        releaseCurrentTask()
        if (::conversation.isInitialized) conversation.clearSession()
    }
    fun releaseCurrentTask() { pauseGuidance(); taskId = null; activeTask = null }
    fun say(text: String) { if (syncDeviceVisibility()) model.speak(text) }
    fun silence() { model.silence() }
    fun setBubbleVisible(visible: Boolean) {
        contextRequest++; activeSwipe = null
        pendingStart?.cancel()
        engine.stop(); preferences.bubbleVisible = visible
        if (visible) overlay.showBubble() else overlay.hideAll()
    }
    fun moveBubble() { overlay.moveBubble() }
    fun refreshLanguage() {
        val reopen = overlay.isExpanded
        contextRequest++; activeSwipe = null; pendingStart?.cancel()
        taskId = null; activeTask = null
        AssistantActivity.stopForLanguageChange()
        engine.stop(); model.voiceReady()
        if (!syncDeviceVisibility() || !preferences.bubbleVisible) return
        if (reopen) prepareContext { conversation.open() } else overlay.showBubble()
    }
    fun homePackage(): String? = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).resolveActivity(packageManager)?.packageName
    private fun rememberLauncher(screen: ScreenState) {
        if (screen.packageName == homePackage() && LauncherNavigation.drawer(screen) && preferences.drawerLauncher != screen.packageName)
            preferences.drawerLauncher = screen.packageName
    }
    fun prefersAppList() = preferences.drawerLauncher.isNotBlank() && preferences.drawerLauncher == homePackage()
        ?.takeUnless { it == "android" || it == "com.android.systemui" || it == packageName }

    /** Target-app launching is reachable only from the explicit launcher-card button. */
    fun openLauncherAppForMe() {
        if (!syncDeviceVisibility() || sensitive || taskId != "open") return
        val requested = activeTask?.takeIf { it.id == "open" } ?: return
        prepareContext {
            val before = cachedScreen ?: return@prepareContext
            if (taskId != "open" || activeTask?.appPackage != requested.appPackage) return@prepareContext
            if (!before.complete || SafetyPolicy.sensitive(before) || before.packageName !in requested.allowedPackages ||
                before.packageName == requested.appPackage) return@prepareContext
            val destination = packageManager.getLaunchIntentForPackage(requested.appPackage) ?: return@prepareContext
            pendingStart?.cancel(); val request = ++contextRequest; engine.stop(); conversation.pauseForLauncherOpen()
            overlay.showConversation(ui.pick("Opening ${requested.appLabel}…", "Đang mở ${requested.appLabel}…"), emptyList())
            try { startActivity(destination.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            catch (_: Exception) { conversation.findAppSearch(requested); return@prepareContext }
            pendingStart = scope.launch {
                repeat(15) {
                    delay(200)
                    if (request != contextRequest || !syncDeviceVisibility() || !preferences.bubbleVisible) return@launch
                    val fresh = withContext(Dispatchers.IO) { try { readScreenTree() } catch (_: Exception) { null } }
                    if (request != contextRequest || !syncDeviceVisibility()) return@launch
                    if (fresh != null && fresh.complete && !SafetyPolicy.sensitive(fresh) && fresh.packageName == requested.appPackage &&
                        before.packageName != fresh.packageName) {
                        cachedScreen = fresh; taskId = null; activeTask = null; conversation.afterOpen(requested); return@launch
                    }
                }
                conversation.findAppSearch(requested)
            }
        }
    }

    fun resolveGoal(text: String, ready: (GoalRoute?) -> Unit) {
        if (!syncDeviceVisibility() || sensitive || GeneralGuidePolicy.sensitiveGoal(text)) return
        val installed = installedApps()
        val browser = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).resolveActivity(packageManager)?.packageName
        val direct = GoalRouting.fast(text, installed, cachedScreen?.packageName ?: packageName, browser, mapOf("com.zing.zalo" to listOf(preferences.zaloAlias),
            "com.instagram.android" to listOf(preferences.instagramAlias)))
        if (direct != null) { ready(direct); return }
        val request = ++contextRequest
        overlay.showConversation(ui.thinking, emptyList())
        scope.launch {
            val result = model.routeGoal(text, installed, cachedScreen?.packageName ?: packageName)
            if (request != contextRequest || !syncDeviceVisibility() || !preferences.bubbleVisible) return@launch
            ready(result)
        }
    }
    private suspend fun captureForModel(screen: ScreenState): Bitmap? {
        if (!syncDeviceVisibility() || SafetyPolicy.sensitive(screen) || !screen.complete) return null
        if (!PlanningContext.compatible(screen, withContext(Dispatchers.IO) { readScreenTree() })) return null
        val image = try { capture() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
        if (!syncDeviceVisibility() || !PlanningContext.compatible(screen, withContext(Dispatchers.IO) { readScreenTree() })) { image?.recycle(); return null }
        return image
    }

    /** One user-requested launcher stroke. This never taps an app or enters search text. */
    fun swipeLauncher(task: GuideTask, direction: LauncherSwipe) {
        if (activeSwipe != null || !conversation.isLauncherTask(task)) return
        prepareContext {
            if (!conversation.isLauncherTask(task)) return@prepareContext
            val screen = cachedScreen
            val capable = serviceInfo.capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES != 0
            val magnified = try { magnificationController.scale != 1f } catch (_: Exception) { true }
            if (!LauncherSwipePolicy.allowed(screen, homePackage(), DeviceVisibility.allowed(this), capable) ||
                getSystemService(android.view.accessibility.AccessibilityManager::class.java).isTouchExplorationEnabled ||
                magnified) {
                conversation.launcherSwipeFinished(task, direction, false); return@prepareContext
            }
            if (screen != null && LauncherTargets.hasTarget(screen, task.appLabel)) { begin(task); return@prepareContext }
            val request = contextRequest
            activeSwipe = request
            // Remove both interactive overlay windows so the stroke reaches the launcher.
            overlay.showAutoSwipe(direction, 420L)
            val bounds = getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
            val line = direction.line
            val path = Path().apply {
                moveTo(line.startX * bounds.width(), line.startY * bounds.height())
                lineTo(line.endX * bounds.width(), line.endY * bounds.height())
            }
            fun finished(completed: Boolean) {
                if (activeSwipe != request) return
                activeSwipe = null
                if (request != contextRequest || !preferences.bubbleVisible || !syncDeviceVisibility()) return
                scope.launch {
                    delay(300) // Let the launcher finish its animation; this never marks task success.
                    val fresh = withContext(Dispatchers.IO) { try { readScreenTree() } catch (_: Exception) { null } }
                    if (request != contextRequest || !preferences.bubbleVisible || !syncDeviceVisibility() || !conversation.isLauncherTask(task)) return@launch
                    cachedScreen = fresh
                    conversation.launcherSwipeFinished(task, direction, completed)
                }
            }
            // Recheck foreground ownership after the scan and immediately before dispatch.
            if (!DeviceVisibility.allowed(this) || rootInActiveWindow?.packageName?.toString() != homePackage()) {
                finished(false); return@prepareContext
            }
            try {
                val accepted = dispatchGesture(GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, 420)).build(),
                    object : GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) { finished(true) }
                        override fun onCancelled(gestureDescription: GestureDescription?) { finished(false) }
                    }, null)
                if (!accepted) finished(false)
                else scope.launch {
                    delay(1800)
                    if (activeSwipe == request) finished(false) // Restore manual UI if the platform never calls back.
                }
            } catch (_: Exception) { finished(false) }
        }
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!::engine.isInitialized || !syncDeviceVisibility()) return
        val eventPackage = event.packageName?.toString()
        if (::conversation.isInitialized && conversation.launcherActive) {
            val opening = activeTask?.takeIf { it.id == "open" }
            if (opening != null && eventPackage == opening.appPackage && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                contextRequest++; activeSwipe = null; pendingStart?.cancel()
                engine.suspendForInput()
                conversation.launcherDestinationObserved(opening.appPackage)
                updates.trySend(Unit)
            }
            if (eventPackage == homePackage() && event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                val pager = event.className?.toString().orEmpty().lowercase()
                if (listOf("viewpager", "workspace", "screenview", "pagedview").any { it in pager }) {
                    val indexed = event.itemCount > 0 && event.fromIndex >= 0
                    val positioned = event.maxScrollX > 0 && event.scrollX >= 0
                    if (indexed || positioned || event.scrollDeltaX != 0) {
                        launcherScrollAt = android.os.SystemClock.uptimeMillis()
                        val index = if (indexed) (if (event.toIndex >= 0) event.toIndex else event.fromIndex) else null
                        conversation.launcherScrolled(
                            if (positioned) event.scrollX <= 0 else index == 0,
                            if (positioned) event.scrollX >= event.maxScrollX else index != null && index >= event.itemCount - 1,
                            index, event.scrollDeltaX)
                    }
                }
            }
        }
        if (!::engine.isInitialized || (engine.status == GuideStatus.IDLE && !conversation.followsScreen)) return
        if (event.packageName?.toString() == packageName) return
        // Safety inspection precedes any debounce so sensitive screens stop immediately.
        val hint = ScreenState(event.packageName?.toString().orEmpty(), event.text.map { ScreenNode("hint", it.toString(), null, false) })
        if (SafetyPolicy.sensitive(hint)) scope.launch { engine.onFrame(GuideFrame(hint, null)) }
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            var source = event.source
            repeat(8) {
                source?.let { node -> engine.onClick(readNode(node)); source = node.parent }
            }
        }
        updates.trySend(Unit)
    }
    private fun readNode(node: AccessibilityNodeInfo, display: Rect = displayBounds): ScreenNode {
        val rect = Rect(); node.getBoundsInScreen(rect)
        val region = if (rect.width() > 0 && rect.height() > 0 && rect.left < display.width() && rect.top < display.height() && rect.right > 0 && rect.bottom > 0)
            Region((rect.left.toFloat() / display.width()).coerceIn(0f, 1f), (rect.top.toFloat() / display.height()).coerceIn(0f, 1f),
                (rect.right.toFloat() / display.width()).coerceIn(0f, 1f), (rect.bottom.toFloat() / display.height()).coerceIn(0f, 1f)) else null
        val labels = if (node.isPassword) emptyList() else listOfNotNull(node.text?.toString(), node.contentDescription?.toString(), node.hintText?.toString()).filter { it.isNotBlank() }.distinct()
        val label = labels.firstOrNull().orEmpty()
        return ScreenNode(node.viewIdResourceName ?: "${node.className}:${rect.flattenToString()}",
            if (node.isPassword) "" else label, region, node.isClickable && node.isEnabled && node.isVisibleToUser,
            node.isSelected || node.isChecked, node.isPassword, node.className?.toString().orEmpty(), if (node.isPassword) emptyList() else labels.distinct(), node.isFocused)
    }
    private fun readScreenTree(): ScreenState? {
        if (!DeviceVisibility.allowed(this)) return null
        var root = rootInActiveWindow ?: return null
        if (root.packageName?.toString() == packageName && AssistantActivity.isVoiceVisible) {
            val ownWindow = root.windowId
            root = windows.sortedByDescending { it.layer }.firstOrNull {
                it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION && it.id != ownWindow
            }?.root ?: return null
        }
        val nodes = mutableListOf<ScreenNode>()
        val display = getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
        displayBounds = display
        val started = android.os.SystemClock.uptimeMillis()
        var complete = true
        val launcher = root.packageName?.toString() == homePackage()
        fun walk(node: AccessibilityNodeInfo, depth: Int, inFolder: Boolean = false): List<String> {
            if (depth > 96 || nodes.size >= 350 || android.os.SystemClock.uptimeMillis() - started > 700) { complete = false; return emptyList() }
            if (!node.isVisibleToUser) return emptyList()
            val raw = readNode(node, display)
            val folder = launcher && LauncherNavigation.folderIcon(raw.id, raw.className)
            val value = raw.copy(launcherFolder = folder, inLauncherFolder = inFolder)
            val slot = nodes.size; nodes += value
            val childLabels = mutableListOf<String>()
            if (!value.password) for (i in 0 until node.childCount) {
                if (!complete) break
                node.getChild(i)?.let { childLabels += walk(it, depth + 1, inFolder || folder).take(8) }
            }
            if (value.clickable && childLabels.isNotEmpty() && (value.text.isBlank() || folder)) {
                nodes[slot] = value.copy(text = value.text.ifBlank { childLabels.first() },
                    aliases = (value.aliases + childLabels).distinct().take(16))
            }
            return (value.aliases + value.text + childLabels).filter { it.isNotBlank() }.distinct().take(8)
        }
        walk(root, 0)
        android.util.Log.d("GuidingHandTiming", "tree_nodes=${nodes.size};complete=$complete;read_ms=${android.os.SystemClock.uptimeMillis() - started}")
        if (!DeviceVisibility.allowed(this)) return null
        return ScreenState(root.packageName?.toString().orEmpty(), nodes, complete)
    }
    private suspend fun capture(): Bitmap? = suspendCancellableCoroutine { continuation ->
        if (!DeviceVisibility.allowed(this)) { continuation.resume(null); return@suspendCancellableCoroutine }
        lastScreenshotAt = android.os.SystemClock.elapsedRealtime()
        val callback = object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val buffer = result.hardwareBuffer
                if (!DeviceVisibility.allowed(this@GuidingHandService)) {
                    buffer.close(); if (continuation.isActive) continuation.resume(null); return
                }
                val image = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)?.let { hardware ->
                    val copy = hardware.copy(Bitmap.Config.ARGB_8888, false); hardware.recycle(); copy
                }
                buffer.close()
                if (continuation.isActive) continuation.resume(image) else image?.recycle()
            }
            override fun onFailure(errorCode: Int) { if (continuation.isActive) continuation.resume(null) }
        }
        if (android.os.Build.VERSION.SDK_INT >= 34 && rootInActiveWindow != null)
            takeScreenshotOfWindow(rootInActiveWindow.windowId, mainExecutor, callback)
        else takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, callback)
    }
    fun dialFamily() {
        if (!syncDeviceVisibility()) return
        val number = preferences.phone.filter { it.isDigit() || it == '+' }
        if (number.isBlank()) { overlay.show(null, ui.pick("Ask family to save a phone number first.", "Nhờ gia đình lưu số điện thoại trước."), GuideStatus.UNCONFIRMED); return }
        val intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(packageManager) != null) startActivity(intent)
    }
    override fun onInterrupt() { contextRequest++; activeSwipe = null; if (::engine.isInitialized) engine.stop() }
    override fun onDestroy() {
        contextRequest++; activeSwipe = null
        if (screenReceiverRegistered) { unregisterReceiver(screenReceiver); screenReceiverRegistered = false }
        if (packageReceiverRegistered) { unregisterReceiver(packageReceiver); packageReceiverRegistered = false }
        if (::overlay.isInitialized) overlay.destroy()
        if (instance === this) instance = null
        if (::engine.isInitialized) engine.stop()
        if (::model.isInitialized) model.close()
        scope.cancel(); super.onDestroy()
    }
}
