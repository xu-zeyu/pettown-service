import contextlib
import importlib.util
import io
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


spec = importlib.util.spec_from_file_location("shared_wecom_sender", Path(__file__).with_name("send_wecom.py"))
sender = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sender)


class Response(io.BytesIO):
    status = 200


class WecomSenderTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.config = Path(self.temp.name) / ".env"
        self.webhook = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=example"

    def call(self):
        output = io.StringIO()
        with patch.object(sender, "CONFIG", self.config), patch("sys.stdin", io.StringIO("测试通知")), contextlib.redirect_stdout(output):
            code = sender.main()
        return code, json.loads(output.getvalue())

    def test_missing_target_never_sends(self):
        self.config.write_text("WECOM_WEBHOOK_URL=" + self.webhook + "\n")
        with patch.object(sender.urllib.request, "urlopen") as urlopen:
            code, result = self.call()
        self.assertEqual(code, 1)
        self.assertEqual(result["status"], "target_unidentified")
        urlopen.assert_not_called()

    def test_http_and_business_success_are_required(self):
        self.config.write_text("WECOM_TARGET_NAME=研发测试群\nWECOM_WEBHOOK_URL=" + self.webhook + "\n")
        with patch.object(sender.urllib.request, "urlopen", return_value=Response(b'{"errcode":0}')):
            code, result = self.call()
        self.assertEqual(code, 0)
        self.assertEqual(result["status"], "sent")
        self.assertEqual(result["target_name"], "研发测试群")
        self.assertNotIn("key=", json.dumps(result))

    def test_business_failure_is_rejected(self):
        self.config.write_text("WECOM_TARGET_NAME=研发测试群\nWECOM_WEBHOOK_URL=" + self.webhook + "\n")
        with patch.object(sender.urllib.request, "urlopen", return_value=Response(b'{"errcode":40058}')):
            code, result = self.call()
        self.assertEqual(code, 1)
        self.assertEqual(result["status"], "rejected")


if __name__ == "__main__":
    unittest.main()
