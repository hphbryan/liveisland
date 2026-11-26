package io.github.hphbryan.liveisland.core

/** Decides whether and how a posted notification is relayed as a Live Update. */
object Classifier {

    sealed interface Decision {
        /** Show (or update) a relayed Live Update with this content. */
        data class Relay(val content: LiveContent) : Decision

        /** Remove any relayed copy of this source. */
        data object Remove : Decision
    }

    /** Why a notification can never be relayed, regardless of the user's choices; null if it can. */
    fun ineligibility(s: NotificationSnapshot): String? = when {
        s.packageName in KnownApps.SYSTEM -> "system"
        s.isHiddenBySystem -> "hidden"
        s.isClassifiedAsBulk || s.category == CATEGORY_PROMO || s.category == CATEGORY_RECOMMENDATION -> "promo"
        isBackgroundNotice(s) -> "background"
        s.isGroupSummary -> "group-summary"
        s.isPromoted -> "already-live-update"
        s.isSamsungLiveNotification -> "already-samsung-live"
        s.isMedia -> "media"
        s.category == CATEGORY_CALL -> "call"
        s.title.isNullOrBlank() -> "no-title"
        !KnownApps.isRelevant(s) -> "not-relevant"
        else -> null
    }

    /** True if this notification could be relayed at all, regardless of the user's choices. */
    fun isCandidate(s: NotificationSnapshot): Boolean = ineligibility(s) == null

    /** A short, stable label for what [classify] does with [s] and why; recorded in notification samples. */
    fun explain(s: NotificationSnapshot, policy: SourcePolicy): String {
        ineligibility(s)?.let { return "skip:$it" }
        userSkip(s, policy)?.let { return "skip:$it" }
        val person = PersonMatcher.match(policy.people(s.packageName), s)
        return when {
            // The user's chip choice found nothing in this one: say so, as the automatic chip is shown instead.
            isOngoingActivity(s) -> if (usesFallbackChip(s, policy)) "relay:ongoing-fallback" else "relay:ongoing"
            // Something in progress stays on the island quietly; a new message or reply would interrupt.
            !s.interruptsNow && (person != null || policy.isFlashEnabled(s.packageName)) -> "skip:dnd"
            person != null && person.untilRead -> "relay:person-until-read"
            person != null -> "relay:person"
            policy.isFlashEnabled(s.packageName) -> if (usesFallbackChip(s, policy)) "relay:flash-fallback" else "relay:flash"
            else -> "skip:not-ongoing"
        }
    }

    /** True if the app has a chip choice that [s] has nothing for (e.g. no number in it). */
    private fun usesFallbackChip(s: NotificationSnapshot, policy: SourcePolicy): Boolean {
        val source = policy.chipSource(s.packageName)
        if (source == ChipSource.AUTO || s.title.isNullOrBlank()) return false
        return ChipRule.pick(source, s.title.trim(), chipText(s)) == null
    }

    /** The line under the title that a chip choice reads: the expanded text if the app gives one. */
    private fun chipText(s: NotificationSnapshot) = (s.bigText ?: s.text)?.trim()?.ifEmpty { null }

    /**
     * A permanent "running in the background" notice: the app marks it as a service and it shows no
     * progress or timer. Something in progress (an upload, a workout) still counts by its progress or timer.
     */
    private fun isBackgroundNotice(s: NotificationSnapshot) =
        s.isOngoing && s.category == CATEGORY_SERVICE && s.progressMax <= 0 && !s.progressIndeterminate && !s.showsChronometer

    /** True if this notification represents something in progress (as opposed to a one-off). */
    fun isOngoingActivity(s: NotificationSnapshot): Boolean =
        s.isOngoing || s.progressMax > 0 || s.progressIndeterminate || s.showsChronometer

    /**
     * Why the user's settings leave [s] out, or null: the app is off, relaying is paused, its notification
     * type is off (or, never chosen, is named like promotions), or it contains a skipped word.
     */
    private fun userSkip(s: NotificationSnapshot, policy: SourcePolicy): String? = when {
        !policy.isEnabled(s.packageName) -> "app-off"
        policy.isPaused() -> "paused"
        s.channelId != null && policy.channelChoice(s.packageName, s.channelId) == false -> "channel"
        s.channelId != null && policy.channelChoice(s.packageName, s.channelId) == null &&
            KnownApps.looksPromotional(s.channelName) -> "promo"
        KeywordFilter.matches(policy.ignoredKeywords(s.packageName), s) -> "keyword"
        else -> null
    }

    fun classify(s: NotificationSnapshot, policy: SourcePolicy): Decision {
        // An update that is now left out (e.g. a ride that turned into an ad) removes the copy.
        if (!isCandidate(s) || userSkip(s, policy) != null) return Decision.Remove
        if (isOngoingActivity(s)) return Decision.Relay(ongoingContent(s, policy.chipSource(s.packageName)))
        if (!s.interruptsNow) return Decision.Remove
        val flashMillis = policy.flashDurationMillis(s.packageName)
        PersonMatcher.match(policy.people(s.packageName), s)?.let { return Decision.Relay(personContent(s, it, flashMillis)) }
        return when {
            policy.isFlashEnabled(s.packageName) -> Decision.Relay(flashContent(s, flashMillis, policy.chipSource(s.packageName)))
            // Was ongoing before and has now ended (e.g. "Delivered"): drop the relayed copy.
            else -> Decision.Remove
        }
    }

    private fun ongoingContent(s: NotificationSnapshot, source: ChipSource): LiveContent {
        val progress = when {
            // In whole percent: a download counting bytes would otherwise re-post for changes no one can see.
            s.progressMax > 0 -> LiveContent.Progress.Determinate(
                (s.progress.coerceIn(0, s.progressMax).toLong() * 100 / s.progressMax).toInt(), 100,
            )
            s.progressIndeterminate -> LiveContent.Progress.Indeterminate
            else -> null
        }
        val text = chipText(s)
        val title = s.title!!.trim()
        // The user's choice first; if it finds nothing, a value; with no value but a running timer, leave the
        // chip to the timer.
        val chosen = ChipRule.pick(source, title, text)
        val value = if (chosen == null) ChipTextExtractor.extract(title, text, progress) else null
        return LiveContent(
            sourceKey = s.key,
            packageName = s.packageName,
            title = title,
            text = text,
            chip = chosen ?: value ?: if (s.showsChronometer) null else ChipText.Headline(TextShortener.fit(title)),
            chipDetail = value?.let { chipDetail(it, title, text) },
            progress = progress,
            chronometer = if (s.showsChronometer) LiveContent.Chronometer(s.whenMillis, s.chronometerCountsDown) else null,
            kind = LiveContent.Kind.ONGOING,
            lockScreen = s.lockScreen,
        )
    }

    private fun flashContent(s: NotificationSnapshot, timeoutMillis: Long, source: ChipSource): LiveContent = LiveContent(
        sourceKey = s.key,
        packageName = s.packageName,
        title = s.title!!.trim(),
        text = s.text?.trim()?.ifEmpty { null },
        chip = ChipRule.pick(source, s.title!!.trim(), chipText(s)) ?: when {
            s.packageName in KnownApps.AI_ASSISTANTS -> ChipText.Replied
            s.isConversation && !s.sender.isNullOrBlank() -> message(s.sender, s.text)
            else -> ChipText.Headline(TextShortener.fit(listOfNotNull(s.title, s.text).joinToString(" ")))
        },
        progress = null,
        chronometer = null,
        kind = LiveContent.Kind.FLASH,
        timeoutMillis = timeoutMillis,
        lockScreen = s.lockScreen,
        // Messages from others in a chat app, shown briefly: earlier unread ones come along too.
        messages = if (s.isConversation || KnownApps.hasPeople(s.packageName)) messageLines(s) else emptyList(),
        messageId = s.messageId,
    )

    private fun personContent(s: NotificationSnapshot, person: PersonRule, flashMillis: Long) = LiveContent(
        sourceKey = s.key,
        packageName = s.packageName,
        title = s.title!!.trim(),
        text = s.text?.trim()?.ifEmpty { null },
        chip = message(s.sender?.ifBlank { null } ?: s.title!!, s.text),
        progress = null,
        chronometer = null,
        kind = if (person.untilRead) LiveContent.Kind.STICKY else LiveContent.Kind.FLASH,
        timeoutMillis = if (person.untilRead) null else flashMillis,
        lockScreen = s.lockScreen,
        messages = messageLines(s),
        messageId = s.messageId,
    )

    /** The messages [s] shows, a line each: all it lists, or else its text as the latest message. */
    private fun messageLines(s: NotificationSnapshot): List<String> {
        val chat = s.conversationTitle?.ifBlank { null } ?: s.title!!.trim()
        val lines = s.messages.ifEmpty { listOfNotNull(s.text?.trim()?.ifEmpty { null }?.let { ChatLine(s.sender, it) }) }
        return lines.map { MessageHistory.format(it, chat) }
    }

    /**
     * The line a chip value was not taken from, so the chip says what it is about: "12 往火車站" next to
     * "3 分" found in "約 3 分鐘到站", or the driver's car next to "3 min" found in the title.
     */
    private fun chipDetail(value: ChipText, title: String, text: String?): String? {
        val fromTitle = ChipTextExtractor.extract(title, null, null) == value
        val other = (if (fromTitle) text else title)?.ifBlank { null } ?: return null
        return TextShortener.fit(other, DETAIL_COLUMNS)
    }

    /** "Name: how the message starts", without the name repeated when the app puts it in the text too. */
    private fun message(sender: String, text: String?): ChipText.Message {
        val name = TextShortener.fit(sender, TextShortener.NAME_COLUMNS)
        val body = text?.trim()
            ?.replaceFirst(Regex("^" + Regex.escape(sender.trim()) + "\\s*[:：]\\s*"), "")
            ?.ifBlank { null }
        val room = TextShortener.CHIP_COLUMNS - TextShortener.width(name) - 2
        return ChipText.Message(name, body?.let { TextShortener.fit(it, room) })
    }

    /** Leaves room for a value such as "12 min" in front. */
    private const val DETAIL_COLUMNS = 12

    /** Same values as android.app.Notification.CATEGORY_*. */
    const val CATEGORY_CALL = "call"
    const val CATEGORY_SERVICE = "service"
    const val CATEGORY_PROMO = "promo"
    const val CATEGORY_RECOMMENDATION = "recommendation"
}
