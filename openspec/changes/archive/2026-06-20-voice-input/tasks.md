## 1. 源码现状确认(零代码改动)

- [x] 1.1 `grep "RECORD_AUDIO" app/src/main/AndroidManifest.xml` → 0 匹配(确认 v1 manifest 无录音权限)
- [x] 1.2 `grep -rE "(RECORD_AUDIO|Whisper|Vosk|讯飞|百度|腾讯).*STT" app/src/main/` → 0 匹配(确认无 STT 依赖)
- [x] 1.3 `grep -rE "(interceptKey|onKeyEvent|InputConnection.*rawInput)" app/src/main/java/com/yy/writingwithai/feature/quicknote/edit/` → 0 匹配(确认编辑器不拦截 IME)

## 2. Spec 同步

- [x] 2.1 `openspec/specs/quick-note/spec.md` 末尾 `## ADDED Requirements (voice-input)` 段合入 delta(1 Requirement + 6 Scenarios)
- [x] 2.2 `openspec status --change voice-input` 4 个 artifact 全 done(proposal/design/specs/tasks)

## 3. 验收

- [x] 3.1 `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL UP-TO-DATE
- [x] 3.2 `./gradlew :app:ktlintCheck` → BUILD SUCCESSFUL
- [x] 3.3 `./gradlew :app:lintDebug` → BUILD SUCCESSFUL
- [x] 3.4 `grep "RECORD_AUDIO" app/src/main/AndroidManifest.xml` → 0 匹配(spec Scenario 验证)
- [x] 3.5 更新 `docs/progress.md` 加 voice-input entry

## 4. 归档

- [ ] 4.1 跑 `/opsx:archive voice-input` 收口
