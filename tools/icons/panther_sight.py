"""Black Panther's sight icons (the eye over each player while he is camouflaged).

eye_hidden.png: a violet eye struck through (they do not see him).
eye_seen.png:   a red open eye (they see him).
Drawn 4x larger and scaled down for smooth edges. Run: python tools/icons/panther_sight.py
"""
import math, os
from PIL import Image, ImageDraw, ImageFilter

OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'superheromod', 'textures', 'gui', 'panther')
S = 64
K = 4
W = S * K

def eye_outline(cx, cy, w, h, n=80):
    pts = []
    for i in range(n + 1):
        t = math.pi * i / n
        pts.append((cx - w * math.cos(t), cy - h * math.sin(t) ** 1.25))
    for i in range(n + 1):
        t = math.pi * i / n
        pts.append((cx + w * math.cos(t), cy + h * .82 * math.sin(t) ** 1.25))
    return pts

def draw(color, glow, slash):
    img = Image.new('RGBA', (W, W), (0, 0, 0, 0))
    cx, cy = W / 2, W / 2
    w, h = W * .44, W * .26
    # A soft glow behind, so it reads on any background.
    g = Image.new('RGBA', (W, W), (0, 0, 0, 0))
    gd = ImageDraw.Draw(g)
    gd.polygon(eye_outline(cx, cy, w * 1.02, h * 1.15), fill=glow + (150,))
    g = g.filter(ImageFilter.GaussianBlur(W * .05))
    img = Image.alpha_composite(img, g)
    d = ImageDraw.Draw(img)
    # Dark rim, then the eye white (tinted), then the iris and pupil.
    d.polygon(eye_outline(cx, cy, w + K * 3, h + K * 3), fill=(12, 6, 18, 255))
    d.polygon(eye_outline(cx, cy, w, h), fill=color + (255,))
    inner = tuple(min(255, int(c * .25 + 230 * .75)) for c in color)
    d.polygon(eye_outline(cx, cy, w * .78, h * .72), fill=inner + (255,))
    r = h * .78
    d.ellipse((cx - r - K * 2, cy - r - K * 2, cx + r + K * 2, cy + r + K * 2), fill=(12, 6, 18, 255))
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=color + (255,))
    pr = r * .45
    d.ellipse((cx - pr, cy - pr, cx + pr, cy + pr), fill=(12, 6, 18, 255))
    hl = r * .22
    d.ellipse((cx - r * .45 - hl, cy - r * .45 - hl, cx - r * .45 + hl, cy - r * .45 + hl), fill=(255, 255, 255, 230))
    if slash:
        a, b = (cx + w * .78, cy - W * .40), (cx - w * .78, cy + W * .40)
        d.line([a, b], fill=(12, 6, 18, 255), width=K * 11)
        d.line([a, b], fill=color + (255,), width=K * 6)
    return img.resize((S, S), Image.LANCZOS)

os.makedirs(OUT, exist_ok=True)
draw((160, 92, 255), (120, 60, 230), True).save(os.path.join(OUT, 'eye_hidden.png'))
draw((255, 58, 58), (230, 30, 30), False).save(os.path.join(OUT, 'eye_seen.png'))
print('ok')
