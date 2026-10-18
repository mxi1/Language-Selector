package vegabobo.languageselector.qs

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import vegabobo.languageselector.R

/**
 * Placeholder QS tile. The real implementation (cycling pinned languages for the
 * foreground app) lands with the service layer.
 */
class LocaleTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_UNAVAILABLE
            label = getString(R.string.app_name)
            subtitle = getString(R.string.unavailable)
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
    }
}
