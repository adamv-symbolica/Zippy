# Trie Runtime For MORKL Spaces

`SpaceValue(Set[PathValue])` remains the reference semantics. It is simple,
generic, and excellent for tests, but most MORKL operators are naturally trie
operators. The optimized runtime in `src/main/scala/TrieSpace.scala` adds an
ordered immutable trie keyed by `PathItem`, conversions to and from
`SpaceValue`, a zipper-like cursor, and a direct `evalTrie` evaluator.

## Ordering

`PathItemOrder` gives every `PathItem` a deterministic order.  `PathItem` is
now an opaque alias over `String`, so the order is the unit path-set order:

1. compare item strings lexicographically;
2. compare paths item-by-item;
3. put a strict prefix before its extension.

This keeps trie traversal deterministic and makes ordered `Range` agree with
the reference evaluator's sorted path order.

## Native Operations

The runtime implements the native space operations directly on the trie:

- `union` / `joinAll`;
- `intersect` / `meetAll`;
- `diff`;
- `restrictBy`;
- `raffinate`;
- `concat`;
- `wrap` / `unwrap`;
- `tailsUnion`;
- `tailsIntersection`;
- ordered `Range` / first / last slices;
- first-symbol `Iteration`;
- ordered `Fold`.

Grounded host callbacks (`GroundedPS`, `GroundedSS`, `GroundedPP`, `GroundedSP`)
remain semantic boundaries. The trie evaluator converts the focused trie back to
`SpaceValue` before calling host Scala code, then re-trieifies the result.
Benchmarks show this boundary clearly: native path-algebra programs speed up;
grounded-heavy programs can slow down.

## Algebraic Results And Structural Reuse

Every Boolean trie operation computes an `AlgebraicResult` recursively, not
only at the root. `Identity` contains a bit mask naming every argument equal to
the result, `Bespoke` carries a newly built trie, and `Empty` records why the
result vanished. The ordinary `union`, `intersect`, `diff`, and `restrictBy`
methods unwrap this result while returning an input object directly whenever an
identity bit is present.

The exhaustive binary cases are:

- Union is a left, right, or both identity; bespoke; or empty because both
  arguments were empty.
- Intersection is a left, right, or both identity; bespoke; empty because an
  argument was empty; or empty because two non-empty arguments were disjoint.
- Subtraction is a left identity when the right side is empty or merely
  disjoint; bespoke after removing a proper non-empty overlap; empty because
  the left side was empty; or empty because the right side covered it. A
  subtraction cannot equal a non-empty right argument: membership in the right
  side is exactly what subtraction excludes.
- Restriction additionally returns `allPrefixesMatched`. A left identity means
  no source path was dropped. A right-only identity means the kept paths equal
  the prefixes and some source paths were dropped. A bespoke result with every
  prefix matched is a superset in the prefix order (every right path has an
  equal or longer kept left path); a bespoke result with that flag false has
  unused prefixes as well as dropped source paths. Empty results distinguish
  both-empty, left-empty, right-empty, and non-empty/no-match inputs.

This metadata is computed inside `TrieIntMapOps`, alongside the existing
Patricia `Tip`/`Bin` descent. A parent node combines child identity masks and
terminal bits, so a containment result can reuse the complete smaller or larger
argument without an equality re-traversal or a generic key-set scan.

## Join-All And Meet-All

`joinAll` folds tries with structural union. Its useful property is that sparse
branches are copied whole whenever only one operand contains the branch.

`meetAll` is the non-trivial operation. A naive implementation would enumerate
paths from one trie and check membership in all others, which is acceptable as a
reference but loses the trie structure. The native implementation:

1. materializes the operand list once;
2. chooses the root with the smallest child/node/path profile as the driver;
3. recurses only on child keys present in every operand;
4. sets the terminal bit only when every operand is terminal at that focus;
5. prunes empty children.

That makes `TailsIntersection` cheap for trie-shaped universal queries: it
becomes `meetAll(children.values)` rather than `groupMap(...).reduce(_ intersect
_)` over materialized suffix sets.

## PathMap Influence

The supplied `PathMap-master.zip` is a Rust pathmap implementation with DAG
sharing, read/write zippers, prefix/product/overlay zippers, and algebraic trie
operations. This Scala runtime does not copy the Rust implementation or FFI into
it. Instead it mirrors the relevant API ideas in a small form suitable for this
Scala codebase:

- `TrieSpace.Cursor` is a read zipper over a `TrieSpace` root and prefix focus.
- `Cursor.down`, `up`, `descend`, and `subtree` are enough to express focused
  prefix descent without reparsing path sets.
- `wrap` corresponds to PathMap's `PrefixZipper` idea.
- `concat` corresponds to a materialized `ProductZipper` result.
- `union` is the materialized form of an overlay/join zipper.

The next serious backend step is to compile `RecursiveOpGraph` nodes into
zipper loops rather than materializing every intermediate `TrieSpace`. The
current module takes the first step with `execTrie`, a `RecursiveOpGraph`
executor whose space stack slots are `TrieSpace` values. It runs supported graph
nodes with native trie operations and handles iteration subgraphs by iterating
over child subtries directly.

## Correctness

`TrieSpaceTest` compares trie operations and `evalTrie` against the reference
`eval` on:

- round-trip conversion, including epsilon;
- set and prefix operators;
- `TailsIntersection` / meet-all;
- cursor descent;
- arbitrary binary interval cover generation;
- Aunt query;
- recursive semi-naive Datalog;
- grounded Game of Life;
- process-supercompiled residual programs.
- `RecursiveOpGraph` execution over `TrieSpace`, including Aunt query,
  transformation, closures, and range graph nodes.

The benchmark harness also checks equality before timing every row.

## Runtime Evidence

See `TRIE_BENCHMARKS.md`. The headline result is mixed but informative:

- large prefix-heavy and recursive path-algebra workloads improve strongly;
- small residual programs can be slower because the reference set is already
  tiny;
- grounded-heavy workloads still pay conversion costs and need native kernels or
  zipper-level graph execution to improve.
