package io.github.hphbryan.liveisland.core

/** Chooses which of a source notification's buttons are carried over to the relayed Live Update. */
object ActionPicker {
    /** What the decision needs to know about one source action. */
    data class SourceAction(val title: String?, val isContextual: Boolean)

    /** Android shows at most three buttons on a notification; one is kept for our own Dismiss. */
    const val MAX_SOURCE_ACTIONS = 2

    /**
     * Indices of the actions to copy, in their original order: titled, non-contextual (system smart
     * replies are regenerated per notification and would be stale), at most [MAX_SOURCE_ACTIONS].
     */
    fun pick(actions: List<SourceAction>): List<Int> =
        actions.withIndex()
            .filter { (_, a) -> !a.isContextual && !a.title.isNullOrBlank() }
            .map { it.index }
            .take(MAX_SOURCE_ACTIONS)
}
