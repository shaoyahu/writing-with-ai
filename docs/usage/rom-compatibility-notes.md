# 国产 ROM 兼容性笔记

writing-with-ai v1 的 widget + predictive back 在主流国产 ROM 上的已知问题与降级方案。现场测试设备有限(开发机 Pixel),此文档基于公开社区反馈 + 推断,等真机验证后更新。

## 小米 MIUI / HyperOS

| 问题 | 影响 | 状态 |
| --- | --- | --- |
| Widget 被电池优化"锁定" | `WorkManager` 15min 兜底刷新可能被延迟到 30min+;App 进程被杀后 widget `RemoteViews` 不再更新 | 已知,已通过 WorkManager `KEEP` policy 缓解 |
| 自启动权限默认关闭 | widget PendingIntent 不响应(需用户手动开自启动) | 用户需手动前往"设置→应用→自启动管理" |
| Predictive back | `enableOnBackInvokedCallback="true"` 行为与 AOSP 一致,手势正常 | ✅ 预期正常 |

**降级**:widget 不可用时用户可进 App 内快捷入口(随手记列表 FAB)操作;首次安装引导页会提示检查自启动权限。

## 华为 HarmonyOS / EMUI

| 问题 | 影响 | 状态 |
| --- | --- | --- |
| `WorkManager` schedule 稳定性 | HarmonyOS 对周期任务限制更严;15min 可能被忽略到 30~60min | WorkManager `KEEP` policy 不保证精确周期 |
| Widget `PendingIntent` 拦截 | Intent extra `route` 字符串可能被系统安全中间件 strip | widget 入口失效时用户从 launcher 图标进 App 走主路由 |
| Predictive back | 部分 EMUI 版本 `enableOnBackInvokedCallback` 不生效(系统级覆盖) | 降级:用户手动按导航栏 back(传统 onBackPressed) |

**降级**:widget 不可用 → 依赖手动进 App 写笔记;widget 只做快捷入口(非核心功能)。back 手势失效 → 依赖导航栏 back 按钮 + App 内返回箭头。

## OPPO ColorOS

| 问题 | 影响 | 状态 |
| --- | --- | --- |
| Widget reorder crash | 在"多桌面预览"模式下拖动 widget 可能触发 `RemoteViews.setText` with null layout | M4-1 r1 已加 `supportRtl` / `initialLayout` 兜底;仍有较低概率 crash(系统 bug) |
| `PendingIntent.getActivity` 限制 | ColorOS 默认拦截后台 Noti 启动 Activity;widget 在后台时 PendingIntent 可能被延迟 3~5s | 用户可见延迟;核心流程不依赖 widget |
| Predictive back | `enableOnBackInvokedCallback="true"` 行为与 AOSP 基本一致 | ✅ 预期正常 |

**降级**:widget crash → Glance `WidgetError` 已有 display text fallback(`R.string.widget_error`)。后台 PendingIntent 延迟 → 接受 3~5s,不做额外处理。

## vivo OriginOS

| 问题 | 影响 | 状态 |
| --- | --- | --- |
| Widget layout 缺失 | OriginOS 部分版本对 Glance `sizeMode="Single"` 支持不完整;widget layout 可能在特定桌面网格下不渲染 | M4-1 r1 `@GlanceComposable` 已加 fallback layout;widget 预览图 `widget_preview.xml` 显示正常 |
| 电池管理策略激进 | App 进程在 OriginOS"超级省电模式"下 3min 即杀 | widget 刷新依赖 WorkManager 兜底(即便被杀,下次系统唤醒仍可执行) |
| Predictive back | 已知在 `Funtouch OS 13` 不完整支持;`enableOnBackInvokedCallback` 可能不生效 | 降级:传统 onBackPressed |

**降级**:widget 不渲染 → 用户从 launcher 图标进 app。电池管理 → 接受,不做保活(保活是产品决策,非技术问题)。

---

## 降级方案(统一)

所有 ROM 的 widget / predictive back 问题共享两道降级:

1. **Widget 不可用**: 进 App 内快捷入口 — launcher 图标 → 随手记列表 → FAB 新建笔记 / 搜索 / 标签筛选。widget 是便利入口,不阻塞核心写作流程。
2. **Back 手势失效**: 导航栏 back 按钮 + App 内 Toolbar 返回箭头(Compose `IconButton(onClick = navController::popBackStack)`)。M4-2 的 `enableOnBackInvokedCallback="true"` 是增强路径,降级路径始终存在。

若用户反馈某 ROM 问题严重(如 3 种以上同时触发),考虑为该 ROM 单独发 FAQ 或禁 widget(设计期不做,等真机数据)。

---

## v1 内测真机验证矩阵

> **状态字段说明**:
> - `[verified]` — 真机跑过,行为符合预期 / workaround 已落
> - `[pending]` — 待真机验证(首版 internal testing APK 后跑)
> - `[deferred]` — 真机跑不到 / 设备不足,推迟到 v1.1
>
> 维护人: 真机验证者本人改;AI 每周巡检。

| OEM | 限制项 | 验证状态 | 降级方案 |
| --- | --- | --- | --- |
| 小米 MIUI / HyperOS | Widget 后台拉活 + 自启动权限 | `[pending]` | WorkManager `KEEP` policy + onboarding 引导卡跳转自启动设置页 |
| 华为 HarmonyOS / EMUI | 应用启动管理 + Widget PendingIntent extra strip | `[pending]` | 用户手动改"手动管理";Intent extra 改 Parcelable 序列化 |
| OPPO ColorOS | 睡眠待机 + Widget reorder crash | `[pending]` | Glance `WidgetError` fallback;接受 3~5s PendingIntent 延迟 |
| vivo OriginOS | 电池高耗电禁止 + Widget layout 部分版本不渲染 | `[pending]` | `@GlanceComposable` fallback layout;不做保活 |
| 其它 (三星 OneUI / 一加 ColorOS 衍生 / Pixel 类原生) | 无国产 ROM 特有约束 | `[pending]` | 默认走 AOSP 路径,无需特殊降级 |
