# Standalone Rust cornerstone programs

The review directory is [`output/cornerstone-rust-review/`](../../output/cornerstone-rust-review/README.md).
It contains **108 complete Rust programs** for **36 cases**: 22 cornerstone
variants and 14 supplementary large cases, each compiled with interned bytes,
zero-terminated string bytes, and Fibonacci bits injected into bytes.

Each case includes its symbolic source, three `.rs` translation units, optimized
executor graph dumps, exact wire inputs, codeword dictionary, and an independent
Scala trie result in `expected.json`. Generated loops contain typed locals and
direct PathMap operations. The dictionary is for inspection and checking;
runtime spaces carry byte paths and unit terminals.

The pinned PathMap runtime, Cargo.lock and native CPU configuration are included
in `runtime/`. See the expanded directory's README for compilation commands, or
run `tools/check_cornerstone_rust.py` to build and verify every program.

All **108 sources compiled and executed**, with decoded output matching the
independent Scala trie exactly. See [export checks](../validation/pathmap/cornerstone-export-checks.json).
The [per-example tables](../pathmap-benchmarks/README.md) retain all evaluator
and native encoding timings, including compilation and constant-folding notes.

The [portable source archive](cornerstone-rust-review.tar.gz) is committed so the
directory can be restored on a fresh checkout:

```sh
mkdir -p output
tar -xzf docs/compiled-cornerstones-rust/cornerstone-rust-review.tar.gz -C output
```

[manifest.json](manifest.json) records every source hash, generated-code
optimization level and graph size. [archive.json](archive.json) records the
archive checksum and source totals.
