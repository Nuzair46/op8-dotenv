# Adaptive brightness

On the tested installation, manual brightness worked but automatic brightness did not continue following ambient light. Disabling LineageOS's one-shot sampling made it respond to covering and uncovering the upper front sensor.

Record your current values first:

```sh
adb shell settings --lineage get system auto_brightness_one_shot
adb shell settings get system screen_brightness_mode
```

Enable ongoing sampling and Android automatic brightness:

```sh
adb shell settings --lineage put system auto_brightness_one_shot 0
adb shell settings put system screen_brightness_mode 1
```

With the phone unlocked and the screen awake, cover the upper front for about 15 seconds, then uncover it toward a bright room light for another 15 seconds. Leave the slider untouched. Avoid confusing the normal inactivity dim with a response to light.

Restore the recorded values to undo. If a value was originally `null`, use the corresponding `settings ... delete` command rather than writing the literal word `null`. This setting does not calibrate the light sensor and cannot repair a physically obstructed or faulty sensor.
