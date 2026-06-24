#!/usr/bin/env python3
"""
app-self-hosted-update · 扫 APK 目录 + release-notes/ 目录,渲染 index.html。

输出:写到 DOWNLOAD_DIR/index.html(覆盖)。

模板:index.html.template,用 `{{KEY}}` 占位符替换。
"""
import hashlib
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

DIR = Path("/var/www/xiaozha/app/download")
TEMPLATE = DIR / "index.html.template"
APK_PATTERN = re.compile(r"^writing-with-ai-(\d+)\.apk$")


def parse_version_code(path: Path) -> int:
    m = APK_PATTERN.match(path.name)
    if not m:
        raise ValueError(f"unexpected filename: {path.name}")
    return int(m.group(1))


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


def render_history_html(apks: list[Path]) -> str:
    items = []
    for apk in sorted(apks, key=parse_version_code, reverse=True):
        code = parse_version_code(apk)
        size = apk.stat().st_size
        mtime = format_time(apk.stat().st_mtime)
        items.append(
            f'<li><a href="/app/download/{apk.name}">code {code}</a> · {format_size(size)} · {mtime}</li>'
        )
    return "\n".join(items) if items else "<li>暂无历史版本</li>"


def _markdown_to_html(md: str) -> str:
    """极简 markdown → HTML,只处理代码块/段落/换行。"""
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
    return "\n".join(out) if out else "<p>(无更新日志)</p>"


def _escape(s: str) -> str:
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def main() -> None:
    if not DIR.exists():
        print(f"error: download dir not found: {DIR}", file=sys.stderr)
        sys.exit(1)
    if not TEMPLATE.exists():
        print(f"error: template not found: {TEMPLATE}", file=sys.stderr)
        sys.exit(1)

    apks = [p for p in DIR.iterdir() if APK_PATTERN.match(p.name)]
    if not apks:
        print(f"error: no APK in {DIR}", file=sys.stderr)
        sys.exit(1)

    # 取最高 versionCode 作为「最新」
    latest = max(apks, key=parse_version_code)
    code = parse_version_code(latest)
    latest_notes_path = DIR / "release-notes" / f"{code}.md"
    latest_notes = (
        latest_notes_path.read_text(encoding="utf-8").strip()
        if latest_notes_path.exists()
        else "(无更新日志)"
    )

    # 计算 SHA-256(纯 stdlib hashlib,跨平台;Linux/Mac/Windows 都跑得了)
    sha = hashlib.sha256()
    with latest.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            sha.update(chunk)
    sha = sha.hexdigest()

    history_html = render_history_html(apks)

    template = TEMPLATE.read_text(encoding="utf-8")
    rendered = (
        template
        .replace("{{LATEST_VERSION_CODE}}", str(code))
        .replace("{{LATEST_APK_NAME}}", latest.name)
        .replace("{{LATEST_APK_SIZE}}", format_size(latest.stat().st_size))
        .replace("{{LATEST_SHA256}}", sha)
        .replace("{{LATEST_SHA256_SHORT}}", sha256_short(sha))
        .replace("{{LATEST_RELEASE_DATE}}", format_time(latest.stat().st_mtime))
        .replace("{{LATEST_NOTES_HTML}}", _markdown_to_html(latest_notes))
        .replace("{{HISTORY_LIST}}", history_html)
    )

    out = DIR / "index.html"
    out.write_text(rendered, encoding="utf-8")
    print(f"wrote {out}")


if __name__ == "__main__":
    main()