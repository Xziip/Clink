package com.clink.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
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

class ShareActivity : ComponentActivity() {

    private lateinit var layoutSheet: View
    private lateinit var tvStatus: TextView
    private lateinit var tvCountdown: TextView
    private lateinit var tvCleanedUrl: TextView
    private lateinit var flowRemovedTags: FlowLayout
    private lateinit var btnClose: Button
    private lateinit var btnReshare: Button

    private var cleanedText: String? = null
    private var countDownTimer: CountDownTimer? = null
    private val stats by lazy { StatsManager.get() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_share)

        bindViews()

        // 让底部 sheet 自动为导航栏留出内边距
        ViewCompat.setOnApplyWindowInsetsListener(layoutSheet) { v, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val base = (24 * resources.displayMetrics.density + 0.5f).toInt()
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, base + navBar.bottom)
            insets
        }

        setupListeners()

        // 卡片从底部滑入
        layoutSheet.startAnimation(AnimationUtils.loadAnimation(this, R.anim.sheet_slide_up))

        // 从 Intent 中提取文本并处理
        val sharedText = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (sharedText.isNullOrBlank()) {
            finish()
            return
        }
        processText(sharedText)
    }

    private fun bindViews() {
        layoutSheet     = findViewById(R.id.layoutSheet)
        tvStatus        = findViewById(R.id.tvStatus)
        tvCountdown     = findViewById(R.id.tvCountdown)
        tvCleanedUrl    = findViewById(R.id.tvCleanedUrl)
        flowRemovedTags = findViewById(R.id.flowRemovedTags)
        btnClose        = findViewById(R.id.btnClose)
        btnReshare      = findViewById(R.id.btnReshare)
    }

    private fun setupListeners() {
        // 点击遮罩关闭
        findViewById<View>(R.id.rootScrim).setOnClickListener {
            finishWithAnim()
        }
        // 卡片拦截点击，防止穿透到遮罩
        layoutSheet.setOnClickListener { /* 拦截 */ }

        btnClose.setOnClickListener { finishWithAnim() }

        btnReshare.setOnClickListener {
            val text = cleanedText ?: return@setOnClickListener
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(intent, null))
            finishWithAnim()
        }
    }

    private fun processText(raw: String) {
        lifecycleScope.launch {
            val result: CleanResult? = withContext(Dispatchers.IO) {
                ClinkEngine.cleanFirst(raw)
            }

            if (result == null) {
                // 没有检测到 URL，直接 finish
                finish()
                return@launch
            }

            // 写入剪贴板
            val textToWrite = if (result.hasChanges) result.cleanedUrl else raw
            writeClipboard(textToWrite)
            cleanedText = textToWrite

            // 更新统计
            if (result.hasChanges) {
                stats.recordBatch(1, result.removedParams.size)
            }

            // 更新 UI
            if (result.hasChanges) {
                tvStatus.text = getString(R.string.share_done)
                tvCleanedUrl.text = result.cleanedUrl
                tvCleanedUrl.visibility = View.VISIBLE
                renderRemovedTags(result.removedParams)
            } else {
                tvStatus.text = getString(R.string.toast_already_clean)
            }

            btnReshare.visibility = View.VISIBLE
            startCountdown()
        }
    }

    private fun renderRemovedTags(params: List<RemovedParam>) {
        if (params.isEmpty()) return
        flowRemovedTags.visibility = View.VISIBLE
        for (param in params) {
            val tag = TextView(this)
            val bgRes = if (param.isDanger) R.drawable.bg_tag_danger else R.drawable.bg_tag_normal
            val colorRes = if (param.isDanger) R.color.text_danger else R.color.text_accent
            tag.background = ContextCompat.getDrawable(this, bgRes)
            tag.setTextColor(ContextCompat.getColor(this, colorRes))
            tag.textSize = 11f
            val px8 = (8 * resources.displayMetrics.density).toInt()
            val px4 = (4 * resources.displayMetrics.density).toInt()
            tag.setPadding(px8, px4, px8, px4)
            tag.text = buildString {
                if (param.isDanger) append("⚠ ")
                append(param.key)
                append(" · ")
                append(param.label)
            }
            flowRemovedTags.addView(tag)
        }
    }

    // 5秒退出
    private fun startCountdown(seconds: Int = 5) {
        tvCountdown.visibility = View.VISIBLE
        countDownTimer = object : CountDownTimer(seconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val s = (millisUntilFinished / 1000).toInt() + 1
                tvCountdown.text = getString(R.string.share_auto_close, s)
            }
            override fun onFinish() {
                finishWithAnim()
            }
        }.start()
    }

    private fun finishWithAnim() {
        countDownTimer?.cancel()
        layoutSheet.startAnimation(
            AnimationUtils.loadAnimation(this, R.anim.sheet_slide_down).also {
                it.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
                    override fun onAnimationStart(a: android.view.animation.Animation?) = Unit
                    override fun onAnimationRepeat(a: android.view.animation.Animation?) = Unit
                    override fun onAnimationEnd(a: android.view.animation.Animation?) { finish() }
                })
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }

    private fun writeClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("clink_cleaned", text))
    }
}
