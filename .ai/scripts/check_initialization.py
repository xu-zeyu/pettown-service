#!/usr/bin/env python3
"""Audit a selected .ai project before marking initialization complete."""

import argparse
import json
import sys
from pathlib import Path

try:
    import yaml
except ImportError:
    print("需要 PyYAML 才能校验 YAML；未校验时不得宣称初始化完成", file=sys.stderr)
    raise SystemExit(2)


AI = Path(__file__).resolve().parents[1]
UNKNOWN = ("待确认", "待填写", "待扫描", "替换为")


def read(path):
    return yaml.safe_load(path.read_text(encoding="utf-8"))


def get(obj, dotted):
    for name in dotted.split("."):
        if not isinstance(obj, dict):
            return None
        obj = obj.get(name)
    return obj


def incomplete(value):
    if value is None or value == "" or value == [] or value == {}:
        return True
    if isinstance(value, str):
        return any(token in value for token in UNKNOWN)
    if isinstance(value, list):
        return any(incomplete(item) for item in value)
    if isinstance(value, dict):
        return any(incomplete(item) for item in value.values())
    return False


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("project_id")
    args = parser.parse_args()
    registry = read(AI / "config/projects.yaml")
    matches = [item for item in registry.get("projects", []) if item.get("id") == args.project_id]
    issues = []
    if len(matches) != 1:
        issues.append("项目注册表中项目 ID 缺失或重复")
    else:
        entry = matches[0]
        path = (AI / "config" / entry["config"]).resolve()
        if not path.is_relative_to(AI / "projects") or not path.is_file():
            issues.append("项目配置文件路径无效")
        else:
            project = read(path)
            if get(project, "project.id") != args.project_id:
                issues.append("项目配置 ID 与注册表不一致")
            fields = ("project.name", "project.description", "project.repository.path",
                      "project.repository.remote_url", "project.repository.default_branch",
                      "project.stack_profiles", "project.technology.languages",
                      "project.technology.frameworks", "project.technology.runtime_versions",
                      "project.technology.build_tools", "project.commands.build", "project.commands.test",
                      "project.modules.source_of_truth")
            for field in fields:
                if incomplete(get(project, field)):
                    issues.append(field + " 未填写或未核实")
            repo_path = get(project, "project.repository.path")
            if isinstance(repo_path, str) and not incomplete(repo_path):
                if Path(repo_path).resolve() != Path(entry.get("repository_path", "")).resolve():
                    issues.append("仓库路径与注册表不一致")
            stacks = get(project, "project.stack_profiles") or []
            for stack in stacks:
                if not (AI / "config/stack-profiles" / (str(stack) + ".yaml")).is_file():
                    issues.append("技术栈画像不存在: " + str(stack))
            for field in ("project.modules.source_of_truth",):
                value = get(project, field)
                if isinstance(value, str) and not incomplete(value):
                    source = Path(repo_path) / value if isinstance(repo_path, str) else Path(value)
                    if not source.is_file():
                        issues.append(field + " 指向的文件不存在")
            if any("frontend" in str(s) or "harmonyos" in str(s) for s in stacks):
                for field in ("project.ui.guidelines", "project.ui.design_source"):
                    if incomplete(get(project, field)):
                        issues.append(field + " 未填写或未核实")
                guideline = get(project, "project.ui.guidelines")
                if isinstance(guideline, str) and not incomplete(guideline):
                    if not (Path(repo_path) / guideline).is_file():
                        issues.append("UI 规范文件不存在")
            for field in ("project.ui.ardot_project_id", "project.ui.ardot_mcp_connection"):
                value = get(project, field)
                if value is not None and incomplete(value):
                    issues.append(field + " 未核实；执行 UI 入项前必须补齐")
    print(json.dumps({"project_id": args.project_id, "ready": not issues, "issues": issues}, ensure_ascii=False, indent=2))
    return 0 if not issues else 1


if __name__ == "__main__":
    raise SystemExit(main())
