from common import *

def make(path):
    c = S / 2
    img = canvas('#d39a48', '#3a220e')
    img = radial_light(img, c, 700, 700, '#ffd890', .6)
    # Dunes and a storm swirl.
    dunes = mask(); d = ImageDraw.Draw(dunes)
    d.ellipse((-600, 1550, 1300, 2600), fill=200); d.ellipse((900, 1650, 2800, 2700), fill=160)
    img = paint(img, dunes.filter(ImageFilter.GaussianBlur(20)), (120, 72, 30), .8)
    swirl = mask(); d = ImageDraw.Draw(swirl)
    for k in range(10): d.arc((200 - k * 60, 200 - k * 40, 1900 + k * 60, 1500 + k * 40), 190 + k * 8, 330 - k * 6, fill=255, width=10)
    img = paint(img, swirl.filter(ImageFilter.GaussianBlur(6)), (240, 200, 130), .6)
    img = halftone(img, (110, 60, 20), lambda x, y: .6 * y, step=30, strength=.35)
    body = poly(mask(), bust(600, 250, 140, 1300, 1090))
    # Green and black striped shirt.
    stripes = Image.new('RGB', (S, S), (20, 24, 18)); d = ImageDraw.Draw(stripes)
    for y in range(1050, S, 120): d.rectangle((0, y, S, y + 60), fill=(40, 110, 48))
    img = paint(img, body, stripes); img = rim(img, body, (255, 220, 150), 14, 10, .8); img = outline(img, body, 14)
    head = poly(mask(), [(c - 170, 680), (c, 620), (c + 170, 680), (c + 200, 900), (c + 150, 1080), (c, 1130), (c - 150, 1080), (c - 200, 900)])
    img = paint(img, head, (150, 112, 82)); img = rim(img, head, (255, 220, 160), 12, 8, .8)
    hair = poly(mask(), [(c - 175, 700), (c, 610), (c + 175, 700), (c + 160, 760), (c - 160, 760)])
    img = paint(img, hair, (60, 38, 20)); img = outline(img, head, 12)
    eyes = mask()
    for side in (-1, 1): poly(eyes, [(c + side * 30, 880), (c + side * 130, 860), (c + side * 125, 890), (c + side * 35, 900)])
    img = paint(img, eyes, (20, 14, 10))
    # The right side of him coming apart into sand.
    r = random.Random(61); grains = mask(); d = ImageDraw.Draw(grains)
    for i in range(1400):
        a = r.uniform(-.9, .6); rr = r.uniform(250, 1100)
        x, y = c + 300 + math.cos(a) * rr * .9, 1100 + math.sin(a) * rr * .6
        s = r.uniform(3, 12); d.ellipse((x - s, y - s, x + s, y + s), fill=int(r.uniform(120, 255)))
    erode = mask(); poly(erode, [(c + 160, 600), (S, 400), (S, S), (c + 420, S), (c + 250, 1300)])
    erode = erode.filter(ImageFilter.GaussianBlur(80))
    sandy = Image.new('RGB', (S, S), (214, 170, 100))
    img = paint(img, ImageChops.multiply(erode, ImageChops.lighter(body, head)), sandy, .85)
    img = paint(img, grains, (230, 190, 120))
    img = misprint(img, head, 12)
    img = vignette(img, .6); img = grain(img, 10)
    save(img, path)

if __name__ == '__main__':
    make('/tmp/claude-0/sandman.png')
