# 隐私条款

最后更新:2026-06-19

## 一、数据存储

本应用所有数据(**笔记、标签、AI 调用历史**)均存储在**您本机设备**上，不向任何服务器上传您的笔记内容。

我们**不**运营云端后端，**不**收集用户账户信息。卸载 App 即清除全部本地数据。

## 二、AI 功能与数据流

启用 AI 操作(扩写 / 润色 / 整理)时:

1. 您**手动选中的文本片段**会随 HTTPS 请求发送至您配置的 AI provider
2. 默认 provider 为 `deepseek`(Anthropic Messages API 兼容);您可在设置中切换或填入自建 provider
3. **您的 apikey** 通过 Android Keystore + Tink AES256_GCM 加密后存于本机 `EncryptedSharedPreferences`,**不**进入 logcat / Room / Auto Backup
4. AI 返回的文本**不会**保存到本应用之外的任何位置

## 三、第三方 AI provider 列表

| provider | base URL | 数据处理地 |
| --- | --- | --- |
| deepseek | `https://api.deepseek.com` | provider 决定 |
| minimax | `https://api.minimax.chat` | provider 决定 |
| mimo | `https://api.mimo.example.com` | provider 决定 |
| 自定义 | 用户填写 | 用户自定 |

各家具体协议见应用内"AI provider 协议说明"。

## 四、如何撤回同意

- **拒绝**:在同意页点击"拒绝并退出",App 立即退出
- **撤回已同意**:在设置页(开发中)选择"撤回同意"，已存 apikey 同步清除，下次启动重新走同意页
- **清除 apikey**:在设置页(开发中)选择"清除 apikey"，对应 provider 的 apikey 立即从加密存储中移除

## 五、联系方式

- 应用开发者:`com.yy.writingwithai`
- 反馈渠道:应用内"关于"页(开发中)

---

继续使用即表示您已阅读并同意以上条款。
