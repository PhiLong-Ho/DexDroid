"""Run tooling tests and retain structured case outcomes for release reporting."""

import argparse
import json
import subprocess
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class RecordingResult(unittest.TextTestResult):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.started = []

    def startTest(self, test):
        self.started.append(test.id())
        super().startTest(test)

    def case_results(self):
        if len(self.started) != len(set(self.started)):
            raise ValueError("Duplicate tooling test IDs")
        outcomes = dict.fromkeys(self.started, "passed")
        # Later categories take precedence, so multiple failed subtests count as
        # one failed parent case rather than distorting the pass denominator.
        for status, tests in [
            ("skipped", [test for test, _ in self.skipped]),
            ("expected_failure", [test for test, _ in self.expectedFailures]),
            ("unexpected_success", self.unexpectedSuccesses),
            ("failed", [test for test, _ in self.failures]),
            ("error", [test for test, _ in self.errors]),
        ]:
            for test in tests:
                ident = getattr(test, "test_case", test).id()
                if ident not in outcomes:
                    raise ValueError("Outcome without a started tooling case")
                outcomes[ident] = status
        return [{"id": ident, "status": status} for ident, status in sorted(outcomes.items())]


def run_tests(suite, output, source_commit):
    result = unittest.TextTestRunner(verbosity=2, resultclass=RecordingResult).run(suite)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        json.dumps(
            {"schema_version": 1, "source_commit": source_commit, "cases": result.case_results()},
            indent=2,
        )
        + "\n",
        encoding="utf-8",
        newline="\n",
    )
    return 0 if result.wasSuccessful() and result.testsRun > 0 else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    commit = subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=ROOT, encoding="utf-8"
    ).strip()
    suite = unittest.defaultTestLoader.discover(str(ROOT / "tools/tests"))
    return run_tests(suite, args.output, commit)


if __name__ == "__main__":
    raise SystemExit(main())
