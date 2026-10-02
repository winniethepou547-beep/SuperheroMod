"""Shared tools for the champion splash art: comic-poster style (dark silhouettes, coloured rim
light, glowing signature features, halftone, ink outlines, a slight print misregistration)."""
import math, random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageChops

S = 2048  # working size; saved at OUT
OUT = 512

def rgb(h):
    h = h.lstrip('#'); return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))

def canvas(top, bottom):
    t, b = np.array(rgb(top), float), np.array(rgb(bottom), float)
    y = np.linspace(0, 1, S)[:, None, None]
    img = (t * (1 - y) + b * y) * np.ones((S, S, 1))
    return Image.fromarray(img.astype(np.uint8), 'RGB')

def mask():
    return Image.new('L', (S, S), 0)

def poly(m, pts, fill=255):
    ImageDraw.Draw(m).polygon([(float(x), float(y)) for x, y in pts], fill=fill)
    return m

def ellipse(m, cx, cy, rx, ry, fill=255):
    ImageDraw.Draw(m).ellipse((cx - rx, cy - ry, cx + rx, cy + ry), fill=fill)
    return m

def paint(img, m, colour, alpha=1.0):
    """Fills the mask with a colour (or an RGB image) at an opacity."""
    layer = Image.new('RGB', (S, S), colour) if not isinstance(colour, Image.Image) else colour
    a = m.point(lambda v: int(v * alpha))
    img.paste(layer, (0, 0), a)
    return img

def glow(img, m, colour, radius, strength=1.0):
    """Additive light: the mask blurred and added in a colour."""
    b = m.filter(ImageFilter.GaussianBlur(radius))
    arr = np.array(b, float)[:, :, None] / 255 * strength
    add = arr * np.array(rgb(colour) if isinstance(colour, str) else colour, float)
    out = np.clip(np.array(img, float) + add, 0, 255)
    return Image.fromarray(out.astype(np.uint8), 'RGB')

def shift(m, dx, dy):
    return ImageChops.offset(m, int(dx), int(dy))

def rim(img, m, colour, dx, dy, strength=1.0, blur=6):
    """Light catching the edge of a shape from one side."""
    edge = ImageChops.subtract(m, shift(m, dx, dy)).filter(ImageFilter.GaussianBlur(blur))
    return _add(img, edge, colour, strength)

def _add(img, m, colour, strength):
    arr = np.array(m, float)[:, :, None] / 255 * strength
    out = np.clip(np.array(img, float) + arr * np.array(rgb(colour) if isinstance(colour, str) else colour, float), 0, 255)
    return Image.fromarray(out.astype(np.uint8), 'RGB')

def outline(img, m, width=14, colour=(8, 6, 10)):
    """Ink line round a shape."""
    grown = m.filter(ImageFilter.MaxFilter(width * 2 + 1))
    ring = ImageChops.subtract(grown, m)
    return paint(img, ring, colour)

def rays(img, cx, cy, colour, count=28, strength=.35, seed=1):
    r = random.Random(seed)
    m = mask(); d = ImageDraw.Draw(m)
    for i in range(count):
        a = i / count * math.tau + r.uniform(-.05, .05)
        w = r.uniform(.02, .06)
        far = S * 1.5
        d.polygon([(cx, cy), (cx + math.cos(a - w) * far, cy + math.sin(a - w) * far), (cx + math.cos(a + w) * far, cy + math.sin(a + w) * far)], fill=int(255 * r.uniform(.4, 1)))
    radial = Image.radial_gradient('L').resize((S, S)).point(lambda v: 255 - v)
    m = ImageChops.multiply(m.filter(ImageFilter.GaussianBlur(10)), radial)
    return _add(img, m, colour, strength)

def radial_light(img, cx, cy, radius, colour, strength=1.0):
    m = mask(); ellipse(m, cx, cy, radius, radius)
    return glow(img, m, colour, radius * .6, strength)

def halftone(img, colour, density_fn, step=26, strength=.5, seed=3):
    """Comic dots whose size follows density_fn(x, y) in 0..1."""
    m = mask(); d = ImageDraw.Draw(m)
    for y in range(0, S, step):
        for x in range((y // step % 2) * step // 2, S, step):
            k = density_fn(x / S, y / S)
            if k <= .02: continue
            r = step * .5 * min(1, k)
            d.ellipse((x - r, y - r, x + r, y + r), fill=255)
    return _add(img, m, colour, strength)

def vignette(img, strength=.75):
    g = Image.radial_gradient('L').resize((S, S))
    arr = np.array(g, float)[:, :, None] / 255
    k = 1 - strength * np.clip((arr - .45) / .55, 0, 1) ** 1.4
    return Image.fromarray(np.clip(np.array(img, float) * k, 0, 255).astype(np.uint8))

def grain(img, amount=10, seed=7):
    rng = np.random.default_rng(seed)
    n = rng.normal(0, amount, (S, S, 1))
    return Image.fromarray(np.clip(np.array(img, float) + n, 0, 255).astype(np.uint8))

def misprint(img, ink, offset=10):
    """The multiverse print look: the ink shape echoed in cyan and magenta, a little off register."""
    img = _add(img, shift(ink, -offset, 0).filter(ImageFilter.GaussianBlur(2)), (0, 70, 90), .25)
    img = _add(img, shift(ink, offset, offset // 2).filter(ImageFilter.GaussianBlur(2)), (90, 0, 60), .2)
    return img

def frame_lines(img, colour, seed=11, count=10):
    """Speed / dimension-rift lines streaking across the background."""
    r = random.Random(seed)
    m = mask(); d = ImageDraw.Draw(m)
    for i in range(count):
        y = r.uniform(0, S); a = r.uniform(-.35, .35)
        d.line([(-100, y), (S + 100, y + math.tan(a) * S)], fill=int(r.uniform(90, 255)), width=int(r.uniform(3, 12)))
    return _add(img, m.filter(ImageFilter.GaussianBlur(3)), colour, .35)

def blob_smoke(img, colour, count, area, seed, alpha=.6, size=(80, 260)):
    r = random.Random(seed)
    m = mask(); d = ImageDraw.Draw(m)
    x0, y0, x1, y1 = area
    for _ in range(count):
        cx, cy, rr = r.uniform(x0, x1), r.uniform(y0, y1), r.uniform(*size)
        d.ellipse((cx - rr, cy - rr * .7, cx + rr, cy + rr * .7), fill=int(255 * r.uniform(.3, 1)))
    return paint(img, m.filter(ImageFilter.GaussianBlur(40)), colour, alpha)

def shuriken(cx, cy, reach, rot=0.0, curve=.55):
    """Outline points of a four-bladed shuriken with hooked blades."""
    pts = []
    for i in range(4):
        a = rot + i * math.pi / 2
        hub = reach * .2
        pts.append((cx + math.cos(a - .45) * hub, cy + math.sin(a - .45) * hub))
        # Leading edge out to the hooked tip, then the trailing edge back with a curl.
        for k in range(1, 9):
            u = k / 8
            ang = a + curve * u * u
            rr = hub + (reach - hub) * u
            pts.append((cx + math.cos(ang) * rr, cy + math.sin(ang) * rr))
        for k in range(8, 0, -1):
            u = k / 8
            ang = a + curve * u * u + .32 * (1 - u) + .05
            rr = hub + (reach - hub) * u * .78
            pts.append((cx + math.cos(ang) * rr, cy + math.sin(ang) * rr))
    return pts

def bolt(d, x0, y0, x1, y1, seed, width=10, jag=60, fill=255):
    r = random.Random(seed)
    pts = [(x0, y0)]
    n = 9
    for i in range(1, n):
        u = i / n
        pts.append((x0 + (x1 - x0) * u + r.uniform(-jag, jag), y0 + (y1 - y0) * u + r.uniform(-jag * .4, jag * .4)))
    pts.append((x1, y1))
    d.line(pts, fill=fill, width=width, joint='curve')
    return pts

def save(img, path):
    img.resize((OUT, OUT), Image.LANCZOS).save(path, optimize=True)

def bust(shoulder=620, trap=250, neck=120, top=1250, neck_top=1080):
    """A generic head-and-shoulders torso outline (without the head)."""
    c = S / 2
    return [(c - shoulder - 120, S), (c - shoulder, top + 330), (c - shoulder + 60, top + 120), (c - trap, top - 30), (c - neck, neck_top),
            (c + neck, neck_top), (c + trap, top - 30), (c + shoulder - 60, top + 120), (c + shoulder, top + 330), (c + shoulder + 120, S)]
