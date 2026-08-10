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
| 1% | binary | union | 41,820 | 53,776 | 18.174 | 15.037 | 1.21 x | merge two aligned huge tries |
| 1% | binary | intersection | 6,180 | 9,226 | 11.327 | 9.180 | 1.23 x | walk only common child keys |
| 1% | binary | subtraction | 17,820 | 23,761 | 5.294 | 4.169 | 1.27 x | left-guided traversal |
| 1% | binary | restriction | 1,536 | 2,017 | 0.510 | 0.510 | 1.00 x | top-level prefix filter |
| 1% | binary | raffination | 22,464 | 29,485 | 0.367 | 0.690 | 0.53 x | complement of the prefix filter |
| 1% | binary | composition | 576,000,000 | 756,031,501 | 14.162 | 21.038 | 0.67 x | no selective consumer; full product is materialized |
| 1% | n-ary | three-way intersection | 240 | 316 | 16.486 | 0.090 | 183.35 x | evalZ flattens nested intersections and starts from the sparse overlap operand |
| 1% | unary | wrap | 24,000 | 31,502 | 0.008 | 0.010 | 0.82 x | virtual prefix node |
| 1% | unary | unwrap | 24,000 | 31,501 | 0.010 | 0.008 | 1.28 x | descend through virtual prefix |
| 1% | unary | tails-union | 18,004 | 22,506 | 9.128 | 9.458 | 0.97 x | join every first-level tail |
| 1% | unary | tails-intersection | 4 | 6 | 8.397 | 10.594 | 0.79 x | meet every first-level tail |
| 1% | unary | prefix-closure | 31,500 | 31,501 | 9.103 | 8.357 | 1.09 x | all non-empty prefixes |
| 1% | unary | suffix-closure | 48,008 | 60,010 | 25.261 | 29.284 | 0.86 x | all non-empty suffixes |
| 1% | unary | tails-closure | 48,009 | 60,010 | 17.409 | 19.542 | 0.89 x | epsilon plus suffix closure |
| 1% | range | first(512) | 512 | 673 | 0.019 | 0.020 | 0.98 x | ordered border slice |
| 1% | range | last(512) | 512 | 673 | 0.020 | 0.020 | 1.02 x | ordered border slice |
| 1% | combination | union then exact intersection | 1 | 4 | 3.005 | 2.552 | 1.18 x | selective consumer over a virtual union |
| 1% | combination | diff then restriction | 1,140 | 1,521 | 1.599 | 0.136 | 11.76 x | left-guided diff under prefix filter |
| 1% | combination | product exact intersection | 1 | 7 | 5.514 | 9.714 | 0.57 x | selector should avoid the full product |
| 1% | combination | product prefix restriction | 24,000 | 31,504 | 5.043 | 7.340 | 0.69 x | one product row selected by prefix |
| 1% | combination | tails of restricted union | 2,296 | 2,871 | 2.774 | 0.584 | 4.75 x | join tails after a logical prefix filter |
| 1% | combination | range of union | 512 | 666 | 2.646 | 6.207 | 0.43 x | border slice over a virtual union |
| 50% | binary | union | 33,000 | 42,751 | 2.334 | 2.519 | 0.93 x | merge two aligned huge tries |
| 50% | binary | intersection | 15,000 | 20,251 | 1.559 | 1.550 | 1.01 x | walk only common child keys |
| 50% | binary | subtraction | 9,000 | 12,001 | 0.638 | 0.614 | 1.04 x | left-guided traversal |
| 50% | binary | restriction | 1,536 | 2,017 | 0.046 | 0.044 | 1.03 x | top-level prefix filter |
| 50% | binary | raffination | 22,464 | 29,485 | 0.159 | 0.145 | 1.09 x | complement of the prefix filter |
| 50% | binary | composition | 576,000,000 | 756,031,501 | 9.709 | 4.731 | 2.05 x | no selective consumer; full product is materialized |
| 50% | n-ary | three-way intersection | 12,000 | 15,751 | 0.584 | 0.046 | 12.68 x | evalZ flattens nested intersections and starts from the sparse overlap operand |
| 50% | unary | wrap | 24,000 | 31,502 | 0.004 | 0.005 | 0.81 x | virtual prefix node |
| 50% | unary | unwrap | 24,000 | 31,501 | 0.004 | 0.004 | 0.94 x | descend through virtual prefix |
| 50% | unary | tails-union | 18,004 | 22,506 | 21.621 | 7.468 | 2.90 x | join every first-level tail |
| 50% | unary | tails-intersection | 4 | 6 | 0.867 | 1.645 | 0.53 x | meet every first-level tail |
| 50% | unary | prefix-closure | 31,500 | 31,501 | 4.810 | 2.321 | 2.07 x | all non-empty prefixes |
| 50% | unary | suffix-closure | 48,008 | 60,010 | 20.081 | 13.995 | 1.43 x | all non-empty suffixes |
| 50% | unary | tails-closure | 48,009 | 60,010 | 16.384 | 20.124 | 0.81 x | epsilon plus suffix closure |
| 50% | range | first(512) | 512 | 673 | 0.013 | 0.018 | 0.73 x | ordered border slice |
| 50% | range | last(512) | 512 | 673 | 0.012 | 0.013 | 0.93 x | ordered border slice |
| 50% | combination | union then exact intersection | 1 | 4 | 0.797 | 0.819 | 0.97 x | selective consumer over a virtual union |
| 50% | combination | diff then restriction | 552 | 737 | 0.424 | 0.078 | 5.46 x | left-guided diff under prefix filter |
| 50% | combination | product exact intersection | 1 | 7 | 4.805 | 3.930 | 1.22 x | selector should avoid the full product |
| 50% | combination | product prefix restriction | 24,000 | 31,504 | 4.846 | 4.795 | 1.01 x | one product row selected by prefix |
| 50% | combination | tails of restricted union | 1,708 | 2,136 | 1.082 | 0.366 | 2.95 x | join tails after a logical prefix filter |
| 50% | combination | range of union | 512 | 673 | 2.260 | 1.948 | 1.16 x | border slice over a virtual union |
| 90% | binary | union | 25,800 | 33,751 | 0.198 | 0.198 | 1.00 x | merge two aligned huge tries |
| 90% | binary | intersection | 22,200 | 29,251 | 0.094 | 0.111 | 0.85 x | walk only common child keys |
| 90% | binary | subtraction | 1,800 | 2,401 | 0.097 | 0.100 | 0.97 x | left-guided traversal |
| 90% | binary | restriction | 1,536 | 2,017 | 0.029 | 0.028 | 1.01 x | top-level prefix filter |
| 90% | binary | raffination | 22,464 | 29,485 | 0.113 | 0.129 | 0.88 x | complement of the prefix filter |
| 90% | binary | composition | 576,000,000 | 756,031,501 | 4.806 | 8.522 | 0.56 x | no selective consumer; full product is materialized |
| 90% | n-ary | three-way intersection | 21,600 | 28,351 | 0.099 | 0.043 | 2.29 x | evalZ flattens nested intersections and starts from the sparse overlap operand |
| 90% | unary | wrap | 24,000 | 31,502 | 0.003 | 0.004 | 0.67 x | virtual prefix node |
| 90% | unary | unwrap | 24,000 | 31,501 | 0.003 | 0.003 | 0.95 x | descend through virtual prefix |
| 90% | unary | tails-union | 18,004 | 22,506 | 4.669 | 3.581 | 1.30 x | join every first-level tail |
| 90% | unary | tails-intersection | 4 | 6 | 0.672 | 0.661 | 1.02 x | meet every first-level tail |
| 90% | unary | prefix-closure | 31,500 | 31,501 | 2.639 | 2.556 | 1.03 x | all non-empty prefixes |
| 90% | unary | suffix-closure | 48,008 | 60,010 | 14.896 | 17.756 | 0.84 x | all non-empty suffixes |
| 90% | unary | tails-closure | 48,009 | 60,010 | 11.945 | 12.277 | 0.97 x | epsilon plus suffix closure |
| 90% | range | first(512) | 512 | 673 | 0.010 | 0.012 | 0.83 x | ordered border slice |
| 90% | range | last(512) | 512 | 673 | 0.011 | 0.013 | 0.84 x | ordered border slice |
| 90% | combination | union then exact intersection | 1 | 4 | 0.178 | 0.202 | 0.88 x | selective consumer over a virtual union |
| 90% | combination | diff then restriction | 72 | 97 | 0.083 | 0.032 | 2.59 x | left-guided diff under prefix filter |
| 90% | combination | product exact intersection | 1 | 7 | 4.623 | 3.344 | 1.38 x | selector should avoid the full product |
| 90% | combination | product prefix restriction | 24,000 | 31,504 | 6.781 | 3.014 | 2.25 x | one product row selected by prefix |
| 90% | combination | tails of restricted union | 1,228 | 1,536 | 0.353 | 0.215 | 1.64 x | join tails after a logical prefix filter |
| 90% | combination | range of union | 512 | 673 | 0.332 | 0.472 | 0.70 x | border slice over a virtual union |

Ratios above `1.00 x` mean the zipper evaluator is faster. Direct `composition` intentionally has no selective consumer, so it is a useful overhead baseline rather than the expected zipper win. Selective product rows exercise composition without forcing the full product before the final materialization boundary.
