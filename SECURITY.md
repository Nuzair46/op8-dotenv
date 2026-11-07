# Security and support scope

The fingerprint recipe bypasses a calibration-ready check. It does not recalibrate the sensor, and accepting one enrolled finger while rejecting another does not establish biometric security. Treat it as an opt-in experiment on a spare/test device, retain a strong PIN, and do not use its result as evidence of strong authentication.

The phone experiments depended on an unlocked bootloader and development remount overlays. These change the normal security/update assumptions. Do not disable SELinux, weaken package signature checks, or globally exempt applications from hidden-API restrictions to make the camera work. The supplied XML names only the original signed camera package.

Recipes accept exact binaries only. Unknown hashes, errors, or differing metadata are reasons to stop. There is no automatic flashing, calibration deletion, donor-data download, or remote control in the public tools. A corrupted output from a failed disk write must not be deployed.

For a reproducibility report, include device model, ROM build, tool version, a minimal sanitized error, and relevant file hashes. Do not post serials, IMEIs, authentication tokens, account details, raw bugreports, biometric templates, calibration partitions, or personal media. Review logs before attaching them. Never upload vendor binaries as issue attachments.

This small experimental project has no guaranteed private disclosure or support response channel. Use GitHub's private vulnerability reporting if the repository offers it; otherwise avoid placing exploitable private details or secrets in a public issue.
