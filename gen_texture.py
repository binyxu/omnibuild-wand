import struct, zlib, math, os

N = 32

# ── palette ─────────────────────────────────────────────────────────────────
OUT = (20, 16, 30, 255)
AQ = [(10, 78, 118, 255), (26, 140, 182, 255), (58, 196, 222, 255),
      (150, 232, 244, 255), (220, 250, 255, 255)]   # aqua gem dark->bright
WT = (255, 255, 255, 255)
LD = (150, 98, 16, 255)
LC = (210, 150, 28, 255)
LB = (248, 200, 70, 255)
LA = (255, 236, 150, 255)
GLOW = (150, 205, 255)

# ── wand axis: tip at top-left, pommel at bottom-right (45°) ─────────────────
TX, TY = 6.0, 6.0                       # tip anchor (top-left)
DUX, DUY = 1/math.sqrt(2), 1/math.sqrt(2)   # along axis (toward pommel)
DNX, DNY = -DUY, DUX                        # perpendicular

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

# swept wings (in along/|perp| space): gentle straight sweep from collar to a point
WING = [(12.5, 2.0), (8.5, 4.4), (4.2, 6.6)]

def gem_width(a):
    if a <= 5: return 3.0 * (a / 5.0)
    return 1.3 + (3.0 - 1.3) * (11.0 - a) / 6.0

def color_for(a, p):
    ap = abs(p)
    # 1) faceted aqua gem (head)
    if 0 <= a <= 11:
        w = gem_width(a)
        if ap <= w:
            if 3.0 <= a <= 4.2 and ap < 0.7:
                return WT, True
            rv = ap / max(w, 0.1)
            idx = 4 if rv < 0.3 else 3 if rv < 0.6 else 2 if rv < 0.85 else 1
            if a > 6.5: idx -= 1
            if a < 1.5: idx -= 1
            if 5.2 < a < 6.0 and ap > 0.4: idx -= 1     # girdle seam
            return AQ[max(0, min(4, idx))], True
    # 2) golden wings (behind gem) — taper to a point near the tip
    wing_hw = max(0.7, min(1.6, 0.7 + (a - 4.2) * (0.9 / 8.3)))
    if dist_poly(a, ap, WING) <= wing_hw:
        if a < 5.0:  return LA, False        # bright tip
        if a > 9.5:  return LC, False        # shaded near base
        return LB, False
    # 3) ornate collar / guard under the gem
    if 10.5 <= a <= 14.0:
        gw = 3.6 - (a - 10.5) * 0.55
        if ap <= gw:
            return (LA if ap < 0.9 else LB if ap < 2.0 else LC), False
    # 4) decorative mid band
    if 18.5 <= a <= 20.6 and ap <= 2.2:
        return (LA if ap < 0.9 else LB if ap < 1.6 else LC), False
    # 5) slim shaft (cylinder shading)
    if 13.5 <= a <= 26.5 and ap <= 1.4:
        return (LA if ap < 0.7 else LC), False
    # 6) pommel knob
    if math.hypot(a - 28.2, p) <= 2.6:
        d = math.hypot(a - 28.2, p)
        return (LA if d < 1.0 else LB if d < 1.9 else LC), False
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

# ── light-blue glow (strong around gem, faint halo overall) ─────────────────
gcx = TX + DUX * 4
gcy = TY + DUY * 4
for y in range(N):
    for x in range(N):
        if buf[y][x][3] != 0:
            continue
        near = min((math.hypot(x - gx, y - gy) for gx, gy in gem_cells), default=99)
        a = 0
        if near <= 2.4:    a = 140
        elif near <= 3.8:  a = 80
        elif math.hypot(x - gcx, y - gcy) <= 11:  a = 30
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

SC = 9
bigN = N * SC
big = [[None] * bigN for _ in range(bigN)]
for y in range(N):
    for x in range(N):
        px = buf[y][x]
        if px[3] == 0:
            px = [200, 200, 200, 255] if (x + y) % 2 == 0 else [170, 170, 170, 255]
        elif px[3] < 255:
            bg = (200, 200, 200) if (x + y) % 2 == 0 else (170, 170, 170)
            al = px[3] / 255
            px = [int(px[i] * al + bg[i] * (1 - al)) for i in range(3)] + [255]
        for yy in range(SC):
            for xx in range(SC):
                big[y * SC + yy][x * SC + xx] = px
with open(r'C:\Users\20129\preview_wand.png', 'wb') as f:
    f.write(make_png_sz(big, bigN))

# crisp transparent icon for Modrinth (nearest-neighbour upscale, keeps alpha/glow)
ISC = 8
iN = N * ISC
icon = [[buf[y // ISC][x // ISC] for x in range(iN)] for y in range(iN)]
with open(r'C:\Users\20129\icon_wand.png', 'wb') as f:
    f.write(make_png_sz(icon, iN))
print('Texture + preview + icon written OK')
