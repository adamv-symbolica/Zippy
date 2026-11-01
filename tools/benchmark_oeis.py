#!/usr/bin/env python3
"""Export and run Zippy's OEIS programs beside the upstream PathMap algorithm."""
import argparse
import csv
import hashlib
import json
import os
from pathlib import Path
import platform
import statistics
import subprocess
import sys
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--data', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--samples', type=int, default=3)
    parser.add_argument('--classpath', help='Precompiled benchmark JAR/dependency classpath')
    parser.add_argument('--scala-cli', default='scala-cli')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    out, data = args.output.resolve(), args.data.resolve()
    out.mkdir(parents=True, exist_ok=True)
    env = dict(os.environ, RUSTFLAGS='-C target-cpu=native')
    env['_JAVA_OPTIONS'] = env.get('_JAVA_OPTIONS', '') + ' -Xss64m'

    def run(command, log, **kwargs):
        with (out / log).open('w') as handle:
            subprocess.run(command, cwd=root, env=env, stdout=handle, stderr=subprocess.STDOUT,
                           timeout=7200, check=True, **kwargs)

    export = (['java', '-cp', args.classpath, 'morkl.exportOEISRust'] if args.classpath else
              [args.scala_cli, 'run', 'src/main/scala', 'src/test/scala', '--server=false',
               '--scala', '3.8.1', '--source', '3.3', '--dependency', 'org.scalameta::munit:1.2.1',
               '--dependency', 'org.scala-lang.modules::scala-collection-contrib:0.3.0',
               '--main-class', 'morkl.exportOEISRust', '--'])
    run(export + [str(out / 'sources')], 'export.log')
    target = out / 'runtime-target'
    run(['cargo', 'build', '--release', '--locked', '--manifest-path',
         str(root / 'src/main/rust/pathmap/Cargo.toml'), '--target-dir', str(target)], 'runtime.log')
    library, source = target / 'release', out / 'sources/oeis.rs'
    length = len(source.read_text())
    level = 0 if length > 2000000 else 1 if length > 500000 else 3
    command = ['rustc', '--edition=2024', '--crate-name', 'oeis_zippy', '-C', f'opt-level={level}',
               '-C', 'codegen-units=16', '-C', 'target-cpu=native', '-L', 'dependency=' + str(library / 'deps')]
    for crate in ['zippy_pathmap', 'pathmap', 'num_bigint']:
        files = list((library / 'deps').glob('lib' + crate + '-*.rlib'))
        assert len(files) == 1, (crate, files)
        command += ['--extern', crate + '=' + str(files[0])]
    run(command + [str(source), '-o', str(out / 'oeis')], 'rustc.log')
    start = time.monotonic()
    with (out / 'samples.csv').open('w') as output, (out / 'execution.log').open('w') as log:
        p = subprocess.run([str(out / 'oeis'), str(data), str(args.samples)], cwd=root, env=env,
                           stdout=output, stderr=log, timeout=7200)
    rows = list(csv.DictReader((out / 'samples.csv').open()))
    assert p.returncode == 0 and len(rows) == 2 * 15 * args.samples, (p.returncode, len(rows))
    medians = []
    for engine, stage in sorted({(r['engine'], r['stage']) for r in rows}):
        group = [r for r in rows if (r['engine'], r['stage']) == (engine, stage)]
        assert len({r['paths'] for r in group}) == 1
        medians.append(dict(engine=engine, stage=stage, paths=group[0]['paths'], samples=len(group),
                            exec_ms=statistics.median(int(r['exec_ns']) for r in group) / 1e6,
                            count_ms=statistics.median(int(r['count_ns']) for r in group) / 1e6))
    with (out / 'median.csv').open('w', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=list(medians[0])); writer.writeheader(); writer.writerows(medians)
    report = dict(success=True, samples=len(rows), stages=15, repeats=args.samples,
                  exactKeySets=True, seconds=round(time.monotonic() - start, 3),
                  dataset=dict(path=data.name, bytes=data.stat().st_size, sha256=hashlib.sha256(data.read_bytes()).hexdigest()),
                  generatedSourceBytes=source.stat().st_size, generatedOptimization=level,
                  runtimeOptimization=3, platform=platform.platform(),
                  source='https://github.com/Adam-Vandervorst/PathMap/blob/master/benches/oeis.rs',
                  notes='Record-trie ingestion is outside Zippy execution. Native terminals retain usize IDs; Zippy checks the exact key set with unit terminals. Execution and cardinality timings are separate; count_contents includes counting in execution.')
    (out / 'summary.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))
    return 0


if __name__ == '__main__':
    sys.exit(main())
