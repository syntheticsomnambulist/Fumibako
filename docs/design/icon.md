# The Fumibako icon

Agreed 2026-11-06 in the design session (the "Fumibako icon" design canvas; the decision is
recorded in that session's `ideas.md`).

- A flat beni wax seal with an uneven edge, with 文 cut out of it in the paper color inside a
  thin ring (hakubun), in Yuji Syuku, raised slightly so it sits centered in the ring.
- The seal closes an envelope: the flap's V lines run in from the top corners and meet behind
  it, and the flap is a shade darker than the envelope. No tape.
- The themed (single-color, Android 13+) icon and the notification icon are the seal alone,
  with the ring and 文 cut out.

## Files

The design is drawn in the 108-unit grid of an Android adaptive icon, so the layers are vector
drawables generated from the same geometry, with 文 taken from the app's own brush font:

- `app/src/main/res/drawable/ic_launcher_background.xml`: the paper and the shaded flap.
- `app/src/main/res/drawable/ic_launcher_foreground.xml`: the seal, the ring and 文.
- `app/src/main/res/drawable/ic_launcher_monochrome.xml` and `ic_notification.xml`: the seal
  with the ring and 文 cut out.

The computer's icons come from the same geometry:

- `desktop/src/main/resources/icon.xml`: the window's icon, the whole icon (the visible square of
  the adaptive icon) as a rounded square with a thin darker edge, so the pale paper shows on a
  white background.
- `desktop/src/main/resources/tray.xml`: the tray's, the seal alone in colour, filling its
  square (a tray icon is 16 to 22 pixels).
- `desktop/src/main/resources/seal.xml`: a copy of the foreground layer, for Settings, About.
- `desktop/icons/fumibako.ico`, `.icns` and `.png`: the installers' icons for Windows, macOS and
  Linux. The Mac's follows Apple's grid (the body 824 of 1024, with a soft shadow).

To regenerate them (needs `pip install fonttools skia-pathops skia-python pillow`; on Linux
Skia also needs `libegl1`):

```
python3 tools/make_icon.py ui/src/androidMain/res/font/brush_regular.ttf app/src/main/res <preview dir> desktop
```

It also writes `preview.html` beside a drawing that uses the font directly, to compare, and the
desktop icon at a few sizes. Leave off `desktop` to write only the phone's (then only the first
two packages are needed). `IconsTest` in the desktop module checks the computer's icons draw.
