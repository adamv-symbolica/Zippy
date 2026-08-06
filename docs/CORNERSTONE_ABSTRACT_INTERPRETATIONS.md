# Cornerstone abstract interpretations

These results are computed at open routine boundaries from one explicit
`SpatialRoutineAnnotations` value per program. `outputRoutineAbstract` never
invokes the exact-value evaluator, including for literal ranges and
zero-argument generators. An exact cardinality below therefore means that an
annotated semantic law or abstract constraint domain proved a count; it does
not mean that result paths were enumerated.

The dataflow is strictly one-way:

```text
annotated spatial arguments + program syntax + annotated semantic type laws
  -> abstract interpretation -> reported bounds
```

Concrete executions used by soundness tests run afterward and cannot alter an
annotation or a reported bound. In particular, neither the 8-puzzle state graph
nor the MORKL queens output is evaluated while constructing this report.
When a symbolic expression is numerically audited, `annotatedBound` resolves
only propagated one-sided bounds and Z3 constraints over annotated atom
envelopes; opaque operations remain at zero/infinity rather than being run.

The analysis combines constructor transfer with semantic reduced-product
contracts. Provenance is explicit below: “structural” means derived from syntax
and input types; “asserted” means a theorem/CSP supplied in `resultLaws`. The
reduced result is their intersection, never a substitution.

| Program | Structurally derived | Asserted annotation | Reduced result | Path information |
| --- | --- | --- | --- | --- |
| Aunt query | schema and conservative join product | `upper ≤ P*min(Pe,Ce,F)` | structural ∩ asserted | exact length 3, `Aunt.person.aunt` |
| Semi-naive Datalog closure | large fixpoint exceeds the configured structural budget and becomes top | direct edges retained; `upper ≤ E²` | `[E,E²]` | unknown length |
| Pure Game of Life | the pure `neigh` helper derives eight affine alternatives; the full step exceeds the report budget | radius-one image: `upper ≤ 9L` | `[0,9L]` | length `[1,∞]` under the report budget |
| Full 8-puzzle closure | large fixpoint exceeds the configured structural budget and becomes top | legal nonempty seed saturates `9!/2` component | exactly `181440` | unknown length |
| Temperature restriction | `[0,W]`, source schema | none | `[0,W]` | exact length 4 |
| 4-queens generator | generator structure derives four-coordinate paths | finite-domain CSP has 2 solutions | exactly `2` | exact length 4 |

## Aunt query

The family input is stratified by relation:

```text
parent.?parent.?child [Pe]
child.?child.?parent [Ce]
female.?person       [F]
male.?person         [M]
person.?person       [T]
people = ?person     [P]
```

Every result chooses one person and one aunt witness. The witness must survive
the parent, inverse-child, and female joins, hence at most
`min(Pe,Ce,F)` witnesses can contribute per person. No lower witness is known,
so the result is `[0, P*min(Pe,Ce,F)]`. The exact output schema is
`Aunt.?person.?aunt`.

This is still deliberately non-dependent: keys and relation fibers are not
correlated, so a functional-family annotation could tighten the product
further in a future domain.

## Semi-naive Datalog fixpoint

The call is structurally lowered to a `Fixpoint` before interpretation:

```text
unwrap(Fixpoint(semiNaiveInitial(edges), state,
                semiNaiveStep(state)), complete.path)
```

For `E` distinct directed edges, the closure contains every direct edge, so its
lower cardinality is `E`. Every reachable pair is an ordered pair of endpoints
drawn from edges; the elementary edge-witness bound gives at most `E^2`
distinct pairs. Thus the abstract cardinality is `[E,E^2]`. The cardinality
statement comes from the explicit closure theorem; the configured report
budget returns top for the large structural fixpoint, including its length.

The contract was exhaustively checked on all 512 directed graphs over three
vertices, including loops and the empty graph.

## Pure Game of Life

The open input is:

```text
field = Cell.cell.x[-63..63].cell.y[-63..63] [L]
```

Pure arithmetic interpretation of the `neigh` helper retains the eight affine alternatives
`Cell.(x+dx).(y+dy)` for non-zero `(dx,dy)` in `{-1,0,1}^2`. More importantly,
the full next-generation output is a subset of the radius-one image of the
input: any surviving or born cell must be in one of nine positions associated
with some live cell. Therefore the scalable cardinality bound is `[0,9L]`.
The structural coordinate/rank bound is also retained, so the actual internal
upper is the minimum of both.

The subset/image theorem was exhaustively checked on every subset of a 3x3
field against an independent B3/S23 implementation.

## Full 8-puzzle reachability

The analyzed program is the unrestricted closure

```text
Fixpoint(start, reachable,
  reachable union step3(reachable))
```

where `start` is one annotated symbolic legal board of length nine. Legal moves
preserve permutation parity, the 3x3 state graph has one connected parity
component, and its capacity is `9!/2`. The connected finite-component contract
therefore proves exact cardinality `181440`. The result still has
`exactValue=None`: no board was inlined and no state was enumerated by the
analysis.

The capacity is a theorem annotation (`9!/2` from permutation parity and
connectedness), not a number discovered by exploring the MORKL output. A
separate downstream test may validate that theorem, but its result is not
available to the analysis.

## Temperature prefix query

```text
world = cell.?latitude.?longitude.?bucket [W]
```

Restriction preserves the source schema and cannot add paths, giving `[0,W]`
and exact length four. This is optimal without a coverage witness: the selected
prefix may occur nowhere. `SpatialPrefixCoverage` can raise the lower bound when
such a witness is part of the input annotation.

## 4-queens generator

Each of four columns is abstracted as a finite row domain `1..4`. The relational
component imposes `AllDifferent` and every diagonal constraint
`abs(row_i-row_j) != abs(i-j)`. Its finite constraint count is exactly two, so
the MORKL output has exact cardinality two. The resource-limited full-generator
analysis retains the structurally derived exact path length four and has
`exactValue=None`.

The same generic constraint domain derives `1,0,0,2,10,4` for board sizes one
through six directly from annotated finite domains and constraints.

## Remaining precision boundary

None of the six analyses materializes a concrete output. Aunt and temperature
retain exact output length, including queens; the resource-limited Datalog,
Life, and puzzle reports deliberately lose it rather than exposing an
unchecked partial analysis. The principal remaining gaps are dependent relation fibers
for the Aunt query, a first-class graph/degree domain rather than supplied
closure contracts, a symbolic arithmetic expression for parameterized puzzle
and queens sizes, and lower coverage facts for spatial restrictions.
