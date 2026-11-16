#!/usr/bin/env python3
"""Run all Scala tests and audit captured reference programs through eight translations."""
import argparse
import json
import os
import re
from pathlib import Path
import subprocess
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=Path("/tmp/zippy-radix-full-suite"))
    parser.add_argument("--scala-cli", default="scala-cli")
    parser.add_argument("--heap", default="8g")
    parser.add_argument("--stack", default="64m", help="JVM thread stack; deep cold trie tests may need 256m.")
    parser.add_argument("--workers", type=int, default=16)
    parser.add_argument("--seconds", type=int, default=5400)
    parser.add_argument("--test-only", help="Optional diagnostic subset; omitted runs all tests.")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    summary_path = output / "audit" / "summary.json"
    if summary_path.exists():
        parser.error("output already contains an audit summary; use a fresh directory")
    command = [args.scala_cli, "test", "src/main/scala", "src/test/scala", "--server=false",
               "--scala", "3.8.1", "--source", "3.3", "--dependency", "org.scalameta::munit:1.2.1",
               "--dependency", "org.scala-lang.modules::scala-collection-contrib:0.3.0", "--require-tests"]
    if (root / "valued/src/main/scala").exists():
        command += ["valued/src/main/scala", "valued/src/test/scala"]
    if args.test_only:
        command += ["--test-only", args.test_only]
    env = os.environ.copy()
    env["_JAVA_OPTIONS"] = (env.get("_JAVA_OPTIONS", "") + f" -Xmx{args.heap} -Xss{args.stack}"
                            f" -Dmorkl.radixAudit.path={output / 'audit'}"
                            f" -Dmorkl.radixAudit.workers={args.workers}"
                            f" -Dmorkl.radixAudit.seconds={args.seconds}").strip()
    with (output / "tests.log").open("w") as log:
        completed = subprocess.run(command, cwd=root, env=env, stdout=log, stderr=subprocess.STDOUT)
    log_text = (output / "tests.log").read_text(errors="replace")
    log_text = re.sub(r"\x1b\[[0-9;]*m", "", log_text)
    audit = json.loads(summary_path.read_text()) if summary_path.exists() else None
    valid = (completed.returncode == 0 and audit is not None and audit["finished"]
             and audit["cases"] > 0 and audit["completed"] == audit["cases"]
             and audit["checks"] == audit["cases"] * 8 and audit["failures"] == 0
             and set(audit["passed"]) == {"symbols-bits", "strings-bytes", "length-bytes", "interned-bytes", "bits-bytes", "bytes-bits", "bits-symbols", "bytes-symbols"} and all(n == audit["cases"] for n in audit["passed"].values()))
    report = {"testExitCode": completed.returncode, "passedTests": sum(line.startswith("  + ") for line in log_text.splitlines()),
              "fullSuite": args.test_only is None, "audit": audit, "success": valid}
    (output / "result.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))
    print(f"Logs: {output / 'tests.log'}")
    return 0 if valid else 1


if __name__ == "__main__":
    sys.exit(main())
