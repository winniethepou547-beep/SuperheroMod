"""The plain bat symbol for Batman's HUD (the Batarang count): a white classic bat on transparent, drawn large and
scaled down for clean edges. Writes assets/superheromod/textures/gui/batman/bat.png (64x32)."""
from PIL import Image, ImageDraw
import os

W, H, S = 64, 32, 8
half = [(0.0, -0.14), (0.04, -0.16), (0.09, -0.46), (0.14, -0.16), (0.20, -0.22), (0.42, -0.34), (0.66, -0.42), (0.86, -0.40),
        (1.0, -0.30), (0.90, -0.10), (0.84, 0.10), (0.72, 0.00), (0.60, 0.04), (0.52, 0.26), (0.40, 0.12),
        (0.28, 0.14), (0.16, 0.30), (0.08, 0.36), (0.0, 0.50)]
pts = half + [(-x, y) for (x, y) in reversed(half[1:-1])]
img = Image.new('RGBA', (W * S, H * S), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
cx, cy, sx, sy = W * S / 2, H * S / 2 + 0.5 * S, W * S * 0.47, H * S * 0.92
d.polygon([(cx + x * sx, cy + y * sy) for x, y in pts], fill=(255, 255, 255, 255))
img = img.resize((W, H), Image.LANCZOS)
out = os.path.join(os.path.dirname(__file__), '..', '..', 'src/main/resources/assets/superheromod/textures/gui/batman/bat.png')
os.makedirs(os.path.dirname(out), exist_ok=True)
img.save(out)
print('wrote', os.path.normpath(out))
