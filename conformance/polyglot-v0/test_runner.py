"""Regression checks for the runner's evidence rules, not for TPF execution."""

import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

import run as runner


class MutationEvidenceTest(unittest.TestCase):
    def test_copying_a_fixture_preserves_exact_numeric_spellings(self):
        original = '{"version":1.0000000000000001,"retryLimit":20e-1,"label":"1.5","items":[2.0]}'
        parsed = json.loads(original, parse_float=runner.NumberLexeme)
        self.assertEqual(runner.fixture_json(parsed), original)

    def test_only_the_expected_assertion_is_a_rejection(self):
        result = subprocess.CompletedProcess([], 1, "", "CONFORMANCE_MISMATCH:numbers/example/exact\n")
        runner.require_expected_rejection(result, "numbers/example/exact", "valid witness")

    def test_success_crashes_signals_and_wrong_assertions_are_not_evidence(self):
        cases = [
            (0, "CONFORMANCE_MISMATCH:numbers/example/exact\n"),
            (1, "Traceback: interpreter crashed\n"),
            (-9, "CONFORMANCE_MISMATCH:numbers/example/exact\n"),
            (1, "CONFORMANCE_MISMATCH:presence/example/state\n"),
            (1, "error text contains CONFORMANCE_MISMATCH:numbers/example/exact\n"),
            (1, "CONFORMANCE_MISMATCH:numbers/example/exact\nCONFORMANCE_MISMATCH:other\n"),
        ]
        for exit_code, stderr in cases:
            with self.subTest(exit_code=exit_code, stderr=stderr):
                result = subprocess.CompletedProcess([], exit_code, "", stderr)
                with self.assertRaises(AssertionError):
                    runner.require_expected_rejection(result, "numbers/example/exact", "bad witness")

    def test_relative_vector_path_is_resolved_in_invoking_directory(self):
        original = Path.cwd()
        with tempfile.TemporaryDirectory() as temporary:
            try:
                os.chdir(temporary)
                args = runner.parse_args(["--skip-build", "--vectors", "my-vectors.json"])
                self.assertEqual(args.vectors, Path(temporary).resolve() / "my-vectors.json")
            finally:
                os.chdir(original)


if __name__ == "__main__":
    unittest.main()
