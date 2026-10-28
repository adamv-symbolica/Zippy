# Full-suite translation validation

Validated on 28 October 2026 using an ephemeral Symba `cpu-h4d-xlarge` node
(192 CPUs, 1488 GiB RAM), with 48 audit workers and a 256 GiB JVM heap.

- **311 Scala tests passed**, including the optional valued-map suite.
- **216,076 translation comparisons passed**: 30,868 distinct expression/input
  frames through each of the seven requested translation directions.
- The frames came from **192,770 successful reference evaluations** throughout
  the test suite, including optimizer checks and fuzzer generation/verification.
- **Zero** comparison failures, source evaluation failures, or unfinished frames.
- **8 Python tooling tests passed**.
- All three root egglog models passed: `formal.egg`, `zipper.egg`, and
  `zipper-descend.egg`. The Scala suite also ran the generated zipper egg witnesses.

Three tests retain their pre-existing ignored status: `Unification.double
transform`, `UnionFind.find`, and `Imperative.push out`. No active tests were
filtered out. Native data-structure and proof-format tests run normally;
translation comparisons apply to successfully reference-evaluated source
expression/input frames. The test snapshot uses the repository fixtures and
built-in fallbacks for optional external datasets.

## Reproduce

```bash
python3 tools/test_radix_translations.py \
  --workers 48 --heap 256g --output /tmp/zippy-radix-full-suite
python3 -m unittest discover -s tools -p 'test_*.py'
egglog formal.egg
egglog zipper.egg
egglog zipper-descend.egg
```

The audit captures actual input bindings, intermediate grounded symbols, and
reachable routine definitions, including calls nested in arguments. It replays
outside test timing and cost-counter scopes. The runner rejects missing reports,
failed comparisons, incomplete runs, and ordinary suite failures.

The run used Scala 3.8.1 with source mode 3.3, Scala CLI 1.18.0, Java 17.0.21,
munit 1.2.1, scala-collection-contrib 0.3.0, egglog 2.0.0, and Z3 4.8.12.

## Issues fixed during validation

- Empty/epsilon-only Fibonacci and zero-terminated iteration no longer eagerly
  demands captures belonging to a body that never runs.
- Path evaluation and recursive target routines now use heap continuations. A
  regression evaluates 8,192-deep concatenations and recursive calls on a
  256 KiB JVM stack; the full 256-byte fold also passes.
- Ordered head selection shares its computed head set. Last-path selection
  tests the local frontier before descending, avoiding exponential recomputation.
  Range bounds share one evaluated source argument through an ordinary routine.
  A grounded-source counter regression verifies that ranges evaluate it once.
- Audit collection retains supplied free inputs even in unexecuted branches and
  follows callees nested in argument expressions.

These changes use the existing language cases and function abstraction.

The machine-readable [summary](validation/radix-full-suite.json) records every
translation direction and source-suite case counts. The complete downloaded
[test log](/private/tmp/zippy-radix-full-final/tests.log) and
[comparison ledger](/private/tmp/zippy-radix-full-final/audit/checks.jsonl)
are retained locally. The evidence archive SHA-256 is
`2a0451becc570ec41ac3328146b0b09d05a9a6c53b4a566d8849f94ba174a4b6`.
