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
| 1% | binary | union | 41,820 | 53,776 | 3.091 | 3.653 | 0.85 x | merge two aligned huge tries |
| 1% | binary | intersection | 6,180 | 9,226 | 1.726 | 1.697 | 1.02 x | walk only common child keys |
| 1% | binary | subtraction | 17,820 | 23,761 | 0.898 | 0.768 | 1.17 x | left-guided traversal |
| 1% | binary | restriction | 1,536 | 2,017 | 0.180 | 0.206 | 0.87 x | top-level prefix filter |
| 1% | binary | raffination | 22,464 | 29,485 | 0.323 | 0.192 | 1.68 x | complement of the prefix filter |
| 1% | binary | composition | 576,000,000 | 756,031,501 | 10.475 | 5.440 | 1.93 x | no selective consumer; full product is materialized |
| 1% | n-ary | three-way intersection | 240 | 316 | 5.564 | 0.057 | 96.98 x | evalZ flattens nested intersections and starts from the sparse overlap operand |
| 1% | unary | wrap | 24,000 | 31,502 | 0.007 | 0.008 | 0.82 x | virtual prefix node |
| 1% | unary | unwrap | 24,000 | 31,501 | 0.007 | 0.006 | 1.05 x | descend through virtual prefix |
| 1% | unary | tails-union | 18,004 | 22,506 | 1.582 | 1.550 | 1.02 x | join every first-level tail |
| 1% | unary | tails-intersection | 4 | 6 | 1.844 | 1.691 | 1.09 x | meet every first-level tail |
| 1% | unary | prefix-closure | 31,500 | 31,501 | 1.643 | 1.186 | 1.39 x | all non-empty prefixes |
| 1% | unary | suffix-closure | 48,008 | 60,010 | 7.768 | 6.981 | 1.11 x | all non-empty suffixes |
| 1% | unary | tails-closure | 48,009 | 60,010 | 11.793 | 5.054 | 2.33 x | epsilon plus suffix closure |
| 1% | range | first(512) | 512 | 673 | 0.013 | 0.014 | 0.93 x | ordered border slice |
| 1% | range | last(512) | 512 | 673 | 0.013 | 0.012 | 1.12 x | ordered border slice |
| 1% | combination | union then exact intersection | 1 | 4 | 0.241 | 0.255 | 0.94 x | selective consumer over a virtual union |
| 1% | combination | diff then restriction | 1,140 | 1,521 | 0.860 | 0.154 | 5.59 x | left-guided diff under prefix filter |
| 1% | combination | product exact intersection | 1 | 7 | 6.187 | 2.663 | 2.32 x | selector should avoid the full product |
| 1% | combination | product prefix restriction | 24,000 | 31,504 | 6.012 | 2.045 | 2.94 x | one product row selected by prefix |
| 1% | combination | tails of restricted union | 2,296 | 2,871 | 0.516 | 0.260 | 1.99 x | join tails after a logical prefix filter |
| 1% | combination | range of union | 512 | 666 | 0.403 | 3.169 | 0.13 x | border slice over a virtual union |
| 50% | binary | union | 33,000 | 42,751 | 0.141 | 0.144 | 0.98 x | merge two aligned huge tries |
| 50% | binary | intersection | 15,000 | 20,251 | 0.116 | 0.124 | 0.93 x | walk only common child keys |
| 50% | binary | subtraction | 9,000 | 12,001 | 0.440 | 0.442 | 1.00 x | left-guided traversal |
| 50% | binary | restriction | 1,536 | 2,017 | 0.035 | 0.033 | 1.06 x | top-level prefix filter |
| 50% | binary | raffination | 22,464 | 29,485 | 0.060 | 0.059 | 1.02 x | complement of the prefix filter |
| 50% | binary | composition | 576,000,000 | 756,031,501 | 5.382 | 5.361 | 1.00 x | no selective consumer; full product is materialized |
| 50% | n-ary | three-way intersection | 12,000 | 15,751 | 0.117 | 0.034 | 3.50 x | evalZ flattens nested intersections and starts from the sparse overlap operand |
| 50% | unary | wrap | 24,000 | 31,502 | 0.003 | 0.004 | 0.68 x | virtual prefix node |
| 50% | unary | unwrap | 24,000 | 31,501 | 0.003 | 0.004 | 0.79 x | descend through virtual prefix |
| 50% | unary | tails-union | 18,004 | 22,506 | 0.651 | 0.686 | 0.95 x | join every first-level tail |
| 50% | unary | tails-intersection | 4 | 6 | 0.618 | 0.605 | 1.02 x | meet every first-level tail |
| 50% | unary | prefix-closure | 31,500 | 31,501 | 1.140 | 1.079 | 1.06 x | all non-empty prefixes |
| 50% | unary | suffix-closure | 48,008 | 60,010 | 4.227 | 4.201 | 1.01 x | all non-empty suffixes |
| 50% | unary | tails-closure | 48,009 | 60,010 | 4.670 | 4.431 | 1.05 x | epsilon plus suffix closure |
| 50% | range | first(512) | 512 | 673 | 0.013 | 0.013 | 1.03 x | ordered border slice |
| 50% | range | last(512) | 512 | 673 | 0.009 | 0.015 | 0.59 x | ordered border slice |
| 50% | combination | union then exact intersection | 1 | 4 | 0.127 | 0.140 | 0.91 x | selective consumer over a virtual union |
| 50% | combination | diff then restriction | 552 | 737 | 0.165 | 0.045 | 3.64 x | left-guided diff under prefix filter |
| 50% | combination | product exact intersection | 1 | 7 | 5.255 | 2.134 | 2.46 x | selector should avoid the full product |
| 50% | combination | product prefix restriction | 24,000 | 31,504 | 5.273 | 2.123 | 2.48 x | one product row selected by prefix |
| 50% | combination | tails of restricted union | 1,708 | 2,136 | 0.208 | 0.099 | 2.09 x | join tails after a logical prefix filter |
| 50% | combination | range of union | 512 | 673 | 0.211 | 0.892 | 0.24 x | border slice over a virtual union |
| 90% | binary | union | 25,800 | 33,751 | 0.048 | 0.049 | 1.00 x | merge two aligned huge tries |
| 90% | binary | intersection | 22,200 | 29,251 | 0.044 | 0.061 | 0.73 x | walk only common child keys |
| 90% | binary | subtraction | 1,800 | 2,401 | 0.054 | 0.054 | 0.99 x | left-guided traversal |
| 90% | binary | restriction | 1,536 | 2,017 | 0.020 | 0.021 | 0.96 x | top-level prefix filter |
| 90% | binary | raffination | 22,464 | 29,485 | 0.034 | 0.042 | 0.81 x | complement of the prefix filter |
| 90% | binary | composition | 576,000,000 | 756,031,501 | 5.255 | 5.214 | 1.01 x | no selective consumer; full product is materialized |
| 90% | n-ary | three-way intersection | 21,600 | 28,351 | 0.056 | 0.050 | 1.13 x | evalZ flattens nested intersections and starts from the sparse overlap operand |
| 90% | unary | wrap | 24,000 | 31,502 | 0.002 | 0.004 | 0.56 x | virtual prefix node |
| 90% | unary | unwrap | 24,000 | 31,501 | 0.002 | 0.003 | 0.91 x | descend through virtual prefix |
| 90% | unary | tails-union | 18,004 | 22,506 | 0.636 | 0.646 | 0.98 x | join every first-level tail |
| 90% | unary | tails-intersection | 4 | 6 | 0.577 | 0.587 | 0.98 x | meet every first-level tail |
| 90% | unary | prefix-closure | 31,500 | 31,501 | 1.073 | 1.125 | 0.95 x | all non-empty prefixes |
| 90% | unary | suffix-closure | 48,008 | 60,010 | 4.693 | 4.838 | 0.97 x | all non-empty suffixes |
| 90% | unary | tails-closure | 48,009 | 60,010 | 4.742 | 4.903 | 0.97 x | epsilon plus suffix closure |
| 90% | range | first(512) | 512 | 673 | 0.008 | 0.010 | 0.77 x | ordered border slice |
| 90% | range | last(512) | 512 | 673 | 0.006 | 0.007 | 0.95 x | ordered border slice |
| 90% | combination | union then exact intersection | 1 | 4 | 0.048 | 0.081 | 0.59 x | selective consumer over a virtual union |
| 90% | combination | diff then restriction | 72 | 97 | 0.053 | 0.014 | 3.78 x | left-guided diff under prefix filter |
| 90% | combination | product exact intersection | 1 | 7 | 5.339 | 2.194 | 2.43 x | selector should avoid the full product |
| 90% | combination | product prefix restriction | 24,000 | 31,504 | 5.784 | 2.082 | 2.78 x | one product row selected by prefix |
| 90% | combination | tails of restricted union | 1,228 | 1,536 | 0.103 | 0.068 | 1.52 x | join tails after a logical prefix filter |
| 90% | combination | range of union | 512 | 673 | 0.113 | 0.412 | 0.27 x | border slice over a virtual union |

Ratios above `1.00 x` mean the zipper evaluator is faster. Direct `composition` intentionally has no selective consumer, so it is a useful overhead baseline rather than the expected zipper win. Selective product rows exercise composition without forcing the full product before the final materialization boundary.
