package com.musictracker.tracking

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile: tap to start or stop tracking without opening the app. */
class ListenTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        Prefs.setListening(this, !Prefs.isListening(this))
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val on = Prefs.isListening(this)
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Music Tracker"
        tile.subtitle = if (on) "Listening" else "Stopped"
        tile.updateTile()
    }
}
