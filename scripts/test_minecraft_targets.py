from __future__ import annotations

import pathlib
import sys
import tempfile
import unittest
import unittest.mock


sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import minecraft_targets


class MinecraftTargetsTest(unittest.TestCase):
    @staticmethod
    def _source_layout_matrix() -> minecraft_targets.Matrix:
        return minecraft_targets.Matrix(
            properties={
                "fabric.1.20.1.covers": "1.20.1",
                "fabric.1.20.1.adapter_version": "1.20.1",
                "fabric.1.20.2.covers": "1.20.2",
                "fabric.1.20.2.adapter_version": "1.20.2",
                "neoforge_legacy_targets": "",
            },
            map_properties={},
            official=("1.20.1", "1.20.2"),
            fabric=("1.20.1", "1.20.2"),
            neoforge=(),
            neoforge_modern=(),
            neoforge_legacy=(),
        )

    @staticmethod
    def _write_source_layout_skeleton(root: pathlib.Path) -> None:
        for module in ("fabric", "neoforge", "neoforge-legacy"):
            (root / module / "src/version").mkdir(parents=True)
        for adapter in ("1.20.1", "1.20.2"):
            version_root = root / "fabric/src/version" / adapter
            version_root.mkdir()
            (version_root / "adapter.properties").write_text(
                f"adapter={adapter}\n", encoding="utf-8"
            )
        service = (
            root
            / "fabric/src/shared/resources/META-INF/services/"
            "fun.prof_chen.teamviewer.main_code.client.sdk.ClientAdapterFactory"
        )
        service.parent.mkdir(parents=True)
        service.write_text("example.Factory\n", encoding="utf-8")

    def test_compact_profiles_preserve_derived_build_contract(self) -> None:
        matrix = minecraft_targets.load_manifest()

        fabric_legacy = minecraft_targets.profile(matrix.properties, "fabric", "1.18.2")
        self.assertEqual("1.18.2", fabric_legacy["adapter_version"])
        self.assertEqual("17", fabric_legacy["game_java_version"])
        self.assertEqual("21", fabric_legacy["gradle_runtime_java"])
        self.assertEqual(
            "TeamViewRelay-Fabric-MC1.18-to-1.18.2-{mod_version}.jar",
            fabric_legacy["standalone_artifact"],
        )

        fabric_26 = minecraft_targets.profile(matrix.properties, "fabric", "26.2")
        self.assertEqual("26.1", fabric_26["adapter_version"])
        self.assertEqual("25", fabric_26["adapter_java_release"])
        self.assertEqual("teamviewer-adapter-26.2.jar", fabric_26["slim_adapter_artifact"])
        self.assertEqual(
            "26.1",
            minecraft_targets.profile(matrix.properties, "fabric", "26.1.2")["adapter_version"],
        )

        neoforge_legacy = minecraft_targets.profile(matrix.properties, "neoforge", "1.20.4")
        self.assertEqual("legacy", neoforge_legacy["build_kind"])
        self.assertEqual("stable", neoforge_legacy["stability"])
        self.assertEqual(
            "26.1",
            minecraft_targets.profile(matrix.properties, "neoforge", "26.1.2")["adapter_version"],
        )
        self.assertEqual(
            "26.1",
            minecraft_targets.profile(matrix.properties, "neoforge", "26.2")["adapter_version"],
        )

    def test_every_adapter_source_has_one_explainable_origin(self) -> None:
        matrix = minecraft_targets.load_manifest()
        for loader, targets in (("fabric", matrix.fabric), ("neoforge", matrix.neoforge)):
            for target in targets:
                plan = minecraft_targets.source_plan(matrix, loader, target)
                self.assertTrue(plan.entries, f"empty source plan for {loader}:{target}")
                self.assertEqual(len(plan.entries), len(set(plan.entries)))
                self.assertTrue(
                    all(entry.origin == "shared" or entry.origin.startswith(("compat:", "version:"))
                        for entry in plan.entries.values())
                )

    def test_version_override_wins_over_shared_and_compatibility_layer(self) -> None:
        matrix = minecraft_targets.Matrix(
            properties={
                "fabric.1.20.1.covers": "1.20.1",
                "fabric.1.20.1.adapter_version": "1.20.1",
                "neoforge_legacy_targets": "",
            },
            map_properties={},
            official=("1.20.1",),
            fabric=("1.20.1",),
            neoforge=(),
            neoforge_modern=(),
            neoforge_legacy=(),
        )
        with tempfile.TemporaryDirectory() as temp_dir:
            root = pathlib.Path(temp_dir)
            shared = root / "fabric/src/shared/java/example/Port.java"
            compat = root / "fabric/src/compat/port-v1/java/example/Port.java"
            version = root / "fabric/src/version/1.20.1/java/example/Port.java"
            for path, content in ((shared, "shared"), (compat, "compat"), (version, "version")):
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content, encoding="utf-8")
            (compat.parents[2] / "layer.properties").write_text(
                "adapters=1.20.1,1.20.2\n", encoding="utf-8"
            )
            with unittest.mock.patch.object(minecraft_targets, "ROOT", root):
                plan = minecraft_targets.source_plan(matrix, "fabric", "1.20.1")
            entry = plan.entries[pathlib.Path("java/example/Port.java")]
            self.assertEqual("version:1.20.1", entry.origin)
            self.assertEqual("version", entry.path.read_text(encoding="utf-8"))

    def test_conflicting_compatibility_layers_are_rejected(self) -> None:
        matrix = minecraft_targets.Matrix(
            properties={
                "fabric.1.20.1.covers": "1.20.1",
                "fabric.1.20.1.adapter_version": "1.20.1",
                "neoforge_legacy_targets": "",
            },
            map_properties={},
            official=("1.20.1",),
            fabric=("1.20.1",),
            neoforge=(),
            neoforge_modern=(),
            neoforge_legacy=(),
        )
        with tempfile.TemporaryDirectory() as temp_dir:
            root = pathlib.Path(temp_dir)
            for layer in ("first", "second"):
                layer_root = root / f"fabric/src/compat/{layer}"
                source = layer_root / "java/example/Port.java"
                source.parent.mkdir(parents=True, exist_ok=True)
                source.write_text(layer, encoding="utf-8")
                (layer_root / "layer.properties").write_text(
                    "adapters=1.20.1,1.20.2\n", encoding="utf-8"
                )
            with unittest.mock.patch.object(minecraft_targets, "ROOT", root):
                with self.assertRaisesRegex(SystemExit, "conflicting compatibility layers"):
                    minecraft_targets.source_plan(matrix, "fabric", "1.20.1")

    def test_unknown_adapter_is_rejected(self) -> None:
        matrix = self._source_layout_matrix()
        with self.assertRaisesRegex(SystemExit, "Unsupported fabric target"):
            minecraft_targets.source_plan(matrix, "fabric", "1.20.4")

    def test_empty_compatibility_layer_is_rejected(self) -> None:
        matrix = self._source_layout_matrix()
        with tempfile.TemporaryDirectory() as temp_dir:
            root = pathlib.Path(temp_dir)
            self._write_source_layout_skeleton(root)
            layer = root / "fabric/src/compat/empty"
            layer.mkdir(parents=True)
            (layer / "layer.properties").write_text(
                "adapters=1.20.1,1.20.2\n", encoding="utf-8"
            )
            with unittest.mock.patch.object(minecraft_targets, "ROOT", root):
                with self.assertRaisesRegex(SystemExit, "has no sources or resources"):
                    minecraft_targets.validate_source_layout(matrix)

    def test_single_adapter_compatibility_layer_is_rejected(self) -> None:
        matrix = self._source_layout_matrix()
        with tempfile.TemporaryDirectory() as temp_dir:
            root = pathlib.Path(temp_dir)
            self._write_source_layout_skeleton(root)
            layer_root = root / "fabric/src/compat/single"
            source_root = layer_root / "java/example"
            source_root.mkdir(parents=True)
            (source_root / "Port.java").write_text("class Port {}\n", encoding="utf-8")
            (layer_root / "layer.properties").write_text(
                "adapters=1.20.1\n", encoding="utf-8"
            )
            with unittest.mock.patch.object(minecraft_targets, "ROOT", root):
                with self.assertRaisesRegex(SystemExit, "must cover at least two adapters"):
                    minecraft_targets.validate_source_layout(matrix)

    def test_compatibility_layer_with_unknown_adapter_is_rejected(self) -> None:
        matrix = self._source_layout_matrix()
        with tempfile.TemporaryDirectory() as temp_dir:
            root = pathlib.Path(temp_dir)
            self._write_source_layout_skeleton(root)
            layer_root = root / "fabric/src/compat/unknown"
            source_root = layer_root / "java/example"
            source_root.mkdir(parents=True)
            (source_root / "Port.java").write_text("class Port {}\n", encoding="utf-8")
            (layer_root / "layer.properties").write_text(
                "adapters=1.20.1,1.20.4\n", encoding="utf-8"
            )
            with unittest.mock.patch.object(minecraft_targets, "ROOT", root):
                with self.assertRaisesRegex(SystemExit, "has invalid adapters"):
                    minecraft_targets.validate_source_layout(matrix)

    def test_identical_version_override_is_rejected(self) -> None:
        matrix = self._source_layout_matrix()
        with tempfile.TemporaryDirectory() as temp_dir:
            root = pathlib.Path(temp_dir)
            shared = root / "fabric/src/shared/java/example/Port.java"
            version = root / "fabric/src/version/1.20.1/java/example/Port.java"
            for path in (shared, version):
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("class Port {}\n", encoding="utf-8")
            with unittest.mock.patch.object(minecraft_targets, "ROOT", root):
                with self.assertRaisesRegex(SystemExit, "Redundant version override"):
                    minecraft_targets.source_plan(matrix, "fabric", "1.20.1")

    def test_repository_has_single_gradle_wrapper(self) -> None:
        wrappers = sorted(
            path.relative_to(minecraft_targets.ROOT)
            for path in minecraft_targets.ROOT.glob("**/gradle/wrapper/gradle-wrapper.properties")
        )

        self.assertEqual(
            [pathlib.Path("gradle/wrapper/gradle-wrapper.properties")],
            wrappers,
        )
        self.assertIn(
            "gradle-9.5.1-bin.zip",
            (minecraft_targets.ROOT / wrappers[0]).read_text(encoding="utf-8"),
        )

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
