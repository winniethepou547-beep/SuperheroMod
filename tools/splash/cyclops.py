from common import *

def make(path):
    c = S / 2
    img = canvas('#0c1e52', '#02050f')
    img = radial_light(img, c, 880, 600, '#2c58c8', .7)
    img = rays(img, c, 880, (40, 90, 200), 26, .3, seed=51)
    img = halftone(img, (40, 80, 180), lambda x, y: .7 * (1 - y), step=30, strength=.3)
    img = frame_lines(img, (60, 110, 220), seed=52, count=8)
    body = poly(mask(), bust(620, 270, 140, 1300, 1090))
    img = paint(img, body, (16, 24, 52)); img = rim(img, body, (110, 160, 255), 14, 10, .9); img = outline(img, body, 14)
    # Yellow X straps across the chest.
    straps = mask(); d = ImageDraw.Draw(straps)
    d.line([(c - 520, 1350), (c + 300, S)], fill=255, width=70); d.line([(c + 520, 1350), (c - 300, S)], fill=255, width=70)
    img = paint(img, ImageChops.multiply(straps, body), (220, 180, 40))
    # Cowl and jaw.
    cowl = poly(mask(), [(c, 560), (c + 200, 640), (c + 230, 880), (c + 200, 1040), (c - 200, 1040), (c - 230, 880), (c - 200, 640)])
    img = paint(img, cowl, (22, 34, 76)); img = rim(img, cowl, (140, 180, 255), 12, 8, .9)
    jaw = poly(mask(), [(c - 170, 980), (c + 170, 980), (c + 130, 1120), (c, 1170), (c - 130, 1120)])
    img = paint(img, jaw, (60, 46, 44)); img = outline(img, ImageChops.lighter(cowl, jaw), 14)
    # The ruby visor and the beam.
    visor = poly(mask(), [(c - 250, 830), (c + 250, 830), (c + 230, 920), (c - 230, 920)])
    img = paint(img, visor, (120, 10, 20))
    beam_core = poly(mask(), [(c - 200, 860), (c + 200, 860), (c + 200, 890), (c - 200, 890)])
    img = glow(img, beam_core, (255, 30, 40), 70, 2.2); img = paint(img, beam_core, (255, 220, 210))
    # The beam fans out of both ends of the visor, to the right and to the left.
    beam = poly(mask(), [(c + 220, 850), (S + 50, 700), (S + 50, 1050), (c + 220, 900)])
    img = glow(img, beam, (255, 40, 40), 40, 1.3)
    beam_left = poly(mask(), [(c - 220, 850), (-50, 700), (-50, 1050), (c - 220, 900)])
    img = glow(img, beam_left, (255, 40, 40), 40, 1.3)
    img = outline(img, visor, 10)
    img = misprint(img, cowl, 12)
    img = vignette(img, .7); img = grain(img, 8)
    save(img, path)

if __name__ == '__main__':
    make('/tmp/claude-0/cyclops.png')
