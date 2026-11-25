package io.github.hphbryan.liveisland.platform

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How often the user dismisses each app's relayed copies, and of which notification type, so the home
 * screen can suggest showing less of it. Counts only: which app, which type id, how many times. Never what a
 * notification said. On this phone only (not backed up), and cleared for an app when a suggestion is answered.
 */
class Habits private constructor(context: Context) {
    /** Dismissals per app, and per app and notification type. */
    data class State(val apps: Map<String, Int> = emptyMap(), val types: Map<Pair<String, String>, Int> = emptyMap())

    private val prefs: SharedPreferences = context.getSharedPreferences("habits", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<State> = _state.asStateFlow()

    fun dismissed(packageName: String, channelId: String?) {
        prefs.edit {
            putInt(APP + packageName, prefs.getInt(APP + packageName, 0) + 1)
            if (channelId != null) {
                val key = TYPE + packageName + SEP + channelId
                putInt(key, prefs.getInt(key, 0) + 1)
            }
        }
        _state.value = load()
    }

    /** Forgets [packageName]'s counts, after its suggestion was answered either way. */
    fun reset(packageName: String) {
        prefs.edit {
            prefs.all.keys
                .filter { it == APP + packageName || it.startsWith(TYPE + packageName + SEP) }
                .forEach(::remove)
        }
        _state.value = load()
    }

    private fun load(): State {
        val all = prefs.all
        return State(
            apps = all.mapNotNull { (k, v) -> if (k.startsWith(APP) && v is Int) k.removePrefix(APP) to v else null }.toMap(),
            types = all.mapNotNull { (k, v) ->
                if (!k.startsWith(TYPE) || v !is Int) return@mapNotNull null
                val rest = k.removePrefix(TYPE)
                val i = rest.indexOf(SEP)
                if (i <= 0) null else (rest.substring(0, i) to rest.substring(i + 1)) to v
            }.toMap(),
        )
    }

    companion object {
        private const val APP = "app:"
        private const val TYPE = "type:"
        private const val SEP = "|"

        /** Dismissals of one notification type before suggesting to stop showing that type. */
        const val TYPE_THRESHOLD = 3

        /** Dismissals of an app before suggesting to turn it off. */
        const val APP_THRESHOLD = 5

        @Volatile private var instance: Habits? = null

        fun get(context: Context): Habits =
            instance ?: synchronized(this) { instance ?: Habits(context.applicationContext).also { instance = it } }
    }
}
