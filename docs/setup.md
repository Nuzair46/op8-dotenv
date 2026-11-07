# Scope, prerequisites, and recovery

This repository documents an existing installation. It does not unlock the bootloader, install LineageOS, erase data, or provide Google apps. For initial installation, use the current [official instantnoodle device page](https://wiki.lineageos.org/devices/instantnoodle/) and [installation guide](https://wiki.lineageos.org/devices/instantnoodle/install/). Do not substitute OnePlus 8T or 8 Pro images.

The observed system was LineageOS 24.0, build `20261103-NIGHTLY-instantnoodle`, with OxygenOS `IN2015_13.1.0.593(EX01)` as the source of stock camera files. These are historical test versions, not a claim about today's latest release. File hashes in the recipes are mandatory compatibility checks, not optional hints.

For the kernel experiment, use a matching kernel source/configuration and keep a known-good boot image and working recovery available. Determine your own active slot; no slot name from another device is safe to assume. Never overwrite both boot slots as part of this experiment. The original experiment preserved the device's boot header, ramdisk, and DTB while changing only the kernel.

Camera and fingerprint deployment used ADB root/remount OverlayFS on the development-capable ROM. They require that capability; a normal locked production device is not supported. Remount overlays can affect OTA updates and do not establish a fully verified boot chain. See AOSP's [OverlayFS notes](https://android.googlesource.com/platform/system/core/+/refs/heads/android16-release/fs_mgr/README.overlayfs.md).

Before changing a file, privately record its SHA-256, owner, mode, SELinux label, whether it already existed, and a verified backup. Record property/settings values separately. Keep this record off GitHub. Stop on any hash, partition, package-signature, or source mismatch. Do not replace existing vendor libraries with a superficially similar file from another build.

Rollback should restore only the files and settings that the individual change touched. **`adb enable-verity` is not a component-specific undo:** it can discard all ADB remount overlays, including other working fixes. Likewise, do not relock the bootloader while modified boot/system components are present. Follow the ROM's recovery/update instructions when returning to stock or updating.

After a deployment, verify file hashes and labels, reboot, unlock once with the PIN, and test the specific feature. Turn root debugging off when finished. This repository provides offline patch tooling and an integration record, rather than an unattended phone installer.
