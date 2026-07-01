## Context

v1 笔记编辑器(`feature/quicknote/edit/QuickNoteEditorScreen.kt`)用 Compose `OutlinedTextField` 落地标题 + 多行正文输入。Compose `OutlinedTextField` / `TextField` 是 Android 输入系统的标准 client，自动接入 `InputMethodManager` + `InputConnection` 协议 — 任何系统 IME(含 Gboard / 搜狗 / 讯飞 / 百度输入法)都可弹出，IME 自带的"麦克风"按钮触发 ASR 出文字后，文字通过 `InputConnection.commitText()` 注入到当前光标位置，应用层零感知。

当前 spec 没显式声明这条路径，后续可能误判为欠功能:
1. 误增 `android.permission.RECORD_AUDIO`(敏感权限，触发 Play Store 隐私披露)
2. 误接 on-device ASR(Whisper.cpp / Vosk)— 引入 ~50MB native lib
3. 误改 `CONSENT_VERSION` 触发同意门 bump 强制重同

把"v1 voice input 走 IME"钉在 spec，留 v2+ 增量 STT 路径占位。

## Goals / Non-Goals

**Goals:**
- 显式声明 v1 不集成 STT,voice input 完全委托系统 IME
- 不申请 `RECORD_AUDIO` 权限
- 给出 v2+ 引入 STT 的扩展路径占位(新增 capability / 触发同意门 / 加权限申请 UI)

**Non-Goals:**
- 不集成任何 STT provider(Whisper / 讯飞 / 百度 / 腾讯 ASR)
- 不修改 `OutlinedTextField` 调用方式
- 不增加"语音输入"专属按钮(由 IME 自然提供)
- 不写新代码(纯 spec 改动)

## Decisions

### D1: 不集成 STT，委托 IME

**选型**:Compose `OutlinedTextField` / `TextField` 已是 Android IME 标准 client,IME 自带的"麦克风"按钮出文字后通过 `InputConnection` 注入。零额外代码。

**理由**:
- 用户在中文场景普遍使用第三方 IME(搜狗 / 讯飞 / 百度)，这些 IME 自带 ASR，质量/速度优于 on-device 通用模型
- 集成 STT 需申请 `RECORD_AUDIO` 权限，触发 Play Store 隐私披露 + 应用市场审核(v1 不上架，即便如此也增加隐私压力)
- on-device ASR native lib(~50MB)增加 APK 体积 5-10x
- v1 用户群小(自用 / 朋友内测)，不会暴露 STT 缺失问题

**替代方案**:
- 集成 on-device Whisper.cpp:拒绝(APK 膨胀 + 准确度低)
- 集成云 STT(讯飞/百度):拒绝(需 apikey + 隐私 + 网络)
- 提供"语音输入"专属 UI 按钮调 `ACTION_RECOGNIZE_SPEECH`:拒绝(走系统 STT dialog，与 IME 体验重复)

### D2: 显式 spec 拒绝 `RECORD_AUDIO` 权限

`AndroidManifest.xml` MUST NOT 声明 `<uses-permission android:name="android.permission.RECORD_AUDIO" />`。当前 v1 manifest 也不含(已确认)，本 change 仅在 spec 显式约束以防误增。

### D3: v2+ STT 增量路径占位

v2+ 引入 ASR 时的 spec 演进路径:
1. 新建 capability `voice-stt` (或 `voice-input-v2`)
2. bump `CONSENT_VERSION` 触发同意门
3. 加 `<uses-permission RECORD_AUDIO />` + 运行时权限申请 UI
4. 编辑器新增"麦克风"专属按钮，触发 STT provider
5. IME 路径仍保留，两条路径共存(用户选)

不在本 change 实现。

## Risks / Trade-offs

- **[风险] 部分小众 ROM / 旧版 Android(API < 23)IME 不支持 voice input 协议** → v1 `minSdk = 26`,API 26+ IME 全支持;无影响
- **[风险] 用户预期"应用应该自带语音输入"** → onboarding 隐私条款可加一句"语音输入由您的输入法提供"，留 M5.1 补
- **[风险] 后续误增 RECORD_AUDIO** → spec 显式约束 + code review checklist

## Open Questions

1. onboarding 隐私文案是否补充"voice input 委托 IME" — 当前 v1 没显式说，等用户反馈决定是否补
2. v2+ STT 路径是否走 on-device(Whisper.cpp)还是云(讯飞 ASR) — 留 v2+ change 拍板
