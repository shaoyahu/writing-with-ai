## Why

v1 笔记编辑器(`QuickNoteEditorScreen`)用 Compose `TextField`，已自动支持系统 IME 自带的语音输入(搜狗 / 讯飞 / 百度 / Gboard 等输入法内置"麦克风"按钮即用即出文字)。但当前没有任何 spec 显式声明"voice input 委托给 IME, app 不集成 STT"，后续可能误判为欠功能、误增 `RECORD_AUDIO` 权限 / 误接 on-device ASR / 误改同意门 bump 触发重同。把"v1 voice input 走 IME"这件事在 spec 层面钉死，留出后续 v2+ 增量加 STT 的路径。

## What Changes

- **新增 spec 声明** `quick-note`:编辑器 TextField 委托语音输入给系统 IME;app 不集成 on-device / 三方 STT;不申请 `RECORD_AUDIO` 权限
- **修改** `AndroidManifest.xml` 注释:明确"v1 不申请 RECORD_AUDIO, voice input 走 IME"(实际 manifest 不加权限，仅留注释指向 spec)
- **新增** 后续增量 STT 路径占位 Scenario:v2+ 引入 ASR 时需新增 capability + 触发同意门 bump + 加权限申请 UI

无新代码，无新依赖，无 UI 改动。

## Capabilities

### New Capabilities

- (无)

### Modified Capabilities

- `quick-note`: 增加 1 个 Requirement("Note editor delegates voice input to system IME")+ 3 个 Scenarios(TextField 走标准 IME / BasicTextField action 模式 / v2+ 增量 STT 路径占位)

## Impact

**新文件**:
- (无生产代码)
- `openspec/changes/voice-input/specs/quick-note/spec.md` (delta)

**修改文件**:
- `openspec/specs/quick-note/spec.md` (MODIFIED，合入 delta 后)
- (可选) `app/src/main/AndroidManifest.xml` 加注释说明 v1 不申请 RECORD_AUDIO(若已在 M0 写明则跳过)

**依赖**:
- 无

**回归风险**:
- 无(纯 spec 改动)
