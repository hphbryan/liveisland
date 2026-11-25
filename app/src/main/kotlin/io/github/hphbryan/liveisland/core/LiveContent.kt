package io.github.hphbryan.liveisland.core

/** What a relayed Live Update shows. Built by [Classifier]; rendered by the platform layer. */
data class LiveContent(
    val sourceKey: String,
    val packageName: String,
    val title: String,
    val text: String?,
    val chip: ChipText?,
    /** What a value in the chip is about, shown after it: the route after "3 min", the file after "45%". */
    val chipDetail: String? = null,
    val progress: Progress?,
    /** Start or end time for a chronometer shown in the chip, if the source shows one. */
    val chronometer: Chronometer?,
    val kind: Kind,
    /** When the relayed copy removes itself; set for [Kind.FLASH] only. Not part of the [fingerprint]. */
    val timeoutMillis: Long? = null,
    /** How much of the copy the lock screen may show, carried over from the source. */
    val lockScreen: LockScreen = LockScreen.DEFAULT,
) {
    enum class Kind {
        /** Mirrors an ongoing activity for as long as the source notification is ongoing. */
        ONGOING,

        /** A one-off notification (e.g. an AI reply) shown briefly, then removed. */
        FLASH,

        /** A message from someone the user picked, kept until the source notification goes away (read). */
        STICKY,
    }

    sealed interface Progress {
        data class Determinate(val value: Int, val max: Int) : Progress
        data object Indeterminate : Progress
    }

    data class Chronometer(val whenMillis: Long, val countsDown: Boolean)

    /** Identity of what the user sees; an update with the same fingerprint is not re-posted. */
    val fingerprint: Int get() = listOf(title, text, chip, chipDetail, progress, chronometer, kind, lockScreen).hashCode()
}

/**
 * What the status bar chip says before the Live Update is expanded. Values are localised by the platform
 * layer; text taken from the notification is already fitted with [TextShortener].
 */
sealed interface ChipText {
    data class Minutes(val minutes: Int) : ChipText
    data class Distance(val amount: String, val unit: String) : ChipText
    data class ClockTime(val time: String) : ChipText
    data class Percent(val percent: Int) : ChipText

    /** A one-off reply from an AI assistant; shown with the app's name, e.g. "ChatGPT replied". */
    data object Replied : ChipText

    /** A chat message: who sent it and how it starts, e.g. "Amy: on my way". */
    data class Message(val sender: String, val preview: String?) : ChipText

    /** The notification's own words when there is no value to show, e.g. "Order delivered". */
    data class Headline(val text: String) : ChipText

    /** A bus or train at the stop or about to arrive. */
    data object Arriving : ChipText

    data class Stops(val stops: Int) : ChipText
}

/** What the lock screen may show of a relayed copy, so it never shows more than the source would. */
enum class LockScreen {
    /** Follows the user's lock screen setting, like any notification. */
    DEFAULT,

    /** Only which app it is from (the source comes from another profile, such as a work profile). */
    APP_NAME_ONLY,

    /** Nothing at all (the source is secret on the lock screen, by the app or the user's setting). */
    HIDDEN,
}
