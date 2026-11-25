package io.github.hphbryan.liveisland.platform

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.hphbryan.liveisland.R
import io.github.hphbryan.liveisland.ui.MainActivity

/**
 * A Quick Settings tile that pauses and resumes relaying for every app at once, e.g. for a meeting, without
 * opening the app. On means relaying.
 */
class PauseTileService : TileService() {
    private val store get() = SettingsStore.get(this)

    override fun onStartListening() = update()

    override fun onClick() {
        if (!SetupStatus.read(this).ready) {
            // Nothing relays until setup is done, so pausing would mean nothing: open the app to finish it.
            val open = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE))
            return
        }
        store.setPaused(!store.state.value.paused)
        update()
    }

    private fun update() {
        val tile = qsTile ?: return
        val paused = store.state.value.paused
        when {
            !SetupStatus.read(this).ready -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = getString(R.string.tile_setup)
            }
            paused -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = getString(R.string.tile_paused)
            }
            else -> {
                tile.state = Tile.STATE_ACTIVE
                tile.subtitle = getString(R.string.tile_on)
            }
        }
        tile.updateTile()
    }

    companion object {
        /** Asks the system to redraw the tile after the setting changed elsewhere. */
        fun refresh(context: Context) =
            requestListeningState(context, ComponentName(context, PauseTileService::class.java))
    }
}
