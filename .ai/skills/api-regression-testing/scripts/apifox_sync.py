#!/usr/bin/env python3
"""Import an OpenAPI document only after a readable Apifox preflight; verify by export.

No write is retried automatically. The receipt is usable by run_cases.py only
when a fresh export proves every expected method/path is present.
"""

import argparse
import hashlib
import json
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request
from pathlib import Path

ENV_FILE = Path(__file__).resolve().parent.parent / ".env"
API_ORIGIN = "https://api.apifox.com/v1/projects"
HTTP_METHODS = {"get", "post", "put", "patch", "delete", "head", "options", "trace"}


class SyncError(Exception):
    pass


class CurlResponse:
    def __init__(self, status, content_type, body):
        self.status = status
        self.headers = {"Content-Type": content_type}
        self.body = body

    def read(self):
        return self.body

    def __enter__(self):
        return self

    def __exit__(self, *_unused):
        return False


def curl_urlopen(request, timeout):
    """Use curl because the Apifox gateway gives urllib an empty HTML 201 here.

    Headers live in a mode-0600 temporary file rather than process arguments.
    The caller makes exactly one request; failed writes are never retried.
    """
    with tempfile.TemporaryDirectory(prefix="apifox-http-") as directory:
        root = Path(directory)
        headers_file = root / "headers"
        body_file = root / "request.json"
        response_file = root / "response"
        headers = request.header_items()
        if any("\r" in value or "\n" in value for _, value in headers):
            raise SyncError("请求头包含非法换行")
        headers_file.write_text("".join(f"{key}: {value}\n" for key, value in headers))
        headers_file.chmod(0o600)
        body_file.write_bytes(request.data or b"")
        command = ["curl", "--silent", "--show-error", "--max-time", str(timeout),
                   "--request", request.get_method(), "--header", "@" + str(headers_file),
                   "--data-binary", "@" + str(body_file), "--output", str(response_file),
                   "--write-out", "%{http_code}\n%{content_type}", request.full_url]
        try:
            result = subprocess.run(command, capture_output=True, text=True, timeout=timeout + 5)
        except (OSError, subprocess.TimeoutExpired) as error:
            raise SyncError("Apifox curl 传输失败") from error
        if result.returncode != 0:
            raise SyncError(f"Apifox curl 传输失败，退出码 {result.returncode}")
        lines = result.stdout.splitlines()
        if len(lines) != 2 or not lines[0].isdigit():
            raise SyncError("Apifox curl 响应元数据无效")
        return CurlResponse(int(lines[0]), lines[1], response_file.read_bytes())


def load_env(path=ENV_FILE):
    if not path.is_file():
        raise SyncError("Apifox .env 不存在")
    return dict(line.split("=", 1) for line in path.read_text().splitlines()
                if "=" in line and not line.startswith("#"))


def operations(document, require_nonempty=True):
    if not isinstance(document, dict) or not str(document.get("openapi", "")).startswith("3."):
        raise SyncError("输入不是 OpenAPI 3 文档")
    paths = document.get("paths")
    if paths is None and not require_nonempty:
        return set()
    if not isinstance(paths, dict):
        raise SyncError("OpenAPI paths 为空")
    result = {(method.upper(), path) for path, verbs in paths.items()
              if isinstance(verbs, dict) for method in verbs if method.lower() in HTTP_METHODS}
    if require_nonempty and not result:
        raise SyncError("OpenAPI 没有 HTTP 操作")
    return result


def import_payload(document, folder_id=None):
    # Apifox 官方 String 导入格式：input 是 JSON 字符串，不是 {type, data}。
    options = {}
    if folder_id is not None:
        options["targetEndpointFolderId"] = int(folder_id)
    return {"input": json.dumps(document, ensure_ascii=False, separators=(",", ":")),
            "options": options}


def response_meta(status, headers, raw):
    return {"http_status": status,
            "content_type": headers.get("Content-Type", "").split(";", 1)[0].lower(),
            "body_bytes": len(raw)}


def call_api(project_id, action, token, payload, opener=curl_urlopen):
    request = urllib.request.Request(
        f"{API_ORIGIN}/{project_id}/{action}",
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Authorization": "Bearer " + token,
                 "X-Apifox-Api-Version": "2024-03-28",
                 "Content-Type": "application/json"},
        method="POST")
    try:
        with opener(request, timeout=30) as response:
            return response.status, response.headers, response.read()
    except urllib.error.HTTPError as error:
        return error.code, error.headers, error.read()
    except (urllib.error.URLError, TimeoutError, OSError) as error:
        raise SyncError(f"{action} 传输失败：{type(error).__name__}") from error


def parse_success(status, headers, raw, action):
    meta = response_meta(status, headers, raw)
    if status != 200 or meta["content_type"] != "application/json" or not raw:
        raise SyncError(f"{action} 响应不可核实：HTTP {status}、{meta['content_type'] or '无 Content-Type'}、{len(raw)} 字节")
    try:
        return json.loads(raw)
    except ValueError as error:
        raise SyncError(f"{action} 返回无效 JSON") from error


def export_document(project_id, token, out, label, opener):
    payload = {"scope": {"type": "ALL"}, "options": {
        "includeApifoxExtensionProperties": False, "addFoldersToTags": False},
        "oasVersion": "3.1", "exportFormat": "JSON"}
    status, headers, raw = call_api(project_id, "export-openapi", token, payload, opener)
    (out / f"{label}-response.json").write_text(json.dumps(response_meta(status, headers, raw), indent=2) + "\n")
    document = parse_success(status, headers, raw, "export-openapi")
    operations(document, require_nonempty=False)
    (out / f"{label}-openapi.json").write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n")
    return document


def sync(target_key, spec_path, out, folder_id=None, opener=curl_urlopen, env_path=ENV_FILE):
    settings = load_env(env_path)
    if folder_id is None:
        folder_id = settings.get("APIFOX_TARGET_FOLDER_ID") or None
    token = settings.get("APIFOX_ACCESS_TOKEN", "").strip()
    mapping = json.loads(settings.get("APIFOX_PROJECT_IDS_JSON") or "{}")
    project_id = str(mapping.get(target_key) or (
        settings.get("APIFOX_PROJECT_ID", "") if target_key == "default" else ""))
    if not token or not project_id.isdecimal():
        raise SyncError("目标映射或 Apifox 令牌缺失")
    document = json.loads(spec_path.read_text(encoding="utf-8"))
    expected = operations(document)
    sha256 = hashlib.sha256(spec_path.read_bytes()).hexdigest()
    out.mkdir(parents=True, exist_ok=True)
    receipt = {"target_key": target_key, "project_id": project_id,
               "source_spec": str(spec_path.resolve()), "source_sha256": sha256,
               "expected_operations": len(expected),
               "verified": False, "import_attempted": False}
    receipt_path = out / "apifox-receipt.json"
    receipt_path.write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + "\n")

    # Read before writing: a 201/empty/proxy response cannot establish project state.
    before = export_document(project_id, token, out, "before", opener)
    before_operations = operations(before, require_nonempty=False)
    missing = expected - before_operations
    if missing and expected & before_operations:
        raise SyncError("目标中已存在部分预期接口；先核对覆盖策略与重复项，禁止盲目混合导入")
    if missing:
        receipt["import_attempted"] = True
        receipt_path.write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + "\n")
        status, headers, raw = call_api(project_id, "import-openapi", token,
                                        import_payload(document, folder_id), opener)
        (out / "import-response.json").write_text(json.dumps(response_meta(status, headers, raw), indent=2) + "\n")
        # Even when import response is ambiguous, one read-only export may resolve it.
        after = export_document(project_id, token, out, "after", opener)
    else:
        after = before
    remaining = sorted(expected - operations(after, require_nonempty=False))
    receipt["missing_operations"] = [{"method": method, "path": path} for method, path in remaining]
    receipt["observed_operations"] = len(expected) - len(remaining)
    receipt["evidence"] = str((out / ("after-openapi.json" if missing else "before-openapi.json")).resolve())
    receipt["verified"] = not remaining and folder_id is None
    if folder_id is not None:
        receipt["folder_verification"] = "需用 Apifox endpoint list/get 或 UI 核对目录；仅导出 OpenAPI 不足以确认目录"
    receipt_path.write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + "\n")
    if not receipt["verified"]:
        raise SyncError("Apifox 回读未达到门禁；查看 apifox-receipt.json")
    return receipt


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target-key", required=True)
    parser.add_argument("--spec", required=True, type=Path)
    parser.add_argument("--out-dir", required=True, type=Path)
    parser.add_argument("--target-folder-id", type=int)
    args = parser.parse_args()
    try:
        result = sync(args.target_key, args.spec, args.out_dir, args.target_folder_id)
        print(json.dumps({"status": "verified", "project_id": result["project_id"],
                          "operations": result["observed_operations"]}))
        return 0
    except (SyncError, OSError, ValueError, KeyError) as error:
        print(json.dumps({"status": "blocked", "reason": str(error)}, ensure_ascii=False))
        return 2


if __name__ == "__main__":
    sys.exit(main())
