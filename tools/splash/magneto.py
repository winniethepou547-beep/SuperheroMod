from common import *

def make(path):
    c = S / 2
    img = canvas('#3a3540', '#0c0a10')
    # The storm: heavy grey cloud, a burst of light breaking through behind him.
    img = blob_smoke(img, (70, 66, 78), 26, (0, 0, S, S), seed=41, alpha=.55, size=(160, 420))
    img = radial_light(img, c + 260, 520, 700, '#e8dcc8', .9)
    img = rays(img, c + 260, 520, (210, 200, 180), 30, .35, seed=42)
    img = halftone(img, (40, 36, 46), lambda x, y: .6 * y, step=30, strength=.35)
    # The cape behind him: violet, flaring wide off both shoulders.
    cape = poly(mask(), [(c - 330, 1180), (c + 330, 1180), (c + 760, 2048), (c - 820, 2048), (c - 900, 1700)])
    img = paint(img, cape, canvas('#4a2266', '#170824'))
    img = rim(img, cape, (170, 120, 230), 10, 8, .6)
    img = outline(img, cape, 12)
    # The body: the crimson armoured suit, chest plates in a V.
    body = poly(mask(), bust(600, 260, 130, 1290, 1090))
    img = paint(img, body, canvas('#8e1620', '#3a060c'))
    img = rim(img, body, (255, 140, 120), 12, 8, .8)
    plates = mask(); d = ImageDraw.Draw(plates)
    d.line([(c - 430, 1380), (c, 1640), (c + 430, 1380)], fill=255, width=12)
    d.line([(c, 1640), (c, 2048)], fill=255, width=10)
    for y in (1780, 1920):
        d.line([(c - 300, y), (c + 300, y)], fill=255, width=8)
    img = paint(img, ImageChops.multiply(plates, body), (40, 4, 8))
    # The violet mantle over the shoulders.
    mantle = mask()
    for side in (-1, 1):
        poly(mantle, [(c + side * 140, 1150), (c + side * 520, 1250), (c + side * 640, 1420), (c + side * 300, 1300)])
    img = paint(img, mantle, (90, 44, 128)); img = rim(img, mantle, (190, 140, 240), 8, 6, .7); img = outline(img, mantle, 8)
    img = outline(img, body, 14)
    # The face inside the helmet: an old man, grey brows, a hard mouth.
    face = poly(mask(), [(c - 150, 640), (c + 150, 640), (c + 170, 900), (c + 120, 1060), (c, 1110), (c - 120, 1060), (c - 170, 900)])
    img = paint(img, face, canvas('#d9a68a', '#8a5a48'))
    feat = mask(); d = ImageDraw.Draw(feat)
    for side in (-1, 1):
        d.line([(c + side * 30, 760), (c + side * 130, 735)], fill=255, width=16)
        d.line([(c + side * 50, 820), (c + side * 110, 815)], fill=255, width=12)
        d.line([(c + side * 70, 900), (c + side * 95, 1000)], fill=200, width=6)
    d.line([(c - 60, 1010), (c + 60, 1010)], fill=255, width=9)
    d.line([(c, 820), (c - 12, 930)], fill=160, width=7)
    img = paint(img, ImageChops.multiply(feat, face), (60, 34, 30))
    brows = mask(); d = ImageDraw.Draw(brows)
    for side in (-1, 1): d.line([(c + side * 30, 745), (c + side * 135, 720)], fill=255, width=14)
    img = paint(img, brows, (200, 200, 205))
    # The helmet: the crimson dome with its crest, cheek guards, violet trim round the face.
    helm = mask()
    ellipse(helm, c, 640, 270, 260)
    for side in (-1, 1):
        poly(helm, [(c + side * 170, 600), (c + side * 285, 640), (c + side * 270, 1000), (c + side * 175, 1080), (c + side * 165, 860)])
    helm = ImageChops.subtract(helm, ImageChops.multiply(face, poly(mask(), [(0, 690), (S, 690), (S, S), (0, S)])))
    img = paint(img, helm, canvas('#b0202c', '#4a0810'))
    img = rim(img, helm, (255, 170, 150), -10, -10, .9)
    trim = mask(); d = ImageDraw.Draw(trim)
    for side in (-1, 1):
        d.line([(c + side * 160, 700), (c + side * 175, 1060)], fill=255, width=16)
        d.line([(c + side * 160, 700), (c, 640)], fill=255, width=14)
    img = paint(img, trim, (130, 70, 180))
    crest = poly(mask(), [(c - 22, 390), (c + 22, 390), (c + 30, 650), (c - 30, 650)])
    img = paint(img, crest, (90, 10, 16))
    img = outline(img, helm, 14)
    # Metal circling him: plates, bars and rods turning in the air, catching the light.
    metal = mask(); rnd = random.Random(44)
    for i in range(22):
        a = i / 22 * math.tau + rnd.random() * .3
        r = 720 + rnd.random() * 220
        x, y = c + math.cos(a) * r, 1050 + math.sin(a) * r * .55
        ln, w, rot = 60 + rnd.random() * 160, 18 + rnd.random() * 30, rnd.random() * math.pi
        dx, dy, px, py = math.cos(rot) * ln, math.sin(rot) * ln, -math.sin(rot) * w, math.cos(rot) * w
        poly(metal, [(x - dx - px, y - dy - py), (x + dx - px, y + dy - py), (x + dx + px, y + dy + py), (x - dx + px, y - dy + py)])
    img = paint(img, metal, (70, 72, 80))
    img = rim(img, metal, (230, 232, 240), -6, -6, .9)
    img = outline(img, metal, 6)
    # Faint field lines.
    field = mask(); d = ImageDraw.Draw(field)
    for i in range(7):
        d.arc((c - 900 + i * 40, 500 + i * 30, c + 900 - i * 40, 1700 - i * 20), 200 + i * 6, 340 - i * 6, fill=120, width=4)
    img = glow(img, field, (200, 210, 255), 10, .5)
    img = misprint(img, helm, 10)
    img = vignette(img, .7)
    img = grain(img, 8)
    save(img, path)

if __name__ == '__main__':
    make('/tmp/claude-0/magneto.png')
