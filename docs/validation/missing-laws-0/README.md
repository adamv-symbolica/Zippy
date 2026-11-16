# Missing-laws validation — 2026-11-16

The implementation was checked on `cpu-h4d-xlarge` against the workspace based
on `6ef74ef`. `result.json` is the strict runner's successful result:

- 369 Scala tests passed with both native PathMap and OpenFst enabled.
- 389,961 captured reference cases passed all eight translations:
  3,119,688 checks, zero failures, zero source errors.
- An independent ledger scan required exactly one passing record per
  `(case, direction)`, rejecting duplicates, omissions and errors. Its hashes
  and counts are in `ledger-verification.json`.
- All 48 benchmark measurements checked the complete output set. The four
  generated Rust/C++ files also compiled separately and passed 12 additional
  protocol checks; see `benchmark/standalone-checks.json`.
- The eight Python tooling tests passed.

The translation replay uses the reference bit/byte/symbol evaluators. Native
correctness is covered by the full suite's compiled backend tests; the
3,119,688 figure does not mean that many native programs were compiled.

The corpus includes 57,431 cases from the new `EvidenceEliminationTest` and
301,519 from the workspace's pre-existing, untracked `DivergenceLawsTest.scala`.
That file and its accompanying document were left unmodified and uncommitted.
Counts in a checkout without that additional suite will therefore be smaller.

`tests.log.gz` retains the successful test/replay log. The complete compressed
ledger is preserved locally at
`output/missing-laws-0/h4d/checks.jsonl.gz` (13,715,701 bytes); generated output
is excluded from Git. `source-sha256.txt` fingerprints the Scala sources and
runner used for verification and the final benchmark.

Reproduce the full replay, with Rust/Cargo 1.99.1, Scala CLI, Java 21, OpenFst,
Boost, Z3 and egglog installed:

```sh
env _JAVA_OPTIONS='-Dmorkl.pathmap.test=true -Dmorkl.openfst.test=true' \
  python3 tools/test_radix_translations.py \
  --output output/missing-laws-0/reproduce --workers 48 \
  --heap 256g --stack 256m --seconds 5400
```

The cold 4096-level trie construction test required the larger JVM stack on
this Linux toolchain. The runner now exposes `--stack`, retaining its previous
64m default. Validation pins Rust/Cargo 1.99.1: the node's Cargo 1.100 produced
no `release/deps` directory, which the existing native build driver expects.
The machine and tool versions are recorded in `environment.json`.

See [the implementation and benchmark report](../../EVIDENCE_ELIMINATION.md)
for rewrite conditions, timing boundaries and performance comparisons.
