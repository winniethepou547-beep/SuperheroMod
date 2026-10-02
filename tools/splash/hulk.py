from common import *

def make(path):
    c = S / 2
    img = canvas('#14320f', '#030803')
    img = radial_light(img, c, 900, 650, '#3fd23a', .7)
    img = rays(img, c, 900, (60, 200, 60), 30, .3, seed=31)
    # Cracked ground glowing below.
    cracks = mask(); d = ImageDraw.Draw(cracks); r = random.Random(32)
    for i in range(14):
        x, y = c + r.uniform(-900, 900), S - r.uniform(0, 260)
        for k in range(6):
            nx, ny = x + r.uniform(-140, 140), y - r.uniform(20, 90)
            d.line([(x, y), (nx, ny)], fill=255, width=8); x, y = nx, ny
    img = glow(img, cracks, (90, 255, 80), 16, 1.2)
    img = halftone(img, (40, 140, 40), lambda x, y: .6 * y, step=30, strength=.3)
    # Massive traps and shoulders, a small head sunk between them.
    body = poly(mask(), [(c - 980, S), (c - 940, 1380), (c - 780, 1140), (c - 480, 1000), (c - 200, 980), (c - 150, 1080), (c + 150, 1080), (c + 200, 980), (c + 480, 1000), (c + 780, 1140), (c + 940, 1380), (c + 980, S)])
    img = paint(img, body, (32, 70, 22)); img = rim(img, body, (140, 255, 110), 16, 10, 1.0); img = outline(img, body, 16)
    # Muscle lines: pecs, delts, veins.
    lines = mask(); d = ImageDraw.Draw(lines)
    d.arc((c - 560, 1250, c - 20, 1700), 200, 340, fill=255, width=14); d.arc((c + 20, 1250, c + 560, 1700), 200, 340, fill=255, width=14)
    d.line([(c, 1300), (c, 1900)], fill=255, width=12)
    d.arc((c - 980, 1120, c - 500, 1600), 180, 300, fill=255, width=14); d.arc((c + 500, 1120, c + 980, 1600), 240, 360, fill=255, width=14)
    for side in (-1, 1): d.line([(c + side * 700, 1300), (c + side * 760, 1450), (c + side * 720, 1600)], fill=255, width=7)
    img = paint(img, lines, (12, 28, 10))
    head = poly(mask(), [(c - 190, 760), (c + 190, 760), (c + 215, 960), (c + 160, 1150), (c, 1200), (c - 160, 1150), (c - 215, 960)])
    img = paint(img, head, (44, 92, 30)); img = rim(img, head, (160, 255, 120), 12, 8, .9)
    hair = poly(mask(), [(c - 230, 860), (c - 200, 640), (c - 60, 560), (c + 120, 580), (c + 240, 680), (c + 230, 860), (c + 150, 760), (c - 150, 760)])
    img = paint(img, hair, (16, 16, 18)); img = outline(img, hair, 10)
    brow = poly(mask(), [(c - 190, 900), (c - 20, 950), (c + 20, 950), (c + 190, 900), (c + 190, 930), (c, 985), (c - 190, 930)])
    img = paint(img, brow, (18, 40, 12))
    mouth = poly(mask(), [(c - 110, 1060), (c + 110, 1060), (c + 80, 1140), (c - 80, 1140)])
    img = paint(img, mouth, (20, 6, 6))
    teeth = mask(); ImageDraw.Draw(teeth).rectangle((c - 100, 1062, c + 100, 1082), fill=255); img = paint(img, teeth, (220, 225, 210))
    img = outline(img, head, 14)
    eyes = mask()
    for side in (-1, 1): poly(eyes, [(c + side * 30, 990), (c + side * 140, 960), (c + side * 130, 1000), (c + side * 40, 1012)])
    img = glow(img, eyes, (90, 255, 70), 50, 1.8); img = paint(img, eyes, (220, 255, 210))
    img = misprint(img, head, 12)
    img = vignette(img, .65); img = grain(img, 8)
    save(img, path)

if __name__ == '__main__':
    make('/tmp/claude-0/hulk.png')
