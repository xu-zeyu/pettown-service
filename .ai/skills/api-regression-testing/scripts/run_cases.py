#!/usr/bin/env python3
"""Run ordered HTTP cases after every project's Apifox import is verified.

Collection: {"apifox_uploads": {"project-id": {"receipt_path": "path/to/apifox-receipt.json"}},
"cases": [{"name": "...",
"project": "project-id", "method": "POST", "path": "/...", "capture":
{"shared_id": "data.id"}, "expect": {"status": 200}}], "required_flows":
["business-flow"]}. Later cases can use
${shared_id} in paths, headers, queries, bodies and expectations. Base URLs and
project IDs come from .env; no HTTP request runs without matching upload receipts.
"""

import json
import hashlib
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

from apifox_sync import SyncError, operations

TYPES = {"str": str, "int": int, "float": (int, float), "bool": bool,
         "list": list, "dict": dict, "null": type(None)}
VARIABLE = re.compile(r"\$\{([A-Za-z_][A-Za-z_0-9]*)\}")


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


def substitute(value, variables):
    if isinstance(value, dict):
        return {key: substitute(item, variables) for key, item in value.items()}
    if isinstance(value, list):
        return [substitute(item, variables) for item in value]
    if not isinstance(value, str):
        return value
    match = VARIABLE.fullmatch(value)
    if match:
        return variables[match.group(1)]
    return VARIABLE.sub(lambda found: str(variables[found.group(1)]), value)


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
        return {"name": case["name"], "result": "blocked", "reason": str(error)}, None

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
            "status": status, "failures": failures}, parsed


def configuration(settings, collection):
    cases = collection.get("cases")
    if not isinstance(cases, list) or not cases:
        raise ValueError("cases must be a nonempty list")
    projects = {case.get("project", "default") for case in cases}
    if any(not isinstance(project, str) or not project for project in projects):
        raise ValueError("each project must have a nonempty ID")
    if len(projects) > 1:
        required_flows = collection.get("required_flows")
        if not isinstance(required_flows, list) or not required_flows:
            raise ValueError("multi-project cases require required_flows")
        if len(required_flows) != len(set(required_flows)):
            raise ValueError("required_flows must be unique")
        for flow in required_flows:
            flow_projects = {case.get("project", "default") for case in cases
                             if case.get("flow") == flow}
            if not isinstance(flow, str) or not flow or len(flow_projects) < 2:
                raise ValueError(f"cross-project flow missing steps: {flow}")
    ids = json.loads(settings.get("APIFOX_PROJECT_IDS_JSON") or "{}")
    bases = json.loads(settings.get("API_BASE_URLS_JSON") or "{}")
    if projects == {"default"}:
        ids.setdefault("default", settings.get("APIFOX_PROJECT_ID", ""))
        bases.setdefault("default", settings.get("API_BASE_URL", ""))
    receipts = collection.get("apifox_uploads") or {}
    for project in projects:
        receipt_ref = receipts.get(project) or {}
        receipt_path = Path(receipt_ref.get("receipt_path") or "")
        if not receipt_path.is_file():
            raise ValueError(f"Apifox receipt missing for {project}")
        receipt = json.loads(receipt_path.read_text(encoding="utf-8"))
        evidence = Path(receipt.get("evidence") or "")
        source_spec = Path(receipt.get("source_spec") or "")
        if (not ids.get(project) or not bases.get(project)
                or receipt.get("project_id") != ids[project]
                or receipt.get("target_key") != project
                or receipt.get("verified") is not True
                or not isinstance(receipt.get("expected_operations"), int)
                or receipt["expected_operations"] < 1
                or receipt.get("observed_operations") != receipt["expected_operations"]
                or receipt.get("missing_operations")
                or not evidence.is_file()
                or not source_spec.is_file()):
            raise ValueError(f"Apifox upload or test environment unverified for {project}")
        if hashlib.sha256(source_spec.read_bytes()).hexdigest() != receipt.get("source_sha256"):
            raise ValueError(f"Apifox source changed after verification for {project}")
        expected_operations = operations(json.loads(source_spec.read_text(encoding="utf-8")))
        exported_operations = operations(json.loads(evidence.read_text(encoding="utf-8")),
                                         require_nonempty=False)
        if expected_operations - exported_operations:
            raise ValueError(f"Apifox export missing operations for {project}")
        if not bases[project].startswith(("http://", "https://")):
            raise ValueError(f"invalid test environment for {project}")
    return cases, bases


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: run_cases.py <cases.json>", file=sys.stderr)
        return 2
    try:
        collection = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
        cases, bases = configuration(load_env(), collection)
    except (OSError, ValueError, TypeError, KeyError, SyncError) as error:
        print(json.dumps({"status": "configuration_invalid", "reason": str(error)}))
        return 2
    variables = {}
    results = []
    for case in cases:
        project = case.get("project", "default")
        try:
            resolved = substitute(case, variables)
            result, parsed = run_case(bases[project], resolved)
            if result["result"] == "passed":
                for name, dotted in case.get("capture", {}).items():
                    value, found = dig(parsed, dotted)
                    if not found:
                        result["result"] = "failed"
                        result.setdefault("failures", []).append(f"capture field {dotted} missing")
                    else:
                        variables[name] = value
        except (KeyError, TypeError, ValueError) as error:
            result = {"name": case.get("name", "unnamed"), "result": "blocked",
                      "reason": f"unresolved input: {error}"}
        result["project"] = project
        if "flow" in case:
            result["flow"] = case["flow"]
        results.append(result)
    flows = {}
    for result in results:
        if "flow" in result:
            flows.setdefault(result["flow"], []).append(result)
    flow_summary = {name: {"result": "blocked" if any(
        step["result"] == "blocked" for step in steps) else "failed" if any(
        step["result"] == "failed" for step in steps) else "passed",
        "projects": sorted({step["project"] for step in steps}),
        "steps": len(steps)} for name, steps in flows.items()}
    required_flows = collection.get("required_flows") or []
    summary = {"total": len(results),
               "passed": sum(r["result"] == "passed" for r in results),
               "failed": sum(r["result"] == "failed" for r in results),
               "blocked": sum(r["result"] == "blocked" for r in results),
               "results": results, "flows": flow_summary,
               "required_flows": len(required_flows),
               "passed_flows": sum(flow_summary[name]["result"] == "passed"
                                   for name in required_flows)}
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 1 if summary["failed"] or summary["blocked"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
