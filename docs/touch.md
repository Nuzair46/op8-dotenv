# Touchscreen resume workaround

The replacement screen worked initially, but touch stopped after sleep on LineageOS. Resetting only the touch controller reproduced the failure. A kernel change that preserved this panel across resume restored touch in the tested reboot and sleep/wake checks. This was **not** a touch firmware flash.

The change is restricted to the Samsung **S6SY771** driver when `ts->panel_data.TP_FW == 0x17870131`. That value identifies a firmware version, not a unique device. Matching it alone does not prove every replacement screen with that version will behave identically.

Base source:

- Repository: <https://github.com/LineageOS/android_kernel_oneplus_sm8250>
- Commit: `98a7970af93b7e01ee77156e7fa0eb7e059a57f0`
- Branch used at the time: `lineage-23.2` (despite the installed ROM being LineageOS 24.0)
- Driver: `drivers/input/touchscreen/oplus_touchscreen/Samsung/S6SY771/sec_drivers_s6sy771.c`
- Observed kernel release: `4.19.325-cip136-st20-perf-g98a7970af93b`

In a separate kernel checkout at that exact commit, first inspect the diff and check that it applies:

```sh
git rev-parse HEAD
git apply --check /path/to/op8-dotenv/patches/touch-s6sy771-resume.patch
git apply /path/to/op8-dotenv/patches/touch-s6sy771-resume.patch
```

The added condition sets `skip_reset_in_resume = true` during probe for that firmware version. No other controller's reset behavior is changed.

The original experiment built with the running kernel's configuration and Android Clang r596125, from [this pinned toolchain archive](https://android.googlesource.com/platform/prebuilts/clang/host/linux-x86/+archive/29182889399818f97baa65ea238c80e8117981eb/clang-r596125.tar.gz). Packaging preserved the original boot header arguments, ramdisk, and DTB; their contents were compared before deployment. A known-good boot image was kept privately. Those device-specific artifacts are not supplied here.

This is a source patch, not a complete kernel-build distribution. Follow the matching device/ROM kernel build and boot-image tooling, validate the resulting image, and test a temporary boot where supported before a persistent change. Do not apply the prebuilt boot image from someone else's phone. Keep recovery and a matching original boot image available.

Validation should include repeated sleep/wake, normal reboot, charging, and longer idle periods. Only short reboot and sleep/wake checks were confirmed here; broad panel compatibility and long-term stability are unproven. To undo the source change, use `git apply --reverse --check` and then `git apply --reverse`; to undo an installed kernel, restore the matching known-good boot image using your device's recovery procedure.
