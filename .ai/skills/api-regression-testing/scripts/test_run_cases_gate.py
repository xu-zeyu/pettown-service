import json
import hashlib
import tempfile
import unittest
from pathlib import Path

import run_cases


class RunCasesGateTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.export = self.root / "after-openapi.json"
        self.export.write_text('{"openapi":"3.1.0","paths":{"/pets":{"get":{}}}}')
        self.source = self.root / "source-openapi.json"
        self.source.write_text(self.export.read_text())
        self.receipt = self.root / "apifox-receipt.json"
        self.record = {"target_key": "pet-system/miniapp", "project_id": "123",
                       "verified": True, "expected_operations": 1,
                       "observed_operations": 1, "missing_operations": [],
                       "source_spec": str(self.source),
                       "source_sha256": hashlib.sha256(self.source.read_bytes()).hexdigest(),
                       "evidence": str(self.export)}
        self.receipt.write_text(json.dumps(self.record))
        self.settings = {"APIFOX_PROJECT_IDS_JSON": '{"pet-system/miniapp":"123"}',
                         "API_BASE_URLS_JSON": '{"pet-system/miniapp":"http://127.0.0.1:8082"}'}
        self.collection = {"apifox_uploads": {"pet-system/miniapp": {
            "receipt_path": str(self.receipt)}},
            "cases": [{"name": "list", "project": "pet-system/miniapp",
                       "method": "GET", "path": "/pets"}]}

    def test_verified_receipt_allows_configuration(self):
        cases, bases = run_cases.configuration(self.settings, self.collection)
        self.assertEqual(len(cases), 1)
        self.assertEqual(bases["pet-system/miniapp"], "http://127.0.0.1:8082")

    def test_unverified_receipt_blocks_configuration(self):
        self.record["verified"] = False
        self.receipt.write_text(json.dumps(self.record))
        with self.assertRaises(ValueError):
            run_cases.configuration(self.settings, self.collection)

    def test_missing_export_blocks_configuration(self):
        self.export.unlink()
        with self.assertRaises(ValueError):
            run_cases.configuration(self.settings, self.collection)

    def test_export_without_source_operation_blocks_configuration(self):
        self.export.write_text('{"openapi":"3.1.0","paths":{}}')
        with self.assertRaises(ValueError):
            run_cases.configuration(self.settings, self.collection)


if __name__ == "__main__":
    unittest.main()
