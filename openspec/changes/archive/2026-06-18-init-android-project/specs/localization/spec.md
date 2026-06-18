## ADDED Requirements

### Requirement: Strings resolve per system locale

User-visible strings MUST be loaded from `res/values/strings.xml` (default locale, Simplified Chinese) or `res/values-en/strings.xml` (English) according to the device system language.

#### Scenario: Simplified Chinese system shows Chinese strings
- **WHEN** the device locale is `zh-CN` AND the app reads `R.string.app_name`
- **THEN** the returned value is the Chinese `app_name` declared in `values/strings.xml`

#### Scenario: English system shows English strings
- **WHEN** the device locale is `en-US` (or any non-`zh` locale) AND the app reads `R.string.app_name`
- **THEN** the returned value is the English `app_name` declared in `values-en/strings.xml`

### Requirement: strings.xml exists for default and English locales

Both `values/strings.xml` and `values-en/strings.xml` MUST exist and MUST contain at least `app_name` and a placeholder greeting string.

#### Scenario: Both resource files present
- **WHEN** the `app/src/main/res/` directory is listed
- **THEN** `values/strings.xml` AND `values-en/strings.xml` both exist

#### Scenario: Same key set in both locales
- **WHEN** the keys in `values/strings.xml` are compared to those in `values-en/strings.xml`
- **THEN** every key present in the default file is also present in the English file (no missing translations)

### Requirement: Hardcoded Chinese strings in Composable code are blocked by lint

A lint rule MUST flag any Composable code that passes a literal Chinese string to a Composable parameter expecting a `String` resource (e.g., `Text("中文")`).

#### Scenario: Lint warns on hardcoded Chinese
- **WHEN** a Composable contains `Text("你好")` and lint is run via `./gradlew :app:lintDebug`
- **THEN** the lint report contains a warning pointing to the literal Chinese string AND the warning category references `HardcodedText` (Android Lint built-in) or a custom equivalent

#### Scenario: Lint does not warn on stringResource references
- **WHEN** a Composable uses `Text(stringResource(R.string.app_name))` AND lint is run
- **THEN** no `HardcodedText` warning is reported on that line