package com.kap.record.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.kap.record.Constants
import com.kap.record.R
import com.kap.record.projection.ProjectionTrampolineActivity
import com.kap.record.service.ScreenRecordService
import com.kap.record.state.RecordingState
import com.kap.record.state.RecordingStateHolder

class RecordTileService : TileService(), RecordingStateHolder.Listener {

    override fun onStartListening() {
        super.onStartListening()
        RecordingStateHolder.ensureRestored(this)
        RecordingStateHolder.addListener(this)
        updateTile()
    }

    override fun onStopListening() {
        RecordingStateHolder.removeListener(this)
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        when (RecordingStateHolder.state) {
            RecordingState.IDLE -> launchTrampoline()
            RecordingState.RECORDING, RecordingState.PAUSED, RecordingState.ARMED -> stopFromTile()
            RecordingState.STOPPING -> Unit
        }
    }

    override fun onStateChanged(state: RecordingState, isPaused: Boolean, elapsedMs: Long, error: String?) {
        updateTile()
    }

    private fun launchTrampoline() {
        val intent = Intent(this, ProjectionTrampolineActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun stopFromTile() {
        val stopIntent = Intent(this, ScreenRecordService::class.java).setAction(Constants.ACTION_STOP)
        startService(stopIntent)
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        when (RecordingStateHolder.state) {
            RecordingState.RECORDING, RecordingState.ARMED -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = getString(R.string.tile_label_recording)
            }
            RecordingState.PAUSED -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = getString(R.string.tile_label_paused)
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = getString(R.string.tile_label)
            }
        }
        tile.updateTile()
    }
}
