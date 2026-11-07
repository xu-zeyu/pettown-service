#!/usr/bin/env python3
"""Send one text message to the named, configured WeCom group robot."""

import json
import sys
import urllib.error
import urllib.request
from pathlib import Path
from urllib.parse import urlparse

CONFIG = Path(__file__).resolve().parents[2] / "tapd-requirement-intake" / ".env"


def main() -> int:
    try:
        settings = dict(line.split("=", 1) for line in CONFIG.read_text(encoding="utf-8").splitlines()
                        if line and not line.startswith("#") and "=" in line)
        webhook = settings.get("WECOM_WEBHOOK_URL", "").strip()
        target_name = settings.get("WECOM_TARGET_NAME", "").strip()
    except OSError:
        print(json.dumps({"status": "configuration_missing"}))
        return 1
    if not target_name:
        print(json.dumps({"status": "target_unidentified"}))
        return 1
    parsed = urlparse(webhook)
    if (parsed.scheme, parsed.hostname, parsed.path) != (
            "https", "qyapi.weixin.qq.com", "/cgi-bin/webhook/send") or not parsed.query.startswith("key="):
        print(json.dumps({"status": "configuration_invalid"}))
        return 1
    message = sys.stdin.read().strip()
    if not message:
        print(json.dumps({"status": "message_empty"}))
        return 1
    payload = json.dumps({"msgtype": "text", "text": {"content": message}}, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(webhook, data=payload,
                                     headers={"Content-Type": "application/json; charset=utf-8"},
                                     method="POST")
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            status = response.status
            body = json.load(response)
    except urllib.error.HTTPError as error:
        print(json.dumps({"status": "http_error", "http_status": error.code, "target_name": target_name}))
        return 1
    except (urllib.error.URLError, TimeoutError, OSError, ValueError):
        print(json.dumps({"status": "delivery_uncertain", "target_name": target_name}))
        return 2
    errcode = body.get("errcode") if isinstance(body, dict) else None
    sent = status == 200 and errcode == 0
    print(json.dumps({"status": "sent" if sent else "rejected", "http_status": status,
                      "errcode": errcode, "target_name": target_name}, ensure_ascii=False))
    return 0 if sent else 1


if __name__ == "__main__":
    sys.exit(main())
