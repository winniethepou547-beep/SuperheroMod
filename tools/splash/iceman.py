from common import *
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ICE_TILE = os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'superheromod', 'textures', 'entity', 'iceman', 'ice.png')


def make(path):
    """Iceman as the game draws him (Minecraft blocks: iceman_model.png is the in-game model rendered offline from
    IcemanBody with its skin) on a bright snowy day: blocky ice hills, a beam of ice blocks across the bottom, snow."""
    c = S / 2
    img = canvas('#2c6cb6', '#bfe2ff')
    img = radial_light(img, c + 520, 420, 700, '#fff6d8', .45)
    # Blocky hills of packed ice and snow far off, stepped like Minecraft terrain.
    hills = mask(); rnd = random.Random(81)
    x, h = 0, 760
    while x < S:
        h = max(420, min(980, h + rnd.choice([-128, -64, 0, 64, 128])))
        poly(hills, [(x, S), (x + 128, S), (x + 128, S - h), (x, S - h)])
        x += 128
    img = paint(img, hills, canvas('#9cc8f0', '#5c94d0'), .9)
    caps = mask(); d = ImageDraw.Draw(caps); x, h = 0, 760; rnd = random.Random(81)
    while x < S:
        h = max(420, min(980, h + rnd.choice([-128, -64, 0, 64, 128])))
        d.rectangle([x, S - h, x + 127, S - h + 40], fill=255)
        x += 128
    img = paint(img, caps, (240, 248, 255), .95)
    # The beam of ice blocks he rides, across the bottom on the slant, in the game's own ice texture.
    tile = Image.open(ICE_TILE).convert('RGB').resize((256, 256), Image.NEAREST)
    tex = Image.new('RGB', (S, S))
    for ty in range(0, S, 256):
        for tx in range(0, S, 256): tex.paste(tile, (tx, ty))
    beam = poly(mask(), [(-40, 1800), (S + 40, 1380), (S + 40, 1620), (-40, 2048)])
    img = paint(img, beam, tex, 1)
    top = poly(mask(), [(-40, 1800), (S + 40, 1380), (S + 40, 1420), (-40, 1840)])
    img = paint(img, top, (225, 244, 255), .8)
    img = outline(img, beam, 10, (10, 40, 80))
    # Ice cubes breaking off the beam.
    cubes = mask(); rnd = random.Random(82)
    for i in range(7):
        cx, cy, r = 160 + rnd.random() * 1700, 1300 + rnd.random() * 500, 34 + rnd.random() * 40
        a = rnd.random() * math.pi
        poly(cubes, [(cx + r * math.cos(a + k * math.pi / 2), cy + r * math.sin(a + k * math.pi / 2)) for k in range(4)])
    img = paint(img, cubes, tex, 1)
    img = rim(img, cubes, (235, 250, 255), -6, -6, .9)
    img = outline(img, cubes, 6, (10, 40, 80))
    # Him.
    model = Image.open(os.path.join(HERE, 'iceman_model.png')).convert('RGBA')
    size = 1900
    model = model.resize((size, size), Image.NEAREST)
    layer = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    layer.paste(model, (int(c - size / 2), S - size + 60), model)
    body = layer.split()[3]
    img = glow(img, body, (170, 225, 255), 40, .5)
    img.paste(layer.convert('RGB'), (0, 0), body)
    img = outline(img, body, 12, (8, 26, 52))
    # Snow: square flakes.
    snow = mask(); d = ImageDraw.Draw(snow); rnd = random.Random(76)
    for i in range(380):
        x, y, r = rnd.random() * S, rnd.random() * S, 3 + rnd.random() * 7
        d.rectangle([x - r, y - r, x + r, y + r], fill=220)
    img = paint(img, snow, (245, 250, 255), .85)
    img = vignette(img, .45)
    img = grain(img, 5)
    save(img, path)

if __name__ == '__main__':
    make('/tmp/claude-0/iceman.png')
