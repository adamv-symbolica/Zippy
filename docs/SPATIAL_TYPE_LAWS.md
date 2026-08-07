# Spatial abstract-interpretation laws

This document records two related layers. The generated FOL obligations model
the semantic set-interval lattice `[must,may]`. New bridge obligations formalize
constructor induction, the post-fixpoint induction step, optimizer/evaluator preservation, and guarded
backend selection. They still do **not** constitute a machine extraction or a
line-by-line proof of the Scala interpreter. Executable audits are regression
evidence, not a substitute for that final implementation-refinement theorem.

## Information carried by the implementation

The analysis currently uses all of the following information.

| Component | Information and use |
| --- | --- |
| Symbolic path item | Constant item, unknown item, or an affine integer item `x + k` with a domain for `x`. Used to prove equality, disjointness, and arithmetic relations. |
| Symbolic path pattern | Ordered vector of symbolic items. Used by concatenation, prefix tests, unwrap, restriction, and exact-value recovery. |
| Path-expression type | Alternative patterns plus a lower/upper length interval. A path expression still denotes one path; its patterns are alternatives. |
| Spatial stratum | Pattern or length region together with a lower/upper cardinality. Provably disjoint strata have additive lower bounds; overlapping strata use their maximum lower bound. |
| Total size | Lower/upper result-space cardinality, including symbolic and `ifZero` expressions. |
| Global path length | Minimum/maximum length projected from every nonempty stratum. |
| Exact constant | A finite path set represented path-for-path while below the pattern cap. It is the constant abstract subdomain. |
| Emptiness and positivity | Zero upper means definitely empty; positive lower means definitely nonempty. These facts guard iteration and encoded control flow. |
| Prefix dependency | `SpatialPrefixCoverage` records that a path prefixes a selected source stratum. It is required for sound positive restriction lower bounds. Length compatibility alone only proves a possible match. |
| Conditional guard | Mutually exclusive empty/nonempty branches retain `ifZero` instead of being independently added. |
| Scalar reduction | Z3-backed total-size and path-length bounds reduce the spatial product from annotated atom envelopes only; opaque atoms are never measured by evaluation. Reduction may tighten but may not change concretization. |
| Group-sensitive iteration | Constant heads contribute at most one nonempty group; affine heads are capped by their declared domain; unknown heads by source size. A constant-headed tail is the whole matching stratum. |
| Pointwise iterator chain | A canonical nested iterator chain consuming one complete source path has upper `source size * leaf-per-path upper`, avoiding independent regrouping products. |
| Total source caps | Selectors cannot exceed their source; composition cannot exceed the product of operand totals. These caps reduce stratum-derived totals. |
| Semantic type annotation | `SpatialRoutineAnnotations.resultLaws` contributes a proved must/may cardinality envelope. It is an analysis input and is intersected with, never substituted for, constructor analysis. |
| Finite relational count | `FiniteIntConstraintProblem` counts assignments within an explicit node budget and falls back to no refinement when exhausted. |
| Fiber degree | Exact constants are counted directly. Symbolic constant/affine/unknown items yield per-level choice and fiber bounds; key-specific dependent correlation remains future work. |
| Bounded representation | Above the pattern limit, patterns are summarized into length strata. Summarization enlarges concretization; it never truncates alternatives. |
| Inconsistent state | `SpatialType.bottom` represents contradictory evidence and absorbs meet and deterministic transfer. |
| Fixpoint invariant | Ascending iteration plus checked widening returns only a post-fixpoint; failure to establish one returns top in every spatial component. |
| Analysis cost | Symbolic lower/upper work/allocation intervals are propagated generically and per backend. A dominant-monomial antichain supplies the separate asymptotic order. |
| Decorated tree | Every occurrence has a positional child-index identity; repeated lexical observations are retained and joined for optimizer consumers. |

## Semantic interval lattice

Let `Path` be the set of paths and `Space = P(Path)`. The semantic envelope of a spatial type is

```text
A = {⊥} + {[L,U] | L ⊆ U ⊆ Path}
```

with concretization

```text
γ(⊥)     = ∅
γ([L,U]) = {S | L ⊆ S ⊆ U}.
```

The precision order is inclusion of concretizations:

```text
A ⊑ B  iff  γ(A) ⊆ γ(B)
[L₁,U₁] ⊑ [L₂,U₂]  iff  L₂ ⊆ L₁ and U₁ ⊆ U₂.
```

Thus `⊥` admits no concrete space, while `⊤ = [∅,Path]` admits every space. Exact abstraction is `[S,S]` and has the singleton concretization `{S}`.

For a nonempty family of non-bottom intervals:

```text
supᵢ [Lᵢ,Uᵢ] = [intersectionᵢ Lᵢ, unionᵢ Uᵢ]
infᵢ [Lᵢ,Uᵢ] = normalize([unionᵢ Lᵢ, intersectionᵢ Uᵢ])
```

where an inconsistent interval normalizes to `⊥`. Bottom is ignored by a supremum and absorbs an infimum. The empty supremum is `⊥`; the empty infimum is `⊤`. Binary join and meet are the supremum and infimum of a pair. This is a complete bounded lattice and satisfies partial-order, universal-bound, idempotence, commutativity, associativity, and absorption laws.

It is **not distributive**. Normalization can turn an inconsistent meet into bottom, and both distributive equalities have finite counterexamples. Optimizers and reductions must not assume distributivity of abstract types even though concrete path sets form a Boolean algebra.

## Reduced product

The implementation is a finite projection of this model. Its intended semantic interpretation is a reduced product:

```text
γᵣ(shape × size × length × dependencies)
  = γshape ∩ γsize ∩ γlength ∩ γdependencies.
```

Componentwise refinement is monotone in the product order. `SpatialType.reduce`
clamps strata by totals, projects strata back to total/length components,
detects constant contradictions, and iterates to an idempotent result. The FOL
obligations abstract non-shape components behind `qgamma`; they specify the
target property rather than verify this Scala reduction.

A semantic contract can equivalently contribute a must-set `C_L` and may-set
`C_U`. Its interval reduction is

```text
contract([L,U], C_L, C_U) = normalize([L union C_L, U intersection C_U]).
```

If `L subset S subset U` and `C_L subset S subset C_U`, the reduced interval
still contains `S`. It always refines `[L,U]`; strengthening either side of a
consistent contract refines the result again (an inconsistent interval
normalizes to bottom in the executable lattice). These three statements are
standalone FOL obligations. Cardinality contracts in the implementation are
the arithmetic projection of this theorem.

The concrete semantic laws currently used at cornerstone boundaries are:

| Contract | Required semantic precondition | Cardinality consequence |
| --- | --- | --- |
| subset of an `m`-image of `X` | every output has an input witness and each input has at most `m` outputs | `upper <= m*upper(X)` |
| contains `X` | every input path occurs in the result | `lower >= lower(X)` |
| directed transitive closure of `E` distinct edges | direct edges retained; outputs are reachable ordered endpoint pairs | `E <= result <= E^2` |
| finite universe `U` | every output is a member of `U` | `upper <= |U|` |
| connected finite component | every nonempty legal seed saturates the named component | exact zero for empty seed, otherwise component capacity |
| finite constraint solutions | finite variable domains and relational constraints are part of the input annotation | exact abstract constraint count |

These are explicit boundary annotations. Exact consequences are intersected
with structural intervals and produce bottom on contradiction. There is no raw exact-cardinality law,
so an observed result size cannot be fed back as an unexplained bound. The
generic analyzer does not infer a
graph closure, legal puzzle state, or queens constraint merely from an
unrelated syntax tree.

## Best interval transformers

For valid input intervals `A=[L₁,U₁]` and `B=[L₂,U₂]`, the following are sound. Union and subtraction are additionally proved to be the best correct interval abstractions, not merely safe envelopes.

| MORKL operation | Abstract interval |
| --- | --- |
| `A ∪ B` | `[L₁ ∪ L₂, U₁ ∪ U₂]` |
| `A ∩ B` | `[L₁ ∩ L₂, U₁ ∩ U₂]` |
| `A \ B` | `[L₁ \ U₂, U₁ \ L₂]` |
| `A x B` | `[L₁ x L₂, U₁ x U₂]` |
| `A <| B` | `[L₁ <| L₂, U₁ <| U₂]` |
| raffination | `[L₁ raff U₂, U₁ raff L₂]` because the prefix operand is antitone |
| wrap/unwrap by fixed prefix | Apply the operation to both bounds |
| tails union, prefix closure, suffix closure, tails closure | Apply the monotone operation to both bounds |
| tails intersection | `[∅, tailsUnion(U)]`; adding a head can shrink a universal tails meet |
| ordered range | `[∅,U]` generally; full sentinel is identity and a statically empty range is exact empty |

The product, restriction, and closure formulas rely on concrete monotonicity. Raffination and subtraction are monotone in the left operand and antitone in the right operand.

`Range` is not monotone under source inclusion: inserting an earlier path changes rank-based selection. An arbitrary iteration body is also not monotone because it may subtract or inspect a changing bound tail. If the template is monotone in its tail argument, however, source iteration is monotone and `[Iter(L,F), Iter(U,F)]` is sound. This positivity condition is proved explicitly rather than silently assumed.

## FOL model obligations

The generated `spatial_*_fo.p` obligations cover:

- closed-form interval order, partial order, bottom/top, and exact concretization;
- arbitrary supremum/infimum contracts, binary upper/lower bounds, least/greatest universal properties, interval consistency, and all bounded-lattice algebra laws;
- concrete and abstract monotonicity/variance for composition, restriction, raffination, and positive iteration;
- transfer soundness for union, intersection, subtraction, composition, restriction, raffination, wrap, unwrap, closures, safe range, universal tails intersection, and positive iteration;
- best-correctness for union and subtraction;
- reduced-product projection, componentwise monotonicity, gamma preservation, and reduction idempotence.
- semantic-contract transfer soundness, refinement, and monotonicity under a stronger contract.
- structural interpreter soundness from the finite-AST induction principle and constructor-transfer premises;
- post-fixpoint induction for `Fixpoint` and optimizer soundness transported across evaluator-preserving rewrites;
- backend-selection bridges: bounded-depth trie unrolling, common-prefix zipper pre-focus, and exact graph constant folding, each derived from extensional backend semantics.

Each generated problem has a lean, operator-specific prelude. Abstract values and abstract collections are guarded by predicates because TPTP FOL is untyped. Transformer equations are guarded by `L ⊆ U`; without that guard, different invalid interval representatives all denote bottom but could be mapped to different outputs, making the theory inconsistent.

`SpatialTypeLatticeTest` independently enumerates the 28 semantic interval
values over a three-element universe. It also exercises the production
bottom/join/meet/order/widening API, but the enumeration is an oracle for the
semantic model, not a representation-isomorphism proof.

Executable semantic checks complement FOL where cardinality arithmetic is not
encoded: all 512 three-node directed graphs satisfy `E <= |TC(E)| <= E^2`; all
512 subsets of a 3x3 Life field satisfy the nine-image law; and the finite
constraint component matches n-queens counts through size six. These checks run
after abstract interpretation and have no data path back into its annotations.

## Remaining implementation-refinement obligations

The complete lattice makes the following future obligations well-formed rather than ad hoc:

1. Connect the generic constructor-soundness and fixpoint bridge theories to the concrete Scala `analyze` implementation by a checked refinement/extraction layer. The semantic theorem `eval(e,ρ) ∈ γ(analyze(e,ρ#))` is now stated at the FOL boundary and audited over original and optimized random programs, but source-to-theory correspondence remains trusted.
2. Assumption refinement: refining every input type must refine output for positive programs; variance annotations identify the exact exceptions.
3. Strengthen the current post-fixpoint induction obligation to a leastness theorem for positive `Fixpoint` over the full represented stratum product.
4. Best-correctness for intersection, product, restriction, raffination, closures, and positive iteration.
5. A Galois insertion for the finite pattern/stratum representation, including a proof that cap-based summarization is an upper closure operator.
6. Concrete cardinality/length reduction coherence, replacing generic `qgamma` with arithmetic theories and proving every reduction step preserves gamma.
7. Dependent fiber-degree and prefix-coverage components, so graph min/max/average degree claims participate in the same product order and fixpoint induction.
