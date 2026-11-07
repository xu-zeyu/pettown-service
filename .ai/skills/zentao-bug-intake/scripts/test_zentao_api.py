import json
import tempfile
import unittest
import urllib.error
from pathlib import Path

import zentao_api


class Response:
    status = 200
    headers = {"Content-Type": "application/json"}

    def __init__(self, value):
        self.value = json.dumps(value).encode()

    def read(self):
        return self.value

    def __enter__(self):
        return self

    def __exit__(self, *_args):
        return False


class ZenTaoApiTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.settings = {"ZENTAO_BASE_URL": "http://127.0.0.1:18888/zentao",
                         "ZENTAO_API_TOKEN": "secret-token", "ZENTAO_PROJECT_ID": "1",
                         "ZENTAO_PROJECT_NAME": "尾巴町", "ZENTAO_PRODUCT_NAME": "尾巴町"}
        self.bug_file = self.root / "bug.json"
        self.bug_file.write_text(json.dumps({"title": "登录失败", "steps": "复现步骤与期望实际",
                                            "failure_signature": "login-500", "openedBuild": ["trunk"]}))
        self.receipt = self.root / "receipt.json"

    def opener(self, request, timeout):
        path = request.full_url.split("/api.php/v2", 1)[1].split("?", 1)[0]
        if path == "/projects":
            return Response({"status": "success", "projects": [{"id": "1", "name": "尾巴町"}]})
        if path == "/products":
            self.assertIn("browseType=all", request.full_url)
            return Response({"status": "success", "products": [{"id": "7", "name": "尾巴町"}]})
        if path == "/products/7":
            return Response({"status": "success", "product": {"id": "7", "name": "尾巴町"}})
        raise AssertionError(path)

    def test_rejects_public_plaintext_http(self):
        with self.assertRaises(zentao_api.IntakeError):
            zentao_api.api_root("http://8.163.103.108:88/zentao")
        with self.assertRaises(zentao_api.IntakeError):
            zentao_api.api_root("http://8.163.103.108:88/zentao", "8.163.103.108:88", False)
        self.assertEqual(zentao_api.api_root("http://8.163.103.108:88/zentao", "8.163.103.108:88", True),
                         "http://8.163.103.108:88/zentao/api.php/v2")
        self.assertEqual(zentao_api.api_root("http://127.0.0.1:18888/zentao"),
                         "http://127.0.0.1:18888/zentao/api.php/v2")

    def test_token_is_used_without_login_and_target_is_read_back(self):
        calls = []

        def opener(request, timeout):
            calls.append((request.full_url, request.get_header("Token")))
            return self.opener(request, timeout)

        target = zentao_api.ZenTaoClient(self.settings, opener).resolve_target()
        self.assertEqual(target["product_id"], 7)
        self.assertEqual(len(calls), 3)
        self.assertTrue(all(token == "secret-token" for _, token in calls))

    def test_login_uses_credentials_only_when_token_absent(self):
        settings = {**self.settings, "ZENTAO_API_TOKEN": "", "ZENTAO_ACCOUNT": "tester",
                    "ZENTAO_PASSWORD": "password=with#symbols"}
        calls = []

        def opener(request, timeout):
            calls.append(request)
            if request.full_url.endswith("/users/login"):
                self.assertNotIn("Token", dict(request.header_items()))
                body = json.loads(request.data)
                self.assertEqual(body["password"], "password=with#symbols")
                return Response({"status": "success", "token": "issued-token"})
            self.assertEqual(request.get_header("Token"), "issued-token")
            return self.opener(request, timeout)

        target = zentao_api.ZenTaoClient(settings, opener).resolve_target()
        self.assertEqual(target["product_id"], 7)
        self.assertEqual(len(calls), 4)

    def test_project_or_product_mismatch_blocks_before_create(self):
        settings = {**self.settings, "ZENTAO_PRODUCT_ID": "1"}
        client = zentao_api.ZenTaoClient(settings, self.opener)
        with self.assertRaises(zentao_api.IntakeError):
            zentao_api.create_bug_flow(client, self.bug_file, self.receipt)
        self.assertFalse(self.receipt.exists())

    def test_create_once_then_readback_and_deduplicate(self):
        state = {"bug": None, "writes": 0}

        def opener(request, timeout):
            path = request.full_url.split("/api.php/v2", 1)[1].split("?", 1)[0]
            if path in ("/projects", "/products", "/products/7"):
                return self.opener(request, timeout)
            if path == "/products/7/bugs":
                self.assertIn("browseType=all", request.full_url)
                return Response({"status": "success", "bugs": [state["bug"]] if state["bug"] else []})
            if path == "/bugs" and request.get_method() == "POST":
                state["writes"] += 1
                draft = json.loads(request.data)
                self.assertEqual(draft["productID"], 7)
                self.assertEqual(draft["openedBuild"], ["trunk"])
                state["bug"] = {"id": "9", "product": "7", "title": draft["title"],
                                "steps": draft["steps"]}
                return Response({"status": "success", "id": 9})
            if path == "/bugs/9":
                return Response({"status": "success", "bug": state["bug"]})
            raise AssertionError(path)

        client = zentao_api.ZenTaoClient(self.settings, opener)
        result = zentao_api.create_bug_flow(client, self.bug_file, self.receipt)
        self.assertEqual((result["status"], result["bug_id"], state["writes"]), ("created", 9, 1))
        again = zentao_api.create_bug_flow(client, self.bug_file, self.receipt)
        self.assertEqual((again["status"], again["bug_id"], state["writes"]), ("existing", 9, 1))

    def test_uncertain_write_is_never_retried(self):
        calls = {"writes": 0}

        def opener(request, timeout):
            path = request.full_url.split("/api.php/v2", 1)[1].split("?", 1)[0]
            if path in ("/projects", "/products", "/products/7"):
                return self.opener(request, timeout)
            if path == "/products/7/bugs":
                return Response({"status": "success", "bugs": []})
            if path == "/bugs":
                calls["writes"] += 1
                raise urllib.error.URLError("response lost")
            raise AssertionError(path)

        client = zentao_api.ZenTaoClient(self.settings, opener)
        with self.assertRaises(zentao_api.IntakeError):
            zentao_api.create_bug_flow(client, self.bug_file, self.receipt)
        self.assertTrue(json.loads(self.receipt.read_text())["create_attempted"])
        with self.assertRaises(zentao_api.IntakeError):
            zentao_api.create_bug_flow(client, self.bug_file, self.receipt)
        self.assertEqual(calls["writes"], 1)


if __name__ == "__main__":
    unittest.main()
