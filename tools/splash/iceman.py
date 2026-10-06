from common import *

def make(path):
    c = S / 2
    img = canvas('#0d2a44', '#020a14')
    # A cold night: a pale aurora high up, drifting snow, ice crystals rising behind him.
    img = radial_light(img, c - 300, 380, 900, '#3fb6d8', .55)
    img = radial_light(img, c + 500, 520, 700, '#6fe0c8', .3)
    img = blob_smoke(img, (120, 170, 210), 18, (0, 900, S, 2048), seed=71, alpha=.35, size=(160, 420))
    # The crystals: tall faceted spires behind the shoulders, lit at their edges.
    spires = mask()
    rnd = random.Random(72)
    for side in (-1, 1):
        for i in range(5):
            x = c + side * (520 + i * 170 + rnd.random() * 60)
            h = 900 + rnd.random() * 600 - i * 120
            w = 70 + rnd.random() * 60
            lean = side * (40 + i * 25)
            poly(spires, [(x - w, 2048), (x + w, 2048), (x + w * .6 + lean, 2048 - h * .78), (x + lean, 2048 - h), (x - w * .6 + lean, 2048 - h * .8)])
    img = paint(img, spires, canvas('#5aa8d8', '#123a5c'), .85)
    img = rim(img, spires, (200, 240, 255), 10, 6, .8)
    img = outline(img, spires, 8, (4, 20, 34))
    rays_ = img
    img = rays(img, c, 900, (160, 220, 255), count=24, strength=.18, seed=73)
    # The body of ice: pale blue, see-through looking (lighter inside), crystalline facets with white edges.
    body = poly(mask(), bust(620, 260, 115, 1290, 1080))
    img = paint(img, body, canvas('#bfe6ff', '#5ea2d6'), .92)
    inner = poly(mask(), bust(430, 190, 80, 1380, 1140))
    img = paint(img, ImageChops.multiply(inner, body), (232, 246, 255), .55)
    facets = mask(); d = ImageDraw.Draw(facets); rnd = random.Random(74)
    for i in range(46):
        x0, y0 = c + (rnd.random() - .5) * 1300, 1150 + rnd.random() * 900
        x1, y1 = x0 + (rnd.random() - .5) * 360, y0 + (rnd.random() - .3) * 300
        d.line([(x0, y0), (x1, y1)], fill=255, width=4 + int(rnd.random() * 4))
    img = paint(img, ImageChops.multiply(facets, body), (245, 252, 255), .75)
    pecs = mask(); d = ImageDraw.Draw(pecs)
    for side in (-1, 1):
        d.polygon([(c + side * 30, 1360), (c + side * 380, 1330), (c + side * 420, 1560), (c + side * 60, 1600)], fill=110)
        d.line([(c + side * 60, 1600), (c + side * 420, 1560)], fill=255, width=10)
    for y in (1700, 1820, 1940):
        for side in (-1, 1):
            x0, x1 = (c - 140, c - 25) if side < 0 else (c + 25, c + 140)
            d.rounded_rectangle([x0, y, x1, y + 92], radius=26, fill=90)
    pecs = pecs.filter(ImageFilter.GaussianBlur(6))
    img = paint(img, ImageChops.multiply(pecs, body), (60, 120, 180), .55)
    img = rim(img, body, (235, 250, 255), 14, 10, .95)
    img = outline(img, body, 14, (6, 22, 40))
    # The head: a skull of ice, a heavy brow, a crest of crystals swept back, pale glowing eyes.
    head = mask(); ellipse(head, c, 760, 245, 300)
    poly(head, [(c - 160, 900), (c + 160, 900), (c + 120, 1080), (c, 1130), (c - 120, 1080)])
    img = paint(img, head, canvas('#d2efff', '#6aaee0'), .95)
    crest = mask()
    for i in range(6):
        x = c + (i - 2.5) * 70
        poly(crest, [(x - 40, 560 + abs(i - 2.5) * 20), (x + 40, 560 + abs(i - 2.5) * 20), (x + 70, 400 + abs(i - 2.5) * 40), (x + 20, 330 + abs(i - 2.5) * 50)])
    img = paint(img, crest, canvas('#e8f8ff', '#8cc8f0'))
    img = rim(img, crest, (255, 255, 255), 6, 6, .9)
    img = outline(img, crest, 8, (6, 22, 40))
    feat = mask(); d = ImageDraw.Draw(feat)
    d.line([(c - 170, 770), (c - 20, 800)], fill=255, width=22); d.line([(c + 170, 770), (c + 20, 800)], fill=255, width=22)
    d.line([(c, 820), (c, 950)], fill=170, width=12)
    d.line([(c - 70, 1020), (c + 70, 1020)], fill=200, width=10)
    for side in (-1, 1): d.line([(c + side * 150, 900), (c + side * 80, 1080)], fill=120, width=8)
    img = paint(img, ImageChops.multiply(feat, head), (40, 90, 140), .8)
    img = rim(img, head, (240, 252, 255), -10, -10, .9)
    eyes = mask(); d = ImageDraw.Draw(eyes)
    for side in (-1, 1): d.polygon([(c + side * 40, 838), (c + side * 130, 826), (c + side * 120, 852), (c + side * 45, 862)], fill=255)
    img = glow(img, eyes, (170, 230, 255), 26, 1.0)
    img = paint(img, eyes, (240, 252, 255), .95)
    img = outline(img, head, 14, (6, 22, 40))
    # Frost breath and snow over everything.
    img = blob_smoke(img, (220, 240, 255), 6, (c - 260, 1000, c + 260, 1300), seed=75, alpha=.25, size=(60, 160))
    snow = mask(); d = ImageDraw.Draw(snow); rnd = random.Random(76)
    for i in range(520):
        x, y, r = rnd.random() * S, rnd.random() * S, 2 + rnd.random() * 5
        d.ellipse([x - r, y - r, x + r, y + r], fill=200)
    img = paint(img, snow, (235, 245, 255), .8)
    img = halftone(img, (10, 40, 70), lambda x, y: .4 * y, step=30, strength=.25)
    img = misprint(img, body, 6)
    img = vignette(img, .75)
    img = grain(img, 7)
    save(img, path)

if __name__ == '__main__':
    make('/tmp/claude-0/iceman.png')
