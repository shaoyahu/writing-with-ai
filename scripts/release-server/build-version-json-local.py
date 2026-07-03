#!/usr/bin/env python3
"""
publish-release · 本地生成 version.json,apkUrl 指向 GitHub Releases CDN。

不再扫描服务器目录,改用显式参数:本地计算 SHA-256,构建 GitHub 下载 URL。
调用方(publish-release.sh)通过 stdout 重定向写入文件。

用法:
  python3 build-version-json-local.py \
    --version-code 3 --version-name 0.3.0 \
    --channel release --apk ./app-release.apk \
    --notes ./release-notes/3.md

设计要点:
- 显式参数驱动,不依赖服务器文件系统
- apkUrl 指向 GitHub Releases(确定性 URL,无需查 API)
- 纯 stdlib,无 pip 依赖
"""
import argparse
import hashlib
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

GITHUB_REPO = "shaoyahu/writing-with-ai"

CHANNEL_CONFIG = {
    "release": {
        "apk_name_template": "writing-with-ai-{code}.apk",
        "tag_template": "v{code}",
    },
    "debug": {
        "apk_name_template": "writing-with-ai-debug-{code}.apk",
        "tag_template": "v{code}-debug",
    },
}


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def build_manifest(
    version_code: int,
    version_name: str,
    channel: str,
    apk_path: Path,
    notes_path: Path | None,
    github_repo: str,
) -> dict:
    cfg = CHANNEL_CONFIG[channel]
    apk_name = cfg["apk_name_template"].format(code=version_code)
    tag = cfg["tag_template"].format(code=version_code)
    apk_url = f"https://github.com/{github_repo}/releases/download/{tag}/{apk_name}"

    notes = ""
    if notes_path and notes_path.exists():
        notes = notes_path.read_text(encoding="utf-8").strip()

    return {
        "versionCode": version_code,
        "versionName": version_name,
        "apkUrl": apk_url,
        "apkSize": apk_path.stat().st_size,
        "apkSha256": sha256(apk_path),
        "apkName": apk_name.removesuffix(".apk"),
        "releaseNotes": notes,
        "releasedAt": datetime.now(tz=timezone.utc).isoformat(),
        "minSupportedVersionCode": 1,
        "mandatory": False,
    }


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Build version.json locally with GitHub Releases URLs"
    )
    parser.add_argument("--version-code", type=int, required=True)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--channel", choices=["debug", "release"], required=True)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--notes", type=Path, default=None)
    parser.add_argument("--github-repo", default=GITHUB_REPO)
    args = parser.parse_args()

    if not args.apk.exists():
        print(f"error: APK not found: {args.apk}", file=sys.stderr)
        sys.exit(1)

    manifest = build_manifest(
        version_code=args.version_code,
        version_name=args.version_name,
        channel=args.channel,
        apk_path=args.apk,
        notes_path=args.notes,
        github_repo=args.github_repo,
    )
    json.dump(manifest, sys.stdout, ensure_ascii=False, indent=2)
    sys.stdout.write("\n")


if __name__ == "__main__":
    main()
