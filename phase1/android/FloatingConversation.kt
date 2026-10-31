package vn.guidinghand

/** App-agnostic local conversation. No Activity navigation for questions or steps. */
class FloatingConversation(private val service: GuidingHandService, private val overlay: GuideOverlay,
                           private val prefs: FamilyPreferences, private val screen: () -> ScreenState?) {
    private val ui get() = UiText(prefs.language)
    private var phase = "start"
    private var pending = Tasks.message
    private var recipient = ""
    private var page = 0
    private var shownFingerprint: String? = null
    private var nameAlternatives = emptyList<String>()
    private var appMatches = emptyList<AppNameMatch>()
    private var requestedTask: GuideTask? = null
    private var requestedPerson: String? = null
    private var aiGoal = ""
    private var aiPackage = ""
    private var aiFeedback = ModelFeedback(false)
    private var aiRoute: GoalRoute? = null
    private var aiRecipient: String? = null
    private var preserveAiContext = false
    private var pendingAiGoal: String? = null
    private var awaitingGoalApp = false
    private var deferredTask: GuideTask? = null
    private var aiSearchQuery = ""
    private var aiClarification = ""
    private var pausedPhase: String? = null
    private val launcherProgress = LauncherProgress()
    private var manualSwipeOnly = false
    private val launcherDirection get() = launcherProgress.direction
    private val launcherPhase get() = phase == "launcherSearch"
    val launcherActive get() = launcherPhase || phase == "launcherGuide" || phase == "launcherVerifying"
    val followsScreen get() = ((phase == "screen" || launcherPhase) && overlay.isExpanded) || phase == "launcherVerifying"
    fun isLauncherTask(task: GuideTask) = launcherPhase && pending.id == "open" && pending.appPackage == task.appPackage
    fun screenUpdated(current: ScreenState) {
        if (!followsScreen) return
        if (launcherActive && current.packageName == pending.appPackage) {
            if (SafetyPolicy.sensitive(current)) { overlay.show(null, ui.sensitive, GuideStatus.SENSITIVE); return }
            if (current.complete) { service.begin(pending); return }
            launcherDestinationObserved(pending.appPackage); return
        }
        if (launcherPhase && current.complete && current.packageName == service.homePackage()) {
            val direction = launcherDirection; val exhausted = launcherProgress.exhausted
            launcherProgress.observe(current)
            if (direction != launcherDirection || exhausted != launcherProgress.exhausted) { showLauncherSwipe(pending); return }
        }
        if (current.fingerprint == shownFingerprint) {
            if (launcherPhase && current.packageName == service.homePackage() && !SafetyPolicy.sensitive(current) && !overlay.hasSwipeCue)
                overlay.showSwipeCue(launcherDirection)
            return
        }
        shownFingerprint = current.fingerprint
        if (launcherActive) {
            if (SafetyPolicy.sensitive(current)) { overlay.show(null, ui.sensitive, GuideStatus.SENSITIVE); return }
            if (current.packageName != pending.appPackage && current.packageName !in pending.allowedPackages) {
                showLauncherSwipe(pending); return
            }
            if (current.complete && !SafetyPolicy.sensitive(current) && (current.packageName == pending.appPackage ||
                (current.packageName in pending.allowedPackages && LauncherTargets.hasTarget(current, pending.appLabel)))) {
                service.begin(pending, preserveContext = true)
                return
            }
            if (current.complete && current.packageName == service.homePackage()) {
                launcherProgress.observe(current)
                if (prefs.metaGuideEnabled && (!service.prefersAppList() || LauncherNavigation.openFolder(current)))
                    service.begin(pending, preserveContext = true)
                else showLauncherSwipe(pending)
            }
        } else { page = 0; renderScreen(true) }
    }
    fun launcherDestinationObserved(packageName: String) {
        if (!launcherActive || packageName != pending.appPackage) return
        phase = "launcherVerifying"
        overlay.showConversation(ui.pick("${pending.appLabel} is open. Reading the screen…", "${pending.appLabel} đã mở. Đang đọc màn hình…"), emptyList())
    }
    fun leaveLauncherSearch() { phase = "inApp"; shownFingerprint = null; launcherProgress.reset() }
    fun trackLauncher(task: GuideTask) {
        if (!launcherActive || pending.appPackage != task.appPackage) { launcherProgress.reset(service.prefersAppList()); manualSwipeOnly = false }
        pending = task; phase = "launcherGuide"
        screen()?.takeIf { it.packageName in task.allowedPackages && LauncherTargets.icon(it, task.appLabel) != null }
            ?.let {
                launcherProgress.rememberTarget(it)
                if (BuildConfig.DEBUG) android.util.Log.d("GuidingHandTiming",
                    "launcher_target_found=true;launcher_page=${LauncherPage.read(it)?.index ?: -1}")
            }
    }
    fun launcherScrolled(atStart: Boolean, atEnd: Boolean, index: Int?, deltaX: Int) {
        if (launcherActive) launcherProgress.noteBoundary(atStart, atEnd, index, deltaX)
    }
    fun pauseSession() { pausedPhase = phase; phase = "paused" }
    fun restoreSession() { pausedPhase?.let { phase = it }; pausedPhase = null }
    fun clearSession() {
        leaveLauncherSearch(); aiGoal = ""; aiRoute = null; aiRecipient = null; pendingAiGoal = null; aiClarification = ""
        awaitingGoalApp = false; deferredTask = null; pausedPhase = null; phase = "start"
    }
    fun findAppSearch(task: GuideTask, preferApps: Boolean = false) {
        if (!launcherActive || pending.appPackage != task.appPackage) { launcherProgress.reset(service.prefersAppList()); manualSwipeOnly = false }
        if (preferApps) launcherProgress.preferAppList()
        showLauncherSwipe(task)
    }
    private fun showLauncherSwipe(task: GuideTask, notice: String? = null) {
        if (!safe()) return
        phase = "launcherSearch"
        pending = task
        val current = screen()
        if (current?.packageName == task.appPackage) {
            if (current.complete) service.begin(task) else launcherDestinationObserved(task.appPackage)
            return
        }
        if (current != null && current.complete && current.packageName in task.allowedPackages && LauncherTargets.hasTarget(current, task.appLabel)) {
            service.begin(task, preserveContext = true); return
        }
        shownFingerprint = screen()?.fingerprint
        if (screen()?.packageName != service.homePackage()) {
            overlay.showConversation(ui.pick("Return Home to look for ${task.appLabel}.", "Về màn hình chính để tìm ${task.appLabel} nhé."), listOf(
                choice("Go Home", "Về màn hình chính", true) { launch(task) }, choice("Check screen", "Đọc màn hình") { service.prepareContext { service.begin(task) } }))
            return
        }
        current?.let { launcherProgress.observe(it) }
        if (BuildConfig.DEBUG) android.util.Log.d("GuidingHandTiming",
            "launcher_direction=$launcherDirection;launcher_page=${current?.let { LauncherPage.read(it) }?.index ?: -1};launcher_exhausted=${launcherProgress.exhausted}")
        if (launcherProgress.exhausted) {
            overlay.showConversation(ui.pick("No new app page appeared. Use Search or choose another route.", "Không thấy trang ứng dụng mới. Dùng Tìm kiếm hoặc chọn cách khác nhé."), listOf(
                choice("Read this screen", "Đọc màn hình", true) { service.prepareContext { service.begin(task) } },
                choice("Open for me", "Mở giúp tôi") { service.openLauncherAppForMe() }),
                listOf(choice("Choose app", "Chọn ứng dụng") { page = 0; showApps() }))
            return
        }
        val instruction = when (launcherDirection) {
            LauncherSwipe.NEXT_PAGE_LEFT -> ui.pick("Swipe left to the next page.", "Vuốt trái để xem trang tiếp.")
            LauncherSwipe.PREVIOUS_PAGE_RIGHT -> ui.pick("Swipe right to the previous page.", "Vuốt phải để xem trang trước.")
            LauncherSwipe.ALL_APPS_UP -> ui.pick("Swipe up to look for All apps or Search.", "Vuốt lên để tìm danh sách ứng dụng hoặc ô Tìm kiếm.")
        }
        overlay.showConversation(notice ?: instruction, listOf(
            if (manualSwipeOnly) choice("I swiped", "Đã vuốt", true) { service.prepareContext { launcherSwipeFinished(task, launcherDirection, true) } }
            else choice("Swipe for me", "Vuốt giúp tôi", true) { service.swipeLauncher(task, launcherDirection) },
            choice("No more pages", "Đã tới cuối") {
            launcherProgress.blockCurrent()
            showLauncherSwipe(task)
        }), listOf(choice("All apps / Search", "Danh sách / Tìm kiếm") {
            launcherProgress.preferAppList(); showLauncherSwipe(task)
        }, choice("Open for me", "Mở giúp tôi") { service.openLauncherAppForMe() }))
        overlay.showSwipeCue(launcherDirection)
    }
    fun launcherSwipeFinished(task: GuideTask, direction: LauncherSwipe, dispatched: Boolean) {
        if (!isLauncherTask(task) || !safe()) return
        val current = screen()
        if (current != null && current.complete && (current.packageName == task.appPackage ||
                (current.packageName in task.allowedPackages && LauncherTargets.hasTarget(current, task.appLabel)))) {
            service.begin(task); return
        }
        if (!dispatched) manualSwipeOnly = true
        if (current != null && current.complete && dispatched) {
            launcherProgress.observe(current, attempted = true)
            if (direction == LauncherSwipe.ALL_APPS_UP && !launcherProgress.appListAvailable &&
                current.packageName == prefs.drawerLauncher && !LauncherNavigation.drawer(current)) prefs.drawerLauncher = ""
        }
        showLauncherSwipe(task, if (dispatched) null else ui.pick(
            "Please swipe along the arrow yourself.", "Vuốt theo mũi tên giúp mình nhé."))
    }
    private fun apps(): List<SpokenApp> = service.installedApps().sortedWith(compareBy<SpokenApp> {
        when (it.packageName) { "com.zing.zalo" -> 0; "com.instagram.android" -> 1; else -> 2 }
    }.thenBy { it.label.lowercase() })
    fun appsUpdated() {
        if (!overlay.isExpanded) return
        when (phase) { "aiApps" -> showGoalApps(); "apps" -> showApps(); "appsForRequest" -> showApps(true) }
    }
    private fun choice(en: String, vi: String, primary: Boolean = false, action: () -> Unit) = FloatingChoice(ui.pick(en, vi), action, primary)
    private fun safe(): Boolean {
        val current = screen()
        if (service.sensitive || (current != null && SafetyPolicy.sensitive(current))) {
            overlay.show(null, ui.sensitive, GuideStatus.SENSITIVE); return false
        }
        return true
    }
    fun open(task: GuideTask? = null) {
        if (!safe()) return
        if (task != null || pausedPhase == null) deferredTask = null
        if (task != null) pausedPhase = null
        if (awaitingGoalApp && aiGoal.isNotBlank()) aiRoute = apps().firstOrNull { it.packageName == aiPackage }?.let { GoalRoute(it) }
        awaitingGoalApp = false
        page = 0
        if (task != null) { choose(task); return }
        requestedTask = null; requestedPerson = null; nameAlternatives = emptyList()
        phase = "start"
        overlay.showConversation(ui.pick("What can I help with?", "Cần giúp việc gì?"), listOf(
            if (prefs.metaGuideEnabled) choice("Tell me your goal", "Nói việc muốn làm", true) { phase = "aiGoal"; service.openVoice() }
            else choice("Speak", "Nói", true) { service.openVoice() },
            if (pausedPhase != null || (prefs.metaGuideEnabled && aiGoal.isNotBlank())) choice("Continue task", "Tiếp tục việc đang làm") {
                restoreSession(); if (!service.resumeCurrentTask()) { if (aiGoal.isNotBlank()) continueGoal() else open() }
            }
            else choice("This screen", "Màn hình này") { showScreen() }
        ), listOfNotNull(if (prefs.metaGuideEnabled) choice("Everyday tasks", "Việc thường ngày") { everydayTasks() } else null,
            choice("Find an app", "Tìm ứng dụng") { page = 0; showApps() }, choice("Hide", "Ẩn") { service.setBubbleVisible(false) }), home = true)
    }
    fun afterOpen(task: GuideTask) {
        leaveLauncherSearch()
        deferredTask?.takeIf { it.appPackage == task.appPackage }?.let {
            deferredTask = null; service.begin(it); return
        }
        if (awaitingGoalApp && task.appPackage == aiPackage) {
            awaitingGoalApp = false; pendingAiGoal = null; startHere(); return
        }
        if (prefs.metaGuideEnabled) { askGoal(); return }
        pending = Tasks.messageIn(task.appPackage, task.appLabel)
        if (task.appPackage in setOf("com.zing.zalo", "com.instagram.android") || screen()?.has("Chats", "Tin nhắn", "Direct messages") == true) askRecipient()
        else { page = 0; showScreen(changed = true) }
    }
    private fun choose(task: GuideTask) {
        pending = task
        if (task.id in listOf("message", "video", "photo")) askRecipient() else launch(task)
    }
    private fun launch(task: GuideTask) {
        var destination = task
        if (task.id == "wifi") {
            val intent = android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
            val settingsPackage = intent.resolveActivity(service.packageManager)?.packageName
            if (settingsPackage != null) destination = task.copy(appPackage = settingsPackage,
                appLabel = apps().firstOrNull { it.packageName == settingsPackage }?.label ?: ui.pick("Settings", "Cài đặt"))
        }
        if (destination.id != "open" && screen()?.packageName != destination.appPackage) {
            deferredTask = destination
            launch(Tasks.openApp(destination.appLabel, destination.appPackage)); return
        }
        service.prepareContext {
            val error = service.launchTask(destination)
            if (error != null) showNotice(error)
        }
    }
    fun resumeTask(task: GuideTask) {
        if (task.id == "open" && screen()?.packageName == task.appPackage) service.begin(task)
        else if (task.id == "assist" && screen()?.packageName != service.packageName) service.begin(task, preserveContext = true)
        else launch(task)
    }
    private fun askRecipient() {
        if (!safe()) return
        phase = "recipient"
        val question = if (pending.id == "video") ui.pick("Who would you like to call?", "Muốn gọi cho ai?") else ui.who
        val choices = mutableListOf(choice("Say a name", "Nói tên", true) { service.openVoice() })
        if (prefs.contact.isNotBlank()) choices += FloatingChoice(prefs.contact.take(36), { nameAlternatives = emptyList(); recipient = prefs.contact; confirm() })
        overlay.showConversation(question, choices, listOf(
            choice("Choose on screen", "Chọn trên màn hình") { showScreen() }, choice("Back", "Quay lại") { open() }))
        service.say(question)
    }
    private fun confirm() {
        if (!safe()) return
        phase = "confirm"
        overlay.showConversation(ui.pick("Guide you to $recipient?", "Chỉ cách tìm $recipient?"), listOf(
            choice("Guide me", "Chỉ cách làm", true) { launch(pending.copy(recipient = recipient)) },
            choice("Change", "Đổi tên") { askRecipient() }
        ), nameAlternatives.filter { it != recipient }.take(2).map { name ->
            FloatingChoice(name.take(34), { recipient = name; confirm() })
        })
    }
    private fun showApps(forRequest: Boolean = false) {
        if (!safe()) return
        phase = if (forRequest) "appsForRequest" else "apps"
        if (!forRequest) { requestedTask = null; requestedPerson = null }
        val apps = apps()
        if (apps.isEmpty()) { showNotice(ui.pick("No launchable apps were found.", "Chưa tìm được ứng dụng.")); return }
        if (page >= apps.size) page = 0
        val choices = apps.drop(page).take(2).map { app ->
            FloatingChoice(app.label.take(34), {
                if (forRequest) useApp(app, requestedTask, requestedPerson)
                else launch(Tasks.openApp(app.label, app.packageName))
            }, iconPackage = app.packageName)
        }
        overlay.showConversation(ui.pick("Which app?", "Ứng dụng nào?"), choices, listOf(
            choice("More", "Xem tiếp") { page += 2; showApps(forRequest) }, choice("Speak", "Nói", true) { service.openVoice() }
        ))
    }
    fun showScreen(changed: Boolean = false) {
        service.prepareContext { renderScreen(changed) }
    }
    private fun renderScreen(changed: Boolean) {
        if (!safe()) return
        phase = "screen"
        val current = screen()
        shownFingerprint = current?.fingerprint
        if (current == null || current.packageName == service.packageName) {
            showNotice(ui.pick("Open an app, then tap Guide.", "Mở ứng dụng rồi bấm Giúp.")); return
        }
        val controls = LocalScreenGuide.candidates(current)
        if (controls.isEmpty()) {
            showNotice(ui.pick("I can't read any labeled controls here.", "Chưa đọc được nút có nhãn ở đây.")); return
        }
        if (page >= controls.size) page = 0
        overlay.showConversation(if (changed) ui.pick("Screen changed. What next?", "Màn hình đã đổi. Cần giúp gì tiếp?") else ui.pick("Which control needs help?", "Cần tìm nút nào?"),
            controls.drop(page).take(2).mapIndexed { index, node -> FloatingChoice(GuidanceText.controlLabel(node, ui, page + index + 1), { pointTo(node.text) }) },
            listOf(choice("More", "Xem tiếp") { page += 2; showScreen() }, choice("Speak", "Nói", true) { service.openVoice() }))
    }
    private fun pointTo(label: String) {
        service.prepareContext {
            val current = screen()
            if (current != null && safe()) {
                val node = LocalScreenGuide.match(current, label)
                if (node == null) { page = 0; renderScreen(false) }
                else service.begin(LocalScreenGuide.task(current, node))
            }
        }
    }
    private fun askGoal() {
        if (!safe()) return
        phase = "aiGoal"
        overlay.showConversation(ui.pick("What would you like to do? We can change apps together.", "Muốn làm gì? Mình có thể cùng chuyển ứng dụng nhé."), listOf(
            choice("Speak", "Nói", true) { service.openVoice() },
            choice("Everyday tasks", "Việc thường ngày") { everydayTasks() }))
    }
    private fun everydayTasks() {
        if (!safe()) return
        phase = "everyday"
        overlay.showConversation(ui.pick("What would you like to do?", "Muốn làm việc gì?"), listOf(
            choice("Watch YouTube", "Xem YouTube", true) { confirmGoal(ui.pick("Watch a cooking video on YouTube", "Xem video nấu ăn trên YouTube")) },
            choice("Read news", "Đọc báo") { confirmGoal(ui.pick("Read today's news", "Đọc tin tức hôm nay")) }), listOf(
            choice("Message family", "Nhắn cho cháu") { confirmGoal(ui.pick("Message my grandchild on Zalo", "Nhắn tin cho cháu trên Zalo")) },
            choice("Find an app", "Tìm ứng dụng") { page = 0; showApps() }))
    }
    private fun showGoalApps() {
        if (!safe()) return
        phase = "aiApps"
        val installed = apps().sortedBy { if (it.packageName.contains("calculator")) 0 else 1 }
        if (installed.isEmpty()) { showNotice(ui.pick("No launchable apps were found.", "Chưa tìm được ứng dụng.")); return }
        if (page >= installed.size) page = 0
        overlay.showConversation(ui.pick("Which app would you like help with?", "Muốn được giúp trong ứng dụng nào?"),
            installed.drop(page).take(2).map { app -> FloatingChoice(app.label.take(34), {
                val request = pendingAiGoal; pendingAiGoal = null
                if (request == null) launch(Tasks.openApp(app.label, app.packageName)) else {
                    aiGoal = request; aiPackage = app.packageName; aiRoute = GoalRoute(app)
                    renderGoalConfirmation()
                }
            }, iconPackage = app.packageName) }, listOf(
                choice("More", "Xem tiếp") { page += 2; showGoalApps() },
                choice("Back", "Quay lại") { open() }))
    }
    private fun confirmGoal(text: String) {
        if (!safe()) return
        if (GeneralGuidePolicy.sensitiveGoal(text)) { overlay.show(null, ui.sensitive, GuideStatus.SENSITIVE); return }
        service.releaseCurrentTask()
        deferredTask = null; awaitingGoalApp = false; pendingAiGoal = null; pausedPhase = null
        aiGoal = text.trim().take(500); aiClarification = ""; preserveAiContext = false
        aiRecipient = if (GoalRouting.usesFamily(text)) prefs.contact.takeIf { it.isNotBlank() }
            else Conversation.recipient(GoalRouting.requested(text), apps().map { it.label }, true)?.take(100)
        phase = "aiRouting"
        service.resolveGoal(aiGoal) { route ->
            aiRoute = route; aiSearchQuery = route?.query.orEmpty()
            if (route?.question != null) {
                if (route.question == "app") { pendingAiGoal = aiGoal; page = 0; showGoalApps() }
                else modelQuestion(GeneralGuidePolicy.task(screen() ?: ScreenState(service.packageName, emptyList()), aiGoal), ModelFeedback(false, route.question))
                return@resolveGoal
            }
            if (route?.app == null) { pendingAiGoal = aiGoal; page = 0; showGoalApps(); return@resolveGoal }
            aiPackage = route.app.packageName
            if (GoalRouting.usesFamily(aiGoal) && aiRecipient == null) {
                aiFeedback = ModelFeedback(false, "recipient"); phase = "aiClarify"
                overlay.showConversation(GeneralGuidePolicy.question("recipient", ui), listOf(choice("Say a name", "Nói tên", true) { service.openVoice() }))
                return@resolveGoal
            }
            renderGoalConfirmation()
        }
    }
    private fun renderGoalConfirmation() {
        phase = "aiConfirm"
        val app = apps().firstOrNull { it.packageName == aiPackage }
        val details = listOfNotNull(app?.label, aiRecipient).joinToString(" · ")
        val query = aiRoute?.query.orEmpty()
        overlay.showConversation(ui.pick("Let's work on this:\n", "Mình cùng làm việc này nhé:\n") + aiGoal.take(140) +
            (if (details.isBlank()) "" else "\n$details") + (if (query.isBlank()) "" else "\n$query"), listOf(
            choice("Guide me", "Chỉ cách làm", true) { continueGoal() },
            choice("Change", "Đổi yêu cầu") {
                overlay.showConversation(ui.pick("What would you like to change?", "Muốn đổi điều gì?"), listOf(
                    choice("Say the request", "Nói lại yêu cầu", true) { phase = "aiGoal"; service.openVoice() },
                    choice("Choose app", "Chọn ứng dụng") { pendingAiGoal = aiGoal; page = 0; showGoalApps() }))
            }))
    }
    private fun startHere() {
        leaveLauncherSearch()
        service.prepareContext {
            val current = screen()
            if (!safe()) return@prepareContext
            if (current == null || current.packageName == service.packageName) { pendingAiGoal = aiGoal; page = 0; showGoalApps(); return@prepareContext }
            aiPackage = current.packageName
            service.begin(GeneralGuidePolicy.task(current, aiGoal, service.goalPackages(), aiRecipient).copy(clarification = aiClarification), preserveAiContext)
            preserveAiContext = true
        }
    }
    private fun continueGoal() {
        if (!safe()) return
        val route = aiRoute
        aiRoute = null
        val app = route?.app ?: if (screen()?.packageName == service.packageName) apps().firstOrNull { it.packageName == aiPackage } else null
        if (app != null && screen()?.packageName != app.packageName) {
            aiPackage = app.packageName; pendingAiGoal = aiGoal; awaitingGoalApp = true
            launch(Tasks.openApp(app.label, app.packageName))
        } else startHere()
    }
    fun pauseForLauncherOpen() { phase = "launcherOpening" }
    fun recoverGoal(task: GuideTask) {
        aiGoal = task.label; aiPackage = task.appPackage; aiRecipient = task.recipient; preserveAiContext = true
        phase = "aiRecover"
        overlay.showConversation(ui.pick("I can't identify the next control yet. Your goal is saved.", "Chưa xác định được nút tiếp theo. Việc đang làm vẫn được giữ lại."), listOf(
            choice("Read again", "Đọc lại màn hình", true) { continueGoal() },
            choice("Change app", "Đổi ứng dụng") { pendingAiGoal = aiGoal; page = 0; showGoalApps() }), listOf(
            choice("Tell me more", "Nói rõ hơn") { phase = "aiClarify"; service.openVoice() },
            choice("Call family", "Gọi người thân") { service.dialFamily() }))
    }
    fun modelQuestion(task: GuideTask, feedback: ModelFeedback) {
        if (!safe()) return
        aiFeedback = feedback; aiGoal = task.label; aiPackage = task.appPackage; aiRecipient = task.recipient; preserveAiContext = true
        phase = if (feedback.review) "aiReview" else "aiClarify"
        if (feedback.appPackage != null) {
            val app = apps().firstOrNull { it.packageName == feedback.appPackage }
            if (app == null) { pendingAiGoal = aiGoal; page = 0; showGoalApps(); return }
            aiRoute = GoalRoute(app); aiPackage = app.packageName; renderGoalConfirmation(); return
        }
        if (feedback.question == "app") { pendingAiGoal = aiGoal; page = 0; showGoalApps(); return }
        if (feedback.review) overlay.showConversation(ui.pick("Does this look finished?", "Việc này đã xong chưa?"), listOf(
            choice("Yes, thank you", "Xong rồi, cảm ơn", true) { aiGoal = ""; aiRoute = null; open() },
            choice("Not yet", "Chưa xong") { phase = "aiClarify"; service.openVoice() }))
        else if (feedback.question in listOf("type", "handoff", "scroll_down", "scroll_up", "moving")) {
            overlay.showConversation(GeneralGuidePolicy.question(feedback.question, ui) +
                (if (feedback.question == "type" && aiSearchQuery.isNotBlank()) "\n$aiSearchQuery" else ""), listOf(
                if (feedback.question == "handoff") choice("I'm done", "Tôi làm xong rồi", true) { aiGoal = ""; aiRoute = null; open() }
                else choice("Continue", "Tiếp tục", true) { continueGoal() },
                choice("More help", "Giúp thêm") { service.openVoice() }))
            if (feedback.question == "scroll_down") overlay.showSwipeCue(LauncherSwipe.ALL_APPS_UP)
        } else overlay.showConversation(GeneralGuidePolicy.question(feedback.question, ui) +
                (if (feedback.question == "type" && aiSearchQuery.isNotBlank()) "\n$aiSearchQuery" else ""), listOf(
            if (feedback.question == "connection") choice("Read again", "Đọc lại màn hình", true) { continueGoal() }
            else choice("Speak", "Nói", true) { service.openVoice() },
            choice("Choose a control", "Chọn một nút") { page = 0; showScreen() }))
    }
    fun heard(text: String) = heardCandidates(listOf(text))
    fun heardCandidates(utterances: List<String>) {
        when (ConversationControl.from(utterances.firstOrNull().orEmpty().trim())) {
            ConversationControl.STOP -> { service.stopGuidance(); return }
            ConversationControl.PAUSE -> { pauseSession(); service.pauseGuidance(); return }
            else -> {}
        }
        if (!safe()) return
        val heard = utterances.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(5)
        val text = heard.firstOrNull() ?: run { chooseInstead(); return }
        when (ConversationControl.from(text)) {
            ConversationControl.STOP -> { service.stopGuidance(); return }
            ConversationControl.PAUSE -> { pauseSession(); service.pauseGuidance(); return }
            ConversationControl.REPEAT -> if (phase !in listOf("recipient", "confirm")) {
                restoreSession()
                if (!service.repeatCurrentStep()) {
                    if (!service.resumeCurrentTask()) resume()
                }
                return
            }
            ConversationControl.CONTINUE -> if (phase !in listOf("recipient", "confirm")) {
                if (phase == "aiReview" || (phase == "aiClarify" && aiFeedback.question == "handoff")) { service.stopGuidance(); return }
                restoreSession()
                if (!service.resumeCurrentTask()) {
                    if (aiGoal.isNotBlank()) continueGoal() else resume()
                }
                return
            }
            ConversationControl.REPLAN -> if (phase !in listOf("recipient", "confirm")) {
                restoreSession()
                if (GeneralGuidePolicy.sensitiveGoal(text)) { overlay.show(null, ui.sensitive, GuideStatus.SENSITIVE); return }
                if (launcherActive) {
                    if (screen()?.packageName == pending.appPackage) service.begin(pending)
                    else if (launcherPhase) { launcherProgress.blockCurrent(); showLauncherSwipe(pending) }
                    else overlay.showConversation(ui.pick("We can point again, or help open the app.", "Có thể chỉ lại biểu tượng, hoặc mở giúp ông bà."), listOf(
                        choice("Point again", "Chỉ lại", true) { if (!service.repeatCurrentStep()) service.resumeCurrentTask() },
                        choice("Open for me", "Mở giúp tôi") { service.openLauncherAppForMe() }))
                } else if (aiGoal.isNotBlank()) {
                    aiClarification = text.take(200); preserveAiContext = true; startHere()
                } else resume()
                return
            }
            ConversationControl.NEW_REQUEST -> {}
            else -> {}
        }
        if (phase == "recipient") {
            nameAlternatives = heard.map { Conversation.recipient(it) ?: it }.distinct()
            recipient = nameAlternatives.first(); confirm(); return
        }
        if (phase == "aiClarify" && prefs.metaGuideEnabled) {
            if (aiFeedback.question == "recipient") { aiRecipient = text.take(100); renderGoalConfirmation() }
            else confirmGoal((aiGoal.take(300) + "\n" + text).take(500))
            return
        }
        val query = folded(text)
        val visible = screen()
        val direct = visible?.let { LocalScreenGuide.match(it, text) }
        if (direct != null && (phase == "screen" || Regex("^(tap|press|where|show|bam|tim)\\b").containsMatchIn(query))) {
            pointTo(direct.text); return
        }
        val installed = apps()
        if (prefs.metaGuideEnabled && phase != "appsForRequest") { confirmGoal(text); return }
        val structured = Tasks.fromGoal(text)
        val matches = AppNameMatcher.suggest(heard, installed, mapOf(
            "com.zing.zalo" to listOf(prefs.zaloAlias).filter { it.isNotBlank() },
            "com.instagram.android" to listOf(prefs.instagramAlias).filter { it.isNotBlank() }))
        if (matches.isNotEmpty()) {
            val appNames = installed.map { it.label } + listOf(prefs.zaloAlias, prefs.instagramAlias).filter { it.isNotBlank() }
            val openOnly = phase != "appsForRequest" && Conversation.isAppOpenRequest(text, appNames)
            requestedTask = if (openOnly) null else structured?.takeUnless { it.id == "open" } ?: requestedTask.takeIf { phase == "appsForRequest" }
            requestedPerson = if (openOnly) null else Conversation.recipient(text, appNames, matchedAppContext = true)
                ?: requestedPerson.takeIf { phase == "appsForRequest" }
            appMatches = matches; page = 0; confirmApp(); return
        }
        if (structured != null) {
            if (structured.id == "call") { askCallType(Conversation.recipient(text)); return }
            if (structured.id in listOf("message", "video", "photo")) {
                val supported = installed.filter { app -> app.packageName == "com.zing.zalo" ||
                    (structured.id == "message" && app.packageName == "com.instagram.android") }
                val current = supported.firstOrNull { it.packageName == screen()?.packageName }
                if (current != null) { useApp(current, structured, Conversation.recipient(text)); return }
                if (supported.isEmpty()) { showNotice(ui.pick("Choose an installed app to get started.", "Chọn ứng dụng đã cài để bắt đầu nhé.")); return }
                requestedTask = structured; requestedPerson = Conversation.recipient(text)
                appMatches = supported.map { AppNameMatch(it, false) }; page = 0; confirmApp(); return
            }
            choose(structured)
            return
        }
        val current = screen()
        val target = current?.let { LocalScreenGuide.match(it, text) }
        if (target != null) { pointTo(target.text); return }
        showNotice(ui.pick("I'm not sure yet. Let's choose together.", "Mình chưa chắc ý này. Cùng chọn nhé."))
    }
    private fun confirmApp() {
        if (!safe()) return
        phase = "appConfirm"
        val options = appMatches.drop(page).take(2)
        overlay.showConversation(if (appMatches.size == 1)
            ui.pick("Did you mean ${options.first().app.label}?", "Có phải muốn dùng ${options.first().app.label}?")
            else ui.pick("Which app did you mean?", "Muốn dùng ứng dụng nào?"),
            options.map { match -> FloatingChoice(match.app.label.take(30),
                { useApp(match.app, requestedTask, requestedPerson) }, primary = appMatches.size == 1, iconPackage = match.app.packageName) },
            listOfNotNull(if (appMatches.size > 2) choice("More", "Xem tiếp") { page = if (page == 0) 2 else 0; confirmApp() } else null,
                choice("Choose another", "Chọn lại") { page = 0; showApps(forRequest = true) }))
    }
    private fun useApp(app: SpokenApp, task: GuideTask?, person: String?) {
        if (task?.id == "call") { askCallType(person, app); return }
        if (task?.id in listOf("video", "photo") && app.packageName != "com.zing.zalo") {
            phase = "appsForRequest"
            overlay.showConversation(ui.pick("These local steps are ready for Zalo. Choose another way?", "Các bước này hiện có cho Zalo. Chọn cách khác nhé?"), listOf(
                choice("Choose app", "Chọn ứng dụng", true) { page = 0; showApps(forRequest = true) },
                choice("This screen", "Màn hình này") { showScreen() }))
            return
        }
        val target = when (task?.id) {
            "message" -> Tasks.messageIn(app.packageName, app.label)
            "video", "photo" -> task.copy(appPackage = app.packageName, appLabel = app.label)
            else -> Tasks.openApp(app.label, app.packageName)
        }
        nameAlternatives = emptyList()
        if (person != null && target.id in listOf("message", "video", "photo")) {
            pending = target; recipient = person; confirm()
        } else choose(target)
    }
    private fun askCallType(person: String?, app: SpokenApp? = null) {
        phase = "callType"
        overlay.showConversation(ui.pick("How would you like to call?", "Muốn gọi theo cách nào?"), listOf(
            choice("Video call", "Gọi hình", true) {
                requestedTask = Tasks.all.first(); requestedPerson = person
                if (app != null) useApp(app, Tasks.all.first(), person)
                else {
                    requestedTask = Tasks.all.first(); requestedPerson = person
                    appMatches = apps().filter { it.packageName == "com.zing.zalo" }.map { AppNameMatch(it, false) }
                    if (appMatches.isEmpty()) showNotice(ui.pick("Local video guidance currently needs Zalo.", "Hướng dẫn gọi hình trên máy hiện cần Zalo."))
                    else { page = 0; confirmApp() }
                }
            }, choice("Call family", "Gọi người thân") { service.dialFamily() }
        ))
    }
    fun chooseInstead() {
        if (!safe()) return
        when (phase) {
            "recipient", "confirm" -> askRecipient()
            "screen" -> showScreen()
            "aiGoal", "aiConfirm", "aiClarify", "aiReview" -> askGoal()
            "aiApps" -> showGoalApps()
            "appsForRequest" -> showApps(forRequest = true)
            else -> { page = 0; showApps() }
        }
    }
    fun showNotice(message: String) {
        overlay.showConversation(message, listOf(choice("This screen", "Màn hình này", true) { page = 0; showScreen() },
            choice("Apps", "Ứng dụng") { page = 0; showApps() }))
    }
    fun resume() {
        when (phase) {
            "paused" -> open()
            "aiGoal" -> askGoal()
            "everyday" -> everydayTasks()
            "aiApps" -> showGoalApps()
            "aiConfirm" -> renderGoalConfirmation()
            "aiRecover" -> recoverGoal(GeneralGuidePolicy.task(ScreenState(aiPackage, emptyList()), aiGoal, recipient = aiRecipient))
            "aiClarify", "aiReview" -> modelQuestion(GeneralGuidePolicy.task(ScreenState(aiPackage, emptyList()), aiGoal), aiFeedback)
            "recipient" -> askRecipient()
            "confirm" -> confirm()
            "apps" -> showApps()
            "appsForRequest" -> showApps(forRequest = true)
            "screen" -> showScreen()
            "launcherSearch" -> findAppSearch(pending)
            "appConfirm" -> confirmApp()
            else -> open()
        }
    }
}
