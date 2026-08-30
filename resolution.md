# Review resolution

This ledger maps each item in [`plan.md`](plan.md) to the concrete code,
artifact, documentation, and regression that resolves it. Proof results remain
explicit about bounded evidence: the checked report is
`PASS_WITH_PROOF_DEBT`, not an unconditional unbounded proof success.

1. **Proof status synchronized.** [`README.md`](README.md) now describes the
   checked-in report as `PASS_WITH_PROOF_DEBT` after Vampire, Z3, and egglog all
   executed successfully. The status is computed by
   [`tools/proof_pipeline.py`](tools/proof_pipeline.py) and recorded in
   [`docs/proofs/PROOF_REPORT.md`](docs/proofs/PROOF_REPORT.md).

2. **Duplicate Datalog dump removed.** The root `datalog-morkl.txt` duplicate
   was deleted. [`terminating/datalog_a.txt`](terminating/datalog_a.txt) is the
   sole canonical copy used by the termination artifacts.

3. **`PathItem.Symbol` documentation removed.** Every obsolete constructor in
   [`docs/ALGEBRA.md`](docs/ALGEBRA.md) now uses the current `PathItem(...)`
   representation.

4. **Portable Vampire discovery.** [`tools/proof_pipeline.py`](tools/proof_pipeline.py)
   resolves Vampire in the order `--vampire`, `VAMPIRE`, then `PATH`; it has no
   `/Applications/Vampire` default. [`README.md`](README.md) and
   [`docs/proofs/README.md`](docs/proofs/README.md) document the same contract.

5. **Zipper routine calls, including semi-naive Datalog, are executable.** The
   `Space.Call` path in [`src/main/scala/ZipperSpace.scala`](src/main/scala/ZipperSpace.scala)
   binds path and space parameters for non-recursive helpers and gives an
   explicit diagnostic for recursive calls that were not lowered. Recursive
   union-saturating calls are first converted by `lowerFixpointCalls`; the
   generator does this in
   [`src/test/scala/CornerstoneProofArtifacts.scala`](src/test/scala/CornerstoneProofArtifacts.scala).
   [`src/test/scala/TrieSpaceTest.scala`](src/test/scala/TrieSpaceTest.scala)
   exercises a helper with both argument kinds and compares a lowered
   semi-naive-Datalog zipper execution with the reference evaluator.

6. **Demand-driven zipper fixpoint support added to the important closure
   family.** Recognized tail fixpoints in
   [`src/main/scala/ZipperSpace.scala`](src/main/scala/ZipperSpace.scala) lower
   to `TailsClosure`, whose child observations use Antimirov frontier states
   instead of an eager Kleene materialization. The focused 256-path regression
   in [`src/test/scala/TrieSpaceTest.scala`](src/test/scala/TrieSpaceTest.scala)
   proves a requested child is answered with zero Kleene rounds. Other
   recognized finite templates remain zipper nodes; arbitrary unrecognized
   monotone step functions retain the correctness-first materializing fallback,
   so this resolution does not claim a general demand scheduler for every
   possible higher-order step.

7. **Dependent fibers, degree facts, arithmetic, and restriction lower bounds
   strengthened.** [`src/main/scala/ResultSpaceSize.scala`](src/main/scala/ResultSpaceSize.scala)
   computes exact unions of selected literal fibers, exact literal restrictions,
   identity/epsilon restriction bounds, and reconstruction-iteration lower
   bounds. `SpatialType.fiberDegree` in
   [`src/main/scala/SpatialType.scala`](src/main/scala/SpatialType.scala) exposes
   exact literal fiber degree and bounded symbolic key/degree information.
   [`src/test/scala/ResultSpaceSizeTest.scala`](src/test/scala/ResultSpaceSizeTest.scala)
   checks all of those cases. Puzzle state arithmetic is parameterized as
   `(width²)!/2` (with the 1-cell exception) and tested for widths 1–3 in
   [`src/test/scala/CornerstoneSpatialTypeTest.scala`](src/test/scala/CornerstoneSpatialTypeTest.scala);
   the existing parameterized n-queens interpretation is tested for sizes 1–6.

8. **Correlated nested size expressions no longer multiply the same partition
   twice.** The mixed analysis in
   [`src/main/scala/ResultSpaceSize.scala`](src/main/scala/ResultSpaceSize.scala)
   recognizes flattened nested maps, independent branches, reconstruction, and
   literal lookup fibers, producing tighter nonzero lower bounds and finite
   uppers. `SizeExpr.annotatedBound` now memoizes the identity DAG so repeated
   correlated terms are not recursively re-expanded. Regressions in
   [`src/test/scala/ResultSpaceSizeTest.scala`](src/test/scala/ResultSpaceSizeTest.scala)
   tighten a nested-map upper from 16 to 4 and a dependent lookup from an
   unbounded baseline to exact 3.

9. **Recursive cost recurrences are supported.** Decreasing recursive calls and
   their depth parameters are discovered in
   [`src/main/scala/SpatialRecursion.scala`](src/main/scala/SpatialRecursion.scala);
   additive/geometric closed forms and per-backend round components live in
   [`src/main/scala/SpatialCostModel.scala`](src/main/scala/SpatialCostModel.scala).
   [`src/test/scala/SpatialTypeTest.scala`](src/test/scala/SpatialTypeTest.scala)
   covers closed forms and a routine whose self-call consumes tails, while
   [`src/test/scala/SpatialCostCounterTest.scala`](src/test/scala/SpatialCostCounterTest.scala)
   checks predicted iteration rounds against all executors.

10. **Tautological cornerstone SMT2 certificates retired.** The 24 tracked
    `proofs/examples/smt2/*.smt2` files were deleted and that generated directory
    is ignored in [`.gitignore`](.gitignore). The cornerstone generator in
    [`src/test/scala/CornerstoneProofArtifacts.scala`](src/test/scala/CornerstoneProofArtifacts.scala)
    now emits only exact TPTP and executable egg certificates; the authoritative
    set is [`proofs/examples/proof_manifest.tsv`](proofs/examples/proof_manifest.tsv).

11. **Spatial-analysis boundary stated honestly.** [`README.md`](README.md) now
    says spatial analysis is an explicit API and that specialization is an
    explicit operation; it no longer implies that normal evaluation or
    compilation automatically runs either pass.

12. **Superficial acceptance scans removed from the proof decision.** The
    `validate_*_acceptance` source-string scanners are no longer called by
    [`tools/proof_pipeline.py`](tools/proof_pipeline.py). The live cheap gates
    validate generated artifact structure, negative controls, symbol coverage,
    exact required full-program manifest identities, frontier structure,
    termination inputs, and zero `UNPROVED` operational rows. Runtime semantics
    and asymptotics are checked by Scala tests rather than Python string
    presence. [`docs/proofs/README.md`](docs/proofs/README.md) documents this
    split.

13. **Stale review diff removed.** `laws.diff` was deleted; the repository and
    generated manifests are now the sources of truth.

14. **Z3 startup and timeout failures are distinct and visible.** Shared
    `Z3Executable` in
    [`src/main/scala/Z3ResultSpaceSize.scala`](src/main/scala/Z3ResultSpaceSize.scala)
    throws `MissingZ3Executable` with an installation/property hint when the
    process cannot start, emits a timeout diagnostic when its wait budget
    fires, and no longer swallows arbitrary exceptions. Path-length analysis in
    [`src/main/scala/ResultPathLength.scala`](src/main/scala/ResultPathLength.scala)
    uses the same wrapper. The missing-command and forced-timeout behaviors are
    regression-tested in
    [`src/test/scala/ResultSpaceSizeTest.scala`](src/test/scala/ResultSpaceSizeTest.scala).

15. **Huge full-open SMT output is ephemeral, not committed.** All six tracked
    Aunt/GOL `*_full_open_*.smt2` files were removed and the pattern is ignored
    by [`.gitignore`](.gitignore). The full cases in
    [`src/test/scala/OpenProgramProofArtifacts.scala`](src/test/scala/OpenProgramProofArtifacts.scala)
    no longer request those bounded relation expansions; structural TPTP is the
    scalable full-program tier. Regeneration still owns the remaining bounded
    open artifacts and
    [`proofs/open/proof_manifest.tsv`](proofs/open/proof_manifest.tsv).

16. **SCC is a cornerstone throughout.** The direct transitive-closure program
    and expected mutually reachable pairs are defined in
    [`src/test/scala/SccCornerstone.scala`](src/test/scala/SccCornerstone.scala).
    [`src/test/scala/MORKL.scala`](src/test/scala/MORKL.scala) runs it, unignored,
    through reference, trie, zipper, and compiled-graph backends;
    [`src/test/scala/TrieBenchmarks.scala`](src/test/scala/TrieBenchmarks.scala)
    includes it in the common benchmark table;
    [`src/test/scala/CornerstoneSpatialTypeTest.scala`](src/test/scala/CornerstoneSpatialTypeTest.scala)
    checks its spatial report; and the cornerstone/open generators emit its egg,
    four TPTP backend certificates, and structural full-program certificate as
    listed in [`proofs/examples/proof_manifest.tsv`](proofs/examples/proof_manifest.tsv)
    and [`proofs/open/proof_manifest.tsv`](proofs/open/proof_manifest.tsv).

17. **A larger bounded proof mode is available and parallelized.** `--long` in
    [`tools/proof_pipeline.py`](tools/proof_pipeline.py) changes the default
    universe from `a,b` through length 3 to `a,b,c` through length 4 while
    preserving explicit user overrides. Independent solver obligations use the
    existing worker pool, defaulting to `min(4, CPU count)`. Both controls are
    documented in [`README.md`](README.md) and
    [`docs/proofs/README.md`](docs/proofs/README.md).

18. **The important full programs are mandatory obligations.** The runner's
    `REQUIRED_FULL_PROGRAM_OBLIGATIONS` in
    [`tools/proof_pipeline.py`](tools/proof_pipeline.py) requires exact Vampire
    theorem rows and existing files for semi-naive Datalog, the arbitrary-input
    2x2 puzzle, the complete 24-state 2x2 step, and SCC. The generated rows are
    in [`proofs/open/proof_manifest.tsv`](proofs/open/proof_manifest.tsv). A
    missing row, wrong solver kind/expectation, or missing artifact fails before
    solver execution.

19. **Loop sharing is alpha-invariant.** Graph hashing and equality in
    [`src/main/scala/MORKL.scala`](src/main/scala/MORKL.scala) erase binder-name
    constants for `Iteration`, `Fold`, `Fixpoint`, `ExtractPathRef`, and
    `ExtractSpaceMention` while preserving graph shape. The regression in
    [`src/test/scala/TrieSpaceTest.scala`](src/test/scala/TrieSpaceTest.scala)
    starts with two differently named iteration subgraphs, confirms they merge
    to one, checks scope, and compares both graph executors with the reference.

20. **Size-only merges use operation-local Patricia aggregates.** Generic
    cached `ChildMapAggregate` metadata and bottom-up aggregate propagation in
    [`src/main/scala/TrieIntMapOps.scala`](src/main/scala/TrieIntMapOps.scala)
    let union/intersection/difference retain counts for untouched operands
    without scanning them. `TrieSpace.nodeFromChildren` in
    [`src/main/scala/TrieSpace.scala`](src/main/scala/TrieSpace.scala) consumes
    those aggregates directly. The identity, root-disjoint, mixed, and fully
    interwoven regimes in
    [`src/test/scala/TrieLayerAsymptoticTest.scala`](src/test/scala/TrieLayerAsymptoticTest.scala)
    enforce the touched-Patricia-node behavior.

21. **Lazy cursor cost follows the demanded frontier.** For syntactic
    `(A \/ B) /\ C`, [`src/main/scala/SpatialAnalyzer.scala`](src/main/scala/SpatialAnalyzer.scala)
    caps only the zipper union and intersection intervals by `C`'s frontier;
    eager reference/trie/graph bounds are unchanged. The 32/512/4096 regression
    in [`src/test/scala/SpatialCostCounterTest.scala`](src/test/scala/SpatialCostCounterTest.scala)
    holds `C` fixed, proves zipper work constant, and proves reference work
    grows.

22. **Set-interval lattice proofs are connected to code operations.**
    [`src/main/scala/ProofArtifacts.scala`](src/main/scala/ProofArtifacts.scala)
    now generates `spatial_code_normalize_bridge_fo`,
    `spatial_code_join_bridge_fo`, and `spatial_code_meet_bridge_fo`, matching
    `SpatialType` normalization, `joinAlternatives`, and `meet` with the abstract
    interval normalize/join/meet laws. They are registered in
    [`proofs/proof_manifest.tsv`](proofs/proof_manifest.tsv) and materialized as
    [`proofs/vampire/generated/spatial_code_normalize_bridge_fo.p`](proofs/vampire/generated/spatial_code_normalize_bridge_fo.p),
    [`proofs/vampire/generated/spatial_code_join_bridge_fo.p`](proofs/vampire/generated/spatial_code_join_bridge_fo.p),
    and [`proofs/vampire/generated/spatial_code_meet_bridge_fo.p`](proofs/vampire/generated/spatial_code_meet_bridge_fo.p).

## Verification status

- Focused Scala suites cover result-size/Z3 behavior, zipper semantics and
  sharing, lazy spatial costs, cornerstone spatial types, and SCC across all
  backends.
- Artifact generation produced the main, cornerstone, and open manifests from
  Scala source.
- The proof runner passed every structural/manifest gate, every Vampire and Z3
  obligation at the normal 300-second per-obligation budgets, and every egglog
  model. [`docs/proofs/PROOF_REPORT.md`](docs/proofs/PROOF_REPORT.md) therefore
  says `PASS_WITH_PROOF_DEBT`; its 433 bounded operational rows remain explicit
  proof debt.
