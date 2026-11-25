package io.github.hphbryan.liveisland.core

/**
 * The parts of a posted notification that relaying decisions depend on.
 *
 * Kept free of Android types so classification and text extraction can be unit tested on the JVM.
 */
data class NotificationSnapshot(
    val key: String,
    val packageName: String,
    val channelId: String?,
    /** The channel's name as the app shows it in settings, e.g. "Order updates"; null if unknown. */
    val channelName: String? = null,
    val category: String?,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val isOngoing: Boolean,
    val isGroupSummary: Boolean,
    /**
     * The app does Live Updates itself: the system promoted it, or the app asked for promotion. A request
     * the system turned down is left alone too, since the usual reason is that the user switched off Live
     * Updates for that app.
     */
    val isPromoted: Boolean,
    /** Uses One UI's own Live Notification extras (Samsung Clock, Voice Recorder, ...). */
    val isSamsungLiveNotification: Boolean,
    val isMedia: Boolean,
    val progress: Int,
    val progressMax: Int,
    val progressIndeterminate: Boolean,
    val showsChronometer: Boolean,
    val chronometerCountsDown: Boolean,
    val whenMillis: Long,
    /** For messages (MessagingStyle): who sent the latest message. */
    val sender: String? = null,
    /** For group messages: the group's name. */
    val conversationTitle: String? = null,
    /** Uses MessagingStyle, i.e. a chat message or conversation. */
    val isConversation: Boolean = false,
    /**
     * The user keeps this out of sight: its channel is minimised or blocked, or the app is paused (Digital
     * Wellbeing, Focus mode). Relaying it would put back what the user hid.
     */
    val isHiddenBySystem: Boolean = false,
    /** False while Do Not Disturb would silence it. */
    val interruptsNow: Boolean = true,
    val lockScreen: LockScreen = LockScreen.DEFAULT,
    /**
     * Android's own notification classification put it with promotions, news, recommendations or social
     * media posts. (The app's own "promo"/"recommendation" category is read from [category].)
     */
    val isClassifiedAsBulk: Boolean = false,
)
