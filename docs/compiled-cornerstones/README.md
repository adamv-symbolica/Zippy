# Compiled cornerstone programs

The ready-to-read directory is [`output/cornerstone-review/`](../../output/cornerstone-review/README.md).
It contains **108 complete C++ translation units**: symbols, Fibonacci bits,
and four-byte intern IDs for each of **22 cornerstone variants** and **14
supplementary large cases**. Every exported program is standalone.

Each case directory includes:

- `symbols.cpp`, `bits.cpp`, and `bytes.cpp`, including `main` and reachable functions;
- `symbolic.zippy`, showing the input expression and reachable symbolic routines;
- `encodings.json`, mapping original symbols to intern IDs and bit/byte codewords;
- `*.input.txt` protocol fixtures and `*.bindings.json` environment/label maps.

The shared C++ runtime and CMake project are under `runtime/`. OpenFst remains
an external build dependency. Build instructions are in the bundle README.

The portable [source archive](cornerstone-review.tar.gz) retains the entire
directory, per-example benchmark pages, and their raw evidence in Git. Restore
it from the repository root with:

```sh
mkdir -p output
tar -xzf docs/compiled-cornerstones/cornerstone-review.tar.gz -C output
```

[manifest.json](manifest.json) identifies every C++ file and its SHA-256;
[archive.json](archive.json) identifies the archive itself. The C++ source
totals 53,352,997 bytes. These are regenerated review sources from the validated
compiler and case factories, rather than copies of the terminated node's cache.

All **108 files passed C++17 syntax checks**, and all **108 input fixtures**
were decoded and checked for matching symbolic inputs across the **36 cases**.
See [export checks](../validation/openfst/cornerstone-export-checks.json).
The retained native benchmark matrix checks every exported case/dialect's
result exactly. An additional h4d run checks and times the **44 bit/byte set
reference evaluations** for the cornerstone cases.

The [per-example benchmark index](../cornerstone-benchmarks/README.md) shows
all evaluator/transpilation measurements, compile costs, and the original
evaluator omissions. Timings from different measurement protocols are clearly
labelled.
