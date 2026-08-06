# Spatial abstract interpretation

The semantic lattice, complete list of tracked properties, proved transfer laws, and known non-laws are documented in [SPATIAL_TYPE_LAWS.md](SPATIAL_TYPE_LAWS.md).

`SpatialTypeAnalysis` interprets a MORKL program from abstract path/space inputs to an abstract output space. It replaces the old output-side `otypes` experiment, which only collected syntactic variables and could not express cardinality, path length, control flow, or arithmetic relations. The input-side `itypes` experiment remains separate for now.

## Domain

A `SpatialType` is a finite union of `SpatialStratum` values. Each stratum carries:

- a lower/upper path-length interval;
- a lower/upper cardinality interval;
- optionally, a symbolic path pattern.

Patterns contain constants, unknown items, or affine integer items such as `coord.x-1`. Provably different lengths and patterns are disjoint, so their cardinality lower bounds add; possibly overlapping regions use the conservative maximum lower bound. The complete type also exposes total-size and global path-length projections.

`SpatialAssumptions` maps free space mentions and path references to input types. `SpatialPrefixCoverage(k, xs, lengths)` is an optional dependency asserting that path `k` prefixes a represented fiber of `xs` at the selected lengths. This small dependency is necessary for positive restriction lower bounds: knowing only that `k` has length three cannot prove that an arbitrary length-four path starts with `k`.

Iteration is group-sensitive. A constant head has at most one group when its
source is nonempty; an affine head has at most the size of its declared domain;
and an unknown head falls back to source cardinality. The bound tail denotes an
entire fiber, not one arbitrary path, so a constant-headed stratum passes its
full cardinality into the body. Canonical nested iterator chains that consume a
complete source path are additionally interpreted pointwise: their total upper
is the number of source paths times the per-path leaf result, instead of a
product of independent group estimates.

`outputRoutine` and its compatibility alias `outputRoutineAbstract` (also
exposed as `Supercompiler.abstractSpatialType`) always interpret a routine body
from argument annotations. Neither invokes the concrete evaluator, even when
all inputs are exact constants or the routine has no arguments. A closed syntax
tree therefore cannot silently turn an abstract-interpretation experiment into
execution.

The strict API takes one `SpatialRoutineAnnotations` value containing path and
space argument types, prefix coverage, and `SpatialBoundLaw` result-type laws.
Those laws are input annotations, not evaluator callbacks or facts learned from
an output. They intersect the structural cardinality with facts such as
subset-of-image multiplicity, source containment, directed-closure bounds, a
finite universe, or a connected finite component. The only generic exact-count
law accepts an annotated `FiniteIntConstraintProblem`; there is deliberately no
raw `ExactCardinality(number)` hook. Structural and semantic evidence form a
reduced product: neither side can weaken the other.

`FiniteIntConstraintProblem` is the first small relational component. It counts
finite-domain assignments subject to all-different, disequality, and absolute-
difference constraints. The 4-queens report uses it to prove two outputs without
executing the zero-argument MORKL generator. See
[CORNERSTONE_ABSTRACT_INTERPRETATIONS.md](CORNERSTONE_ABSTRACT_INTERPRETATIONS.md)
for all six open-program results.

## Example

For input strata

```text
xs = length 2: 5 paths; length 4: 7 paths; length 6: 9 paths
ys = empty
len(k) = 3
k prefixes xs at lengths 4 and 6
```

the type of

```text
TailsUnion(xs <| (ys ∪ {k}))
```

is:

```text
length 3: [1, 7] paths
length 5: [1, 9] paths
total:    [2, 16] paths
```

Without the prefix-coverage fact, the sound lower cardinalities are zero, while the upper type is unchanged.

## Encoded control flow

The iterator encoding used by `on_empty` is recognized structurally. For

```text
s ∪ s.on_empty(g)
```

the exact total size is represented by the arithmetic expression

```text
ifZero(|s|, |g|, |s|)
```

and each length stratum is guarded by the same condition. This preserves the two elaborated cases directly rather than losing their exclusivity in independent interval additions.

## Pure arithmetic and Game of Life

Unwrapping a two-column integer literal relation recognizes a uniform affine map when the abstract input range is inside the relation domain. Consequently `decr(x)`, `ident(x)`, and `succ(x)` become the distinct patterns `x-1`, `x`, and `x+1`.

For an abstract two-coordinate input, the pure `LifeExample.neigh` routine therefore derives:

- three alternatives for each coordinate;
- nine distinct Cartesian-product patterns;
- removal of the exact center pattern;
- exactly eight output paths, all of length two.

Exact constant spatial inputs form the constant subdomain. A five-step glider
is analyzed as one nested open expression: each abstract output feeds the next
abstract call, while no concrete intermediate result does. The resulting bound
retains exact path length three and contains the final five-cell result. A
separate evaluator call runs only after analysis to check soundness.

## Graph fibers

`fiberDegree(prefixLength)` reports minimum degree, maximum degree, edge count, key count, and the average as an edge/key ratio. It is exact for constant-pattern types and conservative for abstract patterns. For `{edge.a.b, edge.a.c, edge.b.c}` at prefix length two it reports minimum degree 1, maximum degree 2, three edges, two keys, and average `3/2`.

The domain deliberately keeps this summary separate from individual strata. A later dependent extension can propagate degree summaries through symbolic vertices without changing the current input-to-output API.

## Cardinality-preserving caps

Every selector-like transfer retains the total source cap even when its strata
must be summarized: intersection is capped by both operands; subtraction,
restriction, range, tails-union, and unwrap by their selected source; and
composition by the product of operand totals. These scalar caps are part of the
same reduced product as the shape strata and prevent a lossy shape split from
inventing a larger total.

The `SizeExpr` normalizer uses only universally valid natural-number order
facts. It flattens extrema and products, makes Boolean indicators idempotent,
uses `positive(x) <= x`, and removes a dominated branch of a minimum/maximum
when a structural proof succeeds. It deliberately leaves incomparable
polynomials intact.

## One-way validation

Validation is deliberately downstream of analysis:

```text
annotated inputs + syntax + annotated semantic laws -> abstract result
concrete inputs + evaluator                           -> validation only
```

No concrete result, fixture cardinality, or evaluator-derived path shape is an
input to either spatial routine entry point or to the cornerstone report.
Numerical audits use `annotatedBound`, which resolves constants, propagated
one-sided arithmetic, and Z3 constraints from their abstract atom bounds.
Opaque `SizeOf`/minimum-length/maximum-length atoms contribute only their safe
zero/infinity envelope; the legacy `evaluate` helper is reserved for downstream
concrete validation.

The canonical corpus `SpaceFuzzerCorpus.generate(1000, 20260708L, 5, 400)` was evaluated with the same concrete inputs used to construct its spatial assumptions:

- all 1,000 total-size projections contained the concrete result;
- all 1,000 path-length projections contained the concrete result;
- every finite spatial projection was at least as tight as the corresponding Z3 result-size or path-length projection;
- 988 programs also had fully exact-length strata, and every per-length cardinality interval contained its concrete count;
- 373 total sizes and 874 global length intervals were exact;
- spatial structure tightened 597 size lowers, 571 size uppers, 127 length lowers, and 210 length uppers beyond the annotation-only scalar analyses;
- no total-size, global-length, or exact-length-stratum upper remained unbounded.

These are the corrected annotation-only figures. Earlier `444/904` figures used
the legacy concrete resolver while scoring symbolic bounds and are superseded;
the analyzer itself did not use those results, but they must not appear in an
abstract-interpretation precision report.

Pattern retention is capped (64 by default). Overflow is merged into length strata with preserved cardinality bounds; alternatives are never truncated. This bounds symbolic arithmetic growth without making the abstraction unsound.

The permanent `spatialTypeScalingAudit` guard measures balanced unions. On the
final implementation, seven-sample medians for 1,024/2,048/4,096/8,192/16,384/
32,768 leaves were 14.446/22.566/32.240/62.584/122.296/241.800 ms. The large
end doubles with input size and is below the preceding
40.931/59.910/61.913/115.842/232.997/464.938 ms run at every measured size.
