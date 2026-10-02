from common import *

def flames(cx, cy, w, h, seed, count=16):
    r = random.Random(seed); m = mask(); d = ImageDraw.Draw(m)
    for i in range(count):
        x = cx + r.uniform(-w / 2, w / 2); hh = h * r.uniform(.5, 1.1); ww = w * r.uniform(.12, .25)
        d.polygon([(x - ww, cy), (x - ww * .3, cy - hh * .55), (x + r.uniform(-ww, ww), cy - hh), (x + ww * .4, cy - hh * .5), (x + ww, cy)], fill=int(255 * r.uniform(.6, 1)))
    return m.filter(ImageFilter.GaussianBlur(8))

def make(path):
    c = S / 2
    img = canvas('#3c0c02', '#070100')
    img = radial_light(img, c, 800, 700, '#ff6a10', .8)
    low = flames(c, S + 200, S * 1.4, 900, 41, 30)
    img = glow(img, low, (255, 110, 20), 30, 1.1); img = paint(img, low, (255, 150, 40), .5)
    embers = mask(); d = ImageDraw.Draw(embers); r = random.Random(42)
    for i in range(120):
        x, y, rr = r.uniform(0, S), r.uniform(0, S), r.uniform(3, 9); d.ellipse((x - rr, y - rr, x + rr, y + rr), fill=255)
    img = glow(img, embers, (255, 140, 40), 8, 1.2)
    img = halftone(img, (160, 40, 0), lambda x, y: .6 * y, step=30, strength=.3)
    body = poly(mask(), bust(620, 260, 140, 1300, 1100))
    img = paint(img, body, (20, 14, 12)); img = rim(img, body, (255, 120, 30), 14, 10, .9); img = outline(img, body, 14)
    # Leather jacket spikes on the shoulders, the chain across the chest.
    for side in (-1, 1):
        for k in range(4):
            x = c + side * (380 + k * 70); y = 1270 + k * 30
            sp = poly(mask(), [(x - 22, y), (x + 22, y), (x + side * 10, y - 110)]); img = paint(img, sp, (190, 190, 195)); img = outline(img, sp, 6)
    chain = mask(); d = ImageDraw.Draw(chain)
    for k in range(9):
        x = c - 520 + k * 125; y = 1500 + k * 55
        d.ellipse((x - 60, y - 30, x + 60, y + 30), outline=255, width=18)
    img = glow(img, chain, (255, 120, 20), 20, 1.0); img = paint(img, chain, (255, 170, 70))
    # The flames rising off the skull.
    fire = flames(c, 900, 520, 700, 43, 18)
    img = glow(img, fire, (255, 90, 10), 40, 1.6); img = paint(img, fire, (255, 160, 40), .9)
    core = flames(c, 860, 360, 480, 44, 10); img = paint(img, core, (255, 235, 150), .8)
    # The skull.
    skull = poly(mask(), [(c - 200, 760), (c - 150, 640), (c, 600), (c + 150, 640), (c + 200, 760), (c + 190, 930), (c + 130, 1020), (c + 120, 1150), (c - 120, 1150), (c - 130, 1020), (c - 190, 930)])
    img = paint(img, skull, canvas('#f4ead6', '#9a8a70')); img = rim(img, skull, (255, 180, 80), 10, 8, .5)
    sockets = mask()
    for side in (-1, 1): ellipse(sockets, c + side * 80, 860, 62, 72)
    poly(sockets, [(c - 26, 960), (c + 26, 960), (c, 1010)])
    img = paint(img, sockets, (14, 6, 4))
    flare = mask()
    for side in (-1, 1): ellipse(flare, c + side * 80, 870, 22, 26)
    img = glow(img, flare, (255, 120, 20), 40, 1.8); img = paint(img, flare, (255, 220, 120))
    teeth = mask(); d = ImageDraw.Draw(teeth)
    for i in range(-4, 5): d.rectangle((c + i * 26 - 10, 1060, c + i * 26 + 10, 1120), fill=255)
    img = paint(img, teeth, (40, 30, 24))
    img = outline(img, skull, 12)
    img = misprint(img, skull, 12)
    img = vignette(img, .65); img = grain(img, 8)
    save(img, path)

if __name__ == '__main__':
    make('/tmp/claude-0/ghost_rider.png')
