package io.github.hphbryan.liveisland.platform

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import androidx.annotation.Keep
import androidx.core.content.edit
import io.github.hphbryan.liveisland.core.AppRules
import io.github.hphbryan.liveisland.core.ChipSource
import io.github.hphbryan.liveisland.core.KeywordFilter
import io.github.hphbryan.liveisland.core.KnownApps
import io.github.hphbryan.liveisland.core.PersonRule
import io.github.hphbryan.liveisland.core.SourcePolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The user's choices, shared by the UI and the listener service (same process). */
class SettingsStore private constructor(context: Context) : SourcePolicy {

    data class State(
        val enabled: Set<String>,
        val flash: Set<String>,
        val recordSamples: Boolean,
        /** Packages seen posting relayable notifications, most recent first; ones that came with the phone only if known. */
        val seen: List<String>,
        /** Per-app duration of one-off notifications, for apps that changed the default. */
        val flashSeconds: Map<String, Int> = emptyMap(),
        /** Per-app words that make a notification be skipped. */
        val keywords: Map<String, List<String>> = emptyMap(),
        /** Per-app people whose messages are always shown. */
        val people: Map<String, List<PersonRule>> = emptyMap(),
        /** Per-app names of recent message senders and groups, newest first, offered when adding people. */
        val recentPeople: Map<String, List<String>> = emptyMap(),
        /** Relaying is paused for every app until turned back on. */
        val paused: Boolean = false,
        /** Words that skip a notification from any app, on top of each app's own. */
        val sharedKeywords: List<String> = emptyList(),
        /** Per-app notification types (channels) seen from enabled apps, in the order first seen. */
        val channels: Map<String, List<ChannelInfo>> = emptyMap(),
        /** Per-app choices for notification types: channel id to shown (true) or left out (false). */
        val channelChoices: Map<String, Map<String, Boolean>> = emptyMap(),
        /** Apps the user said no to when the home screen suggested turning them on. */
        val suggestionsDismissed: Set<String> = emptySet(),
        /** Per-app chip choice, for apps that changed it from automatic. */
        val chipSources: Map<String, ChipSource> = emptyMap(),
        /** Apps whose activity keeps the status bar chip while others are going on too. */
        val first: Set<String> = emptySet(),
        /** On Android 17, one more card that shows every activity at once when there are several. */
        val overview: Boolean = true,
    ) {
        fun flashSecondsFor(packageName: String) =
            flashSeconds[packageName] ?: (SourcePolicy.DEFAULT_FLASH_MILLIS / 1000).toInt()
    }

    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val seenPrefs: SharedPreferences = context.getSharedPreferences("seen", Context.MODE_PRIVATE)
    private val recentPrefs: SharedPreferences = context.getSharedPreferences("recent_people", Context.MODE_PRIVATE)
    private val channelPrefs: SharedPreferences = context.getSharedPreferences("channels", Context.MODE_PRIVATE)
    /** About this phone only (what Android was asked), so not backed up or moved to a new phone. */
    private val devicePrefs: SharedPreferences = context.getSharedPreferences("device", Context.MODE_PRIVATE)
    private val packageManager = context.packageManager
    /** Whether a package came with the phone; asked once per package. Declared before [_state], which uses it. */
    private val preinstalled = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private val _state = MutableStateFlow(load())
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * SharedPreferences holds its listeners weakly, so this field is the only thing keeping it alive. Without
     * @Keep, R8 turns a field that is only read in init into a local, the listener is collected, and in release
     * builds the screens stop updating after a change.
     */
    @field:Keep
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> _state.value = load() }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
        seenPrefs.registerOnSharedPreferenceChangeListener(listener)
        recentPrefs.registerOnSharedPreferenceChangeListener(listener)
        channelPrefs.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun isEnabled(packageName: String) = packageName in _state.value.enabled

    override fun isFlashEnabled(packageName: String) = packageName in _state.value.flash

    override fun flashDurationMillis(packageName: String) = _state.value.flashSecondsFor(packageName) * 1000L

    override fun ignoredKeywords(packageName: String) =
        _state.value.keywords[packageName].orEmpty() + _state.value.sharedKeywords

    override fun isPaused() = _state.value.paused

    override fun channelChoice(packageName: String, channelId: String) = _state.value.channelChoices[packageName]?.get(channelId)

    override fun chipSource(packageName: String) = _state.value.chipSources[packageName] ?: ChipSource.AUTO

    fun isFirst(packageName: String) = packageName in _state.value.first

    fun setChipSource(packageName: String, source: ChipSource) = prefs.edit {
        if (source == ChipSource.AUTO) remove(PREFIX_CHIP + packageName) else putString(PREFIX_CHIP + packageName, source.key)
    }

    fun setFirst(packageName: String, on: Boolean) = editSet(KEY_FIRST, emptySet(), packageName, on)

    fun setOverview(on: Boolean) = prefs.edit { putBoolean(KEY_OVERVIEW, on) }

    /** [packageName]'s choices as [AppRules], to share. People are left out: their names are personal. */
    fun rulesOf(packageName: String): AppRules {
        val st = _state.value
        val ownWords = prefs.getString(PREFIX_KEYWORDS + packageName, null)
        return AppRules(
            packageName = packageName,
            oneOffs = packageName in st.flash,
            seconds = st.flashSeconds[packageName],
            chip = chipSource(packageName),
            first = packageName in st.first,
            skip = ownWords?.let(KeywordFilter::parse),
            types = st.channelChoices[packageName].orEmpty(),
        )
    }

    /** Turns the app in [rules] on and applies its choices, in one write. */
    fun apply(rules: AppRules) {
        val p = rules.packageName
        val flash = (prefs.getStringSet(KEY_FLASH, null) ?: KnownApps.FLASH_BY_DEFAULT).toMutableSet()
        rules.oneOffs?.let { if (it) flash += p else flash -= p }
        val first = prefs.getStringSet(KEY_FIRST, null).orEmpty().toMutableSet()
        if (rules.first) first += p else first -= p
        val types = _state.value.channelChoices[p].orEmpty() + rules.types
        prefs.edit {
            putStringSet(KEY_ENABLED, prefs.getStringSet(KEY_ENABLED, null).orEmpty() + p)
            // Pasted choices replace the starting setup a first turn-on would apply.
            putStringSet(KEY_EVER_ENABLED, prefs.getStringSet(KEY_EVER_ENABLED, null).orEmpty() + p)
            putStringSet(KEY_FLASH, flash)
            putStringSet(KEY_FIRST, first)
            rules.seconds?.let { putInt(PREFIX_FLASH_SECONDS + p, it) }
            if (rules.chip == ChipSource.AUTO) remove(PREFIX_CHIP + p) else putString(PREFIX_CHIP + p, rules.chip.key)
            rules.skip?.let { putString(PREFIX_KEYWORDS + p, KeywordFilter.format(it)) }
            if (types.isNotEmpty()) {
                putStringSet(PREFIX_CHANNELS + p, types.mapTo(HashSet()) { (id, v) -> (if (v) "1:" else "0:") + id })
            }
        }
    }

    /** Whether the notification permission has been asked for, to tell "not asked yet" from "refused for good". */
    fun askedForNotifications() = devicePrefs.getBoolean(KEY_ASKED_POST, false)

    fun markAskedForNotifications() = devicePrefs.edit { putBoolean(KEY_ASKED_POST, true) }

    fun setPaused(paused: Boolean) = prefs.edit { putBoolean(KEY_PAUSED, paused) }

    fun setSharedKeywords(words: List<String>) = prefs.edit { putString(KEY_SHARED_KEYWORDS, KeywordFilter.format(words)) }

    /** Shows ([on]) or leaves out one of an app's notification types. */
    fun setChannel(packageName: String, channelId: String, on: Boolean) {
        val next = _state.value.channelChoices[packageName].orEmpty() + (channelId to on)
        prefs.edit { putStringSet(PREFIX_CHANNELS + packageName, next.mapTo(HashSet()) { (id, v) -> (if (v) "1:" else "0:") + id }) }
    }

    /**
     * Remembers a notification type an enabled app uses, with its name, so it can be chosen on the app's page.
     * Written only when a type is new or renamed.
     */
    fun rememberChannel(packageName: String, id: String, name: String) {
        val current = _state.value.channels[packageName].orEmpty()
        if (current.any { it.id == id && it.name == name }) return
        val next = (current.filterNot { it.id == id } + ChannelInfo(id, name)).takeLast(MAX_CHANNELS)
        channelPrefs.edit { putString(packageName, ChannelInfo.encode(next)) }
    }

    fun dismissSuggestion(packageName: String) = editSet(KEY_SUGGESTIONS_DISMISSED, emptySet(), packageName, true)

    /** Turns several apps on at once, each with its starting setup, in a single write. */
    fun enableAll(packages: Collection<String>) {
        val enabled = prefs.getStringSet(KEY_ENABLED, null).orEmpty()
        val ever = prefs.getStringSet(KEY_EVER_ENABLED, null).orEmpty()
        val firstTime = packages.filter { it !in ever }
        val flash = (prefs.getStringSet(KEY_FLASH, null) ?: KnownApps.FLASH_BY_DEFAULT) + firstTime.filter(KnownApps::flashByDefault)
        prefs.edit {
            putStringSet(KEY_ENABLED, enabled + packages)
            putStringSet(KEY_EVER_ENABLED, ever + packages)
            putStringSet(KEY_FLASH, flash)
        }
    }

    override fun people(packageName: String) = _state.value.people[packageName].orEmpty()

    fun setPeople(packageName: String, rules: List<PersonRule>) = prefs.edit {
        if (rules.isEmpty()) remove(PREFIX_PEOPLE + packageName) else putString(PREFIX_PEOPLE + packageName, PeopleCodec.encode(rules))
    }

    /**
     * Remembers sender and group names from an enabled app so it can be picked when adding people. Names
     * only, at most [MAX_RECENT_PEOPLE] per app, on this device; written only when the order changes, and
     * forgotten when the app is turned off.
     */
    fun rememberPeople(packageName: String, names: List<String>) {
        if (names.isEmpty()) return
        val current = _state.value.recentPeople[packageName].orEmpty()
        // The last name ends up newest; one write for a group name and its sender together.
        val front = names.asReversed().distinct()
        val next = (front + (current - front.toSet())).take(MAX_RECENT_PEOPLE)
        if (next != current) recentPrefs.edit { putString(packageName, PeopleCodec.encodeNames(next)) }
    }

    /**
     * Turning an app on for the first time applies its starting setup (e.g. brief display for AI replies);
     * later changes are the user's. Turning an app off forgets its recent senders (names of people), but keeps
     * the kinds of notifications it was seen with, so the choices made for them still show when it is back on.
     */
    fun setEnabled(packageName: String, on: Boolean) {
        val firstTime = on && packageName !in prefs.getStringSet(KEY_EVER_ENABLED, null).orEmpty()
        editSet(KEY_ENABLED, emptySet(), packageName, on)
        if (firstTime) {
            editSet(KEY_EVER_ENABLED, emptySet(), packageName, true)
            if (KnownApps.flashByDefault(packageName)) setFlash(packageName, true)
        }
        if (!on) recentPrefs.edit { remove(packageName) }
    }

    fun setFlash(packageName: String, on: Boolean) = editSet(KEY_FLASH, KnownApps.FLASH_BY_DEFAULT, packageName, on)

    fun setFlashSeconds(packageName: String, seconds: Int) = prefs.edit { putInt(PREFIX_FLASH_SECONDS + packageName, seconds) }

    /** Stores what the user typed, normalized. An empty list is stored too, so it overrides a built-in default. */
    fun setKeywords(packageName: String, words: List<String>) =
        prefs.edit { putString(PREFIX_KEYWORDS + packageName, KeywordFilter.format(words)) }

    fun setRecordSamples(on: Boolean) = prefs.edit { putBoolean(KEY_RECORD, on) }

    /** Remembers that [packageName] posted a relayable notification; written at most once a minute per app. */
    fun markSeen(packageName: String, now: Long = System.currentTimeMillis()) {
        if (now - seenPrefs.getLong(packageName, 0L) > SEEN_WRITE_INTERVAL_MS) {
            seenPrefs.edit { putLong(packageName, now) }
        }
    }

    private fun editSet(key: String, default: Set<String>, item: String, on: Boolean) {
        val next = (prefs.getStringSet(key, null) ?: default).toMutableSet()
        if (on) next += item else next -= item
        prefs.edit { putStringSet(key, next) }
    }

    /**
     * Apps that came with the phone (camera, security scans, routines, the store) post plenty of "in progress"
     * notifications that nobody wants on the island; only suggest them when they are known to be useful.
     */
    private fun isWorthSuggesting(packageName: String): Boolean =
        packageName in KnownApps.ALL || !preinstalled.getOrPut(packageName) {
            runCatching {
                packageManager.getApplicationInfo(packageName, 0).flags and
                    (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            }.getOrDefault(true) // Uninstalled: nothing to suggest.
        }

    private fun load(): State {
        val all = prefs.all
        return State(
            enabled = prefs.getStringSet(KEY_ENABLED, null).orEmpty().toSet(),
            flash = (prefs.getStringSet(KEY_FLASH, null) ?: KnownApps.FLASH_BY_DEFAULT).toSet(),
            recordSamples = prefs.getBoolean(KEY_RECORD, false),
            seen = seenPrefs.all.entries
                .mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }
                .filter { it.first !in KnownApps.SYSTEM && isWorthSuggesting(it.first) }
                .sortedByDescending { it.second }
                .map { it.first },
            flashSeconds = all.entries
                .filter { it.key.startsWith(PREFIX_FLASH_SECONDS) }
                .mapNotNull { (k, v) -> (v as? Int)?.let { k.removePrefix(PREFIX_FLASH_SECONDS) to it } }
                .toMap(),
            keywords = KnownApps.DEFAULT_IGNORED_KEYWORDS + all.entries
                .filter { it.key.startsWith(PREFIX_KEYWORDS) }
                .mapNotNull { (k, v) -> (v as? String)?.let { k.removePrefix(PREFIX_KEYWORDS) to KeywordFilter.parse(it) } }
                .toMap(),
            people = all.entries
                .filter { it.key.startsWith(PREFIX_PEOPLE) }
                .mapNotNull { (k, v) -> (v as? String)?.let { k.removePrefix(PREFIX_PEOPLE) to PeopleCodec.decode(it) } }
                .toMap(),
            recentPeople = recentPrefs.all.entries
                .mapNotNull { (k, v) -> (v as? String)?.let { k to PeopleCodec.decodeNames(it) } }
                .toMap(),
            paused = prefs.getBoolean(KEY_PAUSED, false),
            sharedKeywords = KeywordFilter.parse(prefs.getString(KEY_SHARED_KEYWORDS, null).orEmpty()),
            channels = channelPrefs.all.entries
                .mapNotNull { (k, v) -> (v as? String)?.let { k to ChannelInfo.decode(it) } }
                .toMap(),
            channelChoices = all.entries
                .filter { it.key.startsWith(PREFIX_CHANNELS) }
                .mapNotNull { (k, v) ->
                    (v as? Set<*>)?.filterIsInstance<String>()?.associate { it.substringAfter(':') to it.startsWith("1:") }
                        ?.let { k.removePrefix(PREFIX_CHANNELS) to it }
                }
                .toMap(),
            suggestionsDismissed = prefs.getStringSet(KEY_SUGGESTIONS_DISMISSED, null).orEmpty().toSet(),
            chipSources = all.entries
                .filter { it.key.startsWith(PREFIX_CHIP) }
                .mapNotNull { (k, v) -> (v as? String)?.let { k.removePrefix(PREFIX_CHIP) to ChipSource.of(it) } }
                .toMap(),
            first = prefs.getStringSet(KEY_FIRST, null).orEmpty().toSet(),
            overview = prefs.getBoolean(KEY_OVERVIEW, true),
        )
    }

    companion object {
        private const val KEY_ENABLED = "enabled_packages"
        private const val KEY_FLASH = "flash_packages"
        private const val KEY_RECORD = "record_samples"
        private const val KEY_EVER_ENABLED = "ever_enabled_packages"
        private const val PREFIX_FLASH_SECONDS = "flash_seconds:"
        private const val PREFIX_KEYWORDS = "ignored_keywords:"
        private const val PREFIX_PEOPLE = "people:"
        private const val PREFIX_CHANNELS = "channels:"
        private const val KEY_PAUSED = "paused"
        private const val KEY_SHARED_KEYWORDS = "shared_keywords"
        private const val KEY_ASKED_POST = "asked_post_notifications"
        private const val KEY_SUGGESTIONS_DISMISSED = "suggestions_dismissed"
        private const val PREFIX_CHIP = "chip:"
        private const val KEY_FIRST = "first_packages"
        private const val KEY_OVERVIEW = "overview"
        private const val MAX_CHANNELS = 30
        private const val MAX_RECENT_PEOPLE = 20
        private const val SEEN_WRITE_INTERVAL_MS = 60_000L

        @Volatile private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context.applicationContext).also { instance = it }
            }
    }
}
