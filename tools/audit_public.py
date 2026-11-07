#!/usr/bin/env python3
"""Audit a text-only release tree. Does not contact GitHub or upload anything."""

import argparse
from pathlib import Path, PurePosixPath
import re
import sys
from urllib.parse import unquote, urlsplit


RULES = {
    "private-key material": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----"),
    "GitHub token": re.compile(r"\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,})\b"),
    "cloud access key": re.compile(r"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b"),
    "Google API key": re.compile(r"\bAIza[0-9A-Za-z_-]{30,}\b"),
    "bearer credential": re.compile(r"(?i)\bBearer\s+[A-Za-z0-9._~-]{20,}"),
    "local Windows profile": re.compile(r"(?i)[a-z]:[\\/](?:users|documents and settings)[\\/][^\s/\\]+"),
    "local Unix profile": re.compile(r"/(?:home|Users|mnt/[a-z]/Users)/[A-Za-z0-9_.-]+"),
    "email address": re.compile(r"\b[A-Za-z0-9_.+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b"),
    "URL credential": re.compile(r"https?://[^\s/]+:[^\s/@]+@"),
}

# Android HAL library filenames contain '@' but are not contact addresses.
HAL_FILENAMES = {
    "vendor.qti.hardware.camera.postproc" + "@" + "1.0.so",
    "vendor.qti.hardware.camera.postproc" + "@" + "1.0-service-impl.so",
}


def listed_files(root):
    lines = (root / "PUBLIC_FILES.txt").read_text(encoding="utf-8").splitlines()
    names = [line.strip() for line in lines if line.strip() and not line.startswith("#")]
    if len(names) != len(set(names)):
        raise ValueError("Duplicate allowlist entry")
    for name in names:
        parts = PurePosixPath(name)
        if parts.is_absolute() or ".." in parts.parts or "\\" in name or ":" in name:
            raise ValueError("Unsafe allowlist entry")
    if "PUBLIC_FILES.txt" not in names:
        raise ValueError("Allowlist must include itself")
    return set(names)


def audit(root, deny_terms=()):
    root = Path(root).resolve()
    try:
        allowed = listed_files(root)
    except (ValueError, OSError) as exc:
        return [str(exc)]
    failures, found = [], set()
    for path in sorted(root.rglob("*")):
        relative = path.relative_to(root)
        if ".git" in relative.parts or "__pycache__" in relative.parts:
            continue
        name = relative.as_posix()
        if path.is_symlink():
            failures.append(f"{name}: symlinks are not allowed")
            continue
        if not path.is_file():
            continue
        found.add(name)
        if name not in allowed:
            failures.append(f"{name}: outside the reviewed allowlist")
            continue
        data = path.read_bytes()
        if len(data) > 128 * 1024 or b"\x00" in data:
            failures.append(f"{name}: oversized or binary content")
            continue
        try:
            content = data.decode("utf-8")
        except UnicodeDecodeError:
            failures.append(f"{name}: not UTF-8 text")
            continue
        for label, pattern in RULES.items():
            matches = [match.group() for match in pattern.finditer(content)]
            if label == "email address":
                matches = [match for match in matches if match not in HAL_FILENAMES]
            if matches:
                failures.append(f"{name}: possible {label}")
        if any(term.casefold() in content.casefold() for term in deny_terms if term):
            failures.append(f"{name}: private denylist match")
        if path.suffix == ".md":
            for link in re.findall(r"\]\(([^\s)]+)\)", content):
                parsed = urlsplit(link)
                if parsed.scheme or parsed.netloc or not parsed.path:
                    continue
                target = (path.parent / unquote(parsed.path)).resolve()
                if not target.is_relative_to(root) or not target.is_file():
                    failures.append(f"{name}: broken or out-of-tree relative link")
    failures.extend(f"{name}: allowlisted file missing" for name in sorted(allowed - found))
    return failures


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--deny-file", type=Path,
                        help="Private newline-delimited identifiers; never commit this file")
    args = parser.parse_args()
    try:
        denied = args.deny_file.read_text(encoding="utf-8").splitlines() if args.deny_file else []
        failures = audit(args.root, denied)
    except (OSError, ValueError) as exc:
        print(f"Audit failed: {exc}", file=sys.stderr)
        return 1
    if failures:
        for failure in failures:
            print(failure, file=sys.stderr)
        return 1
    print(f"PASS: {len(listed_files(args.root))} allowlisted text files; no flagged content or broken local links")
    return 0


if __name__ == "__main__":
    sys.exit(main())
