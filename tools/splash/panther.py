from common import *

def lines(m, segs, width):
    d = ImageDraw.Draw(m)
    for (x0, y0, x1, y1) in segs:
        d.line([(x0, y0), (x1, y1)], fill=255, width=width)
    return m

def make(path):
    c = S / 2
    img = canvas('#1a0a33', '#04020a')
    img = radial_light(img, c, 900, 640, '#6a2cd8', .8)
    img = rays(img, c, 900, (90, 40, 170), 26, .3, seed=21)
    # The kinetic lattice behind him: triangles of violet light.
    lat = mask(); d = ImageDraw.Draw(lat)
    rnd = random.Random(23)
    for i in range(16):
        a = i / 16 * math.tau
        r0, r1 = 520 + rnd.random() * 80, 900 + rnd.random() * 120
        x0, y0 = c + math.cos(a) * r0, 860 + math.sin(a) * r0 * .9
        x1, y1 = c + math.cos(a + .2) * r1, 860 + math.sin(a + .2) * r1 * .9
        x2, y2 = c + math.cos(a - .2) * r1, 860 + math.sin(a - .2) * r1 * .9
        d.line([(x0, y0), (x1, y1), (x2, y2), (x0, y0)], fill=170, width=5)
    img = glow(img, lat, (150, 90, 255), 14, 1.1)
    img = paint(img, lat, (200, 170, 255), .55)
    img = halftone(img, (70, 30, 140), lambda x, y: .7 * (1 - y), step=30, strength=.3)
    img = frame_lines(img, (120, 70, 220), seed=24, count=8)
    # The body: black, the suit's sheen along its edges, silver triangle lines across the chest.
    body = poly(mask(), bust(640, 270, 135, 1300, 1080))
    img = paint(img, body, (14, 13, 18))
    img = rim(img, body, (150, 110, 255), 14, 10, .9)
    pattern = lines(mask(), [(c - 520, 1420, c, 1780), (c + 520, 1420, c, 1780), (c - 300, 1330, c - 60, 1560), (c + 300, 1330, c + 60, 1560),
                             (c, 1300, c, 2048), (c - 420, 1700, c + 420, 1700)], 9)
    img = paint(img, ImageChops.multiply(pattern, body), (170, 175, 190))
    img = glow(img, ImageChops.multiply(pattern, body), (150, 90, 255), 10, .9)
    # The Vibranium necklace: silver fangs round the collar.
    neck = mask()
    for i in range(-7, 8):
        a = i * .17
        x, y = c + math.sin(a) * 330, 1230 + (1 - math.cos(a)) * 160
        ln = 120 - abs(i) * 6
        poly(neck, [(x - 26, y), (x + 26, y), (x, y + ln)])
    img = paint(img, neck, (210, 214, 226)); img = rim(img, neck, (255, 255, 255), -6, -6, .8); img = outline(img, neck, 6)
    # The mask: rounded, low pointed ears, a muzzle.
    head = poly(mask(), [(c - 120, 470), (c + 120, 470), (c + 215, 560), (c + 235, 820), (c + 200, 1010), (c + 110, 1130), (c - 110, 1130),
                          (c - 200, 1010), (c - 235, 820), (c - 215, 560)])
    for side in (-1, 1):
        poly(head, [(c + side * 140, 520), (c + side * 230, 400), (c + side * 225, 580)])
    img = paint(img, head, canvas('#26242e', '#08070c'))
    img = rim(img, head, (170, 130, 255), 12, 8, .9)
    # Silver lines of the mask: forehead to nose, brow, round the muzzle, down the cheeks.
    mlines = lines(mask(), [(c, 500, c, 760), (c - 40, 760, c, 900), (c + 40, 760, c, 900), (c - 210, 690, c - 40, 760), (c + 210, 690, c + 40, 760),
                            (c - 120, 940, c - 150, 1100), (c + 120, 940, c + 150, 1100), (c - 120, 940, c + 120, 940),
                            (c - 225, 820, c - 170, 1020), (c + 225, 820, c + 170, 1020)], 8)
    img = paint(img, ImageChops.multiply(mlines, head), (190, 194, 210))
    img = outline(img, head, 14)
    # The eyes: angular, pale, lit violet-white.
    eyes = mask()
    for side in (-1, 1):
        poly(eyes, [(c + side * 45, 800), (c + side * 185, 735), (c + side * 175, 790), (c + side * 55, 840)])
    img = glow(img, eyes, (170, 110, 255), 50, 1.4)
    img = paint(img, eyes, (235, 236, 255))
    # A clawed hand raised in front of one shoulder: four curved claws.
    hand = poly(mask(), [(c + 470, 1500), (c + 610, 1440), (c + 660, 1250), (c + 560, 1210), (c + 450, 1330)])
    img = paint(img, hand, (16, 15, 20)); img = rim(img, hand, (150, 110, 255), 8, 6, .8); img = outline(img, hand, 10)
    claws = mask()
    for i in range(4):
        x, y = c + 560 + i * 34, 1250 - i * 12
        poly(claws, [(x - 10, y), (x + 12, y - 10), (x + 40, y - 190 + i * 10), (x + 10, y - 150 + i * 10)])
    img = paint(img, claws, (225, 228, 238)); img = rim(img, claws, (255, 255, 255), -4, -4, .9); img = outline(img, claws, 6)
    # Four claw slashes across the frame, in violet light.
    slash = mask(); d = ImageDraw.Draw(slash)
    for i in range(4):
        d.line([(c - 900 + i * 40, 250 + i * 34), (c - 280 + i * 40, 1050 + i * 34)], fill=230, width=10)
    img = glow(img, slash, (160, 100, 255), 22, 1.3)
    img = paint(img, slash, (240, 235, 255), .85)
    img = misprint(img, head, 12)
    img = vignette(img, .7)
    img = grain(img, 8)
    save(img, path)

if __name__ == '__main__':
    make('/tmp/claude-0/panther.png')
