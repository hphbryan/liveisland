package io.github.hphbryan.liveisland.platform

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Opt-in log of recent notifications from enabled apps, used to write rules for specific apps from
 * real examples. Stored in app-private storage, capped, and never sent anywhere by the app itself.
 */
class SampleLog private constructor(context: Context) {
    data class Entry(val time: Long, val packageName: String, val title: String?, val summary: String, val json: String)

    private val file = File(context.filesDir, "samples.jsonl")

    /** The recorded lines, newest last; the file is a copy written off the main thread. */
    private val lines = ArrayDeque(if (file.exists()) file.readLines().takeLast(MAX_ENTRIES) else emptyList())
    private val writer = Executors.newSingleThreadExecutor()
    private val _entries = MutableStateFlow(parse(lines.toList()))
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    @Synchronized
    fun record(sbn: StatusBarNotification, decision: String) {
        val n = sbn.notification
        val e = n.extras
        val json = JSONObject()
            .put("event", "posted")
            .put("decision", decision)
            .put("time", sbn.postTime)
            .put("package", sbn.packageName)
            .put("id", sbn.id)
            .put("tag", sbn.tag)
            .put("channel", n.channelId)
            .put("category", n.category)
            .put("flags", n.flags)
            .put("template", e.getString(Notification.EXTRA_TEMPLATE))
            .put("title", e.getCharSequence(Notification.EXTRA_TITLE)?.toString())
            .put("text", e.getCharSequence(Notification.EXTRA_TEXT)?.toString())
            .put("bigText", e.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString())
            .put("subText", e.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString())
            .put("progress", e.getInt(Notification.EXTRA_PROGRESS, -1))
            .put("progressMax", e.getInt(Notification.EXTRA_PROGRESS_MAX, -1))
            .put("progressIndeterminate", e.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false))
            .put("chronometer", e.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false))
            .put("chronometerCountDown", e.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false))
            .put("when", n.`when`)
            .put("customView", n.contentView != null || n.bigContentView != null)
            .put("actions", n.actions?.joinToString(" | ") { it.title.toString() })
            .put("extraKeys", e.keySet().sorted().joinToString(","))
            .toString()
        append(json)
    }

    /** Records that a notification went away, so short-lived ones (e.g. "responding…") are visible too. */
    @Synchronized
    fun recordRemoval(sbn: StatusBarNotification, reason: Int) {
        append(
            JSONObject()
                .put("event", "removed")
                .put("reason", reason)
                .put("time", System.currentTimeMillis())
                .put("package", sbn.packageName)
                .put("id", sbn.id)
                .put("tag", sbn.tag)
                .put("channel", sbn.notification.channelId)
                .put("title", sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
                .toString(),
        )
    }

    private fun append(json: String) {
        lines.addLast(json)
        while (lines.size > MAX_ENTRIES) lines.removeFirst()
        val snapshot = lines.toList()
        _entries.value = parse(snapshot)
        // A source that updates every second must not mean disk writes on the listener's thread.
        writer.execute { save(snapshot) }
    }

    /** Writes a new file and swaps it in, so a crash mid-write never leaves a half-written log. */
    private fun save(snapshot: List<String>) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        runCatching {
            tmp.writeText(if (snapshot.isEmpty()) "" else snapshot.joinToString("\n", postfix = "\n"))
            check(tmp.renameTo(file))
        }
    }

    @Synchronized
    fun clear() {
        lines.clear()
        _entries.value = emptyList()
        writer.execute { file.delete() }
    }

    /** The raw JSON Lines, for sharing. */
    @Synchronized
    fun export(): String = lines.joinToString("\n", postfix = if (lines.isEmpty()) "" else "\n")

    private fun parse(lines: List<String>): List<Entry> = lines.mapNotNull { line ->
        runCatching {
            val o = JSONObject(line)
            val summary = if (o.optString("event") == "removed") "removed" else o.optString("decision")
            Entry(o.optLong("time"), o.optString("package"), o.optString("title").ifEmpty { null }, summary, line)
        }.getOrNull()
    }.asReversed()

    companion object {
        private const val MAX_ENTRIES = 300

        @Volatile private var instance: SampleLog? = null

        fun get(context: Context): SampleLog =
            instance ?: synchronized(this) {
                instance ?: SampleLog(context.applicationContext).also { instance = it }
            }
    }
}
