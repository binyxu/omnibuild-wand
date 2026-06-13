import struct, zlib, math, os

N = 16  # vanilla item resolution, matches sword/axe style

# ── palette ─────────────────────────────────────────────────────────────────
OUT = (20, 16, 30, 255)
AQ = [(14, 86, 124, 255), (34, 150, 190, 255), (70, 205, 230, 255),
      (160, 236, 248, 255), (225, 252, 255, 255)]   # aqua gem dark->bright
WT = (255, 255, 255, 255)
LD = (150, 98, 16, 255)
LC = (210, 150, 28, 255)
LB = (248, 200, 70, 255)
LA = (255, 236, 150, 255)
GLOW = (150, 205, 255)

# wand axis: tip top-right, pommel bottom-left (45°)
TX, TY = 12.0, 3.0
DUX, DUY = -1/math.sqrt(2), 1/math.sqrt(2)   # along axis (toward pommel, down-left)
DNX, DNY = -DUY, DUX

buf   = [[[0, 0, 0, 0] for _ in range(N)] for _ in range(N)]
solid = [[False] * N for _ in range(N)]
gem_cells = []

def dist_seg(px, py, ax, ay, bx, by):
    vx, vy = bx - ax, by - ay
    wx, wy = px - ax, py - ay
    L2 = vx * vx + vy * vy
    t = 0 if L2 == 0 else max(0.0, min(1.0, (wx * vx + wy * vy) / L2))
    return math.hypot(px - (ax + t * vx), py - (ay + t * vy))

def dist_poly(px, py, pts):
    return min(dist_seg(px, py, pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1])
               for i in range(len(pts) - 1))

WING = [(6.2, 1.0), (4.2, 2.3), (2.3, 3.1)]   # small swept wings

def gem_width(a):
    if a <= 2.6: return 1.75 * (a / 2.6)
    return 0.7 + (1.75 - 0.7) * (5.5 - a) / 2.9

def color_for(a, p):
    ap = abs(p)
    # 1) faceted aqua gem head
    if 0 <= a <= 5.5:
        w = gem_width(a)
        if ap <= w:
            if 1.7 <= a <= 2.5 and ap < 0.55:
                return WT, True
            rv = ap / max(w, 0.1)
            idx = 4 if rv < 0.34 else 3 if rv < 0.68 else 2
            if a > 3.4: idx -= 1
            if a < 0.9: idx -= 1
            return AQ[max(0, min(4, idx))], True
    # 2) small golden wings, tapering to a point
    whw = max(0.4, min(0.95, 0.4 + (a - 2.3) * (0.55 / 3.9)))
    if dist_poly(a, ap, WING) <= whw:
        if a < 2.8:  return LA, False
        if a > 5.0:  return LC, False
        return LB, False
    # 3) collar under the gem
    if 5.0 <= a <= 7.0 and ap <= 1.85 - (a - 5.0) * 0.45:
        return (LA if ap < 0.6 else LB if ap < 1.2 else LC), False
    # 4) small decorative band
    if 9.3 <= a <= 10.2 and ap <= 1.35:
        return (LA if ap < 0.6 else LB if ap < 1.0 else LC), False
    # 5) slim shaft
    if 6.7 <= a <= 12.8 and ap <= 0.95:
        return (LA if ap < 0.45 else LC), False
    # 6) pommel
    if math.hypot(a - 13.5, p) <= 1.45:
        d = math.hypot(a - 13.5, p)
        return (LA if d < 0.6 else LB if d < 1.05 else LC), False
    return None

for y in range(N):
    for x in range(N):
        a = (x - TX) * DUX + (y - TY) * DUY
        p = (x - TX) * DNX + (y - TY) * DNY
        res = color_for(a, p)
        if res is not None:
            col, isgem = res
            buf[y][x] = list(col)
            solid[y][x] = True
            if isgem:
                gem_cells.append((x, y))

# ── dark outline (8-neighbour) ──────────────────────────────────────────────
outline = []
for y in range(N):
    for x in range(N):
        if solid[y][x]:
            continue
        if any(0 <= x + ox < N and 0 <= y + oy < N and solid[y + oy][x + ox]
               for ox in (-1, 0, 1) for oy in (-1, 0, 1)):
            outline.append((x, y))
for x, y in outline:
    buf[y][x] = list(OUT)

# ── subtle light-blue glow near the gem ─────────────────────────────────────
gcx, gcy = TX + DUX * 2.2, TY + DUY * 2.2
for y in range(N):
    for x in range(N):
        if buf[y][x][3] != 0:
            continue
        near = min((math.hypot(x - gx, y - gy) for gx, gy in gem_cells), default=99)
        a = 0
        if near <= 1.3:   a = 120
        elif near <= 2.1: a = 55
        if a:
            buf[y][x] = [GLOW[0], GLOW[1], GLOW[2], a]

# ── PNG writer + outputs ────────────────────────────────────────────────────
def make_png_sz(pix, n):
    def chunk(tag, data):
        c = tag + data
        return struct.pack('>I', len(data)) + c + struct.pack('>I', zlib.crc32(c) & 0xffffffff)
    raw = b''.join(b'\x00' + bytes(sum((pix[y][x] for x in range(n)), [])) for y in range(n))
    return (b'\x89PNG\r\n\x1a\n'
            + chunk(b'IHDR', struct.pack('>IIBBBBB', n, n, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(raw, 9))
            + chunk(b'IEND', b''))

out = r'C:\Users\20129\BuildingWandMod\src\main\resources\assets\buildingwand\textures\item\building_wand.png'
os.makedirs(os.path.dirname(out), exist_ok=True)
with open(out, 'wb') as f:
    f.write(make_png_sz(buf, N))

# preview (checkerboard) + transparent icon
def upscale(scale, checker):
    bn = N * scale
    g = [[None] * bn for _ in range(bn)]
    for y in range(N):
        for x in range(N):
            px = buf[y][x]
            if checker and px[3] == 0:
                px = [200, 200, 200, 255] if (x + y) % 2 == 0 else [170, 170, 170, 255]
            elif checker and px[3] < 255:
                bg = (200, 200, 200) if (x + y) % 2 == 0 else (170, 170, 170)
                al = px[3] / 255
                px = [int(px[i] * al + bg[i] * (1 - al)) for i in range(3)] + [255]
            for yy in range(scale):
                for xx in range(scale):
                    g[y * scale + yy][x * scale + xx] = px
    return g, bn
g, bn = upscale(16, True)
with open(r'C:\Users\20129\preview_wand.png', 'wb') as f:
    f.write(make_png_sz(g, bn))
g, bn = upscale(16, False)
with open(r'C:\Users\20129\icon_wand.png', 'wb') as f:
    f.write(make_png_sz(g, bn))
print('16x16 texture + preview + icon written OK')
