package vn.guidinghand

import android.content.Intent

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
    private var launcherDirection = LauncherSwipe.initial
    private val launcherPhase get() = phase == "launcherSearch"
    val followsScreen get() = (phase == "screen" || launcherPhase) && overlay.isExpanded
    fun isLauncherTask(task: GuideTask) = launcherPhase && pending.id == "open" && pending.appPackage == task.appPackage
    fun screenUpdated(current: ScreenState) {
        if (!followsScreen) return
        if (current.fingerprint == shownFingerprint) {
            if (launcherPhase && current.packageName == service.homePackage() && !SafetyPolicy.sensitive(current) && !overlay.hasSwipeCue)
                overlay.showSwipeCue(launcherDirection)
            return
        }
        shownFingerprint = current.fingerprint
        if (launcherPhase) {
            if (SafetyPolicy.sensitive(current)) { overlay.show(null, ui.sensitive, GuideStatus.SENSITIVE); return }
            if (current.packageName != pending.appPackage && current.packageName !in pending.allowedPackages) {
                showLauncherSwipe(pending); return
            }
            if (current.complete && !SafetyPolicy.sensitive(current) && (current.packageName == pending.appPackage ||
                (current.packageName in pending.allowedPackages && LauncherTargets.hasTarget(current, pending.appLabel)))) {
                service.begin(pending)
            }
        } else { page = 0; renderScreen(true) }
    }
    fun findAppSearch(task: GuideTask) {
        launcherDirection = LauncherSwipe.initial
        showLauncherSwipe(task)
    }
    private fun showLauncherSwipe(task: GuideTask, notice: String? = null) {
        if (!safe()) return
        phase = "launcherSearch"
        pending = task
        shownFingerprint = screen()?.fingerprint
        if (screen()?.packageName != service.homePackage()) {
            overlay.showConversation(ui.pick("Return Home to look for ${task.appLabel}.", "Về màn hình chính để tìm ${task.appLabel} nhé."), listOf(
                choice("Go Home", "Về màn hình chính", true) { launch(task) }, choice("Check screen", "Đọc màn hình") { service.prepareContext { service.begin(task) } }))
            return
        }
        val instruction = when (launcherDirection) {
            LauncherSwipe.NEXT_PAGE_LEFT -> ui.pick("Swipe left to the next page.", "Vuốt trái để xem trang tiếp.")
            LauncherSwipe.PREVIOUS_PAGE_RIGHT -> ui.pick("Swipe right to the previous page.", "Vuốt phải để xem trang trước.")
            LauncherSwipe.ALL_APPS_UP -> ui.pick("Swipe up to look for apps.", "Vuốt lên để tìm ứng dụng.")
        }
        overlay.showConversation(notice ?: instruction, listOf(
            choice("Swipe for me", "Vuốt giúp tôi", true) { service.swipeLauncher(task, launcherDirection) },
            choice("Other way", "Cách khác") {
            launcherDirection = launcherDirection.next()
            showLauncherSwipe(task)
        }))
        overlay.showSwipeCue(launcherDirection)
    }
    fun launcherSwipeFinished(task: GuideTask, direction: LauncherSwipe, dispatched: Boolean) {
        if (!isLauncherTask(task) || !safe()) return
        val current = screen()
        if (current != null && current.complete && (current.packageName == task.appPackage ||
                (current.packageName in task.allowedPackages && LauncherTargets.hasTarget(current, task.appLabel)))) {
            service.begin(task); return
        }
        launcherDirection = direction
        showLauncherSwipe(task, if (dispatched) null else ui.pick(
            "Please swipe along the arrow yourself.", "Vuốt theo mũi tên giúp mình nhé."))
    }
    private fun apps(): List<SpokenApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return service.packageManager.queryIntentActivities(intent, 0).map {
            SpokenApp(it.loadLabel(service.packageManager).toString(), it.activityInfo.packageName)
        }.filter { it.packageName != service.packageName }.distinctBy { it.packageName }
            .sortedWith(compareBy<SpokenApp> { when (it.packageName) { "com.zing.zalo" -> 0; "com.instagram.android" -> 1; else -> 2 } }.thenBy { it.label.lowercase() })
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
        page = 0
        if (task != null) { choose(task); return }
        requestedTask = null; requestedPerson = null; nameAlternatives = emptyList()
        phase = "start"
        overlay.showConversation(ui.pick("What can I help with?", "Cần giúp việc gì?"), listOf(
            choice("Speak", "Nói", true) { service.openVoice() },
            choice("This screen", "Màn hình này") { showScreen() }
        ), listOf(choice("Find an app", "Tìm ứng dụng") { page = 0; showApps() }, choice("Hide", "Ẩn") { service.setBubbleVisible(false) }), home = true)
    }
    fun afterOpen(task: GuideTask) {
        pending = Tasks.messageIn(task.appPackage, task.appLabel)
        if (task.appPackage in setOf("com.zing.zalo", "com.instagram.android") || screen()?.has("Chats", "Tin nhắn", "Direct messages") == true) askRecipient()
        else { page = 0; showScreen(changed = true) }
    }
    private fun choose(task: GuideTask) {
        pending = task
        if (task.id in listOf("message", "video", "photo")) askRecipient() else launch(task)
    }
    private fun launch(task: GuideTask) {
        service.prepareContext {
            val error = service.launchTask(task)
            if (error != null) showNotice(error)
        }
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
    fun heard(text: String) = heardCandidates(listOf(text))
    fun heardCandidates(utterances: List<String>) {
        if (!safe()) return
        val heard = utterances.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(5)
        val text = heard.firstOrNull() ?: run { chooseInstead(); return }
        if (phase == "recipient") {
            nameAlternatives = heard.map { Conversation.recipient(it) ?: it }.distinct()
            recipient = nameAlternatives.first(); confirm(); return
        }
        val query = folded(text)
        val visible = screen()
        val direct = visible?.let { LocalScreenGuide.match(it, text) }
        if (direct != null && (phase == "screen" || Regex("^(tap|press|where|show|bam|tim)\\b").containsMatchIn(query))) {
            pointTo(direct.text); return
        }
        val installed = apps()
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
