#!/usr/bin/env bash
#
# real-provider-integration §5 · 手动 SSE smoke 脚本。
#
# **用途**:在不装 APK 的情况下,直接对 3 家 provider 真实端点发一条最小流式请求,
#          用于 §7 真机联调前的"环境 + apikey + 网络"三合一快速验证。
#
# **安全约束**(对齐 CLAUDE.md + spec):
# - apikey **只**通过环境变量 `API_KEY` 传入,不在命令行回显 / 不写文件 / 不进日志。
# - 默认 silent(不打印响应体),失败才打印 stderr。
# - 超时 15s 单次(避免 CI 挂死)。
# - 不会自动 commit / 不会写 .gradle / 不会写仓库任何文件。
#
# **用法**:
#   # minimax(默认;Anthropic 兼容 / Authorization: Bearer)
#   API_KEY=sk-xxx ./scripts/real-provider-smoke.sh
#
#   # mimo(CUSTOM_HEADER "api-key",Anthropic 兼容)
#   PROVIDER=mimo API_KEY=sk-xxx ./scripts/real-provider-smoke.sh
#
#   # deepseek(OpenAI 兼容)
#   PROVIDER=deepseek API_KEY=sk-xxx ./scripts/real-provider-smoke.sh
#
#   # 指定模型
#   MODEL=MiniMax-M2.7-highspeed API_KEY=sk-xxx ./scripts/real-provider-smoke.sh
#
# **返回码**: 0 = 流式首字节到达(网络 + auth 通),非 0 = 出错(具体见 stderr)。
#
set -euo pipefail

PROVIDER="${PROVIDER:-minimax}"
MODEL="${MODEL:-}"
API_KEY="${API_KEY:-}"

if [[ -z "${API_KEY}" ]]; then
    echo "ERROR: API_KEY env var required (apikey 不进命令行,只走 env)" >&2
    echo "  usage: API_KEY=sk-xxx $0 [provider]" >&2
    exit 2
fi

# 选择 endpoint + auth 头(对齐 docs/usage/api-<provider>.md)
case "${PROVIDER}" in
    minimax)
        BASE_URL="https://api.minimaxi.com"
        PATH_URL="/anthropic/v1/messages"
        AUTH_HEADER="Authorization: Bearer ${API_KEY}"
        MODEL="${MODEL:-MiniMax-M2.7-highspeed}"
        ;;
    mimo)
        BASE_URL="https://api.xiaomimimo.com"
        PATH_URL="/anthropic/v1/messages"
        AUTH_HEADER="api-key: ${API_KEY}"
        MODEL="${MODEL:-mimo-v2.5-flash}"
        ;;
    deepseek)
        BASE_URL="https://api.deepseek.com"
        PATH_URL="/chat/completions"
        AUTH_HEADER="Authorization: Bearer ${API_KEY}"
        MODEL="${MODEL:-deepseek-v4-flash}"
        ;;
    *)
        echo "ERROR: unknown provider '${PROVIDER}' (use minimax|mimo|deepseek)" >&2
        exit 2
        ;;
esac

# 拼请求体(Anthropic 与 OpenAI 两种 protocol 各自不同)
case "${PROVIDER}" in
    minimax|mimo)
        BODY=$(cat <<JSON
{"model":"${MODEL}","max_tokens":16,"stream":true,
 "system":"You are a smoke test.","messages":[{"role":"user","content":"hi"}]}
JSON
)
        ;;
    deepseek)
        BODY=$(cat <<JSON
{"model":"${MODEL}","max_tokens":16,"stream":true,
 "messages":[
   {"role":"system","content":"You are a smoke test."},
   {"role":"user","content":"hi"}]}
JSON
)
        ;;
esac

# 取消 API_KEY 痕迹(进程列表里仍可见;接受此 risk,因 endpoint 必须发送 header)
# 注意:**不要**用 set +x 或 trap DEBUG 打印变量。
TIMEOUT=15

# curl: --silent --no-buffer --max-time 控制;首字节到达即 exit 0;否则看 HTTP code。
HTTP_CODE=$(
    curl --silent --no-buffer --max-time "${TIMEOUT}" \
         --output /dev/null \
         --write-out "%{http_code}" \
         -X POST "${BASE_URL}${PATH_URL}" \
         -H "Content-Type: application/json" \
         -H "${AUTH_HEADER}" \
         --data "${BODY}"
) || {
    echo "ERROR: curl failed (network / DNS / TLS); see above" >&2
    exit 3
}

# 安全:不打印 BODY / 不打印 API_KEY / 不打印 response body。
# 仅返 HTTP code + provider/model 标识供人判读。
echo "provider=${PROVIDER} model=${MODEL} http=${HTTP_CODE}"

if [[ "${HTTP_CODE}" =~ ^2 ]]; then
    echo "OK: ${PROVIDER} ${MODEL} 接受了请求(auth + endpoint 通)"
    exit 0
elif [[ "${HTTP_CODE}" == "401" ]]; then
    echo "FAIL: ${PROVIDER} 返 401 → apikey 无效 / 走错 header(对照 docs/usage/api-${PROVIDER}.md §2)" >&2
    exit 4
elif [[ "${HTTP_CODE}" == "402" ]]; then
    echo "FAIL: ${PROVIDER} 返 402 → 余额不足" >&2
    exit 5
elif [[ "${HTTP_CODE}" == "404" ]]; then
    echo "FAIL: ${PROVIDER} 返 404 → 模型名不存在(检查 docs/usage/api-${PROVIDER}.md §3)" >&2
    exit 6
elif [[ "${HTTP_CODE}" == "429" ]]; then
    echo "FAIL: ${PROVIDER} 返 429 → 限流(等 Retry-After 后再试)" >&2
    exit 7
elif [[ "${HTTP_CODE}" =~ ^5 ]]; then
    echo "FAIL: ${PROVIDER} 返 ${HTTP_CODE} → 服务端错误(等稍后重试)" >&2
    exit 8
else
    echo "FAIL: ${PROVIDER} 返 ${HTTP_CODE}(未知错误码)" >&2
    exit 9
fi