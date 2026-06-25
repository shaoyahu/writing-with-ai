#!/usr/bin/env python3
"""
app-self-hosted-update · 扫指定通道 APK 目录 + release-notes,渲染 index.html。

支持双通道(debug / release):各自扫各自目录,各取最新 APK,写一份合并的
index.html 到 BASE_DIR(下载页根)展示双卡片。

用法:
  python3 build-index.py                       # 默认:合并两通道到单页
  python3 build-index.py --channel release     # 仅 release
  python3 build-index.py --channel debug       # 仅 debug

设计要点:
- 双通道入口合并在 BASE_DIR/index.html 一页两卡片,而不是 BASE_DIR/{debug,release}/index.html,
  避免用户从根 URL 跳进子目录还要二次跳转
- 模板占位符统一为 {{CHANNEL_CARDS}},循环渲染各通道 section
- 纯 stdlib,跨平台
"""
import argparse
import hashlib
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

BASE_DIR = Path("/var/www/xiaozha/app")
TEMPLATE = BASE_DIR / "index.html.template"
NOTES_DIRNAME = "release-notes"

CHANNEL_CONFIG = {
    "release": {
        "label": "正式版",
        "tagline": "推荐日常使用 · R8 混淆 · 体积小",
        "dir": BASE_DIR / "release",
        "pattern": re.compile(r"^writing-with-ai-(\d+)\.apk$"),
        "url_prefix": "https://xiaozha.nananxue.cn/app/release",
        "badge_class": "badge-release",
    },
    "debug": {
        "label": "测试版",
        "tagline": "开发者调试用 · 体积大 · 可与正式版共存",
        "dir": BASE_DIR / "debug",
        "pattern": re.compile(r"^writing-with-ai-debug-(\d+)\.apk$"),
        "url_prefix": "https://xiaozha.nananxue.cn/app/debug",
        "badge_class": "badge-debug",
    },
}


def format_size(size_bytes: int) -> str:
    if size_bytes < 1024:
        return f"{size_bytes} B"
    if size_bytes < 1024 * 1024:
        return f"{size_bytes / 1024:.1f} KB"
    return f"{size_bytes / (1024 * 1024):.1f} MB"


def format_time(epoch: float) -> str:
    return datetime.fromtimestamp(epoch, tz=timezone.utc).strftime("%Y-%m-%d")


def sha256_short(sha: str) -> str:
    return f"{sha[:8]}…{sha[-8:]}"


def parse_version_code(path: Path, pattern: re.Pattern) -> int:
    m = pattern.match(path.name)
    if not m:
        raise ValueError(f"unexpected filename: {path.name}")
    return int(m.group(1))


def _escape(s: str) -> str:
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def _markdown_to_html(md: str) -> str:
    """极简 markdown → HTML,只处理代码块/段落/换行。"""
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


def render_history_html(apks: list[Path], pattern: re.Pattern, url_prefix: str) -> str:
    items = []
    for apk in sorted(apks, key=lambda p: parse_version_code(p, pattern), reverse=True):
        code = parse_version_code(apk, pattern)
        size = format_size(apk.stat().st_size)
        mtime = format_time(apk.stat().st_mtime)
        items.append(
            f'<li><a href="{url_prefix}/{apk.name}">code {code}</a> · {size} · {mtime}</li>'
        )
    return "\n".join(items) if items else "<li>暂无历史版本</li>"


def collect_channel(channel: str) -> dict | None:
    """扫某通道目录,返回渲染所需 dict;目录不存在/无 APK 时返回 None。"""
    cfg = CHANNEL_CONFIG[channel]
    scan_dir = cfg["dir"]
    if not scan_dir.exists():
        return None
    apks = [p for p in scan_dir.iterdir() if cfg["pattern"].match(p.name)]
    if not apks:
        return None
    apks_sorted = sorted(apks, key=lambda p: parse_version_code(p, cfg["pattern"]))
    latest = apks_sorted[-1]
    code = parse_version_code(latest, cfg["pattern"])
    notes_path = scan_dir / NOTES_DIRNAME / f"{code}.md"
    notes = (
        notes_path.read_text(encoding="utf-8").strip()
        if notes_path.exists()
        else "(无更新日志)"
    )
    sha = hashlib.sha256()
    with latest.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            sha.update(chunk)
    sha_hex = sha.hexdigest()
    return {
        "channel": channel,
        "label": cfg["label"],
        "tagline": cfg["tagline"],
        "badge_class": cfg["badge_class"],
        "code": code,
        "apk_name": latest.name,
        "apk_size": format_size(latest.stat().st_size),
        "sha256": sha_hex,
        "sha256_short": sha256_short(sha_hex),
        "release_date": format_time(latest.stat().st_mtime),
        "notes_html": _markdown_to_html(notes),
        "history_html": render_history_html(apks, cfg["pattern"], cfg["url_prefix"]),
    }


def render_channel_card(c: dict) -> str:
    return f"""
    <section class="card {c['badge_class']}">
      <div class="card-head">
        <span class="badge {c['badge_class']}">{c['label']}</span>
        <h2>v{c['code']} <span class="muted">· {c['release_date']}</span></h2>
      </div>
      <p class="tagline">{c['tagline']}</p>
      <p>
        <a class="btn-download" href="/app/{c['channel']}/{c['apk_name']}" download>
          下载 APK ({c['apk_size']})
        </a>
      </p>
      <div class="meta">
        <div>文件名:<code>{c['apk_name']}</code></div>
        <div>SHA-256:<code title="{c['sha256']}">{c['sha256_short']}</code></div>
      </div>
      <details>
        <summary>更新日志</summary>
        {c['notes_html']}
      </details>
      <details>
        <summary>历史版本</summary>
        <ul>{c['history_html']}</ul>
      </details>
    </section>
    """


def main() -> None:
    parser = argparse.ArgumentParser(description="Render download index.html (dual-channel)")
    parser.add_argument(
        "--channel",
        choices=["debug", "release", "all"],
        default="all",
        help="Which channel to render; 'all' (default) renders both into single page",
    )
    args = parser.parse_args()

    if not TEMPLATE.exists():
        print(f"error: template not found: {TEMPLATE}", file=sys.stderr)
        sys.exit(1)

    channels = ["release", "debug"] if args.channel == "all" else [args.channel]
    cards = []
    for ch in channels:
        data = collect_channel(ch)
        if data is None:
            print(f"warn: channel '{ch}' has no APK, skipped", file=sys.stderr)
            continue
        cards.append(render_channel_card(data))

    if not cards:
        print("error: no channel has any APK", file=sys.stderr)
        sys.exit(1)

    template = TEMPLATE.read_text(encoding="utf-8")
    rendered = template.replace("{{CHANNEL_CARDS}}", "\n".join(cards))
    out = BASE_DIR / "index.html"
    out.write_text(rendered, encoding="utf-8")
    print(f"wrote {out} ({len(cards)} channel(s))")


if __name__ == "__main__":
    main()
