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
| 1% | binary | union | 41,820 | 53,776 | 4.451 | 4.719 | 0.94 x | merge two aligned huge tries |
| 1% | binary | intersection | 6,180 | 9,226 | 1.905 | 1.801 | 1.06 x | walk only common child keys |
| 1% | binary | subtraction | 17,820 | 23,761 | 0.909 | 1.858 | 0.49 x | left-guided traversal |
| 1% | binary | restriction | 1,536 | 2,017 | 0.186 | 0.220 | 0.84 x | top-level prefix filter |
| 1% | binary | raffination | 22,464 | 29,485 | 0.270 | 0.173 | 1.57 x | complement of the prefix filter |
| 1% | binary | composition | 576,000,000 | 756,031,501 | 15.660 | 5.594 | 2.80 x | no selective consumer; full product is materialized |
| 1% | n-ary | three-way intersection | 240 | 316 | 1.930 | 0.032 | 59.83 x | evalZ flattens nested intersections and starts from the sparse overlap operand |
| 1% | unary | wrap | 24,000 | 31,502 | 0.007 | 0.012 | 0.61 x | virtual prefix node |
| 1% | unary | unwrap | 24,000 | 31,501 | 0.008 | 0.007 | 1.16 x | descend through virtual prefix |
| 1% | unary | tails-union | 18,004 | 22,506 | 2.991 | 1.658 | 1.80 x | join every first-level tail |
| 1% | unary | tails-intersection | 4 | 6 | 1.971 | 1.881 | 1.05 x | meet every first-level tail |
| 1% | unary | prefix-closure | 31,500 | 31,501 | 4.534 | 3.050 | 1.49 x | all non-empty prefixes |
| 1% | unary | suffix-closure | 48,008 | 60,010 | 49.317 | 8.532 | 5.78 x | all non-empty suffixes |
| 1% | unary | tails-closure | 48,009 | 60,010 | 6.294 | 6.053 | 1.04 x | epsilon plus suffix closure |
| 1% | range | first(512) | 512 | 673 | 0.021 | 0.025 | 0.85 x | ordered border slice |
| 1% | range | last(512) | 512 | 673 | 0.038 | 0.022 | 1.73 x | ordered border slice |
| 1% | combination | union then exact intersection | 1 | 4 | 0.727 | 0.772 | 0.94 x | selective consumer over a virtual union |
| 1% | combination | diff then restriction | 1,140 | 1,521 | 0.724 | 0.209 | 3.46 x | left-guided diff under prefix filter |
| 1% | combination | product exact intersection | 1 | 7 | 10.851 | 4.808 | 2.26 x | selector should avoid the full product |
| 1% | combination | product prefix restriction | 24,000 | 31,504 | 7.213 | 3.310 | 2.18 x | one product row selected by prefix |
| 1% | combination | tails of restricted union | 2,296 | 2,871 | 1.446 | 0.586 | 2.47 x | join tails after a logical prefix filter |
| 1% | combination | range of union | 512 | 666 | 0.472 | 7.522 | 0.06 x | border slice over a virtual union |
| 50% | binary | union | 33,000 | 42,751 | 0.441 | 0.394 | 1.12 x | merge two aligned huge tries |
| 50% | binary | intersection | 15,000 | 20,251 | 0.319 | 0.347 | 0.92 x | walk only common child keys |
| 50% | binary | subtraction | 9,000 | 12,001 | 1.265 | 1.205 | 1.05 x | left-guided traversal |
| 50% | binary | restriction | 1,536 | 2,017 | 0.088 | 0.069 | 1.28 x | top-level prefix filter |
| 50% | binary | raffination | 22,464 | 29,485 | 0.150 | 0.146 | 1.03 x | complement of the prefix filter |
| 50% | binary | composition | 576,000,000 | 756,031,501 | 10.894 | 12.512 | 0.87 x | no selective consumer; full product is materialized |
| 50% | n-ary | three-way intersection | 12,000 | 15,751 | 0.135 | 0.033 | 4.12 x | evalZ flattens nested intersections and starts from the sparse overlap operand |
| 50% | unary | wrap | 24,000 | 31,502 | 0.003 | 0.004 | 0.66 x | virtual prefix node |
| 50% | unary | unwrap | 24,000 | 31,501 | 0.003 | 0.005 | 0.63 x | descend through virtual prefix |
| 50% | unary | tails-union | 18,004 | 22,506 | 1.863 | 1.506 | 1.24 x | join every first-level tail |
| 50% | unary | tails-intersection | 4 | 6 | 1.247 | 1.096 | 1.14 x | meet every first-level tail |
| 50% | unary | prefix-closure | 31,500 | 31,501 | 1.134 | 1.125 | 1.01 x | all non-empty prefixes |
| 50% | unary | suffix-closure | 48,008 | 60,010 | 7.141 | 4.736 | 1.51 x | all non-empty suffixes |
| 50% | unary | tails-closure | 48,009 | 60,010 | 5.778 | 6.033 | 0.96 x | epsilon plus suffix closure |
| 50% | range | first(512) | 512 | 673 | 0.010 | 0.016 | 0.64 x | ordered border slice |
| 50% | range | last(512) | 512 | 673 | 0.008 | 0.013 | 0.66 x | ordered border slice |
| 50% | combination | union then exact intersection | 1 | 4 | 0.141 | 0.156 | 0.90 x | selective consumer over a virtual union |
| 50% | combination | diff then restriction | 552 | 737 | 0.165 | 0.045 | 3.65 x | left-guided diff under prefix filter |
| 50% | combination | product exact intersection | 1 | 7 | 8.510 | 2.700 | 3.15 x | selector should avoid the full product |
| 50% | combination | product prefix restriction | 24,000 | 31,504 | 6.355 | 2.449 | 2.60 x | one product row selected by prefix |
| 50% | combination | tails of restricted union | 1,708 | 2,136 | 0.216 | 0.097 | 2.22 x | join tails after a logical prefix filter |
| 50% | combination | range of union | 512 | 673 | 0.216 | 0.985 | 0.22 x | border slice over a virtual union |
| 90% | binary | union | 25,800 | 33,751 | 0.049 | 0.050 | 0.98 x | merge two aligned huge tries |
| 90% | binary | intersection | 22,200 | 29,251 | 0.043 | 0.064 | 0.68 x | walk only common child keys |
| 90% | binary | subtraction | 1,800 | 2,401 | 0.052 | 0.052 | 0.99 x | left-guided traversal |
| 90% | binary | restriction | 1,536 | 2,017 | 0.019 | 0.019 | 0.98 x | top-level prefix filter |
| 90% | binary | raffination | 22,464 | 29,485 | 0.035 | 0.033 | 1.06 x | complement of the prefix filter |
| 90% | binary | composition | 576,000,000 | 756,031,501 | 5.714 | 8.004 | 0.71 x | no selective consumer; full product is materialized |
| 90% | n-ary | three-way intersection | 21,600 | 28,351 | 0.078 | 0.051 | 1.52 x | evalZ flattens nested intersections and starts from the sparse overlap operand |
| 90% | unary | wrap | 24,000 | 31,502 | 0.002 | 0.003 | 0.78 x | virtual prefix node |
| 90% | unary | unwrap | 24,000 | 31,501 | 0.002 | 0.002 | 0.93 x | descend through virtual prefix |
| 90% | unary | tails-union | 18,004 | 22,506 | 0.997 | 1.024 | 0.97 x | join every first-level tail |
| 90% | unary | tails-intersection | 4 | 6 | 0.644 | 0.661 | 0.97 x | meet every first-level tail |
| 90% | unary | prefix-closure | 31,500 | 31,501 | 1.219 | 1.115 | 1.09 x | all non-empty prefixes |
| 90% | unary | suffix-closure | 48,008 | 60,010 | 5.873 | 4.868 | 1.21 x | all non-empty suffixes |
| 90% | unary | tails-closure | 48,009 | 60,010 | 5.782 | 4.740 | 1.22 x | epsilon plus suffix closure |
| 90% | range | first(512) | 512 | 673 | 0.008 | 0.010 | 0.80 x | ordered border slice |
| 90% | range | last(512) | 512 | 673 | 0.008 | 0.007 | 1.03 x | ordered border slice |
| 90% | combination | union then exact intersection | 1 | 4 | 0.046 | 0.715 | 0.06 x | selective consumer over a virtual union |
| 90% | combination | diff then restriction | 72 | 97 | 0.053 | 0.015 | 3.54 x | left-guided diff under prefix filter |
| 90% | combination | product exact intersection | 1 | 7 | 5.458 | 2.404 | 2.27 x | selector should avoid the full product |
| 90% | combination | product prefix restriction | 24,000 | 31,504 | 5.424 | 2.381 | 2.28 x | one product row selected by prefix |
| 90% | combination | tails of restricted union | 1,228 | 1,536 | 0.184 | 0.071 | 2.58 x | join tails after a logical prefix filter |
| 90% | combination | range of union | 512 | 673 | 0.125 | 0.430 | 0.29 x | border slice over a virtual union |

Ratios above `1.00 x` mean the zipper evaluator is faster. Direct `composition` intentionally has no selective consumer, so it is a useful overhead baseline rather than the expected zipper win. Selective product rows exercise composition without forcing the full product before the final materialization boundary.
