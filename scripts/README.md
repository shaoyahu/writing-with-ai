# scripts/

本地开发辅助脚本。**不放构建 / CI / 部署脚本**(那部分走 `./gradlew`)。

## `real-provider-smoke.sh`

`real-provider-integration` change §5 的真机前 smoke 工具。直接对 3 家 provider
(deepseek / minimax / mimo)真实端点发一条最小流式请求，验证 **网络 + apikey + endpoint** 三件套，
无需装 APK。

### 安全约束

- apikey **只**通过环境变量 `API_KEY` 传入，命令行 / 日志 / 仓库都不写。
- 默认 silent，失败才打 stderr。
- 15s 单次超时，失败返非 0 退出码。

### 用法

```bash
# minimax(默认;Anthropic 兼容 / Authorization: Bearer)
API_KEY=sk-xxx ./scripts/real-provider-smoke.sh

# mimo(CUSTOM_HEADER "api-key";Anthropic 兼容)
PROVIDER=mimo API_KEY=sk-xxx ./scripts/real-provider-smoke.sh

# deepseek(OpenAI 兼容)
PROVIDER=deepseek API_KEY=sk-xxx ./scripts/real-provider-smoke.sh

# 指定模型
MODEL=MiniMax-M2.7-highspeed API_KEY=sk-xxx ./scripts/real-provider-smoke.sh
```

### 退出码

| code | 含义 |
|---|---|
| 0 | 2xx 首字节到达 — provider auth + endpoint 通 |
| 2 | 入参错(API_KEY 缺 / 未知 provider) |
| 3 | curl 失败(网络 / DNS / TLS) |
| 4 | 401 — apikey 无效或 header 名错 |
| 5 | 402 — 余额不足 |
| 6 | 404 — 模型名不存在 |
| 7 | 429 — 限流 |
| 8 | 5xx — 服务端错误 |
| 9 | 其它未知 HTTP code |

### 适用场景

- 改完 `core/ai/provider/<provider>/` 后，先跑这脚本验 endpoint 还活着再去装 APK 跑 §7。
- 新增 provider 时，先在 `case` 里加一支 `PROVIDER=xxx`，再补 `docs/usage/api-<provider>.md` §2。
- CI 跑不起来(没法装 APK)的场景，用它当最低限度的"真实 provider 还活着"健康检查。

### 不适用场景

- 测 SSE 字段语义 / ContentBlock 解析 / 限流重试 —— 那些必须走 `AiGateway.streamWritingOp()` 真机联调。
- 测自定义 provider 的 `authStyle` / `customHeaders` —— 改走 `custom_provider_test_connection` 设置页入口。

### 注意事项

- 不要 `set -x` 或加 `trap DEBUG`(会把 API_KEY 打出来)。
- 不要把输出 pipe 到 `tee` / 重定向到文件再上传 CI(响应里可能含 provider 上游 message，不一定有 apikey 但偏安全)。
- macOS / Linux 都用 `/usr/bin/env bash`(shebang 固定)，不依赖 bash 4+ 特性，兼容 bash 3.2。