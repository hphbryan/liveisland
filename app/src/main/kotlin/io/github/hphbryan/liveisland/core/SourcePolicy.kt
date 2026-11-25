package io.github.hphbryan.liveisland.core

/** Which apps are relayed, and how. Implemented by the settings store. */
interface SourcePolicy {
    fun isEnabled(packageName: String): Boolean

    /** Whether one-off (non-ongoing) notifications from this app are shown briefly. */
    fun isFlashEnabled(packageName: String): Boolean

    /** How long a one-off notification from this app stays up. */
    fun flashDurationMillis(packageName: String): Long = DEFAULT_FLASH_MILLIS

    /** Words that make a notification from this app be skipped (e.g. "coupon" for promotions). */
    fun ignoredKeywords(packageName: String): List<String> = emptyList()

    /** Relaying is paused for every app, e.g. from the Quick Settings tile. */
    fun isPaused(): Boolean = false

    /**
     * The user's choice for one of this app's notification types (channels): true to show, false to leave
     * out, null if never chosen (then types named like promotions are left out, the rest shown).
     */
    fun channelChoice(packageName: String, channelId: String): Boolean? = null

    /** People whose messages from this app are always shown, however the app is otherwise set up. */
    fun people(packageName: String): List<PersonRule> = emptyList()

    companion object {
        const val DEFAULT_FLASH_MILLIS = 8_000L

        /** Durations offered for one-off notifications, in seconds. */
        val FLASH_SECONDS_CHOICES = listOf(5, 8, 15, 30)
    }
}
