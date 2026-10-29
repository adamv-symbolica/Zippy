#!/usr/bin/env python3
"""Measure both digit-set reference interpreters for all 22 cornerstone cases.

Build an assembly whose main is morkl.cornerstoneRadixReference, then run this
from the repository root. Each case/dialect has an isolated JVM and timeout.
"""

import argparse
import concurrent.futures
import csv
import json
import os
import platform
import signal
import subprocess
import time
from datetime import datetime, timezone
from pathlib import Path


def main():
    p = argparse.ArgumentParser(description=__doc__)
    runtime = p.add_mutually_exclusive_group(required=True)
    runtime.add_argument("--jar", type=Path)
    runtime.add_argument("--classpath", help="Reuse the immutable benchmark JAR and its dependencies")
    p.add_argument("--output", type=Path, required=True)
    p.add_argument("--workers", type=int, default=4)
    p.add_argument("--seconds", type=int, default=600)
    p.add_argument("--heap", default="128g")
    args = p.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    if (output / "summary.json").exists():
        p.error("choose a fresh output directory")
    started = datetime.now(timezone.utc).isoformat()

    def run(job):
        index, dialect = job
        stem = f"{index:02d}-{dialect}"
        destination = output / f"{stem}.csv"
        command = ["java", f"-Xmx{args.heap}", "-Xss64m", "-XX:ActiveProcessorCount=8",
                   *( ["-jar", str(args.jar.resolve())] if args.jar else
                      ["-cp", args.classpath, "morkl.cornerstoneRadixReference"] ),
                   str(destination), str(index), dialect]
        start = time.monotonic()
        with (output / f"{stem}.log").open("w") as log:
            process = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
            try:
                code = process.wait(timeout=args.seconds)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait()
                code = 124
        result = dict(index=index, dialect=dialect, exitCode=code,
                      seconds=round(time.monotonic() - start, 3), csv=destination.name)
        print(json.dumps(result), flush=True)
        return result

    jobs = [(i, d) for i in range(22) for d in ("bits", "bytes")]
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
        results = list(pool.map(run, jobs))
    rows = []
    for result in results:
        if result["exitCode"] == 0:
            with (output / result["csv"]).open(newline="") as handle:
                current = list(csv.DictReader(handle))
            assert len(current) == 1
            rows.extend(current)
    if rows:
        with (output / "reference.csv").open("w", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=list(rows[0]), lineterminator="\n")
            writer.writeheader()
            writer.writerows(rows)
    report = dict(success=len(rows) == 44 and all(r["exitCode"] == 0 for r in results),
                  completed=len(rows), total=44, startedUTC=started,
                  finishedUTC=datetime.now(timezone.utc).isoformat(), workers=args.workers,
                  heap=args.heap, timeoutSeconds=args.seconds, hostname=platform.node(),
                  cpu=subprocess.run(["lscpu"], capture_output=True, text=True).stdout,
                  java=subprocess.run(["java", "-version"], capture_output=True, text=True).stderr,
                  runs=results)
    (output / "summary.json").write_text(json.dumps(report, indent=2) + "\n")
    return 0 if report["success"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
