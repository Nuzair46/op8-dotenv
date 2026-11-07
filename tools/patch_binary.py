#!/usr/bin/env python3
"""Offline, exact-hash binary patching. Never connects to a device."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import sys


class PatchError(ValueError):
    pass


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def validate_recipe(recipe):
    if not isinstance(recipe, dict) or recipe.get("schema_version") != 1:
        raise PatchError("Unsupported recipe schema")
    if type(recipe.get("experimental_biometric")) is not bool:
        raise PatchError("Recipe must explicitly declare biometric risk")
    for key in ("original_sha256", "patched_sha256"):
        value = recipe.get(key)
        if not isinstance(value, str) or not re.fullmatch(r"[0-9a-f]{64}", value):
            raise PatchError("Invalid SHA-256 in recipe")
    if recipe["original_sha256"] == recipe["patched_sha256"]:
        raise PatchError("Input and output hashes must differ")
    edits = recipe.get("edits")
    if not isinstance(edits, list) or not edits:
        raise PatchError("Recipe has no edits")
    parsed = []
    for edit in edits:
        try:
            offset = int(edit["offset"], 0)
            before = bytes.fromhex(edit["before"])
            after = bytes.fromhex(edit["after"])
        except (KeyError, TypeError, ValueError) as exc:
            raise PatchError("Invalid edit format") from exc
        if offset < 0 or not before or len(before) != len(after) or before == after:
            raise PatchError("Edits must replace nonempty equal-length byte sequences")
        parsed.append((offset, before, after))
    parsed.sort(key=lambda entry: entry[0])
    end = 0
    for offset, before, _ in parsed:
        if offset < end:
            raise PatchError("Overlapping edits are forbidden")
        end = offset + len(before)
    return parsed


def transform(data, recipe, *, reverse=False, acknowledge=False):
    edits = validate_recipe(recipe)
    if recipe["experimental_biometric"] and not acknowledge:
        raise PatchError("Experimental biometric recipe requires explicit acknowledgement")
    source_key, target_key = ("original_sha256", "patched_sha256")
    if reverse:
        source_key, target_key = target_key, source_key
    if sha256(data) != recipe[source_key]:
        raise PatchError("Input SHA-256 mismatch; no output written")
    result = bytearray(data)
    for offset, before, after in edits:
        if reverse:
            before, after = after, before
        if offset + len(before) > len(data) or data[offset:offset + len(before)] != before:
            raise PatchError(f"Original bytes mismatch at offset {offset:#x}")
        result[offset:offset + len(after)] = after
    result = bytes(result)
    if sha256(result) != recipe[target_key]:
        raise PatchError("Output SHA-256 mismatch; no output written")
    return result


def patch_file(input_path, output_path, recipe, *, reverse=False, acknowledge=False):
    input_path, output_path = Path(input_path), Path(output_path)
    if input_path.resolve() == output_path.resolve():
        raise PatchError("Input and output must be different files")
    if output_path.exists() or output_path.is_symlink():
        raise PatchError("Output already exists; refusing to overwrite")
    result = transform(input_path.read_bytes(), recipe, reverse=reverse, acknowledge=acknowledge)
    # Exclusive creation also rejects a file/symlink introduced after the check.
    # A failed write may leave a partial new output; it is never an input file.
    with output_path.open("xb") as stream:
        stream.write(result)
        stream.flush()
        os.fsync(stream.fileno())
    if sha256(output_path.read_bytes()) != sha256(result):
        raise PatchError("On-disk verification failed; do not deploy the output")
    return sha256(result)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--recipe", type=Path, required=True)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--reverse", action="store_true")
    parser.add_argument("--acknowledge-experimental-biometric-risk", action="store_true")
    args = parser.parse_args()
    try:
        recipe = json.loads(args.recipe.read_text(encoding="utf-8"))
        digest = patch_file(args.input, args.output, recipe, reverse=args.reverse,
                            acknowledge=args.acknowledge_experimental_biometric_risk)
    except (OSError, ValueError) as exc:
        print(f"Refused: {exc}", file=sys.stderr)
        return 1
    print(f"Verified output SHA-256: {digest}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
