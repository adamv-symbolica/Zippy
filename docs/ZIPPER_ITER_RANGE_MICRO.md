# Iter Range-Tail Zipper Microbenchmark

This isolates `SpaceZipper.Iteration(..., RangeTail(0, -1))`, the path used by range-tail iteration over child zippers. The old model first collected all output keys, then recomputed each key by scanning every source head again. The current implementation accumulates per-key children in the first branch traversal and joins them once.

| source heads | tail fanout | result paths | old emulation ms | current ms | old / current |
|---:|---:|---:|---:|---:|---:|
| 32 | 12 | 352 | 5.005 | 3.184 | 1.57 x |
| 96 | 16 | 1,440 | 22.527 | 23.398 | 0.96 x |
| 192 | 16 | 2,880 | 93.853 | 90.809 | 1.03 x |
