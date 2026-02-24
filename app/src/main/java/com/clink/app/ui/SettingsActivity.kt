package com.clink.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.clink.app.BuildConfig
import com.clink.app.R
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // 避免 Edge-to-Edge 导致的内容重叠，根布局 android:fitsSystemWindows="true" 已在 XML 中设置
        // 但如果是在代码中动态处理，可以启用下面的 WindowCompat 配置（配合 XML 使用可双重保险）
        // WindowCompat.setDecorFitsSystemWindows(window, false)

        findViewById<android.view.View>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<TextView>(R.id.tvVersion).text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

        setupDarkMode()
        setupLanguage()
        setupRules()
        setupAbout()
    }

    private fun setupDarkMode() {
        val btn = findViewById<LinearLayout>(R.id.btnDarkMode)
        val tvValue = findViewById<TextView>(R.id.tvDarkModeValue)

        val modes = arrayOf("System Default", "Light", "Dark")
        val modeValues = arrayOf(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES
        )

        // 从 SharedPreferences 读取当前保存的模式
        val prefs = getSharedPreferences("clink_settings", Context.MODE_PRIVATE)
        val currentMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_YES)
        val currentIndex = modeValues.indexOf(currentMode).takeIf { it >= 0 } ?: 0
        tvValue.text = modes[currentIndex]

        btn.setOnClickListener {
            // 只有跟随系统才根据当前系统状态判断，否则使用保存的值
            val activeIndex = modeValues.indexOf(currentMode).takeIf { it >= 0 } ?: 0

            AlertDialog.Builder(this)
                .setTitle(R.string.dark_mode)
                .setSingleChoiceItems(modes, activeIndex) { dialog, which ->
                    val selectedMode = modeValues[which]
                    
                    // 1. 立即应用
                    AppCompatDelegate.setDefaultNightMode(selectedMode)
                    tvValue.text = modes[which]
                    
                    // 2. 持久化保存
                    prefs.edit().putInt("theme_mode", selectedMode).apply()
                    
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun setupLanguage() {
        val btn = findViewById<LinearLayout>(R.id.btnLanguage)
        val tvDesc = findViewById<TextView>(R.id.tvLanguageDesc)
        
        // 显示当前语言状态
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        val currentLang = if (!currentLocales.isEmpty) {
            val tag = currentLocales[0]?.toLanguageTag()
            if (tag == "zh-CN") "中文 (简体)" else "English"
        } else {
            "System Default"
        }
        tvDesc.text = currentLang

        btn.setOnClickListener {
            val languages = arrayOf("System Default", "English", "中文 (简体)")
            val locales = arrayOf("", "en", "zh-CN")
            
            AlertDialog.Builder(this)
                .setTitle(R.string.language)
                .setItems(languages) { _, which ->
                     // 为空表示跟随系统
                     val tag = locales[which]
                     val appLocale = if (tag.isEmpty()) {
                         LocaleListCompat.getEmptyLocaleList()
                     } else {
                         LocaleListCompat.forLanguageTags(tag)
                     }
                     AppCompatDelegate.setApplicationLocales(appLocale)
                     // 语言切换后 Activity 会重启，onCreate 会重新运行并更新 UI
                }
                .show()
        }
    }

    private fun setupRules() {
        findViewById<LinearLayout>(R.id.btnUserRules).setOnClickListener {
            startActivity(Intent(this, UserRulesActivity::class.java))
        }
    }

    private fun setupAbout() {
        findViewById<LinearLayout>(R.id.btnCheckUpdate).setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Xziip/Clink/releases")))
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
