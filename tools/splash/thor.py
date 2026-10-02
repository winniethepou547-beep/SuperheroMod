from common import *

def make(path):
    c = S / 2
    img = canvas('#1d3048', '#06090f')
    img = radial_light(img, c, 760, 560, '#4a78b8', .8)
    img = blob_smoke(img, (40, 52, 70), 26, (0, 0, S, 900), seed=21, alpha=.7, size=(200, 420))
    # Lightning across the storm.
    bolts = mask(); d = ImageDraw.Draw(bolts)
    bolt(d, 200, 0, 520, 900, 3, 14, 90); bolt(d, 1800, 0, 1500, 820, 9, 12, 80); bolt(d, 1100, 0, 1350, 500, 13, 8, 60)
    img = glow(img, bolts, (120, 180, 255), 30, 1.4); img = paint(img, bolts, (235, 245, 255))
    img = halftone(img, (60, 110, 180), lambda x, y: .7 * (1 - y), step=30, strength=.3)
    # Red cape behind the shoulders.
    cape = poly(mask(), [(c - 760, S), (c - 600, 1250), (c - 260, 1150), (c + 260, 1150), (c + 600, 1250), (c + 760, S)])
    img = paint(img, cape, (130, 14, 18)); img = rim(img, cape, (255, 90, 80), 12, 10, .6); img = outline(img, cape, 12)
    body = poly(mask(), bust(560, 250, 130, 1300, 1080))
    img = paint(img, body, (24, 26, 32)); img = rim(img, body, (140, 190, 255), 14, 8, .9)
    # Armour discs.
    for x, y in ((c - 210, 1520), (c + 210, 1520), (c - 210, 1740), (c + 210, 1740)):
        m = ellipse(mask(), x, y, 90, 90)
        img = paint(img, m, (70, 74, 84)); img = rim(img, m, (200, 220, 255), 8, 8, .7); img = outline(img, m, 8)
    # Shoulder-length hair and the head.
    hair = poly(mask(), [(c, 560), (c + 230, 650), (c + 280, 900), (c + 300, 1230), (c + 160, 1250), (c + 150, 1000), (c - 150, 1000), (c - 160, 1250), (c - 300, 1230), (c - 280, 900), (c - 230, 650)])
    img = paint(img, hair, (150, 112, 50)); img = rim(img, hair, (255, 220, 140), 12, 8, .8); img = outline(img, hair, 12)
    head = ellipse(mask(), c, 900, 175, 240)
    img = paint(img, head, (150, 112, 92)); img = rim(img, head, (160, 200, 255), 12, 8, .6)
    beard = poly(mask(), [(c - 160, 960), (c + 160, 960), (c + 110, 1120), (c, 1160), (c - 110, 1120)])
    img = paint(img, beard, (110, 80, 36)); img = outline(img, head, 12)
    # Eyes crackling with lightning.
    eyes = mask()
    for side in (-1, 1): ellipse(eyes, c + side * 70, 880, 42, 18)
    img = glow(img, eyes, (130, 190, 255), 50, 1.8); img = paint(img, eyes, (240, 250, 255))
    # Mjolnir raised at his side, lightning off it.
    handle = poly(mask(), [(c + 470, 1180), (c + 520, 1180), (c + 560, 760), (c + 510, 760)])
    img = paint(img, handle, (110, 70, 40)); img = outline(img, handle, 8)
    hammer = poly(mask(), [(c + 360, 520), (c + 720, 560), (c + 700, 800), (c + 340, 760)])
    img = paint(img, hammer, canvas('#e2e6ee', '#6b707c')); img = rim(img, hammer, (255, 255, 255), 10, 10, .6); img = outline(img, hammer, 14)
    sparks = mask(); d = ImageDraw.Draw(sparks)
    for k in range(5): bolt(d, c + 530, 640, c + 530 + math.cos(k * 1.3) * 420, 640 + math.sin(k * 1.3) * 380, 40 + k, 6, 40)
    img = glow(img, sparks, (120, 190, 255), 18, 1.4); img = paint(img, sparks, (230, 240, 255))
    img = misprint(img, ImageChops.lighter(head, hammer), 12)
    img = vignette(img, .7); img = grain(img, 8)
    save(img, path)

if __name__ == '__main__':
    make('/tmp/claude-0/thor.png')
