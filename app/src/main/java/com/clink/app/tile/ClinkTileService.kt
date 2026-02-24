package com.clink.app.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.clink.app.R

// 通知栏磁贴 

class ClinkTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        // 更新瓷贴状态为 INACTIVE（非激活，表示"可点击"而非"已开启"）
        qsTile?.apply {
            state    = Tile.STATE_INACTIVE
            subtitle = getString(R.string.tile_label)
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        launchGhost()
    }

    private fun launchGhost() {
        val intent = Intent(applicationContext, GhostActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
        }

        if (Build.VERSION.SDK_INT >= 34) {
            // Android 14+ (API 34)，使用 PendingIntent 方法
            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            // Android 13 及以下，使用旧的 Intent 方法
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    override fun onTileAdded() {
        super.onTileAdded()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
