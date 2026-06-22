# fix-review-r2-high — design

详细 plan 见 `/Users/bytedance/.claude/plans/optimized-wiggling-muffin.md`。本文件列每项的具体 file:line + 改法 + 测试。

## H5 · NoteAssociationSettings 挪到 core/prefs/

### 新文件 `core/prefs/NoteAssociationSettingsStore.kt`

```kotlin
package com.yy.writingwithai.core.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

interface NoteAssociationSettingsStore {
    fun isEnabled(): Boolean
    fun setEnabled(value: Boolean)
    fun observeEnabled(): Flow<Boolean>
}

@Singleton
class NoteAssociationSettingsStoreImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : NoteAssociationSettingsStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    override fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
    }

    override fun observeEnabled(): Flow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_ENABLED) trySend(isEnabled())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(isEnabled())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    companion object {
        private const val PREFS_NAME = "settings_note_association"
        private const val KEY_ENABLED = "llm_extract_enabled"
    }
}
```

### 删除 `feature/settings/NoteAssociationSettings.kt`

整个文件删除。

### 改 `core/note/impl/CompositeNoteLinker.kt`

- import 改 `core.prefs.NoteAssociationSettingsStore`
- 依赖类型改 `NoteAssociationSettingsStore`
- L53 `catch (_: Exception)` → 修 M2 顺手:`catch (e: Exception) { if (e is CancellationException) throw e }`

### 改 `feature/settings/SettingsScreen.kt:120`

```kotlin
fun noteAssociationSettings(): NoteAssociationSettingsStore
```

### 改 `feature/quicknote/detail/QuickNoteDetailScreen.kt:88`

`fun noteAssociationSettings(): NoteAssociationSettingsStore`

### 改 `core/note/di/NoteLinkerModule.kt`

Hilt bind `NoteAssociationSettingsStore` ← `NoteAssociationSettingsStoreImpl`(若 module 存在)

### 改 test `CompositeNoteLinkerTest.kt:14, 45`

import 改;mockk 类型改 `NoteAssociationSettingsStore`

## H4 · AiwritingEntry 扩 public surface

### 改 `feature/aiwriting/AiwritingEntry.kt`

扩 surface:

```kotlin
object AiwritingEntry {
    @Composable
    fun rememberAiActionViewModel(noteId: String): AiActionViewModel = hiltViewModel()

    fun requestConsent(navController: NavController) { OnboardingEntry.requestConsent(navController) }

    @Composable
    fun ActionSheetRoute(
        visible: Boolean,
        onDismiss: () -> Unit,
        anchorOffset: IntOffset,
        onActionSelected: (WritingOp) -> Unit,
        onCopy: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        ActionSheet(visible, onDismiss, anchorOffset, onActionSelected, onCopy, modifier)
    }

    @Composable
    fun StreamingPanelRoute(
        state: AiActionUiState,
        onAccept: () -> Unit,
        onReject: () -> Unit,
        onCancel: () -> Unit,
        onRegenerate: () -> Unit,
        onClose: () -> Unit,
        onDismiss: () -> Unit,
        onUndo: () -> Unit = {},
        onDismissReplace: () -> Unit = {}
    ) {
        StreamingPanel(state, onAccept, onReject, onCancel, onRegenerate, onClose, onDismiss, onUndo, onDismissReplace)
    }

    fun copyToClipboard(context: Context, text: String, label: String = "") {
        // 内部调 feature.aiwriting.action.copyToClipboard
    }
}

// typealias 让 QuickNoteDetailScreen 通过 AiwritingEntry 拿到 UiState 类型
typealias AiActionUiState = com.yy.writingwithai.feature.aiwriting.streaming.AiActionUiState
```

### 改 `feature/quicknote/detail/QuickNoteDetailScreen.kt:65-72`

- 删 5 个 internal import
- 调用改 `AiwritingEntry.ActionSheetRoute(...)` 等
- `AiActionUiState` 用 `AiwritingEntry` 同 package 的 typealias

## H1 · CoreAiGateway.runBlocking ANR

### 改 `core/ai/api/AiGateway.kt`

```kotlin
interface AiGateway {
    fun streamWritingOp(
        op: WritingOp,
        sourceText: String,
        providerId: String,
        apikey: String,
        modelName: String?,
        systemPrompt: String? = null,
        apiFormatOverride: ApiFormat? = null  // ← NEW
    ): Flow<AiStreamEvent>

    suspend fun ping(
        providerId: String,
        apikey: String,
        modelName: String,
        apiFormatOverride: ApiFormat? = null  // ← NEW
    ): String?
}
```

### 改 `core/ai/CoreAiGateway.kt`

- 删 L114-117 runBlocking
- 删 L167-178 resolveProviderFlow 整个函数
- `streamWritingOp` 用 caller 传入的 `request.apiFormatOverride`(`request` 已经有 `apiFormatOverride` 字段)
- `ping` 已在 suspend 上下文,可 await `providerPrefsStore.getApiFormat(providerId)`

### 改 `feature/aiwriting/streaming/AiActionViewModel.kt:109-126`

`start()` 在 suspend 上下文显式 await:

```kotlin
viewModelScope.launch {
    val providerId = providerPrefsStore.getSelectedProviderId()
    val apikey = secureApiKeyStore.get(providerId)
    val apiFormatOverride = providerPrefsStore.getApiFormat(providerId)
    val modelName = providerPrefsStore.getSelectedModel(providerId)
    // ... gate + state init ...
    aiGateway.streamWritingOp(
        op, sourceText, providerId, apikey ?: "", modelName, systemPrompt, apiFormatOverride
    ).collect { ... }
}
```

## H9 · pingFromForm 重构

### 改 `feature/settings/model/CustomProviderEditViewModel.kt:162-200`

```kotlin
fun pingFromForm(
    providerId: String,
    baseUrl: String,
    model: String,
    apiFormat: ApiFormat
) {
    viewModelScope.launch {
        _pingState.value = PingState.Pinging
        val apikey = secureApiKeyStore.get(providerId)?.takeIf { it.isNotBlank() }
        if (apikey == null) {
            _pingState.value = PingState.Failed("apikey 未配置,请先在 apikey 区域填写")
            return@launch
        }
        val reason = coreAiGateway.ping(providerId, apikey, model, apiFormat)
        _pingState.value = if (reason == null) PingState.Success else PingState.Failed(reason)
    }
}
```

## H2 · LIKE 转义

### 改 `core/note/impl/LlmNoteLinkExtractor.kt:50-52`

```kotlin
val query = sanitize(src.content).take(50)
    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
val q = "%$query%"
```

## H3 · AiHistoryRepository 脱敏

### 改 `core/data/repo/AiHistoryRepository.kt:25-59`

```kotlin
private companion object {
    const val MAX_SNAPSHOT_LEN = 10_000
    const val MAX_ERROR_LEN = 1_000
    const val PRUNE_DAYS = 90L

    val APIKEY_PATTERNS = listOf(
        Regex("""sk-[A-Za-z0-9_\-]{16,}"""),
        Regex("""(?i)Bearer\s+[A-Za-z0-9_\-\.=]{16,}"""),
        Regex("""(?i)x-api-key[:\s]+[A-Za-z0-9_\-\.=]{16,}"""),
    )
}

private fun redact(s: String): String =
    APIKEY_PATTERNS.fold(s) { acc, p -> acc.replace(p, "***REDACTED***") }

suspend fun record(...) {
    val redactedInput = redact(inputSnapshot).take(MAX_SNAPSHOT_LEN)
    val redactedOutput = redact(outputSnapshot).take(MAX_SNAPSHOT_LEN)
    val redactedError = error?.let { redact(it).take(MAX_ERROR_LEN) }
    // ... insert ...
}
```

## H6 · acceptReplace indexOf 校验

### 改 `feature/aiwriting/streaming/AiActionViewModel.kt:188`

```kotlin
val idx = originalContent.indexOf(sourceText)
if (idx < 0) {
    _state.value = AiActionUiState.Failed(op = op, error = AiError.Unknown(null, "原文已被修改,请重新生成"))
    return@withContext
}
if (originalContent.indexOf(sourceText, idx + sourceText.length) >= 0) {
    _state.value = AiActionUiState.Failed(op = op, error = AiError.Unknown(null, "原文有多处匹配,请手动选择"))
    return@withContext
}
val updatedContent = originalContent.replaceRange(idx, idx + sourceText.length, aiText)
```

## H7 · 删 delay + tryEmit

### 改 `feature/aiwriting/streaming/AiActionViewModel.kt:195-199`

删 `delay(150)` + `tryEmit(noteId)` + Log.d 两行。

## H8 · DetailVM 单 collector

### 改 `feature/quicknote/detail/QuickNoteDetailViewModel.kt:46-84`

合并双 launch,删 `noteUpdateEvents` listener 路径,删 Log.d。

## 验收

```bash
./gradlew :app:assembleDebug
./gradlew :app:ktlintCheck
./gradlew :app:lintDebug
./gradlew :app:testDebugUnitTest
grep -rn "runBlocking" app/src/main/java/
grep -nE "import com.yy.writingwithai.feature.aiwriting.(action|streaming)" app/src/main/java/com/yy/writingwithai/feature/quicknote/detail/QuickNoteDetailScreen.kt
grep -nE "import com.yy.writingwithai.feature" app/src/main/java/com/yy/writingwithai/core/
```
