package io.github.hphbryan.liveisland.platform

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.hphbryan.liveisland.R

/**
 * A Quick Settings tile that pauses and resumes relaying for every app at once, e.g. for a meeting, without
 * opening the app. On means relaying.
 */
class PauseTileService : TileService() {
    private val store get() = SettingsStore.get(this)

    override fun onStartListening() = update()

    override fun onClick() {
        store.setPaused(!store.state.value.paused)
        update()
    }

    private fun update() {
        val tile = qsTile ?: return
        val paused = store.state.value.paused
        tile.state = if (paused) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.subtitle = getString(if (paused) R.string.tile_paused else R.string.tile_on)
        tile.updateTile()
    }

    companion object {
        /** Asks the system to redraw the tile after the setting changed elsewhere. */
        fun refresh(context: Context) =
            requestListeningState(context, ComponentName(context, PauseTileService::class.java))
    }
}
