"""Exercise installation, portability and the no-overwrite boundary."""

import importlib.util
from pathlib import Path
import re
import tempfile
import unittest
from urllib.parse import unquote, urlsplit


REPOSITORY = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("install_skill", REPOSITORY / "scripts/install_skill.py")
installer = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(installer)


def local_links(root: Path):
    """Read the inline Markdown links used by this skill's maintained documents."""
    for document in root.rglob("*.md"):
        if any(part in installer.IGNORED_NAMES for part in document.parts):
            continue
        for target in re.findall(r"\[[^\]]*\]\(([^\s)]+)\)", document.read_text()):
            url = urlsplit(target)
            if not url.scheme and not url.netloc and url.path:
                yield document, (document.parent / unquote(url.path)).resolve()


class InstallSkillTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.source = self.root / "source"
        self.source.mkdir()
        (self.source / "SKILL.md").write_text("# Skill\n")
        self.destination = self.root / "app/.agents/skills/androidcleannetwork"

    def test_installs_nested_resources_and_excludes_generated_artifacts(self):
        assets = self.source / "assets/Example"
        (assets / "Jsons").mkdir(parents=True)
        (assets / "Jsons/user.json").write_text('{"id": 1}')
        (assets / "build.gradle.kts").write_text("// example manifest\n")
        for name in (".gradle", "build", "__pycache__"):
            (assets / name).mkdir()
            (assets / name / "temporary").write_text("generated")
        (assets / "local.properties").write_text("generated")

        installer.install_skill(self.source, self.destination)

        self.assertEqual((self.destination / "assets/Example/Jsons/user.json").read_text(), '{"id": 1}')
        self.assertTrue((self.destination / "assets/Example/build.gradle.kts").is_file())
        for name in installer.IGNORED_NAMES:
            self.assertFalse((self.destination / "assets/Example" / name).exists())

    def test_existing_installation_is_preserved(self):
        self.destination.mkdir(parents=True)
        marker = self.destination / "user-customization.md"
        marker.write_text("Keep this content")
        with self.assertRaises(FileExistsError):
            installer.install_skill(self.source, self.destination)
        self.assertEqual(marker.read_text(), "Keep this content")
        self.assertFalse((self.destination / "SKILL.md").exists())

    def test_existing_broken_symlink_is_preserved(self):
        self.destination.parent.mkdir(parents=True)
        self.destination.symlink_to(self.root / "missing")
        with self.assertRaises(FileExistsError):
            installer.install_skill(self.source, self.destination)
        self.assertTrue(self.destination.is_symlink())

    def test_cannot_install_inside_source(self):
        with self.assertRaises(ValueError):
            installer.install_skill(self.source, self.source / "nested/androidcleannetwork")
        self.assertFalse((self.source / "nested").exists())

    def test_missing_source_entrypoint_leaves_destination_absent(self):
        with self.assertRaises(ValueError):
            installer.install_skill(self.root / "missing", self.destination)
        self.assertFalse(self.destination.parent.exists())

    def test_folder_name_matches_skill_name(self):
        with self.assertRaises(ValueError):
            installer.install_skill(self.source, self.root / "wrong-name")

    def test_real_installed_bundle_retains_all_local_references(self):
        installer.install_skill(installer.SKILL_SOURCE, self.destination)
        links = list(local_links(self.destination))
        self.assertTrue(links, "The check must actually discover the guide/example links.")
        for document, target in links:
            with self.subTest(document=document.name, target=target):
                self.assertTrue(target.is_relative_to(self.destination.resolve()), "Reference escapes skill bundle")
                self.assertTrue(target.exists(), "Reference is missing from standalone installation")
        self.assertTrue((self.destination / "assets/UserExample/build.gradle.kts").is_file())
        self.assertTrue((self.destination / "assets/UserExample/gradle/wrapper/gradle-wrapper.jar").is_file())
        self.assertTrue((self.destination / "assets/UserExample/gradlew").stat().st_mode & 0o111)

    def test_link_check_detects_a_missing_reference(self):
        (self.source / "SKILL.md").write_text("[Missing](references/missing.md)\n")
        links = list(local_links(self.source))
        self.assertEqual(len(links), 1)
        self.assertFalse(links[0][1].exists())


if __name__ == "__main__":
    unittest.main()
