# Result-space size audit

Both audited runs were sound: every concrete result was at least its evaluated lower bound and no larger than its finite evaluated upper bound. No lower bound was unknown.

## Corpus summary

| Corpus | Observations | Finite upper | Unbounded upper | Exact finite upper | Exact lower | Mean finite upper gap | Mean lower gap |
|---|---:|---:|---:|---:|---:|---:|---:|
| Full test suite | 467,359 | 448,444 (95.95%) | 18,915 (4.05%) | 338,359 (75.45%) | 215,645 (46.14%) | 5.176e39 | 14.020 |
| Canonical random corpus | 1,000 | 963 (96.30%) | 37 (3.70%) | 301 (31.26%) | 205 (20.50%) | 23,106,409.808 | 3.904 |

The full-suite upper-gap mean is dominated by a small number of nested permutation/n-queens iterations whose independent per-level cardinality products compound. The bucket distributions below are more representative than that mean.

## Full test suite distributions

### Finite upper additive overestimate

| Gap | Count | Share |
|---|---:|---:|
| 0 | 338,359 | 75.45% |
| 1 | 14,488 | 3.23% |
| 2–3 | 24,059 | 5.36% |
| 4–7 | 5,929 | 1.32% |
| 8–15 | 6,322 | 1.41% |
| 16–31 | 1,473 | 0.33% |
| 32–63 | 12,575 | 2.80% |
| 64–255 | 9,657 | 2.15% |
| 256+ | 35,582 | 7.93% |

### Finite upper multiplicative overestimate

| Factor | Count | Share |
|---|---:|---:|
| Exact | 338,359 | 75.45% |
| ≤1.25× | 23,775 | 5.30% |
| ≤1.5× | 3,660 | 0.82% |
| ≤2× | 15,606 | 3.48% |
| ≤4× | 15,054 | 3.36% |
| ≤10× | 6,666 | 1.49% |
| >10× | 22,154 | 4.94% |
| Zero result, positive bound | 23,170 | 5.17% |

### Lower additive underestimate

| Gap | Count | Share |
|---|---:|---:|
| 0 | 215,645 | 46.14% |
| 1 | 22,709 | 4.86% |
| 2–3 | 88,339 | 18.90% |
| 4–7 | 94,842 | 20.29% |
| 8–15 | 20,404 | 4.37% |
| 16–31 | 7,065 | 1.51% |
| 32–63 | 2,437 | 0.52% |
| 64–255 | 12,969 | 2.77% |
| 256+ | 2,949 | 0.63% |

### Lower coverage of the actual result

| Coverage | Count | Share |
|---|---:|---:|
| Exact | 215,645 | 46.14% |
| ≥75% | 28,500 | 6.10% |
| ≥50% | 10,324 | 2.21% |
| ≥25% | 72,144 | 15.44% |
| >0% | 80,421 | 17.21% |
| 0% | 60,325 | 12.91% |

## Random corpus distributions

The canonical corpus uses `SpaceFuzzerCorpus.generate(1000, 20260708L, maxDepth = 5, maxResult = 400)`.

### Finite upper additive overestimate

| Gap | Count | Share |
|---|---:|---:|
| 0 | 301 | 31.26% |
| 1 | 98 | 10.18% |
| 2–3 | 114 | 11.84% |
| 4–7 | 112 | 11.63% |
| 8–15 | 80 | 8.31% |
| 16–31 | 54 | 5.61% |
| 32–63 | 44 | 4.57% |
| 64–255 | 77 | 8.00% |
| 256+ | 83 | 8.62% |

### Finite upper multiplicative overestimate

| Factor | Count | Share |
|---|---:|---:|
| Exact | 301 | 31.26% |
| ≤1.25× | 24 | 2.49% |
| ≤1.5× | 52 | 5.40% |
| ≤2× | 102 | 10.59% |
| ≤4× | 132 | 13.71% |
| ≤10× | 138 | 14.33% |
| >10× | 214 | 22.22% |
| Zero result, positive bound | 0 | 0.00% |

### Lower additive underestimate

| Gap | Count | Share |
|---|---:|---:|
| 0 | 205 | 20.50% |
| 1 | 351 | 35.10% |
| 2–3 | 227 | 22.70% |
| 4–7 | 124 | 12.40% |
| 8–15 | 51 | 5.10% |
| 16–31 | 20 | 2.00% |
| 32–63 | 13 | 1.30% |
| 64–255 | 9 | 0.90% |
| 256+ | 0 | 0.00% |

### Lower coverage of the actual result

| Coverage | Count | Share |
|---|---:|---:|
| Exact | 205 | 20.50% |
| ≥75% | 11 | 1.10% |
| ≥50% | 43 | 4.30% |
| ≥25% | 36 | 3.60% |
| >0% | 16 | 1.60% |
| 0% | 689 | 68.90% |

## Least-tight bounds

### Full test suite

- Least-tight upper bounds with no finite ceiling occurred in large coverage-biased iteration programs: the largest observed concrete result among these was 339 paths versus `∞`.
- The largest finite upper gap was a nested permutation/n-queens expression with 28,512 actual paths and an upper bound of `2,316,673,043,953,008,053,236,882,700,427,523,658,046,124,800`.
- The next large finite case had 85,994 actual paths and upper bound `4,400,999,571,347,089,869,648,423,061,330,876,864,245,888`.
- The least-tight lower bound had 178,098 actual paths and lower bound zero. The next had 162,606 actual paths and lower bound zero.

### Canonical random corpus

- The largest concrete result with an unbounded upper was 58 paths versus `∞`; 37 programs had unbounded uppers in total.
- The largest finite upper gap had 3 actual paths and upper bound `20,661,046,784` (gap `20,661,046,781`). It is a deeply nested iteration/subtraction program whose independent upper products compound.
- The next had 1 actual path and upper bound `1,549,681,956`.
- The least-tight lower bound had 256 actual paths and lower bound 15 (gap 241), from a literal-headed iteration producing `x × (x ∪ x)` followed by singleton subtraction.
- The next lower cases were 99 versus 0 and 84 versus 0.

The main opportunities for tighter analysis are therefore correlated iteration groups, idempotent repeated operands such as `x ∪ x`, and iteration bodies whose outputs collide heavily. General products and nested iterations are sound but intentionally assume independence for their upper ceilings.
