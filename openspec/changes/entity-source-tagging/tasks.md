## 1. Database Schema & Migration

- [x] 1.1 Add `source` column to `NoteEntityRow` (Room Entity) with default `"AI_EXTRACTED"`
- [x] 1.2 Bump `AppDatabase` version and add Migration (add `source` TEXT column)
- [x] 1.3 Update `NoteEntityDao.upsertAll` to handle new `source` field

## 2. Data Layer Updates

- [x] 2.1 Update `QuickNoteDetailViewModel.addEntityFromSelection()` to set `source = "USER_ADDED"`
- [x] 2.2 Update `LlmEntityExtractor` to set `source = "AI_EXTRACTED"` when creating `NoteEntityRow`
- [x] 2.3 Update `EntityHighlight.toHighlight()` to carry `source` field to UI layer

## 3. Localization

- [x] 3.1 Add `strings.xml` entries for 12 `EntityType` Chinese names
- [x] 3.2 Add `strings.xml` entries for 12 `EntityType` English names
- [x] 3.3 Add `strings.xml` entry for "自定义" / "Custom" label
- [x] 3.4 Create `EntityType.localizedName()` extension function in UI layer

## 4. UI Updates

- [x] 4.1 Modify `QuickNoteDetailScreen` entity sheet title to show `surfaceForm · 自定义` for `USER_ADDED`
- [x] 4.2 Modify `QuickNoteDetailScreen` entity sheet title to show `surfaceForm · 本地化类型名` for `AI_EXTRACTED`
- [x] 4.3 Update `EntityHighlight` data class to include `source` field

## 5. Verification

- [x] 5.1 Compile and ktlint check
- [x] 5.2 Run unit tests
- [x] 5.3 Verify on emulator: user-added entity shows "自定义" tag
- [x] 5.4 Verify on emulator: AI-extracted entity shows localized type name
