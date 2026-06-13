import struct, zlib, os

def make_png(pixels):
    def chunk(tag, data):
        c = tag + data
        return struct.pack('>I', len(data)) + c + struct.pack('>I', zlib.crc32(c) & 0xffffffff)
    raw = b''.join(
        b'\x00' + bytes(sum(([r, g, b, a] for r, g, b, a in row_pixels), []))
        for row_pixels in pixels
    )
    return (b'\x89PNG\r\n\x1a\n'
            + chunk(b'IHDR', struct.pack('>IIBBBBB', 16, 16, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(raw, 9))
            + chunk(b'IEND', b''))

T  = (  0,   0,   0,   0)  # transparent
DK = ( 15,   5,   2, 255)  # dark outline
SH = (230, 240, 255, 255)  # gem specular highlight
SL = (170, 200, 255, 255)  # gem light blue
SM = ( 90, 140, 240, 255)  # gem mid blue
SD = ( 40,  75, 180, 255)  # gem dark blue
GS = (200, 190,  80, 255)  # gem-shaft junction (greenish gold)
GL = (255, 215,  55, 255)  # gold light
GM = (210, 160,  15, 255)  # gold mid
GD = (140, 100,   5, 255)  # gold dark
HL = (190, 115,  50, 255)  # handle light
HM = (130,  65,  18, 255)  # handle mid
HD = ( 72,  28,   4, 255)  # handle dark

def row(cols):
    r = [T] * 16
    for c, v in cols.items():
        r[c] = v
    return r

pixels = [
    # Gem cluster at top-left (rows 0-3)
    row({1: SL, 2: SH, 3: SL}),
    row({0: SD, 1: SH, 2: SM, 3: SL, 4: DK}),
    row({1: SD, 2: SM, 3: DK, 4: GL, 5: GD}),
    row({2: DK, 3: GL, 4: GM, 5: GD}),
    # Gold shaft (rows 4-9)
    row({3: DK, 4: GL, 5: GM, 6: GD}),
    row({4: DK, 5: GL, 6: GM, 7: GD}),
    row({5: DK, 6: GL, 7: GM, 8: GD}),
    row({6: DK, 7: GL, 8: GM, 9: GD}),
    row({7: DK, 8: GL, 9: GM, 10: GD}),
    row({8: DK, 9: GL, 10: GM, 11: GD}),
    # Shaft-handle transition (row 10)
    row({9: DK, 10: HL, 11: HM, 12: DK}),
    # Handle (rows 11-15)
    row({10: DK, 11: HL, 12: HM, 13: HD}),
    row({11: DK, 12: HL, 13: HM, 14: HD}),
    row({12: DK, 13: HM, 14: HM, 15: HD}),
    row({13: DK, 14: HM, 15: HD}),
    row({14: DK, 15: HM}),
]

out = r'C:\Users\20129\BuildingWandMod\src\main\resources\assets\buildingwand\textures\item\building_wand.png'
os.makedirs(os.path.dirname(out), exist_ok=True)
with open(out, 'wb') as f:
    f.write(make_png(pixels))
print('Texture written OK')
