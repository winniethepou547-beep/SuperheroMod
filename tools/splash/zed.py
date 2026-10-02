from common import *

def make(path):
    c = S / 2
    img = canvas('#2a060c', '#050206')
    img = radial_light(img, c, 820, 520, '#a01020', .9)
    img = rays(img, c, 820, (120, 20, 30), 24, .35, seed=4)
    img = blob_smoke(img, (6, 3, 8), 22, (0, 900, S, S), seed=5, alpha=.85, size=(150, 380))
    img = halftone(img, (90, 10, 20), lambda x, y: (1 - abs(x - .5) * 2) * .6 * (1 - y), step=30, strength=.35)
    img = frame_lines(img, (140, 20, 40), seed=8, count=7)
    # Two great shurikens behind the shoulders.
    for cx, rot in ((640, .3), (1408, -.2)):
        m = poly(mask(), shuriken(cx, 1080, 470, rot, .6))
        img = paint(img, m, (150, 155, 165))
        img = rim(img, m, (230, 235, 245), -10, -10, .9)
        img = outline(img, m, 10)
        img = paint(img, ellipse(mask(), cx, 1080, 70, 70), (40, 42, 48))
    # Body: dark plate with crimson cowl.
    body = poly(mask(), bust(680, 280, 140, 1300, 1090))
    img = paint(img, body, (22, 18, 24))
    img = rim(img, body, (200, 40, 60), 14, 10, .9)
    # Pauldrons: layered silver plates flaring up and out.
    for side in (-1, 1):
        for k, (w, h, y) in enumerate(((340, 230, 1300), (330, 130, 1440), (290, 120, 1550))):
            x = c + side * (450 - k * 10)
            if k == 0:
                m = ellipse(mask(), x, y, w * .55, h * .5)
            else:
                pts = [(x - side * w * .55, y - h * .1), (x + side * w * .55, y - h * .45), (x + side * w * .6, y + h * .45), (x - side * w * .5, y + h * .6)]
                m = poly(mask(), pts)
            img = paint(img, m, (120 - k * 15, 124 - k * 15, 135 - k * 15))
            img = rim(img, m, (235, 190, 100), 0, 14, .8)
            img = outline(img, m, 8)
        spike = poly(mask(), [(c + side * 420, 1270), (c + side * 690, 1050), (c + side * 560, 1300)])
        img = paint(img, spike, (170, 172, 182)); img = outline(img, spike, 8)
    cowl = poly(mask(), [(c - 230, 1160), (c + 230, 1160), (c + 120, 1500), (c - 60, 1700), (c - 180, 1420)])
    img = paint(img, cowl, (120, 14, 22)); img = rim(img, cowl, (255, 70, 80), 8, 8, .6); img = outline(img, cowl, 10)
    # Crimson hood, pointed, framing the helm.
    hood = poly(mask(), [(c, 380), (c + 300, 640), (c + 330, 980), (c + 250, 1230), (c - 250, 1230), (c - 330, 980), (c - 300, 640)])
    img = paint(img, hood, (70, 6, 12)); img = rim(img, hood, (220, 40, 50), 14, 6, .55); img = outline(img, hood, 14)
    # Silver helm: crest to a point, cheek guards, narrow jaw.
    helm = poly(mask(), [(c, 470), (c + 175, 640), (c + 205, 860), (c + 160, 1060), (c + 70, 1150), (c - 70, 1150), (c - 160, 1060), (c - 205, 860), (c - 175, 640)])
    grad = canvas('#d8dbe2', '#5a5e68')
    img = paint(img, helm, grad)
    img = rim(img, helm, (255, 255, 255), 10, 10, .5)
    crest = poly(mask(), [(c, 470), (c + 40, 560), (c + 22, 760), (c - 22, 760), (c - 40, 560)])
    img = paint(img, crest, (225, 175, 80)); img = outline(img, crest, 6)
    # The face: a black V-shaped band where the eyes are, a barred grille.
    band = poly(mask(), [(c - 190, 790), (c, 860), (c + 190, 790), (c + 175, 900), (c, 950), (c - 175, 900)])
    img = paint(img, band, (12, 8, 12))
    grille = mask(); d = ImageDraw.Draw(grille)
    for i in range(-3, 4):
        d.rectangle((c + i * 42 - 9, 960, c + i * 42 + 9, 1120), fill=255)
    img = paint(img, grille, (25, 24, 30))
    img = outline(img, helm, 14)
    # The eyes: two slanted slits of red light, blooming.
    eyes = mask()
    for side in (-1, 1):
        poly(eyes, [(c + side * 40, 885), (c + side * 160, 835), (c + side * 165, 862), (c + side * 45, 905)])
    img = glow(img, eyes, (255, 40, 30), 60, 1.6)
    img = glow(img, eyes, (255, 80, 40), 18, 1.2)
    img = paint(img, eyes, (255, 230, 210))
    # A red streak from one eye.
    streak = mask(); ImageDraw.Draw(streak).polygon([(c + 160, 840), (c + 700, 760), (c + 690, 775), (c + 165, 860)], fill=200)
    img = glow(img, streak.filter(ImageFilter.GaussianBlur(6)), (255, 30, 30), 20, 1.2)
    ink = helm
    img = misprint(img, ink, 12)
    img = vignette(img, .7)
    img = grain(img, 8)
    save(img, path)

if __name__ == '__main__':
    make('/tmp/claude-0/zed.png')
