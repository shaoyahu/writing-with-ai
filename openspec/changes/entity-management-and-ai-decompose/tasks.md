## 1. Database Schema & Migration

- [x] 1.1 Add `custom_prompts` table to Room schema (id, prompt_type, content, is_default, created_at, updated_at)
- [x] 1.2 Bump AppDatabase version and add Migration (create `custom_prompts` table)
- [x] 1.3 Create `CustomPromptDao` with CRUD operations
- [x] 1.4 Initialize default entity extraction prompt on first launch

## 2. AI Decompose Implementation

- [x] 2.1 Add `CustomPromptRepository` to read/write custom prompts
- [x] 2.2 Update `LlmEntityExtractor` to use custom prompt from database (fallback to default)
- [x] 2.3 Implement `NoteEntityMatcher` for matching existing entities in note content (case-insensitive)
- [x] 2.4 Update `QuickNoteDetailViewModel.loadCachedEntities()` to auto-match existing entities on open
- [x] 2.5 Add `decompose()` confirmation dialog for re-decompose
- [x] 2.6 Add full-screen loading overlay during decompose
- [x] 2.7 Add API key check with error dialog + navigate to settings

## 3. Entity Highlight Style Update

- [x] 3.1 Replace `TextDecoration.Underline` with `colorScheme.primary` font color in entity span style
- [x] 3.2 Implement cross-star mark (`✦`) overlay at upper-right corner of entity text last character
- [x] 3.3 Ensure cross-star occupies fixed width (8-12dp) and does not overlap adjacent text
- [x] 3.4 Update entity click area to include cross-star mark

## 4. Entity Management UI

- [x] 4.1 Create `EntityManagementScreen` with list display
- [x] 4.2 Add entity count badge to "我的" tab "实体管理" entry
- [x] 4.3 Implement search by `surfaceForm`
- [x] 4.4 Implement filter by entity type (chips/dropdown)
- [x] 4.5 Implement sort options (name, note count, last extracted)
- [x] 4.6 Implement multi-select mode (long-press) and batch delete
- [x] 4.7 Add empty state illustration
- [x] 4.8 Create `EntityDetailScreen` with entity info + associated notes list
- [x] 4.9 Implement context snippet with ellipsis for associated notes
- [x] 4.10 Add single entity delete with confirmation dialog

## 5. Developer Mode

- [x] 5.1 Add `DeveloperModeStore` (DataStore) to persist enabled state
- [x] 5.2 Add version tap counter with random target (5-12) and shake animation
- [x] 5.3 Add "开发者选项" entry to "我的" tab (visible only when enabled)
- [x] 5.4 Create `DeveloperModeScreen` with toggle to disable
- [x] 5.5 Create `PromptEditorScreen` with multi-line text input, save, and reset buttons
- [x] 5.6 Add default entity extraction prompt content (Chinese, with role, types, format, example)

## 6. Localization

- [x] 6.1 Add Chinese strings for all new UI elements (实体管理, 拆解, 重新拆解, 开发者选项, etc.)
- [x] 6.2 Add English strings for all new UI elements

## 7. Navigation

- [x] 7.1 Add "实体管理" route to AppNav
- [x] 7.2 Add "实体详情" route to AppNav
- [x] 7.3 Add "开发者选项" route to AppNav
- [x] 7.4 Add "提示词编辑" route to AppNav

## 8. Testing & Verification

- [x] 8.1 Compile and ktlint check
- [x] 8.2 Run unit tests
- [x] 8.3 Verify on emulator: AI decompose extracts entities and shows blue font + cross-star highlights
- [x] 8.4 Verify on emulator: Auto-match existing entities on note open
- [x] 8.5 Verify on emulator: Entity management list/search/filter/sort/delete
- [x] 8.6 Verify on emulator: Entity detail shows associated notes with context
- [x] 8.7 Verify on emulator: Developer mode activation (version tap) and prompt editing
- [x] 8.8 Verify on emulator: API key check shows error dialog when not configured
