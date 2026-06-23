## Context

M4-1 `home-screen-widget` 落地后 widget 在 AOSP / Pixel Launcher 验证通过,M5 polish-and-internal-release 集中收口了 r2 review 部分 follow-up,但**国产 ROM 适配 + GlanceStateDefinition + glance-material3 颜色 token + DateUtils locale** 4 项未实际落地。

具体痛点:
- 国产 ROM(MIUI / EMUI / ColorOS / OriginOS)默认杀 widget 进程,widget 重启后状态丢失 → 显示空 widget(用户视角"App 没装好")
- widget 颜色硬编码 6 个 hex(`cBlue / cWhite / cBg / cTitle / cBody / cMeta`),不走 Material 3 ColorScheme → 暗色 / 亮色不能跟随系统,Material You 取色失效
- `formatRelativeTime` 手写 5 分支 when,locale 写死英文 (`m / h / d / w`),中文系统显示 "1 小时前" 但 "1h" 不通顺
- `MainActivity.onNewIntent` 已修(M4-4 r1 M1 修),但缺 test 锁住行为,后续重构可能 regress

roadmap §14 标了"国产 ROM 对 widget 限制"为风险项。本 change 集中收口。

## Goals / Non-Goals

**Goals:**
- 自定义 `WidgetStateDefinition` 走 DataStore 持久化 widget 状态(widget 进程被杀恢复后状态完整)
- 颜色 token 化:6 个硬编码 hex 替换为 `GlanceTheme.colors.widgetPrimary / widgetBackground / widgetOnBackground / widgetOnSurfaceVariant / widgetPrimaryContainer / widgetOutline`,深色 / 亮色 / Material You 三档跟随系统
- `formatRelativeTime` / `formatRelativeTimeCompact` 改 `DateUtils.getRelativeTimeSpanString(...)`,locale-aware,删除手写 when
- 新增 `RomDetector` + widget empty 文案分支:命中 MIUI / EMUI / ColorOS / OriginOS 显示"如 widget 不显示,请在系统设置 → 电池 → 自启动管理开启"
- `MainActivity.onNewIntent` 加 Robolectric test(锁住"重复触发 widget Intent 不重复 start Activity"行为)
- 加 4 个 i18n key(每个 ROM 一条)
- 新 `docs/usage/domestic-rom-widget.md`:4 国产 ROM 适配状态表 + 用户自助开 widget 自启动教程

**Non-Goals:**
- 重新设计 widget 布局(2x2 / 4x2 / 1x4 布局 M4-1 + widget-1x4-compact 已固化)
- 重新设计 widget 刷新策略(WorkManager 15min 兜底 M4-1 已落地)
- 适配 iOS / iPadOS / HarmonyOS PC(本 change 仅 Android 手机 ROM)
- 自动引导用户跳到系统设置(`Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)`)— 留 v2+
- 重写 1x4 widget 布局(只改颜色 token / DateUtils)
- 删 `cBlue / cWhite / ...` 之外的 widget 工具函数(`createNoteIntent` 等保留)

## Decisions

### 1. GlanceStateDefinition 走 DataStore,不复用 Preferences

```kotlin
class WidgetStateDefinition(private val context: Context) : GlanceStateDefinition<WidgetState> {
    private val Context.widgetStore: DataStore<WidgetState> by dataStore(
        fileName = "widget_state",
        serializer = WidgetStateSerializer
    )
    override suspend fun getDataStore(context: Context, fileKey: String): DataStore<WidgetState> = context.widgetStore
    override val location: GlanceStateDefinition.GlanceStateValueLocation = GlanceStateValueLocation.GlanceState
}

// 在 widget receiver 注入:
override val stateDefinition: GlanceStateDefinition<*> = WidgetStateDefinition(context.applicationContext)
```

**Why:** Glance 默认 `PreferencesGlanceStateDefinition` 落 SharedPreferences(单 key 文件);改 DataStore 更可控(可自定义 serializer + 跨 widget host 共享)。`dataStore` 委托自带 IO + 协程,不阻塞 widget host。

**替代方案:** 复用 `PreferencesGlanceStateDefinition` — 简单但 SharedPreferences 跨进程偶发 commit 失败;**选自定义 DataStore**。

### 2. WidgetState 数据类

```kotlin
@Serializable
data class WidgetState(
    val cachedNoteIds: List<String> = emptyList(),  // 最近 N 条 id
    val lastRefreshAt: Long = 0L,                    // 上次 refresh 时间戳
    val romVendor: RomVendor = RomVendor.AOSP,       // ROM 命中(MIUI / EMUI / ColorOS / ORIGINOS / AOSP)
)
```

**Why:** widget 进程被 ROM 杀 → 重启后 `cachedNoteIds` 还能命中 stale 笔记 → 显示"暂无更新"占位而非"还没有笔记"empty 状态。`lastRefreshAt` 给"最后更新于 5 分钟前"提示(若 ROM 拒绝 refresh)。

### 3. 颜色 token:`GlanceTheme.colors` 派生

```kotlin
@Immutable
data class WidgetColors(
    val widgetPrimary: ColorProvider,
    val widgetBackground: ColorProvider,
    val widgetOnBackground: ColorProvider,
    val widgetOnSurfaceVariant: ColorProvider,
    val widgetPrimaryContainer: ColorProvider,
    val widgetOutline: ColorProvider,
)

@Composable
@ReadOnlyComposable
fun widgetColors(): WidgetColors = WidgetColors(
    widgetPrimary = ColorProvider(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary),
    widgetBackground = ColorProvider(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface),
    widgetOnBackground = ColorProvider(MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.onSurface),
    widgetOnSurfaceVariant = ColorProvider(MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant),
    widgetPrimaryContainer = ColorProvider(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primaryContainer),
    widgetOutline = ColorProvider(MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.outline),
)
```

**Why:** Glance 1.1 颜色必须走 `ColorProvider(light, dark)` 形态;Material 3 `colorScheme` 已含 light/dark 双套(系统跟随);删 6 个硬编码 hex,改 `widgetColors().widgetPrimary` 引用;`@Immutable + @ReadOnlyComposable` 让 Compose 优化静态推导。

### 4. DateUtils.getRelativeTimeSpanString 替换手写

```kotlin
internal fun formatRelativeTime(context: Context, epochMs: Long): String {
    return DateUtils.getRelativeTimeSpanString(
        epochMs,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE  // "1h ago" 风格
    ).toString()
}

internal fun formatRelativeTimeCompact(context: Context, epochMs: Long): String {
    return DateUtils.getRelativeTimeSpanString(
        epochMs,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE or DateUtils.FORMAT_ABBREV_MONTH
    ).toString()
}
```

**Why:** Android framework 自带 locale-aware 时间格式化;支持中文("1小时前")、英文("1h ago")、日文、其他 locale 自动适配;删 30 行 when。

**替代方案:** 写 Kotlin locale-aware when — 重造轮子,Android framework 已做。**选 framework**。

### 5. RomDetector:Build.MANUFACTURER / Build.BRAND 命中

```kotlin
enum class RomVendor { MIUI, EMUI, COLOROS, ORIGINOS, AOSP }

object RomDetector {
    fun current(): RomVendor = when {
        Build.MANUFACTURER.equals("Xiaomi", true) || Build.BRAND.contains("Redmi", true) -> RomVendor.MIUI
        Build.MANUFACTURER.equals("HUAWEI", true) || Build.BRAND.contains("Honor", true) -> RomVendor.EMUI
        Build.MANUFACTURER.equals("OPPO", true) || Build.BRAND.contains("realme", true) -> RomVendor.COLOROS
        Build.MANUFACTURER.equals("vivo", true) || Build.BRAND.contains("iQOO", true) -> RomVendor.ORIGINOS
        else -> RomVendor.AOSP
    }
}
```

**Why:** 4 大国产 ROM 各占独立 path,`Build.MANUFACTURER` 主判 + `Build.BRAND` 兜底(子品牌如 Redmi / realme / iQOO / Honor);AOSP fallback 不显示提示。

### 6. Empty widget 文案分支

```kotlin
if (note != null) {
    // 既有 note 渲染
} else {
    val romHint = when (RomDetector.current()) {
        RomVendor.MIUI -> stringResource(R.string.widget_rom_miui_hint)
        RomVendor.EMUI -> stringResource(R.string.widget_rom_emui_hint)
        RomVendor.COLOROS -> stringResource(R.string.widget_rom_coloros_hint)
        RomVendor.ORIGINOS -> stringResource(R.string.widget_rom_originos_hint)
        RomVendor.AOSP -> null
    }
    if (romHint != null) {
        Text(stringResource(R.string.widget_empty), style = TextStyle(...))
        Text(romHint, style = TextStyle(fontSize = 10.sp, color = cp(cMeta)))
    } else {
        Text(stringResource(R.string.widget_empty), style = TextStyle(fontSize = 11.sp, color = cp(cMeta)))
    }
}
```

**Why:** 国产 ROM 用户第一次看到空 widget 时立即知道"是我设置问题,不是 App 没装好";无网络请求 + 无引导跳转,纯文案;降到 `fontSize = 10.sp` 防文字溢出。

### 7. i18n:4 个新 key

| key | 中文 | 英文(TODO 占位) | 用途 |
| --- | --- | --- | --- |
| `widget_rom_miui_hint` | 小米设备请到设置 → 电池 → 自启动管理开启本应用 | TODO(en) | MIUI 命中 |
| `widget_rom_emui_hint` | 华为设备请到设置 → 应用 → 启动管理关闭自动管理 | TODO(en) | EMUI 命中 |
| `widget_rom_coloros_hint` | OPPO 设备请到设置 → 电池 → 关闭"睡眠待机优化" | TODO(en) | ColorOS 命中 |
| `widget_rom_originos_hint` | vivo 设备请到设置 → 电池 → 后台高耗电允许 | TODO(en) | OriginOS 命中 |

**Why:** 4 个独立 key 而非 1 个带占位的(每个 ROM 引导路径不同,文案独立)。

### 8. MainActivity.onNewIntent test 锁住

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityOnNewIntentTest {
    @Test fun second_widget_intent_with_same_route_does_not_duplicate_navigation() { ... }
    @Test fun second_widget_intent_with_different_route_replaces_initialRoute() { ... }
}
```

**Why:** M4-4 r1 M1 修已实现正确行为(无重复 startActivity + lastInitialRoute 替换),本 change 加 Robolectric test 锁住,后续重构 regress 早发现。

### 9. 不引入 glance-material3,自管 GlanceTheme

**Why:** `glance-material3` 1.1.x 是实验 API + 不稳定 surface,与 `androidx.glance.material3` alpha 包冲突风险高;自管 `WidgetColors` 5 分钟改完,可控 + 不引新依赖。

**替代方案:** 引入 `androidx.glance:glance-material3` — 后续升级 Glance 时可能 breaking;**选自管**。

## Risks / Trade-offs

- **[Risk] DataStore widget state 与 Room 不同步** → widget 进程缓存的 `cachedNoteIds` 是 stale 副本,Room 更新后 widget 仍显示旧 id。Mitigation:`provideGlance` 内仍 `noteRepository.observeRecent(3).first()` 拉真实最新 → 用 stale cache 仅作 fallback(网络 / ROM 拒 refresh 时)。
- **[Risk] DateUtils 短时间(< 1 分钟)输出"0 分钟前"不直观** → 用户视角"我刚保存的笔记怎么就 0 分钟前"。Mitigation:`FORMAT_ABBREV_RELATIVE` 自动输出 "刚刚" / "Just now"。
- **[Risk] Build.MANUFACTURER 误判** → 用户装 CyanogenMod 等第三方 ROM 时 `Build.MANUFACTURER` 不为空字符串但 ROM 行为接近 AOSP。Mitigation:`Build.BRAND.contains(...)` 兜底白名单命中;误判时仍显示"AOSP 不提示"兜底。
- **[Risk] Robolectric Activity 启动测试慢** → Robolectric 首次运行时下载 ~500MB vintage engine。Mitigation:沿用 polish-and-internal-release 加的 `robolectric = "4.13"`,无需新依赖;测试 class 加 `@Config(sdk = [34])` 跳过 API level 兼容 check。
- **[Risk] 颜色 token 重构破坏现有 widget 视觉** → 删 6 个 hex 改 token,可能跟原色差。Mitigation:`widgetPrimary = MaterialTheme.colorScheme.primary`(系统 Material You 取色,默认蓝紫),与原 `cBlue = Color(0xFF3B82F6)` 接近;如果用户拒绝 material You,fallback 到 `colorScheme.primary` 系统默认蓝。

## Migration Plan

无 schema 变更,纯重构。无回滚成本:`git revert` 即恢复。

## Open Questions

- 1x4 widget (`QuickNote1x4Widget.kt`) 是否同步改 token + DateUtils?**默认同步**(proposal 已写,设计也同步);若用户要求 1x4 保持硬编码(为快),可拆两批。
- 是否加 `widget_rom_harmonyos_hint`?(HarmonyOS NEXT 不再是 Android fork) **默认不加**,留 v2+ 评估。
- 是否暴露 `RomDetector` 给业务侧使用(如设置页"我的 ROM 适配状态")?**默认不暴露**(单 widget 使用),留后续 change。