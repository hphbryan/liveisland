package io.github.hphbryan.liveisland.core

/** One chat message as the source notification has it: who sent it, if the app says, and what it says. */
data class ChatLine(val sender: String?, val text: String)

/**
 * The unread messages of each chat, so a relayed copy can show all of them when expanded, not only the
 * latest. Some apps (LINE, for one) replace their notification with each new message, so the earlier ones
 * are only kept here, as they arrive. In memory only, and forgotten when the chat's notification goes away
 * (the messages were read).
 */
class MessageHistory(private val maxLines: Int = MAX_LINES) {
    /** A kept message: its line, and the app's id for it when the app gives one. */
    private data class Entry(val id: String?, val line: String)

    /** Messages to show, per chat. */
    private val chats = HashMap<String, List<Entry>>()

    /** Messages the user already dismissed, per chat: not shown again, nor taken for new ones. */
    private val seen = HashMap<String, List<Entry>>()

    /**
     * Adds what [sourceKey]'s notification shows now and returns the messages to show, oldest first. When
     * the notification lists several messages itself ([incoming] has two or more), that list is used, less
     * any already dismissed. When it shows only the latest, that one is added after the earlier ones unless
     * it is already there: by the app's id for it ([id]) when it gives one, otherwise when it says the same
     * as the last one.
     */
    fun merge(sourceKey: String, incoming: List<String>, id: String? = null): List<String> {
        val shown = chats[sourceKey].orEmpty()
        val past = seen[sourceKey].orEmpty()
        val latest = incoming.singleOrNull()
        val merged = when {
            incoming.size >= 2 -> past.mapTo(HashSet()) { it.line }.let { old -> incoming.filterNot { it in old } }.map { Entry(null, it) }
            latest == null -> shown
            id != null && (past + shown).any { it.id == id } -> shown
            id == null && (shown.lastOrNull() ?: past.lastOrNull())?.line == latest -> shown
            else -> shown + Entry(id, latest)
        }.takeLast(maxLines)
        chats[sourceKey] = merged
        return merged.map { it.line }
    }

    /** The user dismissed [sourceKey]'s copy: what it showed is not shown again; only newer messages are. */
    fun dismissed(sourceKey: String) {
        seen[sourceKey] = (seen[sourceKey].orEmpty() + chats[sourceKey].orEmpty()).takeLast(maxLines)
        chats -= sourceKey
    }

    /** Whether anything is known of [sourceKey] in this process. */
    fun knows(sourceKey: String) = sourceKey in chats || sourceKey in seen

    fun forget(sourceKey: String) {
        chats -= sourceKey
        seen -= sourceKey
    }

    companion object {
        /** Messages kept per chat; the oldest go first. */
        const val MAX_LINES = 10

        /**
         * A message as a line of the expanded copy: with who sent it, unless that is the chat itself ([chat],
         * the other person in a one-to-one chat), and without the name repeated when the app puts it in the
         * text too ("Bob: hi").
         */
        fun format(line: ChatLine, chat: String): String {
            val sender = line.sender?.trim()?.ifEmpty { null }
            val text = line.text.trim()
            if (sender == null || sender == chat.trim()) return text
            val body = text.replaceFirst(Regex("^" + Regex.escape(sender) + "\\s*[:：]\\s*"), "")
            return "$sender: $body"
        }
    }
}
