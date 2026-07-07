## 1. Database Schema & Migration

- [ ] 1.1 Add `custom_prompts` table to Room schema (id, prompt_type, content, is_default, created_at, updated_at)
- [ ] 1.2 Bump AppDatabase version and add Migration (create `custom_prompts` table)
- [ ] 1.3 Create `CustomPromptDao` with CRUD operations
- [ ] 1.4 Initialize default entity extraction prompt on first launch

## 2. AI Decompose Implementation

- [ ] 2.1 Add `CustomPromptRepository` to read/write custom prompts
- [ ] 2.2 Update `LlmEntityExtractor` to use custom prompt from database (fallback to default)
- [ ] 2.3 Implement `NoteEntityMatcher` for matching existing entities in note content (case-insensitive)
- [ ] 2.4 Update `QuickNoteDetailViewModel.loadCachedEntities()` to auto-match existing entities on open
- [ ] 2.5 Add `decompose()` confirmation dialog for re-decompose
- [ ] 2.6 Add full-screen loading overlay during decompose
- [ ] 2.7 Add API key check with error dialog + navigate to settings

## 3. Entity Management UI

- [ ] 3.1 Create `EntityManagementScreen` with list display
- [ ] 3.2 Add entity count badge to "我的" tab "实体管理" entry
- [ ] 3.3 Implement search by `surfaceForm`
- [ ] 3.4 Implement filter by entity type (chips/dropdown)
- [ ] 3.5 Implement sort options (name, note count, last extracted)
- [ ] 3.6 Implement multi-select mode (long-press) and batch delete
- [ ] 3.7 Add empty state illustration
- [ ] 3.8 Create `EntityDetailScreen` with entity info + associated notes list
- [ ] 3.9 Implement context snippet with ellipsis for associated notes
- [ ] 3.10 Add single entity delete with confirmation dialog

## 4. Developer Mode

- [ ] 4.1 Add `DeveloperModeStore` (DataStore) to persist enabled state
- [ ] 4.2 Add version tap counter with random target (5-12) and shake animation
- [ ] 4.3 Add "开发者选项" entry to "我的" tab (visible only when enabled)
- [ ] 4.4 Create `DeveloperModeScreen` with toggle to disable
- [ ] 4.5 Create `PromptEditorScreen` with multi-line text input, save, and reset buttons
- [ ] 4.6 Add default entity extraction prompt content (Chinese, with role, types, format, example)

## 5. Localization

- [ ] 5.1 Add Chinese strings for all new UI elements (实体管理, 拆解, 重新拆解, 开发者选项, etc.)
- [ ] 5.2 Add English strings for all new UI elements

## 6. Navigation

- [ ] 6.1 Add "实体管理" route to AppNav
- [ ] 6.2 Add "实体详情" route to AppNav
- [ ] 6.3 Add "开发者选项" route to AppNav
- [ ] 6.4 Add "提示词编辑" route to AppNav

## 7. Testing & Verification

- [ ] 7.1 Compile and ktlint check
- [ ] 7.2 Run unit tests
- [ ] 7.3 Verify on emulator: AI decompose extracts entities and shows highlights
- [ ] 7.4 Verify on emulator: Auto-match existing entities on note open
- [ ] 7.5 Verify on emulator: Entity management list/search/filter/sort/delete
- [ ] 7.6 Verify on emulator: Entity detail shows associated notes with context
- [ ] 7.7 Verify on emulator: Developer mode activation (version tap) and prompt editing
- [ ] 7.8 Verify on emulator: API key check shows error dialog when not configured
