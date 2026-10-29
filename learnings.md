Techniques worth retaining:

- **Reuse recoders for ordered ops.** If a compiler already has a generic "recode into an order-preserving projection, apply Range, recode back" path for non-order-preserving codes, then stable-ID ordered ops need only a frozen vocabulary to populate `orderedDigits`. They need no parallel "unsupported" flag.
- **Check order-free lowerings before any ordering requirement.** Do this for unchanged or `_` accumulators, and for full-cover Ranges. Otherwise well-defined programs are rejected.
- **Run stack-safety witnesses on an explicitly small-stack thread** (for example `new Thread(null, r, name, 512 KiB)`) rather than under `-Xss8m`. Cover persistent-trie binary operations on 100k-byte overlapping paths, not just the evaluator.
- **Iterate tries with a mutable path buffer.** Copying the full prefix at each node is quadratic in path length; detect it by timing a single long path at 4× lengths.
- **Keep failing performance gates strict and report them honestly.** Re-run them several times: a single-run 20.9× claim reproduced at 26.9×.
- **Size bounded decoders from the admissible id bound.** A global `Int.MaxValue` bound doubles routine count and node size.
