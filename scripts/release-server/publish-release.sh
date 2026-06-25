#!/usr/bin/env bash
# app-self-hosted-update · 一行发版(支持双通道)
#
# 用法:./publish-release.sh <versionCode> <versionName> <notes.md> <apk> [debug|release]
# 例:./publish-release.sh 2 0.2.0 notes.md app-release.apk
#     ./publish-release.sh 2 0.2.0 notes.md app-debug.apk debug
#
# 步骤(串行,任一失败立即退出):
#   1. scp APK 到服务器对应通道目录
#   2. scp release notes
#   3. ssh 重跑 build-version-json.py --channel
#   4. ssh 重跑 build-index.py(双通道合并下载页)
#
# 幂等:重跑覆盖同名文件,version.json 与 index.html 重新派生。

set -euo pipefail

if [[ $# -lt 4 ]]; then
  echo "用法: $0 <versionCode> <versionName> <notes.md> <apk> [debug|release]" >&2
  exit 1
fi

CODE=$1
NAME=$2
NOTES=$3
APK=$4
CHANNEL=${5:-release}

if [[ "$CHANNEL" != "debug" && "$CHANNEL" != "release" ]]; then
  echo "error: channel must be 'debug' or 'release', got '$CHANNEL'" >&2
  exit 1
fi

SERVER="${RELEASE_SERVER:-root@xiaozha.nananxue.cn}"
REMOTE_BASE="${REMOTE_BASE_DIR:-/var/www/xiaozha/app}"
REMOTE_DIR="${REMOTE_BASE}/${CHANNEL}"

if [[ "$CHANNEL" == "debug" ]]; then
  APK_NAME="writing-with-ai-debug-${CODE}.apk"
else
  APK_NAME="writing-with-ai-${CODE}.apk"
fi
NOTES_NAME="${CODE}.md"

echo "=== publish-release (${CHANNEL}) ==="
echo "  code:      ${CODE}"
echo "  name:      ${NAME}"
echo "  channel:   ${CHANNEL}"
echo "  apk:       ${APK}"
echo "  notes:     ${NOTES}"
echo "  server:    ${SERVER}:${REMOTE_DIR}"
echo

SSH_OPTS="-o ConnectTimeout=10 -o ServerAliveInterval=30 -o ServerAliveCountMax=3"
SCP_OPTS="-o ConnectTimeout=10"

# 0. 确保目录存在
echo "[0/4] mkdir -p..."
ssh $SSH_OPTS "${SERVER}" "mkdir -p ${REMOTE_DIR}/release-notes"

# 1. 上传 APK
echo "[1/4] scp ${APK_NAME}..."
scp $SCP_OPTS "${APK}" "${SERVER}:${REMOTE_DIR}/${APK_NAME}"

# 2. 上传 release notes
echo "[2/4] scp ${NOTES_NAME}..."
scp $SCP_OPTS "${NOTES}" "${SERVER}:${REMOTE_DIR}/release-notes/${NOTES_NAME}"

# 3. 重生成 version.json
echo "[3/4] rebuild version.json (${CHANNEL})..."
ssh $SSH_OPTS "${SERVER}" "cd ${REMOTE_BASE} && ./build-version-json.py --channel ${CHANNEL} > ${CHANNEL}/version.json"

# 4. 重生成下载页 index.html(双通道合并,默认 --channel all)
echo "[4/4] rebuild index.html..."
ssh $SSH_OPTS "${SERVER}" "cd ${REMOTE_BASE} && ./build-index.py"

echo
echo "=== 完成 ==="
echo "下载页:    https://xiaozha.nananxue.cn/app/"
echo "manifest:  https://xiaozha.nananxue.cn/app/${CHANNEL}/version.json"
