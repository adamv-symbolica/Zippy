# C++ / OpenFst backend

`OpenFstBackend.symbols`, `.bits`, and `.bytes` transpile the corresponding
Zippy program and routine module into C++17 through the same optimized
`RecursiveOpGraph` used by `exec`/`execT`. The emitter consumes graph coordinates,
not expression-tree nodes. Generated code executes against delayed OpenFst acceptors. There is no AST interpreter or reference-evaluator fallback
in the executable. All five path cases and all 24 space cases are supported.

```scala
val native = OpenFstBackend.symbols(expression, routines)
val result: SpaceValue = SpaceValue(native.eval(
  paths = Map(pathRef -> pathValue),
  spaces = Map(inputMention -> inputValue.paths)))

val bits = OpenFstBackend.bits(bitProgram.entry, bitProgram.routines)
val bitResult: Set[BigInt] = bits.eval()
val bytes = OpenFstBackend.bytes(byteProgram.entry, byteProgram.routines)
val byteResult: Set[BytePathValue] = bytes.eval()
```

Definitions are supplied as a `Map[RoutinePtr, Routine]` for that dialect.
`OpenFstBackend.reachable(expression, context)` collects a symbolic module from
a partial-function routine context, including calls inside arguments. Routine
parameters, lexical shadowing, ignored `_` binders, mutual recursion, and the
reference evaluator's stationary positive self-call convention are preserved.

For repeated execution, reuse a native process:

```scala
val compiled = Vector(OpenFstBackend.bits(bitProgram.entry, bitProgram.routines))
val session = OpenFstBackend.Session(compiled)
try
  val result = session.eval(0, compiled(0))(pathInputs, spaceInputs)
  val timing = session.benchmark(0, compiled(0), repeats = 10)(pathInputs, spaceInputs)
  println(timing.nanos)   // native execution only, per invocation
  println(timing.paths)   // exact BigInt cardinality, without enumerating paths
finally session.close()
```

Native timings exclude compilation, initial input-acceptor construction,
request/result protocol IO, and result enumeration. A grounded host callback
inside execution includes its host work and bridge IO in the measured interval.
Final automaton expansion and exact DAG cardinality analysis occur **inside**
every timed invocation; deferring an operation does not remove its consumption
cost from the measurement. Constants and symbol-table initialization
are warmed before timing. The compiled program and shared runtime are cached
under `target/openfst`; nonblocking filesystem locks protect concurrent JVM builds. Audit batches are
bounded by emitted source size as well as case count.

## Representation and operations

A space is an immutable language view over an OpenFst `Fst<StdArc>` and a
root-state cursor. Literals use `VectorFst`; composed operators use the private
`CachedFst` implementation of OpenFst's interface. Its deterministic,
epsilon-free transitions and final states are computed once per state on demand.
An operator records its input cursors without traversing or copying their graphs.
Final states represent terminal paths, including epsilon at the initial state.
Common suffix states form a DAG, permitting compact exponential languages.
No encoding-boundary metadata or new Zippy language cases are introduced.
Graph path constants use lossless UTF-16 serialization when symbol boundaries
would otherwise be ambiguous, including dotted and empty symbols.

Language labels are positive OpenFst labels. Bit `0` and `1` become labels `1`
and `2`; byte `0..255` becomes `1..256`. Symbol labels use a per-program interner.
All returned automata are epsilon free. Symbol ordering uses the original UTF-16
code units, including NULs, empty symbols, and unpaired surrogates; it does not
depend on numeric interning order. Short paths use an inline four-label buffer.

| Zippy operation | Native implementation |
|---|---|
| Empty, literal, singleton | Direct acyclic acceptors with shared suffix states |
| Union | Delayed n-ary deterministic union; nested unexpanded unions fuse when consumed |
| Intersection, subtraction | Delayed state-pair products |
| Restriction, raffination | Direct delayed prefix-state products; singleton prefix exclusion uses a persistent boundary edit |
| Composition | Delayed deterministic concatenation subsets; no intermediate epsilon automaton |
| Wrap, unwrap | Delayed prefix automaton and shared root cursors; completed wrap/unwrap prefixes cancel |
| Iteration | Inline C++ `for` loop over productive native branches; bit loops have exactly two candidate labels |
| Fold | Inline ordered loop, native path accumulator, direct body/update locals; graph framing tags are removed at compilation |
| Fixpoint | Inline loop with lazy union and language equality via symmetric difference |
| Head | Native two-state projection; bit/byte masks bound the alphabet and cache small results |
| Tails union/intersection | Shared child cursors and delayed union/product automata |
| Prefix closure | Delayed final-state view |
| Suffix/tails closure | Delayed deterministic union of reachable residuals, optionally excluding epsilon |
| Range | Delayed rank slices; positive bounds use saturated counts, negative bounds use exact arbitrary-precision DAG counts |
| Calls and bindings | Typed C++ routine calls, graph locals and lexical loop binders |
| Grounded operations | Plain native function pointers when supplied; otherwise the explicit host callback protocol |

`OpenFstBackend.fromGraph(graph, callees)` accepts an already optimized symbolic
executor graph and its called-graph index directly. The dialect entry points run
source optimization, existing graph lowering, hoisting, push-out and sharing,
then use the same emitter. Lexical binders are alpha-renamed before inlining
and source specialization, including unused replacements for ignored `_`
binders; external captures remain free. Exact and maximum path-length hints
survive renaming. Opaque host closures are never evaluated by compilation.
Proven encoding selectors survive source rewriting through ordinary function
abstraction before native graph lowering.

Every live graph coordinate becomes a typed C++ local. There is no node dispatch,
value stack, type-erased loop callback or environment copy in generated loops.
Only actual language routines become generated functions, eligible for ordinary
C++ inlining. Input maps are consulted once by the protocol entry wrapper;
unused captures are not forced. Potentially recursive or opaque computations
stay within the composition guards that demand them. Shared computations use
existing dominating locals or are emitted inside their guarded use sites.

Large literal path sets and symbol tables use compact numeric text constants,
parsed once. Only routines reachable after graph optimization are emitted.
Cold generated units above two million characters default to `-O0`; set
`morkl.openfst.largeOptimize` to override that policy. Normal units use `-O2`,
and the automaton runtime always uses Release optimization. No forced no-inline
attribute or compiler flag is applied. Source normalization has its existing
30-second budget; if it expires, structural lowering still proceeds through the
optimized graph. `morkl.openfst.graphCompileMillis` bounds native graph
optimization separately (default 300,000 ms). Graph sharing indexes canonical
nodes instead of repeatedly scanning the graph.

The caches hold automaton transitions, productivity and cardinalities, not
symbol encodings or path boundaries. Consumers traverse the cached DAG without
creating a `VectorFst` snapshot. Operator-chain destruction uses an iterative
queue, avoiding stack growth for long persistent union chains. Singleton prefix
exclusion copies only states along the changed prefix and shares the remaining
child cursors. Cursor normalization bypasses these edit views, so successive
exclusions do not create a chain of product machines. Literal epsilon
concatenations cancel without probing a delayed operand.

Ordered codebook decision trees are recognized directly from the existing AST.
A conservative grammar check and abstract scanner execution prove when head
materialization can become prefix lookup on the input acceptor. Contiguous
Fibonacci IDs use bounded best-first search over acceptor states and code
positions, with a consistent lower bound on the remaining ordinal cost.
Lexicographically ordered codes use graph-product traversal; other
orders use an indexed OpenFst control literal with priorities derived from
the existing constant list. The value-keyed control cache never adds fields to
a space or stores encoding boundaries. Unsupported shapes compile
through the ordinary operations. The generated native constants are derived
from language expressions, with no extra language cases or path metadata.

Pure nonempty guards over a bound space lower to a direct emptiness test.
A nonempty guard over its tails union lowers to a native head-presence check,
avoiding construction of residual unions for a Boolean observation.

The encoding compiler also shares the source and union of tails for encoded
`TailsIntersection` through ordinary routine parameters. This removes repeated
source evaluation and scanner reconstruction inside each branch while keeping
the translation within the existing language.

Generated recursive functions use the native stack. On Unix, the executable
raises its soft stack limit toward 256 MiB, bounded by the process hard limit.
`ZIPPY_STACK_MIB` changes that budget. The suite includes 8,192-deep recursion
and concatenation, plus range selection from a compact language of `2^80` paths.

## Grounded functions

Arbitrary Scala closures cannot be serialized as C++. By default, the native
process sends the grounded operation's concrete argument to its owning JVM and
receives the resulting path or acceptor. All surrounding Zippy operations still
execute in C++. Callback-created symbols extend the interner through the same
protocol, preserving ordering.

A callback-free program has `isStandalone == true`. To make opaque operations
standalone too, supply C++ bodies for their emitted callback IDs:

```scala
val p = OpenFstBackend.symbols(expression, routines)
println(p.callbackCount) // IDs also appear in p.source
val native = p.withNativeFunctions(Map(
  0 -> "return tails_union(argument);"))
```

Each body receives `Context& c` and `const Space& argument`, and returns `Space`.
Path callbacks receive a singleton acceptor and must return a singleton.
`labels.encode` gives the native labels for a constant path when a custom body
needs one. Unbound grounded IDs continue to use the host bridge. No extra
operation is added to any dialect.

## Build and export

Dependencies: JDK 17+, Scala CLI, CMake 3.22+, a C++17 compiler, Boost headers, and OpenFst. The
validated upstream revision is
[`5c887c63a882d608ec3010dba60ab25d57684b02`](https://github.com/google-research/openfst/tree/5c887c63a882d608ec3010dba60ab25d57684b02),
with Abseil `20260817.0`. Build them into a chosen prefix:

```bash
bash tools/build_openfst.sh /tmp/openfst-install
export CMAKE_PREFIX_PATH=/tmp/openfst-install
export LD_LIBRARY_PATH=/tmp/openfst-install/lib:${LD_LIBRARY_PATH:-}
```

CMake's imported OpenFst target carries the exact library and Abseil dependency
closure. The legacy `fst/fstlib.h` installation layout is also supported when
an upstream CMake package is absent. Do not mix headers and libraries from
different OpenFst versions.

JVM properties: `morkl.openfst.root`, `.cache`, `.prefix`, `.cxx`, `.cxxflags`,
`.ldflags`, `.compileSeconds`, `.largeOptimize`, and `.optimize` configure the project root, cache, dependency prefix,
compiler, extra flags, compiler timeout (default 1,800 seconds), and
generated-code optimization level (default `-O2`). Very large expanded programs
can still produce substantial source and compilation costs; benchmark CSVs
record source/graph compilation (`graph_compile_ms`), live graph nodes, generated
source size, precomputed-literal status, and native session setup separately.
The runtime is built as a shared C++ library through CMake.

```scala
native.writeSource(java.nio.file.Paths.get("/tmp/program.cpp"))
```

```bash
cmake -S src/main/cpp -B /tmp/zippy-cpp \
  -DCMAKE_BUILD_TYPE=Release -DZIPPY_PROGRAM=/tmp/program.cpp
cmake --build /tmp/zippy-cpp -j 8
printf 'R 0 0 0 0\nQ\n' | /tmp/zippy-cpp/zippy_program
```

This example runs a closed program with no input bindings. The numeric protocol
is documented by `read_env`, `read_space`, and `run` in the runtime. The Scala
`Session` supplies it for open programs and services host callbacks.

## Verification and benchmarks

```bash
python3 tools/test_openfst.py --workers 48 --heap 256g --output /tmp/openfst-tests
python3 tools/benchmark_openfst.py --output /tmp/openfst-benchmarks
cmake -S src/main/cpp -B /tmp/openfst-native -DCMAKE_BUILD_TYPE=Release \
  -DZIPPY_BUILD_BENCHMARKS=ON
cmake --build /tmp/openfst-native -j 8
ctest --test-dir /tmp/openfst-native --output-on-failure
/tmp/openfst-native/zippy_openfst_benchmark
```

The test runner executes the complete Scala suite, then replays every distinct
successful symbolic expression/input frame through the native symbolic backend
and all seven translation directions. Direct bit/byte reference executions are
also captured. Source values are used only as the differential oracle; C++
computes the result. Checks run outside cost-counting and timed scopes, with a
per-case ledger and a summary that rejects missing, failed, or unfinished runs.

The native benchmark matrix covers the existing program workloads, large
queries and selective products, the full three-scenario algebra matrix,
iteration/range microbenchmarks, construction, incremental union, scaling, independent products, and cost-model workloads in all three dialects. Results up to 250,000 paths are
compared exactly with the trie evaluator; larger results are checked by exact
cardinality. Separate C++ microbenchmarks measure actual acceptor construction
and incremental union. The runner also executes the 14 existing benchmark and analysis report entry points listed
in `tools/benchmark_openfst.py`. It compiles a private Scala snapshot once and
launches benchmarks from an immutable JAR, avoiding concurrent class-directory
rewrites. The benchmark runner sets `ZIPPY_STACK_MIB=1024` for its deepest
translated folds. Use one benchmark worker and an otherwise idle node for
isolated timings. The benchmark runner sets `ZIPPY_BENCH_WARMUPS=0`: its
correctness invocation warms exact rows, and it explicitly warms rows checked
only by cardinality. Ordinary `Session.benchmark` keeps two internal warmups
unless this environment variable is set.

For throughput on a large node, `--shards 6 --workers 6`
splits each native family into independent JVMs and merges the checked rows
without duplication. `--only algebra` limits this to the algebra matrix.

See [validation results](OPENFST_TEST_REPORT.md) and
[benchmark measurements](OPENFST_BENCHMARKS.md) for the complete h4d run,
raw CSVs, scope, and timing methodology.
