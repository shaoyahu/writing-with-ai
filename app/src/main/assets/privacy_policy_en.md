# Privacy Policy

Last updated: 2026-06-19

## 1. Data Storage

All data in this app (**notes, tags, AI call history**) is stored **locally on your device**. Your note content is never uploaded to any server we operate.

We **do not** run a cloud backend and **do not** collect user account information. Uninstalling the app removes all local data.

## 2. AI Features and Data Flow

When AI operations (expand / polish / organize) are enabled:

1. The **text fragment you manually select** is sent over HTTPS to the AI provider you configured
2. The default provider is `deepseek` (Anthropic Messages API compatible); you can switch or add a custom provider in Settings
3. **Your API key** is encrypted with Android Keystore + Tink AES256_GCM and stored in `EncryptedSharedPreferences` on device. It **never** enters logcat / Room / Auto Backup
4. AI responses are **not** persisted outside this app

## 3. Third-Party AI Providers

| Provider | Base URL | Data Processing |
| --- | --- | --- |
| deepseek | `https://api.deepseek.com` | Decided by provider |
| minimax | `https://api.minimax.chat` | Decided by provider |
| mimo | `https://api.mimo.example.com` | Decided by provider |
| Custom | User-defined | User-defined |

See the in-app "AI Provider Protocol" page for each provider's specification.

## 4. How to Withdraw Consent

- **Decline**: Tap "Decline and Exit" on the consent screen. The app exits immediately.
- **Revoke consent**: In Settings (under development), choose "Revoke Consent". Stored API keys are cleared, and the consent screen reappears on next launch.
- **Clear API key**: In Settings (under development), choose "Clear API Key". The corresponding provider's API key is removed from encrypted storage immediately.

## 5. Contact

- App developer: `com.yy.writingwithai`
- Feedback: in-app "About" page (under development)

---

Continued use indicates that you have read and agreed to the above terms.
