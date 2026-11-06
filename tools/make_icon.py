"""Builds the Fumibako launcher and notification icons from the agreed design.

The design (the "Fumibako icon" canvas, simple seal component) is drawn in the 108-unit grid of an
Android adaptive icon: a wax-edge seal (blob, R 25) at the centre, a thin ring (r 18.5, 1.4 wide),
and 文 in Yuji Syuku at 27 units, cut out in the paper colour and raised 1.6; behind it an envelope
whose shaded flap's V lines meet at the centre. Usage: tools/make_icon.py <brush font> <res dir> <preview dir>; see docs/design/icon.md.
"""
import math, sys, os
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
import pathops

font_path, res_dir, preview_dir = sys.argv[1:4]
PAPER = "#f8f0e9"
WAX = "#a43b3b"
LINE = "#b9a18c"

def mix(c, t, a):
    x = [int(c[i:i + 2], 16) for i in (1, 3, 5)]
    y = [int(t[i:i + 2], 16) for i in (1, 3, 5)]
    return "#" + "".join("%02x" % round(v + (w - v) * a) for v, w in zip(x, y))

FLAP = mix(PAPER, "#7a5a40", 0.07)
CX, CY, R = 54.0, 54.0, 25.0

def blob_points(cx, cy, r0):
    pts = []
    for i in range(121):
        t = i / 120 * math.pi * 2
        r = r0 * (1 + 0.045 * math.sin(5 * t + 0.6) + 0.03 * math.sin(8 * t + 2.1) + 0.018 * math.sin(13 * t + 4.0))
        pts.append((cx + r * math.cos(t), cy + r * math.sin(t)))
    return pts

def blob_d():
    pts = blob_points(CX, CY, R)
    return "M " + " L ".join("%.2f %.2f" % p for p in pts) + " Z"

# The glyph: 27 units, centred on its advance, its em box centred on CY - 1.6 (the design's lift).
font = TTFont(font_path)
cmap = font.getBestCmap()
name = cmap[ord("文")]
glyphs = font.getGlyphSet()
upm = font["head"].unitsPerEm
os2 = font["OS/2"]
size = 27.0
scale = size / upm
advance = font["hmtx"][name][0]
# CSS "central" baseline: halfway between the ideographic em box's top and bottom.
central = (os2.sTypoAscender + os2.sTypoDescender) / 2
text_x, text_y = CX, CY - 1.6
transform = (scale, 0, 0, -scale, text_x - advance / 2 * scale, text_y + central * scale)

svg_pen = SVGPathPen(glyphs)
glyphs[name].draw(TransformPen(svg_pen, transform))
glyph_d = svg_pen.getCommands()

def circle(path, cx, cy, r, clockwise=True):
    k = 0.5522847498 * r
    pts = [(cx + r, cy), (cx, cy + r), (cx - r, cy), (cx, cy - r)]
    if not clockwise:
        pts = pts[::-1]
    path.moveTo(*pts[0])
    for i in range(4):
        a, b = pts[i], pts[(i + 1) % 4]
        # control points along the tangents
        def tangent(p, toward):
            dx, dy = p[0] - cx, p[1] - cy
            tx, ty = -dy, dx
            if not clockwise:
                tx, ty = dy, -dx
            return (p[0] + tx / r * k * toward, p[1] + ty / r * k * toward)
        c1 = tangent(a, 1)
        c2 = tangent(b, -1)
        path.cubicTo(*c1, *c2, *b)
    path.close()

# The single-colour seal: the blob, minus the ring, minus the glyph (overlaps removed first).
seal = pathops.Path()
pts = blob_points(CX, CY, R)
seal.moveTo(*pts[0])
for p in pts[1:]:
    seal.lineTo(*p)
seal.close()
ring = pathops.Path()
circle(ring, CX, CY, 18.5 + 0.7)
inner = pathops.Path()
circle(inner, CX, CY, 18.5 - 0.7)
ring = pathops.op(ring, inner, pathops.PathOp.DIFFERENCE)
glyph = pathops.Path()
glyphs[name].draw(TransformPen(glyph.getPen(), transform))
glyph.simplify()
mono = pathops.op(seal, ring, pathops.PathOp.DIFFERENCE)
mono = pathops.op(mono, glyph, pathops.PathOp.DIFFERENCE)
mono_pen = SVGPathPen(None)
mono.draw(mono_pen)
mono_d = mono_pen.getCommands()

def ring_d(r):
    return "M %.2f %.2f a %.2f %.2f 0 1 0 %.2f 0 a %.2f %.2f 0 1 0 %.2f 0 Z" % (CX - r, CY, r, r, 2 * r, r, r, -2 * r)

HEADER = '<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated from the Fumibako icon design; see docs/design/icon.md. -->\n'

def vector(w, paths, viewport=108, group=None):
    body = "\n".join(paths)
    if group:
        body = "    <group %s>\n%s\n    </group>" % (group, "\n".join("    " + l for l in body.splitlines()))
    return (HEADER + '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="%ddp"\n    android:height="%ddp"\n    android:viewportWidth="%s"\n    android:viewportHeight="%s">\n%s\n</vector>\n'
            % (w, w, viewport, viewport, body))

def path(d, fill=None, stroke=None, width=None, cap=None, join=None, even_odd=False):
    attrs = ['android:pathData="%s"' % d]
    if fill: attrs.append('android:fillColor="%s"' % fill)
    if stroke: attrs.append('android:strokeColor="%s"' % stroke)
    if width: attrs.append('android:strokeWidth="%s"' % width)
    if cap: attrs.append('android:strokeLineCap="%s"' % cap)
    if join: attrs.append('android:strokeLineJoin="%s"' % join)
    if even_odd: attrs.append('android:fillType="evenOdd"')
    return "    <path\n        " + "\n        ".join(attrs) + " />"

background = vector(108, [
    path("M 0 0 H 108 V 108 H 0 Z", fill=PAPER),
    path("M 0 0 L 108 0 L 54 54 Z", fill=FLAP),
    path("M 0 0 L 54 54 L 108 0", stroke=LINE, width="1.6", cap="round", join="round"),
])
foreground = vector(108, [
    path(blob_d(), fill=WAX),
    path(ring_d(18.5), stroke=PAPER, width="1.4"),
    path(glyph_d, fill=PAPER),
])
monochrome = vector(108, [path(mono_d, fill="#FF000000")])
# Notification: the seal alone, in white, cropped to the seal (28..80) on a 24 dp icon.
notification = vector(24, [path(mono_d, fill="#FFFFFFFF")], viewport=52, group='android:translateX="-28" android:translateY="-28"')

files = {
    "drawable/ic_launcher_background.xml": background,
    "drawable/ic_launcher_foreground.xml": foreground,
    "drawable/ic_launcher_monochrome.xml": monochrome,
    "drawable/ic_notification.xml": notification,
}
for rel, text in files.items():
    with open(os.path.join(res_dir, rel), "w") as f:
        f.write(text)

# Previews: the same layers as SVG, next to the design's own drawing (text in the font).
os.makedirs(preview_dir, exist_ok=True)
font_url = "file://" + os.path.abspath(font_path)
def svg(body, view="18 18 72 72", size=288):
    return '<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="%s">%s</svg>' % (size, size, view, body)
ours = (
    '<rect width="108" height="108" fill="%s"/><path d="M 0 0 L 108 0 L 54 54 Z" fill="%s"/>'
    '<path d="M 0 0 L 54 54 L 108 0" fill="none" stroke="%s" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>'
    '<path d="%s" fill="%s"/><path d="%s" fill="none" stroke="%s" stroke-width="1.4"/><path d="%s" fill="%s"/>'
) % (PAPER, FLAP, LINE, blob_d(), WAX, ring_d(18.5), PAPER, glyph_d, PAPER)
design = (
    '<rect width="108" height="108" fill="%s"/><path d="M 0 0 L 108 0 L 54 54 Z" fill="%s"/>'
    '<path d="M 0 0 L 54 54 L 108 0" fill="none" stroke="%s" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>'
    '<path d="%s" fill="%s"/><path d="%s" fill="none" stroke="%s" stroke-width="1.4"/>'
    '<text x="54" y="%.2f" style="font-family: Brush; font-size: 27px; text-anchor: middle; dominant-baseline: central; fill: %s">文</text>'
) % (PAPER, FLAP, LINE, blob_d(), WAX, ring_d(18.5), PAPER, text_y, PAPER)
mono_svg = '<rect width="108" height="108" fill="#3b2f22"/><path d="%s" fill="#e8d3c1"/>' % mono_d
html = """<!doctype html><html><head><style>
@font-face { font-family: Brush; src: url('%s'); }
body { margin: 0; background: #e6dcd3; display: flex; gap: 24px; padding: 24px; font: 14px sans-serif; color: #5d4c3c }
figure { margin: 0; display: flex; flex-direction: column; gap: 6px; align-items: center }
.sq svg { border-radius: 30%%; } .ci svg { border-radius: 50%%; }
</style></head><body>
<figure class="sq">%s<figcaption>Generated (vector paths)</figcaption></figure>
<figure class="sq">%s<figcaption>Design (text in the font)</figcaption></figure>
<figure class="ci">%s<figcaption>Themed icon</figcaption></figure>
<figure>%s<figcaption>Small sizes</figcaption></figure>
</body></html>""" % (font_url, svg(ours), svg(design), svg(mono_svg),
                     svg(ours, size=48) + svg(ours, size=24) + svg(mono_svg, size=24))
with open(os.path.join(preview_dir, "preview.html"), "w") as f:
    f.write(html)
print("glyph", name, "upm", upm, "typo", os2.sTypoAscender, os2.sTypoDescender, "advance", advance)
