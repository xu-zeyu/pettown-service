#!/usr/bin/env python3
"""ZenTao API V2: verify product, deduplicate, create one Bug, and read it back."""

import argparse
import hashlib
import ipaddress
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ENV_FILE = Path(__file__).resolve().parents[1] / ".env"
PAGE_SIZE = 1000
MAX_PAGES = 50


class IntakeError(Exception):
    pass


def read_env(path=ENV_FILE):
    if not path.is_file():
        raise IntakeError("禅道 Agent .env 不存在")
    result = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line or line.lstrip().startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        result[key.strip()] = value
    return result


def api_root(base_url, allowed_http_host="", token_present=False):
    url = urllib.parse.urlsplit(base_url.strip())
    if url.scheme not in ("http", "https") or not url.hostname or url.username or url.password or url.query or url.fragment:
        raise IntakeError("ZENTAO_BASE_URL 格式无效")
    if url.scheme == "http":
        try:
            loopback = ipaddress.ip_address(url.hostname).is_loopback
        except ValueError:
            loopback = url.hostname.lower() == "localhost"
        if not loopback and (url.netloc != allowed_http_host or not token_present):
            raise IntakeError("公网 HTTP 仅允许 .env 明确指定的主机且必须使用现成 Token；账号密码需 HTTPS 或本机隧道")
    path = url.path.rstrip("/")
    if path.endswith("/api.php/v2"):
        raise IntakeError("ZENTAO_BASE_URL 应为禅道实例根地址，不含 /api.php/v2")
    return urllib.parse.urlunsplit((url.scheme, url.netloc, path + "/api.php/v2", "", ""))


def positive_id(value, label):
    if isinstance(value, bool) or not str(value).isdecimal() or int(value) <= 0:
        raise IntakeError(f"{label} 必须是已核实的正整数 ID")
    return int(value)


class ZenTaoClient:
    def __init__(self, settings, opener=urllib.request.urlopen):
        self.settings = settings
        self.token = settings.get("ZENTAO_API_TOKEN", "").strip()
        self.root = api_root(settings.get("ZENTAO_BASE_URL", ""),
                             settings.get("ZENTAO_ALLOW_HTTP_HOST", "").strip(), bool(self.token))
        self.opener = opener

    def request(self, method, path, body=None, authenticated=True):
        if authenticated and not self.token:
            self.login()
        headers = {"Accept": "application/json", "Content-Type": "application/json"}
        if authenticated:
            headers["token"] = self.token
        data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
        request = urllib.request.Request(self.root + path, data=data, headers=headers, method=method)
        try:
            with self.opener(request, timeout=20) as response:
                status = response.status
                content_type = response.headers.get("Content-Type", "").lower()
                raw = response.read()
        except urllib.error.HTTPError as error:
            raise IntakeError(f"禅道 API HTTP {error.code}") from error
        except (urllib.error.URLError, TimeoutError, OSError) as error:
            raise IntakeError("禅道 API 传输失败，结果未确认") from error
        if status != 200 or "application/json" not in content_type or not raw:
            raise IntakeError(f"禅道 API 响应不可核实：HTTP {status}")
        try:
            result = json.loads(raw)
        except ValueError as error:
            raise IntakeError("禅道 API 返回无效 JSON") from error
        if not isinstance(result, dict) or result.get("status") != "success":
            raise IntakeError("禅道 API 业务状态不是 success")
        return result

    def login(self):
        account = self.settings.get("ZENTAO_ACCOUNT", "").strip()
        password = self.settings.get("ZENTAO_PASSWORD", "")
        if not account or not password:
            raise IntakeError("请在禅道 Agent .env 配置 ZENTAO_API_TOKEN，或同时配置账号和密码")
        result = self.request("POST", "/users/login", {"account": account, "password": password}, False)
        token = result.get("token")
        if not isinstance(token, str) or not token.strip():
            raise IntakeError("禅道登录未返回 API V2 Token")
        self.token = token.strip()

    def pages(self, path, field, filters=None):
        items = []
        for page in range(1, MAX_PAGES + 1):
            query = urllib.parse.urlencode({**(filters or {}), "recPerPage": PAGE_SIZE, "pageID": page})
            result = self.request("GET", f"{path}?{query}")
            batch = result.get(field)
            if not isinstance(batch, list):
                raise IntakeError(f"禅道 {field} 列表响应缺失")
            items.extend(batch)
            if len(batch) < PAGE_SIZE:
                return items
        raise IntakeError(f"禅道 {field} 列表超出安全分页上限，查重不完整")

    def resolve_target(self):
        expected_project_id = positive_id(self.settings.get("ZENTAO_PROJECT_ID", ""), "禅道项目")
        expected_project_name = self.settings.get("ZENTAO_PROJECT_NAME", "").strip()
        expected_product_name = self.settings.get("ZENTAO_PRODUCT_NAME", "").strip()
        if not expected_project_name or not expected_product_name:
            raise IntakeError("禅道项目名或产品名未配置")
        projects = self.pages("/projects", "projects")
        project = [item for item in projects if str(item.get("id")) == str(expected_project_id)]
        if len(project) != 1 or project[0].get("name") != expected_project_name:
            raise IntakeError("目标禅道项目 ID/名称未在当前实例回读一致")
        products = self.pages("/products", "products", {"browseType": "all"})
        matches = [item for item in products if item.get("name") == expected_product_name]
        if len(matches) != 1:
            raise IntakeError("目标禅道产品名称未唯一匹配")
        product_id = positive_id(matches[0].get("id"), "禅道产品")
        configured_id = self.settings.get("ZENTAO_PRODUCT_ID", "").strip()
        if configured_id and positive_id(configured_id, "禅道产品") != product_id:
            raise IntakeError("配置的禅道产品 ID 与只读列表不一致")
        detail = self.request("GET", f"/products/{product_id}").get("product")
        if not isinstance(detail, dict) or str(detail.get("id")) != str(product_id) or detail.get("name") != expected_product_name:
            raise IntakeError("目标禅道产品详情回读不一致")
        return {"project_id": expected_project_id, "project_name": expected_project_name,
                "product_id": product_id, "product_name": expected_product_name}

    def bugs(self, product_id):
        return self.pages(f"/products/{product_id}/bugs", "bugs", {"browseType": "all"})

    def bug_detail(self, bug_id):
        bug = self.request("GET", f"/bugs/{positive_id(bug_id, 'Bug')}").get("bug")
        if not isinstance(bug, dict):
            raise IntakeError("Bug 详情响应缺失")
        return bug


def write_receipt(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def checked_bug(bug, product_id, title, marker):
    if str(bug.get("product")) != str(product_id) or bug.get("title") != title or marker not in str(bug.get("steps", "")):
        raise IntakeError("Bug 详情与创建请求不一致，状态待核实")
    return positive_id(bug.get("id"), "Bug")


def create_bug_flow(client, bug_file, receipt_file):
    target = client.resolve_target()
    draft = json.loads(bug_file.read_text(encoding="utf-8"))
    title = str(draft.get("title", "")).strip()
    steps = str(draft.get("steps", "")).strip()
    signature = str(draft.get("failure_signature", "")).strip()
    builds = draft.get("openedBuild")
    if not title or not steps or not signature or not isinstance(builds, list) or not builds or not all(isinstance(x, str) and x.strip() for x in builds):
        raise IntakeError("Bug 草稿缺少标题、复现步骤、失败签名或影响版本数组")
    fingerprint = hashlib.sha256((str(target["product_id"]) + "\n" + signature).encode()).hexdigest()[:24]
    marker = f"[api-regression:{fingerprint}]"
    prior = json.loads(receipt_file.read_text()) if receipt_file.is_file() else None
    if prior and prior.get("fingerprint") != fingerprint:
        raise IntakeError("回执文件已属于其他失败签名，不能覆盖")
    bug_rows = client.bugs(target["product_id"])
    by_marker = [row for row in bug_rows if marker in str(row.get("steps", ""))]
    same_title = [row for row in bug_rows if row.get("title") == title]
    if len(by_marker) > 1:
        raise IntakeError("同一失败签名出现多条 Bug，需人工查重")
    if by_marker:
        detail = client.bug_detail(by_marker[0].get("id"))
        bug_id = checked_bug(detail, target["product_id"], title, marker)
        receipt = {**target, "status": "existing", "verified": True, "bug_id": bug_id,
                   "fingerprint": fingerprint, "create_attempted": bool(prior and prior.get("create_attempted"))}
        write_receipt(receipt_file, receipt)
        return receipt
    if same_title:
        raise IntakeError("同名 Bug 已存在但失败签名不一致，需人工核对")
    if prior and prior.get("create_attempted"):
        raise IntakeError("此前创建结果不明，不能自动重发；只读查重未发现签名")
    try:
        severity = int(draft.get("severity", 3))
        priority = int(draft.get("pri", 3))
    except (ValueError, TypeError) as error:
        raise IntakeError("Bug 严重级别或优先级不是整数") from error
    if not (1 <= severity <= 4 and 1 <= priority <= 4):
        raise IntakeError("Bug 严重级别或优先级不在 1..4")
    bug_type = draft.get("type", "codeerror")
    if bug_type not in ("codeerror", "config", "install", "security", "performance",
                        "standard", "automation", "designdefect", "others"):
        raise IntakeError("Bug 类型不在禅道官方枚举中")
    payload = {"productID": target["product_id"], "title": title, "openedBuild": builds,
               "severity": severity, "pri": priority, "type": bug_type,
               "steps": steps + "\n\n" + marker}
    if "project" in draft:
        project = positive_id(draft["project"], "禅道项目")
        if project != target["project_id"]:
            raise IntakeError("Bug 草稿项目 ID 与只读核实结果不一致")
        payload["project"] = project
    write_receipt(receipt_file, {**target, "status": "uncertain", "verified": False,
                                 "fingerprint": fingerprint, "create_attempted": True})
    try:
        created = client.request("POST", "/bugs", payload)
    except IntakeError:
        # 服务器可能已创建但响应丢失；只读核查由下次调用继续，不重复 POST。
        raise IntakeError("Bug 创建响应不明；已记录尝试，先只读查重，不自动重发")
    bug_id = positive_id(created.get("id"), "新建 Bug")
    detail = client.bug_detail(bug_id)
    checked_bug(detail, target["product_id"], title, marker)
    receipt = {**target, "status": "created", "verified": True, "bug_id": bug_id,
               "fingerprint": fingerprint, "create_attempted": True}
    write_receipt(receipt_file, receipt)
    return receipt


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("check", "create-bug"))
    parser.add_argument("--env-file", type=Path, default=ENV_FILE)
    parser.add_argument("--bug-file", type=Path)
    parser.add_argument("--receipt", type=Path, required=True)
    args = parser.parse_args()
    try:
        client = ZenTaoClient(read_env(args.env_file))
        if args.action == "check":
            result = {**client.resolve_target(), "status": "verified"}
            write_receipt(args.receipt, result)
        else:
            if not args.bug_file:
                raise IntakeError("create-bug 需要 --bug-file")
            result = create_bug_flow(client, args.bug_file, args.receipt)
        print(json.dumps(result, ensure_ascii=False))
        return 0
    except (IntakeError, OSError, ValueError, KeyError, TypeError) as error:
        print(json.dumps({"status": "blocked", "reason": str(error)}, ensure_ascii=False))
        return 2


if __name__ == "__main__":
    sys.exit(main())
