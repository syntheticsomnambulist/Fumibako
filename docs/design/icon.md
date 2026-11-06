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

To regenerate them (needs `pip install fonttools skia-pathops`):

```
python3 tools/make_icon.py app/src/main/res/font/brush_regular.ttf app/src/main/res <preview dir>
```

It also writes `preview.html` beside a drawing that uses the font directly, to compare.
