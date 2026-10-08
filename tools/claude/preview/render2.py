"""Preview renderer with textures: every textured face is cut into texel cells, painter's order; gloss added per face."""
import sys, os, numpy as np
from PIL import Image, ImageDraw
import pathlib
TEXDIR = str(pathlib.Path(__file__).resolve().parents[3] / 'src/main/resources/assets/superheromod') + '/'
TEX = {}
def tex(name):
    if name not in TEX: TEX[name] = np.asarray(Image.open(TEXDIR + name).convert('RGBA'), float) / 255
    return TEX[name]
groups = {}; cur = None
for line in open(sys.argv[1]):
    if line.startswith('#'): cur = line[1:].strip(); groups[cur] = []; continue
    p = line.split()
    groups[cur].append((p[0], p[1], list(map(float, p[2:]))))
W, H, F = int(os.environ.get("RW", 420)), int(os.environ.get("RH", 560)), float(os.environ.get("RF", 900))
L0 = np.array([.2, 1, -.7]); L0 /= np.linalg.norm(L0)
L1 = np.array([-.2, 1, .7]); L1 /= np.linalg.norm(L1)
def bg():
    a = np.zeros((H, W, 3))
    if os.environ.get("KEY"): a[:, :, :] = np.array([1, 0, 1]); return a
    for y in range(H): a[y, :, :] = np.array([.62, .76, .92]) * (1 - y / H * .25)
    if not os.environ.get("NOFLOOR"): a[int(H * .78):, :, :] = np.array([.9, .93, .96])
    return a
def proj(v): z = -v[2]; return (W / 2 + v[0] / z * F, H * .52 - v[1] / z * F, z)
def render(name, verts):
    img = bg()
    items = []
    quads = [verts[i:i + 4] for i in range(0, len(verts) - 3, 4)]
    last_face = None
    for q in quads:
        kind, tname = q[0][0], q[0][1]
        vs = [np.array(v[2]) for v in q]
        P = [proj(v) for v in vs]
        if min(p[2] for p in P) <= .05: continue
        z = np.mean([p[2] for p in P])
        if kind == 'G':
            col = np.mean([v[3:6] for v in vs], axis=0)
            items.append((z - float(os.environ.get('ADDBIAS', 1e-4)), 'add', [(p[0], p[1]) for p in P], col)); continue
        n = vs[0][7:10]; nl = np.linalg.norm(n); n = n / nl if nl > 0 else n
        lit = min(1, .4 + .6 * (max(0, n.dot(L0)) + max(0, n.dot(L1))))
        a = np.mean([v[6] for v in vs])
        if a < .1: continue
        t = tex(tname)
        th, tw = t.shape[:2]
        uv = np.array([[v[10], v[11]] for v in vs])
        du = max(np.linalg.norm((uv[1] - uv[0]) * [tw, th]), np.linalg.norm((uv[2] - uv[3]) * [tw, th]))
        dv = max(np.linalg.norm((uv[3] - uv[0]) * [tw, th]), np.linalg.norm((uv[2] - uv[1]) * [tw, th]))
        nu, nv = int(min(24, max(1, np.ceil(du - 1e-3)))), int(min(24, max(1, np.ceil(dv - 1e-3))))
        cols = [v[3:6] for v in vs]
        def bil(s, r, arr): return (arr[0] * (1 - s) + arr[1] * s) * (1 - r) + (arr[3] * (1 - s) + arr[2] * s) * r
        for i in range(nu):
            for j in range(nv):
                s0, s1, r0, r1 = i / nu, (i + 1) / nu, j / nv, (j + 1) / nv
                cu, cv = bil((s0 + s1) / 2, (r0 + r1) / 2, uv)
                tx = t[int(np.floor(cv * th)) % th, int(np.floor(cu * tw)) % tw]
                if tx[3] < .1: continue
                corners = [bil(s0, r0, P := np.array([p[:2] for p in [proj(v) for v in vs]])), bil(s1, r0, P), bil(s1, r1, P), bil(s0, r1, P)]
                vc = bil((s0 + s1) / 2, (r0 + r1) / 2, np.array(cols))
                items.append((z, 'fill', [tuple(c) for c in corners], tx[:3] * vc * lit))
    items.sort(key=lambda t: -t[0])
    im = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))
    d = ImageDraw.Draw(im)
    for z, kind, pts, col in items:
        if kind == 'fill':
            d.polygon(pts, fill=tuple((np.clip(col, 0, 1) * 255).astype(int)))
        else:
            xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
            x0, x1, y0, y1 = int(max(0, min(xs))), int(min(W - 1, max(xs))) + 1, int(max(0, min(ys))), int(min(H - 1, max(ys))) + 1
            if x1 <= x0 or y1 <= y0: continue
            m = Image.new('L', (x1 - x0, y1 - y0), 0)
            ImageDraw.Draw(m).polygon([(p[0] - x0, p[1] - y0) for p in pts], fill=255)
            mk = np.asarray(m, float)[..., None] / 255
            arr = np.asarray(im, float) / 255
            arr[y0:y1, x0:x1] = np.minimum(1, arr[y0:y1, x0:x1] + col * mk)
            im = Image.fromarray((arr * 255).astype(np.uint8)); d = ImageDraw.Draw(im)
    if not os.environ.get("KEY"): ImageDraw.Draw(im).text((6, 6), name, fill=(0, 0, 0))
    return im
names = sys.argv[3].split(',') if len(sys.argv) > 3 else list(groups)
ims = [render(n, groups[n]) for n in names]
cols = min(4, len(ims)); rows = (len(ims) + cols - 1) // cols
sheet = Image.new('RGB', (W * cols, H * rows))
for i, im in enumerate(ims): sheet.paste(im, ((i % cols) * W, (i // cols) * H))
sheet.save(sys.argv[2])
