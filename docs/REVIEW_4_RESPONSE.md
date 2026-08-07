# Review 4 response

This maps each remaining item in `review.md` to its implementation and permanent
gate.

| Concern | Resolution | Permanent gate |
| --- | --- | --- |
| Rank-only cost validation misses magnitude | The mixed-operation corpus fits one geometric-mean nanoseconds-per-unit calibration for each backend and bounds every multiplicative residual by 4x. Composition, restriction, union, and range all participate; no per-operation constant is fitted. | `SpatialCostCorrelationTest` fails on either weak Spearman rank or excessive calibrated residual. |
| `SpatialType.scala` remains monolithic | `SpatialType.scala` now contains the domain, symbolic bounds, CSP values, and decorated results; the interpreter and transfer implementation moved to `SpatialAnalyzer.scala`. | Normal whole-project compilation and the complete spatial suite. |
| Guarded specializer has no production caller | `SpatialCompilation` now lives beside `SpatialSpecializer`; `Supercompiler.specialize` derives annotations from syntactically concrete arguments, calls `selectApplicable`, and installs the residual only when `applicableTo` accepts those arguments. The report retains the exact spatial rewrite facts used. | A production-pipeline test checks selection, recorded facts, and evaluation equivalence; the guarded residual corpus checks broader equivalence. |

The cost scale is intentionally backend- and machine-specific. The meaningful
dimensionless claim is that one scale per backend explains all operations in the
corpus within the residual threshold; Spearman remains as a separate scaling-order
check. The final report's maximum residual factors were 2.13x for reference, 3.08x
for trie, 3.04x for zipper, and 1.71x for graph. The balanced analyzer scaling run
remained linear through 32,768 leaves (57.527, 67.621, 157.302, 317.915, 573.570,
and 1,132.108 ms from 1,024 through 32,768 leaves); absolute times reflect the loaded
machine, while the final five doublings preserve the expected linear trend.
