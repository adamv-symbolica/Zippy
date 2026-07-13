# Zipper Algebra Sharing Benchmarks

This report isolates direct zipper evaluation over physically large tries. Each scenario builds two full operands `a` and `b` with the same top-level keys, plus a sparse third operand `cOverlap` containing only the buckets that are physically shared by all three. For the requested sharing level, the corresponding child subtries are the same JVM objects in all overlapping operands; the rest of `a` and `b` have the same outer shape but distinct unique leaves. Each child subtrie also has a small common tail vocabulary so `tailsIntersection` and ordinary intersections have non-empty work to do outside the physically shared portion.

Each operand has 1500 top-level buckets and 16 paths per bucket. Timings compare `evalTrie(expr).pathCount` to `evalZ(expr).pathCount` after a correctness check. Very large results are checked by path count plus border membership samples instead of decoding every path.

| target shared nodes | shared top-level subtries | measured shared nodes / operand nodes | `a`/`b` paths | `a`/`b` trie nodes | sparse third paths | prefix paths |
|---:|---:|---:|---:|---:|---:|---:|
| 1% | 15 / 1,500 | 1.00% | 24,000 | 31,501 | 240 | 96 |
| 50% | 750 / 1,500 | 50.00% | 24,000 | 31,501 | 12,000 | 96 |
| 90% | 1,350 / 1,500 | 90.00% | 24,000 | 31,501 | 21,600 | 96 |

| share | group | operation | result paths | result trie nodes | evalTrie ms | evalZ ms | evalTrie / evalZ | note |
|---:|---|---|---:|---:|---:|---:|---:|---|
| 1% | binary | union | 41,820 | 53,776 | 4.777 | 4.605 | 1.04 x | merge two aligned huge tries |
| 1% | binary | intersection | 6,180 | 9,226 | 4.686 | 3.579 | 1.31 x | walk only common child keys |
| 1% | binary | subtraction | 17,820 | 23,761 | 1.065 | 1.081 | 0.98 x | left-guided traversal |
| 1% | binary | restriction | 1,536 | 2,017 | 0.308 | 0.381 | 0.81 x | top-level prefix filter |
| 1% | binary | raffination | 22,464 | 29,485 | 0.225 | 0.148 | 1.52 x | complement of the prefix filter |
| 1% | binary | composition | 576,000,000 | 756,031,501 | 21.009 | 10.463 | 2.01 x | no selective consumer; full product is materialized |
| 1% | n-ary | three-way intersection | 240 | 316 | 3.716 | 0.030 | 121.99 x | evalZ flattens nested intersections and starts from the sparse overlap operand |
| 1% | unary | wrap | 24,000 | 31,502 | 0.013 | 0.021 | 0.62 x | virtual prefix node |
| 1% | unary | unwrap | 24,000 | 31,501 | 0.008 | 0.008 | 1.05 x | descend through virtual prefix |
| 1% | unary | tails-union | 18,004 | 22,506 | 2.154 | 2.316 | 0.93 x | join every first-level tail |
| 1% | unary | tails-intersection | 4 | 6 | 7.157 | 7.737 | 0.93 x | meet every first-level tail |
| 1% | unary | prefix-closure | 31,500 | 31,501 | 3.602 | 2.878 | 1.25 x | all non-empty prefixes |
| 1% | unary | suffix-closure | 48,008 | 60,010 | 11.797 | 10.469 | 1.13 x | all non-empty suffixes |
| 1% | unary | tails-closure | 48,009 | 60,010 | 7.620 | 6.615 | 1.15 x | epsilon plus suffix closure |
| 1% | range | first(512) | 512 | 673 | 0.018 | 0.017 | 1.00 x | ordered border slice |
| 1% | range | last(512) | 512 | 673 | 0.014 | 0.014 | 1.01 x | ordered border slice |
| 1% | combination | union then exact intersection | 1 | 4 | 0.274 | 0.289 | 0.95 x | selective consumer over a virtual union |
| 1% | combination | diff then restriction | 1,140 | 1,521 | 2.119 | 0.328 | 6.47 x | left-guided diff under prefix filter |
| 1% | combination | product exact intersection | 1 | 7 | 9.322 | 9.638 | 0.97 x | selector should avoid the full product |
| 1% | combination | product prefix restriction | 24,000 | 31,504 | 7.323 | 2.505 | 2.92 x | one product row selected by prefix |
| 1% | combination | tails of restricted union | 2,296 | 2,871 | 0.515 | 0.255 | 2.02 x | join tails after a logical prefix filter |
| 1% | combination | range of union | 512 | 666 | 0.633 | 5.359 | 0.12 x | border slice over a virtual union |
| 50% | binary | union | 33,000 | 42,751 | 0.157 | 0.155 | 1.01 x | merge two aligned huge tries |
| 50% | binary | intersection | 15,000 | 20,251 | 0.130 | 0.143 | 0.91 x | walk only common child keys |
| 50% | binary | subtraction | 9,000 | 12,001 | 0.551 | 0.555 | 0.99 x | left-guided traversal |
| 50% | binary | restriction | 1,536 | 2,017 | 0.032 | 0.046 | 0.71 x | top-level prefix filter |
| 50% | binary | raffination | 22,464 | 29,485 | 0.057 | 0.067 | 0.85 x | complement of the prefix filter |
| 50% | binary | composition | 576,000,000 | 756,031,501 | 10.565 | 9.429 | 1.12 x | no selective consumer; full product is materialized |
| 50% | n-ary | three-way intersection | 12,000 | 15,751 | 0.155 | 0.035 | 4.41 x | evalZ flattens nested intersections and starts from the sparse overlap operand |
| 50% | unary | wrap | 24,000 | 31,502 | 0.005 | 0.006 | 0.86 x | virtual prefix node |
| 50% | unary | unwrap | 24,000 | 31,501 | 0.005 | 0.005 | 1.08 x | descend through virtual prefix |
| 50% | unary | tails-union | 18,004 | 22,506 | 0.903 | 0.918 | 0.98 x | join every first-level tail |
| 50% | unary | tails-intersection | 4 | 6 | 0.833 | 0.781 | 1.07 x | meet every first-level tail |
| 50% | unary | prefix-closure | 31,500 | 31,501 | 1.231 | 1.220 | 1.01 x | all non-empty prefixes |
| 50% | unary | suffix-closure | 48,008 | 60,010 | 5.134 | 5.005 | 1.03 x | all non-empty suffixes |
| 50% | unary | tails-closure | 48,009 | 60,010 | 5.640 | 5.372 | 1.05 x | epsilon plus suffix closure |
| 50% | range | first(512) | 512 | 673 | 0.012 | 0.013 | 0.87 x | ordered border slice |
| 50% | range | last(512) | 512 | 673 | 0.010 | 0.013 | 0.81 x | ordered border slice |
| 50% | combination | union then exact intersection | 1 | 4 | 0.146 | 0.146 | 1.00 x | selective consumer over a virtual union |
| 50% | combination | diff then restriction | 552 | 737 | 0.173 | 0.047 | 3.68 x | left-guided diff under prefix filter |
| 50% | combination | product exact intersection | 1 | 7 | 9.849 | 3.120 | 3.16 x | selector should avoid the full product |
| 50% | combination | product prefix restriction | 24,000 | 31,504 | 6.162 | 2.413 | 2.55 x | one product row selected by prefix |
| 50% | combination | tails of restricted union | 1,708 | 2,136 | 0.248 | 0.151 | 1.64 x | join tails after a logical prefix filter |
| 50% | combination | range of union | 512 | 673 | 0.234 | 2.100 | 0.11 x | border slice over a virtual union |
| 90% | binary | union | 25,800 | 33,751 | 0.083 | 0.063 | 1.31 x | merge two aligned huge tries |
| 90% | binary | intersection | 22,200 | 29,251 | 0.055 | 0.057 | 0.97 x | walk only common child keys |
| 90% | binary | subtraction | 1,800 | 2,401 | 0.053 | 0.055 | 0.95 x | left-guided traversal |
| 90% | binary | restriction | 1,536 | 2,017 | 0.042 | 0.038 | 1.10 x | top-level prefix filter |
| 90% | binary | raffination | 22,464 | 29,485 | 0.065 | 0.060 | 1.09 x | complement of the prefix filter |
| 90% | binary | composition | 576,000,000 | 756,031,501 | 10.274 | 5.943 | 1.73 x | no selective consumer; full product is materialized |
| 90% | n-ary | three-way intersection | 21,600 | 28,351 | 0.075 | 0.048 | 1.58 x | evalZ flattens nested intersections and starts from the sparse overlap operand |
| 90% | unary | wrap | 24,000 | 31,502 | 0.003 | 0.004 | 0.78 x | virtual prefix node |
| 90% | unary | unwrap | 24,000 | 31,501 | 0.003 | 0.003 | 0.96 x | descend through virtual prefix |
| 90% | unary | tails-union | 18,004 | 22,506 | 0.707 | 0.713 | 0.99 x | join every first-level tail |
| 90% | unary | tails-intersection | 4 | 6 | 0.725 | 0.663 | 1.09 x | meet every first-level tail |
| 90% | unary | prefix-closure | 31,500 | 31,501 | 1.306 | 1.097 | 1.19 x | all non-empty prefixes |
| 90% | unary | suffix-closure | 48,008 | 60,010 | 5.118 | 4.976 | 1.03 x | all non-empty suffixes |
| 90% | unary | tails-closure | 48,009 | 60,010 | 4.883 | 4.905 | 1.00 x | epsilon plus suffix closure |
| 90% | range | first(512) | 512 | 673 | 0.015 | 0.020 | 0.75 x | ordered border slice |
| 90% | range | last(512) | 512 | 673 | 0.013 | 0.011 | 1.10 x | ordered border slice |
| 90% | combination | union then exact intersection | 1 | 4 | 0.052 | 0.069 | 0.75 x | selective consumer over a virtual union |
| 90% | combination | diff then restriction | 72 | 97 | 0.059 | 0.018 | 3.18 x | left-guided diff under prefix filter |
| 90% | combination | product exact intersection | 1 | 7 | 6.244 | 2.326 | 2.68 x | selector should avoid the full product |
| 90% | combination | product prefix restriction | 24,000 | 31,504 | 6.589 | 2.292 | 2.87 x | one product row selected by prefix |
| 90% | combination | tails of restricted union | 1,228 | 1,536 | 0.111 | 0.084 | 1.32 x | join tails after a logical prefix filter |
| 90% | combination | range of union | 512 | 673 | 0.127 | 0.422 | 0.30 x | border slice over a virtual union |

Ratios above `1.00 x` mean the zipper evaluator is faster. Direct `composition` intentionally has no selective consumer, so it is a useful overhead baseline rather than the expected zipper win. Selective product rows exercise composition without forcing the full product before the final materialization boundary.
