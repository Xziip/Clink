package com.clink.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import android.content.Context

class ClinkApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // 仅全局注入 Application Context 供单例使用
        appContext = this
        
        // 恢复深色模式设置
        restoreTheme()
    }

    private fun restoreTheme() {
        val prefs = getSharedPreferences("clink_settings", Context.MODE_PRIVATE)
        // 默认深色模式 = AppCompatDelegate.MODE_NIGHT_YES (2)
        val mode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_YES)
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    companion object {
        @JvmStatic
        lateinit var appContext: ClinkApplication
            private set
    }
}
