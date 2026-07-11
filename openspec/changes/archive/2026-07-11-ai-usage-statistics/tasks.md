# Tasks

> 总工时估算:1 天 DAO + 1 天 Repository/Store + 2 天 Screen/Canvas + 0.5 天 entry point + 1 天测试 = **5.5 工作日**
>
> 实施顺序:DAO → Repository → Store → VM → Screen → Canvas → entry point → i18n → 单测 → verify。
> 每步完成后跑 `./gradlew :app:compileDebugKotlin` 验证编译,提交前跑完整 `:app:assembleDebug :app:ktlintCheck :app:testDebugUnitTest`。

## 1. DAO 聚合查询(0.5-1 天)

- [x] 1.1 在 `app/src/main/java/com/yy/writingwithai/core/data/db/AiHistoryDao.kt` 新增 3 个 `@Query`
  - `aggregateByDay(periodStart, periodEnd): Flow<List<DailyUsageBucket>>`,GROUP BY `(createdAt / 86400000)`,过滤 `error IS NULL`
  - `aggregateByOp(periodStart, periodEnd): Flow<List<OpUsageBucket>>`,GROUP BY `op`,过滤 `error IS NULL`
  - `aggregateByProvider(periodStart, periodEnd): Flow<List<ProviderUsageBucket>>`,GROUP BY `providerId`,过滤 `error IS NULL`
- [x] 1.2 新建 `app/src/main/java/com/yy/writingwithai/core/data/db/AggregateModels.kt`
  - `data class DailyUsageBucket(val dayBucket: Long, val sumInput: Int, val sumOutput: Int, val sumTotal: Int, val count: Int)`
  - `data class OpUsageBucket(val op: String, val sumInput: Int, val sumOutput: Int, val sumTotal: Int, val count: Int)`
  - `data class ProviderUsageBucket(val providerId: String, val sumInput: Int, val sumOutput: Int, val sumTotal: Int, val count: Int)`
  - 全部 `@Immutable` 注解(Compose 用)
- [x] 1.3 验证 Room 编译成功(`./gradlew :app:compileDebugKotlin`)

## 2. Repository + Store(0.5-1 天)

- [x] 2.1 新建 `app/src/main/java/com/yy/writingwithai/core/data/repo/AiUsageRepository.kt`(Hilt 单例)
  - 注入 `AiHistoryDao`
  - API `observeUsage(period: UsagePeriod): Flow<UsageSnapshot>`
  - `UsagePeriod` sealed class: `Last7Days` / `Last30Days`,构造时计算 `periodStart` / `periodEnd`(本地时区)
  - 用 `combine(byDayFlow, byOpFlow, byProviderFlow) { ... }` 组合 3 个 flow
  - `byDay` 填满连续 day bucket(`fillDays` helper),`byOp` / `byProvider` 按 `sumTotal` 降序
- [x] 2.2 新建 `app/src/main/java/com/yy/writingwithai/core/data/repo/UsagePeriod.kt`
  - `sealed class UsagePeriod(val startMs: Long, val endMs: Long)`
  - `data class Last7Days(val nowMs: Long = System.currentTimeMillis()) : UsagePeriod(...)`
  - `data class Last30Days(val nowMs: Long = System.currentTimeMillis()) : UsagePeriod(...)`
  - `private fun startOfTodayInMillis(nowMs: Long): Long`(本地时区零点)
- [x] 2.3 新建 `app/src/main/java/com/yy/writingwithai/core/data/repo/UsageSnapshot.kt`
  - `data class UsageSnapshot(val byDay: List<DailyUsageBucket>, val byOp: List<OpUsageBucket>, val byProvider: List<ProviderUsageBucket>, val totalTokens: Long)`
- [x] 2.4 新建 `app/src/main/java/com/yy/writingwithai/core/prefs/ProviderCostStore.kt`
  - `interface ProviderCostStore { fun getCostRate(providerId: String): Pair<Double, Double>; suspend fun setCostRate(providerId: String, input: Double, output: Double) }`
  - `@Singleton class ProviderCostStoreImpl @Inject constructor(@ApplicationContext context: Context) : ProviderCostStore`
  - 走普通 `getSharedPreferences("provider_cost", MODE_PRIVATE)`,默认 (0.0, 0.0)
- [x] 2.5 在 `core/prefs/PrefsModule.kt` 加 `@Provides @Singleton fun provideProviderCostStore(impl: ProviderCostStoreImpl): ProviderCostStore = impl`

## 3. ViewModel(0.5 天)

- [x] 3.1 新建 `app/src/main/java/com/yy/writingwithai/feature/aiwriting/usage/AiUsageViewModel.kt`
  - `@HiltViewModel class AiUsageViewModel @Inject constructor(repo: AiUsageRepository, costStore: ProviderCostStore) : ViewModel()`
  - `private val _period = MutableStateFlow<UsagePeriod>(UsagePeriod.Last7Days())`
  - `val uiState: StateFlow<AiUsageUiState> = _period.flatMapLatest { repo.observeUsage(it).map { ... } }`
  - `AiUsageUiState` sealed:`Loading` / `Empty` / `Ready(snapshot, period, costByProvider: Map<String, Double?>)`
  - `fun setPeriod(p: UsagePeriod)` 切换 → flatMapLatest 重拉
  - `fun estimateCostUsd(providerId, sumInput, sumOutput): Double?` 调 costStore
- [x] 3.2 验证编译(`./gradlew :app:compileDebugKotlin`)

## 4. Screen + Compose Canvas 条形图(1-1.5 天)

- [x] 4.1 新建 `app/src/main/java/com/yy/writingwithai/feature/aiwriting/usage/UsageBarChart.kt`
  - `@Composable fun UsageBarChart(buckets: List<DailyUsageBucket>, modifier: Modifier = Modifier)`
  - 用 `Canvas` + `drawRect`,柱数 = `buckets.size`,柱宽 = `(size.width - barGap*(n+1)) / n`
  - 柱色 = `MaterialTheme.colorScheme.primary`,无网格线
  - Y 轴最大数字 label 用 `drawContext.canvas.nativeCanvas.drawText`
  - height = `160.dp` 固定(design-system-v2 token 一致)
- [x] 4.2 新建 `app/src/main/java/com/yy/writingwithai/feature/aiwriting/usage/AiUsageScreen.kt`
  - Scaffold + TopAppBar(返回箭头 + 标题)
  - `LazyColumn` 包含:FilterChip 行 + Canvas + op 表 + provider 表
  - 7d / 30d chips 走 `FilterChip`,互斥,VM.setPeriod 切换
  - Empty 状态:`if (state is Empty) { Text(stringResource(R.string.ai_usage_empty)) } else { ... }`
  - Op 表:`op 名` 走 `WritingOp.valueOf(op).name` → 翻译;调用次数走 `(count)` 副标题
  - Provider 表:`provider displayName` 走 `ProviderDescriptor.displayName`(需注入 `ProviderPrefsStore`),cost 列按 rate 0/非 0 分支
- [x] 4.3 新建 `AiUsageScreenPreview`(同文件底部,`@Preview` + `WritingAppTheme` + `MyScreenPreview` 风格)

## 5. Entry point(0.25 天)

- [x] 5.1 `MeTabTarget.kt` 加 `AiUsage` enum
- [x] 5.2 `MyScreen.kt`「数据管理」SectionCard 加"MyListItem(title=ai_usage_title, icon=QueryStats, onClick=MeTabTarget.AiUsage)"
- [x] 5.3 `AppNav.kt` 加 `@Serializable data object AiUsage` + `composable<AiUsage>` 渲染 `AiUsageScreen(onBack = popBackStack)`
- [x] 5.4 编译验证(`./gradlew :app:compileDebugKotlin`)

## 6. i18n(0.25 天)

- [x] 6.1 `app/src/main/res/values/strings.xml` 加 12 条新 key
  - `ai_usage_title` = "AI 用量"
  - `ai_usage_period_7d` = "近 7 天"
  - `ai_usage_period_30d` = "近 30 天"
  - `ai_usage_total_tokens` = "总消耗 %1$d token"
  - `ai_usage_section_by_op` = "按操作"
  - `ai_usage_section_by_provider` = "按模型服务"
  - `ai_usage_empty` = "还没有 AI 调用记录"
  - `ai_usage_cost_fmt` = "约 $%1$.2f"
  - `ai_usage_cost_disabled` = "未配置成本费率"
  - `ai_usage_op_expand` / `ai_usage_op_polish` / `ai_usage_op_organize` / `ai_usage_op_translate` / `ai_usage_op_summarize` = "扩写" / "润色" / "整理" / "翻译" / "摘要"
- [x] 6.2 `app/src/main/res/values-en/strings.xml` 同步加英文版
- [x] 6.3 `MyScreenPreview` 跑通,确认新入口不破坏现有 My tab 视觉

## 7. 单测(1 天)

- [x] 7.1 `app/src/test/.../core/data/db/AiHistoryDaoAggregateTest.kt`(Robolectric)
  - 插入 5 行 + 1 行失败(`error` 非 null) → aggregateByDay / aggregateByOp / aggregateByProvider 行为正确(失败行被过滤)
  - 空区间 → 返回空列表
  - 用 `Room.inMemoryDatabaseBuilder` + Turbine `.test { expectItem() }`
- [x] 7.2 `app/src/test/.../core/data/repo/AiUsageRepositoryTest.kt`
  - Mock `AiHistoryDao`,3 个 Flow 返回 fixture → `observeUsage(Last7Days)` 输出 `UsageSnapshot`
  - 验证 `byDay` 填满 7 个 bucket(缺失日 0 token)
  - 验证 `byOp` / `byProvider` 降序
- [x] 7.3 `app/src/test/.../feature/aiwriting/usage/AiUsageViewModelTest.kt`
  - Mock Repository + CostStore
  - `setPeriod(Last30Days)` → StateFlow 切换 → 新 snapshot
  - `estimateCostUsd` rate 全 0 → 返回 null
  - `estimateCostUsd` rate 非 0 → 返回正确 USD
- [x] 7.4 (可选)`app/src/test/.../feature/aiwriting/usage/UsageBarChartTest.kt`
  - Compose UI test:7 个 bucket → Canvas 渲染 7 个 `drawRect` 调用(mock nativeCanvas)

## 8. 收口验证(0.5 天)

- [x] 8.1 `./gradlew :app:ktlintFormat`(自动修格式)
- [x] 8.2 `./gradlew :app:ktlintCheck`(应 0 violations)
- [x] 8.3 `./gradlew :app:assembleDebug`(编译通过,无 schema 变更 → 无 migration 警告)
- [x] 8.4 `./gradlew :app:testDebugUnitTest`(全部通过)
- [x] 8.5 grep 验证 main 无残留:`grep -r "TODO\|FIXME" app/src/main/java/com/yy/writingwithai/feature/aiwriting/usage/` 应无
- [x] 8.6 `docs/progress.md` 追加 1 条(本 change 完成节点)
- [x] 8.7 `git commit`(等用户指令,CLAUDE.md 规则 AI 不自动提交)

## 9. (可选)USER-OWNED 真机验证(不在 AI scope)

- [x] 9.1 真机跑 debug 包 → 进 My tab → 数据管理 → AI 用量
  - 已调用过 AI 的用户 → 看到图表 + 表
  - 未调用过 AI 的用户 → 看到空状态
  - 7d ↔ 30d 切换 → 图表正确刷新
- [x] 9.2 横竖屏 / 暗色模式切换 → 图表颜色 / 比例 OK
- [x] 9.3 等用户指令走 `archive` 归档