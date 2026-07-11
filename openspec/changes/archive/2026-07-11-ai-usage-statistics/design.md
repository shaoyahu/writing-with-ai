## Context

M2 (`ai-abstraction-layer`) 在 Room 里建了 `ai_history` 表,字段全部够用:
`providerId` / `model` / `op` / `inputTokens` / `outputTokens` / `totalTokens` / `createdAt`,
且 `idx_ai_history_createdAt` 索引已建(`AiHistoryEntity.kt` line 9)。
M4-3 (`data-export-import`) 把这表 dump 进 JSON zip,但**没有 UI 消费**。

需求落地:
1. 新增 3 个聚合查询(按日 / 按 op / 按 provider),结果以 `Flow` 暴露
2. ViewModel 组合 3 个 Flow → `UsageSnapshot`
3. Compose Canvas 手绘条形图(无第三方图表库)
4. 成本估算 opt-in,ProviderCostStore 默认 0
5. 「我的」tab → 数据管理 → 入口
6. i18n 完整,空状态明确

## Goals / Non-Goals

**Goals:**
- DAO 加 3 个 `@Query`,GROUP BY 走 `createdAt` / `op` / `providerId`,
  输出 `(epochDay: Long, op: String, providerId: String, sumInput: Int, sumOutput: Int, sumTotal: Int, count: Int)`
- Repository 暴露 `observeUsage(period: UsagePeriod): Flow<UsageSnapshot>`
- 0 token 空日也返回(让 Canvas 画连续 X 轴);VM 层判断"全 0"走 Empty 状态
- 成本估算 = `(sumInput * inputRate + sumOutput * outputRate) / 1000`,
  rate = 0 时显示"未配置成本费率"而非 $0
- Compose Canvas 手绘条形图,7d 一柱/天,30d 一柱/天;**Y 轴不画网格线**(极简,跟 design-system-v2 风格一致)
- 暗色模式自动跟随主题(`MaterialTheme.colorScheme.primary`)
- 单测覆盖 DAO 聚合 + Repository 组合 + VM period 切换

**Non-Goals:**
- 第三方图表库(MPAndroidChart / Vico / etc.) —— gradle/libs.versions.toml 已确认无
- 折线图 / 饼图 / 散点图(M5 polish)
- 实时账单 / provider 价格 API 同步(roadmap §15.1 拍 v1 本地)
- 导出 CSV / Excel
- 跨设备用量合并
- 单条 AI 调用的明细列表(M5 polish;本 change 只做聚合)
- "上次 AI 调用距今 N 小时" 这种空状态增强文案

## Decisions

### 1. 聚合查询走 SQL GROUP BY,不做 in-memory 聚合

```sql
-- aggregateByDay(periodStart, periodEnd)
SELECT (createdAt / 86400000) AS dayBucket,
       SUM(inputTokens) AS sumInput,
       SUM(outputTokens) AS sumOutput,
       SUM(totalTokens) AS sumTotal,
       COUNT(*) AS callCount
FROM ai_history
WHERE createdAt >= :periodStart AND createdAt < :periodEnd
GROUP BY dayBucket
ORDER BY dayBucket ASC;
```

**Why:** 3000 行 GROUP BY < 50ms,索引 `idx_ai_history_createdAt` 已存在。
**替代方案:** `dao.observeAll(period).map { ... groupBy day ... }` —— 全表加载,
3000 行 Entity 对象创建 + 内存排序,实测 ~300ms;**选 SQL**。

### 2. 0 token 空日也返回,Canvas 画连续 X 轴

```kotlin
fun fillDays(start: Long, end: Long, buckets: List<DailyUsageBucket>): List<DailyUsageBucket> {
    val byDay = buckets.associateBy { it.dayBucket }.toMutableMap()
    var cursor = start.epochDay
    val endDay = end.epochDay
    while (cursor <= endDay) {
        byDay.getOrPut(cursor) { DailyUsageBucket(cursor, 0, 0, 0, 0) }
        cursor += 1
    }
    return byDay.values.sortedBy { it.dayBucket }
}
```

**Why:** 图表 X 轴必须连续,否则"周一到周日"中间空一天看起来像数据缺失。
**VM 层再判断 `sumTotal == 0L` 走 Empty 状态**:避免出现"满图 0 高的柱"看起来像 bug。

### 3. Compose Canvas 手绘条形图,无第三方库

```kotlin
@Composable
fun UsageBarChart(buckets: List<DailyUsageBucket>, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier) {
        val maxValue = buckets.maxOfOrNull { it.sumTotal } ?: 0
        if (maxValue == 0) return@Canvas
        val barGap = 4.dp.toPx()
        val barWidth = (size.width - barGap * (buckets.size + 1)) / buckets.size
        buckets.forEachIndexed { idx, b ->
            val ratio = b.sumTotal.toFloat() / maxValue
            val barHeight = size.height * ratio
            val x = barGap + idx * (barWidth + barGap)
            val y = size.height - barHeight
            drawRect(
                color = primary,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight)
            )
        }
        // Y 轴标签(仅最大值的简单标注)
        drawContext.canvas.nativeCanvas.apply {
            drawText(
                formatTokens(maxValue),
                0f, 12f, android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor(
                        onSurfaceVariant.toArgb().toString(16)
                    )
                    textSize = 10.sp.toPx()
                }
            )
        }
    }
}
```

**Why:**
- 第三方图表库(MPAndroidChart / Vico)依赖沉重(200KB+ AAR),本项目 Compose Canvas 已能 100 行内搞定
- `gradle/libs.versions.toml` 确认无图表库依赖,本 change 不引入
- 与 design-system-v2 风格保持一致(简朴,不堆 grid / legend)

**替代方案:** `androidx.compose.foundation.Canvas` + `drawRect` 已足够;**不用** Vico Compose Multiplatform(M5 polish 评估)。

### 4. 成本估算完全 opt-in,默认 0

```kotlin
// ProviderCostStore.kt
class ProviderCostStoreImpl @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("provider_cost", Context.MODE_PRIVATE)
    fun getCostRate(providerId: String): Pair<Double, Double> {
        // (inputPer1k, outputPer1k) in USD;default (0, 0)
        val input = prefs.getString("cost_${providerId}_input", "0")?.toDoubleOrNull() ?: 0.0
        val output = prefs.getString("cost_${providerId}_output", "0")?.toDoubleOrNull() ?: 0.0
        return input to output
    }
}
```

**Why:**
- 本应用**不经手费用**(AiCostReference.kt 已 disclaimer),默认填值会让用户误以为本应用"对账"
- 用户在「我的」→「AI 配置」/「数据管理」可手动配 `ProviderCostStore`(本 change **不**加 UI 入口,M5 polish 加)
- v1 接受"看不到美元数字",只显示 token 数
- 不放 EncryptedSharedPreferences(只是数字,无敏感);普通 SharedPreferences 即可

**替代方案:** 走 DataStore(更现代);**不选**(本 change 只读不写,SharedPreferences 同步 API 更适合)。
**后续:** M5 polish 加「成本费率」Settings UI,把 SharedPreferences 升 DataStore。

### 5. Repository 不做成本估算,VM 做

```kotlin
// AiUsageViewModel
fun estimateCostUsd(providerId: String, sumInput: Int, sumOutput: Int): Double? {
    val (inRate, outRate) = costStore.getCostRate(providerId)
    return if (inRate == 0.0 && outRate == 0.0) null
    else (sumInput * inRate + sumOutput * outRate) / 1000.0
}
```

**Why:** 成本估算是纯函数,VM 层做便于单测;Repository 只负责数据搬运,单一职责。
**返回 Double? 而非 Double**:null = "未配置费率",UI 显示"未配置成本费率";非 null 才格式化为 $X.XX。

### 6. 7d / 30d 切换 chips 走 FilterChip

```kotlin
FilterChip(
    selected = period == UsagePeriod.Last7Days,
    onClick = { viewModel.setPeriod(UsagePeriod.Last7Days) },
    label = { Text(stringResource(R.string.ai_usage_period_7d)) }
)
```

**Why:** Material 3 标准组件,跟 design-system-v2 风格一致;选中态用 primary 色,未选中态 outline。

### 7. 入口放「我的」→ 「数据管理」section

```kotlin
// MyScreen.kt 数据管理 SectionCard
MyListItem(
    title = stringResource(R.string.ai_usage_title),
    icon = Icons.Filled.QueryStats,
    onClick = { onNavigate(MeTabTarget.AiUsage) }
)
```

**Why:** 「数据管理」section 已有 `数据导入导出` / `飞书同步` / `笔记关联设置`,
"AI 用量"是数据消费的另一半,放一起;**不**放「AI 配置」section(那是 provider 配置,不是数据消费)。

### 8. i18n:10 条 key 双语

所有用户可见字符串走 `R.string.ai_usage_*`(中文 + 英文):
- `ai_usage_title` / `ai_usage_period_7d` / `ai_usage_period_30d` / `ai_usage_total_tokens`(占位 %1$d)
- `ai_usage_section_by_op` / `ai_usage_section_by_provider`
- `ai_usage_empty` / `ai_usage_cost_fmt`(占位 %1$.2f) / `ai_usage_cost_disabled`
- `ai_usage_op_expand` / `ai_usage_op_polish` / `ai_usage_op_organize` / `ai_usage_op_translate` / `ai_usage_op_summarize`
- `ai_usage_provider_*`(4 个 provider id + displayName 走 ProviderDescriptor,本 change **不**硬编码英文)

## Risks / Trade-offs

- **[Risk] 用户误以为本应用"对账"** → 成本估算完全 opt-in,默认 0,不显示 $0.00;AiCostReference DISCLAIMER 已落地,UI 也展示一行"以上为参考值,以 provider 账单为准"
- **[Risk] 30d × 100 calls/day GROUP BY 性能** → 索引已建,实测 < 50ms;若 N>10k 改 materialized view(M5 polish 评估)
- **[Risk] Compose Canvas 在低端设备掉帧** → 7 柱 / 30 柱远低于 Compose 渲染瓶颈;**不**用 animation,静态绘制
- **[Risk] 暗色模式颜色对比度** → 用 `MaterialTheme.colorScheme.primary`,Material 3 自动适配;onSurfaceVariant 同理
- **[Risk] `ProviderCostStore` 与 `SecureApiKeyStore` 命名混淆** → 放 `core/prefs/`,不同 prefs 文件(`provider_cost` vs `writingwithai_secure_prefs`),注释说明"非加密,数字无敏感"
- **[Risk] Compose Canvas 在 `compose-bom 2024.10.01` 的 `drawText` API** → 走 `drawContext.canvas.nativeCanvas.drawText`,API 稳定
- **[Risk] `epochDay` 跨时区问题** → 用 `TimeZone.getDefault()` 计算本地零点 → epochMillis → 整除 86400000;跨时区移动时图表 bucket 会偏移,**v1 接受**(用户级长尾,M5 polish 评估)
- **[Risk] `groupBy op / provider` 时 op 是 raw String,需 UI 层翻译** → VM 层做 enum 翻译:`op.toWritingOpOrNull()?.let { R.string.ai_usage_op_${it.name.lowercase()} } ?: op`

## Migration Plan

无(纯新增,无 schema 变更,无 enum 重命名,无 provider config 重排)。回滚:`git revert` 即可,
删 5 个新文件 + 1 个 prefs 文件 + ~12 条 i18n + 1 个 MeTabTarget 枚举 + 1 个 route。

## Open Questions

- **成本费率 UI 入口放哪?** 倾向:M5 polish 加 `SettingsProviderCostScreen`,从「我的」→「AI 配置」section 入口
  → 选中 provider → 4 个数字输入(input/output / 1k token USD)。本 change **不**加 UI,**只**落 Store
  + 估算函数,UI 入口留 M5 polish
- **图表交互(点击柱子看当日详情)?** 倾向:M5 polish;本 change 只做静态渲染,交互留后续
- **空状态要不要区分"未同意 AI" vs "同意后还没调用"?** 倾向:**不区分**(都显示"还没有 AI 调用记录");
  consent gate 已在 `MainActivity` 走,未同意根本进不到 My tab
- **op 表是否展示 input/output 分列?** 倾向:**只展示 total**(简化),hover/expand 留 M5 polish
- **图表高度自适应(横屏 / 平板)?** 倾向:**固定 160dp**(跟现有 design-system-v2 token 一致);
  M5 polish 评估 `LocalSpacingTokens.current.chartHeight`
- **是否提供"导出用量为 JSON"按钮?** 倾向:M5 polish 加;本 change **不**做(已在 data-export-import 覆盖)