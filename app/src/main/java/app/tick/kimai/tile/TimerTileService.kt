package app.tick.kimai.tile

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.tick.kimai.TimerRepository
import app.tick.kimai.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TimerTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        render()
        scope.launch {
            // updateTile = false: asking the system to re-listen from here would loop.
            val r = TimerRepository(applicationContext).sync(size = 10, updateTile = false)
            if (r.isSuccess) render()
        }
    }

    override fun onClick() {
        scope.launch {
            TimerRepository(applicationContext).toggle(updateTile = false)
            render()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun render() {
        val tile = qsTile ?: return
        val s = Prefs(this).timerState()
        tile.state = if (s.running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Tick"
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = if (s.running) s.project else "Start last"
        }
        tile.updateTile()
    }
}
