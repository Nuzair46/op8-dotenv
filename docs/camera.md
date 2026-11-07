# OnePlus Camera on LineageOS

This is an integration record for the tested build, not a universal flashable camera port. It keeps the original OnePlus app signature and uses locally supplied matching stock libraries. **True 48 MP remains unsupported.** See the [validation matrix](validation.md) for the difference between saved/decoded media and log-only observations.

## Required files and identity

Use your own legally obtained OxygenOS `IN2015_13.1.0.593(EX01)` files. No APK, shared library, firmware, or extracted vendor source is distributed here. Do not download an arbitrary re-signed APK and assume these hashes or permissions still apply.

| Package | Version | SHA-256 of original APK |
| --- | --- | --- |
| `com.oneplus.camera` | 5.9.84 | `7644e2095f21c45073475c9f285751d2e4f10281d3c939b2ab4e26582be6409e` |
| `com.oneplus.camera.pictureprocessing` | 5.9.10 | `0d36b1384f7466b2861af8ae30270c5143e0e506c43cb22776c9edabe8cafe92` |

Both APKs had signer SHA-256 `4681ad50cafc580edfe027bd3fe593254e72cd2def1b351fea306ccf6220cf07`. Verify with Android SDK `apksigner verify --print-certs`. The separate `com.oneplus.camera.service` package was not installed: its system shared-UID signature did not match LineageOS. Do not bypass system package signature checks.

## Integration sequence

1. Read [setup and rollback requirements](setup.md). Save original file metadata and property values privately. Check every supplied file against [the library manifest](../manifests/camera-libraries.json). `add` means the path was absent on the tested ROM; `label_only` means its existing bytes were preserved. If your device differs, stop and reassess rather than applying the inventory blindly.
2. Prepare the JPEG guard offline using [the recipe](../patches/camera-jpeg-null-guard.json) and `tools/patch_binary.py`. The input is the original `libAlgoProcess.so`; the installed file must have the recipe's patched hash.
3. Using the ROM's supported root/remount overlay workflow, supply the missing libraries at the paths in the manifest. The original library versions already supplied by LineageOS must remain intact for `label_only` entries, even where stock versions differ. Set the scoped library labels to `u:object_r:same_process_hal_file:s0`, owner root:root, mode `0644`; use `0755` for required directories. Verify every installed hash and metadata value. Do not make SELinux permissive.
4. Install the two original signed APKs normally. Also expose the same camera APK at `/system_ext/app/OnePlusCameraLocal/OnePlusCamera.apk` through the system overlay so it qualifies as a system app. Add [the scoped hidden-API configuration](../config/hiddenapi-oneplus-camera-local.xml) to `/system_ext/etc/sysconfig/hiddenapi-oneplus-camera-local.xml`. Files used root:root, `0644`, and `u:object_r:system_file:s0`; directories used `0755`. Reboot so PackageManager loads the system app and XML. The observed package flags included `SYSTEM` and `UPDATED_SYSTEM_APP`.
5. Allow camera, microphone, and media permissions as needed through normal Android permission controls. The processing package also needed CAMERA permission. No global hidden-API policy change or privileged-permission allowlist was used. Location access is not required by this procedure.
6. Record and inspect `persist.camera.privapp.list` and `persist.vendor.camera.privapp.list`. On the tested phone both were empty; setting each to `com.oneplus.camera` restored expected sensor resolutions. On another ROM, preserve existing entries and follow that ROM's list format rather than overwriting its contents. Reboot after changing the properties and verify persistence.
7. Select OnePlus Camera for normal camera launch and test saves, not just preview. Leave Aperture available as a fallback. In this experiment OnePlus Camera became the preferred `android.media.action.STILL_IMAGE_CAMERA` handler. The launcher dock shortcut was not replaced, and IMAGE_CAPTURE/VIDEO_CAPTURE chooser behavior was not replaced globally.

The manifest lists 21 added library paths (one under `/odm`) and 18 existing files whose labels were adjusted. The C++ compatibility entry comes from the installed LineageOS `/system/lib64/libstdc++.so`, copied unchanged to `/vendor/lib64/libstdc++.so`; it is not an arbitrary OxygenOS replacement. `/vendor/etc/public.libraries.txt` already listed `libAlgoProcess.so` and was left unchanged. The fingerprint library shares `/odm` but was not modified by camera integration.

The dependency and labeling work was informed by [PixelOS's OnePlus camera integration](https://github.com/PixelOS-OnePlus-8T-Kebab/android_device_oneplus_sm8250-common/commit/5d2b9ebfcd29a09c95e74783b030ec582d93f27a) and [Oplus SELinux file contexts](https://github.com/PixelOS-OnePlus-8T-Kebab/android_hardware_oplus/blob/f58f669daa92ecd90bb384eb06bd193ee7847346/sepolicy/qti/vendor/file_contexts). These links are references, not a claim that those projects support this exact experiment. See also Android's [native library namespace documentation](https://source.android.com/docs/core/permissions/namespaces_libraries).

## JPEG crash guard

The stock algorithm library attempted to use an unavailable hardware JPEG service and dereferenced a null service pointer. The guarded binary checks that pointer and takes the existing failure-cleanup path when it is absent. The caller's existing software-JPEG fallback then handles the encoding after its retries. When the service exists, the normal path remains available.

The recipe changes 20 bytes at file offset `0xfa188` and 4 bytes at `0xfb618`. It reuses the existing GOT slot for the service pointer and the existing cleanup path; it replaces an optional informational entry-log sequence to make room. Full input and output hashes protect against applying those offsets to another library. It does not fake a successful capture or replace the camera algorithms, camera HAL, or Android framework.

Prepare or reverse it locally:

```sh
python tools/patch_binary.py --recipe patches/camera-jpeg-null-guard.json --input ../private/libAlgoProcess.so --output ../private/libAlgoProcess.patched.so
python tools/patch_binary.py --recipe patches/camera-jpeg-null-guard.json --input ../private/libAlgoProcess.patched.so --output ../private/libAlgoProcess.restored.so --reverse
```

The observed result was successful saved captures in the tested modes without the earlier camera/process crashes. This does not prove every capture mode or long-term stability.

## 48 MP limitation

After the OEM camera properties were set, the app requested 8000 x 6000 YUV alongside preview streams, but the HAL rejected stream configuration with error `-38`. Before the properties were corrected, selecting 48 MP could instead save a much smaller image; the UI selection alone was not proof of full resolution.

The observed HAL did not recognize the caller as the OEM camera, and the expected `com.oplus.packageName` session metadata was absent. Comparison with [PixelOS Camera3Device](https://github.com/PixelOS-AOSP/android_frameworks_av/blob/seventeen/services/camera/libcameraservice/device3/Camera3Device.cpp) suggested a framework integration difference. This is a diagnostic hypothesis, not a verified fix. No framework or camera HAL modification for 48 MP was deployed, and further investigation was stopped. Do not advertise this port as supporting 48 MP.

## Scoped rollback

- Restore original property values from your own record. An originally empty value and another ROM's existing list are different cases.
- Restore the original library bytes for the JPEG experiment if reverting only that patch, with the original metadata. Its reverse output must match the recipe's original hash.
- To remove the camera port entirely, remove only the added files after verifying they still match your deployment record; restore the recorded labels on existing libraries. Do not remove or overwrite existing libraries just because they appear in the manifest.
- Remove the exact camera system-overlay APK/configuration entries and the normally installed camera/processing packages as appropriate. Keep another working camera app available and reselect it as the default. Uninstalling an app may remove its app data; preserve anything needed first.
- Reboot, verify the fallback camera, and turn root debugging off. Do not clear all remount overlays, because that can remove the separate fingerprint workaround and other changes.

Updates can replace libraries, ignore overlays, or invalidate these assumptions. Revalidate file hashes and integration after a ROM update; never force a recipe past a mismatch.
