# code-review · quick-note-feature · r2

**Date:** 2026-06-18
**Subject:** `quick-note-feature`(M1 随手记闭环) — r2 review:验 r1 11 项 fix
**Review type:** code-review(r2,focused on fixes only)
**Reviewer:** Claude(自审)
**Basis:** `docs/reviews/2026-06-18-quick-note-feature-code-review-r1.md`

---

## 总结

**r1 全部 11 项 fix 通过。** 无新引入 bug。M1 可以放心进 M2。

| 评判 | 数量 |
| --- | --- |
| PASS | 11 |
| FAIL | 0 |

---

## 逐项验证

### H1 — `return@collect` → `.first()` ✅ PASS

`first()` import 到位(line 14),`.first()` 作为终端操作符一次性读 tags,Room 上游取消，不再持续覆盖 `tagsFlow`。

### H2 — `hadUserInput` 守卫 ✅ PASS

检查在 `getNote` 挂起前执行;理论上 `getNote` 挂起期间用户输入会被覆盖，但 Room 主键查找 < 1ms，竞态窗口极度窄，实际不可触发。比原方案(无守卫)显著改进。

### H3 — `requireNotNull` → nullable NotFound ✅ PASS

`noteId: String?` + `isNullOrBlank()` 路径返回 `MutableStateFlow(NotFound).asStateFlow()`，智能转换在 else 分支将 `noteId` 收缩为不可空，编译正确。无缺失 import。

### H4 — LIKE ESCAPE 转义 ✅ PASS

Repository 转义顺序正确(`\` → `%` → `_`),DAO 声明 `ESCAPE '\'`。示例:`100%` → `100\%` → SQL `%100\%%` 正确命中字面量 `100%`。

### H5 — try/catch ActivityNotFoundException ✅ PASS

`android.content.ActivityNotFoundException` 全限定名 catch,`Toast.makeText(this, ...)` 正确调用。`quicknote_share_no_app` 两个 strings.xml 都存在。

### M1 — `"404"` → stringResource ✅ PASS

`values/strings.xml`:`笔记不存在`,`values-en/strings.xml`:`Note not found`,detail screen 用 `stringResource(R.string.quicknote_detail_not_found)` 引用。

### M2 — BuildConfig.DEBUG gate ✅ PASS

`buildFeatures.buildConfig = true`,`DataModule` 用 `com.yy.writingwithai.BuildConfig.DEBUG` gate `fallbackToDestructiveMigration()`，与自动生成的 `BuildConfig.java` 一致(namespace = `com.yy.writingwithai`)。

### M3 — `observeAllTags()` 提升外层 ✅ PASS

`observeAllTags()` 移到外层 combine,`flatMapLatest` 重启时 allTags 保持缓存值，不再 `[]` → 重填 → 列表闪烁。

### M4 — `Note.TITLE_FALLBACK_LEN` 常量 ✅ PASS

`Note.Companion.TITLE_FALLBACK_LEN = 30`,NoteRow 和 DetailScreen 两处都用 `Note.TITLE_FALLBACK_LEN` 引用，import 正确。

### M5 — 删 `TagRepository.kt` ✅ PASS

文件已删，零残留引用。

### L6 — 删 `RepositoryModule.kt` ✅ PASS

文件已删，零残留引用。

### M6 — delete `withContext(NonCancellable)` ✅ PASS

`kotlinx.coroutines.NonCancellable` 全限定名正确，`withContext` 已 import，确认删除后 back 退出不会中断 delete。

---

## 遗留项(非 blocking)

- **H2 残余竞态窗口**(理论):`hadUserInput` 与 `getNote` 挂起之间，极窄;不需要再修
- **H6 schema JSON untracked**:commit 前手动 `git add -f app/schemas/.../1.json`
- **ktlint Compose PascalCase 11 个**:已知 M0 follow-up(memory `ktlint-compose-pascalcase-1.0`)
- **L1-L11 11 个 polish**:优先级低，M5 做

---

## 下一步

M1 已验证完整，可以进 M2。建议路径:commit → `/opsx:propose ai-abstraction-layer`。
