package io.github.hphbryan.liveisland.core

fun snapshot(
    packageName: String = "com.example.delivery",
    title: String? = "Courier is on the way",
    text: String? = "Arriving in 12 min",
    bigText: String? = null,
    channelId: String? = "orders",
    category: String? = null,
    isOngoing: Boolean = true,
    isGroupSummary: Boolean = false,
    isPromoted: Boolean = false,
    isSamsungLiveNotification: Boolean = false,
    isMedia: Boolean = false,
    progress: Int = 0,
    progressMax: Int = 0,
    progressIndeterminate: Boolean = false,
    showsChronometer: Boolean = false,
    chronometerCountsDown: Boolean = false,
    whenMillis: Long = 1_000L,
    key: String = "0|$packageName|1|null|10001",
    sender: String? = null,
    conversationTitle: String? = null,
    isConversation: Boolean = false,
    isHiddenBySystem: Boolean = false,
    interruptsNow: Boolean = true,
    lockScreen: LockScreen = LockScreen.DEFAULT,
    isClassifiedAsBulk: Boolean = false,
    channelName: String? = null,
) = NotificationSnapshot(
    key = key,
    packageName = packageName,
    channelId = channelId,
    category = category,
    title = title,
    text = text,
    bigText = bigText,
    isOngoing = isOngoing,
    isGroupSummary = isGroupSummary,
    isPromoted = isPromoted,
    isSamsungLiveNotification = isSamsungLiveNotification,
    isMedia = isMedia,
    progress = progress,
    progressMax = progressMax,
    progressIndeterminate = progressIndeterminate,
    showsChronometer = showsChronometer,
    chronometerCountsDown = chronometerCountsDown,
    whenMillis = whenMillis,
    sender = sender,
    conversationTitle = conversationTitle,
    isConversation = isConversation,
    isHiddenBySystem = isHiddenBySystem,
    interruptsNow = interruptsNow,
    lockScreen = lockScreen,
    isClassifiedAsBulk = isClassifiedAsBulk,
    channelName = channelName,
)

class FakePolicy(
    private val enabled: Set<String> = emptySet(),
    private val flash: Set<String> = emptySet(),
    private val keywords: Map<String, List<String>> = emptyMap(),
    private val flashMillis: Map<String, Long> = emptyMap(),
    private val people: Map<String, List<PersonRule>> = emptyMap(),
    private val paused: Boolean = false,
    private val channels: Map<String, Boolean> = emptyMap(),
) : SourcePolicy {
    override fun isPaused() = paused
    override fun channelChoice(packageName: String, channelId: String) = channels[channelId]
    override fun people(packageName: String) = people[packageName].orEmpty()
    override fun isEnabled(packageName: String) = packageName in enabled
    override fun isFlashEnabled(packageName: String) = packageName in flash
    override fun ignoredKeywords(packageName: String) = keywords[packageName].orEmpty()
    override fun flashDurationMillis(packageName: String) =
        flashMillis[packageName] ?: SourcePolicy.DEFAULT_FLASH_MILLIS
}
