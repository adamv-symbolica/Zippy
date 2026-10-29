# Optimized-graph OpenFst evidence

The Scala tests, all benchmark jobs, all 108 cornerstone exports, and the
reference translation audit pass. The exhaustive native audit is **incomplete**:
248,109 comparisons pass, one generated unit reaches its compilation timeout,
and 64 comparisons remain unfinished at the audit deadline. No semantic
mismatch was reported. See [native-tests.json](native-tests.json).

- [Benchmark coverage](benchmark-coverage.json) and [all benchmark jobs](benchmark-summary.json)
- [Reference translation audit](reference-translations.json)
- [Cornerstone export checks](cornerstone-export-checks.json)
- [Environment and source hashes](environment.json)
- [Evidence archive checksum](evidence-archive.json)
- [Full raw archive](../../../output/openfst-graph-validation/zippy-graph-complete-evidence.tar.gz)

The raw archive includes full native/reference ledgers and logs, benchmark
shards/reports, additional evaluator runs, the validated source snapshot, and
the generated C++ unit/build log that timed out. The large archive is retained
in the ignored local `output/` directory; this directory retains its checksum
and compact summaries in Git.
