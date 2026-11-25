package io.github.hphbryan.liveisland.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * What LiveIsland recently did with notifications, and why, so users can see why something did or did
 * not appear. Holds the app, time and outcome only — never notification text — in memory, for this process.
 */
object ActivityLog {
    /** @property outcome a [io.github.hphbryan.liveisland.core.Classifier.explain] label, e.g. "skip:keyword". */
    data class Entry(val key: String, val packageName: String, val outcome: String, val timeMillis: Long)

    private const val MAX_ENTRIES = 100

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())

    /** Newest first. */
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    /**
     * Adds an outcome. Repeated outcomes for the same notification (an ongoing one updating every second)
     * collapse into one entry, moved to the top.
     */
    fun record(key: String, packageName: String, outcome: String, now: Long = System.currentTimeMillis()) {
        _entries.update { list ->
            val rest = list.filterNot { it.key == key && it.outcome == outcome }
            (listOf(Entry(key, packageName, outcome, now)) + rest).take(MAX_ENTRIES)
        }
    }

    fun clear() = _entries.update { emptyList() }

    /** Puts back entries removed by [clear] (Undo), keeping anything recorded since on top. */
    fun restore(old: List<Entry>) = _entries.update { now -> (now + old).distinct().take(MAX_ENTRIES) }
}
