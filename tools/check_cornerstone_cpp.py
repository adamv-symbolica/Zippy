#!/usr/bin/env python3
"""Compile and execute every exported cornerstone fixture; compare dialects exactly."""
import argparse
import concurrent.futures
import hashlib
import json
from pathlib import Path
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bundle', type=Path, required=True)
    parser.add_argument('--library', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--workers', type=int, default=8)
    parser.add_argument('--cxx', default='c++')
    parser.add_argument('--seconds', type=int, default=1800)
    args = parser.parse_args()
    bundle, output, library = args.bundle.resolve(), args.output.resolve(), args.library.resolve()
    output.mkdir(parents=True, exist_ok=True)
    manifest = json.loads((bundle / 'manifest.json').read_text())
    assert len(manifest['artifacts']) == 108

    def run(artifact):
        source = bundle / artifact['source']
        assert artifact['standalone'] and artifact['callbackCount'] == 0
        assert hashlib.sha256(source.read_bytes()).hexdigest() == artifact['sha256']
        stem = '-'.join(source.parts[-3:]).removesuffix('.cpp')
        executable, log = output / stem, output / (stem + '.log')
        command = [args.cxx, '-std=c++17', '-O0', '-I' + str(bundle / 'runtime'),
                   str(source), str(library), '-Wl,-rpath,' + str(library.parent), '-o', str(executable)]
        with log.open('w') as handle:
            compiled = subprocess.run(command, stdout=handle, stderr=subprocess.STDOUT, timeout=args.seconds)
        assert compiled.returncode == 0, log
        result = subprocess.run([str(executable)], input=source.with_suffix('.input.txt').read_text(),
                                capture_output=True, text=True, timeout=args.seconds)
        assert result.returncode == 0, result.stderr[-2000:]
        tokens = iter(result.stdout.split())
        assert next(tokens) == 'O', result.stdout[:1000]
        count = int(next(tokens))
        raw = [tuple(int(next(tokens)) for _ in range(int(next(tokens)))) for _ in range(count)]
        assert next(tokens, None) is None
        if artifact['dialect'] == 'symbols':
            labels = {r['id']: r['symbol'] for r in json.loads(source.with_suffix('.bindings.json').read_text())['symbols']}
            decoded = {tuple(labels[d] for d in path) for path in raw}
        else:
            codes = json.loads((source.parent / 'encodings.json').read_text())
            dictionary = {tuple(r[artifact['dialect']]): r['symbol'] for r in codes}
            prefixes = {code[:i] for code in dictionary for i in range(1, len(code) + 1)}
            decoded = set()
            for path in raw:
                word, prefix = [], ()
                for digit in path:
                    prefix += (digit - 1,)
                    assert prefix in prefixes, (source, prefix)
                    if prefix in dictionary:
                        word.append(dictionary[prefix]); prefix = ()
                assert not prefix, (source, prefix)
                decoded.add(tuple(word))
        assert len(decoded) == count
        print(artifact['source'], count, flush=True)
        return artifact, decoded

    results, cases = [], {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
        for artifact, decoded in pool.map(run, manifest['artifacts']):
            key = (artifact['family'], artifact['name'], artifact['variant'])
            if key in cases:
                assert cases[key] == decoded, (key, artifact['dialect'])
            else:
                cases[key] = decoded
            digest = hashlib.sha256(json.dumps(sorted(decoded), ensure_ascii=True).encode()).hexdigest()
            results.append(dict(source=artifact['source'], sha256=artifact['sha256'], paths=len(decoded),
                                decodedResultSHA256=digest, compiled=True, executed=True))
    assert len(cases) == 36
    report = dict(success=True, cppSources=len(results), cases=len(cases), exactCrossDialectComparisons=72,
                  optimization='-O0 review builds', results=results)
    (output / 'summary.json').write_text(json.dumps(report, indent=2) + '\n')


if __name__ == '__main__':
    main()
