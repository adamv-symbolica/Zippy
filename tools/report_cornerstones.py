#!/usr/bin/env python3
"""Join retained benchmark evidence with the generated C++ review bundle.

This does not run benchmarks. Missing cases, duplicate rows, differing result
cardinalities, and damaged generated sources are errors, rather than blank cells.
"""

import argparse
import csv
import hashlib
import json
import os
import shutil
from collections import Counter, defaultdict
from pathlib import Path


GROUPS = {
    "aunt": "Aunt queries",
    "graph": "Graph queries",
    "scc": "Strongly connected components",
    "datalog": "Semi-naive Datalog",
    "life": "Game of Life",
    "temperature": "Temperature",
    "sliding-puzzle": "Sliding puzzle",
    "n-queens": "N-queens",
    "large-products": "Supplement: large selective products",
}
DIALECTS = ("symbols", "bits", "bytes")
EVALUATORS = ("eval Set", "ROG exec", "evalTrie", "evalZ", "ROG execT")


def group(name):
    name = name.lower()
    if name.startswith("aunt"):
        return "aunt"
    if "two-hop" in name or name.startswith("graph"):
        return "graph"
    if name.startswith("scc"):
        return "scc"
    if "datalog" in name:
        return "datalog"
    if name.startswith("product"):
        return "large-products"
    if name in ("life", "temperature", "n-queens"):
        return name
    if name == "sliding puzzle":
        return "sliding-puzzle"
    raise ValueError(f"unclassified example: {name}")


def tables(path):
    result = []
    lines = path.read_text().splitlines()
    i = 0
    while i + 1 < len(lines):
        if not lines[i].startswith("| ") or not lines[i + 1].startswith("|---"):
            i += 1
            continue
        headers = [s.strip() for s in lines[i].strip("|").split("|")]
        i += 2
        rows = []
        while i < len(lines) and lines[i].startswith("| "):
            values = [s.strip() for s in lines[i].strip("|").split("|")]
            if len(values) != len(headers):
                raise ValueError(f"malformed table in {path}: {lines[i]}")
            rows.append(dict(zip(headers, values)))
            i += 1
        result.append(rows)
    return result


def unique(rows, key):
    result = {}
    for row in rows:
        k = key(row)
        if k in result:
            raise ValueError(f"duplicate case: {k}")
        result[k] = row
    return result


def md_table(headers, rows):
    def cell(value):
        return str(value).replace("|", "\\|").replace("\n", " ")
    return "\n".join("| " + " | ".join(map(cell, row)) + " |" for row in [headers, ["---"] * len(headers), *rows]) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--evidence", type=Path, default=Path("docs/validation/openfst"))
    parser.add_argument("--bundle", type=Path, default=Path("output/cornerstone-review"))
    parser.add_argument("--output", type=Path, default=Path("docs/cornerstone-benchmarks"))
    args = parser.parse_args()
    evidence, bundle = args.evidence.resolve(), args.bundle.resolve()
    manifest = json.loads((bundle / "manifest.json").read_text())
    artifacts = unique(manifest["artifacts"], lambda r: (r["family"], r["name"], r["variant"], r["dialect"]))
    assert manifest["programVariants"] == 22 and manifest["largeVariants"] == 14
    assert len(artifacts) == 108
    for artifact in artifacts.values():
        source = (bundle / artifact["source"]).read_bytes()
        assert len(source) == artifact["bytes"]
        assert hashlib.sha256(source).hexdigest() == artifact["sha256"], artifact["source"]
        assert b"int main()" in source and b"#include \"zippy_openfst.hpp\"" in source
        assert (bundle / artifact["source"]).with_suffix(".input.txt").is_file()

    runtime, compilation, independent_queens = tables(evidence / "existing-reports/trieBenchmarkReport.log")
    large, = tables(evidence / "existing-reports/zipperLargeBenchmarkReport.log")
    folds, = tables(evidence / "existing-reports/constantFoldExecutorReport.log")
    assert len(runtime) == len(compilation) == 22 and len(large) == 14 and len(folds) == 20
    comp = unique(compilation, lambda r: (r["benchmark"], r["variant"]))
    native_rows = []
    for family, count in (("programs", 66), ("large", 42)):
        with (evidence / f"{family}.csv").open(newline="") as handle:
            rows = list(csv.DictReader(handle))
        assert len(rows) == count
        native_rows.extend(rows)
    native = unique(native_rows, lambda r: (r["family"], r["name"], r["dialect"]))
    with (evidence / "cornerstone-radix/reference.csv").open(newline="") as handle:
        radix_rows = list(csv.DictReader(handle))
    radix = unique(radix_rows, lambda r: (r["name"], r["dialect"]))
    radix_summary = json.loads((evidence / "cornerstone-radix/summary.json").read_text())
    assert len(radix) == 44 and radix_summary["success"]
    cases, joined = [], []
    for family, rows in (("programs", runtime), ("large", large)):
        for row in rows:
            name = row["benchmark"]
            variant = row["variant" if family == "programs" else "size"]
            paths = int(row["result paths"].replace(",", ""))
            entry = {"family": family, "name": name, "variant": variant, "row": row, "paths": paths, "group": group(name)}
            entry["native"] = {d: native[family, f"{name}/{variant}", d] for d in DIALECTS}
            entry["artifacts"] = {d: artifacts[family, name, variant, d] for d in DIALECTS}
            literal = family == "programs" and "compiled away" in row["graph note"]
            entry["literal"] = literal
            entry["compilation"] = comp.get((name, variant))
            cases.append(entry)
            for evaluator in EVALUATORS if family == "programs" else ("evalTrie", "evalZ"):
                value = row[f"{evaluator} ms"]
                note = row.get("graph note", row.get("note", ""))
                if literal and evaluator.startswith("ROG"):
                    note = "precomputed literal; " + note
                joined.append(dict(family=family, example=entry["group"], name=name, variant=variant,
                                   evaluator=evaluator, runtime_ms="" if value == "n/a" else value,
                                   availability="omitted" if value == "n/a" else "measured", result_paths=paths,
                                   compile_session_ms=comp[name, variant]["total compile ms"] if evaluator.startswith("ROG") else "",
                                   compile_scope="prep + graph compilation" if evaluator.startswith("ROG") else "",
                                   graph_precomputed_literal=literal if evaluator.startswith("ROG") else False,
                                   source="", callbacks="", notes=note,
                                   evidence=f"existing-reports/{'trieBenchmarkReport' if family == 'programs' else 'zipperLargeBenchmarkReport'}.log"))
            if family == "programs":
                entry["radix"] = {d: radix[f"{name}/{variant}", d] for d in ("bits", "bytes")}
                for d, reference in entry["radix"].items():
                    assert int(reference["paths"]) == paths and reference["checked"] == "exact"
                    joined.append(dict(family=family, example=entry["group"], name=name, variant=variant,
                                       evaluator=f"Zippy_{d} reference", runtime_ms=reference["reference_ms"],
                                       availability="measured", result_paths=paths, compile_session_ms="", compile_scope="",
                                       graph_precomputed_literal=False, source="", callbacks="",
                                       notes=f"two warmups; {reference['samples']} timed samples; separate h4d run",
                                       evidence="cornerstone-radix/reference.csv"))
            for d in DIALECTS:
                n, a = entry["native"][d], entry["artifacts"][d]
                assert int(n["paths"]) == paths and n["checked"] == "exact", (name, variant, d)
                joined.append(dict(family=family, example=entry["group"], name=name, variant=variant,
                                   evaluator=f"C++ OpenFst {d}", runtime_ms=n["native_ms"], availability="measured",
                                   result_paths=paths, compile_session_ms=n["compile_ms"],
                                   compile_scope="native session setup, may hit compilation cache",
                                   graph_precomputed_literal=False, source=a["source"], callbacks=a["callbackCount"],
                                   notes=a["encoding"], evidence=f"{family}.csv"))
    assert len(joined) == 290 and len(cases) == 36
    assert len(native) == len(artifacts) == sum(len(c["native"]) for c in cases)
    # Ensure the reviewed compiler and case factories match the validated revision.
    environment = json.loads((evidence / "environment.json").read_text())
    root = Path(__file__).resolve().parent.parent
    for filename, digest in environment["sourceSHA256"].items():
        assert hashlib.sha256((root / filename).read_bytes()).hexdigest() == digest, filename

    def emit(output, raw_evidence):
        output.mkdir(parents=True, exist_ok=True)
        def link(path):
            return Path(os.path.relpath(path, output)).as_posix()
        def source_link(case, d):
            return f"[{d}.cpp]({link(bundle / case['artifacts'][d]['source'])})"
        grouped = defaultdict(list)
        for case in cases:
            grouped[case["group"]].append(case)
        overview = []
        for g, title in GROUPS.items():
            rows = grouped[g]
            overview.append((f"[{title}]({g}.md)", sum(c["family"] == "programs" for c in rows),
                             sum(c["family"] == "large" for c in rows), sum(len(c["native"]) for c in rows)))
            text = [f"# {title}\n", "[All examples](README.md) · Runtime measurements are **milliseconds per evaluation**.\n"]
            text.append("The five original Scala evaluator columns, two digit-set reference interpreters, and three C++ dialects are listed separately below. Large cases retain their two originally measured Scala evaluators. See the [measurement notes](README.md#measurement-notes) before comparing engines.\n")
            for case in rows:
                r, family = case["row"], case["family"]
                text += [f"## {case['name']} — {case['variant']}\n", f"Fixture family: `{family}`. Result: **{case['paths']:,} paths**.\n"]
                times = []
                for engine in EVALUATORS if family == "programs" else ("evalTrie", "evalZ"):
                    note = "precomputed literal" if case["literal"] and engine.startswith("ROG") else ""
                    if r[f"{engine} ms"] == "n/a":
                        note = "omitted by original benchmark (see note below)"
                    times.append((engine, r[f"{engine} ms"], note))
                if family == "programs":
                    for d in ("bits", "bytes"):
                        reference = case["radix"][d]
                        representation = "BigInt path sets" if d == "bits" else "byte-array path sets"
                        times.append((f"Zippy_{d} reference", reference["reference_ms"],
                                      f"{representation}; {reference['samples']} sample(s), exact check; separate h4d run"))
                for d in DIALECTS:
                    a = case["artifacts"][d]
                    callback = f"; {a['callbackCount']} host callback(s)" if a["callbackCount"] else "; standalone"
                    times.append((f"C++ OpenFst {d}", case["native"][d]["native_ms"], a["encoding"] + callback))
                text.append(md_table(("Evaluator / transpilation", "Runtime ms", "Execution"), times))
                note = r.get("graph note", r.get("note", ""))
                if note:
                    text.append(f"Original benchmark note: {note}.\n")
                if family == "programs":
                    c = case["compilation"]
                    text.append("Scala compilation costs from the same report (milliseconds):\n")
                    metrics = ("prep compile ms", "graph compile ms", "total compile ms", "compile+ROG execT ms",
                               "source pass ms", "lower+inline ms", "graph optimize ms", "graph transpile ms",
                               "const-fold eval ms", "const-fold evalTrie ms", "const-fold evalZ ms", "const-fold execT ms")
                    text.append(md_table(("Stage", "ms"), [(k.removesuffix(" ms"), c[k]) for k in metrics]))
                text.append("Native compilation/session costs and resulting acceptor sizes:\n")
                text.append(md_table(("Dialect / source", "Session setup ms¹", "States", "Arcs", "Samples", "Correctness"),
                                     [(source_link(case, d), *(case["native"][d][k] for k in ("compile_ms", "states", "arcs", "samples", "checked"))) for d in DIALECTS]))
                text.append("¹ May include a compilation cache hit; this is not a cold compilation comparison.\n")
            focused = [f for f in folds if (g == "life" and f["benchmark"].startswith("life")) or
                       (g == "sliding-puzzle" and f["benchmark"].startswith("sliding puzzle")) or
                       (g == "n-queens" and f["benchmark"].startswith("n-queens"))]
            if focused:
                text.append("## Focused constant-fold evaluator sweep\n")
                text.append("This separate compile-time experiment selects Reference, Trie, Zipper, ExecT, or Hybrid as the constant-folding engine. These are compile times, not additional runtime measurements. All durations are milliseconds.\n")
                cols = ("benchmark", "backend", "compile ms", "const-fold total ms", "eval ms", "evalTrie ms", "evalZ ms", "execT ms")
                text.append(md_table(cols, [[f[k] for k in cols] for f in focused]))
            if g == "n-queens":
                text.append("## Independent specialized bit-recursive reference\n")
                text.append("These additional scaling rows use the dedicated n-queens algorithm, not the Zippy bit-language interpreter. They are kept separate from the common-program comparison.\n")
                cols = ("benchmark", "variant", "result", "ms", "note")
                text.append(md_table(cols, [[r[k] for k in cols] for r in independent_queens]))
            (output / f"{g}.md").write_text("\n".join(text))
        intro = f"""# Cornerstone benchmarks, example by example

Recorded on **2026-10-29**, Symba **h4d xlarge**, 192 CPUs (AMD EPYC 9B45).
These tables combine the retained evaluator/C++ matrix with a new run of the two digit-set reference interpreters.
There are **22 cornerstone variants**, **14 supplementary large cases**, and **108 C++ dialect measurements**.
The primary variants include all five original Scala evaluator columns (three `evalZ` omissions are explicitly retained), plus **44 additional bit/byte set-reference measurements**.
The supplementary large suite originally measures `evalTrie` and `evalZ` only.
All 108 native results were checked exactly against the trie result.

{md_table(("Example", "Primary variants", "Large variants", "C++ rows"), overview)}

[Combined evaluator CSV](evaluators.csv) · [Scala compilation detail](scala-compilation.csv) · [Constant-fold sweep](constant-fold.csv) · [Generated C++ bundle]({link(bundle / 'README.md')}) · [Artifact manifest]({link(bundle / 'manifest.json')})

## Measurement notes

- Scala primary runtime values are averages after two warmups. The supplementary large Scala values are medians after two warmups. Native values are medians of five adaptive batches (one sample for invocations over one second), with an untimed setup invocation.
- The bit/byte set references were measured in an additional h4d run on the same date: two warmups, median of five individual evaluations (one timed evaluation if the first exceeds one second), with four isolated JVM workers, 128 GiB heap limits, and eight active processors per JVM. Translation, input encoding, and exact-result decoding/checking are outside the timed interval. These are a separate run with a different load profile, not a controlled speedup comparison with the retained matrix.
- Native times exclude compilation, initial input acceptor construction, request/result IO, and result enumeration. Opaque host callbacks include their bridge IO and JVM work. Each source artifact records its callback count; standalone rows need no callback bridge.
- `ROG exec` and `ROG execT` use the optimizing graph pipeline. Some entire programs become one-node literals, marked **precomputed literal**; the corresponding compilation cost is shown. Native C++ emits the selected source/residual expression directly. It does not implicitly apply that graph pipeline. Rows labelled `compile-pass` or `process-sc` already use residual expressions in all columns.
- Native `compile_ms` is session setup and may hit a cache. It is shown separately and is not a uniformly cold compiler benchmark. The single cold `reference_trie_ms` correctness timing is deliberately excluded from these comparison tables.
- The bit transpilation and reference use Fibonacci-coded intern IDs. The byte transpilation and reference use four-byte big-endian intern IDs. Zero-terminated strings and the other injection routes were correctness-tested separately; no per-cornerstone timing for those routes was recorded. The supplementary large suite retains its original evaluator coverage; the 44 additional reference timings cover the 22 cornerstone variants.
- The retained evaluator/C++ matrix came from separate report processes on the original node, with 16 benchmark workers and the differential audit active. Those values are measurements under shared load, not isolated latency or a controlled speedup study. The additional reference run used a fresh h4d node with four workers. No cross-protocol speedup is inferred.
- The royal92 case uses its explicitly labelled fallback; NOAA uses the committed data slice. `evalZ` is omitted for the 3x3 and 4x4 source puzzle searches and the source 8-queens search because of the original benchmark's memory limits.
- Generated source and input fixtures were exported later from the validated compiler and case factories. Fresh internal routine names can differ from the node's cached source. Export hashes identify the exact review files; these are not claimed to be byte-for-byte copies of the now-terminated node's compiler cache.

Raw evidence: [primary Scala report]({link(raw_evidence / 'existing-reports/trieBenchmarkReport.log')}), [large Scala report]({link(raw_evidence / 'existing-reports/zipperLargeBenchmarkReport.log')}), [primary native CSV]({link(raw_evidence / 'programs.csv')}), [large native CSV]({link(raw_evidence / 'large.csv')}), [bit/byte reference CSV]({link(raw_evidence / 'cornerstone-radix/reference.csv')}), [additional run environment and results]({link(raw_evidence / 'cornerstone-radix/summary.json')}), [environment and validated source hashes]({link(raw_evidence / 'environment.json')}).

## Reproduce this review bundle

From the repository root, with a new output directory:

```sh
scala-cli run src/main/scala src/test/scala --test --server=false \\
  --scala 3.8.1 --source 3.3 \\
  --dependency org.scalameta::munit:1.2.1 \\
  --dependency org.scala-lang.modules::scala-collection-contrib:0.3.0 \\
  --java-opt -Xmx12g --java-opt -Xss64m \\
  --main-class morkl.exportCornerstoneCpp -- output/cornerstone-review true
python3 tools/report_cornerstones.py
```

The source exporter emits C++ without invoking the C++ compiler. The report join checks all 36 case names, all 108 dialect results, all 44 digit-reference results, result cardinalities, artifact SHA-256 hashes, and the validated source hashes. The generated directory is under the repository's existing ignored `output/` tree; the exporter and per-example benchmark reports are committed. A portable source archive is retained in `docs/compiled-cornerstones/`.

To rerun the additional digit references on an h4d node, package a portable assembly and copy it, `tools/benchmark_cornerstone_radix.py`, and `src/test/resources/noaa_slice.txt` to the same relative paths on that node:

```sh
scala-cli --power package src/main/scala src/test/scala --server=false \\
  --scala 3.8.1 --source 3.3 \\
  --dependency org.scalameta::munit:1.2.1 \\
  --dependency org.scala-lang.modules::scala-collection-contrib:0.3.0 \\
  --assembly --main-class morkl.cornerstoneRadixReference -o reference.jar
python3 tools/benchmark_cornerstone_radix.py --jar reference.jar \\
  --output reference-results --workers 4 --seconds 600
```
"""
        (output / "README.md").write_text(intro)
        for name, rows in (("evaluators.csv", joined), ("scala-compilation.csv", compilation), ("constant-fold.csv", folds), ("independent-n-queens.csv", independent_queens)):
            with (output / name).open("w", newline="") as handle:
                writer = csv.DictWriter(handle, fieldnames=list(rows[0]), lineterminator="\n")
                writer.writeheader()
                writer.writerows(rows)
        check = {"primaryVariants": 22, "largeVariants": 14, "cppSources": 108,
                 "evaluatorRows": len(joined), "omittedEvaluatorRows": sum(r["availability"] == "omitted" for r in joined),
                 "exactNativeResults": len(native), "sourceHashesVerified": len(environment["sourceSHA256"]),
                 "exactRadixReferenceResults": len(radix),
                 "sourceBytes": sum(a["bytes"] for a in artifacts.values()),
                 "hostCallbackArtifacts": sum(a["callbackCount"] > 0 for a in artifacts.values())}
        (output / "coverage.json").write_text(json.dumps(check, indent=2) + "\n")
    bundle_evidence = bundle / "evidence"
    for filename in ("existing-reports/trieBenchmarkReport.log", "existing-reports/zipperLargeBenchmarkReport.log",
                     "existing-reports/constantFoldExecutorReport.log", "programs.csv", "large.csv", "environment.json"):
        destination = bundle_evidence / filename
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(evidence / filename, destination)
    shutil.copytree(evidence / "cornerstone-radix", bundle_evidence / "cornerstone-radix", dirs_exist_ok=True)
    emit(args.output.resolve(), evidence)
    emit(bundle / "benchmarks", bundle_evidence)
    print(json.dumps({"cases": len(cases), "cppSources": len(artifacts), "evaluatorRows": len(joined), "pages": len(GROUPS)}))


if __name__ == "__main__":
    main()
