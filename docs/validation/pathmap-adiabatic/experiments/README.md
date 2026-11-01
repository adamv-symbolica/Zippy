# Discarded implementations

`scalar-head-writes.csv` records the `4963d1a` loop that inserted every head byte
individually. The dense four-byte prefix cases regressed against the reuse-off
version in the same process. The final implementation uses native masked grafts
for dense masks at empty output focuses.

`per-branch-tails-oeis.csv` records `1d0308b`, which joined every tail branch into
the output separately. Its OEIS build took about 2.70 seconds. The final adaptive
implementation keeps native bulk projection for multiple residuals and measured
about 0.83 seconds. These are throughput-run medians under different concurrent
loads; their original samples remain in the local raw evidence. Exact key-set
checks passed in both versions.
