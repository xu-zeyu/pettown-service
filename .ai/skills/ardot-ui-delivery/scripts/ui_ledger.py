#!/usr/bin/env python3
"""Track UI delivery stages per TAPD requirement and stable screen key."""

import argparse
import fcntl
import hashlib
import json
import os
import re
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
LEDGERS = ROOT / "runs" / "ui"
KEY = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_-]{0,79}$")
ID = re.compile(r"^[0-9]+$")


def fail(message, code=2):
    print(json.dumps({"error": message}, ensure_ascii=False), file=sys.stderr)
    return code


def valid(args):
    if not KEY.fullmatch(args.project) or not KEY.fullmatch(args.screen):
        raise ValueError("project 和 screen 只能使用字母、数字、下划线和短横线")
    if not ID.fullmatch(args.workspace) or not ID.fullmatch(args.story):
        raise ValueError("workspace 和 story 必须是完整的数字 ID")


def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def run(args):
    valid(args)
    folder = LEDGERS / args.project
    folder.mkdir(parents=True, exist_ok=True)
    ledger = folder / "ledger.json"
    lockfile = folder / ".ledger.lock"
    key = "|".join((args.workspace, args.story, args.screen))
    with lockfile.open("a+") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        data = json.loads(ledger.read_text(encoding="utf-8")) if ledger.exists() else {"schema_version": 1, "screens": {}}
        if data.get("schema_version") != 1 or not isinstance(data.get("screens"), dict):
            raise ValueError("台账格式不受支持")
        screens = data["screens"]
        item = screens.get(key)
        if args.action == "check":
            print(json.dumps({"exists": bool(item), "stage": item["stage"] if item else None}, ensure_ascii=False))
            return 3 if item else 0
        if args.action == "generated":
            if item:
                return fail("该屏幕已生成；请从当前阶段继续，修订须另行授权", 3)
            image = Path(args.image).resolve(strict=True)
            if not image.is_file() or image.stat().st_size == 0:
                raise ValueError("图片不存在或为空")
            expected = (folder / args.story).resolve()
            if not image.is_relative_to(expected):
                raise ValueError("图片必须位于当前项目和需求的 UI 产物目录")
            item = {"project": args.project, "workspace": args.workspace, "story": args.story,
                    "screen": args.screen, "stage": "generated", "image": str(image), "sha256": digest(image)}
            screens[key] = item
        elif args.action == "imported":
            if not item or item["stage"] != "generated":
                raise ValueError("只能在 generated 阶段登记 Ardot 入项")
            if not args.ardot_project.strip() or not args.ardot_item.strip() or not args.url.startswith("https://"):
                raise ValueError("Ardot 项目、入项 ID 和 HTTPS 回读链接必填")
            image = Path(item["image"])
            if not image.is_file() or digest(image) != item["sha256"]:
                raise ValueError("图片已变化，不能将旧回执关联到新图片")
            item.update(stage="imported", ardot_project=args.ardot_project, ardot_item=args.ardot_item,
                        ardot_url=args.url)
        else:
            if not item or item["stage"] != "imported":
                raise ValueError("只能在 imported 阶段登记企业微信成功通知")
            if not args.receipt.strip():
                raise ValueError("脱敏成功回执摘要必填")
            item.update(stage="notified", notification_receipt=args.receipt)
        item["updated_at"] = datetime.now(timezone.utc).isoformat()
        with tempfile.NamedTemporaryFile("w", encoding="utf-8", dir=folder, delete=False) as tmp:
            json.dump(data, tmp, ensure_ascii=False, indent=2)
            tmp.write("\n")
            name = tmp.name
        os.replace(name, ledger)
        print(json.dumps({"key": key, "stage": item["stage"]}, ensure_ascii=False))
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="action", required=True)
    for action in ("check", "generated", "imported", "notified"):
        command = sub.add_parser(action)
        for field in ("project", "workspace", "story", "screen"):
            command.add_argument("--" + field, required=True)
        if action == "generated":
            command.add_argument("--image", required=True)
        if action == "imported":
            for field in ("ardot-project", "ardot-item", "url"):
                command.add_argument("--" + field, required=True)
        if action == "notified":
            command.add_argument("--receipt", required=True)
    args = parser.parse_args()
    try:
        return run(args)
    except (OSError, ValueError, json.JSONDecodeError) as error:
        return fail(str(error))


if __name__ == "__main__":
    raise SystemExit(main())
