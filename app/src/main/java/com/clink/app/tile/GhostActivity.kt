package com.clink.app.tile

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import com.clink.app.R
import com.clink.app.data.StatsManager
import com.clink.app.engine.ClinkEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 这是配合通知栏磁贴使用的

class GhostActivity : Activity() {

    // 使用普通 Activity 而非 ComponentActivity，体积最小
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 不调用 setContentView，窗口完全空白
    }

    // onWindowFocusChanged 是 Android 10+ 剪贴板读取唯一可靠时机
    // onResume 时窗口可能尚未获得输入焦点，导致读取被系统拦截
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) processClipboard()
    }

    private fun processClipboard() {
        scope.launch {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val raw = cm.primaryClip?.getItemAt(0)?.coerceToText(this@GhostActivity)?.toString()

            if (raw.isNullOrBlank()) {
                Toast.makeText(this@GhostActivity, R.string.toast_tile_no_url, Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                ClinkEngine.cleanFirst(raw)
            }

            if (result == null) {
                Toast.makeText(this@GhostActivity, R.string.toast_tile_no_url, Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            if (result.hasChanges) {
                // 写回剪贴板
                cm.setPrimaryClip(ClipData.newPlainText("clink_cleaned", result.cleanedUrl))
                StatsManager.get().recordBatch(1, result.removedParams.size)
                Toast.makeText(this@GhostActivity, R.string.toast_tile_done, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@GhostActivity, R.string.toast_already_clean, Toast.LENGTH_SHORT).show()
            }

            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 取消所有协程，防止内存泄漏
        job.cancel()
    }
}
