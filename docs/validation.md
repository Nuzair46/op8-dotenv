# Validation record

Recorded 2026-11-08 on one OnePlus 8 IN2015, LineageOS `24.0-20261103-NIGHTLY-instantnoodle`. This is a sanitized result summary; raw logs, photos, videos, device identifiers, and calibration data remain private.

## Phone behavior

| Check | Evidence and limit |
| --- | --- |
| Touch resume | User confirmed touch after normal reboot and sleep/wake; kernel source workaround used, not new controller firmware |
| Adaptive brightness | User confirmed covering/uncovering changed brightness after one-shot option was disabled |
| Fingerprint | User confirmed enrollment, enrolled-finger unlock and rejection of a tested unenrolled finger, including after reboot; small sample, no security certification |
| Photo | 3000 x 4000 JPEG saved and decoded |
| Ultrawide | 12 MP output saved and decoded; final property-enabled capture also saved |
| Portrait | 12 MP output saved and decoded; no claim about portrait segmentation quality |
| Nightscape | 12 MP output saved and decoded |
| Selfie | Final 3456 x 4608 output reported by processing logs; that final 16 MP file was not independently decoded in this test |
| Pro | 12 MP capture decoded before the final camera-property change; not repeated afterward |
| Macro | 2 MP capture saved after property correction; no full image-quality assessment |
| Video | 1920 x 1080 H.264/AAC recording decoded; decoder emitted a timestamp warning but completed |
| Slow motion | 1920 x 1080 file probed: 1154 frames over about 38.45 seconds from about 5 seconds of capture; full decoding was not completed |
| 48 MP | Failed full-resolution stream configuration; unsupported |
| Final repeated modes | Photo, ultrawide, portrait, nightscape, and video saved without observed app/HAL restart or error during those checks |
| Final reboot | Boot completed; JPEG guard hash, OEM camera properties, and normal still-camera default persisted; root ADB was off |

Final capture testing was not repeated after that last reboot. First PIN unlock is required after reboot. Panorama stitching, time-lapse, 4K recording, lock-screen launch, long-term stability, and other devices remain unverified. No objective OnePlus-versus-GCam image-quality comparison was established.

## Release tooling

The public patch tool has synthetic tests for forward/reverse transformations, wrong binaries, wrong preimages, output hashes, overlap/bounds/length errors, biometric opt-in, overwrite refusal, preservation of input files, and operation with Python optimization enabled.

Before publication, both vendor recipes were also exercised privately against matching original files, including reverse-to-original verification. No vendor files or generated outputs are committed. The kernel patch was checked against its pinned upstream source. The publication audit checks the explicit file allowlist, UTF-8 text/size rules, obvious credential and local-path patterns, optional private denylist terms, and relative Markdown links. Automated scanning supplements manual review; it cannot prove that arbitrary future contributions are safe.
