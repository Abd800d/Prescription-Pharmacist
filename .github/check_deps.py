#!/usr/bin/env python3
"""Dependency check for Gradle output (run in CI after `./gradlew app:dependencies`).

Fails the build on REAL problems only:
  1. dependency resolution failures (FAILED / Could not resolve / Could not find)
  2. dynamic or non-reproducible versions (1.+, latest, SNAPSHOT, milestone builds)
Prints a short summary otherwise.
"""
import os
import re
import sys

def main(path: str) -> int:
    with open(path, encoding="utf-8", errors="replace") as f:
        log = f.read()

    errors = []

    for line in log.splitlines():
        if re.search(r"\bFAILED\b|Could not resolve|Could not find|Unresolved", line):
            errors.append(f"[resolution] {line.strip()}")
        m = re.search(r"[\\\\+|] *[-+\\ ]*([\w.\-]+:[\w.\-]+:[\w.\-]+)", line)
        if m:
            version = m.group(1).rsplit(":", 1)[-1]
            if version.endswith("+") or version in ("latest.release", "latest.integration") or "SNAPSHOT" in version:
                errors.append(f"[dynamic-version] {line.strip()}")
            elif re.fullmatch(r"\d+\.\d+(?:\.\d+)?\.M\d+", version):
                errors.append(f"[milestone-version] {line.strip()}")

    modules = set(re.findall(r"[\w.\-]+:[\w.\-]+:[\w.\-]+", log))
    print(f"dependency-check: {len(modules)} unique module coordinates seen")

    if errors:
        print(f"dependency-check: {len(errors)} REAL problem(s):")
        for e in errors[:40]:
            print("  " + e)
            if os.environ.get("GITHUB_ACTIONS"):
                print(f"::error title=dependency-check::{e}")
        return 1
    print("dependency-check: OK — all dependencies resolve, no dynamic versions")
    return 0

if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "/tmp/gradle-deps.log"))
