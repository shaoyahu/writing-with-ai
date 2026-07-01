# 飞书应用凭证配置(feishu-app-credentials)

本工具通过飞书「**tenant_access_token**」路线接入，不走 user OAuth(无需浏览器跳转、无 App Link、无 refresh_token)。

本文档记录用户**一次性**操作:在飞书开放平台创建「企业内部应用」并拿到 `app_id` / `app_secret`，然后粘到本工具设置页。

## 前置条件

- 一个飞书账号(可以是个人账号，但需要所在企业开通了飞书)
- 企业管理员同意你在企业内部创建应用
- 本工具已编译安装

## 步骤

### 1. 进入飞书开放平台

访问 <https://open.feishu.cn/app>，用飞书账号登录。

### 2. 创建企业内部应用

点击「**创建企业自建应用**」→「**企业自建应用**」(不是「商店应用」)，填写:

- 应用名称:任意(如「小札同步」)
- 应用描述:任意
- 应用图标:可选

创建完成后进入应用详情页。

### 3. 获取 app_id 和 app_secret

在「**凭证与基础信息**」页面:

- **App ID**:一串 `cli_xxx` 开头的字符串
- **App Secret**:点击「查看」获取一串随机字符串

把这两个值复制到本工具设置页 → 飞书授权 → 输入框。

> ⚠️ app_secret 等同于密码，**不要**截图分享、不要发到公开仓库、不要写到本工具的 note 内容里。
> 本工具走 EncryptedSharedPreferences 加密存储(AES256_GCM)，不会出现在 logcat 或备份中。

### 4. 配置权限

在「**权限管理**」页面，开通以下权限(范围:docx 文档):

- `docx:document:create` — 创建文档
- `docx:document:readonly` — 读取文档
- `docx:document:update` — 更新文档

> 这些是「应用身份」权限，不需要用户授权弹窗，但需要**企业管理员**在「管理后台」→「工作台」→「应用审核」中审批通过。

### 5. (可选) 配置应用可见范围

默认本应用对自己可见，够用。如果希望其他飞书用户也能看到同步的文档，在「**版本管理与发布**」中设置可见范围。

### 6. 在本工具里启用

打开本工具 → 设置 → 飞书授权:

1. 把 `app_id` 粘到「app_id」输入框
2. 把 `app_secret` 粘到「app_secret」输入框(5s 后自动 mask)
3. 点击「**保存并连接**」
4. 工具会 POST `https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal` 取 token
5. 成功后状态显示「已连接」

## 验证

「连接飞书」成功后，你可以在「详情页 → ... → 同步到飞书」创建一篇飞书 docx 文档(由 `feishu-bidir-sync` change 提供)。

## 故障排查

| 现象 | 可能原因 |
|---|---|
| 错误「飞书凭证未配置」 | app_id / app_secret 没保存，先点「保存」再点「连接」 |
| 错误「飞书业务错误 10003: invalid app_id」 | app_id 输错，或 app 已被删除 |
| 错误「飞书业务错误 10014: invalid app_secret」 | app_secret 输错，或泄露后被重置 |
| 错误「飞书权限不足」 | 没在飞书后台开权限，或权限范围不够 |
| 错误「飞书 token 失效」连接后再次失败 | app 已被禁用 / 删除 / 收回，重新走步骤 2-6 |

## 不在本文档范围

- 用户身份 OAuth(本工具不需要，自用场景)
- Lark 国际版(`open.larksuite.com` 暂不支持)
- PKCE 流程
- 自建后端中转 token(本工具纯本地)
- 图片 / 文件上传(留给后续 change)