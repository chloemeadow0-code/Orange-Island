"""Regression checks for source-distribution failures found during the audit."""
import hashlib
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest
from unittest.mock import patch

import pack_sandbox_sources as pack


class SourceDistributionTests(unittest.TestCase):
    def test_package_headers_are_not_overwritten_by_file_entries(self):
        database = "P:busybox\nV:1.37.0-r8\nL:GPL-2.0-only\no:busybox\nc:" + "a" * 40
        database += "\nF:bin\nR:busybox\nV:not-a-package-version\n"
        self.assertEqual(pack.read_packages(database)[0]["version"], "1.37.0-r8")

    def test_missing_source_commit_fails(self):
        with self.assertRaisesRegex(ValueError, "lacks c"):
            pack.read_packages("P:busybox\nV:1.37.0-r8\nL:GPL-2.0-only\no:busybox\n")

    def test_windows_line_endings_preserve_multiple_packages(self):
        first = "P:busybox\nV:1.37.0-r8\nL:GPL-2.0-only\no:busybox\nc:" + "a" * 40
        second = "P:scanelf\nV:1.3.8-r1\nL:GPL-2.0-only\no:pax-utils\nc:" + "b" * 40
        database = first + "\n\n" + second + "\n"
        self.assertEqual(pack.read_packages(database), pack.read_packages(database.replace("\n", "\r\n")))
        self.assertEqual(len(pack.read_packages(database)), 2)

    def test_newer_recipe_is_rejected_before_sources_are_downloaded(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            recipe = root / ("a" * 40) / "busybox" / "recipe" / "APKBUILD"
            recipe.parent.mkdir(parents=True)
            recipe.write_text("pkgver=1.37.0\npkgrel=14\n")
            with patch.object(pack, "recipe_files", return_value=[recipe]), patch.object(pack, "download") as fetch:
                with self.assertRaisesRegex(ValueError, "Version mismatch"):
                    pack.collect_origin("busybox", "a" * 40, [{"version": "1.37.0-r8"}], "3.21", root, root / "stage")
                fetch.assert_not_called()

    def test_modified_source_input_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            recipe = root / ("a" * 40) / "busybox" / "recipe" / "APKBUILD"
            recipe.parent.mkdir(parents=True)
            expected = hashlib.sha512(b"original patch").hexdigest()
            recipe.write_text(f'pkgver=1.37.0\npkgrel=8\nsource="fix.patch"\nsha512sums="{expected}  fix.patch"\n')
            source = recipe.parent / "fix.patch"
            source.write_bytes(b"modified patch")
            with patch.object(pack, "recipe_files", return_value=[recipe, source]):
                with self.assertRaisesRegex(ValueError, "Source checksum mismatch"):
                    pack.collect_origin("busybox", "a" * 40, [{"version": "1.37.0-r8"}], "3.21", root, root / "stage")

    def test_archive_cannot_omit_an_installed_origin(self):
        database = "P:scanelf\nV:1.3.8-r1\nL:GPL-2.0-only\no:pax-utils\nc:" + "b" * 40 + "\n"
        manifest = {"packages": pack.read_packages(database), "origins": []}
        files = {"manifest.json": json.dumps(manifest).encode(), "alpine/installed-packages.txt": database.encode()}
        files["SHA256SUMS"] = "".join(f"{hashlib.sha256(data).hexdigest()}  {name}\n" for name, data in files.items()).encode()
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "incomplete.tar.gz"
            with tarfile.open(output, "w:gz") as archive:
                for name, data in files.items():
                    info = tarfile.TarInfo("source/" + name)
                    info.size = len(data)
                    archive.addfile(info, io.BytesIO(data))
            with self.assertRaisesRegex(ValueError, "Not all installed origins"):
                pack.verify_archive(output)


if __name__ == "__main__":
    unittest.main()
