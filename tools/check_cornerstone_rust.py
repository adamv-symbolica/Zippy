#!/usr/bin/env python3
"""Compile every exported Rust program and check exact independent Scala results."""
import argparse
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bundle', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--workers', type=int, default=8)
    args = parser.parse_args()
    bundle, output = args.bundle.resolve(), args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    env = dict(os.environ, RUSTFLAGS='-C target-cpu=native')
    with (output / 'runtime-build.log').open('w') as log:
        subprocess.run(['cargo', 'build', '--release', '--locked'], cwd=bundle / 'runtime', env=env,
                       stdout=log, stderr=subprocess.STDOUT, check=True)
    library = bundle / 'runtime/target/release'
    manifest = json.loads((bundle / 'manifest.json').read_text())

    def run(artifact):
        source = bundle / artifact['source']
        assert hashlib.sha256(source.read_bytes()).hexdigest() == artifact['sha256']
        stem = '-'.join(source.parts[-3:]).removesuffix('.rs')
        executable, log = output / stem, output / (stem + '.log')
        with log.open('w') as handle:
            subprocess.run(['rustc', '--crate-name', 'cornerstone', '--edition=2024', '-O',
                            '-C', 'target-cpu=native', '-L', 'dependency=' + str(library / 'deps'),
                            '--extern', 'zippy_pathmap=' + str(library / 'libzippy_pathmap.rlib'),
                            str(source), '-o', str(executable)], check=True,
                           stdout=handle, stderr=subprocess.STDOUT, timeout=1200)
        result = subprocess.run([str(executable)], input=source.with_suffix('.input.txt').read_text(),
                                capture_output=True, text=True, check=True, timeout=1200)
        tokens = iter(result.stdout.split())
        assert next(tokens) == 'O'
        count = int(next(tokens))
        codes = json.loads((source.parent / 'encodings.json').read_text())
        dictionary = {tuple(r[artifact['dialect']]): r['symbol'] for r in codes}
        prefixes = {code[:i] for code in dictionary for i in range(1, len(code) + 1)}
        decoded = set()
        for _ in range(count):
            path = tuple(int(next(tokens)) - 1 for _ in range(int(next(tokens))))
            word, prefix = [], ()
            for digit in path:
                prefix += (digit,)
                assert prefix in prefixes, (source, prefix)
                if prefix in dictionary:
                    word.append(dictionary[prefix]); prefix = ()
            assert not prefix, (source, prefix)
            decoded.add(tuple(word))
        assert next(tokens, None) is None
        expected = {tuple(p) for p in json.loads((source.parent / 'expected.json').read_text())}
        assert decoded == expected, (source, len(decoded), len(expected))
        print(artifact['source'], count, flush=True)
        return dict(source=artifact['source'], sha256=artifact['sha256'], paths=count,
                    compiled=True, executed=True, exact=True)

    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
        results = list(pool.map(run, manifest['artifacts']))
    report = dict(success=True, sources=len(results), workloads=manifest['workloads'],
                  optimization='-O / opt-level=3 target-cpu=native', results=results)
    (output / 'summary.json').write_text(json.dumps(report, indent=2) + '\n')


if __name__ == '__main__':
    main()
