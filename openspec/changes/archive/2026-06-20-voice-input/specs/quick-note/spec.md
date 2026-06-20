## ADDED Requirements

### Requirement: Note editor delegates voice input to system IME

`QuickNoteEditorScreen` MUST 走标准 Compose `OutlinedTextField` / `TextField` 落地标题 + 正文输入(已 M1 落地),不修改其 `Modifier` / `value` / `onValueChange` / `keyboardOptions` / `keyboardActions` 等属性以"屏蔽 IME 语音输入";系统 IME 自带的"麦克风"按钮触发 ASR 出文字后,通过 `InputConnection.commitText()` 注入到光标位置,应用层零感知。

App MUST NOT 在 `AndroidManifest.xml` 声明 `<uses-permission android:name="android.permission.RECORD_AUDIO" />`;MUST NOT 集成任何 on-device / 三方 STT provider(Whisper.cpp / Vosk / 讯飞 / 百度 ASR);MUST NOT 在编辑器屏新增"语音输入"专属 UI 按钮(v1 委托 IME 自然提供)。

#### Scenario: TextField 走标准 IME(无 STT 集成)
- **WHEN** 用户在 `QuickNoteEditorScreen` 的正文 `OutlinedTextField` 长按 / 点击唤起系统 IME
- **THEN** IME 弹出;若用户当前 IME 支持 ASR(搜狗 / 讯飞 / 百度 / Gboard 等),"麦克风"按钮可见;用户点"麦克风"说出语音 → IME 内部 ASR 出文字 → 文字通过 InputConnection 注入到光标位置 → `OutlinedTextField` 的 `onValueChange` 收到新 `TextFieldValue` → `editorViewModel.setContent(newValue)` 走 M1 既有 upsert 路径

#### Scenario: BasicTextField action 模式走 IME 标准协议
- **WHEN** 详情屏 `BasicTextField(value = textFieldValue, onValueChange = ..., readOnly = true)`(M3 既有)显示 content
- **THEN** 用户长按选中 5 个字符后,系统弹出含"剪切 / 复制 / 分享 / 语音输入(由 IME 提供)"的浮动工具栏;点"语音输入"后行为同上一个 Scenario(IME 处理 ASR + InputConnection 注入)— app 不感知

#### Scenario: v2+ STT 路径占位
- **WHEN** v2+ OpenSpec change 决定集成 STT(Whisper.cpp / 讯飞 / 百度 ASR)
- **THEN** 该 change MUST:
  1. 新建 capability `voice-stt` (或 `voice-input-v2`)对应 `openspec/specs/<capability>/spec.md`
  2. bump `R.integer.consent_version` 触发同意门强制重同(v1 隐私条款不涉及录音,需更新条款)
  3. 加 `<uses-permission android:name="android.permission.RECORD_AUDIO" />` 到 manifest + 加运行时权限申请 UI(详情页/编辑器页首次进入录音前)
  4. 在编辑器屏新增"麦克风"专属按钮,触发选定的 STT provider
  5. IME 路径仍保留,两条路径共存(用户选)

#### Scenario: 源码 grep 验证无 STT 依赖
- **WHEN** `grep -rE "(RECORD_AUDIO|Whisper|Vosk|讯飞|百度|腾讯).*STT" app/src/main/`
- **THEN** 0 个匹配(当前 v1 无 STT 集成,v2+ change 引入后才会有)

#### Scenario: 源码 grep 验证无 RECORD_AUDIO 权限
- **WHEN** `grep "RECORD_AUDIO" app/src/main/AndroidManifest.xml`
- **THEN** 0 匹配(manifest 不声明录音权限)

#### Scenario: 编辑器 TextField 不绕过 IME
- **WHEN** `grep -rE "(interceptKey|onKeyEvent|InputConnection.*rawInput)" app/src/main/java/com/yy/writingwithai/feature/quicknote/edit/`
- **THEN** 0 匹配(编辑器不拦截 IME 事件,IME 协议完整透传)
