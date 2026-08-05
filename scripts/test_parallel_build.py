from __future__ import annotations

import pathlib
import sys
import tempfile
import unittest


sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import minecraft_targets
import parallel_build


class ParallelBuildTest(unittest.TestCase):
    def test_automatic_jobs_respect_cpu_memory_reserve_and_cap(self) -> None:
        self.assertEqual(5, parallel_build.automatic_job_count(16, 13 * parallel_build.GIB))
        self.assertEqual(1, parallel_build.automatic_job_count(16, 3 * parallel_build.GIB))
        self.assertEqual(8, parallel_build.automatic_job_count(64, 64 * parallel_build.GIB))
        self.assertEqual(4, parallel_build.automatic_job_count(32, None))

    def test_explicit_jobs_and_invalid_values(self) -> None:
        self.assertEqual(1, parallel_build.resolve_job_count("1", 16, 32 * parallel_build.GIB))
        self.assertEqual(12, parallel_build.resolve_job_count("12", 2, 2 * parallel_build.GIB))
        for value in ("0", "-1", "many"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                parallel_build.resolve_job_count(value, 16, 32 * parallel_build.GIB)

    def test_manifest_jobs_cover_every_target_once(self) -> None:
        matrix = minecraft_targets.load_manifest()

        def fake_java_home(
            _matrix: minecraft_targets.Matrix, _loader: str, target: str
        ) -> pathlib.Path:
            return pathlib.Path("/jdk") / target

        adapters = parallel_build.adapter_jobs(
            matrix, "./gradlew", 3, java_home_resolver=fake_java_home
        )
        runtimes = parallel_build.fabric_runtime_jobs(
            matrix, "./gradlew", 3, java_home_resolver=fake_java_home
        )

        self.assertEqual(36, len(adapters))
        self.assertEqual(36, len({job.log_name for job in adapters}))
        self.assertEqual(31, len(runtimes))
        self.assertEqual(31, len({job.log_name for job in runtimes}))
        neoforge = [job for job in adapters if job.label.startswith("NeoForge")]
        self.assertEqual(19, len(neoforge))
        self.assertTrue(all(job.artifact is not None for job in neoforge))
        self.assertTrue(all(dict(job.environment)["JAVA_HOME"].startswith("/jdk/") for job in adapters))
        self.assertTrue(all("--max-workers=3" in " ".join(job.command) for job in adapters))
        self.assertTrue(all("-Pparallel_target_build=true" in job.command for job in adapters))
        self.assertTrue(all("-Pparallel_target_build=true" in job.command for job in runtimes))
        adapter_caches = {
            argument for job in adapters for argument in job.command
            if argument.startswith("--project-cache-dir=")
        }
        runtime_caches = {
            argument for job in runtimes for argument in job.command
            if argument.startswith("--project-cache-dir=")
        }
        self.assertEqual(36, len(adapter_caches))
        self.assertEqual(31, len(runtime_caches))

    def test_parallel_build_requires_every_shared_output(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = pathlib.Path(temp_dir)
            with self.assertRaises(RuntimeError):
                parallel_build.require_shared_outputs(root)
            for relative in parallel_build.SHARED_OUTPUT_MARKERS:
                marker = root / relative
                marker.parent.mkdir(parents=True, exist_ok=True)
                marker.touch()
            parallel_build.require_shared_outputs(root)

    def test_failure_stops_scheduling_and_preserves_logs(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = pathlib.Path(temp_dir)
            marker = root / "should-not-run"
            jobs = [
                parallel_build.BuildJob(
                    "failure",
                    "failure.log",
                    (sys.executable, "-c", "print('expected failure'); raise SystemExit(7)"),
                ),
                parallel_build.BuildJob(
                    "already-running",
                    "running.log",
                    (sys.executable, "-c", "import time; time.sleep(0.3)"),
                ),
                parallel_build.BuildJob(
                    "not-started",
                    "not-started.log",
                    (sys.executable, "-c", f"open({str(marker)!r}, 'w').close()"),
                ),
            ]

            with self.assertRaises(RuntimeError):
                parallel_build.run_jobs(
                    jobs, 2, root / "logs", cwd=root, poll_interval=0.01
                )

            self.assertFalse(marker.exists())
            self.assertIn("expected failure", (root / "logs/failure.log").read_text())
            self.assertFalse((root / "logs/not-started.log").exists())


if __name__ == "__main__":
    unittest.main()
