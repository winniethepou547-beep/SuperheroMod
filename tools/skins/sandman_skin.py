"""Sandman's skin (64x64, classic wide arms): python3 sandman_skin.py
Flint Marko: brown buzz cut, tanned stubbled face, green shirt with black stripes, khaki trousers,
brown boots, a dusting of sand everywhere, as if he could come apart any moment."""
import os, random
from PIL import Image

R = random.Random(7)
img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
px = img.load()

def jitter(c, j=10):
    return tuple(max(0, min(255, v + R.randint(-j, j))) for v in c) + (255,)

def fill(x0, y0, x1, y1, colour_fn):
    for y in range(y0, y1):
        for x in range(x0, x1):
            px[x, y] = colour_fn(x - x0, y - y0)

SKIN, SKIN_D = (196, 146, 108), (168, 120, 86)
HAIR = (74, 52, 32)
GREEN, BLACK = (52, 120, 58), (24, 28, 24)
KHAKI, KHAKI_D = (150, 128, 88), (124, 104, 70)
BOOT = (78, 52, 32)
SAND = (222, 186, 120)

def sandy(c, chance=.12):
    return lambda x, y: jitter(SAND, 14) if R.random() < chance else jitter(c)

# ---- head (8x8 faces)
fill(8, 0, 16, 8, lambda x, y: jitter(HAIR, 8))          # top
fill(16, 0, 24, 8, lambda x, y: jitter(SKIN_D, 6))       # bottom (chin)
def side(x, y):
    if y < 2: return jitter(HAIR, 8)
    if y == 2 and x < 6: return jitter(HAIR, 8)
    return jitter(SKIN_D if y > 5 else SKIN, 6)
fill(0, 8, 8, 16, side); fill(16, 8, 24, 16, side)
fill(24, 8, 32, 16, lambda x, y: jitter(HAIR if y < 4 else SKIN_D, 8))  # back
def face(x, y):
    if y < 2: return jitter(HAIR, 8)
    if y == 3 and x in (1, 2, 5, 6): return jitter((60, 40, 26), 4)          # brows
    if y == 4 and x in (2, 5): return (24, 20, 18, 255)                       # eyes
    if y == 4 and x in (1, 6): return (232, 226, 214, 255)
    if y == 5 and x in (3, 4): return jitter(SKIN_D, 4)                       # nose
    if y == 6 and 2 <= x <= 5: return jitter((120, 70, 56), 4)               # mouth
    if y >= 6: return jitter((150, 112, 82), 8)                               # stubble
    return jitter(SKIN, 6)
fill(8, 8, 16, 16, face)

# ---- body: green with black stripes, a little sand
def shirt(x, y):
    base = BLACK if y % 3 == 2 else GREEN
    return jitter(SAND, 14) if R.random() < .08 else jitter(base, 8)
fill(20, 16, 28, 20, lambda x, y: jitter(GREEN, 8)); fill(28, 16, 36, 20, lambda x, y: jitter(KHAKI, 6))
for x0, x1 in ((16, 20), (20, 28), (28, 32), (32, 40)): fill(x0, 20, x1, 32, lambda x, y: shirt(x, y) if y < 10 else jitter((60, 44, 30), 6) if y == 10 else jitter(KHAKI, 6))
# Collar.
fill(22, 20, 26, 21, lambda x, y: jitter(BLACK, 4))

# ---- arms: striped sleeves to the elbow, then forearms, sand at the hands
def arm(x, y):
    if y < 5: return shirt(x, y)
    if y < 10: return jitter(SKIN, 6) if R.random() > .15 else jitter(SAND, 12)
    return jitter(SAND, 16) if R.random() < .5 else jitter(SKIN_D, 6)
for base in ((40, 16), (32, 48)):
    bx, by = base
    fill(bx + 4, by, bx + 8, by + 4, lambda x, y: jitter(GREEN, 8)); fill(bx + 8, by, bx + 12, by + 4, lambda x, y: jitter(SKIN_D, 8))
    for k in range(4): fill(bx + k * 4, by + 4, bx + k * 4 + 4, by + 16, arm)

# ---- legs: khaki trousers, brown boots, sand piling at the bottom
def leg(x, y):
    if y >= 9: return jitter(SAND, 16) if R.random() < .35 else jitter(BOOT, 8)
    return jitter(KHAKI_D if x in (0, 3) else KHAKI, 7) if R.random() > .06 else jitter(SAND, 12)
for base in ((0, 16), (16, 48)):
    bx, by = base
    fill(bx + 4, by, bx + 8, by + 4, lambda x, y: jitter(KHAKI, 6)); fill(bx + 8, by, bx + 12, by + 4, lambda x, y: jitter(BOOT, 6))
    for k in range(4): fill(bx + k * 4, by + 4, bx + k * 4 + 4, by + 16, leg)

out = os.path.join(os.path.dirname(__file__), '../../src/main/resources/assets/superheromod/textures/entity/sandman.png')
img.save(out)
img.resize((512, 512), Image.NEAREST).save('/tmp/claude-0/sandman_skin_big.png')
print('saved', out)
