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

The runtime vendors [PathMap PR #171](https://github.com/Adam-Vandervorst/PathMap/pull/171)
at `f3e442ed19025582aced71c598a0359b3b469e45`, with the sparse restriction fix from
[PR #172](https://github.com/Adam-Vandervorst/PathMap/pull/172), commit
`4a399a3a038bccc8aa53b9c1613f9dbf4ed94ff1`, cherry-picked onto it.
The PR branches do not contain one another. A local `structurally_equal`
certificate method compares native nodes without
constructing an algebraic result; its exact patch is recorded as
`LOCAL_STRUCTURAL_EQUALITY.patch`. The manifest omits benchmark-only targets
and dependencies. Provenance and the exact upstream patch are in
`src/main/rust/pathmap/vendor/pathmap/UPSTREAM.json` and `PR172.patch`.
Cargo.lock is committed, and cache identities and standalone review bundles
include the vendored build inputs.
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
| Restriction / raffination | Native prefix restriction; singleton raffination removes a subtree directly, otherwise native difference |
| Wrap | Shared clone plus write-zipper `insert_prefix` |
| Unwrap | Read-zipper descent plus `make_map`, sharing the residual subtree |
| Tails union / intersection | `join_k_path_into(1, true)` / `meet_k_path_into(1, true)` |
| Head | Bulk masked graft from a cached 256-byte alphabet, using the source `ByteMask` directly |
| Composition | Cached jumping catamorphism with shared suffix grafts |
| Prefix / suffix / tails closures | Iterative physical-state folds; suffix closure shares original residuals directly |
| Range | Const-generic bounds; native first/last paths and bounded edge counts; exact BigUint ranks for general signed slices |
| Iteration / fold | Generated loops over a reusable zipper and `ByteMask`; tails are borrowed read zippers and the fold accumulator is a Rust local |
| Fixpoint | In-place `join_into` with its algebraic status; exact fallback for opaque steps |
| Equality | Native structural certificate, then exact shared-state zipper comparison when layouts differ |
| Routine calls | Typed Rust functions, with lexical captures resolved at graph compilation |
| Exact cardinality | Iterative BigUint fold memoized at shared physical node boundaries, including unary stems |

Composition and closures visit shared physical subtries through cached folds.
A product with 2^80 accepted paths remains compact; counts and
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

Read-only loop tails implement the statically dispatched `ReadSpace` adapter.
The compiler selects concrete map/view kernel entry points, compiled at release
level 3 even when large generated glue uses level 0 or 1. Algebra consumes those
zippers directly where PathMap supports it; owned maps are materialized for
results and ordinary routine/callback boundaries. Native
map inputs retain PathMap's direct algebra implementations. No trait-object
dispatch or copied path list is introduced.

Small forwarding chains are collapsed, followed by one extra pass over helpers
with at most 128 source nodes and cheap actual arguments. Recursive calls stay
native, and the additional expansion budget is 8,192 nodes
(`morkl.pathmap.inlineHelperNodes` / `morkl.pathmap.inlineNodes`). This permits
helpers such as GOL's `neigh` to optimize together with their callers while
avoiding duplication of compound space arguments. Small acyclic helpers with
one or two path arguments and no space arguments are also marked
`#[inline(always)]` after graph lowering. This includes `neigh`; its generated
body can optimize together with constructed coordinate paths in callers without
expanding the source language's parser loops. Graph dumps render each nested
graph once; rendering it twice
made diagnostic work exponential in nesting depth after deeper inlining.

Modules containing opaque functions retain native call boundaries during source
lowering: call-by-name source substitution could otherwise discard an unused
strict argument. Path-loop fusion likewise keeps a call when an actual argument
can invoke an opaque function. Callback argument evaluation remains observable
even when the callee ignores that argument or its input space is empty.

Repeated ordered selections are hoisted out of a translated fold only when its
graph proves that each continuation removes exactly the selected prefix.
The codewords must be prefix-free, and a step routine that removes its current
head must pass the selected next head back to that same parameter. Fibonacci
and static codebook orderings are streamed once. A separate structural proof
recognizes the ordinary first-zero scanner for string symbols; incomplete
runtime words are ignored exactly as in the source program. No encoding
dictionary or boundary metadata is attached to input/output spaces.

Selectors never collect a `Vec<Path>`. Terminated and matching codewords use
streaming zipper traversals; Fibonacci order uses a priority frontier of packed
prefix states and a single zipper. Listed codewords are static borrowed byte
slices. Literal parsing reuses one scalar path, and protocol output streams
terminal paths. The remaining `Vec` fields are optional input slots at the
process boundary. Traversal stacks use `SmallVec` and can spill for deep paths.

Equality first tries PathMap's native structural certificate, including a
constant-time shared-root shortcut. A failed certificate is inconclusive and
falls back to an exact zipper comparison. That fallback checks terminals
separately from shared branches. Its memo keys also
identify positions inside compressed nodes, so compared tries may have
different node boundaries. PathMap's double identity mask proves equality when
present; its documented converse is false, so missing identity bits cannot be
used as an inequality test. `join_into` can conservatively report `Element`
after a representation change. Pure fixpoints can take another stabilization
step; opaque fixpoints retain an exact check to preserve callback counts.

Large straight-line graph regions become native functions with typed reference
arguments and live-value results. This bounds Rust MIR/drop elaboration and LLVM
work while preserving lexical loops and direct operations. The default region
threshold is 32,000 generated characters (`morkl.pathmap.blockBytes`). Compiler
binding maps are persistent and dependency analysis is memoized, avoiding
quadratic compiler allocation on deeply unrolled programs.

## Product projections and remaining upstream limits

`ProductZipper` retains intermediate factor values and stitches at leaves. For
example, primary paths `{[1], [1,2]}` and secondary `{[3]}` expose values at
`[1]`, `[1,2]`, and `[1,2,3]`; Zippy composition requires `[1,3]` and
`[1,2,3]`. It also cannot supply native subtries for PathMap graft/algebra
operations. A direct replacement for general composition would change accepted
paths. `examples/product_semantics.rs` reproduces these cases; the backend
retains the cached composition implementation.

Native union can repeat work on distinct shared operand pairs. The diagnostic
`examples/dag_union.rs` joins compact binary languages of lengths N and N-1;
their inputs have O(N) states, while native work grows with the logical paths.
This limits very large suffix closures. It is a performance limitation; the
independent model checks exact closure results on bounded inputs. No additional
PathMap correctness defect was found by the Zippy model, the 1,060 upstream unit
tests, or PR #172's six upstream differential tests. One pre-existing upstream
unit test is ignored.

The compiler preserves observable path order. In particular, the current SCC
routine emits pivot/member pairs, so changing its pivot can change returned
paths even when the component partition is the same. Native first/last
selection is used where it preserves the language's actual order.

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
2,000,000 characters. These sizes exclude concrete kernel namespace qualifications;
the manifest records the authoritative level. Large unrolled scanner functions
otherwise spend excessive time in LLVM. Benchmark CSVs record the actual `rust_opt_level`; thresholds can
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
workload families with three byte encodings, and 180 native microbenchmarks,
including paired equality measurements.
Parallel runs use an immutable benchmark JAR. Use `--workers 1` for isolated
timings, or `--workers 8 --shards 4` for the full h4d throughput run.

`exportCornerstoneRust` exports 108 complete programs for the 36 cornerstone
and supplementary cases. `tools/check_cornerstone_rust.py` builds each source
and compares decoded output exactly against its independent Scala trie result.

The [validation evidence](validation/pathmap-zipper/README.md),
[per-example evaluator results](pathmap-benchmarks/README.md), and
[standalone Rust review bundle](compiled-cornerstones-rust/README.md) record the
completed h4d run and its exact source hashes.
