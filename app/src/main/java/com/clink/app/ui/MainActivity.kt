package com.clink.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.clink.app.R
import com.clink.app.data.StatsManager
import com.clink.app.engine.ClinkEngine
import com.clink.app.engine.CleanResult
import com.clink.app.engine.RemovedParam
import com.clink.app.ui.widget.FlowLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Intent
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout

class MainActivity : AppCompatActivity() {

    // 对应 activity_main.xml 中的 View id
    private lateinit var tvStatLinks: TextView
    private lateinit var tvStatParams: TextView
    private lateinit var etInput: EditText
    private lateinit var btnClipboard: ImageButton
    private lateinit var btnClean: Button
    private lateinit var layoutLens: LinearLayout
    private lateinit var containerReports: LinearLayout
    private lateinit var btnSettings: ImageButton

    private val stats by lazy { StatsManager.get() }
    private val clipboard by lazy {
        getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        // 处理状态栏间距
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.scrollRoot)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, bars.bottom)
            insets
        }

        bindViews()
        setupListeners()
        observeStats()
    }

    private fun bindViews() {
        tvStatLinks      = findViewById(R.id.tvStatLinks)
        tvStatParams     = findViewById(R.id.tvStatParams)
        etInput          = findViewById(R.id.etInput)
        btnClipboard     = findViewById(R.id.btnClipboard)
        btnClean         = findViewById(R.id.btnClean)
        layoutLens       = findViewById(R.id.layoutLens)
        containerReports = findViewById(R.id.containerReports)
        btnSettings      = findViewById(R.id.btnSettings)
    }

    private fun setupListeners() {
        // 设置按钮
        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // 剪贴板按钮：读取 -> 填入输入框 -> 自动触发清洗
        btnClipboard.setOnClickListener { v ->
            // KEYBOARD_TAP 需要 API 27+，Android 8.0 使用 VIRTUAL_KEY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            } else {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
            val text = readClipboard()
            if (!text.isNullOrBlank()) {
                etInput.setText(text)
                etInput.setSelection(text.length)
                triggerClean()
            } else {
                Toast.makeText(this, R.string.toast_no_url, Toast.LENGTH_SHORT).show()
            }
        }


        // 净化按钮
        btnClean.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            triggerClean()
        }
    }

    private fun triggerClean() {
        val raw = etInput.text?.toString()?.trim() ?: return
        if (raw.isBlank()) {
            Toast.makeText(this, R.string.toast_no_url, Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    ClinkEngine.cleanAll(raw)
                }

                if (results.isEmpty()) {
                    Toast.makeText(this@MainActivity, R.string.toast_no_url, Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val changed = results.filter { it.hasChanges }
                if (changed.isEmpty()) {
                    Toast.makeText(this@MainActivity, R.string.toast_already_clean, Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // 只保留净化后的 URL，丢弃周围所有文字
                val outputText = changed.joinToString("\n") { it.cleanedUrl }
                etInput.setText(outputText)
                etInput.setSelection(outputText.length)
                writeClipboard(outputText)

                // 震动反馈
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    btnClean.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                } else {
                    btnClean.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }

                // 更新统计
                val totalRemovedParams = changed.sumOf { it.removedParams.size }
                stats.recordBatch(changed.size, totalRemovedParams)

                Toast.makeText(this@MainActivity, R.string.toast_copied, Toast.LENGTH_SHORT).show()

                // 渲染 Lens 报告
                renderLensReports(changed)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@MainActivity, getString(R.string.error_processing, e.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeStats() {
        lifecycleScope.launch {
            stats.statsFlow.collect { snapshot ->
                tvStatLinks.text  = snapshot.totalLinks.toString()
                tvStatParams.text = snapshot.totalParams.toString()
            }
        }
    }

    // Clink Lens报告
    private fun renderLensReports(results: List<CleanResult>) {
        containerReports.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val slideAnim = AnimationUtils.loadAnimation(this, R.anim.slide_up_fade_in)

        for (result in results) {
            val card = inflater.inflate(R.layout.item_lens_report, containerReports, false)

            card.findViewById<TextView>(R.id.tvCleanedUrl).text = result.cleanedUrl

            val flowTags = card.findViewById<FlowLayout>(R.id.flowTags)
            for (param in result.removedParams) {
                flowTags.addView(buildParamTag(param))
            }

            containerReports.addView(card)
        }

        // 整体区域淡入滑出
        layoutLens.visibility = View.VISIBLE
        layoutLens.startAnimation(slideAnim)
    }

    
    private fun buildParamTag(param: RemovedParam): TextView {
        val tag = TextView(this)
        val bgRes = if (param.isDanger) R.drawable.bg_tag_danger else R.drawable.bg_tag_normal
        val textColorRes = if (param.isDanger) R.color.text_danger else R.color.text_accent
        tag.background = ContextCompat.getDrawable(this, bgRes)
        tag.setTextColor(ContextCompat.getColor(this, textColorRes))
        tag.textSize = 11f
        val px8 = (8 * resources.displayMetrics.density).toInt()
        val px4 = (4 * resources.displayMetrics.density).toInt()
        tag.setPadding(px8, px4, px8, px4)
        // 显示格式："key · 中文说明"
        tag.text = buildString {
            if (param.isDanger) append("⚠ ")
            append(param.key)
            append(" · ")
            append(param.label)
        }
        tag.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return tag
    }

    // 剪贴板工具
    private fun readClipboard(): String? {
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(this)?.toString()
    }

    private fun writeClipboard(text: String) {
        val clip = ClipData.newPlainText("clink_cleaned", text)
        clipboard.setPrimaryClip(clip)
    }
}
