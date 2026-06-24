#!/usr/bin/env bash
# app-self-hosted-update · 一行发版
#
# 用法:./publish-release.sh <versionCode> <versionName> <notes.md> <app-release.apk>
# 例:./publish-release.sh 12 0.5.0 release-notes/12.md app-release.apk
#
# 步骤(串行,任一失败立即退出):
#   1. scp APK 到服务器
#   2. scp release notes 到服务器
#   3. ssh 更新 latest 软链
#   4. ssh 重跑 build-version-json.py + build-index.py
#
# 幂等:重跑覆盖同名文件,version.json 重新派生。

set -euo pipefail

if [[ $# -ne 4 ]]; then
  echo "用法: $0 <versionCode> <versionName> <notes.md> <app-release.apk>" >&2
  exit 1
fi

CODE=$1
NAME=$2
NOTES=$3
APK=$4

SERVER="${RELEASE_SERVER:-root@xiaozha.nananxue.cn}"
REMOTE_DIR="${REMOTE_DOWNLOAD_DIR:-/var/www/xiaozha/app/download}"

APK_NAME="writing-with-ai-${CODE}.apk"
NOTES_NAME="${CODE}.md"

echo "=== publish-release ==="
echo "  code:      ${CODE}"
echo "  name:      ${NAME}"
echo "  apk:       ${APK}"
echo "  notes:     ${NOTES}"
echo "  server:    ${SERVER}:${REMOTE_DIR}"
echo

SSH_OPTS="-o ConnectTimeout=10 -o ServerAliveInterval=30 -o ServerAliveCountMax=3"
SCP_OPTS="-o ConnectTimeout=10"

# 1. 上传 APK
echo "[1/4] scp ${APK_NAME}..."
scp $SCP_OPTS "${APK}" "${SERVER}:${REMOTE_DIR}/${APK_NAME}"

# 2. 上传 release notes(确保目录存在)
echo "[2/4] scp ${NOTES_NAME}..."
ssh $SSH_OPTS "${SERVER}" "mkdir -p ${REMOTE_DIR}/release-notes"
scp $SCP_OPTS "${NOTES}" "${SERVER}:${REMOTE_DIR}/release-notes/${NOTES_NAME}"

# 3. 更新 latest 软链(装饰用,version.json 不依赖它)
echo "[3/4] ln -sfn latest.apk..."
ssh $SSH_OPTS "${SERVER}" "cd ${REMOTE_DIR} && ln -sfn ${APK_NAME} latest.apk"

# 4. 重生成 manifest + index.html
echo "[4/4] rebuild version.json + index.html..."
ssh $SSH_OPTS "${SERVER}" "cd ${REMOTE_DIR} && ./build-version-json.py > version.json && ./build-index.py"

echo
echo "=== 完成 ==="
echo "下载页:    https://xiaozha.nananxue.cn/app/download/"
echo "manifest:  https://xiaozha.nananxue.cn/app/version.json"