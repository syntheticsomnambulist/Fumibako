"""Builds the Fumibako launcher and notification icons from the agreed design.

The design (the "Fumibako icon" canvas, simple seal component) is drawn in the 108-unit grid of an
Android adaptive icon: a wax-edge seal (blob, R 25) at the centre, a thin ring (r 18.5, 1.4 wide),
and 文 in Yuji Syuku at 27 units, cut out in the paper colour and raised 1.6; behind it an envelope
whose shaded flap's V lines meet at the centre. Usage:
tools/make_icon.py <brush font> <res dir> <preview dir> [<desktop dir>]; see docs/design/icon.md.
With a desktop dir it also writes the computer's icons: the window's and the tray's (vectors) and
the installers' (.ico, .icns, .png; needs skia-python and pillow).
"""
import math, sys, os
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
import pathops

font_path, res_dir, preview_dir = sys.argv[1:4]
desktop_dir = sys.argv[4] if len(sys.argv) > 4 else None
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


# The computer's icons (desktop/src/main/resources and desktop/icons). The window's and the installers' are the whole icon: the adaptive icon's
# visible square (18..90), as a rounded square with a little room around it (a Mac's icons have
# more room and a shadow). The tray's is the seal alone, filling its square, as the phone's
# notification icon is (with the ring and 文, in colour).
if desktop_dir:
    EDGE = mix(PAPER, "#7a5a40", 0.22)

    def rounded_d(x, y, w, r):
        return ("M %.2f %.2f H %.2f A %.2f %.2f 0 0 1 %.2f %.2f V %.2f A %.2f %.2f 0 0 1 %.2f %.2f H %.2f "
                "A %.2f %.2f 0 0 1 %.2f %.2f V %.2f A %.2f %.2f 0 0 1 %.2f %.2f Z") % (
            x + r, y, x + w - r, r, r, x + w, y + r, y + w - r, r, r, x + w - r, y + w, x + r,
            r, r, x, y + w - r, y + r, r, r, x + r, y)

    # The whole icon in its 108 grid, the visible square being 18..90.
    BODY = (18.0, 18.0, 72.0, 72.0 * 0.225)
    whole_paths = [
        path("M 0 0 H 108 V 108 H 0 Z", fill=PAPER),
        path("M 0 0 L 108 0 L 54 54 Z", fill=FLAP),
        path("M 0 0 L 54 54 L 108 0", stroke=LINE, width="1.6", cap="round", join="round"),
        path(blob_d(), fill=WAX),
        path(ring_d(18.5), stroke=PAPER, width="1.4"),
        path(glyph_d, fill=PAPER),
    ]
    # A thin darker edge, so the pale paper shows on a white background.
    edge_d = rounded_d(BODY[0] + 0.4, BODY[1] + 0.4, BODY[2] - 0.8, BODY[3] - 0.4)

    def whole_vector(margin):
        """The window's icon: the visible square with [margin] units of room on each side."""
        view = BODY[2] + 2 * margin
        clip = '    <clip-path android:pathData="%s" />' % rounded_d(*BODY)
        inner = "\n".join([clip] + whole_paths + [path(edge_d, stroke=EDGE, width="0.8")])
        inner = "\n".join("    " + l for l in inner.splitlines())
        shift = margin - BODY[0]
        return (HEADER + '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
                '    android:width="%ddp"\n    android:height="%ddp"\n    android:viewportWidth="%s"\n    android:viewportHeight="%s">\n'
                '    <group android:translateX="%s" android:translateY="%s">\n%s\n    </group>\n</vector>\n'
                % (32, 32, view, view, shift, shift, inner))

    SEAL = (26.0, 56.0)  # the seal's square in the 108 grid: from 26, 56 wide (its wax edge included)
    tray = vector(32, [
        path(blob_d(), fill=WAX),
        path(ring_d(18.5), stroke=PAPER, width="1.4"),
        path(glyph_d, fill=PAPER),
    ], viewport=SEAL[1], group='android:translateX="-%s" android:translateY="-%s"' % (SEAL[0], SEAL[0]))

    resources = os.path.join(desktop_dir, "src/main/resources")
    with open(os.path.join(resources, "icon.xml"), "w") as f:
        f.write(whole_vector(margin=BODY[2] / 16))
    with open(os.path.join(resources, "tray.xml"), "w") as f:
        f.write(tray)
    # Settings, About shows the launcher's seal layer, as on the phone.
    with open(os.path.join(resources, "seal.xml"), "w") as f:
        f.write(foreground)

    # The installers' pictures, drawn by Skia from the same shapes as SVG.
    import skia
    from PIL import Image

    whole_svg_body = (
        '<rect width="108" height="108" fill="%s"/><path d="M 0 0 L 108 0 L 54 54 Z" fill="%s"/>'
        '<path d="M 0 0 L 54 54 L 108 0" fill="none" stroke="%s" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>'
        '<path d="%s" fill="%s"/><path d="%s" fill="none" stroke="%s" stroke-width="1.4"/><path d="%s" fill="%s"/>'
        '<path d="%s" fill="none" stroke="%s" stroke-width="0.8"/>'
    ) % (PAPER, FLAP, LINE, blob_d(), WAX, ring_d(18.5), PAPER, glyph_d, PAPER, edge_d, EDGE)

    def whole_svg(margin, shadow=False):
        view = BODY[2] + 2 * margin
        o = BODY[0] - margin
        defs = '<clipPath id="c"><path d="%s"/></clipPath>' % rounded_d(*BODY)
        under = ""
        if shadow:
            defs += ('<filter id="s" x="-20%%" y="-20%%" width="140%%" height="140%%">'
                     '<feGaussianBlur stdDeviation="%.2f"/></filter>') % (view * 0.012)
            under = '<path d="%s" fill="#000000" fill-opacity="0.28" filter="url(#s)" transform="translate(0 %.2f)"/>' % (
                rounded_d(*BODY), view * 0.01)
        return ('<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="%.2f %.2f %.2f %.2f">'
                '<defs>%s</defs>%s<g clip-path="url(#c)">%s</g></svg>') % (o, o, view, view, defs, under, whole_svg_body)

    def render(svg_text, size):
        stream = skia.MemoryStream(svg_text.encode())
        dom = skia.SVGDOM.MakeFromStream(stream)
        dom.setContainerSize(skia.Size(size, size))
        surface = skia.Surface(size, size)
        with surface as canvas:
            canvas.clear(skia.ColorTRANSPARENT)
            canvas.scale(size / 1024, size / 1024)
            dom.render(canvas)
        rgba = surface.makeImageSnapshot().toarray(colorType=skia.kRGBA_8888_ColorType)
        return Image.fromarray(rgba, "RGBA")

    icons = os.path.join(desktop_dir, "icons")
    os.makedirs(icons, exist_ok=True)
    # Windows and Linux: a little room (1/18 of the side), as most of their icons have.
    plain = whole_svg(margin=BODY[2] / 16)
    sizes = [16, 20, 24, 32, 40, 48, 64, 128, 256]
    images = {n: render(plain, n) for n in sizes}
    images[256].save(os.path.join(icons, "fumibako.ico"), format="ICO", sizes=[(n, n) for n in sizes],
                     append_images=[images[n] for n in sizes if n != 256])
    render(plain, 512).save(os.path.join(icons, "fumibako.png"))
    # A Mac's grid: the body 824 of 1024, with a soft shadow under it.
    mac = whole_svg(margin=BODY[2] * 100 / 824, shadow=True)
    mac_images = [render(mac, n) for n in (1024, 512, 256, 128, 64, 32, 16)]
    mac_images[0].save(os.path.join(icons, "fumibako.icns"), format="ICNS", append_images=mac_images[1:])
    for n in (16, 32, 256):
        images[n].save(os.path.join(preview_dir, "desktop-%d.png" % n))
    mac_images[2].save(os.path.join(preview_dir, "desktop-mac-256.png"))
    print("desktop icons in", resources, "and", icons)
