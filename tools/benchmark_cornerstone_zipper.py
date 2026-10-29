#!/usr/bin/env python3
"""Attempt the three previously omitted evalZ source cases in bounded JVMs."""
import argparse
import concurrent.futures
import csv
import json
from pathlib import Path
import signal
import subprocess
import time


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--classpath', required=True, help='Probe JAR, benchmark JAR and runtime dependencies')
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--heap', default='192g')
    p.add_argument('--seconds', type=int, default=600)
    args = p.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    assert not (output / 'summary.json').exists()

    def run(index):
        start = time.monotonic()
        path = output / f'{index}.csv'
        cmd = ['java', '-Xmx' + args.heap, '-Xss64m', '-XX:ActiveProcessorCount=8',
               '-XX:+ExitOnOutOfMemoryError', '-cp', args.classpath,
               'morkl.cornerstoneZipperProbe', str(path), str(index)]
        with (output / f'{index}.log').open('w') as log:
            process = subprocess.Popen(cmd, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
            try:
                code = process.wait(timeout=args.seconds)
            except subprocess.TimeoutExpired:
                import os
                os.killpg(process.pid, signal.SIGKILL)
                process.wait()
                code = 124
        result = dict(index=index, exitCode=code, seconds=time.monotonic() - start,
                      status='measured' if code == 0 else 'timeout' if code == 124 else 'failed')
        if code == 0:
            with path.open() as f:
                rows = list(csv.DictReader(f))
            assert len(rows) == 1
            result.update(rows[0])
        print(json.dumps(result), flush=True)
        return result

    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        results = list(pool.map(run, [17, 19, 20]))
    report = dict(attempted=3, measured=sum(r['status'] == 'measured' for r in results),
                  heap=args.heap, timeoutSeconds=args.seconds, runs=results)
    (output / 'summary.json').write_text(json.dumps(report, indent=2) + '\n')


if __name__ == '__main__':
    main()
