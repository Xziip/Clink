package com.clink.app.engine

import com.clink.app.ClinkApplication
import org.json.JSONObject
import java.util.Locale


data class RemovedParam(
    val key: String,       // 参数键名，如 "utm_source"
    val value: String,     // 原始值
    val label: String,     // 中文说明，如 "UTM 来源渠道"
    val isDanger: Boolean  // true = 高危（设备指纹/广告追踪）
)

// Clink Lens 报告

data class CleanResult(
    val originalUrl: String,        // 原始 URL（提取后未净化）
    val cleanedUrl: String,         // 净化后 URL
    val removedParams: List<RemovedParam>, // 被剔除的参数列表
    val hasChanges: Boolean = removedParams.isNotEmpty()
)

// 净化引擎

object ClinkEngine {

    // 规则容器
    private data class RuleEntry(val label: String, val isDanger: Boolean)
    private data class Rules(
        val userBlacklist: Map<String, RuleEntry>,
        val userWhitelist: Set<String>,
        val builtinBlacklist: Map<String, RuleEntry>,
        val builtinWhitelist: Set<String>
    )

    @Volatile
    private var _rules: Rules? = null

    private val rules: Rules
        get() {
            if (_rules == null) {
                synchronized(this) {
                    if (_rules == null) {
                        _rules = loadRules()
                    }
                }
            }
            return _rules!!
        }

    // 重新加载
    fun reload() {
        synchronized(this) {
            _rules = loadRules()
        }
    }

    // URL 提取
    // 在中文字符、全角标点处截断，不会把后续汉字吃入 URL
    private val URL_REGEX = Regex(
        """https?://[^\s\u3000-\u303F\uff00-\uffef\u4e00-\u9fff\u3400-\u4dbf「」【】《》''""、，。！？；：（）…—～·]+""",
        setOf(RegexOption.IGNORE_CASE)
    )
    private val TRIM_TAIL = Regex("""[)\]'">,]+$""")


    // 从任意文本中提取所有 URL
    fun extractUrls(text: String): List<String> {
        return URL_REGEX.findAll(text)
            .map { it.value.replace(TRIM_TAIL, "") }
            .filter { it.length > 10 }
            .distinct()
            .toList()
    }

    // 净化单条 URL，返回 Lens 报告
    fun clean(rawUrl: String): CleanResult {
        val (scheme, rest) = splitScheme(rawUrl)
        val hashIdx = rest.indexOf('#')
        val (beforeHash, fragment) = if (hashIdx >= 0)
            rest.substring(0, hashIdx) to rest.substring(hashIdx)
        else rest to ""

        val queryIdx = beforeHash.indexOf('?')
        if (queryIdx < 0) return CleanResult(rawUrl, rawUrl, emptyList())

        val base  = beforeHash.substring(0, queryIdx)
        val query = beforeHash.substring(queryIdx + 1)
        val removed = mutableListOf<RemovedParam>()
        val kept    = mutableListOf<String>()

        for (pair in query.split('&')) {
            if (pair.isBlank()) continue
            val eqIdx = pair.indexOf('=')
            val key      = if (eqIdx >= 0) pair.substring(0, eqIdx) else pair
            val value    = if (eqIdx >= 0) pair.substring(eqIdx + 1) else ""
            val keyLower = key.lowercase()
            
            // 优先级：用户白名单 > 用户黑名单 > 内置白名单 > 内置黑名单 > 默认保留
            when {
                rules.userWhitelist.contains(keyLower) -> kept.add(pair)
                rules.userBlacklist.containsKey(keyLower) -> {
                    val entry = rules.userBlacklist[keyLower]!!
                    removed.add(RemovedParam(key, value, entry.label, entry.isDanger))
                }
                rules.builtinWhitelist.contains(keyLower) -> kept.add(pair)
                rules.builtinBlacklist.containsKey(keyLower) -> {
                    val entry = rules.builtinBlacklist[keyLower]!!
                    removed.add(RemovedParam(key, value, entry.label, entry.isDanger))
                }
                else -> kept.add(pair)
            }
        }

        val cleanedQuery = if (kept.isEmpty()) "" else "?" + kept.joinToString("&")
        return CleanResult(rawUrl, "$scheme$base$cleanedQuery$fragment", removed)
    }

    // 提取文本中第一条 URL 并净化，无 URL 返回 null
    fun cleanFirst(text: String): CleanResult? =
        extractUrls(text).firstOrNull()?.let { clean(it) }

    // 提取文本中所有 URL 并全部净化
    fun cleanAll(text: String): List<CleanResult> =
        extractUrls(text).map { clean(it) }

    // 将文本中所有 URL 替换为净化版本，周围文字原样保留 @return (替换后的完整文本, 每条 URL 的 Lens 报告)
    fun replaceAllInText(text: String): Pair<String, List<CleanResult>> {
        var result = text
        val reports = mutableListOf<CleanResult>()
        for (url in extractUrls(text)) {
            val report = clean(url)
            reports.add(report)
            if (report.hasChanges) result = result.replace(report.originalUrl, report.cleanedUrl)
        }
        return result to reports
    }

    private fun splitScheme(url: String): Pair<String, String> {
        val idx = url.indexOf("://")
        return if (idx < 0) ("https://" to url)
        else (url.substring(0, idx + 3) to url.substring(idx + 3))
    }

    // 加载规则（分别保存用户规则和内置规则，用户优先）
    private fun loadRules(): Rules {
        val (builtinBlack, builtinWhite) = loadFromAssets()
        val (userBlack, userWhite) = loadFromUser()

        return Rules(
            userBlacklist = userBlack,
            userWhitelist = userWhite,
            builtinBlacklist = builtinBlack,
            builtinWhitelist = builtinWhite
        )
    }

    private fun loadFromAssets(): Pair<Map<String, RuleEntry>, Set<String>> {
        return try {
            // 根据系统语言选择规则文件
            val locale = Locale.getDefault().language
            val fileName = when (locale) {
                "en" -> "en/clink_rules.json"
                else -> "clink_rules.json"  // 默认中文
            }
            
            val json = ClinkApplication.appContext
                .assets.open(fileName)
                .bufferedReader()
                .use { it.readText() }
            parseRulesJson(json)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyMap<String, RuleEntry>() to emptySet()
        }
    }

    private fun loadFromUser(): Pair<Map<String, RuleEntry>, Set<String>> {
        val file = java.io.File(ClinkApplication.appContext.filesDir, "user_rules.json")
        if (!file.exists()) return emptyMap<String, RuleEntry>() to emptySet()

        return try {
            val json = file.readText()
            parseRulesJson(json)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyMap<String, RuleEntry>() to emptySet()
        }
    }

    private fun parseRulesJson(json: String): Pair<Map<String, RuleEntry>, Set<String>> {
        if (json.isBlank()) return emptyMap<String, RuleEntry>() to emptySet()
        val root = JSONObject(json)
        
        val blackMap = HashMap<String, RuleEntry>()
        if (root.has("blacklist")) {
            val blackArr = root.getJSONArray("blacklist")
            for (i in 0 until blackArr.length()) {
                val obj = blackArr.getJSONObject(i)
                blackMap[obj.getString("key").lowercase()] = RuleEntry(
                    label    = obj.getString("label"),
                    isDanger = obj.optBoolean("danger", false)
                )
            }
        }

        val whiteSet = HashSet<String>()
        if (root.has("whitelist")) {
            val whiteArr = root.getJSONArray("whitelist")
            for (i in 0 until whiteArr.length()) {
                whiteSet.add(whiteArr.getString(i).lowercase())
            }
        }

        return blackMap to whiteSet
    }
}
