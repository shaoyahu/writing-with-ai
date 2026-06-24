#!/usr/bin/env python3
"""
app-self-hosted-update · 扫 /var/www/xiaozha/app/download/writing-with-ai-*.apk,
按 versionCode 取最大,生成 version.json manifest 到 stdout。

设计要点:
- 单一可信源:目录内容即真相,无手工维护 manifest 的可能
- 纯 stdout 输出:调用方 `> version.json` 重定向即可
- Python 3 标准库,无 pip 依赖
"""
import hashlib
import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

DIR = Path("/var/www/xiaozha/app/download")
APK_PATTERN = re.compile(r"^writing-with-ai-(\d+)\.apk$")
NOTES_DIRNAME = "release-notes"
VERSION_NAME_PLACEHOLDER = "0.0.0"  # 服务端不存 versionName,留 App 用 BuildConfig 兜底


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def parse_version_code(path: Path) -> int:
    m = APK_PATTERN.match(path.name)
    if not m:
        raise ValueError(f"unexpected filename: {path.name}")
    return int(m.group(1))


def load_release_notes(version_code: int, notes_dir: Path) -> str:
    notes_file = notes_dir / f"{version_code}.md"
    if notes_file.exists():
        return notes_file.read_text(encoding="utf-8").strip()
    return ""


def build_manifest() -> dict:
    if not DIR.exists():
        print(f"error: download dir not found: {DIR}", file=sys.stderr)
        sys.exit(1)

    notes_dir = DIR / NOTES_DIRNAME
    apks = [p for p in DIR.iterdir() if APK_PATTERN.match(p.name)]

    if not apks:
        print(f"error: no APK matching {APK_PATTERN.pattern} in {DIR}", file=sys.stderr)
        sys.exit(1)

    # 按 versionCode 升序,取最大
    apks_sorted = sorted(apks, key=parse_version_code)
    latest = apks_sorted[-1]
    code = parse_version_code(latest)
    return {
        "versionCode": code,
        "versionName": VERSION_NAME_PLACEHOLDER,
        "apkUrl": f"https://xiaozha.nananxue.cn/app/download/{latest.name}",
        "apkSize": latest.stat().st_size,
        "apkSha256": sha256(latest),
        "releaseNotes": load_release_notes(code, notes_dir),
        "releasedAt": datetime.fromtimestamp(latest.stat().st_mtime, tz=timezone.utc).isoformat(),
        "minSupportedVersionCode": 1,
        "mandatory": False,
    }


def main() -> None:
    manifest = build_manifest()
    json.dump(manifest, sys.stdout, ensure_ascii=False, indent=2)
    sys.stdout.write("\n")


if __name__ == "__main__":
    main()