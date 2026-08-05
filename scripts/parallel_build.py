#!/usr/bin/env python3
"""Run isolated Minecraft target builds with bounded parallelism."""

from __future__ import annotations

import argparse
import collections
import os
import pathlib
import shlex
import signal
import subprocess
import sys
import time
from dataclasses import dataclass
from typing import Callable, Iterable, TextIO

import minecraft_targets


ROOT = pathlib.Path(__file__).resolve().parents[1]
GIB = 1024**3
SYSTEM_MEMORY_RESERVE = 2 * GIB
MEMORY_PER_BUILD = 2 * GIB
MAX_AUTOMATIC_JOBS = 8
SHARED_OUTPUT_MARKERS = (
    "common/build/classes/java/main/fun/prof_chen/teamviewer/main_code/client/ClientApplication.class",
    "common-sdk/build/classes/java/main/fun/prof_chen/teamviewer/main_code/client/sdk/ClientAdapterFactory.class",
    "client-bootstrap/build/classes/java/main/fun/prof_chen/teamviewer/client/bootstrap/ClientBootstrap.class",
    "fabric-bootstrap/build/classes/java/main/fun/prof_chen/teamviewer/client/TeamviewerClient.class",
    "neoforge-legacy/runtime-libs/common-runtime.jar",
    "neoforge-legacy/runtime-libs/client-bootstrap.jar",
)


@dataclass(frozen=True)
class BuildJob:
    label: str
    log_name: str
    command: tuple[str, ...]
    environment: tuple[tuple[str, str], ...] = ()
    artifact: tuple[str, str] | None = None


@dataclass
class RunningJob:
    job: BuildJob
    process: subprocess.Popen[bytes]
    log: TextIO
    started_at: float


def _read_text(path: pathlib.Path) -> str | None:
    try:
        return path.read_text(encoding="utf-8").strip()
    except (OSError, UnicodeError):
        return None


def effective_cpu_count() -> int:
    detected = max(1, os.cpu_count() or 1)
    cpu_max = _read_text(pathlib.Path("/sys/fs/cgroup/cpu.max"))
    if cpu_max:
        quota, _, period = cpu_max.partition(" ")
        if quota != "max" and period:
            try:
                detected = min(detected, max(1, int(quota) // int(period)))
            except (TypeError, ValueError, ZeroDivisionError):
                pass
    else:
        quota = _read_text(pathlib.Path("/sys/fs/cgroup/cpu/cpu.cfs_quota_us"))
        period = _read_text(pathlib.Path("/sys/fs/cgroup/cpu/cpu.cfs_period_us"))
        try:
            if quota is not None and period is not None and int(quota) > 0:
                detected = min(detected, max(1, int(quota) // int(period)))
        except (TypeError, ValueError, ZeroDivisionError):
            pass
    return detected


def available_memory_bytes() -> int | None:
    candidates: list[int] = []
    meminfo = _read_text(pathlib.Path("/proc/meminfo"))
    if meminfo:
        for line in meminfo.splitlines():
            if line.startswith("MemAvailable:"):
                try:
                    candidates.append(int(line.split()[1]) * 1024)
                except (IndexError, ValueError):
                    pass
                break

    for maximum_path, current_path in (
        (pathlib.Path("/sys/fs/cgroup/memory.max"), pathlib.Path("/sys/fs/cgroup/memory.current")),
        (
            pathlib.Path("/sys/fs/cgroup/memory/memory.limit_in_bytes"),
            pathlib.Path("/sys/fs/cgroup/memory/memory.usage_in_bytes"),
        ),
    ):
        maximum = _read_text(maximum_path)
        current = _read_text(current_path)
        if maximum is None or current is None or maximum == "max":
            continue
        try:
            maximum_bytes = int(maximum)
            current_bytes = int(current)
            if 0 < maximum_bytes < (1 << 60):
                candidates.append(max(0, maximum_bytes - current_bytes))
        except ValueError:
            continue
    return min(candidates) if candidates else None


def automatic_job_count(cpu_count: int, available_memory: int | None) -> int:
    cpu_jobs = max(1, cpu_count)
    if available_memory is None:
        memory_jobs = 4
    else:
        usable = max(0, available_memory - SYSTEM_MEMORY_RESERVE)
        memory_jobs = max(1, usable // MEMORY_PER_BUILD)
    return max(1, min(MAX_AUTOMATIC_JOBS, cpu_jobs, memory_jobs))


def resolve_job_count(value: str, cpu_count: int, available_memory: int | None) -> int:
    if value == "auto":
        return automatic_job_count(cpu_count, available_memory)
    try:
        jobs = int(value)
    except ValueError as error:
        raise ValueError(f"JOBS must be 'auto' or a positive integer, got {value!r}") from error
    if jobs < 1:
        raise ValueError(f"JOBS must be at least 1, got {jobs}")
    return jobs


def _parallel_gradle_command(gradle: str, workers: int) -> list[str]:
    command = shlex.split(gradle)
    if not command:
        raise ValueError("Gradle command must not be empty")
    return [
        *command,
        f"--max-workers={workers}",
        "--no-daemon",
        "-Pparallel_target_build=true",
    ]


def _with_project_cache(command: list[str], cache_name: str) -> list[str]:
    cache_directory = ROOT / "build/parallel-project-cache" / cache_name
    return [*command, f"--project-cache-dir={cache_directory}"]


def require_shared_outputs(root: pathlib.Path = ROOT) -> None:
    missing = [relative for relative in SHARED_OUTPUT_MARKERS if not (root / relative).is_file()]
    if missing:
        raise RuntimeError(
            "Parallel build prerequisites are missing; run prepareParallelBuild first: "
            + ", ".join(missing)
        )


def adapter_jobs(
    matrix: minecraft_targets.Matrix,
    gradle: str,
    workers: int,
    java_home_resolver: Callable[[minecraft_targets.Matrix, str, str], pathlib.Path]
    = minecraft_targets.java_home,
) -> list[BuildJob]:
    gradle_command = _parallel_gradle_command(gradle, workers)
    fabric = collections.deque(matrix.fabric)
    neoforge = collections.deque(matrix.neoforge)
    jobs: list[BuildJob] = []
    while fabric or neoforge:
        if fabric:
            target = fabric.popleft()
            target_gradle = _with_project_cache(gradle_command, f"fabric-adapter-{target}")
            jobs.append(BuildJob(
                label=f"Fabric adapter {target}",
                log_name=f"fabric-{target}.log",
                command=tuple([*target_gradle, f"-Pfabric_target={target}", ":fabric:build"]),
                environment=(("JAVA_HOME", str(java_home_resolver(matrix, "fabric", target))),),
                artifact=("fabric", target),
            ))
        if neoforge:
            target = neoforge.popleft()
            target_gradle = _with_project_cache(gradle_command, f"neoforge-adapter-{target}")
            jobs.append(BuildJob(
                label=f"NeoForge adapter {target}",
                log_name=f"neoforge-{target}.log",
                command=tuple([
                    *target_gradle,
                    *(["-p", "neoforge-legacy"] if target in matrix.neoforge_legacy else []),
                    f"-Pneoforge_target={target}",
                    *(["build"] if target in matrix.neoforge_legacy else [":neoforge:build"]),
                ]),
                environment=(("JAVA_HOME", str(java_home_resolver(matrix, "neoforge", target))),),
                artifact=("neoforge", target),
            ))
    return jobs


def fabric_runtime_jobs(
    matrix: minecraft_targets.Matrix,
    gradle: str,
    workers: int,
    java_home_resolver: Callable[[minecraft_targets.Matrix, str, str], pathlib.Path]
    = minecraft_targets.java_home,
) -> list[BuildJob]:
    gradle_command = _parallel_gradle_command(gradle, workers)
    jobs = []
    for runtime in matrix.official:
        family = minecraft_targets.fabric_owner(matrix, runtime)
        target_gradle = _with_project_cache(gradle_command, f"fabric-runtime-{runtime}")
        jobs.append(BuildJob(
            label=f"Fabric runtime {runtime}",
            log_name=f"fabric-{runtime}.log",
            command=tuple([
                *target_gradle,
                f"-Pfabric_target={family}",
                f"-Pfabric_runtime_version={runtime}",
                ":fabric:compileClientJava",
                ":fabric:verifyMapPluginContracts",
            ]),
            environment=(("JAVA_HOME", str(java_home_resolver(matrix, "fabric", family))),),
        ))
    return jobs


def _terminate(running: Iterable[RunningJob]) -> None:
    active = list(running)
    for item in active:
        if item.process.poll() is None:
            try:
                os.killpg(item.process.pid, signal.SIGTERM)
            except ProcessLookupError:
                pass
    deadline = time.monotonic() + 10
    while time.monotonic() < deadline and any(item.process.poll() is None for item in active):
        time.sleep(0.05)
    for item in active:
        if item.process.poll() is None:
            try:
                os.killpg(item.process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
        item.process.wait()
        item.log.close()


def _print_log_tail(path: pathlib.Path, lines: int = 80) -> None:
    try:
        tail = path.read_text(encoding="utf-8", errors="replace").splitlines()[-lines:]
    except OSError as error:
        print(f"Unable to read {path}: {error}", file=sys.stderr)
        return
    print(f"--- {path} (last {len(tail)} lines) ---", file=sys.stderr)
    print("\n".join(tail), file=sys.stderr)


def run_jobs(
    jobs: Iterable[BuildJob],
    concurrency: int,
    log_directory: pathlib.Path,
    *,
    cwd: pathlib.Path = ROOT,
    poll_interval: float = 0.1,
    on_started: Callable[[BuildJob], None] | None = None,
    on_success: Callable[[BuildJob], None] | None = None,
) -> None:
    pending = collections.deque(jobs)
    log_directory.mkdir(parents=True, exist_ok=True)
    for old_log in log_directory.glob("*.log"):
        old_log.unlink()
    active: dict[int, RunningJob] = {}
    failures: list[tuple[BuildJob, int]] = []
    try:
        while active or (pending and not failures):
            while pending and not failures and len(active) < concurrency:
                job = pending.popleft()
                log_path = log_directory / job.log_name
                log_file = log_path.open("w", encoding="utf-8")
                started_at = time.monotonic()
                print(f"[start] {job.label} -> {log_path}", flush=True)
                if on_started is not None:
                    on_started(job)
                try:
                    process = subprocess.Popen(
                        job.command,
                        cwd=cwd,
                        env={**os.environ, **dict(job.environment)},
                        stdout=log_file,
                        stderr=subprocess.STDOUT,
                        start_new_session=True,
                    )
                except Exception:
                    log_file.close()
                    raise
                active[process.pid] = RunningJob(job, process, log_file, started_at)

            completed_any = False
            for pid, item in list(active.items()):
                return_code = item.process.poll()
                if return_code is None:
                    continue
                completed_any = True
                item.log.close()
                del active[pid]
                duration = time.monotonic() - item.started_at
                if return_code == 0:
                    try:
                        if on_success is not None:
                            on_success(item.job)
                    except (Exception, SystemExit) as error:
                        with (log_directory / item.job.log_name).open("a", encoding="utf-8") as log:
                            log.write(f"\nPost-build step failed: {error}\n")
                        failures.append((item.job, 1))
                        print(
                            f"[fail]  {item.job.label} post-build step failed ({duration:.1f}s)",
                            file=sys.stderr,
                            flush=True,
                        )
                    else:
                        print(f"[done]  {item.job.label} ({duration:.1f}s)", flush=True)
                else:
                    failures.append((item.job, return_code))
                    print(
                        f"[fail]  {item.job.label} exited {return_code} ({duration:.1f}s)",
                        file=sys.stderr,
                        flush=True,
                    )
            if not completed_any and active:
                time.sleep(poll_interval)
    except KeyboardInterrupt:
        print("Interrupted; terminating active Gradle builds...", file=sys.stderr)
        _terminate(active.values())
        raise
    except BaseException:
        _terminate(active.values())
        raise

    if failures:
        for job, _ in failures:
            _print_log_tail(log_directory / job.log_name)
        skipped = len(pending)
        labels = ", ".join(f"{job.label} ({code})" for job, code in failures)
        raise RuntimeError(f"Parallel build failed: {labels}; skipped {skipped} pending jobs")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("stage", choices=("adapters", "fabric-runtimes"))
    parser.add_argument("--jobs", default="auto")
    parser.add_argument("--gradle", default="./gradlew")
    args = parser.parse_args()

    cpus = effective_cpu_count()
    available_memory = available_memory_bytes()
    try:
        concurrency = resolve_job_count(args.jobs, cpus, available_memory)
    except ValueError as error:
        parser.error(str(error))
    workers = max(1, cpus // concurrency)
    memory_text = "unknown" if available_memory is None else f"{available_memory / GIB:.1f} GiB"
    print(
        f"Parallel stage {args.stage}: jobs={concurrency}, Gradle workers/job={workers}, "
        f"effective CPUs={cpus}, available memory={memory_text}",
        flush=True,
    )

    matrix = minecraft_targets.load_manifest()
    try:
        require_shared_outputs()
    except RuntimeError as error:
        print(error, file=sys.stderr)
        return 1
    if args.stage == "adapters":
        jobs = adapter_jobs(matrix, args.gradle, workers)
    else:
        jobs = fabric_runtime_jobs(matrix, args.gradle, workers)

    def collect(job: BuildJob) -> None:
        if job.artifact is None:
            return
        loader, target = job.artifact
        artifact = minecraft_targets.collect_artifact(matrix, loader, target)
        print(f"[collect] {artifact.relative_to(ROOT)}", flush=True)

    try:
        run_jobs(
            jobs,
            concurrency,
            ROOT / "build/parallel-logs" / args.stage,
            on_success=collect,
        )
    except (KeyboardInterrupt, RuntimeError) as error:
        if isinstance(error, RuntimeError):
            print(error, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
