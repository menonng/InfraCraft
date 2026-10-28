"""InfraCraft 텍스처 생성기 (16x16 바닐라 스타일 + Create 풍 케이싱).

사용법: python3 tools/gen_textures.py   → common/resources/assets/infracraft/textures 에 PNG 를 쓴다.
"""
import os
import random
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "common", "resources", "assets", "infracraft")
TEX = os.path.join(ROOT, "textures")

def hx(c, a=255):
    c = c.lstrip("#")
    return (int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16), a)

def shade(c, d):
    return tuple(max(0, min(255, v + d)) for v in c[:3]) + (c[3],)

def new(fill=None):
    return Image.new("RGBA", (16, 16), hx(fill) if fill else (0, 0, 0, 0))

def noise(img, base, spread, seed, x0=0, y0=0, x1=16, y1=16):
    r = random.Random(seed)
    b = hx(base)
    for y in range(y0, y1):
        for x in range(x0, x1):
            img.putpixel((x, y), shade(b, r.randint(-spread, spread)))

def rect(img, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            img.putpixel((x, y), hx(c) if isinstance(c, str) else c)

def px(img, pts, c):
    for x, y in pts:
        img.putpixel((x, y), hx(c) if isinstance(c, str) else c)

# ── 금속 팔레트(밝음 → 어두움) ──
BRASS = ["#FBE7A1", "#F0C860", "#D9A441", "#B07A2A", "#6E4714"]
ANDESITE = ["#D2D5CF", "#B3B7B0", "#959A93", "#6E726C", "#454843"]
COPPER = ["#F4B08A", "#E0855A", "#C0623B", "#8C432A", "#4F2617"]
IRON = ["#FFFFFF", "#D8D8D8", "#A8A8A8", "#727272", "#3E3E3E"]
SPRUCE = ["#8A6A43", "#735534", "#5E4429", "#47331F"]
GOLD = ["#FFF6A8", "#FCDB4A", "#E2B01F", "#A87A0E", "#5E4106"]

def casing(img, metal, seed=1):
    """Create 모드 케이싱처럼 2px 금속 테두리(빛은 왼쪽 위)."""
    r = random.Random(seed)
    for i in range(16):
        for (x, y) in [(i, 0), (0, i), (i, 15), (15, i)]:
            img.putpixel((x, y), hx(metal[4]))
    for i in range(1, 15):
        img.putpixel((i, 1), hx(metal[0] if r.random() < 0.7 else metal[1]))
        img.putpixel((1, i), hx(metal[0] if r.random() < 0.7 else metal[1]))
        img.putpixel((i, 14), hx(metal[3]))
        img.putpixel((14, i), hx(metal[3]))
    for i in range(2, 14):
        img.putpixel((i, 2), hx(metal[2]))
        img.putpixel((2, i), hx(metal[2]))
        img.putpixel((i, 13), hx(metal[3]))
        img.putpixel((13, i), hx(metal[3]))
    img.putpixel((1, 14), hx(metal[2])); img.putpixel((14, 1), hx(metal[2]))
    # 모서리 리벳
    for (x, y) in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        img.putpixel((x, y), hx(metal[0]))

def planks(img, pal, seed, x0=3, y0=3, x1=12, y1=12, horizontal=True):
    r = random.Random(seed)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            band = (y - y0) if horizontal else (x - x0)
            c = hx(pal[1])
            if band % 4 == 3:
                c = hx(pal[3])
            elif r.random() < 0.18:
                c = hx(pal[2])
            elif r.random() < 0.12:
                c = hx(pal[0])
            img.putpixel((x, y), c)

def save(img, path):
    full = os.path.join(TEX, path + ".png")
    os.makedirs(os.path.dirname(full), exist_ok=True)
    img.save(full)

# ═══════════════════ 블록 ═══════════════════

def asphalt(seed=11):
    img = new()
    noise(img, "#3A3A3F", 5, seed)
    r = random.Random(seed + 1)
    for _ in range(22):
        x, y = r.randrange(16), r.randrange(16)
        img.putpixel((x, y), hx(r.choice(["#55555C", "#2C2C30", "#4A4A50", "#64646C"])))
    return img

save(asphalt(), "block/asphalt")

img = asphalt(13)
for y in list(range(1, 7)) + list(range(9, 15)):
    for x in (7, 8):
        img.putpixel((x, y), shade(hx("#E9E6D6"), random.Random(x * 31 + y).randint(-14, 4)))
save(img, "block/road_line")

# 가로등: 황동 케이싱 + 따뜻한 유리
img = new("#000000")
casing(img, BRASS, 3)
for y in range(3, 13):
    for x in range(3, 13):
        d = abs(x - 7.5) + abs(y - 7.5)
        c = "#FFF4C2" if d < 3 else "#FFE07A" if d < 6 else "#F2B33D"
        img.putpixel((x, y), hx(c))
for i in range(3, 13):
    img.putpixel((i, 7), hx(BRASS[2])); img.putpixel((7, i), hx(BRASS[2]))
    img.putpixel((i, 8), hx(BRASS[3])); img.putpixel((8, i), hx(BRASS[3]))
save(img, "block/street_lamp")

# 이정표: 안산암 합금 테두리 + 가문비 판자 + 흰 화살표
img = new("#000000")
casing(img, ANDESITE, 5)
planks(img, SPRUCE, 6)
arrow = [(4, 7), (5, 7), (6, 7), (7, 7), (8, 7), (9, 7), (4, 8), (5, 8), (6, 8), (7, 8), (8, 8), (9, 8),
         (9, 5), (10, 6), (11, 7), (11, 8), (10, 9), (9, 10), (9, 6), (10, 7), (10, 8), (9, 9)]
px(img, arrow, "#F4F1E6")
px(img, [(x, 9) for x in range(4, 9)], "#BDB8A6")
save(img, "block/signpost")

# 역: 황동 케이싱 + 어두운 안내판(열차 그림)
img = new("#000000")
casing(img, BRASS, 7)
rect(img, 3, 3, 12, 12, "#1C262B")
rect(img, 3, 3, 12, 3, "#2B3A41")
rect(img, 5, 5, 10, 9, "#39C5BB")      # 열차 몸통
rect(img, 6, 6, 9, 7, "#BFF5EF")       # 창문
px(img, [(5, 10), (10, 10)], "#E9F4F2")  # 바퀴
px(img, [(x, 11) for x in range(4, 12)], "#6E726C")  # 선로
save(img, "block/station")
img2 = new("#000000"); casing(img2, BRASS, 8); planks(img2, ["#5E4A3A", "#4A3A2D", "#3B2E24", "#2A2019"], 9)
save(img2, "block/station_side")

# 분기기: 안산암 케이싱 + 레버 홈 + 표시등
def switch_tex(on):
    img = new("#000000")
    casing(img, ANDESITE, 10)
    noise(img, "#5E625C", 6, 12, 3, 3, 13, 13)
    rect(img, 7, 4, 8, 11, "#2E302D")            # 레버 홈
    rect(img, 6, 5 if on else 9, 9, 6 if on else 10, BRASS[1])  # 레버 손잡이
    lamp = "#FF4A3A" if on else "#5A1A1A"
    rect(img, 4, 11, 5, 12, lamp)
    rect(img, 10, 11, 11, 12, lamp)
    if on:
        px(img, [(4, 11), (10, 11)], "#FFC2B8")
    return img
save(switch_tex(False), "block/rail_switch")
save(switch_tex(True), "block/rail_switch_on")

# 물살 블록: 구리 케이싱 + 프리즈머린 + 화살표(위 = 북쪽)
def prismarine_inner(img, seed):
    noise(img, "#5FA898", 10, seed, 3, 3, 13, 13)
    r = random.Random(seed)
    for _ in range(8):
        img.putpixel((r.randint(3, 12), r.randint(3, 12)), hx(r.choice(["#7FC7B6", "#3F7F72", "#9ADBC9"])))

img = new("#000000"); casing(img, COPPER, 14); prismarine_inner(img, 15)
chev = [(7, 4), (8, 4), (6, 5), (9, 5), (5, 6), (10, 6), (7, 5), (8, 5)]
px(img, chev, "#F2FFFB")
px(img, [(x, y) for y in range(6, 12) for x in (7, 8)], "#E3FAF4")
save(img, "block/water_current_top")
img = new("#000000"); casing(img, COPPER, 16); prismarine_inner(img, 17)
save(img, "block/water_current_side")

# 보트 리프트(갑문): 구리 케이싱 + 위쪽 이중 화살표
img = new("#000000"); casing(img, COPPER, 18); prismarine_inner(img, 19)
for oy in (4, 8):
    px(img, [(7, oy), (8, oy), (6, oy + 1), (9, oy + 1), (5, oy + 2), (10, oy + 2)], "#F2FFFB")
save(img, "block/boat_lift_top")
img = new("#000000"); casing(img, COPPER, 20); prismarine_inner(img, 21)
px(img, [(x, y) for y in range(4, 12) for x in (5, 10)], "#3F7F72")
px(img, [(5, 4), (10, 4), (4, 5), (6, 5), (9, 5), (11, 5)], "#E3FAF4")
save(img, "block/boat_lift_side")

# 마구간: 위는 건초, 옆은 가문비 판자 + 편자 문장
img = new("#000000"); casing(img, SPRUCE + ["#2E2014"], 22)
r = random.Random(23)
for y in range(3, 13):
    for x in range(3, 13):
        img.putpixel((x, y), hx(r.choice(["#D9B44A", "#C79F33", "#E8C860", "#B08A2A"])))
save(img, "block/stable_top")
img = new("#000000"); casing(img, SPRUCE + ["#2E2014"], 24); planks(img, SPRUCE, 25, horizontal=False)
shoe = [(5, 5), (5, 6), (5, 7), (5, 8), (6, 9), (7, 10), (8, 10), (9, 9), (10, 8), (10, 7), (10, 6), (10, 5)]
px(img, shoe, IRON[1]); px(img, [(5, 9), (6, 10), (9, 10), (10, 9)], IRON[3])
save(img, "block/stable_side")

# ── 선로(투명 배경) ──
def rail(tie, tie_dark, rail_hi, rail_lo, line_off, line_on, on, plate=None):
    img = new()
    for ty in (1, 5, 9, 13):
        for x in range(1, 15):
            img.putpixel((x, ty), hx(tie)); img.putpixel((x, ty + 1), hx(tie_dark))
        img.putpixel((1, ty), hx(tie_dark)); img.putpixel((14, ty + 1), hx(tie_dark))
    for y in range(16):
        for x0 in (2, 12):
            img.putpixel((x0, y), hx(rail_hi)); img.putpixel((x0 + 1, y), hx(rail_lo))
    if plate:
        for ty in (1, 5, 9, 13):
            for x0 in (2, 12):
                img.putpixel((x0 - 1 if x0 == 2 else x0 + 2, ty), hx(plate))
    line = line_on if on else line_off
    for y in range(16):
        for x in (7, 8):
            if y % 4 != 0 or on:
                img.putpixel((x, y), hx(line))
    if on:
        for y in range(0, 16, 4):
            img.putpixel((7, y), shade(hx(line), 60))
    return img

for on in (False, True):
    sfx = "_on" if on else ""
    save(rail("#7A7E78", "#4E524C", "#E4E6EA", "#8E9198", "#4A1010", "#FF2A1A", on, COPPER[1]), "block/high_speed_rail" + sfx)
    save(rail("#5E4A3A", "#3B2E24", BRASS[0], BRASS[3], "#1F4A47", "#4FF2DE", on, BRASS[2]), "block/express_rail" + sfx)
    save(rail("#3A2E33", "#211A1E", GOLD[1], GOLD[3], "#5A2A0A", "#FFA020", on, "#C44A12"), "block/blaze_rail" + sfx)

# ═══════════════════ 아이템 ═══════════════════

# 승차권
img = new()
rect(img, 1, 4, 14, 11, "#F1E6C8")
for x in range(1, 15):
    img.putpixel((x, 4), hx("#8A7A55")); img.putpixel((x, 11), hx("#8A7A55"))
for y in range(4, 12):
    img.putpixel((1, y), hx("#8A7A55")); img.putpixel((14, y), hx("#8A7A55"))
rect(img, 2, 5, 4, 10, "#C8323C")
px(img, [(3, 7), (3, 8)], "#F1E6C8")
for y in (6, 8):
    px(img, [(x, y) for x in range(6, 13 if y == 6 else 11)], "#6B5E44")
px(img, [(12, 9), (12, 10)], "#3355BB")
save(img, "item/ticket")

# 도로 포장기(롤러)
img = new()
for i in range(7):
    px(img, [(13 - i, 2 + i)], SPRUCE[0] if i % 2 else SPRUCE[1])
    px(img, [(14 - i, 2 + i)], SPRUCE[3])
rect(img, 1, 9, 10, 14, ANDESITE[2])
rect(img, 1, 9, 10, 9, ANDESITE[0]); rect(img, 1, 10, 10, 10, ANDESITE[1])
rect(img, 1, 14, 10, 14, ANDESITE[4]); rect(img, 1, 13, 10, 13, ANDESITE[3])
rect(img, 0, 10, 0, 13, BRASS[3]); rect(img, 11, 10, 11, 13, BRASS[2])
px(img, [(11, 9), (11, 14), (0, 9), (0, 14)], BRASS[4])
px(img, [(6, 8), (7, 8)], BRASS[1])
save(img, "item/road_paver")

# 선로 부설기(손잡이 + 레일 조각)
img = new()
for i in range(8):
    px(img, [(13 - i, 1 + i)], SPRUCE[i % 2])
    px(img, [(14 - i, 1 + i)], SPRUCE[3])
rect(img, 1, 9, 9, 9, "#7A5A3A"); rect(img, 1, 12, 9, 12, "#7A5A3A")
rect(img, 2, 8, 2, 14, IRON[1]); rect(img, 3, 8, 3, 14, IRON[3])
rect(img, 7, 8, 7, 14, IRON[1]); rect(img, 8, 8, 8, 14, IRON[3])
px(img, [(5, 9), (5, 12)], BRASS[1])
save(img, "item/track_layer")

# 터널 굴착기(Create 드릴 머리: 나선 홈이 있는 원뿔)
img = new()
for t in range(10):
    cx, cy = 5 + t, 10 - t
    r = 3 - (t * 3) // 10
    for k in range(-r, r + 1):
        for (x, y) in ((cx + k, cy + k), (cx + k + 1, cy + k)):
            if 0 <= x < 16 and 0 <= y < 16:
                edge = abs(k) == r
                groove = t % 3 == 2
                c = IRON[3] if groove else (IRON[2] if edge else IRON[1])
                if k == -r and not groove:
                    c = IRON[0]
                img.putpixel((x, y), hx(c))
rect(img, 0, 11, 4, 15, BRASS[2]); rect(img, 0, 11, 4, 11, BRASS[0]); rect(img, 0, 11, 0, 15, BRASS[1])
rect(img, 4, 11, 4, 15, BRASS[4]); rect(img, 0, 15, 4, 15, BRASS[4])
px(img, [(2, 13)], BRASS[4])
save(img, "item/tunnel_borer")

# 측량기(삼각대 + 황동 망원경)
img = new()
px(img, [(7, 9), (6, 10), (5, 11), (4, 12), (3, 13), (2, 14)], SPRUCE[1])
px(img, [(8, 9), (8, 10), (8, 11), (8, 12), (8, 13), (8, 14)], SPRUCE[2])
px(img, [(9, 9), (10, 10), (11, 11), (12, 12), (13, 13), (14, 14)], SPRUCE[1])
rect(img, 3, 5, 12, 7, BRASS[2]); rect(img, 3, 5, 12, 5, BRASS[0]); rect(img, 3, 7, 12, 7, BRASS[3])
rect(img, 13, 4, 14, 8, BRASS[3]); px(img, [(13, 5), (13, 6), (13, 7)], "#8FD8FF")
px(img, [(2, 5), (2, 6), (2, 7)], BRASS[4])
rect(img, 7, 8, 8, 8, BRASS[3])
save(img, "item/surveyor")

# 호루라기
img = new()
rect(img, 3, 7, 11, 10, BRASS[2]); rect(img, 3, 7, 11, 7, BRASS[0]); rect(img, 3, 10, 11, 10, BRASS[4])
rect(img, 12, 8, 14, 9, BRASS[3]); px(img, [(6, 8)], "#2A1B08"); px(img, [(7, 8)], "#2A1B08")
px(img, [(2, 6), (1, 5), (1, 4), (2, 3), (3, 3), (4, 4), (4, 5)], "#D8D0C0")
save(img, "item/whistle")

def horseshoe(pal, name):
    img = new()
    u = [(4, 3), (4, 4), (4, 5), (4, 6), (4, 7), (4, 8), (4, 9), (5, 10), (5, 11), (6, 12), (7, 12), (8, 12), (9, 12), (10, 11), (10, 10), (11, 9), (11, 8), (11, 7), (11, 6), (11, 5), (11, 4), (11, 3)]
    inner = [(5, 3), (5, 4), (5, 5), (5, 6), (5, 7), (5, 8), (5, 9), (6, 10), (6, 11), (7, 11), (8, 11), (9, 11), (9, 10), (10, 9), (10, 8), (10, 7), (10, 6), (10, 5), (10, 4), (10, 3)]
    px(img, u, pal[2]); px(img, inner, pal[1])
    px(img, [(4, 3), (11, 3)], pal[0]); px(img, [(5, 12), (10, 12), (6, 13), (9, 13)], pal[3])
    px(img, [(5, 5), (5, 8), (10, 5), (10, 8)], pal[4])
    save(img, "item/" + name)
horseshoe(IRON, "iron_horseshoe")
horseshoe(["#E8FFFC", "#8CF4E2", "#4AD6C0", "#2A9C8C", "#145C52"], "diamond_horseshoe")

# 엔더 선로 핵: 황동 고리 + 엔더 코어
img = new()
ring = [(x, y) for x in range(16) for y in range(16) if 4.2 <= ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5 <= 6.3]
for (x, y) in ring:
    img.putpixel((x, y), hx(BRASS[1] if x + y < 15 else BRASS[3]))
for (x, y) in [(x, y) for x in range(16) for y in range(16) if ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5 < 4.2]:
    d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
    img.putpixel((x, y), hx("#C9FFF6" if d < 1.2 else "#39C5BB" if d < 2.4 else "#5A2A8A"))
px(img, [(7, 0), (8, 0), (7, 15), (8, 15), (0, 7), (0, 8), (15, 7), (15, 8)], BRASS[2])
save(img, "item/ender_rail_core")

# ═══════════════════ 지하철 키트 ═══════════════════

# 터널 라이닝: 콘크리트 세그먼트 4장 + 볼트
img = new()
noise(img, "#A7A9A4", 6, 31)
for i in range(16):
    img.putpixel((i, 7), hx("#7C7E79")); img.putpixel((i, 8), hx("#C4C6C0"))
    img.putpixel((7, i), hx("#7C7E79")); img.putpixel((8, i), hx("#C4C6C0"))
    img.putpixel((i, 0), hx("#C4C6C0")); img.putpixel((0, i), hx("#C4C6C0"))
    img.putpixel((i, 15), hx("#7C7E79")); img.putpixel((15, i), hx("#7C7E79"))
for (x, y) in [(3, 3), (12, 3), (3, 12), (12, 12)]:
    img.putpixel((x, y), hx(ANDESITE[4])); img.putpixel((x - 1, y - 1), hx(ANDESITE[0]))
save(img, "block/tunnel_lining")

# 터널 조명: 안산암 케이싱 + 흰 형광 막대 두 줄
img = new("#000000"); casing(img, ANDESITE, 32)
rect(img, 3, 3, 12, 12, "#3B3F3A")
for y0 in (4, 9):
    rect(img, 4, y0, 11, y0 + 2, "#EAF7FF"); rect(img, 4, y0 + 1, 11, y0 + 1, "#FFFFFF")
    px(img, [(4, y0), (11, y0 + 2)], "#B9D4E0")
save(img, "block/tunnel_light")

# 승강장 안전선: 노란 점자 블록 + 회색 테두리
img = new()
noise(img, "#E8B923", 5, 33)
for x in range(16):
    img.putpixel((x, 0), hx("#9A9C97")); img.putpixel((x, 15), hx("#6E706B"))
for y in range(16):
    img.putpixel((0, y), hx("#9A9C97")); img.putpixel((15, y), hx("#6E706B"))
for y in range(2, 14, 3):
    for x in range(2, 14, 3):
        px(img, [(x, y), (x + 1, y)], "#FFE27A"); px(img, [(x, y + 1), (x + 1, y + 1)], "#B98E12")
save(img, "block/platform_edge")

# 역 승강기: 황동 케이싱 + 위/아래 화살표
img = new("#000000"); casing(img, BRASS, 34); rect(img, 3, 3, 12, 12, "#2B2F33")
px(img, [(7, 4), (8, 4), (6, 5), (9, 5), (5, 6), (10, 6), (7, 5), (8, 5), (7, 6), (8, 6)], "#55BB44")
px(img, [(7, 11), (8, 11), (6, 10), (9, 10), (5, 9), (10, 9), (7, 10), (8, 10), (7, 9), (8, 9)], "#FFCC11")
save(img, "block/station_elevator_top")
img = new("#000000"); casing(img, BRASS, 35); rect(img, 3, 3, 12, 12, "#2B2F33")
rect(img, 7, 3, 8, 12, "#6E726C")
px(img, [(5, 5), (10, 5), (5, 10), (10, 10)], BRASS[1])
rect(img, 4, 6, 6, 9, "#3E4A52"); rect(img, 9, 6, 11, 9, "#3E4A52")
save(img, "block/station_elevator_side")

# 실드 굴진기: 원형 커터 헤드(황동 고리 + 철 커터 날)
img = new()
for y in range(16):
    for x in range(16):
        d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
        if d < 7.4:
            c = BRASS[3] if d > 6.2 else (BRASS[1] if d > 5.4 else ("#3A3C38" if d > 1.6 else BRASS[0]))
            img.putpixel((x, y), hx(c))
for i in range(-5, 6):
    for (x, y) in [(7 + i, 7), (7, 7 + i), (8 + i, 8), (8, 8 + i)]:
        if 0 <= x < 16 and 0 <= y < 16 and ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5 < 5.4:
            img.putpixel((x, y), hx(IRON[1] if (x + y) % 2 else IRON[2]))
for (x, y) in [(3, 3), (12, 3), (3, 12), (12, 12)]:
    img.putpixel((x, y), hx(IRON[0]))
save(img, "item/subway_shield")

# 지하 역사 설계도: 청사진
img = new()
rect(img, 1, 2, 14, 13, "#2E5C9E")
for x in range(1, 15):
    img.putpixel((x, 2), hx("#1E3F70")); img.putpixel((x, 13), hx("#1E3F70"))
for y in range(2, 14):
    img.putpixel((1, y), hx("#1E3F70")); img.putpixel((14, y), hx("#1E3F70"))
for x in range(3, 13):
    img.putpixel((x, 5), hx("#CFE3FF")); img.putpixel((x, 10), hx("#CFE3FF"))
for y in range(5, 11):
    img.putpixel((3, y), hx("#CFE3FF")); img.putpixel((12, y), hx("#CFE3FF"))
px(img, [(x, 7) for x in range(4, 12)] + [(x, 8) for x in range(4, 12)], "#8FB6E8")
px(img, [(5, 6), (9, 6), (6, 9), (10, 9)], "#FFFFFF")
px(img, [(13, 3), (12, 3), (13, 4)], "#FFCC11")
save(img, "item/station_blueprint")


# ═══════════════════ 낭만 업데이트: 역·항구·차량 ═══════════════════

def paper(fill, edge):
    img = new()
    rect(img, 1, 2, 14, 13, fill)
    for x in range(1, 15):
        img.putpixel((x, 2), hx(edge)); img.putpixel((x, 13), hx(edge))
    for y in range(2, 14):
        img.putpixel((1, y), hx(edge)); img.putpixel((14, y), hx(edge))
    return img

# 지상 역사 설계도: 청사진에 지붕(삼각)과 승강장 두 줄
img = paper("#2E5C9E", "#1E3F70")
px(img, [(7, 4), (8, 4), (6, 5), (9, 5), (5, 6), (10, 6), (4, 7), (11, 7)], "#CFE3FF")
px(img, [(5, y) for y in range(8, 11)] + [(10, y) for y in range(8, 11)], "#CFE3FF")
px(img, [(x, 11) for x in range(3, 13)], "#8FB6E8")
px(img, [(13, 3), (12, 3), (13, 4)], "#FF7E00")
save(img, "item/ground_station_blueprint")

# 항구 설계도: 바랜 양피지에 물결과 잔교(난파선에서 건진 옛 도면)
img = paper("#D9C08A", "#9C7B45")
for x in range(2, 14):
    img.putpixel((x, 9 + (x % 3 == 0)), hx("#3A6FB0"))
    img.putpixel((x, 12 - (x % 3 == 1)), hx("#5C8ECC"))
px(img, [(x, 6) for x in range(3, 11)], "#6B4A26")
px(img, [(4, 7), (4, 8), (7, 7), (7, 8), (10, 7), (10, 8)], "#4A3218")
px(img, [(12, 4), (12, 5), (11, 5), (13, 5), (12, 6), (11, 7), (13, 7), (12, 7)], "#8A2A1E")
save(img, "item/harbor_blueprint")

def car_sheet(body, body_dark, tag):
    img = paper("#2E5C9E", "#1E3F70")
    rect(img, 3, 5, 12, 10, body)
    for x in range(3, 13):
        img.putpixel((x, 5), hx("#FFFFFF")); img.putpixel((x, 10), hx(body_dark))
    for x in (4, 6, 9, 11):
        rect(img, x, 6, x, 7, "#CFE3FF")
    rect(img, 7, 6, 8, 9, body_dark)
    px(img, [(5, 11), (6, 11), (10, 11), (11, 11)], "#1A1A1A")
    px(img, [(13, 3), (12, 3), (13, 4)], tag)
    return img
save(car_sheet("#B9C2C8", "#6E7A82", "#39C5BB"), "item/metro_car_blueprint")
save(car_sheet("#8A5A33", "#5A3A1E", "#FF0045"), "item/coach_blueprint")

# 화물 상자: 가문비나무 판자 + 쇠 모서리 + 꼬리표
img = new()
planks(img, SPRUCE, 41, 2, 3, 13, 13)
for i in range(2, 14):
    img.putpixel((i, 3), hx(SPRUCE[3])); img.putpixel((i, 13), hx(SPRUCE[3]))
    img.putpixel((2, min(13, i + 1)), hx(SPRUCE[3])); img.putpixel((13, min(13, i + 1)), hx(SPRUCE[3]))
for (x, y) in [(2, 3), (13, 3), (2, 13), (13, 13), (3, 4), (12, 4), (3, 12), (12, 12)]:
    img.putpixel((x, y), hx(IRON[2]))
px(img, [(x, x + 1) for x in range(3, 12)], SPRUCE[2])
rect(img, 9, 1, 12, 3, "#F2E6C8"); px(img, [(8, 2)], "#C8B48A"); px(img, [(10, 2), (11, 2)], "#8A2A1E")
save(img, "item/cargo_crate")

# 승강장 안전문: 틀(밝은 철 + 노란 표시등)과 반투명 유리
img = new("#000000"); casing(img, IRON, 42)
rect(img, 3, 3, 12, 12, "#9AA3A8")
px(img, [(7, 7), (8, 7), (7, 8), (8, 8)], "#FFCC11")
save(img, "block/screen_door_frame")
img = Image.new("RGBA", (16, 16), (170, 215, 235, 96))
for i in range(5):
    if 3 + i < 16:
        img.putpixel((3 + i, 9 - i), (255, 255, 255, 150))
        img.putpixel((4 + i, 9 - i), (255, 255, 255, 110))
save(img, "block/screen_door_glass")
img = new()
rect(img, 3, 0, 12, 15, "#A8A8A8")
rect(img, 4, 1, 11, 14, (170, 215, 235, 200))
rect(img, 4, 11, 11, 14, "#727272")
px(img, [(5, 6), (6, 5), (7, 4)], (255, 255, 255, 220))
px(img, [(7, 0), (8, 0)], "#FFCC11")
save(img, "item/screen_door")

# 교역소: 가문비나무 통 + 닻 표식, 옆면엔 장부 판
img = new()
planks(img, SPRUCE, 43, 0, 0, 15, 15)
for i in range(16):
    img.putpixel((i, 0), hx(SPRUCE[3])); img.putpixel((i, 15), hx(SPRUCE[3]))
    img.putpixel((0, i), hx(SPRUCE[3])); img.putpixel((15, i), hx(SPRUCE[3]))
anchor = [(7, 3), (8, 3), (7, 4), (8, 4), (5, 5), (6, 5), (7, 5), (8, 5), (9, 5), (10, 5)] + \
         [(7, y) for y in range(6, 12)] + [(8, y) for y in range(6, 12)] + \
         [(4, 10), (5, 11), (6, 12), (11, 10), (10, 11), (9, 12), (7, 12), (8, 12)]
px(img, anchor, IRON[1])
save(img, "block/trading_post_top")
img = new()
planks(img, SPRUCE, 44, 0, 0, 15, 15, horizontal=False)
for x in range(16):
    img.putpixel((x, 1), hx(IRON[3])); img.putpixel((x, 14), hx(IRON[3]))
rect(img, 4, 4, 11, 11, "#E9DDBB")
for y in (6, 8, 10):
    px(img, [(x, y) for x in range(5, 11)], "#8C7A55")
px(img, [(10, 5), (9, 5)], "#8A2A1E")
save(img, "block/trading_post_side")
img = new(); planks(img, SPRUCE, 45, 0, 0, 15, 15)
save(img, "block/trading_post_bottom")


# ═══════════════════ 옛 길(3등급) ═══════════════════

# 포석: 화강암 각석을 부채꼴로 깐 길
img = new("#4E4A47")
r = random.Random(51)
rows = [(0, 3), (4, 7), (8, 11), (12, 15)]
for ri, (y0, y1) in enumerate(rows):
    off = (ri % 2) * 2
    x = -off
    while x < 16:
        w = 4
        base = r.choice(["#9C8E86", "#A89A90", "#8E827B", "#B3A59A", "#968A84"])
        for yy in range(y0, y1):
            for xx in range(max(0, x), min(16, x + w - 1)):
                curve = 1 if (xx - x) in (0, w - 2) and yy == y0 else 0
                c = shade(hx(base), r.randint(-8, 8) - 18 * curve)
                img.putpixel((xx, yy), c)
        for xx in range(max(0, x), min(16, x + w - 1)):
            img.putpixel((xx, y0), shade(hx(base), 18))
        x += w
save(img, "block/setts")

# 벽돌 포장길: 붉은 벽돌을 바구니 짜임(가로 둘·세로 둘)으로 깐 길
img = new("#7A6A5C")
r = random.Random(55)
for by in range(0, 16, 4):
    for bx in range(0, 16, 4):
        horiz = ((bx + by) // 4) % 2 == 0
        for k in range(2):
            base = r.choice(["#A4513A", "#B05A40", "#9A4A35", "#B9684B"])
            x0, y0 = (bx, by + 2 * k) if horiz else (bx + 2 * k, by)
            w, h = (4, 2) if horiz else (2, 4)
            for y in range(y0, y0 + h):
                for x in range(x0, x0 + w):
                    last = (x == x0 + w - 1) if horiz else (y == y0 + h - 1)
                    if last:
                        continue
                    c = shade(hx(base), r.randint(-7, 7) + (14 if (y == y0 if horiz else x == x0) else 0))
                    img.putpixel((x, y), c)
save(img, "block/brick_paving")

# 판석길: 크기가 다른 납작한 돌, 틈에 이끼
img = new("#4F6B3A")
slabs = [(0, 0, 6, 5), (7, 0, 15, 3), (7, 4, 11, 8), (12, 4, 15, 9), (0, 6, 6, 11), (7, 9, 15, 15), (0, 12, 6, 15)]
r = random.Random(52)
for (x0, y0, x1, y1) in slabs:
    base = r.choice(["#8F8C84", "#9A978E", "#85827A", "#A39F95"])
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            edge = x in (x0, x1) or y in (y0, y1)
            if edge and r.random() < 0.55:
                continue
            img.putpixel((x, y), shade(hx(base), r.randint(-9, 9) + (12 if y == y0 else 0)))
for _ in range(14):
    x, y = r.randrange(16), r.randrange(16)
    if img.getpixel((x, y))[1] > 90 and img.getpixel((x, y))[0] < 90:
        img.putpixel((x, y), hx("#6E9447"))
save(img, "block/flagstone")

# 이정석: 풍화된 돌기둥, 숫자와 화살표를 새김
img = new()
noise(img, "#8E8B83", 7, 53)
for y in range(16):
    img.putpixel((0, y), hx("#77746D")); img.putpixel((15, y), hx("#6A675F"))
px(img, [(5, 4), (6, 4), (7, 4), (8, 4), (9, 4), (10, 4), (9, 3), (9, 5)], "#55524C")
px(img, [(5, 8), (5, 9), (5, 10), (5, 11), (8, 8), (9, 8), (10, 8), (10, 9), (8, 10), (9, 10), (10, 10), (8, 11), (8, 12), (9, 12), (10, 12)], "#55524C")
px(img, [(2, 14), (3, 15), (12, 13), (13, 14)], "#6E9447")
save(img, "block/milestone")
img = new(); noise(img, "#9A978F", 6, 54)
for i in range(16):
    img.putpixel((i, 0), hx("#B0ADA5")); img.putpixel((0, i), hx("#B0ADA5"))
save(img, "block/milestone_top")


# ═══════════════════ 히든: 독수리자리 ═══════════════════
import json as _json, math as _math

STARS = [("altair", "#EAF4FF"), ("tarazed", "#FF9A3C"), ("alshain", "#FFE27A"), ("deneb_el_okab", "#BFD7FF"),
         ("bezek", "#FFC531"), ("chesus", "#A8F0FF"), ("libertas", "#9BFFB0"), ("phonix", "#FF4E6A"), ("petra", "#C8B6FF")]

def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)

def save_anim(frames, path, frametime=2):
    sheet = Image.new("RGBA", (16, 16 * len(frames)), (0, 0, 0, 0))
    for i, f in enumerate(frames):
        sheet.alpha_composite(f, (0, 16 * i))
    save(sheet, path)
    with open(os.path.join(TEX, path + ".png.mcmeta"), "w") as f:
        _json.dump({"animation": {"frametime": frametime, "interpolate": True}}, f)

def shard(color, seed):
    c = hx(color)
    dark = mix(c, (20, 20, 50), 0.6)
    frames = []
    for fi in range(6):
        img = new()
        glow = 0.5 + 0.5 * _math.sin(fi / 6 * _math.pi * 2)
        # 마름모 결정
        for y in range(2, 14):
            w = 6 - abs(y - 7.5) * 0.85
            for x in range(16):
                if abs(x - 7.5) < w:
                    t = (abs(x - 7.5) / max(w, 0.1))
                    col = mix(c, dark, t * 0.7)
                    if x < 7.5 and y < 8:
                        col = mix(col, (255, 255, 255), 0.25)
                    img.putpixel((x, y), col)
        # 가운데 별빛과 십자 반짝임
        core = mix((255, 255, 255), c, 0.2)
        img.putpixel((7, 7), core); img.putpixel((8, 8), core); img.putpixel((7, 8), core); img.putpixel((8, 7), core)
        L = 1 + int(glow * 3)
        for d in range(1, L + 1):
            a = int(230 * (1 - d / (L + 1)))
            for (x, y) in [(7 - d, 7), (8 + d, 8), (7, 7 - d), (8, 8 + d)]:
                if 0 <= x < 16 and 0 <= y < 16:
                    img.putpixel((x, y), (255, 255, 255, max(a, 90)))
        frames.append(img)
    return frames

for i, (k, col) in enumerate(STARS):
    save_anim(shard(col, 60 + i), f"item/star_{k}")

# 아퀼라 성좌핵: 남색 구에 독수리자리 아홉 별과 선
POS = [(8, 8), (6, 6), (10, 10), (3, 2), (12, 13), (13, 5), (7, 13), (5, 10), (2, 7)]
LINES = [(1, 0), (0, 2), (0, 7), (7, 8), (7, 3), (0, 4), (4, 5), (7, 6)]
frames = []
for fi in range(8):
    img = new()
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 7.6:
                col = mix(hx("#1A1F5C"), hx("#05061A"), d / 7.6)
                if d > 6.6:
                    col = mix(hx("#FFCC11"), hx("#B07A2A"), (fi % 4) / 4)
                img.putpixel((x, y), col)
    for (a, b) in LINES:
        (x0, y0), (x1, y1) = POS[a], POS[b]
        n = max(abs(x1 - x0), abs(y1 - y0))
        for k in range(1, n):
            x = round(x0 + (x1 - x0) * k / n); y = round(y0 + (y1 - y0) * k / n)
            img.putpixel((x, y), (120, 150, 255, 255))
    for i, (x, y) in enumerate(POS):
        tw = 0.5 + 0.5 * _math.sin((fi + i * 1.7) / 8 * _math.pi * 2)
        img.putpixel((x, y), mix(hx(STARS[i][1]), (255, 255, 255), tw * 0.6))
    frames.append(img)
save_anim(frames, "item/aquila_core", 3)

# 아스트라 오브리터레이터: 별빛 칼날(대각선) + 황금 코등이 + 남색 손잡이, 칼날을 따라 빛이 흐른다
frames = []
blade = [(4 + i, 11 - i) for i in range(0, 9)]  # (4,11) → (12,3)
for fi in range(8):
    img = new()
    for i, (x, y) in enumerate(blade):
        for (dx, dy, tone) in [(0, 0, 0.0), (1, 0, 0.35), (0, -1, 0.35), (1, -1, 0.7), (-1, 0, 0.75), (0, 1, 0.75)]:
            xx, yy = x + dx, y + dy
            if 0 <= xx < 16 and 0 <= yy < 16:
                base = mix(hx("#EAF4FF"), hx("#3355BB"), tone)
                wave = _math.exp(-((i - fi * 1.4) % 11.2 - 0) ** 2 / 2.0)
                img.putpixel((xx, yy), mix(base, (255, 255, 255), wave * 0.8))
    # 끝의 별
    for (x, y) in [(14, 1), (13, 1), (14, 2), (15, 1), (14, 0)]:
        img.putpixel((x, y), hx("#FFFFFF") if fi % 2 == 0 else hx("#FFE27A"))
    # 칼날 위 아홉 별(색)
    for i, (k, col) in enumerate(STARS):
        bx, by = blade[i % len(blade)]
        if (i + fi) % 3 == 0:
            img.putpixel((bx, by), hx(col))
    # 코등이·손잡이
    for (x, y) in [(2, 10), (3, 11), (4, 12), (5, 13), (3, 9), (6, 12)]:
        img.putpixel((x, y), hx("#FFCC11"))
    img.putpixel((4, 11), hx("#FF0045"))
    for (x, y) in [(2, 13), (1, 14), (3, 12)]:
        img.putpixel((x, y), hx("#660099"))
    img.putpixel((0, 15), hx("#FFCC11"))
    frames.append(img)
save_anim(frames, "item/astra_obliterator", 2)

# 모드 아이콘(64x64): 특급 선로 위의 역 패널
icon = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
icon.alpha_composite(Image.open(os.path.join(TEX, "block/express_rail_on.png")))
st = Image.open(os.path.join(TEX, "block/station.png")).resize((10, 10), Image.NEAREST)
icon.alpha_composite(st, (3, 3))
icon.resize((128, 128), Image.NEAREST).save(os.path.join(ROOT, "icon.png"))
print("textures written")
