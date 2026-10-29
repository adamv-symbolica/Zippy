# Compiled cornerstone programs

The ready-to-read directory is [`output/cornerstone-review/`](../../output/cornerstone-review/README.md).
It contains **108 standalone C++ translation units**: symbols, Fibonacci bits,
and four-byte intern IDs for each of **22 cornerstone variants** and **14
supplementary large cases**.

Each case includes:

- `symbols.cpp`, `bits.cpp`, and `bytes.cpp`, with `main` and reachable routines;
- `*.graph.txt`, the optimized executor graphs used for native emission;
- `symbolic.zippy`, the source expression and reachable symbolic routines;
- `encodings.json`, original symbols and their intern IDs/bit/byte codewords;
- `*.input.txt` fixtures and `*.bindings.json` input-slot/label maps.

The shared delayed OpenFst runtime and CMake project are in `runtime/`.
Generated iterations, folds and fixpoints use inline loops and typed locals.
Only language routines become functions. Precomputed programs are marked in
the accompanying tables; their source/graph compilation costs remain visible.

The portable [source archive](cornerstone-review.tar.gz) retains the directory,
per-example benchmark pages, and their raw evidence in Git. Restore it with:

```sh
mkdir -p output
tar -xzf docs/compiled-cornerstones/cornerstone-review.tar.gz -C output
```

[manifest.json](manifest.json) identifies all C++ files and SHA-256 hashes;
[archive.json](archive.json) identifies the archive itself. C++ sources total
**34,188,777 bytes**. OpenFst remains an external build dependency.

All **108 files compiled and executed** on the h4d. Decoded results agree across
all three dialects for every case; the native benchmark matrix also checks all
108 case/dialect results exactly against the trie oracle. See
[export checks](../validation/openfst-graph/cornerstone-export-checks.json).

The [per-example benchmark index](../cornerstone-benchmarks/README.md) contains
**290 measured evaluator rows with no omitted runtime cells**, including the
three source `evalZ` cases omitted by the normal report. It separates runtime,
source/graph compilation and native session setup, and states each measurement
protocol. The supplementary large cases retain their original evaluator scope.
