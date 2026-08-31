import unittest
from pathlib import Path

from tools.proof_pipeline import Artifact, report_status, vampire_command


class ProofPipelineStatusTest(unittest.TestCase):
    def test_full_generation_with_debt_retains_full_gate_status(self) -> None:
        self.assertEqual(report_status(True, 0, 0, 7), "PASS_WITH_PROOF_DEBT")

    def test_any_skipped_generator_degrades_an_otherwise_full_run(self) -> None:
        self.assertEqual(report_status(True, 0, 1, 7), "PARTIAL PASS")

    def test_all_skipped_solver_families_are_manifest_only(self) -> None:
        self.assertEqual(report_status(True, 3, 4, 0), "MANIFEST-ONLY")

    def test_failure_dominates_skip_and_debt_status(self) -> None:
        self.assertEqual(report_status(False, 3, 4, 7), "FAIL")


class VampireStrategyTest(unittest.TestCase):
    def test_portfolio_is_the_default(self) -> None:
        artifact = Artifact("vampire", "general", "Theorem", Path("general.p"))
        mode, command = vampire_command("vampire", artifact, 60)
        self.assertEqual(mode, "portfolio")
        self.assertEqual(command[1:3], ["--mode", "portfolio"])

    def test_manifest_note_selects_plain_saturation(self) -> None:
        artifact = Artifact(
            "vampire",
            "focused",
            "Theorem",
            Path("focused.p"),
            "Compositional lemma; VAMPIRE-STRATEGY=PLAIN.",
        )
        mode, command = vampire_command("vampire", artifact, 60)
        self.assertEqual(mode, "plain")
        self.assertNotIn("--mode", command)
        self.assertEqual(command[-1], "focused.p")


if __name__ == "__main__":
    unittest.main()
