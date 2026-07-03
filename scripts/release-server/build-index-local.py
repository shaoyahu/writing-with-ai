#!/usr/bin/env python3
"""
publish-release · 本地生成下载页 index.html,下载链接指向 GitHub Releases CDN。

不再扫描服务器目录:
- 当前通道数据:从本地参数(直接传参或读 version.json)
- 另一通道数据:从服务器 curl 现有 version.json 获取

用法:
  python3 build-index-local.py --current release --version-json ./release/version.json
  python3 build-index-local.py --current debug --version-json ./debug/version.json

设计要点:
- 双通道入口合并一页两卡片
- 下载按钮 href 用完整 GitHub Releases URL(不再是 /app/{channel}/{file} 相对路径)
- 纯 stdlib,跨平台
"""
import argparse
import hashlib
import json
import sys
import urllib.request
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
TEMPLATE = SCRIPT_DIR / "index.html.template"

VERSION_JSON_BASE_URL = "https://xiaozha.nananxue.cn/app"

CHANNEL_CONFIG = {
    "release": {
        "label": "正式版",
        "tagline": "推荐日常使用 · R8 混淆 · 体积小",
        "badge_class": "badge-release",
    },
    "debug": {
        "label": "测试版",
        "tagline": "开发者调试用 · 体积大 · 可与正式版共存",
        "badge_class": "badge-debug",
    },
}


def format_size(size_bytes: int) -> str:
    if size_bytes < 1024:
        return f"{size_bytes} B"
    if size_bytes < 1024 * 1024:
        return f"{size_bytes / 1024:.1f} KB"
    return f"{size_bytes / (1024 * 1024):.1f} MB"


def sha256_short(sha: str) -> str:
    return f"{sha[:8]}…{sha[-8:]}"


def _escape(s: str) -> str:
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def _markdown_to_html(md: str) -> str:
    """极简 markdown -> HTML,只处理代码块/段落/换行。"""
    if not md.strip():
        return "<p>(无更新日志)</p>"
    lines = md.split("\n")
    out = []
    in_code = False
    buf = []
    for line in lines:
        if line.strip().startswith("```"):
            if in_code:
                out.append("<pre><code>" + _escape("\n".join(buf)) + "</code></pre>")
                buf = []
            in_code = not in_code
            continue
        if in_code:
            buf.append(line)
        else:
            stripped = line.strip()
            if not stripped:
                continue
            out.append(f"<p>{_escape(stripped)}</p>")
    if in_code and buf:
        out.append("<pre><code>" + _escape("\n".join(buf)) + "</code></pre>")
    return "\n".join(out)


def manifest_to_card_data(manifest: dict, channel: str) -> dict:
    """将 version.json manifest 转为渲染卡片所需的 dict。"""
    cfg = CHANNEL_CONFIG[channel]
    apk_url = manifest["apkUrl"]
    apk_name = apk_url.rsplit("/", 1)[-1]
    sha = manifest.get("apkSha256", "")
    size = manifest.get("apkSize", 0)
    released_at = manifest.get("releasedAt", "")
    # 从 ISO 8601 提取日期部分
    release_date = released_at[:10] if released_at else "unknown"
    notes = manifest.get("releaseNotes", "")

    return {
        "channel": channel,
        "label": cfg["label"],
        "tagline": cfg["tagline"],
        "badge_class": cfg["badge_class"],
        "code": manifest["versionCode"],
        "apk_name": apk_name,
        "apk_url": apk_url,
        "apk_size": format_size(size),
        "sha256": sha,
        "sha256_short": sha256_short(sha) if sha else "",
        "release_date": release_date,
        "notes_html": _markdown_to_html(notes),
    }


def render_channel_card(c: dict) -> str:
    """渲染单个通道卡片 HTML。下载按钮 href 用完整 GitHub URL。"""
    return f"""
    <article class="card {c['badge_class']}">
      <div class="card-head">
        <span class="badge {c['badge_class']}">{c['label']}</span>
        <h2>v{c['code']} <span class="muted">· {c['release_date']}</span></h2>
      </div>
      <p class="tagline">{c['tagline']}</p>
      <a class="btn-download" href="{c['apk_url']}" download>
        <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
          <path d="M12 3a1 1 0 0 1 1 1v8.59l2.3-2.3a1 1 0 1 1 1.4 1.42l-4 4a1 1 0 0 1-1.4 0l-4-4a1 1 0 1 1 1.4-1.42L11 12.6V4a1 1 0 0 1 1-1zM5 17a1 1 0 0 1 1 1v1h12v-1a1 1 0 1 1 2 0v2a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1v-2a1 1 0 0 1 1-1z"/>
        </svg>
        下载 APK ({c['apk_size']})
      </a>
      <div class="meta">
        <div>文件名:<code>{c['apk_name']}</code></div>
        <div>SHA-256:<code title="{c['sha256']}">{c['sha256_short']}</code></div>
      </div>
      <details>
        <summary>更新日志</summary>
        {c['notes_html']}
      </details>
    </article>
    """


def fetch_remote_manifest(channel: str) -> dict | None:
    """从服务器 curl 另一通道的 version.json。"""
    url = f"{VERSION_JSON_BASE_URL}/{channel}/version.json"
    try:
        req = urllib.request.Request(url, headers={"Accept": "application/json"})
        with urllib.request.urlopen(req, timeout=10) as resp:
            return json.loads(resp.read())
    except Exception as e:
        print(f"warn: cannot fetch {url}: {e}", file=sys.stderr)
        return None


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Build download index.html locally with GitHub Releases URLs"
    )
    parser.add_argument(
        "--current",
        choices=["debug", "release"],
        required=True,
        help="Which channel is being published now",
    )
    parser.add_argument(
        "--version-json",
        type=Path,
        required=True,
        help="Path to the locally generated version.json for the current channel",
    )
    args = parser.parse_args()

    if not TEMPLATE.exists():
        print(f"error: template not found: {TEMPLATE}", file=sys.stderr)
        sys.exit(1)

    if not args.version_json.exists():
        print(f"error: version.json not found: {args.version_json}", file=sys.stderr)
        sys.exit(1)

    # 当前通道数据:读本地 version.json
    current_manifest = json.loads(args.version_json.read_text(encoding="utf-8"))
    current_data = manifest_to_card_data(current_manifest, args.current)

    # 另一通道数据:从服务器 curl
    other_channel = "debug" if args.current == "release" else "release"
    other_manifest = fetch_remote_manifest(other_channel)
    other_data = None
    if other_manifest:
        # 检查另一个通道的 apkUrl 是否也是 GitHub URL
        # 如果仍然是旧的自托管 URL,则跳过该通道(不渲染卡片)
        if other_manifest.get("apkUrl", "").startswith("https://github.com/"):
            other_data = manifest_to_card_data(other_manifest, other_channel)
        else:
            print(
                f"warn: {other_channel} channel still uses self-hosted URL, skipping card",
                file=sys.stderr,
            )

    # 按顺序渲染:release 在前,debug 在后
    channels_order = ["release", "debug"]
    cards = []
    for ch in channels_order:
        if ch == args.current:
            cards.append(render_channel_card(current_data))
        elif other_data and ch == other_channel:
            cards.append(render_channel_card(other_data))

    if not cards:
        print("error: no channel data available", file=sys.stderr)
        sys.exit(1)

    template = TEMPLATE.read_text(encoding="utf-8")
    rendered = template.replace("{{CHANNEL_CARDS}}", "\n".join(cards))

    # 输出到 stdout
    sys.stdout.write(rendered)


if __name__ == "__main__":
    main()
