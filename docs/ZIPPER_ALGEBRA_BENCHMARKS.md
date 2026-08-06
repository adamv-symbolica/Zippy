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
| 1% | binary | union | 41,820 | 53,776 | 3.173 | 2.053 | 1.55 x | merge two aligned huge tries |
| 1% | binary | intersection | 6,180 | 9,226 | 2.004 | 1.731 | 1.16 x | walk only common child keys |
| 1% | binary | subtraction | 17,820 | 23,761 | 0.848 | 0.770 | 1.10 x | left-guided traversal |
| 1% | binary | restriction | 1,536 | 2,017 | 0.185 | 0.213 | 0.87 x | top-level prefix filter |
| 1% | binary | raffination | 22,464 | 29,485 | 0.361 | 0.183 | 1.97 x | complement of the prefix filter |
| 1% | binary | composition | 576,000,000 | 756,031,501 | 13.113 | 5.448 | 2.41 x | no selective consumer; full product is materialized |
| 1% | n-ary | three-way intersection | 240 | 316 | 5.390 | 0.057 | 93.88 x | evalZ flattens nested intersections and starts from the sparse overlap operand |
| 1% | unary | wrap | 24,000 | 31,502 | 0.007 | 0.009 | 0.82 x | virtual prefix node |
| 1% | unary | unwrap | 24,000 | 31,501 | 0.007 | 0.006 | 1.08 x | descend through virtual prefix |
| 1% | unary | tails-union | 18,004 | 22,506 | 2.422 | 1.661 | 1.46 x | join every first-level tail |
| 1% | unary | tails-intersection | 4 | 6 | 1.858 | 1.707 | 1.09 x | meet every first-level tail |
| 1% | unary | prefix-closure | 31,500 | 31,501 | 1.972 | 1.998 | 0.99 x | all non-empty prefixes |
| 1% | unary | suffix-closure | 48,008 | 60,010 | 7.770 | 10.278 | 0.76 x | all non-empty suffixes |
| 1% | unary | tails-closure | 48,009 | 60,010 | 4.458 | 5.303 | 0.84 x | epsilon plus suffix closure |
| 1% | range | first(512) | 512 | 673 | 0.013 | 0.015 | 0.85 x | ordered border slice |
| 1% | range | last(512) | 512 | 673 | 0.012 | 0.013 | 0.96 x | ordered border slice |
| 1% | combination | union then exact intersection | 1 | 4 | 0.250 | 0.276 | 0.91 x | selective consumer over a virtual union |
| 1% | combination | diff then restriction | 1,140 | 1,521 | 0.871 | 0.157 | 5.54 x | left-guided diff under prefix filter |
| 1% | combination | product exact intersection | 1 | 7 | 5.618 | 3.603 | 1.56 x | selector should avoid the full product |
| 1% | combination | product prefix restriction | 24,000 | 31,504 | 5.318 | 2.039 | 2.61 x | one product row selected by prefix |
| 1% | combination | tails of restricted union | 2,296 | 2,871 | 0.495 | 0.243 | 2.04 x | join tails after a logical prefix filter |
| 1% | combination | range of union | 512 | 666 | 0.408 | 3.090 | 0.13 x | border slice over a virtual union |
| 50% | binary | union | 33,000 | 42,751 | 0.138 | 0.141 | 0.98 x | merge two aligned huge tries |
| 50% | binary | intersection | 15,000 | 20,251 | 0.107 | 0.119 | 0.90 x | walk only common child keys |
| 50% | binary | subtraction | 9,000 | 12,001 | 0.448 | 0.444 | 1.01 x | left-guided traversal |
| 50% | binary | restriction | 1,536 | 2,017 | 0.037 | 0.032 | 1.13 x | top-level prefix filter |
| 50% | binary | raffination | 22,464 | 29,485 | 0.154 | 0.137 | 1.12 x | complement of the prefix filter |
| 50% | binary | composition | 576,000,000 | 756,031,501 | 7.271 | 5.564 | 1.31 x | no selective consumer; full product is materialized |
| 50% | n-ary | three-way intersection | 12,000 | 15,751 | 0.139 | 0.036 | 3.89 x | evalZ flattens nested intersections and starts from the sparse overlap operand |
| 50% | unary | wrap | 24,000 | 31,502 | 0.003 | 0.005 | 0.72 x | virtual prefix node |
| 50% | unary | unwrap | 24,000 | 31,501 | 0.003 | 0.004 | 0.78 x | descend through virtual prefix |
| 50% | unary | tails-union | 18,004 | 22,506 | 0.647 | 0.669 | 0.97 x | join every first-level tail |
| 50% | unary | tails-intersection | 4 | 6 | 0.622 | 0.604 | 1.03 x | meet every first-level tail |
| 50% | unary | prefix-closure | 31,500 | 31,501 | 1.101 | 1.106 | 1.00 x | all non-empty prefixes |
| 50% | unary | suffix-closure | 48,008 | 60,010 | 4.275 | 5.256 | 0.81 x | all non-empty suffixes |
| 50% | unary | tails-closure | 48,009 | 60,010 | 4.260 | 4.268 | 1.00 x | epsilon plus suffix closure |
| 50% | range | first(512) | 512 | 673 | 0.010 | 0.015 | 0.63 x | ordered border slice |
| 50% | range | last(512) | 512 | 673 | 0.008 | 0.013 | 0.68 x | ordered border slice |
| 50% | combination | union then exact intersection | 1 | 4 | 0.137 | 0.150 | 0.92 x | selective consumer over a virtual union |
| 50% | combination | diff then restriction | 552 | 737 | 0.159 | 0.044 | 3.58 x | left-guided diff under prefix filter |
| 50% | combination | product exact intersection | 1 | 7 | 5.335 | 2.156 | 2.47 x | selector should avoid the full product |
| 50% | combination | product prefix restriction | 24,000 | 31,504 | 5.554 | 2.169 | 2.56 x | one product row selected by prefix |
| 50% | combination | tails of restricted union | 1,708 | 2,136 | 0.210 | 0.163 | 1.29 x | join tails after a logical prefix filter |
| 50% | combination | range of union | 512 | 673 | 0.217 | 0.866 | 0.25 x | border slice over a virtual union |
| 90% | binary | union | 25,800 | 33,751 | 0.047 | 0.048 | 0.99 x | merge two aligned huge tries |
| 90% | binary | intersection | 22,200 | 29,251 | 0.042 | 0.064 | 0.65 x | walk only common child keys |
| 90% | binary | subtraction | 1,800 | 2,401 | 0.049 | 0.051 | 0.97 x | left-guided traversal |
| 90% | binary | restriction | 1,536 | 2,017 | 0.017 | 0.062 | 0.27 x | top-level prefix filter |
| 90% | binary | raffination | 22,464 | 29,485 | 0.033 | 0.039 | 0.85 x | complement of the prefix filter |
| 90% | binary | composition | 576,000,000 | 756,031,501 | 5.500 | 5.342 | 1.03 x | no selective consumer; full product is materialized |
| 90% | n-ary | three-way intersection | 21,600 | 28,351 | 0.063 | 0.037 | 1.69 x | evalZ flattens nested intersections and starts from the sparse overlap operand |
| 90% | unary | wrap | 24,000 | 31,502 | 0.003 | 0.004 | 0.70 x | virtual prefix node |
| 90% | unary | unwrap | 24,000 | 31,501 | 0.003 | 0.004 | 0.77 x | descend through virtual prefix |
| 90% | unary | tails-union | 18,004 | 22,506 | 0.664 | 0.677 | 0.98 x | join every first-level tail |
| 90% | unary | tails-intersection | 4 | 6 | 0.600 | 0.606 | 0.99 x | meet every first-level tail |
| 90% | unary | prefix-closure | 31,500 | 31,501 | 1.113 | 1.078 | 1.03 x | all non-empty prefixes |
| 90% | unary | suffix-closure | 48,008 | 60,010 | 4.290 | 4.824 | 0.89 x | all non-empty suffixes |
| 90% | unary | tails-closure | 48,009 | 60,010 | 4.817 | 14.554 | 0.33 x | epsilon plus suffix closure |
| 90% | range | first(512) | 512 | 673 | 0.015 | 0.034 | 0.45 x | ordered border slice |
| 90% | range | last(512) | 512 | 673 | 0.013 | 0.014 | 0.95 x | ordered border slice |
| 90% | combination | union then exact intersection | 1 | 4 | 0.128 | 0.136 | 0.94 x | selective consumer over a virtual union |
| 90% | combination | diff then restriction | 72 | 97 | 0.116 | 0.030 | 3.89 x | left-guided diff under prefix filter |
| 90% | combination | product exact intersection | 1 | 7 | 6.242 | 2.063 | 3.03 x | selector should avoid the full product |
| 90% | combination | product prefix restriction | 24,000 | 31,504 | 6.420 | 2.102 | 3.05 x | one product row selected by prefix |
| 90% | combination | tails of restricted union | 1,228 | 1,536 | 0.107 | 0.067 | 1.58 x | join tails after a logical prefix filter |
| 90% | combination | range of union | 512 | 673 | 0.120 | 0.382 | 0.31 x | border slice over a virtual union |

Ratios above `1.00 x` mean the zipper evaluator is faster. Direct `composition` intentionally has no selective consumer, so it is a useful overhead baseline rather than the expected zipper win. Selective product rows exercise composition without forcing the full product before the final materialization boundary.
