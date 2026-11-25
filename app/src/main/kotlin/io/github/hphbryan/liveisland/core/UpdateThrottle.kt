package io.github.hphbryan.liveisland.core

/**
 * Limits how often one source is re-posted and skips updates that would not change anything.
 * Not thread-safe; use from a single thread (the listener's main thread).
 */
class UpdateThrottle(
    private val minIntervalMillis: Long,
    private val clock: () -> Long,
) {
    sealed interface Action {
        /** Post now. */
        data object Post : Action

        /** Nothing visible changed. */
        data object Skip : Action

        /** Post after [delayMillis]; the latest content offered by then wins. */
        data class Defer(val delayMillis: Long) : Action
    }

    private val lastFingerprint = HashMap<String, Int>()
    private val lastPostAt = HashMap<String, Long>()
    private val deferred = HashMap<String, LiveContent>()

    fun offer(content: LiveContent): Action {
        val key = content.sourceKey
        if (key in deferred) {
            deferred[key] = content
            return Action.Skip
        }
        if (lastFingerprint[key] == content.fingerprint) return Action.Skip
        val wait = minIntervalMillis - (clock() - (lastPostAt[key] ?: Long.MIN_VALUE / 2))
        if (wait > 0) {
            deferred[key] = content
            return Action.Defer(wait)
        }
        return Action.Post
    }

    /** Returns the content to post for a deferred key, or null if it was cancelled or is unchanged. */
    fun takeDeferred(key: String): LiveContent? {
        val content = deferred.remove(key) ?: return null
        return content.takeIf { lastFingerprint[key] != it.fingerprint }
    }

    fun markPosted(content: LiveContent) {
        lastFingerprint[content.sourceKey] = content.fingerprint
        lastPostAt[content.sourceKey] = clock()
    }

    /** True if this source has been posted (and not forgotten), i.e. a relayed copy may exist. */
    fun isTracked(key: String): Boolean = key in lastFingerprint || key in deferred

    fun forget(key: String) {
        lastFingerprint.remove(key)
        lastPostAt.remove(key)
        deferred.remove(key)
    }
}
