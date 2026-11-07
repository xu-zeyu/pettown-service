import json
import tempfile
import unittest
from pathlib import Path

import apifox_sync


SPEC = {"openapi": "3.1.0", "info": {"title": "x", "version": "1"},
        "paths": {"/pets": {"get": {"responses": {"200": {"description": "ok"}}}}}}
EMPTY = {"openapi": "3.1.0", "info": {"title": "x", "version": "1"}, "paths": {}}


class Response:
    def __init__(self, payload, status=200, content_type="application/json"):
        self.status = status
        self.headers = {"Content-Type": content_type}
        self.payload = payload if isinstance(payload, bytes) else json.dumps(payload).encode()

    def read(self):
        return self.payload

    def __enter__(self):
        return self

    def __exit__(self, *unused):
        return False


class ApifoxSyncTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        root = Path(self.temp.name)
        self.spec = root / "openapi.json"
        self.spec.write_text(json.dumps(SPEC))
        self.env = root / ".env"
        self.env.write_text('APIFOX_ACCESS_TOKEN=example\nAPIFOX_PROJECT_IDS_JSON={"pet-system/miniapp":"123"}\n')
        self.out = root / "evidence"

    def test_import_payload_uses_official_string_input(self):
        payload = apifox_sync.import_payload(SPEC, 42)
        self.assertIsInstance(payload["input"], str)
        self.assertEqual(json.loads(payload["input"]), SPEC)
        self.assertEqual(payload["options"], {"targetEndpointFolderId": 42})

    def test_empty_project_imports_once_and_verifies_export(self):
        calls = []
        responses = [Response(EMPTY), Response({"data": {"counters": {"endpointCreated": 1}}}), Response(SPEC)]

        def opener(request, timeout):
            calls.append((request.full_url.rsplit("/", 1)[-1], json.loads(request.data)))
            return responses.pop(0)

        receipt = apifox_sync.sync("pet-system/miniapp", self.spec, self.out,
                                   opener=opener, env_path=self.env)
        self.assertTrue(receipt["verified"])
        self.assertTrue(receipt["import_attempted"])
        self.assertEqual([x[0] for x in calls], ["export-openapi", "import-openapi", "export-openapi"])
        self.assertEqual(json.loads(calls[1][1]["input"]), SPEC)

    def test_export_without_paths_is_treated_as_empty_project(self):
        calls = []
        responses = [Response({"openapi": "3.1.0", "info": {"title": "empty", "version": "1"}}),
                     Response({"data": {"counters": {"endpointCreated": 1}}}), Response(SPEC)]

        def opener(request, timeout):
            calls.append(request.full_url.rsplit("/", 1)[-1])
            return responses.pop(0)

        receipt = apifox_sync.sync("pet-system/miniapp", self.spec, self.out,
                                   opener=opener, env_path=self.env)
        self.assertTrue(receipt["verified"])
        self.assertEqual(calls, ["export-openapi", "import-openapi", "export-openapi"])

    def test_existing_operations_skip_write(self):
        calls = []

        def opener(request, timeout):
            calls.append(request.full_url.rsplit("/", 1)[-1])
            return Response(SPEC)

        receipt = apifox_sync.sync("pet-system/miniapp", self.spec, self.out,
                                   opener=opener, env_path=self.env)
        self.assertTrue(receipt["verified"])
        self.assertFalse(receipt["import_attempted"])
        self.assertEqual(calls, ["export-openapi"])

    def test_single_target_compatibility(self):
        self.env.write_text('APIFOX_ACCESS_TOKEN=example\nAPIFOX_PROJECT_ID=123\n')

        def opener(request, timeout):
            self.assertIn('/projects/123/', request.full_url)
            return Response(SPEC)

        receipt = apifox_sync.sync("default", self.spec, self.out,
                                   opener=opener, env_path=self.env)
        self.assertTrue(receipt["verified"])

    def test_partially_existing_project_never_writes(self):
        self.spec.write_text(json.dumps({"openapi": "3.1.0", "paths": {
            "/pets": {"get": {}}, "/owners": {"get": {}}}}))
        calls = []

        def opener(request, timeout):
            calls.append(request.full_url.rsplit("/", 1)[-1])
            return Response(SPEC)

        with self.assertRaises(apifox_sync.SyncError):
            apifox_sync.sync("pet-system/miniapp", self.spec, self.out,
                             opener=opener, env_path=self.env)
        self.assertEqual(calls, ["export-openapi"])

    def test_empty_html_preflight_blocks_before_write(self):
        calls = []

        def opener(request, timeout):
            calls.append(request.full_url.rsplit("/", 1)[-1])
            return Response(b"", 201, "text/html")

        with self.assertRaises(apifox_sync.SyncError):
            apifox_sync.sync("pet-system/miniapp", self.spec, self.out,
                             opener=opener, env_path=self.env)
        self.assertEqual(calls, ["export-openapi"])
        receipt = json.loads((self.out / "apifox-receipt.json").read_text())
        self.assertFalse(receipt["verified"])

    def test_missing_after_import_stays_blocked(self):
        responses = [Response(EMPTY), Response(b"", 201, "text/html"), Response(EMPTY)]

        def opener(request, timeout):
            return responses.pop(0)

        with self.assertRaises(apifox_sync.SyncError):
            apifox_sync.sync("pet-system/miniapp", self.spec, self.out,
                             opener=opener, env_path=self.env)
        receipt = json.loads((self.out / "apifox-receipt.json").read_text())
        self.assertFalse(receipt["verified"])
        self.assertEqual(receipt["missing_operations"], [{"method": "GET", "path": "/pets"}])


if __name__ == "__main__":
    unittest.main()
