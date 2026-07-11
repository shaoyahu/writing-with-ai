## MODIFIED Requirements

### Requirement: AiHistoryDao exposes aggregate queries for usage statistics

系统 MUST 在 [AiHistoryDao](file:///Users/bytedance/code/writing-with-ai/app/src/main/java/com/yy/writingwithai/core/data/db/AiHistoryDao.kt) 新增 3 个 `@Query`,输入 `(periodStart: Long, periodEnd: Long)`(epoch millis,半开区间 `[start, end)`),返回 `Flow<List<...>>`:

| 方法 | GROUP BY 字段 | 返回类型字段 |
| --- | --- | --- |
| `aggregateByDay(periodStart, periodEnd)` | `createdAt / 86400000`(本地时区零点) | `(dayBucket: Long, sumInput: Int, sumOutput: Int, sumTotal: Int, count: Int)` |
| `aggregateByOp(periodStart, periodEnd)` | `op` | `(op: String, sumInput: Int, sumOutput: Int, sumTotal: Int, count: Int)` |
| `aggregateByProvider(periodStart, periodEnd)` | `providerId` | `(providerId: String, sumInput: Int, sumOutput: Int, sumTotal: Int, count: Int)` |

索引 `idx_ai_history_createdAt`(`AiHistoryEntity.kt:9`)已存在,**不**新增 migration。
查询**只**聚合 `error IS NULL` 的成功行(避免失败调用污染 token 统计;失败调用有部分 token 估算,语义模糊)。

#### Scenario: aggregateByDay on 7-day window
- **WHEN** `ai_history` 表在 `[periodStart, periodEnd)` 7 天内有 5 行
  (day 1 / day 2 各 2 行,day 3 一行,day 4-7 无)
- **THEN** `aggregateByDay` 返回 2 个 bucket,按 `dayBucket` 升序,
  每个 bucket 的 `sumInput` / `sumOutput` / `sumTotal` / `count` 正确求和

#### Scenario: aggregateByOp excludes failed calls
- **WHEN** 表中含 3 行 EXPAND(成功) + 1 行 EXPAND(`error="Network(500)"`) + 2 行 POLISH(成功)
- **THEN** `aggregateByOp` 返回 2 个 bucket(EXPAND / POLISH),EXPAND 的 `count=3`(不含失败那行)

#### Scenario: aggregateByProvider groups by providerId
- **WHEN** 表中含 deepseek 5 行 + minimax 3 行 + mimo 2 行
- **THEN** `aggregateByProvider` 返回 3 个 bucket,deepseek.sumTotal 最大

#### Scenario: empty window returns empty list
- **WHEN** `[periodStart, periodEnd)` 内 `ai_history` 表无任何行
- **THEN** `aggregateByDay` / `aggregateByOp` / `aggregateByProvider` 均返回空列表

### Requirement: AiUsageRepository composes 3 aggregate flows into UsageSnapshot

系统 MUST 提供 [AiUsageRepository](file:///Users/bytedance/code/writing-with-ai/app/src/main/java/com/yy/writingwithai/core/data/repo/AiUsageRepository.kt)(Hilt 单例),暴露:

```kotlin
sealed class UsagePeriod(val startMs: Long, val endMs: Long) {
    class Last7Days(now: Long = System.currentTimeMillis()) : UsagePeriod(...)
    class Last30Days(now: Long = System.currentTimeMillis()) : UsagePeriod(...)
}
data class UsageSnapshot(
    val byDay: List<DailyUsageBucket>,
    val byOp: List<OpUsageBucket>,
    val byProvider: List<ProviderUsageBucket>,
    val totalTokens: Long
)
fun observeUsage(period: UsagePeriod): Flow<UsageSnapshot>
```

`byDay` 列表**必须填满** `[periodStart.dayBucket, periodEnd.dayBucket)` 连续区间,
缺失日补 `DailyUsageBucket(dayBucket, 0, 0, 0, 0)`(便于图表画连续 X 轴);
`byOp` / `byProvider` 按 `sumTotal` **降序**;
`totalTokens` = `byProvider.sumOf { it.sumTotal }`(或 `byDay.sumOf { it.sumTotal }`,数学等价)。

#### Scenario: 7-day window fills 7 day buckets
- **WHEN** `observeUsage(Last7Days())` 触发,`ai_history` 仅 day 1 / day 3 有调用
- **THEN** `byDay` 列表长度 = 7,day 1 / day 3 是真实值,day 2 / 4-7 是 0 token 占位

#### Scenario: byOp / byProvider sorted by sumTotal descending
- **WHEN** `byOp` 包含 POLISH=5000, EXPAND=8000, SUMMARIZE=3000
- **THEN** `byOp` 顺序为 [EXPAND=8000, POLISH=5000, SUMMARIZE=3000]

#### Scenario: totalTokens equals sum of byProvider
- **WHEN** `byProvider` 含 deepseek=1000, minimax=2000, mimo=500
- **THEN** `snapshot.totalTokens = 3500`

### Requirement: AiUsageScreen renders bar chart + op/provider breakdown

[AiUsageScreen](file:///Users/bytedance/code/writing-with-ai/app/src/main/java/com/yy/writingwithai/feature/aiwriting/usage/AiUsageScreen.kt) MUST 在「我的」tab → 数据管理 → "AI 用量" 入口展示:

- **TopAppBar**:返回箭头 + 标题 `R.string.ai_usage_title`("AI 用量")
- **顶部 7d / 30d FilterChip**:两个 chip 互斥,选中态用 `MaterialTheme.colorScheme.primary`
- **中部条形图**:`UsageBarChart(buckets: List<DailyUsageBucket>)`,
  走 Compose `Canvas` + `drawRect`,**不引第三方图表库**;
  7d 显示 7 柱,30d 显示 30 柱,柱宽根据 chart 宽度自适应,柱高 = `b.sumTotal / max * chartHeight`,
  颜色 = `MaterialTheme.colorScheme.primary`,Y 轴最大数字 label 用 `drawText`
- **下部 1 · 按 op 表格**:每行 `op 名(i18n)` + `总 token` + `调用次数`,降序,空则整 section 隐藏
- **下部 2 · 按 provider 表格**:每行 `provider displayName` + `总 token` + `折算 USD 估算`,
  成本估算走 ViewModel 调 `ProviderCostStore.getCostRate(providerId)`:
  - 双 rate 都是 0 → 显示 `R.string.ai_usage_cost_disabled`("未配置成本费率")
  - 任一 rate 非 0 → 显示 `R.string.ai_usage_cost_fmt.format(estimatedUsd)`
- **空状态**:VM 层若判断 `byProvider.all { it.sumTotal == 0 }`(包括 byDay 全 0),整屏只显示
  `R.string.ai_usage_empty`("还没有 AI 调用记录"),不渲染图表 / 表格

#### Scenario: 7-day bar chart with 5 calls on day 1
- **WHEN** `observeUsage(Last7Days)` 返回 `byDay = [day1=10000, day2..7=0]`
- **THEN** 屏幕渲染 7 柱,day 1 柱高 = `chartHeight`,day 2..7 柱高 = 0;非 day 1 不显示 0 高的柱
  (Canvas `drawRect(height=0)` noop)

#### Scenario: empty state suppresses chart and tables
- **WHEN** `ai_history` 表无任何行
- **THEN** 屏幕**只**显示 `R.string.ai_usage_empty` 文案 + 7d / 30d chips;
  Canvas / op 表 / provider 表**不渲染**(避免出现"满图 0 高的柱"误导)

#### Scenario: cost display when rate is 0
- **WHEN** `ProviderCostStore.getCostRate("deepseek")` 返回 `(0.0, 0.0)`
- **THEN** provider 表的 deepseek 行 cost 列显示 `R.string.ai_usage_cost_disabled`,
  **不**显示 `$0.00`

#### Scenario: cost display when rate is configured
- **WHEN** `ProviderCostStore.getCostRate("deepseek")` 返回 `(0.14, 0.28)` USD per 1k token,
  且 deepseek `sumInput=10000`,`sumOutput=5000`
- **THEN** provider 表的 deepseek 行 cost 列显示 `$2.80`(`10000*0.14 + 5000*0.28` / 1000 = `2.80`)

### Requirement: ProviderCostStore stores cost rate per provider (opt-in)

[ProviderCostStore](file:///Users/bytedance/code/writing-with-ai/app/src/main/java/com/yy/writingwithai/core/prefs/ProviderCostStore.kt) MUST 走**普通 `SharedPreferences`**(文件 `provider_cost`,**非** EncryptedSharedPreferences;
只是数字无敏感信息,UI 文案明确"成本费率仅本机保存")。

API:
- `fun getCostRate(providerId: String): Pair<Double, Double>` — 返回 `(inputUsdPer1k, outputUsdPer1k)`,
  默认 `(0.0, 0.0)`
- `suspend fun setCostRate(providerId: String, input: Double, output: Double)` — 写回 prefs

**默认所有 provider rate = 0**(完全 opt-in);本 change **不**加 UI 入口(M5 polish 加
`SettingsProviderCostScreen`),**不**预填任何官方公开价。

#### Scenario: getCostRate for unset provider returns (0.0, 0.0)
- **WHEN** `ProviderCostStore` 全新初始化,未写过任何 provider rate
- **THEN** `getCostRate("deepseek")` 返回 `(0.0, 0.0)`;UI 据此走"未配置成本费率"分支

#### Scenario: getCostRate after setCostRate
- **WHEN** `setCostRate("deepseek", 0.14, 0.28)` 已调用
- **THEN** `getCostRate("deepseek")` 返回 `(0.14, 0.28)`,`getCostRate("minimax")` 仍返回 `(0.0, 0.0)`

### Requirement: My tab data management section adds AI usage entry

[MyScreen.kt](file:///Users/bytedance/code/writing-with-ai/app/src/main/java/com/yy/writingwithai/feature/my/MyScreen.kt) MUST 在「数据管理」SectionCard 内新增一项:

```kotlin
MyListItem(
    title = stringResource(R.string.ai_usage_title),
    icon = Icons.Filled.QueryStats,
    onClick = { onNavigate(MeTabTarget.AiUsage) }
)
```

插入位置:`数据导入导出` 与 `飞书同步` 之间(数据消费连续性);`MeTabTarget.kt` enum
新增 `AiUsage` 值;[AppNav.kt](file:///Users/bytedance/code/writing-with-ai/app/src/main/java/com/yy/writingwithai/app/AppNav.kt) 新增 `@Serializable data object AiUsage` +
`composable<AiUsage>` 渲染 `AiUsageScreen(onBack = popBackStack)`。

#### Scenario: tap AI usage entry navigates to AiUsageScreen
- **WHEN** 用户点击「数据管理」section 的 "AI 用量" 入口
- **THEN** AppShell 翻译 `MeTabTarget.AiUsage` → 根 NavHost `navigate(AiUsage)`,
  `AiUsageScreen` 显示,TopAppBar 返回箭头可 pop 回 MyScreen