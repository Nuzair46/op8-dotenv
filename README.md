# OnePlus 8 / LineageOS experiments

Source patches, guarded local patch tools, and reproducible notes from a OnePlus 8 repair and camera-port experiment. **This is not a ROM, a firmware bundle, or a universal installer.** No proprietary binaries or device backups are included.

Tested on one **OnePlus 8 IN2015 (`instantnoodle`)**, with a replacement AMOLED screen and the original fingerprint sensor, running **LineageOS 24.0 `20261103-NIGHTLY-instantnoodle`**. Stock camera files came from **OxygenOS `IN2015_13.1.0.593(EX01)`**. Findings recorded on 2026-11-08; other devices, builds, and replacement panels are untested.

| Component | Observed result | Scope and limitations |
| --- | --- | --- |
| Touch after sleep | Worked after reboot and sleep/wake tests | Kernel workaround for S6SY771 firmware `0x17870131`; not a touch firmware update |
| Adaptive brightness | Followed covered/uncovered ambient light | Disabled LineageOS one-shot brightness sampling |
| Fingerprint | Enrollment and unlock worked after reboot; tested unenrolled finger was rejected | **Experimental calibration-ready gate bypass. Calibration remains broken; security is not validated.** |
| OnePlus Camera | Default for ordinary still-camera launch; tested captures saved | Original 5.9.84 app with processing dependencies and a JPEG crash guard |
| True 48 MP | **Unsupported** | 8000 x 6000 stream configuration failed; investigation stopped without a 48 MP fix |

Start with the relevant guide:

- [Scope, prerequisites, and recovery](docs/setup.md)
- [Touch resume source patch](docs/touch.md)
- [Adaptive brightness setting](docs/brightness.md)
- [Experimental fingerprint workaround](docs/fingerprint.md)
- [OnePlus Camera port and rollback](docs/camera.md)
- [What was actually tested](docs/validation.md)
- [Public-release policy](PUBLICATION.md) and [security limitations](SECURITY.md)

## Local binary patching

Requires Python 3.10 or later, using only its standard library. Supply your own legally obtained input files in a **private directory outside this checkout**. The tool checks the complete input SHA-256, every edited byte, and the complete output SHA-256. It never writes to the input, overwrites an existing output, invokes ADB, or flashes a device.

```sh
python tools/patch_binary.py --recipe patches/camera-jpeg-null-guard.json --input ../private/libAlgoProcess.so --output ../private/libAlgoProcess.patched.so
```

Use `--reverse` with a matching patched input and a new output path to reproduce the original bytes. Fingerprint patching additionally requires `--acknowledge-experimental-biometric-risk`; read its guide first.

```sh
python -m unittest discover -s tests -v
python tools/audit_public.py
```

The patcher is exercised with synthetic test data. The release was also checked privately against the original libraries: forward and reverse output hashes matched both recipes. The private libraries and test outputs are not part of this repository.

## Attribution and licensing

Original tooling and documentation: [MIT](LICENSE). The touch patch modifies the [LineageOS OnePlus SM8250 kernel](https://github.com/LineageOS/android_kernel_oneplus_sm8250/tree/98a7970af93b7e01ee77156e7fa0eb7e059a57f0); use it under that kernel's GPL-2.0 terms, as detailed in [NOTICE](NOTICE). Vendor filenames, hashes, and patch recipes identify files the user must supply; this repository grants no rights to redistribute those vendor files. This project is not affiliated with OnePlus, LineageOS, Goodix, or Qualcomm.
