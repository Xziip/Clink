package com.clink.app.ui

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.clink.app.R
import com.clink.app.engine.ClinkEngine
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

class UserRulesActivity : AppCompatActivity() {

    enum class RuleType { BLACKLIST, WHITELIST }
    data class UserRule(val key: String, val label: String, val isDanger: Boolean, val type: RuleType)

    private val rules = mutableListOf<UserRule>()
    private lateinit var adapter: RulesAdapter
    private val rulesFile by lazy { File(filesDir, "user_rules.json") }
    
    // 多选模式
    private var isMultiSelectMode = false
    private val selectedPositions = mutableSetOf<Int>()
    
    // UI组件
    private lateinit var listView: ListView
    private lateinit var btnBack: View
    private lateinit var btnExport: View
    private lateinit var btnImport: View
    private lateinit var btnAdd: View
    private lateinit var tvTitle: TextView
    
    // 文件选择器
    private val jsonFilePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importFromJsonFile(it) }
    }
    
    // 文件创建器（用于导出）
    private var pendingExportType = 0
    private val jsonFileCreator = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { exportToFile(it, pendingExportType) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_rules)

        
        btnBack = findViewById(R.id.btnBack)
        btnExport = findViewById(R.id.btnExport)
        btnImport = findViewById(R.id.btnImport)
        btnAdd = findViewById(R.id.btnAdd)
        tvTitle = findViewById<TextView>(R.id.tvTitle)
        listView = findViewById(R.id.lvRules)
        
        btnBack.setOnClickListener { 
            if (isMultiSelectMode) {
                exitMultiSelectMode()
            } else {
                finish()
            }
        }
        btnExport.setOnClickListener { showExportDialog() }
        btnAdd.setOnClickListener { 
            if (isMultiSelectMode) {
                deleteSelectedRules()
            } else {
                showAddDialog()
            }
        }
        btnImport.setOnClickListener { 
            if (isMultiSelectMode) {
                toggleSelectAll()
            } else {
                showImportDialog()
            }
        }

        adapter = RulesAdapter()
        listView.adapter = adapter
        listView.isLongClickable = true
        
        // 单击编辑
        listView.setOnItemClickListener { _, _, position, _ ->
            if (isMultiSelectMode) {
                toggleSelection(position)
            } else {
                showEditDialog(position)
            }
        }
        
        // 长安进入多选
        listView.setOnItemLongClickListener { _, _, position, _ ->
            if (!isMultiSelectMode) {
                enterMultiSelectMode()
                toggleSelection(position)
            }
            true
        }
        
        loadRules()
    }

    private fun loadRules() {
        rules.clear()
        if (rulesFile.exists()) {
            try {
                val json = rulesFile.readText()
                val root = JSONObject(json)
                
                // 加载黑名单
                val blacklist = root.optJSONArray("blacklist") ?: JSONArray()
                for (i in 0 until blacklist.length()) {
                    val obj = blacklist.getJSONObject(i)
                    rules.add(UserRule(
                        key = obj.getString("key"),
                        label = obj.optString("label", ""),
                        isDanger = obj.optBoolean("danger", false),
                        type = RuleType.BLACKLIST
                    ))
                }
                
                // 白名单
                val whitelist = root.optJSONArray("whitelist") ?: JSONArray()
                for (i in 0 until whitelist.length()) {
                    val key = whitelist.getString(i)
                    rules.add(UserRule(
                        key = key,
                        label = "",
                        isDanger = false,
                        type = RuleType.WHITELIST
                    ))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        findViewById<View>(R.id.tvEmpty).visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE
        adapter.notifyDataSetChanged()
    }

    private fun saveRules() {
        try {
            val root = JSONObject()
            
            // 保存黑名单
            val blacklistArray = JSONArray()
            rules.filter { it.type == RuleType.BLACKLIST }.forEach { rule ->
                val obj = JSONObject()
                obj.put("key", rule.key)
                obj.put("label", rule.label)
                obj.put("danger", rule.isDanger)
                blacklistArray.put(obj)
            }
            root.put("blacklist", blacklistArray)
            
            // 保存白名单（仅键值）
            val whitelistArray = JSONArray()
            rules.filter { it.type == RuleType.WHITELIST }.forEach { rule ->
                whitelistArray.put(rule.key)
            }
            root.put("whitelist", whitelistArray)
            
            rulesFile.writeText(root.toString(2))
            
            // 重新加载引擎
            ClinkEngine.reload()
            
            findViewById<View>(R.id.tvEmpty).visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun enterMultiSelectMode() {
        isMultiSelectMode = true
        selectedPositions.clear()
        updateMultiSelectUI()
        adapter.notifyDataSetChanged()
    }

    private fun exitMultiSelectMode() {
        isMultiSelectMode = false
        selectedPositions.clear()
        updateMultiSelectUI()
        adapter.notifyDataSetChanged()
    }

    private fun toggleSelection(position: Int) {
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(position)
        } else {
            selectedPositions.add(position)
        }
        updateMultiSelectUI()
        adapter.notifyDataSetChanged()
    }

    private fun toggleSelectAll() {
        if (selectedPositions.size == rules.size && rules.isNotEmpty()) {
            // 已经全选就取消
            selectedPositions.clear()
        } else {
            // 全选
            selectedPositions.clear()
            selectedPositions.addAll(rules.indices)
        }
        updateMultiSelectUI()
        adapter.notifyDataSetChanged()
    }

    private fun updateMultiSelectUI() {
        if (isMultiSelectMode) {
            tvTitle.text = getString(R.string.multi_select_mode, selectedPositions.size)
            
            // 隐藏导出开关在多选里
            btnExport.visibility = View.GONE
            
            // 将导入按钮改为全选按钮
            btnImport.visibility = View.VISIBLE
            (btnImport as? ImageButton)?.apply {
                val isAllSelected = selectedPositions.size == rules.size && rules.isNotEmpty()
                setImageResource(if (isAllSelected) android.R.drawable.ic_menu_close_clear_cancel else android.R.drawable.ic_menu_add)
                contentDescription = getString(if (isAllSelected) R.string.deselect_all else R.string.select_all)
            }
            
            // 将导入按钮转换为删除按钮
            if (selectedPositions.isNotEmpty()) {
                btnAdd.visibility = View.VISIBLE
                (btnAdd as? ImageButton)?.apply {
                    setImageResource(android.R.drawable.ic_menu_delete)
                    contentDescription = getString(R.string.delete_selected)
                }
            } else {
                btnAdd.visibility = View.GONE
            }
        } else {
            tvTitle.text = getString(R.string.user_rules)
            btnExport.visibility = View.VISIBLE
            btnImport.visibility = View.VISIBLE
            btnAdd.visibility = View.VISIBLE
            
            // 还原
            (btnImport as? ImageButton)?.apply {
                setImageResource(android.R.drawable.ic_menu_upload)
                contentDescription = getString(R.string.import_builtin)
            }
            (btnAdd as? ImageButton)?.apply {
                setImageResource(android.R.drawable.ic_input_add)
                contentDescription = getString(R.string.add)
            }
        }
    }

    private fun deleteSelectedRules() {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.confirm_delete_multiple, selectedPositions.size))
            .setPositiveButton(R.string.delete) { _, _ ->
                val count = selectedPositions.size
                // 反向排序避免索引位移
                selectedPositions.sortedDescending().forEach { position ->
                    rules.removeAt(position)
                }
                exitMultiSelectMode()
                saveRules()
                Toast.makeText(this, getString(R.string.rules_deleted, count), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showEditDialog(position: Int) {
        val rule = rules[position]
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_rule, null)
        val etKey = view.findViewById<EditText>(R.id.etKey)
        val etLabel = view.findViewById<EditText>(R.id.etLabel)
        val cbDanger = view.findViewById<CheckBox>(R.id.cbDanger)
        val rgType = view.findViewById<android.widget.RadioGroup>(R.id.rgType)
        val rbBlacklist = view.findViewById<android.widget.RadioButton>(R.id.rbBlacklist)
        val rbWhitelist = view.findViewById<android.widget.RadioButton>(R.id.rbWhitelist)
        
        // 预填当前值
        etKey.setText(rule.key)
        etLabel.setText(rule.label)
        cbDanger.isChecked = rule.isDanger
        
        if (rule.type == RuleType.WHITELIST) {
            rbWhitelist.isChecked = true
            cbDanger.visibility = View.GONE
            etLabel.visibility = View.GONE
        } else {
            rbBlacklist.isChecked = true
        }
        
        // 隐藏危险框及其标签（用于白名单）
        rgType.setOnCheckedChangeListener { _, checkedId ->
            val isWhitelist = checkedId == R.id.rbWhitelist
            cbDanger.visibility = if (isWhitelist) View.GONE else View.VISIBLE
            etLabel.visibility = if (isWhitelist) View.GONE else View.VISIBLE
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.edit_rule_title)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val key = etKey.text.toString().trim()
                val label = etLabel.text.toString().trim()
                val isWhitelist = rgType.checkedRadioButtonId == R.id.rbWhitelist
                
                if (key.isBlank()) return@setPositiveButton

                // 检查键是否已存在（当前规则除外）
                if (rules.filterIndexed { idx, _ -> idx != position }
                        .any { it.key.equals(key, ignoreCase = true) && it.type == (if (isWhitelist) RuleType.WHITELIST else RuleType.BLACKLIST) }) {
                    Toast.makeText(this, R.string.key_exists, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val updatedRule = if (isWhitelist) {
                    UserRule(key, "", false, RuleType.WHITELIST)
                } else {
                    UserRule(key, label, cbDanger.isChecked, RuleType.BLACKLIST)
                }
                
                rules[position] = updatedRule
                adapter.notifyDataSetChanged()
                saveRules()
                Toast.makeText(this, R.string.rule_updated, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAddDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_rule, null)
        val etKey = view.findViewById<EditText>(R.id.etKey)
        val etLabel = view.findViewById<EditText>(R.id.etLabel)
        val cbDanger = view.findViewById<CheckBox>(R.id.cbDanger)
        val rgType = view.findViewById<android.widget.RadioGroup>(R.id.rgType)
        val rbBlacklist = view.findViewById<android.widget.RadioButton>(R.id.rbBlacklist)
        val rbWhitelist = view.findViewById<android.widget.RadioButton>(R.id.rbWhitelist)
        
        // 隐藏危险框及其标签（用于白名单）
        rgType.setOnCheckedChangeListener { _, checkedId ->
            val isWhitelist = checkedId == R.id.rbWhitelist
            cbDanger.visibility = if (isWhitelist) View.GONE else View.VISIBLE
            etLabel.visibility = if (isWhitelist) View.GONE else View.VISIBLE
            view.findViewById<TextView>(R.id.etLabel)?.parent?.let {
                if (it is View) (it as? ViewGroup)?.findViewById<TextView>(android.R.id.text1)?.visibility = if (isWhitelist) View.GONE else View.VISIBLE
            }
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.add_rule_title)
            .setView(view)
            .setPositiveButton(R.string.add) { _, _ ->
                val key = etKey.text.toString().trim()
                val label = etLabel.text.toString().trim()
                val isWhitelist = rgType.checkedRadioButtonId == R.id.rbWhitelist
                
                if (key.isBlank()) return@setPositiveButton

                if (rules.any { it.key.equals(key, ignoreCase = true) && it.type == (if (isWhitelist) RuleType.WHITELIST else RuleType.BLACKLIST) }) {
                    Toast.makeText(this, R.string.key_exists, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val newRule = if (isWhitelist) {
                    UserRule(key, "", false, RuleType.WHITELIST)
                } else {
                    UserRule(key, label, cbDanger.isChecked, RuleType.BLACKLIST)
                }
                
                rules.add(0, newRule)
                adapter.notifyDataSetChanged()
                saveRules()
                Toast.makeText(this, R.string.rule_added, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteRule(position: Int) {
        AlertDialog.Builder(this)
            .setMessage(R.string.confirm_delete)
            .setPositiveButton(R.string.delete) { _, _ ->
                rules.removeAt(position)
                adapter.notifyDataSetChanged()
                saveRules()
                Toast.makeText(this, R.string.rule_deleted, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun loadBuiltinRules(): List<UserRule> {
        val builtinRules = mutableListOf<UserRule>()
        try {
            // 根据系统语言选择规则文件
            val locale = Locale.getDefault().language
            val fileName = when (locale) {
                "en" -> "en/clink_rules.json"
                else -> "clink_rules.json"  // 默认中文
            }
            
            val json = assets.open(fileName).bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            
            // 加载内置黑名单
            val blacklist = root.optJSONArray("blacklist") ?: JSONArray()
            for (i in 0 until blacklist.length()) {
                val obj = blacklist.getJSONObject(i)
                builtinRules.add(UserRule(
                    key = obj.getString("key"),
                    label = obj.optString("label", ""),
                    isDanger = obj.optBoolean("danger", false),
                    type = RuleType.BLACKLIST
                ))
            }
            
            // 内置白名单
            val whitelist = root.optJSONArray("whitelist") ?: JSONArray()
            for (i in 0 until whitelist.length()) {
                val key = whitelist.getString(i)
                builtinRules.add(UserRule(
                    key = key,
                    label = "",
                    isDanger = false,
                    type = RuleType.WHITELIST
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return builtinRules
    }

    private fun showImportDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.import_source)
            .setItems(arrayOf(
                getString(R.string.import_from_builtin),
                getString(R.string.import_from_file)
            )) { _, which ->
                when (which) {
                    0 -> showBuiltinImportDialog()
                    1 -> jsonFilePicker.launch(arrayOf("application/json", "text/plain", "*/*"))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showBuiltinImportDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_import_builtin, null)
        val listView = view.findViewById<ListView>(R.id.lvBuiltinRules)
        
        val builtinRules = loadBuiltinRules()
        val adapter = BuiltinRulesAdapter(builtinRules)
        listView.adapter = adapter
        
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.import_builtin_title)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .create()
        
        listView.setOnItemClickListener { _, _, position, _ ->
            val rule = builtinRules[position]
            
            // 检查下用户规则里存不存在
            if (rules.any { it.key.equals(rule.key, ignoreCase = true) && it.type == rule.type }) {
                Toast.makeText(this, R.string.already_in_user_rules, Toast.LENGTH_SHORT).show()
                return@setOnItemClickListener
            }
            
            // 加到用户规则里面
            rules.add(0, rule)
            this.adapter.notifyDataSetChanged()
            saveRules()
            
            Toast.makeText(this, R.string.rule_imported, Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun importFromJsonFile(uri: Uri) {
        try {
            val jsonText = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (jsonText.isNullOrBlank()) {
                Toast.makeText(this, R.string.file_read_error, Toast.LENGTH_SHORT).show()
                return
            }

            val root = JSONObject(jsonText)
            var importedCount = 0

            // 导入黑名单
            if (root.has("blacklist")) {
                val blacklist = root.getJSONArray("blacklist")
                for (i in 0 until blacklist.length()) {
                    val obj = blacklist.getJSONObject(i)
                    val key = obj.getString("key")
                    
                    // 已经存在的就跳过
                    if (rules.any { it.key.equals(key, ignoreCase = true) && it.type == RuleType.BLACKLIST }) {
                        continue
                    }
                    
                    rules.add(UserRule(
                        key = key,
                        label = obj.optString("label", ""),
                        isDanger = obj.optBoolean("danger", false),
                        type = RuleType.BLACKLIST
                    ))
                    importedCount++
                }
            }

            // 导入白名单
            if (root.has("whitelist")) {
                val whitelist = root.getJSONArray("whitelist")
                for (i in 0 until whitelist.length()) {
                    val key = whitelist.getString(i)
                    
                    // 有的就跳过
                    if (rules.any { it.key.equals(key, ignoreCase = true) && it.type == RuleType.WHITELIST }) {
                        continue
                    }
                    
                    rules.add(UserRule(
                        key = key,
                        label = "",
                        isDanger = false,
                        type = RuleType.WHITELIST
                    ))
                    importedCount++
                }
            }

            if (importedCount > 0) {
                adapter.notifyDataSetChanged()
                saveRules()
                Toast.makeText(this, getString(R.string.json_import_success, importedCount), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.already_in_user_rules, Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, R.string.json_parse_error, Toast.LENGTH_LONG).show()
        }
    }

    private fun showExportDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.export_source)
            .setItems(arrayOf(
                getString(R.string.export_builtin_only),
                getString(R.string.export_user_only),
                getString(R.string.export_merged)
            )) { _, which ->
                pendingExportType = which
                val fileName = when (which) {
                    0 -> "clink_builtin_rules.json"
                    1 -> "clink_user_rules.json"
                    else -> "clink_merged_rules.json"
                }
                jsonFileCreator.launch(fileName)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun exportToFile(uri: Uri, exportType: Int) {
        try {
            val jsonContent = when (exportType) {
                0 -> exportBuiltinRules()
                1 -> exportUserRules()
                else -> exportMergedRules()
            }

            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(jsonContent.toByteArray())
            }

            Toast.makeText(this, getString(R.string.export_success, uri.lastPathSegment ?: ""), Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, R.string.export_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun exportBuiltinRules(): String {
        val builtinRules = loadBuiltinRules()
        val root = JSONObject()

        val blacklistArray = JSONArray()
        builtinRules.filter { it.type == RuleType.BLACKLIST }.forEach { rule ->
            val obj = JSONObject()
            obj.put("key", rule.key)
            obj.put("label", rule.label)
            obj.put("danger", rule.isDanger)
            blacklistArray.put(obj)
        }
        root.put("blacklist", blacklistArray)

        val whitelistArray = JSONArray()
        builtinRules.filter { it.type == RuleType.WHITELIST }.forEach { rule ->
            whitelistArray.put(rule.key)
        }
        root.put("whitelist", whitelistArray)

        return root.toString(2)
    }

    private fun exportUserRules(): String {
        val root = JSONObject()

        val blacklistArray = JSONArray()
        rules.filter { it.type == RuleType.BLACKLIST }.forEach { rule ->
            val obj = JSONObject()
            obj.put("key", rule.key)
            obj.put("label", rule.label)
            obj.put("danger", rule.isDanger)
            blacklistArray.put(obj)
        }
        root.put("blacklist", blacklistArray)

        val whitelistArray = JSONArray()
        rules.filter { it.type == RuleType.WHITELIST }.forEach { rule ->
            whitelistArray.put(rule.key)
        }
        root.put("whitelist", whitelistArray)

        return root.toString(2)
    }

    private fun exportMergedRules(): String {
        val builtinRules = loadBuiltinRules()
        val root = JSONObject()

        // 合并黑名单（用户优先，去重）
        val mergedBlacklist = mutableMapOf<String, UserRule>()
        
        // 先添加内置黑名单
        builtinRules.filter { it.type == RuleType.BLACKLIST }.forEach { rule ->
            mergedBlacklist[rule.key.lowercase()] = rule
        }
        
        // 用户黑名单覆盖内置黑名单
        rules.filter { it.type == RuleType.BLACKLIST }.forEach { rule ->
            mergedBlacklist[rule.key.lowercase()] = rule
        }

        val blacklistArray = JSONArray()
        mergedBlacklist.values.forEach { rule ->
            val obj = JSONObject()
            obj.put("key", rule.key)
            obj.put("label", rule.label)
            obj.put("danger", rule.isDanger)
            blacklistArray.put(obj)
        }
        root.put("blacklist", blacklistArray)

        // 合并白名单（去重）
        val mergedWhitelist = mutableSetOf<String>()
        
        builtinRules.filter { it.type == RuleType.WHITELIST }.forEach { rule ->
            mergedWhitelist.add(rule.key.lowercase())
        }
        
        rules.filter { it.type == RuleType.WHITELIST }.forEach { rule ->
            mergedWhitelist.add(rule.key.lowercase())
        }

        val whitelistArray = JSONArray()
        mergedWhitelist.forEach { key ->
            whitelistArray.put(key)
        }
        root.put("whitelist", whitelistArray)

        return root.toString(2)
    }

    inner class RulesAdapter : BaseAdapter() {
        override fun getCount(): Int = rules.size
        override fun getItem(position: Int): Any = rules[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(parent!!.context)
                .inflate(R.layout.item_user_rule, parent, false)
            
            val rule = rules[position]
            val tvKey = view.findViewById<TextView>(R.id.tvKey)
            val tvLabel = view.findViewById<TextView>(R.id.tvLabel)
            val btnDelete = view.findViewById<View>(R.id.btnDelete)
            val typeBadge = view.findViewById<TextView>(R.id.typeBadge)
            val dangerBadge = view.findViewById<View>(R.id.dangerBadge)
            val cbSelect = view.findViewById<CheckBox>(R.id.cbSelect)

            tvKey.text = rule.key
            
            // 多选模式
            if (isMultiSelectMode) {
                cbSelect.visibility = View.VISIBLE
                // Clear listener before setting checked state to avoid triggering onChange
                cbSelect.setOnCheckedChangeListener(null)
                cbSelect.isChecked = selectedPositions.contains(position)
                // Set listener after updating checked state
                cbSelect.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedPositions.add(position)
                    } else {
                        selectedPositions.remove(position)
                    }
                    updateMultiSelectUI()
                }
                btnDelete.visibility = View.GONE
            } else {
                cbSelect.visibility = View.GONE
                cbSelect.setOnCheckedChangeListener(null)
                btnDelete.visibility = View.VISIBLE
                btnDelete.setOnClickListener { deleteRule(position) }
            }
            
            // 显示白名单类型标识
            if (rule.type == RuleType.WHITELIST) {
                typeBadge.visibility = View.VISIBLE
                tvLabel.visibility = View.GONE
                dangerBadge.visibility = View.GONE
            } else {
                typeBadge.visibility = View.GONE
                tvLabel.text = rule.label.ifBlank { resources.getString(R.string.no_description) }
                tvLabel.visibility = View.VISIBLE
                dangerBadge.visibility = if (rule.isDanger) View.VISIBLE else View.GONE
            }

            return view
        }
    }

    inner class BuiltinRulesAdapter(private val builtinRules: List<UserRule>) : BaseAdapter() {
        override fun getCount(): Int = builtinRules.size
        override fun getItem(position: Int): Any = builtinRules[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(parent!!.context)
                .inflate(R.layout.item_builtin_rule, parent, false)
            
            val rule = builtinRules[position]
            val tvKey = view.findViewById<TextView>(R.id.tvBuiltinKey)
            val tvLabel = view.findViewById<TextView>(R.id.tvBuiltinLabel)
            val tvType = view.findViewById<TextView>(R.id.tvBuiltinType)
            val tvDanger = view.findViewById<TextView>(R.id.tvBuiltinDanger)

            tvKey.text = rule.key
            
            if (rule.type == RuleType.WHITELIST) {
                tvType.text = resources.getString(R.string.whitelist)
                tvType.setBackgroundColor(0xFF2196F3.toInt()) // Blue
                tvLabel.visibility = View.GONE
                tvDanger.visibility = View.GONE
            } else {
                tvType.text = resources.getString(R.string.blacklist)
                tvType.setBackgroundColor(0xFF666666.toInt()) // Gray
                tvLabel.text = rule.label.ifBlank { resources.getString(R.string.no_description) }
                tvLabel.visibility = View.VISIBLE
                tvDanger.visibility = if (rule.isDanger) View.VISIBLE else View.GONE
            }

            return view
        }
    }
}
