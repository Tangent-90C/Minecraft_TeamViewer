from __future__ import annotations

import json
import pathlib
import subprocess
import sys
import tempfile
import unittest


SCRIPT = pathlib.Path(__file__).with_name("run_adapter_tck.py")


class RunAdapterTckTest(unittest.TestCase):
    def test_stops_client_after_passing_report_is_written(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            report = pathlib.Path(temp_dir) / "adapter-tck.json"
            payload = json.dumps({
                "passed": True,
                "minecraftVersion": "1.20.3",
                "adapterVersion": "neoforge-1.20.4",
            })
            child = (
                "import pathlib,time; "
                f"pathlib.Path({str(report)!r}).write_text({payload!r}); "
                "time.sleep(30)"
            )

            completed = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "--report",
                    str(report),
                    "--timeout",
                    "5",
                    "--",
                    sys.executable,
                    "-c",
                    child,
                ],
                check=False,
                capture_output=True,
                text=True,
                timeout=10,
            )

            self.assertEqual(0, completed.returncode, completed.stderr)
            self.assertIn("Adapter TCK passed", completed.stdout)

    def test_rejects_failed_report(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            report = pathlib.Path(temp_dir) / "adapter-tck.json"
            payload = json.dumps({"passed": False, "issues": ["missing event"]})
            child = (
                "import pathlib,time; "
                f"pathlib.Path({str(report)!r}).write_text({payload!r}); "
                "time.sleep(30)"
            )

            completed = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "--report",
                    str(report),
                    "--timeout",
                    "5",
                    "--",
                    sys.executable,
                    "-c",
                    child,
                ],
                check=False,
                capture_output=True,
                text=True,
                timeout=10,
            )

            self.assertEqual(1, completed.returncode)
            self.assertIn("Adapter TCK failed", completed.stderr)


if __name__ == "__main__":
    unittest.main()
