#!/usr/bin/env python3
"""Build the OpenTelemetry Collector config from config/agents/*.yaml."""
from __future__ import annotations

import argparse
from pathlib import Path


ROOT = Path(__file__).resolve().parent


def read_definition(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or ":" not in line:
            continue
        key, value = line.split(":", 1)
        values[key.strip()] = value.strip()
    required = {"name", "kind", "signals"}
    missing = required - values.keys()
    if missing:
        raise ValueError(f"{path}: missing {', '.join(sorted(missing))}")
    values["signals"] = values["signals"].replace(" ", "").split(",")
    return values


def build_config(definitions: list[dict[str, str]]) -> str:
    receivers: list[str] = []
    exporters: list[str] = []
    connectors: list[str] = []
    pipelines: list[str] = []
    otlp_agents = [item for item in definitions if item["kind"] == "otlp"]

    for item in definitions:
        name = item["name"]
        output_name = item.get("output_name", name)
        signals = item["signals"]
        if item["kind"] == "filelog":
            receiver_name = f"file_log/{name}"
            receivers.extend([
                f"  {receiver_name}:",
                "    include:",
                f"      - {item['input']}",
                "    start_at: beginning",
                "    operators:",
                "      - type: json_parser",
                "        parse_from: body",
                "      - type: add",
                "        field: attributes.agent",
                f"        value: {output_name}",
            ])
            for signal in signals:
                exporter_name = f"file/{signal}/{name}"
                output_file = item.get("output_file", f"{signal}.jsonl")
                exporters.extend([
                    f"  {exporter_name}:",
                    f"    path: /var/log/otel/{output_name}/{output_file}",
                ])
                pipelines.extend([
                    f"    {signal}/{name}:",
                    f"      receivers: [{receiver_name}]",
                    "      processors: [batch]",
                    f"      exporters: [{exporter_name}, debug]",
                ])
        elif item["kind"] == "otlp":
            for signal in signals:
                exporter_name = f"file/{signal}/{name}"
                output_file = item.get("output_file", f"{signal}.jsonl")
                exporters.extend([
                    f"  {exporter_name}:",
                    f"    path: /var/log/otel/{output_name}/{output_file}",
                ])
                pipelines.extend([
                    f"    {signal}/{name}:",
                    f"      receivers: [routing/{signal}]",
                    "      processors: [batch]",
                    f"      exporters: [{exporter_name}, debug]",
                ])
        else:
            raise ValueError(f"{name}: unsupported kind {item['kind']!r}")

    for signal in sorted({signal for item in otlp_agents for signal in item["signals"]}):
        connectors.extend([
            f"  routing/{signal}:",
            "    table:",
        ])
        for item in otlp_agents:
            if signal in item["signals"]:
                service_name = item["service_name"]
                agent_name = item["name"]
                connectors.extend([
                    "      - context: resource",
                    f"        condition: 'resource.attributes[\"service.name\"] == \"{service_name}\"'",
                    f"        pipelines: [{signal}/{agent_name}]",
                ])
        pipelines.extend([
            f"    {signal}:",
            "      receivers: [otlp]",
            "      processors: [batch]",
            f"      exporters: [routing/{signal}]",
        ])

    template = (ROOT / "base.yaml").read_text(encoding="utf-8")
    replacements = {
        "{{RECEIVERS}}": "\n".join(receivers),
        "{{EXPORTERS}}": "\n".join(exporters),
        "{{CONNECTORS}}": "\n".join(connectors),
        "{{PIPELINES}}": "\n".join(pipelines),
    }
    for marker, value in replacements.items():
        template = template.replace(marker, value)
    return template


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=Path, default=ROOT / "generated" / "collector.yaml")
    args = parser.parse_args()
    definitions = [read_definition(path) for path in sorted((ROOT / "agents").glob("*.yaml"))]
    if not definitions:
        raise SystemExit("No agent definitions found in config/agents")
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(build_config(definitions), encoding="utf-8")
    print(f"Generated {args.out} from {len(definitions)} agent definition(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())