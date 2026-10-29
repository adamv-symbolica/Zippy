# Finite-alphabet review follow-up

This report supersedes the earlier round-three completion evidence. The old
baseline failed the CESU-8 Fold gate (20.89–26.95× source), rejected ordered
stable-ID programs, and overflowed the stack in deep byte-trie algebra.

## Changes

Stable literal-ID codecs now accept `orderedVocabulary = Some(names)`. The
vocabulary is frozen and enforced by the host codec and compiler: unknown
encodes and program constants are rejected before interning; unknown decoded
IDs are rejected too. Literal Fibonacci/4-byte IDs stay unchanged. Existing
ordinary recoder routines project finite source order; no language operation
or runtime symbol-boundary metadata was added. `unsupportedOrdering` and its
exception class were deleted. Default codecs remain open for order-free
programs. Whole/empty windows and proven pure invariant/ignored-accumulator
folds are handled before an ordering requirement. Initializers and updates
still execute; grounded Fold callbacks retain source ordering with snapshots.
Purity follows reachable routines, including mutual calls.

Fold extracts heads once and projects their order once when needed. Minimum
selection uses the sorted backend's first key; singleton subtraction removes
it with a persistent O(log n) update. The fixed input retains its prefix index.
Closed heads bind through shared complete-word membership dispatch. The
reference evaluator now uses one heap continuation loop for paths and spaces;
primitive call arguments avoid intermediate vectors and TailRec objects.
Caches are scoped to an evaluation and cleared on success/failure. Bit atoms
are reused, and short bit-word construction uses primitive arithmetic. Bounded
scanners inline two digits between calls while retaining a state/depth DAG and
shared user continuation. Snapshot and explicit ID bounds size that DAG; only
fully open IDs use the global bound.

Byte-trie union, intersection, subtraction, prefix filtering, composition and
rank slicing use explicit postorder work stacks. Its iterator pushes/pops one
byte in a mutable buffer and copies only when emitting a path. Per-construction
child validation and the unused trie child-mask cache were removed; structural
invariants are checked in tests. The compatibility zipper cursor is preserved.

## Correctness and acceptance

The explicit runner `python3 tools/finite_alphabet_gate.py` passed **38 tests**
using `-Xmx8g` and the default JVM stack. The constructor, core routine, trie
and path-length regression suites add **98 passes**, for **136 unique tests
passed**. Additional consecutive timing runs repeat the review suite.

The 1,000-program × two-frame corpus executes **all 1,000 programs** on each
stable-ID route, including the former 180 rejected rows. It records 22,000 set
target evaluations and separately compares 2,000 byte-trie evaluations. AST
and normalized execution-work limits pass. Differential checks also cover
reversed interning order, negative ranges, accumulator-sensitive folds, Range
inside GroundedSP, mutual routines, shadowed binders, frozen admission, future
symbols on open codecs, and grounded callback sequencing.

The acceptance runner propagated a controlled assertion failure as exit 1,
then returned exit 0 after restoration. The temporary self-check test was
removed. The complete Scala suite also discovers the finite-alphabet suites.

## Unmodified changing-Fold gate: three consecutive final passes

The exact Probe B program and its limits are unchanged: three warmups, median
of five evaluations, source parity, ≤20× original symbol eval, and growth
exponent ≤1.3 from 4k to 16k. Compilation/encoding are outside timing, and GC
is not forced or excluded. Final-code logs are `r4-acceptance-final.log`,
`r4-confirmed-2.log`, and `r4-confirmed-3.log`.

| Run | Codec | 4k ms | 16k ms | 16k/source | Exponent |
| --- | --- | ---: | ---: | ---: | ---: |
| 1 | dictionary4 | 48.413 | 115.068 | 4.57 | 0.625 |
| 1 | dictionaryFib | 56.007 | 264.995 | 10.53 | 1.121 |
| 1 | CESU8 | 58.955 | 246.276 | 9.79 | 1.031 |
| 2 | dictionary4 | 45.777 | 121.472 | 4.99 | 0.704 |
| 2 | dictionaryFib | 59.983 | 289.566 | 11.90 | 1.136 |
| 2 | CESU8 | 63.130 | 264.874 | 10.89 | 1.034 |
| 3 | dictionary4 | 46.948 | 114.359 | 4.60 | 0.642 |
| 3 | dictionaryFib | 54.721 | 263.524 | 10.60 | 1.134 |
| 3 | CESU8 | 58.436 | 243.942 | 9.81 | 1.031 |

The 20,000-path CESU-8 Range comparison also passes, at approximately the same
time as four-byte dictionaries (about 9 ms).

## Deep byte-trie evidence

All six algebra operations ran on overlapping **100,000-byte paths on a
512 KiB thread**, with result parity and structural validation. Iterating one
20k/80k/320k path took **1.249 / 5.243 / 22.022 ms** in the final acceptance
run: exponent approximately **1.04**, below the 1.2 ceiling. No enlarged JVM
stack is used in the documented finite-alphabet acceptance command.

## Allocation and stable-ID sweeps

The real `program.eval` path was profiled with JFR allocation samples,
ThreadMXBean allocated-byte counts and `-Xlog:gc`. Before the continuation
change, CESU Fold allocated **1,368 MB per 16k-group evaluation**, versus
34 MB for the source. TailCalls continuations/calls/done objects and vector
argument construction dominated; GC pauses reached roughly 200 ms. The raw
JFR and GC logs are retained with the probe source.

The durable entry point `morkl.stableIdReviewReport` covers stable-ID
Iteration, Range and changing Fold at 1k/4k/20k. It reports AST/routine counts,
source and target medians, and retains a strict bit/byte Iteration ratio check.
Unlike Probe B, this standalone sweep uses eight warmups and five samples.
Final sweep and post-change allocation results are recorded below.

The final stable-ID sweep passed its ratio assertions at all three sizes:

| Paths | Open bits nodes/routines | Snapshot bits nodes/routines | Open bits ms | Snapshot bits ms | ID4 ms |
| --- | --- | --- | ---: | ---: | ---: |
| 1000 | 2054/46 | 674/16 | 6.971 | 4.345 | 5.024 |
| 4000 | 2054/46 | 766/18 | 21.131 | 20.794 | 9.689 |
| 20000 | 2054/46 | 950/22 | 132.170 | 131.945 | 51.097 |

At 20k, stable-ID negative Range took 346.284 ms (bits) / 277.917 ms (ID4),
and changing Fold took 725.882 / 616.574 ms. These are ordinary generated
programs with stable IDs, not rank-dictionary encodings. Snapshot bit routine
counts satisfy the requested logarithmic bound.

**Timing limitation:** the earlier two-warmup standalone sweep repeatedly
missed the 3× open-bit/ID4 ratio at 4k (about 3.47×). With eight warmups the
final ratios were 1.39×, 2.18× and 2.59×. This short-run/JIT sensitivity is
reported rather than treated as a universal speed guarantee. The required
Probe B harness was not changed, and its three consecutive final passes use
its original three warmups.

Before allocated MB per evaluation: source 34.1, dictionary4 519.8, dictionaryFib 1085.2, CESU8 1368.0.

After allocated MB per evaluation: source 34.1, dictionary4 264.0, dictionaryFib 416.1, CESU8 470.7.

CESU allocation therefore fell about 66%, from 1,368 MB to 471 MB per
16k-group evaluation. The post-change GC log has no forced collections.
Raw logs, the original JFR recording, and `AllocationProbe.scala` are preserved
under `~/polemic-notes/zippy-coded-r4/`. Production changes are checkpointed at
`70aa7a2`; this report records the final checks after that implementation.

No acceptance test is disabled or relaxed. No required functionality remains
unimplemented in this review scope. The short-warmup Iteration variability
above remains a performance qualification. Future timing comparisons should
retain the warmup count and include allocation/GC costs.
