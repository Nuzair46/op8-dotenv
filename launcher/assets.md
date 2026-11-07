# Wallpaper assets and source

The neural shader and offline-rendering approach were adapted from the owner's [tv-dotenv](https://github.com/Nuzair46/tv-dotenv/tree/b60a5c38bc463aeede6c99897bbf1b71533d04c1), which extracted the shader from the owner's [website source](https://github.com/nuzair46/nuzair46.github.io). The phone render changes the viewport to 1080 x 2400 and retains the seamless 24-second time path, packed 16-bit grayscale readback, sRGB-to-BT.709 conversion, and fine dithering. No website or shader renderer runs on the phone.

| Asset | SHA-256 |
| --- | --- |
| `app/src/main/res/raw/neural.mp4` | `4ee78281bcb92a1474f0f959ad0db0ee1ac366e53a99acbe44fea707bca8b024` |
| `app/src/main/res/drawable-nodpi/neural_poster.png` | `001c679b3ea52d00d58ce1d7ba533d831f27b48f0958b8d8d06da452d2e9a690` |

The MP4 has 720 frames, HEVC Main10 SDR BT.709 limited range, and no audio. The PNG is an sRGB first-frame extraction used as a failure fallback and picker thumbnail. Both were generated entirely from the shader; neither includes photos, screenshots, camera metadata, or device identifiers. Hashes pin the reviewed assets, not a promise of identical output across GPUs/encoders.

## Optional regeneration

Normal builds use the committed assets. Regeneration requires Node.js with native fetch/WebSocket support, Chromium, and FFmpeg with libx265/zscale. Start a separate temporary local renderer (use your local Chromium executable):

```sh
chromium --headless=new --no-first-run --no-default-browser-check \
  --disable-extensions --disable-background-networking \
  --remote-debugging-address=127.0.0.1 --remote-debugging-port=9238 \
  --user-data-dir=/tmp/customphone-render --enable-unsafe-swiftshader about:blank
```

Then run:

```sh
node launcher/scripts/render-portrait-wallpaper.mjs --preview
node launcher/scripts/render-portrait-wallpaper.mjs
node launcher/scripts/render-portrait-wallpaper.mjs --close-renderer
```

The renderer writes ignored temporary files under `launcher/build/wallpaper/` and refuses to overwrite an existing finished video. Keep these files outside the publication set. After reviewing the output, copy the new MP4 into the resource directory and regenerate the fallback PNG:

```sh
ffmpeg -i launcher/app/src/main/res/raw/neural.mp4 -frames:v 1 \
  -vf 'zscale=transfer=iec61966-2-1:matrix=gbr:range=full,format=gbrp,format=rgb24' \
  launcher/app/src/main/res/drawable-nodpi/neural_poster.png
```

Use a new output filename if the old PNG exists, then replace it only after review. Update both the sizes/hashes in `PUBLIC_ASSETS.json` and this provenance table when intentionally changing artwork. Remove or relocate renderer intermediates before running the strict publication audit.

## Icons and licenses

`OutlineIcon.java` draws original line artwork for common phone functions. Other icons are read from the user's installed apps at runtime; no extracted app icons or Arcticons artwork from the TV project are bundled. Android supplies the sans-serif fonts. There are no Apple fonts or private frameworks.

The original launcher code, build tooling, and owner-authored shader/artwork are included under the repository's MIT license. Third-party app names and marks remain their owners' property. The pre-existing kernel patch retains its separate GPL-2.0 terms.
