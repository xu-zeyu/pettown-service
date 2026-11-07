#!/usr/bin/env python3
"""Backward-compatible entry point for the shared WeCom notification sender."""

import runpy
from pathlib import Path

runpy.run_path(str(Path(__file__).resolve().parents[2] / "wecom-notification" / "scripts" / "send_wecom.py"),
               run_name="__main__")
