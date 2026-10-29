#!/usr/bin/env python3
"""Run every Scala test and differential C++/OpenFst replay, with a durable ledger."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--workers', type=int, default=32)
    parser.add_argument('--batch', type=int, default=64)
    parser.add_argument('--heap', default='256g')
    parser.add_argument('--optimize', choices=['-O0', '-O1', '-O2', '-O3'], default='-O2', help='Baseline C++ optimization level; cold large units use the documented adaptive policy')
    parser.add_argument('--seconds', type=int, default=14400)
    parser.add_argument('--scala-cli', default='scala-cli')
    parser.add_argument('--test-only')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    summary = output / 'audit/summary.json'
    if summary.exists():
        parser.error(f'{summary} already exists; choose a new output directory')
    command = [args.scala_cli, 'test', 'src/main/scala', 'src/test/scala', '--server=false',
               '--scala', '3.8.1', '--source', '3.3', '--dependency', 'org.scalameta::munit:1.2.1',
               '--dependency', 'org.scala-lang.modules::scala-collection-contrib:0.3.0', '--require-tests']
    if (root / 'valued/src/main/scala').exists():
        command += ['valued/src/main/scala', 'valued/src/test/scala']
    if args.test_only:
        command += ['--test-only', args.test_only]
    env = os.environ.copy()
    env['_JAVA_OPTIONS'] = (env.get('_JAVA_OPTIONS', '') + f' -Xmx{args.heap} -Xss64m'
        f' -Dmorkl.openfst.test=true -Dmorkl.openfst.root={root} -Dmorkl.openfst.optimize={args.optimize}'
        f' -Dmorkl.openfstAudit.path={output / "audit"}'
        f' -Dmorkl.openfstAudit.workers={args.workers} -Dmorkl.openfstAudit.batch={args.batch}'
        f' -Dmorkl.openfstAudit.seconds={args.seconds}').strip()
    with (output / 'tests.log').open('w') as log:
        result = subprocess.run(command, cwd=root, env=env, stdout=log, stderr=subprocess.STDOUT)
    audit = json.loads(summary.read_text()) if summary.exists() else None
    valid = (result.returncode == 0 and audit is not None and audit['finished']
             and audit['cases'] > 0 and audit['checks'] == audit['cases'] and audit['failures'] == 0)
    if not args.test_only and audit:
        valid = valid and audit['symbolCases'] > 0 and all(
            audit['passed'].get(d) == audit['symbolCases']
            for d in ('symbols', 'bits-fibonacci', 'bytes-strings', 'bytes-interned',
                      'bits-bytes', 'bytes-bits', 'bits-symbols', 'bytes-symbols'))
    text = re.sub(r'\x1b\[[0-9;]*m', '', (output / 'tests.log').read_text(errors='replace'))
    report = dict(testExitCode=result.returncode, generatedOptimization=args.optimize, largeGeneratedOptimization='-O0 for cold units above 2M characters; reuse optimized cache when available', passedTests=sum(line.startswith('  + ') for line in text.splitlines()),
                  fullSuite=args.test_only is None, audit=audit, success=bool(valid))
    (output / 'result.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))
    return 0 if valid else 1


if __name__ == '__main__':
    sys.exit(main())
