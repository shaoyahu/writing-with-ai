#!/usr/bin/env python3
"""
app-self-hosted-update · 扫指定目录下的 APK,按 versionCode 取最大,生成 version.json manifest 到 stdout。

支持双通道:--channel debug|release 决定扫描目录和 APK pattern。
用法:
  python3 build-version-json.py --channel release
  python3 build-version-json.py --channel debug

设计要点:
- 单一可信源:目录内容即真相,无手工维护 manifest 的可能
- 纯 stdout 输出:调用方 `> version.json` 重定向即可
- Python 3 标准库,无 pip 依赖
"""
import argparse
import hashlib
import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

BASE_DIR = Path("/var/www/xiaozha/app")
NOTES_DIRNAME = "release-notes"
VERSION_NAME_PLACEHOLDER = "0.0.0"  # 服务端不存 versionName,留 App 用 BuildConfig 兜底

CHANNEL_CONFIG = {
    "release": {
        "dir": BASE_DIR / "release",
        "pattern": re.compile(r"^writing-with-ai-(\d+)\.apk$"),
        "url_prefix": "https://xiaozha.nananxue.cn/app/release",
    },
    "debug": {
        "dir": BASE_DIR / "debug",
        "pattern": re.compile(r"^writing-with-ai-debug-(\d+)\.apk$"),
        "url_prefix": "https://xiaozha.nananxue.cn/app/debug",
    },
}


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def parse_version_code(path: Path, pattern: re.Pattern) -> int:
    m = pattern.match(path.name)
    if not m:
        raise ValueError(f"unexpected filename: {path.name}")
    return int(m.group(1))


def load_release_notes(version_code: int, notes_dir: Path) -> str:
    notes_file = notes_dir / f"{version_code}.md"
    if notes_file.exists():
        return notes_file.read_text(encoding="utf-8").strip()
    return ""


def build_manifest(channel: str) -> dict:
    cfg = CHANNEL_CONFIG[channel]
    scan_dir = cfg["dir"]
    pattern = cfg["pattern"]
    url_prefix = cfg["url_prefix"]

    if not scan_dir.exists():
        print(f"error: dir not found: {scan_dir}", file=sys.stderr)
        sys.exit(1)

    notes_dir = scan_dir / NOTES_DIRNAME
    apks = [p for p in scan_dir.iterdir() if pattern.match(p.name)]

    if not apks:
        print(f"error: no APK matching {pattern.pattern} in {scan_dir}", file=sys.stderr)
        sys.exit(1)

    # 按 versionCode 升序,取最大
    apks_sorted = sorted(apks, key=lambda p: parse_version_code(p, pattern))
    latest = apks_sorted[-1]
    code = parse_version_code(latest, pattern)
    return {
        "versionCode": code,
        "versionName": VERSION_NAME_PLACEHOLDER,
        "apkUrl": f"{url_prefix}/{latest.name}",
        "apkSize": latest.stat().st_size,
        "apkSha256": sha256(latest),
        "releaseNotes": load_release_notes(code, notes_dir),
        "releasedAt": datetime.fromtimestamp(latest.stat().st_mtime, tz=timezone.utc).isoformat(),
        "minSupportedVersionCode": 1,
        "mandatory": False,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Build version.json for a channel")
    parser.add_argument(
        "--channel",
        choices=["debug", "release"],
        required=True,
        help="Which channel to build manifest for",
    )
    args = parser.parse_args()

    manifest = build_manifest(args.channel)
    json.dump(manifest, sys.stdout, ensure_ascii=False, indent=2)
    sys.stdout.write("\n")


if __name__ == "__main__":
    main()
