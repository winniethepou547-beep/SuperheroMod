from common import *

def make(path):
    c = S / 2
    img = canvas('#1b2430', '#05070a')
    # Gotham at night: low cloud, the Bat-Signal burning in it, rain.
    img = blob_smoke(img, (40, 48, 60), 24, (0, 0, S, 1200), seed=61, alpha=.6, size=(180, 460))
    sx, sy = c + 420, 360
    img = radial_light(img, sx, sy, 520, '#e8e2b8', .95)
    signal_ = mask(); ellipse(signal_, sx, sy, 300, 190)
    img = paint(img, signal_, (226, 214, 150), .75)
    bat = mask()
    pts = [(0, -.18), (.08, -.42), (.13, -.22), (.32, -.30), (.62, -.42), (1, -.1), (.78, .02), (.66, .28), (.5, .12), (.34, .2), (.18, .1), (.08, .42),
           (0, .3), (-.08, .42), (-.18, .1), (-.34, .2), (-.5, .12), (-.66, .28), (-.78, .02), (-1, -.1), (-.62, -.42), (-.32, -.30), (-.13, -.22), (-.08, -.42)]
    poly(bat, [(sx + x * 250, sy + y * 250) for x, y in pts])
    img = paint(img, bat, (14, 14, 18))
    # The searchlight beam up into the cloud.
    beam = poly(mask(), [(c + 700, 2048), (c + 760, 2048), (sx + 300, sy + 120), (sx - 300, sy + 120)])
    img = glow(img, beam, (200, 196, 150), 30, .25)
    rain = mask(); d = ImageDraw.Draw(rain); rnd = random.Random(62)
    for i in range(420):
        x, y = rnd.random() * S, rnd.random() * S
        d.line([(x, y), (x - 18, y + 90)], fill=90, width=2)
    img = paint(img, rain, (140, 160, 180), .45)
    img = halftone(img, (20, 26, 34), lambda x, y: .5 * y, step=30, strength=.3)
    # The cape: black, heavy, flaring out wide from the shoulders down to the bottom of the frame.
    cape = poly(mask(), [(c - 360, 1170), (c + 360, 1170), (c + 900, 2048), (c + 560, 1980), (c + 300, 2048), (c - 300, 2048), (c - 560, 1980), (c - 900, 2048)])
    img = paint(img, cape, canvas('#16181d', '#050507'))
    img = rim(img, cape, (120, 150, 190), 10, 8, .5)
    img = outline(img, cape, 12)
    # The armoured body: dark gunmetal, broad shoulders, the big black bat across the chest.
    body = poly(mask(), bust(640, 280, 120, 1290, 1080))
    img = paint(img, body, canvas('#4a4f57', '#1c1f24'))
    img = rim(img, body, (170, 190, 220), 12, 8, .7)
    panels = mask(); d = ImageDraw.Draw(panels)
    d.line([(c, 1640), (c, 2048)], fill=255, width=10)
    for y in (1800, 1930): d.line([(c - 260, y), (c + 260, y)], fill=255, width=8)
    for side in (-1, 1): d.line([(c + side * 520, 1420), (c + side * 300, 1560)], fill=255, width=10)
    img = paint(img, ImageChops.multiply(panels, body), (20, 22, 26))
    chest = mask()
    poly(chest, [(c + x * 430, 1520 + y * 300) for x, y in pts])
    img = paint(img, ImageChops.multiply(chest, body), (12, 12, 15))
    img = rim(img, ImageChops.multiply(chest, body), (110, 120, 140), -4, -6, .5)
    pads = mask()
    for side in (-1, 1): poly(pads, [(c + side * 260, 1210), (c + side * 600, 1300), (c + side * 690, 1500), (c + side * 380, 1380)])
    img = paint(img, pads, (18, 19, 22)); img = rim(img, pads, (150, 165, 190), 8, 6, .6); img = outline(img, pads, 8)
    img = outline(img, body, 14)
    # The face below the cowl: a hard jaw, a set mouth.
    face = poly(mask(), [(c - 160, 860), (c + 160, 860), (c + 150, 1010), (c + 90, 1100), (c, 1125), (c - 90, 1100), (c - 150, 1010)])
    img = paint(img, face, canvas('#c99a7c', '#6e4a3a'))
    feat = mask(); d = ImageDraw.Draw(feat)
    d.line([(c - 70, 1035), (c + 70, 1035)], fill=255, width=10)
    d.line([(c - 120, 960), (c - 60, 1090)], fill=140, width=6); d.line([(c + 120, 960), (c + 60, 1090)], fill=140, width=6)
    img = paint(img, ImageChops.multiply(feat, face), (50, 30, 26))
    # The cowl: black, two pointed ears, the mask down over the eyes and nose.
    cowl = mask()
    ellipse(cowl, c, 760, 250, 270)
    for side in (-1, 1):
        poly(cowl, [(c + side * 110, 560), (c + side * 175, 300), (c + side * 230, 590)])
        poly(cowl, [(c + side * 175, 700), (c + side * 270, 760), (c + side * 230, 1000), (c + side * 160, 1060)])
    cowl = ImageChops.subtract(cowl, poly(mask(), [(c - 150, 900), (c + 150, 900), (c + 150, 1130), (c - 150, 1130)]))
    cowl = ImageChops.lighter(cowl, poly(mask(), [(c - 160, 880), (c - 40, 930), (c, 960), (c + 40, 930), (c + 160, 880), (c + 160, 820), (c - 160, 820)]))
    img = paint(img, cowl, canvas('#1d2026', '#0a0b0d'))
    img = rim(img, cowl, (160, 180, 210), -10, -10, .8)
    eyes = mask(); d = ImageDraw.Draw(eyes)
    for side in (-1, 1): d.polygon([(c + side * 40, 840), (c + side * 125, 818), (c + side * 115, 846), (c + side * 45, 860)], fill=255)
    img = glow(img, eyes, (230, 238, 255), 8, .7)
    img = paint(img, eyes, (225, 232, 245), .85)
    img = outline(img, cowl, 14)
    img = misprint(img, cowl, 8)
    img = vignette(img, .78)
    img = grain(img, 8)
    save(img, path)

if __name__ == '__main__':
    make('/tmp/claude-0/batman.png')
