import copy
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from patch_binary import PatchError, patch_file, sha256, transform


class PatchTests(unittest.TestCase):
    def setUp(self):
        self.original = b"synthetic input only"
        self.patched = b"SYNTHETIC input only"
        self.recipe = {
            "schema_version": 1,
            "experimental_biometric": False,
            "original_sha256": sha256(self.original),
            "patched_sha256": sha256(self.patched),
            "edits": [{"offset": "0x0", "before": b"synthetic".hex(), "after": b"SYNTHETIC".hex()}],
        }

    def test_forward_and_reverse(self):
        self.assertEqual(transform(self.original, self.recipe), self.patched)
        self.assertEqual(transform(self.patched, self.recipe, reverse=True), self.original)

    def test_wrong_version_and_double_patch_rejected(self):
        for data in (b"another version", self.patched):
            with self.subTest(data=data), self.assertRaisesRegex(PatchError, "Input SHA"):
                transform(data, self.recipe)

    def test_wrong_preimage_rejected_even_with_matching_file_hash(self):
        self.recipe["edits"][0]["before"] = b"different".hex()
        with self.assertRaisesRegex(PatchError, "Original bytes"):
            transform(self.original, self.recipe)

    def test_output_hash_is_required(self):
        self.recipe["patched_sha256"] = "0" * 64
        with self.assertRaisesRegex(PatchError, "Output SHA"):
            transform(self.original, self.recipe)

    def test_overlapping_out_of_bounds_and_variable_length_edits_rejected(self):
        bad_edits = [
            self.recipe["edits"] * 2,
            [{"offset": "0xffff", "before": "61", "after": "62"}],
            [{"offset": "0x0", "before": "61", "after": "6263"}],
            [{"offset": "-1", "before": "61", "after": "62"}],
        ]
        for edits in bad_edits:
            recipe = copy.deepcopy(self.recipe)
            recipe["edits"] = edits
            with self.subTest(edits=edits), self.assertRaises(PatchError):
                transform(self.original, recipe)

    def test_biometric_opt_in_required_in_both_directions(self):
        self.recipe["experimental_biometric"] = True
        for data, reverse in ((self.original, False), (self.patched, True)):
            with self.assertRaisesRegex(PatchError, "acknowledgement"):
                transform(data, self.recipe, reverse=reverse)
        self.assertEqual(transform(self.original, self.recipe, acknowledge=True), self.patched)

    def test_missing_risk_declaration_and_bad_schema_rejected(self):
        for key, value in (("experimental_biometric", "false"), ("schema_version", 2),
                           ("original_sha256", "bad"), ("edits", [])):
            recipe = dict(self.recipe, **{key: value})
            with self.subTest(key=key), self.assertRaises(PatchError):
                transform(self.original, recipe)

    def test_file_write_preserves_input_and_refuses_overwrite(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "input"
            target = Path(directory) / "output"
            source.write_bytes(self.original)
            patch_file(source, target, self.recipe)
            self.assertEqual(source.read_bytes(), self.original)
            self.assertEqual(target.read_bytes(), self.patched)
            with self.assertRaises(PatchError):
                patch_file(source, target, self.recipe)
            with self.assertRaises(PatchError):
                patch_file(source, source, self.recipe)

    def test_validation_failure_creates_no_output(self):
        with tempfile.TemporaryDirectory() as directory:
            source, target = Path(directory) / "input", Path(directory) / "output"
            source.write_bytes(b"wrong build")
            with self.assertRaises(PatchError):
                patch_file(source, target, self.recipe)
            self.assertFalse(target.exists())

    def test_optimization_cannot_disable_validation(self):
        code = "from patch_binary import transform; transform(b'wrong', " + repr(self.recipe) + ")"
        result = subprocess.run([sys.executable, "-O", "-c", code],
                                cwd=Path(__file__).resolve().parents[1] / "tools",
                                capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Input SHA-256 mismatch", result.stderr)


if __name__ == "__main__":
    unittest.main()
