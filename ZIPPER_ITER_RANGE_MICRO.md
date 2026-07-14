# Iter Range-Tail Zipper Microbenchmark

This isolates `SpaceZipper.Iteration(..., RangeTail(0, -1))`, the path used by range-tail iteration over child zippers. The old model first collected all output keys, then recomputed each key by scanning every source head again. The current implementation accumulates per-key children in the first branch traversal and joins them once.

| source heads | tail fanout | result paths | old emulation ms | current ms | old / current |
|---:|---:|---:|---:|---:|---:|
| 32 | 12 | 352 | 6.241 | 4.143 | 1.51 x |
| 96 | 16 | 1,440 | 21.623 | 22.039 | 0.98 x |
| 192 | 16 | 2,880 | 82.128 | 83.698 | 0.98 x |
