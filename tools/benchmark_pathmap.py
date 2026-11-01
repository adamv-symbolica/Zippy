#!/usr/bin/env python3
"""Run all existing benchmark mains and the Rust/PathMap byte backend on all encodings."""
import argparse
import concurrent.futures
import csv
import re
import json
import os
from pathlib import Path
import subprocess
import signal
import shutil
import sys
import time

EXISTING = ['trieBenchmarkReport', 'constantFoldExecutorReport', 'zipperAlgebraBenchmarkReport',
            'zipperIterRangeTailMicroReport', 'zipperLargeBenchmarkReport', 'independentProductBenchmark',
            'trieConstructionAsymptoticBenchmark', 'trieIncrementalUnionAsymptoticBenchmark',
            'trieAlgebraAsymptoticBenchmark', 'trieAlgebraDistributionReport', 'spatialCostCounterReport',
            'spatialCostCorrelationReport', 'spatialTypeScalingAudit', 'cornerstoneSpatialTypeReport']
FAMILIES = ['programs', 'large', 'algebra', 'iter-range-tail', 'algebra-scaling', 'construction', 'independent-product', 'cost-counter', 'cost-correlation']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--workers', type=int, default=1, help='Use one worker for isolated timings')
    parser.add_argument('--shards', type=int, default=1, help='Split each native logical family across independent JVMs')
    parser.add_argument('--seconds', type=int, default=9000)
    parser.add_argument('--heap', default='192g')
    parser.add_argument('--scala-cli', default='scala-cli')
    parser.add_argument('--only', action='append', help='Run only a named benchmark main or native family; repeatable')
    parser.add_argument('--native-only', action='store_true')
    parser.add_argument('--existing-only', action='store_true')
    args = parser.parse_args()
    if args.native_only and args.existing_only:
        parser.error('--native-only and --existing-only are mutually exclusive')
    root = Path(__file__).resolve().parents[1]
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    if (output / 'summary.json').exists():
        parser.error('output already contains a run; choose a new directory')
    env = os.environ.copy()
    env.setdefault('ZIPPY_STACK_MIB', '1024')
    env.setdefault('RUSTFLAGS', '-C target-cpu=native')
    env.setdefault('ZIPPY_BENCH_WARMUPS', '0')
    env['_JAVA_OPTIONS'] = env.get('_JAVA_OPTIONS', '') + f' -Xmx{args.heap} -Xss64m -Dmorkl.pathmap.root={root}'
    jobs = ([] if args.native_only else [('existing', n) for n in EXISTING]) + (
        [] if args.existing_only else [('native', n) for n in FAMILIES] + [('native-micro', 'acceptor-algebra'), ('reuse', 'cursor-reuse')])
    if args.only:
        unknown = set(args.only) - {name for _, name in jobs}
        if unknown:
            parser.error(f'unknown or excluded benchmark names: {sorted(unknown)}')
        jobs = [job for job in jobs if job[1] in args.only]

    if args.shards < 1:
        parser.error('--shards must be positive')
    if args.shards > 1:
        jobs = [(kind, f'{name}--shard-{i}-of-{args.shards}' if kind == 'native' else name)
                for kind, name in jobs for i in (range(args.shards) if kind == 'native' else [0])]

    # Scala CLI may rewrite shared class directories during concurrent runs.
    # Compile one private source snapshot to a JAR, then launch every benchmark
    # against immutable classes. Native processes still use the requested repo.
    if any(kind != 'native-micro' for kind, _ in jobs):
        snapshot = output / 'scala-sources'
        for relative in ('src/main/scala', 'src/test/scala'):
            shutil.copytree(root / relative, snapshot / relative,
                            ignore=shutil.ignore_patterns('.scala-build', '.bsp', 'target'))
        jar = output / 'benchmarks.jar'
        compile_command = [args.scala_cli, 'compile', str(snapshot / 'src/main/scala'), str(snapshot / 'src/test/scala'),
                           '--server=false', '--scala', '3.8.1', '--source', '3.3',
                           '--dependency', 'org.scalameta::munit:1.2.1', '--dependency',
                           'org.scala-lang.modules::scala-collection-contrib:0.3.0',
                           '--compilation-output', str(jar), '--print-class-path']
        with (output / 'compile.log').open('w') as log:
            compiled = subprocess.run(compile_command, cwd=root, env=env, stdout=subprocess.PIPE,
                                      stderr=log, text=True, timeout=args.seconds)
        if compiled.returncode != 0:
            report = dict(success=False, completed=0, total=len(jobs), compileExitCode=compiled.returncode, runs=[])
            (output / 'summary.json').write_text(json.dumps(report, indent=2) + '\n')
            return 1
        entries = compiled.stdout.strip().splitlines()[-1].split(os.pathsep)
        classpath = os.pathsep.join([str(jar)] + [p for p in entries if Path(p).is_file() and Path(p) != jar])
        (output / 'classpath.txt').write_text(classpath + '\n')
        java = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if os.environ.get('JAVA_HOME') else 'java'
        base = [java, '-cp', classpath]
    else:
        base = []

    def run(job):
        kind, name = job
        folder = output / kind
        folder.mkdir(exist_ok=True)
        start = time.monotonic()
        def execute(command, log, error_log=None):
            process = subprocess.Popen(command, cwd=root, env=env, stdout=log,
                                       stderr=error_log if error_log is not None else subprocess.STDOUT,
                                       start_new_session=True)
            try:
                return process.wait(timeout=args.seconds)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait()
                return 124
        with (folder / (name + '.log')).open('w') as log:
            if kind == 'native-micro':
                with (output / 'native-micro.csv').open('w') as csv_file:
                    code = execute(['cargo', 'run', '--release', '--locked', '--manifest-path',
                                    str(root / 'src/main/rust/pathmap/Cargo.toml'), '--target-dir',
                                    str(output / 'native-micro-target'), '--example', 'benchmark'], csv_file, log)
            else:
                main = name if kind == 'existing' else 'cursorReuseBenchmarkReport' if kind == 'reuse' else 'rustPathMapBenchmarkReport'
                command = base + ['morkl.' + main]
                if kind == 'native':
                    family, _, suffix = name.partition('--shard-')
                    shard, shards = suffix.split('-of-') if suffix else ('0', '1')
                    command += [str(folder / name), family, shard, shards]
                elif name == 'cornerstoneSpatialTypeReport':
                    command += [str(folder / (name + '.md'))]
                elif kind == 'reuse':
                    command += [str(folder / name)]
                code = execute(command, log)
        return dict(kind=kind, name=name, exitCode=code, seconds=round(time.monotonic() - start, 3))

    results = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
        for future in concurrent.futures.as_completed([pool.submit(run, job) for job in jobs]):
            results.append(future.result())
            report = dict(success=all(r['exitCode'] == 0 for r in results) and len(results) == len(jobs),
                          completed=len(results), total=len(jobs), workers=args.workers, runs=results)
            (output / 'summary.json').write_text(json.dumps(report, indent=2) + '\n')
            print(results[-1], flush=True)
    if report['success'] and args.shards > 1:
        report['familyWorkloads'] = {}
        for family in sorted({name.partition('--shard-')[0] for kind, name in jobs if kind == 'native'}):
            rows, seen, complete, total = [], set(), 0, 0
            for index in range(args.shards):
                part = output / 'native' / f'{family}--shard-{index}-of-{args.shards}'
                match = re.match(r'completed=(\d+)/(\d+)', (part / 'summary.txt').read_text())
                assert match and match[1] == match[2], f'incomplete shard: {part}'
                complete += int(match[1]); total += int(match[2])
                with (part / 'native.csv').open() as source:
                    reader = csv.DictReader(source)
                    fields = reader.fieldnames
                    for row in reader:
                        key = row['name'], row['dialect']
                        assert key not in seen, f'duplicate workload: {key}'
                        seen.add(key); rows.append(row)
            assert len(rows) == 4 * total, f'missing dialect rows: {family}'
            merged = output / 'native' / family
            merged.mkdir(exist_ok=True)
            with (merged / 'native.csv').open('w', newline='') as target:
                writer = csv.DictWriter(target, fieldnames=fields)
                writer.writeheader(); writer.writerows(sorted(rows, key=lambda r: (r['name'], r['dialect'])))
            (merged / 'summary.txt').write_text(f'completed={complete}/{total}\n\n')
            report['familyWorkloads'][family] = total
        (output / 'summary.json').write_text(json.dumps(report, indent=2) + '\n')
    return 0 if report['success'] else 1


if __name__ == '__main__':
    sys.exit(main())
