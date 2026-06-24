## ADDED Requirements

### Requirement: Format strings passed to String.format are syntactically valid

Every `<string>` resource consumed via `Context.getString(id, ...args)` and `String.format(...)` MUST be a syntactically valid Java format string. The placeholder count MUST match the caller argument count for both `values/strings.xml` and `values-en/strings.xml`. The Android Lint `StringFormatInvalid` and `StringFormatCount` checks MUST report zero errors on `./gradlew :app:lintDebug`.

#### Scenario: settings_data_save_failed format valid
- **WHEN** reading `res/values/strings.xml` for `settings_data_save_failed`
- **THEN** the string MUST contain a `%s` (or `%1$s`) placeholder if any caller passes arguments; `%d` / `%f` MUST be paired with numeric arguments

#### Scenario: English parity
- **WHEN** reading `res/values-en/strings.xml` for the same key
- **THEN** the English string MUST use the same placeholder set as the default-locale string (matched count and types)

#### Scenario: Lint clean
- **WHEN** `./gradlew :app:lintDebug` runs
- **THEN** the `StringFormatInvalid` error on `SettingsDataScreen.kt:79` MUST NOT appear in the lint report