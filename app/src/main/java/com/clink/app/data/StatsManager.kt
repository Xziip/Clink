package com.clink.app.data

import android.content.Context
import android.content.SharedPreferences
import com.clink.app.ClinkApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 统计数据快照
 * @param totalLinks  累计净化链接总数
 * @param totalParams 累计拦截参数总数
 */
data class StatsSnapshot(
    val totalLinks: Int,
    val totalParams: Int
)

class StatsManager private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // 专用协程作用域，与 Application 生命周期绑定
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 内部可变 Flow，初始值从 SharedPreferences 读取（同步，极快）
    private val _statsFlow = MutableStateFlow(readFromPrefs())

    val statsFlow: StateFlow<StatsSnapshot> = _statsFlow.asStateFlow()

    val current: StatsSnapshot get() = _statsFlow.value

    fun record(removedParamCount: Int) {
        if (removedParamCount <= 0) return
        scope.launch {
            val newLinks  = prefs.getInt(KEY_LINKS, 0) + 1
            val newParams = prefs.getInt(KEY_PARAMS, 0) + removedParamCount
            prefs.edit()
                .putInt(KEY_LINKS,  newLinks)
                .putInt(KEY_PARAMS, newParams)
                .apply() // 异步提交，不阻塞 IO 线程
            // 更新 Flow —— StateFlow 线程安全，直接赋值即可
            _statsFlow.value = StatsSnapshot(newLinks, newParams)
        }
    }

   
    fun recordBatch(linksDelta: Int, paramsDelta: Int) {
        if (linksDelta <= 0 && paramsDelta <= 0) return
        scope.launch {
            val newLinks  = prefs.getInt(KEY_LINKS, 0) + linksDelta.coerceAtLeast(0)
            val newParams = prefs.getInt(KEY_PARAMS, 0) + paramsDelta.coerceAtLeast(0)
            prefs.edit()
                .putInt(KEY_LINKS,  newLinks)
                .putInt(KEY_PARAMS, newParams)
                .apply()
            _statsFlow.value = StatsSnapshot(newLinks, newParams)
        }
    }

    // 重置所有统计 预留，用于设置页"清空战绩"功能
    fun reset() {
        scope.launch {
            prefs.edit().putInt(KEY_LINKS, 0).putInt(KEY_PARAMS, 0).apply()
            _statsFlow.value = StatsSnapshot(0, 0)
        }
    }

    // 从 SP 同步读取，仅在初始化时调用一次
    private fun readFromPrefs() = StatsSnapshot(
        totalLinks  = prefs.getInt(KEY_LINKS, 0),
        totalParams = prefs.getInt(KEY_PARAMS, 0)
    )

    companion object {
        private const val PREF_NAME  = "clink_stats"
        private const val KEY_LINKS  = "total_links"
        private const val KEY_PARAMS = "total_params"

        @Volatile
        private var INSTANCE: StatsManager? = null

        fun get(): StatsManager = INSTANCE ?: synchronized(this) {
            INSTANCE ?: StatsManager(ClinkApplication.appContext).also { INSTANCE = it }
        }
    }
}
