package io.github.hphbryan.liveisland.core

import java.util.Locale

/**
 * Someone whose messages the user always wants to see.
 *
 * @property name matched against the message's sender, the group's name or the notification title.
 * @property untilRead keep the Live Update until the message is read (the source notification goes away),
 *   rather than showing it briefly.
 */
data class PersonRule(val name: String, val untilRead: Boolean = true)

object PersonMatcher {
    /**
     * The rule for whoever this notification is from, if any. An exact name (ignoring case) wins over a
     * partial one, so "Amy" does not take a message meant for a separate "Amy Chen" rule.
     */
    fun match(rules: List<PersonRule>, s: NotificationSnapshot): PersonRule? {
        if (rules.isEmpty()) return null
        val candidates = listOfNotNull(s.sender, s.conversationTitle, s.title).map { it.trim().lowercase(Locale.ROOT) }
        val named = rules.filter { it.name.isNotBlank() }
        return named.firstOrNull { r -> candidates.any { it == r.name.trim().lowercase(Locale.ROOT) } }
            ?: named.firstOrNull { r ->
                val n = r.name.trim().lowercase(Locale.ROOT)
                n.length >= MIN_PARTIAL_LENGTH && candidates.any { n in it }
            }
    }

    /** Shorter names match only exactly; one character would match far too much. */
    private const val MIN_PARTIAL_LENGTH = 2
}
