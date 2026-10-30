# Rust / PathMap byte backend

`RustPathMapBackend.bytes` compiles Zippy_bytes through the optimized executor
graph used by the native backends. The emitted Rust contains typed locals,
lexical iteration/fold/fixpoint loops, and native functions for residual routines.
There is no expression interpreter, graph dispatcher, value stack, or copied
environment in the executable. All five path cases and all 24 space cases are
supported. Existing translations make symbolic and bit programs available to
the same byte backend.

```scala
val compiled = RustPathMapBackend.bytes(program.entry, program.routines)
val result: Set[BytePathValue] = compiled.eval(pathInputs, spaceInputs)

// A symbolic program with a fixed four-byte encoding of each interned symbol:
val lowered = RadixTranslations.internedSymbolsToBytes(expression, routines, interner)
val native = RustPathMapBackend.bytes(lowered.program.entry, lowered.program.routines)
val encodedResult = native.eval(
  paths = pathInputs.view.mapValues(lowered.encoding.encode).toMap,
  spaces = spaceInputs.view.mapValues(v => lowered.encode(v).paths).toMap)
val decodedResult = SpaceValue(encodedResult.map(lowered.encoding.decode))
```

`RustPathMapBackend.fromGraph(graph, callees)` consumes an already optimized
`RecursiveOpGraph` directly. Constants use the shared byte graph alphabet
`#digit:000` through `#digit:255`. The graph dump, live node count, generated
source, and compiler time are available on the compiled program.

## Dependency and representation

The runtime pins [PathMap PR #171](https://github.com/Adam-Vandervorst/PathMap/pull/171)
at `f3e442ed19025582aced71c598a0359b3b469e45`, with Cargo.lock committed.
It includes the focus-value consistency work from
[PR #163](https://github.com/Adam-Vandervorst/PathMap/pull/163), the pruned model
and Zippy laws, and `meet_k_path_into` for tails intersection. Focus terminals
must participate in graft, prefix insertion, union, restriction, and tails
operations for epsilon paths to have the required semantics.

Every space is a `PathMap<()>`; each accepted path has one unit terminal.
Actual labels are bytes 0..255, including zero. Paths use an inline eight-byte
`SmallVec`. No symbol boundaries, encoding dictionaries, lengths, or auxiliary
language values are attached to spaces. The numeric process protocol reuses
the existing native session transport and represents byte b as b+1; this offset
is removed before PathMap construction.

| Operation | Implementation |
|---|---|
| Union / intersection / subtraction | PathMap `join` / `meet` / `subtract` |
| Restriction / raffination | Native prefix restriction, and native difference from that restriction |
| Wrap | Shared clone plus write-zipper `insert_prefix` |
| Unwrap | Read-zipper descent plus `make_map`, sharing the residual subtree |
| Tails union / intersection | `join_k_path_into(1, true)` / `meet_k_path_into(1, true)` |
| Head | Four-word `ByteMask` enumeration, at most 256 singleton paths |
| Composition | Cached jumping catamorphism with shared suffix grafts |
| Prefix / suffix / tails closures | Cached catamorphisms, preserving physical subtree sharing |
| Range | Exact BigUint cardinalities, boundary selection, whole-subtree grafts |
| Iteration / fold | Generated loops over a reusable zipper and `ByteMask`; fold accumulator is a Rust local |
| Fixpoint | Generated inflationary union/equality loop using native algebra |
| Routine calls | Typed Rust functions, with lexical captures resolved at graph compilation |

Composition and closures visit shared physical subtries through PathMap's
cached folds. A product with 2^80 accepted paths remains compact; counts and
negative range bounds are exact above the machine integer limit. The runtime
avoids `graft_child_maps`, whose pruned-model notes document dangling-root
corner cases, and grafts known productive child maps individually.

The compiler preserves composition guards around recursive calls, opaque
grounded operations, and partial reads of optional entry inputs. Shared values
are generated in a dominating lexical scope, or at guarded use sites. It drops
owned intermediates after their final graph use, reducing retained reference
counts and improving PathMap copy-on-write behavior. Iteration cursors are
released at loop exit. Ordinary path-binding recursors are recognized from their
function bodies and fused into loops over terminal zipper positions, preserving
epsilon, shared prefixes, strict arguments and callback order. A proven single
tail continuation through union and single-path loops becomes a native loop
with typed mutable parameters. Translated ordered folds therefore do not grow
the machine stack per symbol; no continuation interpreter is introduced.
Proven ordered head scanners use joint child-mask search
or a bounded ordinal Fibonacci search; these are optimizations of existing
operations and functions, not new language cases.

## Grounded operations

Opaque Scala functions cross the existing explicit callback protocol. All
surrounding algebra executes in Rust. To obtain a standalone executable, supply
Rust implementations by callback ID:

```scala
val standalone = compiled.withNativeFunctions(Map(
  0 -> "let mut p=singleton_path(argument); p.reverse(); singleton(&p)"))
assert(standalone.isStandalone)
```

Bodies receive `c: &mut Context` and `argument: &Space`, and return `Space`.
Path functions use singleton arguments and results. IDs appear as `grounded_N`
functions in the generated source. Programs without grounded operations are
standalone automatically.

## Build, execution, and measurements

Use Rust 1.88 or later, Cargo, and a C/C++ build toolchain. Build the crate from
its directory so `.cargo/config.toml` selects the host CPU features required by
PathMap's hash implementation:

```sh
cd src/main/rust/pathmap
cargo test --release --locked
cargo clippy --all-targets -- -D warnings
cargo run --release --locked --example benchmark
```

The Scala backend sets the same Rust flags explicitly. Its content-addressed
cache under `target/pathmap` includes runtime sources, lockfile, rustc version,
CPU flags, emitted source, and compilation settings. Filesystem locks protect
builds shared by concurrent JVMs. No OpenFst installation is needed for Rust.

The runtime always uses release optimization level 3. Generated glue defaults
to level 3 up to 500,000 characters, level 1 above that, and level 0 above
2,000,000 characters. Large unrolled scanner functions otherwise spend excessive
time in LLVM. Benchmark CSVs record the actual `rust_opt_level`; thresholds can
be overridden through `morkl.pathmap.mediumOptimize` and `.largeOptimize`.
`morkl.pathmap.optimize` caps the requested level for all sizes.

Expanded scanner glue can have large native stack frames. Each process creates
one executor thread before handling requests, with a 256 MiB reserved stack
(`ZIPPY_STACK_MIB` overrides it; the benchmark runner selects 1024 MiB). There
is no per-evaluation thread creation, and this initialization is outside timing.

Reuse a process for repeated execution:

```scala
val session = RustPathMapBackend.Session(Vector(compiled))
try
  val value = session.eval(0, compiled)(pathInputs, spaceInputs)
  val stat = session.benchmark(0, compiled, 10)(pathInputs, spaceInputs)
finally session.close()
```

Timings include graph execution and exact result cardinality on every
invocation. Compilation, initial input construction, protocol IO, and result
enumeration are outside the timed region; an invoked Scala callback includes
its bridge IO and host work. Source/graph compiler time and native session setup
are reported separately. Precomputed constant results are marked explicitly.

From the repository root:

```sh
python3 tools/test_pathmap.py --output output/pathmap-tests --workers 32 --batch 128
python3 tools/benchmark_pathmap.py --output output/pathmap-benchmarks --workers 1
```

The test runner executes every Scala suite, captures successful symbolic
executions and direct digit executions, and replays them through compiled Rust.
Each symbolic frame is checked with string bytes, fixed-width interned bytes,
and Fibonacci bits injected into bytes. Direct bit tests are injected into bytes;
direct byte tests compile directly. Missing, failed, or incomplete comparisons
fail the runner. Independent Rust tests compare the native primitives against
ordered path sets, including epsilon and byte extremes, and test compact
exponential products and scanner selection.

The benchmark runner covers every existing benchmark main, all nine native
workload families with three byte encodings, and 132 native microbenchmarks.
Parallel runs use an immutable benchmark JAR. Use `--workers 1` for isolated
timings, or `--workers 8 --shards 4` for the full h4d throughput run.

`exportCornerstoneRust` exports 108 complete programs for the 36 cornerstone
and supplementary cases. `tools/check_cornerstone_rust.py` builds each source
and compares decoded output exactly against its independent Scala trie result.
