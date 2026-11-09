# Little fumibakos: the boxes as SVG

Every box from [`../fumibako-studies.html`](../fumibako-studies.html), exported for a pass by hand:
each finish, lid and tie, shut and open, at the chosen size (120 by 80). Made 2026-11-09 by
[`tools/export_fumibako_svgs.js`](../../../tools/export_fumibako_svgs.js), which draws each box in the study and
writes its shapes out with the colors filled in.

- **Folders by finish:** `lacquer/`, `washi/` (covered in their washi), `kiri/` (paulownia).
- **File names:** `<tie>-<lid>-<shut|open>.svg`. Ties: `bow`, `cross` (tied crosswise),
  `mizuhiki`, `tassel`. Lids: `plate` (their name on a plate), `seal`, `engraved`, `gilded`,
  `kanji` (their ink's kanji).
- **Units:** 1 unit = 1 dp in the app. The box is 120 by 80 from (0, 0); the viewBox is larger so
  the lifted lid and the loose cords fit.
- **Ink:** Aria's (sakura), autumn by day, with her dotted tape pattern on washi. In the app every
  fill is a persona's ink mixed into the paper at 12, 25, 40, 55, 70 or 85% (or the ink itself),
  so a redrawn box keeps working in every ink if its fills keep to those mixes.
- **Layers:** shapes are grouped by what they belong to: `box` (the box and its inside), `letter`
  (the letters in it), `lid` (lid, edge band, the cord on it, what's on the lid), `bow` (a knot or
  bow, gone when open), `mzk` (mizuhiki), `tasselend`, `loose` (cords lying loose when open).
  Positions are baked in, so each file is one still frame; the app animates between shut and open.
- **Fonts:** the text is left as text: Yuji Syuku (seals, kanji), Zen Old Mincho (names) and
  Yomogi (the name written on the letter inside). Install them from Google Fonts to see the files
  as drawn; previews without them fall back to other faces.

When you replace one, keep the name and the layers, and the app's drawing can follow it.
