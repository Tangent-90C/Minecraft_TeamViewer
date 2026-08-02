from __future__ import annotations

import pathlib
import sys
import tempfile
import unittest
import unittest.mock


sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import minecraft_targets


class MinecraftTargetsTest(unittest.TestCase):
    def test_gradle_java_versions_are_numeric_sorted_and_deduplicated(self) -> None:
        matrix = minecraft_targets.Matrix(
            properties={
                "fabric.fabric-21.gradle_runtime_java": "21",
                "fabric.fabric-25.gradle_runtime_java": "25",
                "neoforge.neoforge-17.gradle_runtime_java": "17",
                "neoforge.neoforge-21.gradle_runtime_java": "21",
            },
            map_properties={},
            official=(),
            fabric=("fabric-21", "fabric-25"),
            neoforge=("neoforge-17", "neoforge-21"),
            neoforge_modern=(),
            neoforge_legacy=(),
        )

        self.assertEqual(("17", "21", "25"), minecraft_targets.gradle_java_versions(matrix))

    def test_java_home_accepts_setup_java_environment_variable(self) -> None:
        matrix = minecraft_targets.load_manifest()
        with tempfile.TemporaryDirectory() as temp_dir:
            java_home = self._fake_jdk(pathlib.Path(temp_dir), 25)
            with unittest.mock.patch.dict(
                minecraft_targets.os.environ,
                {"JAVA_HOME_25_X64": str(java_home)},
                clear=True,
            ):
                self.assertEqual(
                    java_home.resolve(),
                    minecraft_targets.java_home(matrix, "fabric", "26.1.2"),
                )

    def test_project_environment_variable_takes_precedence(self) -> None:
        matrix = minecraft_targets.load_manifest()
        with tempfile.TemporaryDirectory() as temp_dir:
            root = pathlib.Path(temp_dir)
            preferred = self._fake_jdk(root / "preferred", 25)
            setup_java = self._fake_jdk(root / "setup-java", 25)
            with unittest.mock.patch.dict(
                minecraft_targets.os.environ,
                {
                    "JAVA25_HOME": str(preferred),
                    "JAVA_HOME_25_X64": str(setup_java),
                },
                clear=True,
            ):
                self.assertEqual(
                    preferred.resolve(),
                    minecraft_targets.java_home(matrix, "fabric", "26.1.2"),
                )

    def test_java_home_rejects_wrong_jdk_version(self) -> None:
        matrix = minecraft_targets.load_manifest()
        with tempfile.TemporaryDirectory() as temp_dir:
            wrong_java = self._fake_jdk(pathlib.Path(temp_dir), 21)
            with unittest.mock.patch.object(
                minecraft_targets,
                "java_home_candidates",
                return_value=(wrong_java,),
            ):
                with self.assertRaisesRegex(SystemExit, "requires a Java 25 Gradle runtime"):
                    minecraft_targets.java_home(matrix, "fabric", "26.1.2")

    @staticmethod
    def _fake_jdk(root: pathlib.Path, version: int) -> pathlib.Path:
        java_home = root / f"jdk-{version}"
        (java_home / "bin").mkdir(parents=True)
        (java_home / "bin" / "java").touch()
        (java_home / "release").write_text(f'JAVA_VERSION="{version}.0.1"\n', encoding="utf-8")
        return java_home


if __name__ == "__main__":
    unittest.main()
