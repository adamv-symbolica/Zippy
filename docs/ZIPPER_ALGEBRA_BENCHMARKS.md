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
| 1% | binary | union | 41,820 | 53,776 | 4.772 | 3.255 | 1.47 x | merge two aligned huge tries |
| 1% | binary | intersection | 6,180 | 9,226 | 1.963 | 1.841 | 1.07 x | walk only common child keys |
| 1% | binary | subtraction | 17,820 | 23,761 | 0.873 | 0.812 | 1.08 x | left-guided traversal |
| 1% | binary | restriction | 1,536 | 2,017 | 0.178 | 0.223 | 0.80 x | top-level prefix filter |
| 1% | binary | raffination | 22,464 | 29,485 | 0.461 | 0.453 | 1.02 x | complement of the prefix filter |
| 1% | binary | composition | 576,000,000 | 756,031,501 | 11.551 | 9.155 | 1.26 x | no selective consumer; full product is materialized |
| 1% | n-ary | three-way intersection | 240 | 316 | 8.487 | 0.058 | 145.69 x | evalZ flattens nested intersections and starts from the sparse overlap operand |
| 1% | unary | wrap | 24,000 | 31,502 | 0.007 | 0.013 | 0.53 x | virtual prefix node |
| 1% | unary | unwrap | 24,000 | 31,501 | 0.008 | 0.006 | 1.28 x | descend through virtual prefix |
| 1% | unary | tails-union | 18,004 | 22,506 | 3.093 | 1.713 | 1.81 x | join every first-level tail |
| 1% | unary | tails-intersection | 4 | 6 | 2.823 | 3.726 | 0.76 x | meet every first-level tail |
| 1% | unary | prefix-closure | 31,500 | 31,501 | 2.112 | 1.999 | 1.06 x | all non-empty prefixes |
| 1% | unary | suffix-closure | 48,008 | 60,010 | 10.457 | 5.186 | 2.02 x | all non-empty suffixes |
| 1% | unary | tails-closure | 48,009 | 60,010 | 4.818 | 5.603 | 0.86 x | epsilon plus suffix closure |
| 1% | range | first(512) | 512 | 673 | 0.043 | 0.023 | 1.83 x | ordered border slice |
| 1% | range | last(512) | 512 | 673 | 0.013 | 0.013 | 1.02 x | ordered border slice |
| 1% | combination | union then exact intersection | 1 | 4 | 0.294 | 0.305 | 0.96 x | selective consumer over a virtual union |
| 1% | combination | diff then restriction | 1,140 | 1,521 | 0.879 | 0.162 | 5.43 x | left-guided diff under prefix filter |
| 1% | combination | product exact intersection | 1 | 7 | 5.467 | 2.899 | 1.89 x | selector should avoid the full product |
| 1% | combination | product prefix restriction | 24,000 | 31,504 | 5.317 | 2.136 | 2.49 x | one product row selected by prefix |
| 1% | combination | tails of restricted union | 2,296 | 2,871 | 0.535 | 0.256 | 2.09 x | join tails after a logical prefix filter |
| 1% | combination | range of union | 512 | 666 | 0.455 | 3.614 | 0.13 x | border slice over a virtual union |
| 50% | binary | union | 33,000 | 42,751 | 0.148 | 0.151 | 0.98 x | merge two aligned huge tries |
| 50% | binary | intersection | 15,000 | 20,251 | 0.358 | 0.372 | 0.96 x | walk only common child keys |
| 50% | binary | subtraction | 9,000 | 12,001 | 0.687 | 1.690 | 0.41 x | left-guided traversal |
| 50% | binary | restriction | 1,536 | 2,017 | 0.108 | 0.036 | 3.02 x | top-level prefix filter |
| 50% | binary | raffination | 22,464 | 29,485 | 0.192 | 0.145 | 1.32 x | complement of the prefix filter |
| 50% | binary | composition | 576,000,000 | 756,031,501 | 5.355 | 5.286 | 1.01 x | no selective consumer; full product is materialized |
| 50% | n-ary | three-way intersection | 12,000 | 15,751 | 0.125 | 0.028 | 4.43 x | evalZ flattens nested intersections and starts from the sparse overlap operand |
| 50% | unary | wrap | 24,000 | 31,502 | 0.003 | 0.004 | 0.75 x | virtual prefix node |
| 50% | unary | unwrap | 24,000 | 31,501 | 0.005 | 0.004 | 1.23 x | descend through virtual prefix |
| 50% | unary | tails-union | 18,004 | 22,506 | 0.659 | 0.672 | 0.98 x | join every first-level tail |
| 50% | unary | tails-intersection | 4 | 6 | 0.613 | 0.608 | 1.01 x | meet every first-level tail |
| 50% | unary | prefix-closure | 31,500 | 31,501 | 1.143 | 1.125 | 1.02 x | all non-empty prefixes |
| 50% | unary | suffix-closure | 48,008 | 60,010 | 4.607 | 5.773 | 0.80 x | all non-empty suffixes |
| 50% | unary | tails-closure | 48,009 | 60,010 | 4.688 | 4.665 | 1.01 x | epsilon plus suffix closure |
| 50% | range | first(512) | 512 | 673 | 0.010 | 0.013 | 0.73 x | ordered border slice |
| 50% | range | last(512) | 512 | 673 | 0.009 | 0.010 | 0.86 x | ordered border slice |
| 50% | combination | union then exact intersection | 1 | 4 | 0.141 | 0.153 | 0.92 x | selective consumer over a virtual union |
| 50% | combination | diff then restriction | 552 | 737 | 0.165 | 0.045 | 3.70 x | left-guided diff under prefix filter |
| 50% | combination | product exact intersection | 1 | 7 | 5.305 | 2.250 | 2.36 x | selector should avoid the full product |
| 50% | combination | product prefix restriction | 24,000 | 31,504 | 5.306 | 2.143 | 2.48 x | one product row selected by prefix |
| 50% | combination | tails of restricted union | 1,708 | 2,136 | 0.230 | 0.102 | 2.26 x | join tails after a logical prefix filter |
| 50% | combination | range of union | 512 | 673 | 0.244 | 0.963 | 0.25 x | border slice over a virtual union |
| 90% | binary | union | 25,800 | 33,751 | 0.049 | 0.050 | 0.98 x | merge two aligned huge tries |
| 90% | binary | intersection | 22,200 | 29,251 | 0.046 | 0.200 | 0.23 x | walk only common child keys |
| 90% | binary | subtraction | 1,800 | 2,401 | 0.079 | 0.053 | 1.50 x | left-guided traversal |
| 90% | binary | restriction | 1,536 | 2,017 | 0.011 | 0.026 | 0.43 x | top-level prefix filter |
| 90% | binary | raffination | 22,464 | 29,485 | 0.098 | 0.091 | 1.08 x | complement of the prefix filter |
| 90% | binary | composition | 576,000,000 | 756,031,501 | 5.336 | 5.388 | 0.99 x | no selective consumer; full product is materialized |
| 90% | n-ary | three-way intersection | 21,600 | 28,351 | 0.104 | 0.036 | 2.87 x | evalZ flattens nested intersections and starts from the sparse overlap operand |
| 90% | unary | wrap | 24,000 | 31,502 | 0.002 | 0.003 | 0.76 x | virtual prefix node |
| 90% | unary | unwrap | 24,000 | 31,501 | 0.002 | 0.002 | 0.97 x | descend through virtual prefix |
| 90% | unary | tails-union | 18,004 | 22,506 | 0.666 | 0.684 | 0.97 x | join every first-level tail |
| 90% | unary | tails-intersection | 4 | 6 | 0.582 | 0.577 | 1.01 x | meet every first-level tail |
| 90% | unary | prefix-closure | 31,500 | 31,501 | 1.169 | 1.116 | 1.05 x | all non-empty prefixes |
| 90% | unary | suffix-closure | 48,008 | 60,010 | 4.696 | 4.660 | 1.01 x | all non-empty suffixes |
| 90% | unary | tails-closure | 48,009 | 60,010 | 4.784 | 4.722 | 1.01 x | epsilon plus suffix closure |
| 90% | range | first(512) | 512 | 673 | 0.008 | 0.010 | 0.77 x | ordered border slice |
| 90% | range | last(512) | 512 | 673 | 0.008 | 0.008 | 1.02 x | ordered border slice |
| 90% | combination | union then exact intersection | 1 | 4 | 0.048 | 0.066 | 0.73 x | selective consumer over a virtual union |
| 90% | combination | diff then restriction | 72 | 97 | 0.052 | 0.014 | 3.64 x | left-guided diff under prefix filter |
| 90% | combination | product exact intersection | 1 | 7 | 5.388 | 2.122 | 2.54 x | selector should avoid the full product |
| 90% | combination | product prefix restriction | 24,000 | 31,504 | 5.370 | 2.102 | 2.55 x | one product row selected by prefix |
| 90% | combination | tails of restricted union | 1,228 | 1,536 | 0.209 | 0.065 | 3.22 x | join tails after a logical prefix filter |
| 90% | combination | range of union | 512 | 673 | 0.117 | 0.403 | 0.29 x | border slice over a virtual union |

Ratios above `1.00 x` mean the zipper evaluator is faster. Direct `composition` intentionally has no selective consumer, so it is a useful overhead baseline rather than the expected zipper win. Selective product rows exercise composition without forcing the full product before the final materialization boundary.
