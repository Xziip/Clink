# 贡献指南 / Contributing Guide

非常非常感谢您来支持本项目! 但是为了保证链接净化规则的准确性和可靠性,请在提交新规则前仔细阅读以下指南。

## 提交新追踪参数过滤规则的要求

### 1️⃣ 参数必须满足以下条件之一:
- ✅ **追踪参数**: 用于统计、分析、广告追踪等,删除后不影响页面内容访问
- ✅ **分享参数**: 用于标识分享来源、分享者等,删除后不影响内容显示
- ✅ **营销参数**: UTM、联盟营销、推广位等参数
- ❌ **功能参数**: 删除后会导致页面错误、内容丢失或功能异常的参数（如see_lz 只看楼主）

### 2️⃣ 提交信息要求

**格式要求:**
```json
{ "key": "参数名", "label": "参数说明", "danger": true/false }
```

**必须提供:**
- `key`: 参数的完整名称(区分大小写)
- `label`: 清晰的中/英文说明,注明平台和用途
- `danger`: 
  - `true` - 包含用户隐私/设备指纹/个人标识等敏感信息
  - `false` - 仅用于统计分析,不涉及个人隐私

**示例链接:**
PR里请提供至少 **2个真实链接示例**:
```
原始链接: https://www.bilibili.com/video/BV1GJ411x7h7?参数名=abcdesuwa
删除后: https://www.bilibili.com/video/BV1GJ411x7h7
验证结果: 删除后访问正常,内容一致
```

### 3️⃣ 验证步骤

提交前请务必验证:
1. **删除测试**: 删除该参数后,链接能否正常访问
2. **内容对比**: 删除前后页面内容是否完全一致
3. **功能检查**: 删除后是否影响播放、评论、分P切换等功能
4. **多平台测试**: 在不同设备(PC/移动端)和浏览器测试

### 4️⃣ 不接受的提交

以下情况将被拒绝:
- ❌ 没有提供真实链接示例
- ❌ 参数名称错误或不完整
- ❌ `danger` 标记不准确
- ❌ 删除后会影响页面功能
- ❌ 重复已有的参数
- ❌ 白名单参数(如 `t`, `p` 等)

## 常见平台参数分类参考

### 安全删除的参数特征:
- 包含 `utm_`, `spm`, `from`, `share`, `track`, `trace` 等关键词
- 包含 `_source`, `_id`, `_from` 等后缀
- 广告平台点击ID: `fbclid`, `gclid`, `msclkid` 等
- 设备指纹: `bbid`, `buvid`, `unique_k` 等

### 需要保留的参数(请勿提交):
- **内容ID**: `id`, `v`, `aid`, `bvid`, `goods_id` 等
- **功能参数**: `p`(分P), `t`(时间戳), `answer`(回答ID), `see_lz`(只看楼主)
- **播放列表**: `list`, `playlist` 等
- **SKU规格**: `skuId` 等(可选保留)

## 📝 提交流程

1. **Fork** 本项目
2. 修改 `clink_rules.json` 和 `en/clink_rules.json`
3. 按 **字母顺序** 插入新参数
4. 在 Pull Request 中填写:
   ```
   参数名: xxx
   用途: xxx平台的xxx追踪参数
   测试链接: 
   - 原始: xxx
   - 净化: xxx
   验证结果: 删除后访问正常
   ```
5. 等待审核

## ⚠️ 注意事项

- 提交前先查看现有规则,避免重复！
- 英文版标签要求准确翻译,不能机翻(实在不会问问AI)
- 同一平台的参数建议批量提交
- 有疑问请先开 Issue 讨论

---

**维护者保留最终审核权,所有提交需经过测试验证后方可合并。**

感谢您帮助完善链接净化规则! 🙏 
谢谢喵~

---

# Contributing Guide / 贡献指南

Thank you so much for supporting this project! To ensure the accuracy and reliability of link cleaning rules, please read the following guidelines carefully before submitting new rules.

## Requirements for Submitting New Tracking Parameter Filter Rules

### 1️⃣ Parameters must meet one of the following conditions:
- ✅ **Tracking Parameters**: Used for statistics, analysis, ad tracking, etc., removal does not affect page content access
- ✅ **Sharing Parameters**: Used to identify share source, sharer, etc., removal does not affect content display
- ✅ **Marketing Parameters**: UTM, affiliate marketing, promotion IDs, etc.
- ❌ **Functional Parameters**: Removal causes page errors, content loss, or functional issues (e.g., `see_lz` for "show OP only")

### 2️⃣ Submission Information Requirements

**Format Requirements:**
```json
{ "key": "parameter_name", "label": "parameter description", "danger": true/false }
```

**Must Provide:**
- `key`: Complete parameter name (case-sensitive)
- `label`: Clear description in Chinese/English, indicating platform and purpose
- `danger`: 
  - `true` - Contains user privacy/device fingerprint/personal identifiers or other sensitive information
  - `false` - Only for statistical analysis, no personal privacy involved

**Example Links:**
Please provide at least **2 real link examples** in your PR:
```
Original: https://www.bilibili.com/video/BV1GJ411x7h7?parameter_name=abcdesuwa
Cleaned: https://www.bilibili.com/video/BV1GJ411x7h7
Verification: Accessible after removal, content identical
```

### 3️⃣ Verification Steps

Please verify before submission:
1. **Removal Test**: Can the link be accessed normally after removing this parameter?
2. **Content Comparison**: Is the page content completely identical before and after removal?
3. **Function Check**: Does removal affect playback, comments, multi-part switching, etc.?
4. **Multi-platform Test**: Test on different devices (PC/mobile) and browsers

### 4️⃣ Rejected Submissions

The following will be rejected:
- ❌ No real link examples provided
- ❌ Incorrect or incomplete parameter names
- ❌ Inaccurate `danger` marking
- ❌ Affects page functionality after removal
- ❌ Duplicate existing parameters
- ❌ Whitelist parameters (e.g., `t`, `p`, etc.)

## Common Platform Parameter Classification Reference

### Safe-to-Remove Parameter Characteristics:
- Contains keywords like `utm_`, `spm`, `from`, `share`, `track`, `trace`
- Contains suffixes like `_source`, `_id`, `_from`
- Ad platform click IDs: `fbclid`, `gclid`, `msclkid`, etc.
- Device fingerprints: `bbid`, `buvid`, `unique_k`, etc.

### Parameters to Keep (Do Not Submit):
- **Content IDs**: `id`, `v`, `aid`, `bvid`, `goods_id`, etc.
- **Functional Parameters**: `p` (multi-part), `t` (timestamp), `answer` (answer ID), `see_lz` (show OP only)
- **Playlists**: `list`, `playlist`, etc.
- **SKU Specifications**: `skuId`, etc. (optional to keep)

## 📝 Submission Process

1. **Fork** this project
2. Modify both `clink_rules.json` and `en/clink_rules.json`
3. Insert new parameters in **alphabetical order**
4. Fill in your Pull Request:
   ```
   Parameter: xxx
   Purpose: xxx tracking parameter for xxx platform
   Test Links: 
   - Original: xxx
   - Cleaned: xxx
   Verification: Accessible after removal
   ```
5. Wait for review

## ⚠️ Notes

- Check existing rules before submitting to avoid duplicates!
- Chinese labels require accurate translation, not machine translation (ask AI if you really don't know)
- Parameters from the same platform are recommended to be submitted in batches
- Open an Issue to discuss if you have questions

---

**Maintainers reserve the final right of review. All submissions must pass testing and verification before merging.**

Thank you for helping improve link cleaning rules! 🙏
Thank you meow~
