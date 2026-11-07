# Experimental fingerprint enrollment workaround

**This is not recalibration and is not a security-validated fingerprint repair.** A replacement display changed the optical conditions. Factory calibration remained unresolved. Images displayed on another phone did not establish a valid calibration; this project does not supply calibration targets, donor calibration data, or biometric templates.

The workaround replaces one conditional branch in `goodix::DelmarFingerprintCore::checkEnrollAuthReady` with an AArch64 NOP. It skips an early calibration-ready rejection so enrollment can proceed. The trusted matcher, matching thresholds, fingerprint templates, and sensor calibration data are not patched by this recipe.

On the test phone, enrollment completed and the enrolled finger unlocked after reboot. A tested unenrolled finger was rejected. That small test does **not** establish a false-accept rate, spoof resistance, or that only the owner can unlock. Bypassing readiness checks may permit poor-quality enrollment; retain a strong PIN and do not treat the result as equivalent to a factory-calibrated sensor.

Exact target: `/odm/lib64/libgf_hal_G6_oplus.so`. The [recipe](../patches/fingerprint-enrollment-gate.json) includes the full original/patched SHA-256 and the four-byte edit at file offset `0x29dcc` (`81000054` to `1f2003d5`). Unknown builds are refused.

Keep the input, outputs, and backups outside the checkout:

```sh
python tools/patch_binary.py --recipe patches/fingerprint-enrollment-gate.json --input ../private/libgf_hal_G6_oplus.so --output ../private/libgf_hal_G6_oplus.experimental.so --acknowledge-experimental-biometric-risk
```

This command only creates a local file. It does not deploy the workaround. On the tested ROM, deployment used an existing `/odm` remount overlay, with root ownership, mode `0644`, and SELinux label `u:object_r:vendor_file:s0`. Preserve the original library privately; verify the installed hash and metadata before restarting. Do not overwrite `persist`, rename another sensor's calibration files, clear biometric storage by hand, or weaken SELinux to deploy it.

Use normal Settings for enrollment and removal. After reboot, unlock with the PIN once, then verify the enrolled finger and several unenrolled fingers. Stop using the workaround if unexpected acceptance, HAL instability, or poor reliability occurs. The patch does not guarantee any particular enrollment or unlock outcome on another screen.

To produce the original library from the exact patched one:

```sh
python tools/patch_binary.py --recipe patches/fingerprint-enrollment-gate.json --input ../private/libgf_hal_G6_oplus.experimental.so --output ../private/libgf_hal_G6_oplus.restored.so --reverse --acknowledge-experimental-biometric-risk
```

Restore the original bytes and original metadata through the same scoped overlay mechanism, then reboot. This does not restore or erase enrollment/calibration state. Do not remove every remount overlay as a shortcut; the camera may use the same `/odm` overlay. Reassess compatibility after every ROM update rather than patching a new binary at the old offset.
