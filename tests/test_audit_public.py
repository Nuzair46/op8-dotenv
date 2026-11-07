from pathlib import Path
import hashlib
import json
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from audit_public import audit


class AuditTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        (self.root / "PUBLIC_FILES.txt").write_text("PUBLIC_FILES.txt\nREADME.md\n")
        (self.root / "README.md").write_text("Synthetic public example.\n")

    def test_clean_tree_passes(self):
        self.assertEqual(audit(self.root), [])

    def test_unexpected_files_fail_even_if_ignored_by_git(self):
        (self.root / "private-backup.img").write_bytes(b"synthetic")
        self.assertTrue(any("allowlist" in error for error in audit(self.root)))

    def test_private_denylist_reports_no_value(self):
        term = "synthetic-identifier-for-test"
        (self.root / "README.md").write_text(term)
        failures = audit(self.root, [term])
        self.assertTrue(failures)
        self.assertNotIn(term, " ".join(failures))

    def test_binary_and_missing_files_fail(self):
        (self.root / "README.md").write_bytes(b"text\x00binary")
        self.assertTrue(audit(self.root))
        (self.root / "README.md").unlink()
        self.assertTrue(any("missing" in error for error in audit(self.root)))

    def test_broken_link_fails(self):
        (self.root / "README.md").write_text("[missing](missing.md)")
        self.assertTrue(any("relative link" in error for error in audit(self.root)))

    def test_synthetic_token_is_detected(self):
        (self.root / "README.md").write_text("ghp_" + "a" * 36)
        self.assertTrue(any("token" in error for error in audit(self.root)))

    def test_hal_filename_exception_does_not_hide_email(self):
        name = "vendor.qti.hardware.camera.postproc" + "@" + "1.0.so"
        (self.root / "README.md").write_text(name)
        self.assertEqual(audit(self.root), [])
        (self.root / "README.md").write_text(name + " person" + "@" + "example.com")
        self.assertTrue(any("email address" in error for error in audit(self.root)))

    def artwork_fixture(self):
        payload = b"\x89PNG\r\n\x1a\nsynthetic-artwork-test"
        (self.root / "art.png").write_bytes(payload)
        (self.root / "PUBLIC_FILES.txt").write_text("PUBLIC_FILES.txt\nREADME.md\nPUBLIC_ASSETS.json\nart.png\n")
        manifest = {"schema_version": 1, "files": [{"path": "art.png", "kind": "png", "size": len(payload),
                     "sha256": hashlib.sha256(payload).hexdigest()}]}
        (self.root / "PUBLIC_ASSETS.json").write_text(json.dumps(manifest))
        return manifest

    def test_reviewed_artwork_passes_and_changed_bytes_fail(self):
        self.artwork_fixture()
        self.assertEqual(audit(self.root), [])
        with (self.root / "art.png").open("ab") as stream:
            stream.write(b"unreviewed change")
        self.assertTrue(any("artwork" in error for error in audit(self.root)))

    def test_artwork_cannot_approve_unlisted_file(self):
        manifest = self.artwork_fixture()
        manifest["files"][0]["path"] = "other.png"
        (self.root / "PUBLIC_ASSETS.json").write_text(json.dumps(manifest))
        self.assertTrue(audit(self.root))

    def test_artwork_manifest_cannot_allow_apk(self):
        manifest = self.artwork_fixture()
        manifest["files"][0]["kind"] = "apk"
        (self.root / "PUBLIC_ASSETS.json").write_text(json.dumps(manifest))
        self.assertTrue(audit(self.root))

    def test_private_identifiers_inside_artwork_are_rejected(self):
        self.artwork_fixture()
        failures = audit(self.root, ["synthetic-artwork-test"])
        self.assertTrue(failures)
        self.assertNotIn("synthetic-artwork-test", " ".join(failures))


if __name__ == "__main__":
    unittest.main()
