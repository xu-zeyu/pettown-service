#!/usr/bin/env python3
"""Fallback runner: execute API test cases from a JSON collection and assert.

Used only when Apifox upload/CLI is unavailable. Input: a JSON file with
{"cases": [{"name", "method", "path", "headers"?, "query"?, "json"?,
"expect": {"status"?, "body_contains"?, "json_fields"?}}]}.
json_fields maps a dotted path (e.g. "data.id") to an expected JSON type name
(str/int/float/bool/list/dict/null) or an exact value. Output: JSON summary to
stdout; non-zero exit if any case failed.
"""

import json
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

TYPES = {"str": str, "int": int, "float": (int, float), "bool": bool,
         "list": list, "dict": dict, "null": type(None)}


def load_env() -> dict:
    env_path = Path(__file__).resolve().parent.parent / ".env"
    settings = {}
    if not env_path.exists():
        return settings
    for line in env_path.read_text(encoding="utf-8").splitlines():
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            settings[key.strip()] = value.strip()
    return settings


def dig(data, dotted):
    for part in dotted.split("."):
        if isinstance(data, dict) and part in data:
            data = data[part]
        else:
            return None, False
    return data, True


def run_case(base, case):
    url = base.rstrip("/") + "/" + case["path"].lstrip("/")
    if case.get("query"):
        url += "?" + urllib.parse.urlencode(case["query"])
    body = json.dumps(case["json"]).encode() if "json" in case else None
    request = urllib.request.Request(
        url, data=body, method=case["method"].upper(),
        headers={"Content-Type": "application/json", **case.get("headers", {})})
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            status, raw = response.status, response.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as error:
        status = error.code
        raw = error.read().decode("utf-8", "replace")
    except (urllib.error.URLError, TimeoutError, OSError) as error:
        return {"name": case["name"], "result": "blocked", "reason": str(error)}

    expect = case.get("expect", {})
    failures = []
    if "status" in expect and status != expect["status"]:
        failures.append(f"status {status} != {expect['status']}")
    try:
        parsed = json.loads(raw)
    except ValueError:
        parsed = None
    if "body_contains" in expect and expect["body_contains"] not in raw:
        failures.append(f"body missing {expect['body_contains']!r}")
    for dotted, wanted in (expect.get("json_fields") or {}).items():
        value, found = dig(parsed, dotted) if parsed is not None else (None, False)
        if not found:
            failures.append(f"field {dotted} missing")
        elif isinstance(wanted, str) and wanted in TYPES:
            if not isinstance(value, TYPES[wanted]) or (
                    wanted != "bool" and isinstance(value, bool)
                    and wanted in ("int", "float")):
                failures.append(f"field {dotted} type mismatch")
        elif value != wanted:
            failures.append(f"field {dotted} = {value!r} != {wanted!r}")
    return {"name": case["name"], "result": "failed" if failures else "passed",
            "status": status, "failures": failures}


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: run_cases.py <cases.json>", file=sys.stderr)
        return 2
    base = load_env().get("API_BASE_URL", "").strip()
    if not base:
        print(json.dumps({"status": "configuration_missing", "field": "API_BASE_URL"}))
        return 2
    cases = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))["cases"]
    results = [run_case(base, case) for case in cases]
    summary = {"total": len(results),
               "passed": sum(r["result"] == "passed" for r in results),
               "failed": sum(r["result"] == "failed" for r in results),
               "blocked": sum(r["result"] == "blocked" for r in results),
               "results": results}
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 1 if summary["failed"] or summary["blocked"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
