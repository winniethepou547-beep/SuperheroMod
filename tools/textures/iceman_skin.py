"""Iceman's textures, Minecraft style (16 texels to a block, 1 texel to a model pixel), after the user's reference:
a blocky Minecraft Iceman of bright blue ice in the X-Men suit.

ice.png    16 x 16, tiles seamlessly: bright blue ice as it really looks: a light cyan-blue body, the deeper blue of thick
           ice in pockets, white fracture streaks running across it on the slant (the planes inside ice that catch the
           light), small white air bubbles trapped in it, a frosty lighter grain. Every ice shape that is not his body
           is mapped with it (16 texels a block in the world, 1 a pixel on him).
frost.png  16 x 16, tiles: packed ice / rime: whiter, finer grain, faint blue. Texel (0, 0) is pure white (the plain
           colour for anything drawn flat).
skin.png   64 x 64: his body's boxes in Minecraft's box layout (top, bottom; right side, front, left side, back), each
           face framed by a lighter edge where it is ice (the rounded light a block of ice catches on its edges):
           the ice head with a simple pixel face (white eyes under heavy brows, the nose, a set mouth), the frosty hair
           layer, the suit (red and black, dark grey panels, the X on the chest, grey belt), ice sleeves of the arms,
           ice fists, the shorts with ice from mid thigh, ice shins and feet.
The layout is mirrored in IcemanBody.SKIN_* (u, v, w, h, d per box): change both together.
Run: python3 tools/textures/iceman_skin.py
"""
import os
import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'superheromod', 'textures', 'entity', 'iceman')
rng = np.random.default_rng(1963)

# The ice's ramp, dark to light (the reference: bright blue Minecraft ice, never grey).
ICE = np.array([(44, 110, 204), (64, 140, 236), (88, 168, 250), (114, 192, 255), (140, 208, 255), (168, 224, 255), (206, 240, 255)], float)
FROST = np.array([(118, 170, 228), (140, 190, 240), (162, 206, 248), (184, 220, 252), (204, 232, 255), (224, 242, 255), (255, 255, 255)], float)


def ramp(pal, t):
    """Colour of value t (0..1) on a ramp, snapped to its steps (pixel art, no smooth gradients)."""
    i = np.clip(np.round(t * (len(pal) - 1)), 0, len(pal) - 1).astype(int)
    return pal[i]


def torus_noise(w, h, cell, seed):
    """Smooth value noise in 0..1 that tiles (lattice wraps)."""
    r = np.random.default_rng(seed)
    gw, gh = max(1, w // cell), max(1, h // cell)
    g = r.random((gh, gw))
    out = np.zeros((h, w))
    for y in range(h):
        for x in range(w):
            fx, fy = x / cell, y / cell
            x0, y0 = int(fx) % gw, int(fy) % gh
            x1, y1 = (x0 + 1) % gw, (y0 + 1) % gh
            tx, ty = fx - int(fx), fy - int(fy)
            tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
            a = g[y0, x0] * (1 - tx) + g[y0, x1] * tx
            b = g[y1, x0] * (1 - tx) + g[y1, x1] * tx
            out[y, x] = a * (1 - ty) + b * ty
    return out


def ice_value(w, h, seed, streaks=2, bubbles=3, base=.5):
    """The value field of a patch of ice (0..1): broad pockets of deeper blue and lighter ice, a faint grain, a few
    short slanted white streaks (fracture planes catching the light, broken, never ruled lines), white bubbles."""
    r = np.random.default_rng(seed)
    v = base + .34 * (torus_noise(w, h, 8, seed) - .5) + .12 * (torus_noise(w, h, 4, seed + 1) - .5) + .04 * (r.random((h, w)) - .5)
    for k in range(streaks):
        # A fracture plane seen edge on: a short slanted run of bright texels with a softer one beside it.
        x0, y0 = r.integers(0, w), r.integers(0, h)
        n = int(r.integers(4, 8))
        for i in range(n):
            x, y = (x0 + i) % w, (y0 + i) % h
            v[y, x] += .4 if 0 < i < n - 1 else .22
            v[y, (x + 1) % w] += .12
    for k in range(bubbles):
        bx, by = r.integers(0, w), r.integers(0, h)
        v[by, bx] = 1.0
    return np.clip(v, 0, 1)


def save(img, name):
    os.makedirs(OUT, exist_ok=True)
    Image.fromarray(img.astype(np.uint8), 'RGBA').save(os.path.join(OUT, name))


def tile(pal, seed, streaks, bubbles, base):
    v = ice_value(16, 16, seed, streaks, bubbles, base)
    rgb = ramp(pal, v)
    a = np.full((16, 16, 1), 255.0)
    return np.concatenate([rgb, a], axis=2)


# ------------------------------------------------------------------ the tiles
ice = tile(ICE, 11, 3, 3, .5)
frost = tile(FROST, 23, 2, 3, .5)
frost[0, 0] = (255, 255, 255, 255)

# ------------------------------------------------------------------ the skin
SKIN = np.zeros((64, 64, 4))
RED, RED_D, RED_L = (176, 22, 32), (118, 14, 22), (206, 44, 50)
BLACK, BLACK_L = (22, 23, 30), (40, 42, 52)
GREY, GREY_L, GREY_D = (70, 72, 82), (104, 106, 116), (48, 50, 58)


def faces(u, v, w, h, d):
    """The six face rectangles of a box in Minecraft's layout: name -> (x, y, width, height)."""
    return {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
            'left': (u + d + w, v + d, d, h), 'back': (u + d + w + d, v + d, w, h)}


def ice_face(x, y, w, h, seed, kind='side', pal=ICE, base=.5, edge=True):
    """Ice on one face: the patch, lighter on top faces, darker toward the bottom of sides, a light frame on the edges."""
    v = ice_value(w, h, seed, 1 + (w * h > 40), 1 + (w * h > 30), base)
    if kind == 'top': v = v + .14
    if kind == 'bottom': v = v - .12
    if kind == 'side': v = v - np.linspace(-.04, .1, h)[:, None]
    if edge:
        e = np.zeros((h, w))
        e[0, :] += .2; e[-1, :] -= .06; e[:, 0] += .1; e[:, -1] += .06
        v = v + e
    SKIN[y:y + h, x:x + w, :3] = ramp(pal, np.clip(v, 0, 1))
    SKIN[y:y + h, x:x + w, 3] = 255


def box_ice(u, v, w, h, d, seed, pal=ICE, base=.5):
    for i, (name, (x, y, fw, fh)) in enumerate(faces(u, v, w, h, d).items()):
        ice_face(x, y, fw, fh, seed + i * 7, 'top' if name == 'top' else 'bottom' if name == 'bottom' else 'side', pal, base)


def fill(x, y, w, h, c):
    SKIN[y:y + h, x:x + w, :3] = c
    SKIN[y:y + h, x:x + w, 3] = 255


def px(x, y, c):
    SKIN[y, x, :3] = c
    SKIN[y, x, 3] = 255


def cloth(x, y, w, h, c, seed, amp=10):
    """Suit cloth: the colour with a faint weave (a few texels a shade lighter or darker)."""
    r = np.random.default_rng(seed)
    n = r.integers(-1, 2, (h, w))[:, :, None] * amp
    SKIN[y:y + h, x:x + w, :3] = np.clip(np.array(c, float)[None, None, :] + n, 0, 255)
    SKIN[y:y + h, x:x + w, 3] = 255


# ---- head (0, 0), 8 x 8 x 8: ice, the face on the front.
HEAD = (0, 0, 8, 8, 8)
box_ice(*HEAD, 100, base=.58)
fx, fy, _, _ = faces(*HEAD)['front']
eye_w, brow = (255, 255, 255), ICE[0]
for s in (0, 1):
    ex = fx + (1 if s == 0 else 5)
    # The brow: a heavy darker bar over each eye, lower at the inside (a frown).
    px(ex, fy + 2, brow); px(ex + 1, fy + 2, brow)
    px(ex + (1 if s == 0 else 0), fy + 3, ICE[1])
    # The eye: two white texels, glowing (IcemanBody adds the glow).
    px(ex, fy + 4, eye_w if s == 0 else (220, 244, 255)); px(ex + 1, fy + 4, (220, 244, 255) if s == 0 else eye_w)
    # The socket under it, a shade deeper.
    px(ex, fy + 5, ICE[3]); px(ex + 1, fy + 5, ICE[3])
# The nose: a lighter ridge with a shadow under it; the mouth set hard; the jaw line.
px(fx + 3, fy + 4, ICE[5]); px(fx + 4, fy + 4, ICE[4])
px(fx + 3, fy + 5, ICE[5]); px(fx + 4, fy + 5, ICE[3])
fill(fx + 2, fy + 6, 4, 1, ICE[1])
px(fx + 2, fy + 6, ICE[3]); px(fx + 5, fy + 6, ICE[3])
fill(fx + 1, fy + 7, 6, 1, ICE[3])
# The forehead lit, a crack across it.
fill(fx + 1, fy + 1, 6, 1, ICE[5])
px(fx + 6, fy + 1, ICE[6]); px(fx + 5, fy + 2, ICE[5])

# ---- hair layer (32, 0), 8 x 8 x 8 drawn 0.5 bigger: frosted ice on the top and the upper sides and back, swept up.
HAIR = (32, 0, 8, 8, 8)
f = faces(*HAIR)
ice_face(*f['top'], 200, 'top', ICE, .5)
for name in ('right', 'left', 'back', 'front'):
    x, y, w, h = f[name]
    ice_face(x, y, w, h, 210 + len(name), 'side', ICE, .45)
    # Cut away below the hairline: higher on the front (the forehead shows), ragged, lower behind.
    for i in range(w):
        if name == 'front': stop = 1 + (i % 3 == 1) + (i in (0, 7))
        elif name == 'back': stop = 5 + (i % 2)
        else:
            k = i / max(1, w - 1)
            k = k if name == 'left' else 1 - k        # toward the back the hair comes lower
            stop = int(2 + 3 * k) + (i % 2)
        SKIN[y + stop:y + h, x + i, 3] = 0

# ---- chest (0, 16), 8 x 7 x 4: red over the shoulders and the chest, the black V neckline to the X emblem, black sides.
CHEST = (0, 16, 8, 7, 4)
f = faces(*CHEST)
x, y, w, h = f['front']
cloth(x, y, w, h, RED, 1)
for j in range(h):
    half = max(0, 3 - j)           # the V neckline narrowing down to the emblem
    for i in range(w):
        if abs(i - 3.5) < half: px(x + i, y + j, BLACK)
# The X emblem: a black disc ringed red, the red X (4 x 4 at the breastbone).
for (i, j) in [(2, 3), (5, 3), (2, 6), (5, 6)]: px(x + i, y + j, RED_D)
fill(x + 3, y + 3, 2, 4, BLACK); fill(x + 2, y + 4, 4, 2, BLACK)
for (i, j) in [(3, 4), (4, 5), (4, 4), (3, 5)]: px(x + i, y + j, RED_L)
# Dark side panels at the flanks.
fill(x, y + 4, 1, 3, BLACK); fill(x + 7, y + 4, 1, 3, BLACK)
for name in ('right', 'left'):
    x, y, w, h = f[name]
    cloth(x, y, w, h, BLACK, 3)
    fill(x, y, w, 2, RED)
x, y, w, h = f['back']
cloth(x, y, w, h, BLACK, 4)
fill(x, y, 2, 2, RED); fill(x + 6, y, 2, 2, RED)
fill(x + 3, y + 1, 2, 6, BLACK_L)
for name in ('top', 'bottom'):
    x, y, w, h = f[name]
    cloth(x, y, w, h, RED, 5)
    if name == 'top': fill(x + 2, y + 1, 4, 2, BLACK)   # the collar round the neck

# ---- abdomen (24, 16), 8 x 5 x 4: the red V narrowing to the belt, black sides with dark grey panels.
ABD = (24, 16, 8, 5, 4)
f = faces(*ABD)
x, y, w, h = f['front']
cloth(x, y, w, h, BLACK, 6)
for j in range(h):
    half = 3.4 - j * .62
    for i in range(w):
        if abs(i - 3.5) < half: px(x + i, y + j, RED if abs(i - 3.5) < half - 1 else RED_D)
fill(x, y, 1, h, GREY_D); fill(x + 7, y, 1, h, GREY_D)
for name in ('right', 'left', 'back'):
    x, y, w, h = f[name]
    cloth(x, y, w, h, BLACK, 7 + len(name))
    fill(x + (1 if name != 'back' else 3), y, 2, h, GREY_D)
for name in ('top', 'bottom'):
    x, y, w, h = f[name]
    cloth(x, y, w, h, BLACK, 8)

# ---- pelvis (0, 27), 8 x 4 x 4: the grey belt with its buckle, black shorts.
PEL = (0, 27, 8, 4, 4)
f = faces(*PEL)
for name, (x, y, w, h) in f.items():
    cloth(x, y, w, h, BLACK, 9 + len(name))
    if name not in ('top', 'bottom'):
        fill(x, y, w, 1, GREY)
        fill(x, y + 1, w, 1, GREY_D)
x, y, w, h = f['front']
fill(x + 3, y, 2, 2, GREY_L)
px(x + 3, y + 3, BLACK_L); px(x + 4, y + 3, BLACK_L)

# ---- upper arm (48, 16), 4 x 7 x 4: the red shoulder, then ice (his arms are ice from the sleeve down, like the reference).
UARM = (48, 16, 4, 7, 4)
box_ice(*UARM, 300, base=.52)
f = faces(*UARM)
for name in ('right', 'front', 'left', 'back'):
    x, y, w, h = f[name]
    cloth(x, y, w, 3, RED, 11 + len(name))
    fill(x, y + 2, w, 1, RED_D)
    # The ice creeping up over the sleeve's edge, ragged.
    for i in range(w):
        if (i + len(name)) % 3 == 0: SKIN[y + 2, x + i, :3] = ICE[4]
x, y, w, h = f['top']
cloth(x, y, w, h, RED, 12)

# ---- forearm (24, 27), 4 x 6 x 4: thick ice.
FARM = (24, 27, 4, 6, 4)
box_ice(*FARM, 400, base=.5)
# ---- hand (40, 27), 4 x 4 x 4: an ice fist, the knuckles' grooves on the front.
HAND = (40, 27, 4, 4, 4)
box_ice(*HAND, 500, base=.56)
f = faces(*HAND)
x, y, w, h = f['bottom']
for i in range(4): px(x + i, y + (i % 2) + 1, ICE[2])
for name in ('front', 'back'):
    x, y, w, h = f[name]
    px(x + 1, y + 3, ICE[2]); px(x + 2, y + 3, ICE[3])

# ---- thigh (0, 37), 4 x 6 x 4: the shorts (black, grey trim) to mid thigh, then ice.
THIGH = (0, 37, 4, 6, 4)
box_ice(*THIGH, 600, base=.52)
f = faces(*THIGH)
for name in ('right', 'front', 'left', 'back'):
    x, y, w, h = f[name]
    cloth(x, y, w, 3, BLACK, 13 + len(name))
    fill(x, y + 2, w, 1, GREY_D)
    for i in range(w):
        if (i + len(name)) % 2 == 0: SKIN[y + 3, x + i, :3] = ICE[5]
x, y, w, h = f['top']
cloth(x, y, w, h, BLACK, 14)
# ---- shin (16, 37), 4 x 6 x 4 and foot (32, 37), 4 x 2 x 5: ice.
SHIN = (16, 37, 4, 6, 4)
box_ice(*SHIN, 700, base=.5)
FOOT = (32, 37, 4, 2, 5)
box_ice(*FOOT, 800, base=.46)

save(ice, 'ice.png')
save(frost, 'frost.png')
save(SKIN, 'skin.png')
print('written to', os.path.abspath(OUT))
