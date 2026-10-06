"""Iceman's frost on a frozen player's screen (FrostScreen draws these under the HUD).

frost_atlas.png  12 frames (4 x 3 cells, 640 x 360 each) of real-looking window frost growing in from the edges of the
                 screen: fern-like dendrites (stems that branch at 60 degrees, branches of branches, as ice crystals
                 grow) leading a soft grainy frost film, thickest at the edges and in the corners, the centre always
                 left readable. Every frame is stored as the frost ADDED since the frame before it, worked out so that
                 drawing frames 0..k on top of each other (plain alpha blending) gives exactly the frost of frame k.
                 Drawing the next frame with a growing alpha therefore grows the frost smoothly, never popping.
crack_net.png    a network of fine cracks in the ice (radial cracks from a few impact points near the edges, joined by
                 short arcs), a dark shadow line under a bright edge line: the ice over the view about to break.
lens_drops.png   condensation on the icy lens: many tiny droplets and some bigger ones (dark lower rim, bright highlight),
                 denser toward the edges.
Run: python tools/icons/iceman_frost.py
"""
import math, os, random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'superheromod', 'textures', 'gui', 'iceman')
W, H = 640, 360          # one frame
SS = 2                   # supersampling for the dendrites
COLS, ROWS, FRAMES = 4, 3, 12
# How far in from the edge (pixels of the corner-weighted edge distance) the frost has reached in each frame.
REACH = [6, 11, 17, 24, 32, 41, 51, 62, 74, 86, 98, 110]
rng = random.Random(1963)
np.random.seed(1963)


def value_noise(w, h, cell, seed):
    """Smooth value noise in 0..1 (bilinear between random lattice values, smoothstep weights)."""
    r = np.random.RandomState(seed)
    gw, gh = w // cell + 3, h // cell + 3
    g = r.rand(gh, gw)
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float64)
    fx, fy = xs / cell, ys / cell
    x0, y0 = np.floor(fx).astype(int), np.floor(fy).astype(int)
    tx, ty = fx - x0, fy - y0
    tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
    a, b = g[y0, x0], g[y0, x0 + 1]
    c, d = g[y0 + 1, x0], g[y0 + 1, x0 + 1]
    return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty


def fbm(w, h, base, octaves, seed):
    out, amp, tot = np.zeros((h, w)), 1.0, 0.0
    for o in range(octaves):
        out += amp * value_noise(w, h, max(2, base >> o), seed + o * 17)
        tot += amp
        amp *= .5
    return out / tot


def edge_distance(w, h):
    """Corner-weighted distance from the screen's edges (a soft minimum: the corners are 'nearer', so frost reaches
    further into them), in pixels."""
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float64)
    dx = np.minimum(xs + .5, w - xs - .5)
    dy = np.minimum(ys + .5, h - ys - .5)
    p = 2.2
    return (dx ** -p + dy ** -p) ** (-1 / p)


def centre_mask(w, h):
    """1 in the middle of the view (an ellipse), 0 outside: the frost is held back there so the view stays readable."""
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float64)
    u, v = (xs / w - .5) / .36, (ys / h - .5) / .34
    r = np.sqrt(u * u + v * v)
    return np.clip(1.35 - r, 0, 1) ** 1.2


# ------------------------------------------------------------------ the dendrites (path, arrival time)
def grow_dendrites():
    """Fern-like frost feathers from the edges inward: a gently curving stem with short side barbs at about 60 degrees
    on both sides (longest near the stem's base, shrinking to its tip), now and then a barb grows into a stem of its
    own. Each segment's arrival time (the reach at which it appears) is its path length from the edge, so the
    feathers grow outward from the edge, their tips a little ahead of the film."""
    segs = []  # (x0, y0, x1, y1, t0, t1, level)

    def feather(x, y, ang, length, t, level):
        step = 2.2
        n = max(2, int(length / step))
        curl = math.radians(rng.uniform(-1.6, 1.6))
        gap = rng.uniform(2.4, 4.2) * (1 + level * .5)
        nxt = gap * rng.uniform(.5, 1.5)
        travelled = 0.0
        for i in range(n):
            ang += curl + math.radians(rng.uniform(-3, 3))
            nx, ny = x + math.cos(ang) * step, y + math.sin(ang) * step
            segs.append((x, y, nx, ny, t, t + step, level))
            x, y, t = nx, ny, t + step
            travelled += step
            if travelled >= nxt and level < 3:
                nxt = travelled + gap * rng.uniform(.8, 1.25)
                left = 1 - travelled / length
                for sgn in (1, -1):
                    if rng.random() < .12: continue
                    blen = length * (.10 + .22 * left) * rng.uniform(.6, 1.2) / (1 + level * .9)
                    if level == 0 and rng.random() < .045: blen *= 3.2      # a barb that becomes a feather of its own
                    if blen < 2.2: continue
                    feather(x, y, ang + sgn * math.radians(58 + rng.uniform(-9, 9)), blen, t + rng.uniform(0, 2), level + 1)

    def corner_of(x, y):
        dx, dy = min(x, W - x) / (W / 2), min(y, H - y) / (H / 2)
        return max(0.0, 1 - math.hypot(dx, dy) * 2.2)

    for edge in range(4):
        horizontal = edge < 2
        span = W if horizontal else H
        for _ in range(int(span / 13)):
            q = rng.random()
            if rng.random() < .3: q = rng.choice((rng.uniform(0, .15), rng.uniform(.85, 1)))
            p = q * span
            jitter = math.radians(rng.uniform(-45, 45))
            if edge == 0: x, y, ang = p, -1, math.pi / 2 + jitter
            elif edge == 1: x, y, ang = p, H + 1, -math.pi / 2 + jitter
            elif edge == 2: x, y, ang = -1, p, jitter
            else: x, y, ang = W + 1, p, math.pi + jitter
            c = corner_of(x, y)
            length = (rng.uniform(14, 52) + rng.expovariate(1 / 14)) * (1 + 1.8 * c)
            feather(x, y, ang, length, rng.uniform(-2, 7), 0)
    # Longer diagonal feathers out of each corner.
    for cx, cy in ((0, 0), (W, 0), (0, H), (W, H)):
        for _ in range(7):
            base = math.atan2(H / 2 - cy, W / 2 - cx)
            x = cx + (1 if cx == 0 else -1) * rng.uniform(0, 40)
            y = cy + (1 if cy == 0 else -1) * rng.uniform(0, 25)
            feather(x, y, base + math.radians(rng.uniform(-42, 42)), rng.uniform(55, 120), rng.uniform(0, 8), 0)
    return segs


def arrival_image(segs):
    """Per pixel (supersampled), the earliest arrival time of any dendrite covering it (inf: none), and its level."""
    img = Image.new('F', (W * SS, H * SS), 1e9)
    lev = Image.new('L', (W * SS, H * SS), 255)
    d, dl = ImageDraw.Draw(img), ImageDraw.Draw(lev)
    widths = {0: 1.5, 1: 1.0, 2: .8, 3: .6}
    for x0, y0, x1, y1, t0, t1, level in sorted(segs, key=lambda s: -s[4]):
        w = max(1, int(round(widths.get(level, .9) * SS)))
        d.line([(x0 * SS, y0 * SS), (x1 * SS, y1 * SS)], fill=float(t0), width=w)
        dl.line([(x0 * SS, y0 * SS), (x1 * SS, y1 * SS)], fill=level * 60, width=w)
    return np.array(img, dtype=np.float64), np.array(lev, dtype=np.float64)


def down(a):
    return a.reshape(H, SS, W, SS).mean(axis=(1, 3))


def blur(a, radius):
    im = Image.fromarray(np.clip(a * 255, 0, 255).astype(np.uint8), 'L').filter(ImageFilter.GaussianBlur(radius))
    return np.array(im, dtype=np.float64) / 255


def frost_atlas():
    dist = edge_distance(W, H)
    grain = fbm(W, H, 64, 5, 7)
    fine = np.random.RandomState(3).rand(H, W)
    fine = blur(fine, .7)
    speck = (np.random.RandomState(4).rand(H, W) > .985).astype(np.float64)
    film_t = dist * (.78 + .55 * grain)               # when the film reaches each pixel (uneven front)
    centre = centre_mask(W, H)
    segs = grow_dendrites()
    arrival, level = arrival_image(segs)
    print('dendrite segments:', len(segs))
    frames_a, frames_c = [], []
    prev = np.zeros((H, W))
    for k, reach in enumerate(REACH):
        cover = down((arrival < reach).astype(np.float64))
        core = down(((arrival < reach) & (level < 30)).astype(np.float64))   # the stems: brighter
        halo = blur(cover, 2.2)
        age = np.clip((reach - film_t) / 14, 0, 1)                           # the film fades in behind its front
        depth = np.clip((reach - film_t) / max(reach, 1), 0, 1)               # older frost (outer) is thicker
        film = age * (.10 + .34 * depth) * (.5 + .5 * fine) + age * speck * .45
        a = np.clip(film + cover * .72 + halo * .26, 0, 1)
        a *= 1 - .86 * centre
        a = np.minimum(np.maximum(a, prev), .965)
        frames_a.append(a)
        # Colour: bluish film, white crystals, whitest on the stems.
        white = np.clip(cover * .7 + core * .3, 0, 1)
        r = 196 + (255 - 196) * white
        g = 222 + (255 - 222) * white
        b = 246 + (255 - 246) * white
        frames_c.append(np.stack([r, g, b], -1))
        prev = a
    atlas = np.zeros((H * ROWS, W * COLS, 4), dtype=np.uint8)
    prev = np.zeros((H, W))
    for k in range(FRAMES):
        a = frames_a[k]
        d = 1 - (1 - a) / (1 - prev)                  # the frost added by this frame (see the docstring)
        d = np.clip(d, 0, 1)
        prev = a
        col, row = k % COLS, k // COLS
        cell = atlas[row * H:(row + 1) * H, col * W:(col + 1) * W]
        cell[..., :3] = np.clip(frames_c[k], 0, 255).astype(np.uint8)
        cell[..., 3] = np.clip(np.round(d * 255), 0, 255).astype(np.uint8)
    Image.fromarray(atlas, 'RGBA').save(os.path.join(OUT, 'frost_atlas.png'), optimize=True)
    # A preview of a few cumulative frames on a dark grey picture (for checking only, not used by the game).
    if os.environ.get('FROST_PREVIEW'):
        tiles = []
        for k in (1, 4, 7, 11):
            a = frames_a[k][..., None]
            bg = np.full((H, W, 3), 70.0)
            tiles.append((bg * (1 - a) + frames_c[k] * a).astype(np.uint8))
        Image.fromarray(np.concatenate(tiles, 0), 'RGB').save(os.environ['FROST_PREVIEW'])


# ------------------------------------------------------------------ the crack network
def crack_net():
    w, h = 1024, 576
    k = 2
    img = Image.new('RGBA', (w * k, h * k), (0, 0, 0, 0))
    dark = ImageDraw.Draw(img)
    lines = []
    r = random.Random(77)
    centres = [(r.uniform(.04, .2) * w, r.uniform(.06, .3) * h), (r.uniform(.8, .96) * w, r.uniform(.05, .25) * h),
               (r.uniform(.03, .18) * w, r.uniform(.7, .95) * h), (r.uniform(.82, .97) * w, r.uniform(.72, .96) * h),
               (r.uniform(.42, .58) * w, r.uniform(.9, 1.0) * h), (r.uniform(.45, .6) * w, r.uniform(.0, .08) * h)]
    for cx, cy in centres:
        n = r.randint(7, 10)
        rays = []
        for i in range(n):
            ang = 2 * math.pi * i / n + r.uniform(-.25, .25)
            length = r.uniform(.25, .55) * w
            pts = [(cx, cy)]
            x, y, a = cx, cy, ang
            steps = int(length / 14)
            for s in range(steps):
                a += r.uniform(-.28, .28)
                x, y = x + math.cos(a) * 14, y + math.sin(a) * 14
                pts.append((x, y))
            rays.append(pts)
            lines.append((pts, 1.0))
            # Little side cracks.
            for s in range(2, len(pts) - 1, r.randint(3, 5)):
                bx, by = pts[s]
                ba = a + r.choice((-1, 1)) * r.uniform(.6, 1.1)
                q = [(bx, by)]
                for _ in range(r.randint(2, 5)):
                    ba += r.uniform(-.3, .3)
                    bx, by = bx + math.cos(ba) * 11, by + math.sin(ba) * 11
                    q.append((bx, by))
                lines.append((q, .6))
        # Concentric arcs joining neighbouring rays (the spider-web of broken glass).
        for ring in (.18, .34, .55):
            for i in range(n):
                p, q = rays[i], rays[(i + 1) % n]
                ip, iq = int(len(p) * ring), int(len(q) * ring)
                if ip < len(p) and iq < len(q) and r.random() < .75:
                    (x0, y0), (x1, y1) = p[ip], q[iq]
                    mx, my = (x0 + x1) / 2 + r.uniform(-8, 8), (y0 + y1) / 2 + r.uniform(-8, 8)
                    lines.append(([(x0, y0), (mx, my), (x1, y1)], .7))
    centre = centre_mask(w, h)
    for pts, weight in lines:
        sp = [(x * k, y * k) for x, y in pts]
        dark.line(sp, fill=(25, 55, 95, int(150 * weight)), width=int(3.2 * k))
    bright = Image.new('RGBA', img.size, (0, 0, 0, 0))
    bd = ImageDraw.Draw(bright)
    for pts, weight in lines:
        sp = [(x * k - k, y * k - k) for x, y in pts]
        bd.line(sp, fill=(236, 248, 255, int(235 * weight)), width=max(1, int(1.3 * k)))
    img = Image.alpha_composite(img, bright).resize((w, h), Image.LANCZOS)
    a = np.array(img, dtype=np.float64)
    a[..., 3] *= 1 - .55 * centre
    Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), 'RGBA').save(os.path.join(OUT, 'crack_net.png'), optimize=True)


# ------------------------------------------------------------------ condensation drops
def lens_drops():
    w, h, k = 768, 432, 3
    img = Image.new('RGBA', (w * k, h * k), (0, 0, 0, 0))
    r = random.Random(5)
    dist = edge_distance(w, h)
    near = np.clip(1 - dist / (h * .5), 0, 1)
    drops = []
    for count, r0, r1 in ((1500, 1.0, 2.6), (200, 3.5, 7.5), (34, 9, 16)):
        placed = 0
        while placed < count:
            x, y = r.uniform(0, w), r.uniform(0, h)
            if r.random() > .08 + .92 * near[int(y) % h, int(x) % w] ** 1.4: continue
            drops.append((x, y, r.uniform(r0, r1) * (1 if r.random() < .8 else .7), r.uniform(.85, 1.25)))
            placed += 1
    layer = Image.new('RGBA', img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for x, y, rad, stretch in sorted(drops, key=lambda q: q[2]):
        X, Y, R, RY = x * k, y * k, rad * k, rad * k * stretch
        # The body (faint, cool), the darker lower rim, the bright highlight up-left, a soft lit lower edge.
        d.ellipse((X - R, Y - RY, X + R, Y + RY), fill=(200, 225, 245, 48))
        d.chord((X - R, Y - RY, X + R, Y + RY), 20, 160, fill=(40, 62, 90, 72))
        d.ellipse((X - R * .82, Y - RY * .9, X + R * .82, Y + RY * .62), fill=(215, 236, 252, 55))
        hr = max(.6 * k, R * .26)
        d.ellipse((X - R * .45 - hr, Y - RY * .45 - hr, X - R * .45 + hr, Y - RY * .45 + hr), fill=(255, 255, 255, 230))
        if R > 4 * k:
            d.arc((X - R * .8, Y - RY * .8, X + R * .8, Y + RY * .8), 30, 140, fill=(240, 250, 255, 120), width=max(1, k))
    img = Image.alpha_composite(img, layer).resize((w, h), Image.LANCZOS)
    img.save(os.path.join(OUT, 'lens_drops.png'), optimize=True)


os.makedirs(OUT, exist_ok=True)
frost_atlas()
crack_net()
lens_drops()
for f in ('frost_atlas.png', 'crack_net.png', 'lens_drops.png'):
    print(f, os.path.getsize(os.path.join(OUT, f)) // 1024, 'KB')
print('ok')
