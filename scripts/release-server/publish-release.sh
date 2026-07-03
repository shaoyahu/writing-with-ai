#!/usr/bin/env bash
# publish-release · 一行发版(APK 托管于 GitHub Releases CDN)
#
# 用法:./publish-release.sh <versionCode> <versionName> <notes.md> <apk> [debug|release]
# 例:./publish-release.sh 3 0.3.0 notes.md app-release.apk
#     ./publish-release.sh 3 0.3.0 notes.md app-debug.apk debug
#
# 步骤(串行,任一失败立即退出):
#   1. 本地计算 APK SHA-256
#   2. gh release create + upload APK 到 GitHub
#   3. 本地生成 version.json(apkUrl 指向 GitHub CDN)
#   4. 本地生成 index.html(下载按钮指向 GitHub CDN)
#   5. scp version.json + index.html + release notes 到服务器
#   6. 不上传 APK 到服务器(GitHub CDN 承担下载流量)
#
# 幂等:重跑覆盖同名文件,version.json 与 index.html 重新派生。
#       GitHub Release 已存在时跳过 create,仅覆盖 upload。

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

# 检查 APK 文件存在
if [[ ! -f "$APK" ]]; then
  echo "error: APK not found: $APK" >&2
  exit 1
fi

# 检查 gh CLI 可用
if ! command -v gh &>/dev/null; then
  echo "error: gh CLI not found. Install: https://cli.github.com/" >&2
  exit 1
fi

if ! gh auth status &>/dev/null; then
  echo "error: gh not authenticated. Run: gh auth login" >&2
  exit 1
fi

GITHUB_REPO="${GITHUB_REPO:-shaoyahu/writing-with-ai}"
SERVER="${RELEASE_SERVER:-server}"
REMOTE_BASE="${REMOTE_BASE_DIR:-/var/www/xiaozha/app}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [[ "$CHANNEL" == "debug" ]]; then
  APK_NAME="writing-with-ai-debug-${CODE}.apk"
  TAG="v${CODE}-debug"
else
  APK_NAME="writing-with-ai-${CODE}.apk"
  TAG="v${CODE}"
fi

echo "=== publish-release (${CHANNEL}) ==="
echo "  code:       ${CODE}"
echo "  name:       ${NAME}"
echo "  channel:    ${CHANNEL}"
echo "  apk:        ${APK}"
echo "  notes:      ${NOTES}"
echo "  tag:        ${TAG}"
echo "  github:     ${GITHUB_REPO}"
echo "  server:     ${SERVER}:${REMOTE_BASE}"
echo

SSH_OPTS="-o ConnectTimeout=10 -o ServerAliveInterval=30 -o ServerAliveCountMax=3"
SCP_OPTS="-o ConnectTimeout=10"

# 临时目录(清理用)
TMPDIR_WORK=$(mktemp -d)
trap 'rm -rf "$TMPDIR_WORK"' EXIT

# ─── Step 0: 确保 GitHub Release 存在 ───
echo "[0/5] ensure GitHub Release exists (${TAG})..."
if gh release view "$TAG" --repo "$GITHUB_REPO" &>/dev/null; then
  echo "  Release ${TAG} already exists, skipping create."
else
  echo "  Creating release ${TAG}..."
  gh release create "$TAG" \
    --repo "$GITHUB_REPO" \
    --title "${TAG}" \
    --notes-file "$NOTES"
fi

# ─── Step 1: 上传 APK 到 GitHub Release ───
echo "[1/5] upload APK to GitHub Release..."
# 如果 APK 路径与目标文件名不同,先复制到临时文件
UPLOAD_APK="$APK"
if [[ "$(basename "$APK")" != "$APK_NAME" ]]; then
  cp "$APK" "$TMPDIR_WORK/$APK_NAME"
  UPLOAD_APK="$TMPDIR_WORK/$APK_NAME"
fi
gh release upload "$TAG" "$UPLOAD_APK" \
  --repo "$GITHUB_REPO" \
  --clobber

# ─── Step 2: 本地生成 version.json ───
echo "[2/5] generate version.json locally..."
VERSION_JSON="$TMPDIR_WORK/version.json"
python3 "$SCRIPT_DIR/build-version-json-local.py" \
  --version-code "$CODE" \
  --version-name "$NAME" \
  --channel "$CHANNEL" \
  --apk "$APK" \
  --notes "$NOTES" \
  --github-repo "$GITHUB_REPO" \
  > "$VERSION_JSON"

# 验证 JSON 合法
python3 -c "import json; json.load(open('$VERSION_JSON'))" || {
  echo "error: generated version.json is invalid" >&2
  exit 1
}

echo "  apkUrl: $(python3 -c "import json; print(json.load(open('$VERSION_JSON'))['apkUrl'])")"

# ─── Step 3: 本地生成 index.html ───
echo "[3/5] generate index.html locally..."
INDEX_HTML="$TMPDIR_WORK/index.html"
python3 "$SCRIPT_DIR/build-index-local.py" \
  --current "$CHANNEL" \
  --version-json "$VERSION_JSON" \
  > "$INDEX_HTML"

# ─── Step 4: scp 元数据到服务器 ───
echo "[4/5] scp metadata to server..."
# 确保目录存在
ssh $SSH_OPTS "${SERVER}" "mkdir -p ${REMOTE_BASE}/${CHANNEL}/release-notes"

# version.json
scp $SCP_OPTS "$VERSION_JSON" "${SERVER}:${REMOTE_BASE}/${CHANNEL}/version.json"

# index.html
scp $SCP_OPTS "$INDEX_HTML" "${SERVER}:${REMOTE_BASE}/index.html"

# release notes
scp $SCP_OPTS "$NOTES" "${SERVER}:${REMOTE_BASE}/${CHANNEL}/release-notes/${CODE}.md"

# ─── Step 5: 验证 ───
echo "[5/5] verify deployment..."
sleep 1  # 等服务器文件系统刷新

MANIFEST_URL="https://xiaozha.nananxue.cn/app/${CHANNEL}/version.json"
REMOTE_APK_URL=$(ssh $SSH_OPTS "${SERVER}" "cat ${REMOTE_BASE}/${CHANNEL}/version.json" 2>/dev/null | python3 -c "import json,sys; print(json.load(sys.stdin)['apkUrl'])" 2>/dev/null || echo "FETCH_FAILED")

echo
echo "=== 完成 ==="
echo "下载页:      https://xiaozha.nananxue.cn/app/"
echo "manifest:    ${MANIFEST_URL}"
echo "apkUrl:      ${REMOTE_APK_URL}"
echo "GitHub tag:  ${TAG}"
