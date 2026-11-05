#!/usr/bin/env python3
"""Send one plain-text notification to the configured WeCom group robot."""

import json
import sys
import urllib.error
import urllib.request
from pathlib import Path
from urllib.parse import urlparse


CONFIG = Path(__file__).resolve().parent.parent / ".env"


def main() -> int:
    try:
        settings = dict(
            line.split("=", 1)
            for line in CONFIG.read_text(encoding="utf-8").splitlines()
            if line and not line.startswith("#") and "=" in line
        )
        webhook = settings["WECOM_WEBHOOK_URL"].strip()
    except (OSError, KeyError):
        print(json.dumps({"status": "configuration_missing"}))
        return 1

    parsed = urlparse(webhook)
    if (parsed.scheme, parsed.hostname, parsed.path) != (
        "https", "qyapi.weixin.qq.com", "/cgi-bin/webhook/send"
    ) or not parsed.query.startswith("key="):
        print(json.dumps({"status": "configuration_invalid"}))
        return 1

    message = sys.stdin.read().strip()
    if not message:
        print(json.dumps({"status": "message_empty"}))
        return 1

    payload = json.dumps({"msgtype": "text", "text": {"content": message}}).encode("utf-8")
    request = urllib.request.Request(
        webhook,
        data=payload,
        headers={"Content-Type": "application/json; charset=utf-8"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            status = response.status
            body = json.load(response)
    except urllib.error.HTTPError as error:
        print(json.dumps({"status": "http_error", "http_status": error.code}))
        return 1
    except (urllib.error.URLError, TimeoutError, OSError, ValueError):
        print(json.dumps({"status": "delivery_uncertain"}))
        return 2

    errcode = body.get("errcode") if isinstance(body, dict) else None
    print(json.dumps({"status": "sent" if status == 200 and errcode == 0 else "rejected",
                      "http_status": status, "errcode": errcode}))
    return 0 if status == 200 and errcode == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
