#!/usr/bin/env python3
"""Compare clean before/after checkouts on one machine; preserve exact results.

Requires Java 21, Scala CLI, Rust/Cargo 1.99.1, CMake, OpenFst and Boost.
Each worker pins a before/after pair to the same eight CPUs. Initial parallel
measurements screen for regressions; repeat suspicious pairs in isolation.
"""
import argparse
import concurrent.futures
import csv
import json
import os
from pathlib import Path
import subprocess
import threading
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--before', type=Path, required=True)
    parser.add_argument('--after', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--workers', type=int, default=6)
    parser.add_argument('--cpu-offset', type=int, default=0, help='Skip this many available CPUs')
    parser.add_argument('--heap', default='192g')
    parser.add_argument('--warmup-ms', type=int, default=1000)
    parser.add_argument('--sample-ms', type=int, default=50)
    parser.add_argument('--seconds', type=int, default=3600)
    parser.add_argument('--family', choices=['programs', 'large'], default='programs')
    parser.add_argument('--only', action='append', choices=['scala', 'bits', 'bytes', 'pathmap', 'openfst'])
    parser.add_argument('--index', action='append', type=int)
    parser.add_argument('--reverse', action='store_true')
    parser.add_argument('--reuse-classpath', type=Path, help='Prior run containing compiled revision directories')
    args = parser.parse_args()
    roots = {'before': args.before.resolve(), 'after': args.after.resolve()}
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    if (output / 'runs.json').exists():
        parser.error('choose an unused output directory')
    probe = Path(__file__).with_name('CornerstoneRegressionProbe.scala')
    environment = os.environ.copy()
    environment.update(ZIPPY_STACK_MIB='1024', ZIPPY_BENCH_WARMUPS='0', RUSTFLAGS='-C target-cpu=native')
    environment.pop('_JAVA_OPTIONS', None)
    classpaths = {}
    for revision, root in roots.items():
        folder = output / revision
        folder.mkdir(exist_ok=True)
        if args.reuse_classpath:
            classpaths[revision] = (args.reuse_classpath / revision / 'classpath.txt').read_text().strip()
        else:
            jar = folder / 'benchmarks.jar'
            command = ['scala-cli', 'compile', str(root / 'src/main/scala'), str(root / 'src/test/scala'),
                       str(probe), '--server=false', '--scala', '3.8.1', '--source', '3.3',
                       '--dependency', 'org.scalameta::munit:1.2.1', '--dependency',
                       'org.scala-lang.modules::scala-collection-contrib:0.3.0',
                       '--compilation-output', str(jar), '--print-class-path']
            compile_env = dict(environment, _JAVA_OPTIONS='-Xmx32g -Xss256m -XX:ActiveProcessorCount=16')
            with (folder / 'compile.log').open('w') as log:
                result = subprocess.run(command, cwd=root, env=compile_env, stderr=log,
                                        stdout=subprocess.PIPE, text=True, timeout=args.seconds, check=True)
            entries = result.stdout.strip().splitlines()[-1].split(os.pathsep)
            classpaths[revision] = os.pathsep.join([str(jar)] + [p for p in entries if Path(p).is_file() and Path(p) != jar])
        (folder / 'classpath.txt').write_text(classpaths[revision] + '\n')
    available = sorted(os.sched_getaffinity(0))[args.cpu_offset:]
    assert 0 < args.workers <= len(available) // 8
    lock = threading.Lock()
    slots = list(range(args.workers))
    results = []
    kinds = args.only or (['scala', 'bits', 'bytes', 'pathmap', 'openfst'] if args.family == 'programs'
                         else ['scala', 'pathmap', 'openfst'])
    count = 22 if args.family == 'programs' else 14
    indices = args.index or range(count)
    if any(index < 0 or index >= count for index in indices):
        parser.error('invalid case index')
    if args.family == 'large' and any(kind in ('bits', 'bytes') for kind in kinds):
        parser.error('large fixtures benchmark the trie/zipper references and native backends')

    def pair(index, kind):
        with lock:
            slot = slots.pop()
        cpus = ','.join(map(str, available[slot * 8:(slot + 1) * 8]))
        try:
            order = ['after', 'before'] if args.reverse else ['before', 'after']
            for revision in order:
                root = roots[revision]
                folder = output / revision / f'{kind}-{index:02}'
                folder.mkdir(parents=True)
                command = ['taskset', '-c', cpus, 'java', f'-Xmx{args.heap}', '-Xss256m',
                           '-XX:ActiveProcessorCount=8', '-XX:+ExitOnOutOfMemoryError',
                           f'-Dmorkl.regression.warmupMillis={args.warmup_ms}',
                           f'-Dmorkl.regression.sampleMillis={args.sample_ms}',
                           f'-Dmorkl.pathmap.root={root}', f'-Dmorkl.openfst.root={root}',
                           '-cp', classpaths[revision]]
                if kind == 'scala':
                    main = 'cornerstoneRegressionProbe' if args.family == 'programs' else 'cornerstoneLargeRegressionProbe'
                    command += ['morkl.' + main, str(folder / 'results.csv'), str(index)]
                elif kind in ('bits', 'bytes'):
                    command += ['morkl.cornerstoneRadixRegressionProbe', str(folder / 'results.csv'), str(index), kind]
                else:
                    main = 'rustPathMapBenchmarkReport' if kind == 'pathmap' else 'openFstBenchmarkReport'
                    command += ['morkl.' + main, str(folder), args.family, str(index), str(count)]
                start = time.monotonic()
                with (folder / 'run.log').open('w') as log:
                    try:
                        process = subprocess.run(command, cwd=root, env=environment, stdout=log,
                                                 stderr=subprocess.STDOUT, timeout=args.seconds)
                        code = process.returncode
                    except subprocess.TimeoutExpired:
                        code = 124
                result = dict(revision=revision, kind=kind, index=index, cpus=cpus,
                              exitCode=code, seconds=round(time.monotonic() - start, 3))
                with lock:
                    results.append(result)
                    (output / 'runs.json').write_text(json.dumps(results, indent=2) + '\n')
                    print(result, flush=True)
        finally:
            with lock:
                slots.append(slot)

    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = [pool.submit(pair, index, kind) for index in indices for kind in kinds]
        for future in concurrent.futures.as_completed(futures):
            future.result()
    if any(r['exitCode'] for r in results):
        return 1
    measurements = {}
    for revision in roots:
        files = sorted((output / revision).glob('*/*.csv'))
        assert len(files) == len(indices) * len(kinds), 'missing result files'
        for path in files:
            rows = list(csv.DictReader(path.open()))
            assert rows and all(row['checked'] == 'exact' for row in rows), f'inexact results: {path}'
            kind, index = path.parent.name.rsplit('-', 1)
            scala_rows = 5 if args.family == 'programs' else 2
            assert len(rows) == {'scala': scala_rows, 'bits': 1, 'bytes': 1, 'pathmap': 4, 'openfst': 3}[kind], path
            for row in rows:
                evaluator = row.get('evaluator', kind + ':' + row.get('dialect', ''))
                key = revision, index, evaluator
                assert key not in measurements, f'duplicate measurement: {key}'
                measurements[key] = row
    comparisons = []
    for (revision, index, evaluator), row in measurements.items():
        if revision != 'after':
            continue
        old = measurements['before', index, evaluator]
        assert row['name'] == old['name'] and row.get('variant') == old.get('variant')
        assert row['paths'] == old['paths'], f'changed result count: {index}/{evaluator}'
        assert row.get('output_sha256') == old.get('output_sha256'), f'changed output: {index}/{evaluator}'
        def milliseconds(value):
            return float(value.get('ms', value.get('native_ms', value.get('reference_ms'))))
        before, after = milliseconds(old), milliseconds(row)
        comparisons.append(dict(index=int(index), name=row['name'], variant=row.get('variant', ''),
                                evaluator=evaluator, paths=int(row['paths']), checked='exact',
                                before_ms=before, after_ms=after, ratio=after / before if before else None))
    with (output / 'comparison.csv').open('w') as target:
        writer = csv.DictWriter(target, fieldnames=list(comparisons[0]))
        writer.writeheader()
        writer.writerows(sorted(comparisons, key=lambda row: (row['index'], row['evaluator'])))
    (output / 'summary.json').write_text(json.dumps(dict(success=True, pairs=len(comparisons),
        cases=len(indices), family=args.family, jobs=len(results), workers=args.workers), indent=2) + '\n')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
