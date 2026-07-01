## ADDED Requirements

### Requirement: RomDetector identifies 4 major domestic ROMs

`core/widget/RomDetector.kt` MUST 暴露 `enum RomVendor { MIUI / EMUI / COLOROS / ORIGINOS / AOSP }` 与 `object RomDetector { fun current(): RomVendor }`。

`current()` MUST 按以下规则判别:
- `MIUI`:`Build.MANUFACTURER.equals("Xiaomi", ignoreCase=true)` OR `Build.BRAND.contains("Redmi", ignoreCase=true)`
- `EMUI`:`Build.MANUFACTURER.equals("HUAWEI", ignoreCase=true)` OR `Build.BRAND.contains("Honor", ignoreCase=true)`
- `COLOROS`:`Build.MANUFACTURER.equals("OPPO", ignoreCase=true)` OR `Build.BRAND.contains("realme", ignoreCase=true)`
- `ORIGINOS`:`Build.MANUFACTURER.equals("vivo", ignoreCase=true)` OR `Build.BRAND.contains("iQOO", ignoreCase=true)`
- `AOSP`:以上都不命中(MUST 为兜底，包括 Pixel / 三星国际版 / 一加海外版 / 索尼 / 摩托等)

MUST NOT 抛异常(Build.MANUFACTURER / BRAND 在非标准 ROM 上可能为空字符串，equals ignoreCase 安全)。

#### Scenario: 小米主品牌命中 MIUI

- **WHEN** `Build.MANUFACTURER = "Xiaomi"`,`Build.BRAND = "Xiaomi"`
- **THEN** `RomDetector.current()` 返回 `RomVendor.MIUI`

#### Scenario: Redmi 子品牌命中 MIUI

- **WHEN** `Build.MANUFACTURER = "Xiaomi"`,`Build.BRAND = "Redmi"`
- **THEN** `RomDetector.current()` 返回 `RomVendor.MIUI`(子品牌通过 BRAND 兜底)

#### Scenario: 华为 / Honor 命中 EMUI

- **WHEN** `Build.MANUFACTURER = "HUAWEI"` 或 `Build.BRAND = "HONOR"`
- **THEN** `RomDetector.current()` 返回 `RomVendor.EMUI`

#### Scenario: OPPO / realme 命中 COLOROS

- **WHEN** `Build.MANUFACTURER = "OPPO"` 或 `Build.BRAND = "realme"`
- **THEN** `RomDetector.current()` 返回 `RomVendor.COLOROS`

#### Scenario: vivo / iQOO 命中 ORIGINOS

- **WHEN** `Build.MANUFACTURER = "vivo"` 或 `Build.BRAND = "iQOO"`
- **THEN** `RomDetector.current()` 返回 `RomVendor.ORIGINOS`

#### Scenario: Pixel 兜底 AOSP

- **WHEN** `Build.MANUFACTURER = "Google"`,`Build.BRAND = "google"`
- **THEN** `RomDetector.current()` 返回 `RomVendor.AOSP`

#### Scenario: 空字符串 Build 兜底 AOSP

- **WHEN** `Build.MANUFACTURER = ""`(自定义 ROM / 模拟器异常)
- **THEN** `RomDetector.current()` 不抛异常，返回 `RomVendor.AOSP`

### Requirement: Widget empty state shows ROM-specific hint

`QuickNoteWidget` 与 `QuickNote1x4Widget` 在 `notes.isEmpty()` 分支 MUST 调 `RomDetector.current()` 决定 hint 文案:
- 命中 4 国产 ROM → 在既有 `R.string.widget_empty` 文案下方加 1 行 hint(`fontSize = 10.sp`)，引导用户到系统设置开自启动
- AOSP → 仅显示 `R.string.widget_empty`,**不** 加 hint 行

4 个 hint 文案 MUST 走 R.string，内容(中文权威):
- `widget_rom_miui_hint`:小米设备请到设置 → 电池 → 自启动管理开启本应用
- `widget_rom_emui_hint`:华为设备请到设置 → 应用 → 启动管理关闭自动管理
- `widget_rom_coloros_hint`:OPPO 设备请到设置 → 电池 → 关闭"睡眠待机优化"
- `widget_rom_originos_hint`:vivo 设备请到设置 → 电池 → 后台高耗电允许

`values-en/strings.xml` 4 个 key MUST 用 TODO 占位(英文文案留 v2 polish 翻译)。

#### Scenario: MIUI 设备 widget 空状态

- **WHEN** `RomDetector.current() == MIUI` + 笔记列表为空 + widget 渲染
- **THEN** widget 显示 `widget_empty` + 下行 `widget_rom_miui_hint` 完整两行

#### Scenario: AOSP widget 空状态不变

- **WHEN** `RomDetector.current() == AOSP` + 笔记列表为空 + widget 渲染
- **THEN** widget **只** 显示 `widget_empty`,**不** 显示任何 ROM hint(hint 文案 0 个字符)

#### Scenario: hint 文案走 R.string 不硬编码

- **WHEN** `grep "小米设备请到\|华为设备请到\|OPPO 设备请到\|vivo 设备请到" app/src/main/java/com/yy/writingwithai/core/widget/`
- **THEN** 0 个匹配(文案一律走 R.string,i18n 友好)

### Requirement: docs/usage/domestic-rom-widget.md documents adaptation state

`docs/usage/domestic-rom-widget.md` MUST 存在，内容含:
1. **适配状态表**:4 国产 ROM × 4 项(GlanceStateDefinition 持久化 / 颜色 token / DateUtils locale / 空状态 hint)
2. **用户自助教程**:每 ROM 一节，截图占位(Markdown `![screenshot](path/to/img.png)`)，说明"设置 → 电池 → 自启动管理"路径
3. **已知限制**:国产 ROM 后台杀 widget 进程的缓解措施(WorkManager 15min 兜底 / 用户开自启动 / 上拉桌面刷新)
4. **ROM 检测原理**:`Build.MANUFACTURER` + `Build.BRAND` 白名单(MIUI / Redmi / HUAWEI / Honor / OPPO / realme / vivo / iQOO 8 个关键词)

文档 MUST 跟代码同步更新(`domestic-rom-widget` capability 的 source of truth);新增 ROM 命中 → 必须更新 spec + 代码 + 文档三处。

#### Scenario: 文档存在

- **WHEN** `cat docs/usage/domestic-rom-widget.md`
- **THEN** 文件存在且非空，含 4 段必填章节(状态表 / 教程 / 限制 / 检测原理)

#### Scenario: 状态表 4 ROM × 4 项齐全

- **WHEN** 读文档"适配状态表"章节
- **THEN** 4 行(MIUI / EMUI / ColorOS / OriginOS)× 4 列(GlanceStateDefinition / 颜色 token / DateUtils / 空状态 hint)共 16 格，每格有 ✓ 或 ✗ 标记

#### Scenario: 截图占位存在

- **WHEN** `grep "!\[" docs/usage/domestic-rom-widget.md`
- **THEN** 至少 4 个 Markdown 图片语法(每 ROM 1 个教程截图占位)

### Requirement: MainActivity.onNewIntent handles repeated widget intents

`app/MainActivity.kt` `onNewIntent(intent: Intent)` MUST 在用户连续触发 widget Intent(同一 route 或不同 route)时不重复 startActivity，且正确更新 `lastInitialRoute` 触发 `AppNav` 重 navigate。

MUST 由 Robolectric test `MainActivityOnNewIntentTest` 覆盖:
- 同 route 连续 2 次 widget Intent → state 不重复 navigate(可由 `AppNav` LaunchedEffect 触发次数断言)
- 不同 route 第 2 次 widget Intent → `lastInitialRoute` 替换为新 route,`AppNav` LaunchedEffect 触发新 navigate

test class MUST 用 `@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])` + `HiltAndroidTest` 注入 ConsentStore。

#### Scenario: 同 route 重复触发不重复 navigate

- **WHEN** widget "+" 按钮触发 `quicknote/edit?prefillFocus=true` Intent → 1s 后再触发同 route Intent
- **THEN** `AppNav` LaunchedEffect 触发的 navigate 次数 = 1(不是 2);`MainActivity` 不抛 `IllegalStateException: Activity already started`

#### Scenario: 不同 route 第 2 次 navigate 替换

- **WHEN** widget 笔记项触发 `quicknote/detail/n1` Intent → 5s 后 widget "+" 触发 `quicknote/edit?prefillFocus=true` Intent
- **THEN** `lastInitialRoute` 从 `quicknote/detail/n1` 替换为 `quicknote/edit?prefillFocus=true`;`AppNav` LaunchedEffect 触发 2 次 navigate(首次 detail/n1，二次 edit prefillFocus=true)
