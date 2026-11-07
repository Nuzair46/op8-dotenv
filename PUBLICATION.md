# Public-release boundary

This repository was assembled in a new directory from an explicit allowlist. The diagnostic workspace and its history were not imported. GitHub receives source text, patch recipes, generic configuration, library hashes, and rewritten documentation only.

Excluded from publication:

- Device serials, IMEIs, sensor identifiers, account details, local usernames/paths, credentials, API tokens, ADB keys, and signing keys.
- Fingerprints, biometric templates, factory calibration files, persist/partition dumps, boot images, device backups, and donor-device data.
- APKs, shared libraries, ROM/firmware archives, SDK/toolchain archives, decompiled vendor source, and third-party proprietary binaries.
- Photos, videos, screenshots, raw logs, bugreports, RE tool sessions, and temporary experiment outputs.

Public SHA-256 values identify software versions, not a device. Model identifiers, build versions, driver firmware versions, Android package/partition paths, and upstream commit hashes are intentional reproducibility information.

Run these checks before publishing any change:

```sh
python -m unittest discover -s tests -v
python tools/audit_public.py
```

`PUBLIC_FILES.txt` is the complete reviewed release file list. The scanner refuses unexpected files, including files that Git would ignore, except `.git` internals and Python bytecode caches. `--deny-file` optionally adds a private newline-delimited list of device-specific strings; keep that file outside the repository. Only match counts/file paths are reported, never the private values.

The scanner is a guard, not proof that text is harmless. Review the complete proposed contents and the outgoing Git history as well. An accidentally committed secret can remain in history even after deletion; if exposure occurs, revoke it and address history separately. Do not create blobs, upload releases, or push private source history before the content has been reviewed.

The initial publication was reviewed as a clean text-only release. Future contributions must follow the same boundary. No CI job uploads artifacts or requests secrets; the tests run locally using the Python standard library.
