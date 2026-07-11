## Why

M2 落地了 AI 抽象层,`CoreAiGateway` 在每次 `Usage → Done` / `Failed` 都往 `ai_history` 表写一行(
`providerId` / `model` / `op` / `inputTokens` / `outputTokens` / `totalTokens` / `durationMs` / `createdAt`)。
M3 进一步把 EXPAND / POLISH / ORGANIZE / TRANSLATE / SUMMARIZE 五种 op 接入到笔记编辑器流式按钮上。
M4-3 加了 `data-export-import` 把 `ai_history.json` 灌进 zip 备份,但**没有 UI 消费这份历史**:
用户看不到"上个月调了多少 token"、"哪个 op 最费 token"、"哪个 provider 最便宜"。

**问题:**
1. 选了真 provider(deepseek / MiniMax-M2.7 / mimo / custom)后,用户对**月度 token 消耗和折算花费无感**,
   无法判断"该不该升级套餐 / 是不是 provider 偷偷涨价"。
2. op 维度(扩写 vs 翻译 vs 整理)对 token 的消耗差异巨大(SUMMARIZE 的 input 通常 2000-4000 token),
   没有 per-op 分布,看不出**哪个 op 是 token 大头**。
3. 自定义 provider 上线后,用户需要横向对比 provider 的**性价比**(同 op 同输入,谁的 token 更省)。

**roadmap §5.3 / §15.2**:M5 polish 之前不会做账单/导出等高阶功能;但"用量统计"是数据所有权承诺的一部分
—— 用户应该能**看到自己的 AI 使用画像**(本地计算,从不外发)。

roadmap §15.1 拍板"v1 只本地,不联网同步账单",本 change 把"读 `ai_history` 表 → 渲染统计图"打通,
不引入账单 API / 不接 provider 价格接口。

## What Changes

- **新增 DAO 聚合方法**(`core/data/db/AiHistoryDao.kt`):
  - `aggregateByDay(periodStart: Long, periodEnd: Long): Flow<List<DailyUsageBucket>>` —
    按 `createdAt` 当天零点分桶,聚合 `sum(inputTokens)` / `sum(outputTokens)` / `sum(totalTokens)` / `count`,
    0 token 的空日也返回(便于图表 X 轴画连续日期)
  - `aggregateByOp(periodStart, periodEnd): Flow<List<OpUsageBucket>>` —
    按 `op` 分桶,聚合同样的 4 项;`op` 走 raw string 以便 Room 直接 GROUP BY
  - `aggregateByProvider(periodStart, periodEnd): Flow<List<ProviderUsageBucket>>` —
    按 `providerId` 分桶,聚合同样的 4 项
- **新增 Repository**(`core/data/repo/AiUsageRepository.kt`):
  Hilt 单例,API `observeUsage(period: UsagePeriod): Flow<UsageSnapshot>`,组合 3 个聚合 Flow 成单个 snapshot;
  `UsagePeriod` 是 sealed class(`Last7Days` / `Last30Days`)。**不在 Repository 层做成本估算** —— 成本估算
  走 ViewModel,纯函数,便于单测。
- **新增 cost 配置**(`core/prefs/ProviderCostStore.kt`):
  走普通 `SharedPreferences`(非 Encrypted,数字无敏感信息),存每个 provider 的 `inputCostPer1k` / `outputCostPer1k`
  (USD per 1k tokens);**默认全部 = 0**(成本估算**完全 opt-in**,不配不出值,不弹惊讶)。
  DataStore 替代实现可走 M5 polish。
- **新增 ViewModel**(`feature/aiwriting/usage/AiUsageViewModel.kt`):
  `@HiltViewModel`,注入 `AiUsageRepository` + `ProviderCostStore`;`StateFlow<AiUsageUiState>(Loading / Ready(snapshot) / Empty)`;
  接收 `period` `Flow<UsagePeriod>` 参数(从 SavedStateHandle 取或 VM 内 MutableStateFlow)。
- **新增 Screen**(`feature/aiwriting/usage/AiUsageScreen.kt`):
  - TopAppBar:返回箭头 + "AI 用量"标题
  - 顶部:7d / 30d 切换 chips(FilterChip)
  - 中部:**Compose Canvas 手绘条形图**,X 轴日期(7d 一天一柱 / 30d 一天一柱,30 柱)+ Y 轴 token 数;**不引第三方图表库**;
    颜色走 `MaterialTheme.colorScheme.primary`,bar 宽度根据 period 自适应
  - 下部 1:按 op 分组的 token 表(op 名 + 总 token + 调用次数),降序排列
  - 下部 2:按 provider 分组的 token 表(provider 名 + 总 token + 折算 USD 估算),降序排列
  - 空状态:`R.string.ai_usage_empty` = "还没有 AI 调用记录"
- **新增 entry point**(`feature/my/MyScreen.kt` + `MeTabTarget.kt`):
  在「我的」tab 「数据管理」SectionCard 加 "AI 用量" 入口(`Icons.Filled.QueryStats`),
  navigate 到 `AiUsage` route;`MeTabTarget` 新增 enum `AiUsage`。
- **新增 route**(`app/AppNav.kt`):
  `@Serializable data object AiUsage`,composable 渲染 `AiUsageScreen(onBack = popBackStack)`。
- **新增 i18n**(`values/strings.xml` + `values-en/`):
  - `ai_usage_title` = "AI 用量"
  - `ai_usage_period_7d` / `ai_usage_period_30d` = "近 7 天" / "近 30 天"
  - `ai_usage_total_tokens` = "总消耗 %1$d token"
  - `ai_usage_section_by_op` / `ai_usage_section_by_provider` = "按操作" / "按模型服务"
  - `ai_usage_empty` = "还没有 AI 调用记录"
  - `ai_usage_cost_fmt` = "约 $%1$.2f" / `ai_usage_cost_disabled` = "未配置成本费率"
  - `ai_usage_op_*`(5 个 op 翻译)
- **新增测试**:JUnit5 + Turbine 覆盖
  - `AiHistoryDaoAggregateTest`(Robolectric):插入 N 行 → aggregateByDay / aggregateByOp / aggregateByProvider 结果正确
  - `AiUsageRepositoryTest`:3 个 flow → `UsageSnapshot` 组合
  - `AiUsageViewModelTest`:period 切换 → snapshot 重新拉取
- **不引入**:
  - 第三方图表库(MPAndroidChart / Vico / etc.)—— Compose Canvas 自绘
  - provider 远程账单 / 价格 API(roadmap §15.1 拍 v1 本地)
  - 导出 CSV / Excel(v1 不做,M5 polish)
  - 跨设备用量合并(仅本机)

## Capabilities

### Modified Capabilities
- `ai-history`:DAO 加 3 个聚合方法;UiState 加"用量统计"消费路径;**schema 不变**(AiHistoryEntity 字段够用)
- `app-bottom-tab-bar`:`MeTabTarget` 加 `AiUsage` 枚举值;`MyScreen` 数据管理 Section 加入口

### New Capabilities
无(本 change 仅在 `ai-history` 加需求 + 在 `app-bottom-tab-bar` 加一个枚举 + 一条 MyScreen 入口)。

## Impact

- **新增 package**:
  - `feature/aiwriting/usage/` — AiUsageScreen / AiUsageViewModel / UsagePeriod / UsageSnapshot / DailyUsageBucket / OpUsageBucket / ProviderUsageBucket / UsageBarChart(Compose Canvas)
- **修改**:
  - `core/data/db/AiHistoryDao.kt` — 加 3 个 `@Query`
  - `core/data/repo/` — 加 `AiUsageRepository.kt`
  - `core/prefs/` — 加 `ProviderCostStore.kt`(普通 SharedPreferences)
  - `core/data/db/AppDatabase.kt` — **version 不变**(纯 DAO 方法新增,无 schema 变更)
  - `feature/my/MyScreen.kt` — 数据管理 SectionCard 加一行入口
  - `feature/my/MeTabTarget.kt` — 加 `AiUsage` enum
  - `app/AppNav.kt` — 加 `AiUsage` route + composable
- **新增 res**:
  - ~10 条 i18n key 双语
- **风险**:
  - `AiHistoryEntity` 已有 `providerId` / `model` / `op` / `inputTokens` / `outputTokens` / `totalTokens` / `createdAt`,
    **字段全部够用,无 schema 变更、无 migration**。已 grep 验证。
  - 早期用户(2026-06-19 之前安装的):`ai_history` 表可能为空(尚未记录调用)。
    空状态走 `R.string.ai_usage_empty`,**不弹错误**。
  - `AggregateByDay` 0 token 空日要不要返回?倾向:**返回**,让 Canvas 画连续 X 轴;
    VM 层判断若全部 day 都是 0 token,直接走 Empty 状态(避免出现"满图 0 高的柱")。
  - 成本估算对 deepseek/MiniMax-M2.7/mimo 等**官方公开价**做默认 0(opt-in 政策):
    一旦默认填值,用户会误以为本应用"提供账单" → 留 provider 账单对账风险;
    v1 接受"看不到 USD 数字,只看到 token 数"。
  - `ProviderCostStore` 普通 SharedPreferences 不加密(只是数字) — 但避免与 `EncryptedSharedPreferences` 混用
    导致用户"以为是 apikey 备份策略";UI 文案明确"成本费率仅本机保存"。
  - 30d × 100 calls/day = 3000 行,`aggregateByDay` 是 GROUP BY,O(N);实测 3000 行 < 50ms,
    `idx_ai_history_createdAt` 已存在(看 `AiHistoryEntity.kt` 的 `@Index("createdAt")`)。

## Acceptance Criteria

1. **AC1**:从「我的」tab → 数据管理 → 点击"AI 用量" → 进入 `AiUsageScreen`,能看到标题栏 + 7d/30d chips。
2. **AC2**:7d 模式下,条形图显示 7 根柱;30d 模式下显示 30 根柱;柱子高度与当日 token 数成正比。
3. **AC3**:调用 0 次 → 屏幕显示 "还没有 AI 调用记录",不显示 0 高的图表。
4. **AC4**:按 op 表格展示所有用过的 op(EXPAND / POLISH / ORGANIZE / TRANSLATE / SUMMARIZE 的子集),
   含总 token + 调用次数,降序。
5. **AC5**:按 provider 表格展示所有用过的 provider,含总 token;**未配置成本费率时显示"未配置成本费率",
   不显示美元数字**(避免误读)。
6. **AC6**:7d ↔ 30d 切换时,图表 / 表格内容都重新拉取,loading 态 < 100ms(本地 Room 聚合)。
7. **AC7**:`./gradlew :app:assembleDebug` + `:app:ktlintCheck` + `:app:testDebugUnitTest` 全绿。