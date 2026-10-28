"""영상용 보조 텍스처(돌·흙·잔디·물·용암·선로·Create 트랙 등)를 절차적으로 만든다.
바닐라 텍스처를 쓰지 않고 비슷한 분위기만 낸다. 모드 텍스처는 common/resources 의 것을 그대로 쓴다.
"""
import os
import random
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "assets")


def hx(c, a=255):
    c = c.lstrip("#")
    return (int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16), a)


def noise(base, spread, seed, specks=()):
    r = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    b = hx(base)
    for y in range(16):
        for x in range(16):
            d = r.randint(-spread, spread)
            img.putpixel((x, y), tuple(max(0, min(255, v + d)) for v in b[:3]) + (255,))
    for color, n in specks:
        for _ in range(n):
            img.putpixel((r.randrange(16), r.randrange(16)), hx(color))
    return img


def save(img, name):
    img.save(os.path.join(OUT, name + ".png"))


os.makedirs(OUT, exist_ok=True)
save(noise("#7d7d7d", 9, 1, [("#6a6a6a", 18), ("#969696", 10)]), "stone")
save(noise("#4a4a50", 7, 2, [("#3a3a40", 20), ("#5c5c62", 8)]), "deepslate")
save(noise("#86603f", 10, 3, [("#6e4c30", 16), ("#9b7450", 10)]), "dirt")
save(noise("#5f9b3c", 10, 4, [("#4f8a30", 18), ("#74b04c", 10)]), "grass_top")
side = noise("#86603f", 10, 5, [("#6e4c30", 16)])
r = random.Random(6)
for x in range(16):
    for y in range(3 + r.randint(0, 2)):
        side.putpixel((x, y), hx("#5f9b3c") if r.random() > 0.2 else hx("#4f8a30"))
save(side, "grass_side")
save(noise("#3d6fd6", 8, 7, [("#5b8cf0", 14)]), "water")
save(noise("#e2621d", 18, 8, [("#ffb02e", 26), ("#b8380e", 12)]), "lava")
save(noise("#c8b48a", 6, 9, [("#b29c70", 14)]), "sandstone")
save(noise("#d8d8d8", 4, 10, [("#bdbdbd", 10)]), "snow")
save(noise("#9a6b3f", 8, 11, [("#7f5532", 20)]), "planks")

# 바닐라풍 선로(투명 배경)
rail = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
for ty in (1, 5, 9, 13):
    for x in range(1, 15):
        rail.putpixel((x, ty), hx("#7a5634")); rail.putpixel((x, ty + 1), hx("#5a3d22"))
for y in range(16):
    for x0 in (2, 12):
        rail.putpixel((x0, y), hx("#c9c9c9")); rail.putpixel((x0 + 1, y), hx("#8a8a8a"))
save(rail, "rail")

# Create 풍 트랙: 넓은 침목 + 안산암 합금 레일
track = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
for ty in range(0, 16, 4):
    for x in range(0, 16):
        track.putpixel((x, ty), hx("#6b4a2e")); track.putpixel((x, ty + 1), hx("#5a3d24")); track.putpixel((x, ty + 2), hx("#4a3220"))
for y in range(16):
    for x0 in (3, 11):
        track.putpixel((x0, y), hx("#d7dbd4")); track.putpixel((x0 + 1, y), hx("#9aa097")); track.putpixel((x0 + 2, y), hx("#6e726c"))
save(track, "create_track")
# 지상 역사·항구용
save(noise("#7b7b7b", 6, 12, [("#666666", 14)]), "stone_bricks")
sb = Image.open(os.path.join(OUT, "stone_bricks.png"))
for x in range(16):
    for y in (3, 7, 11, 15):
        sb.putpixel((x, y), hx("#5a5a5a"))
for y in range(16):
    x0 = 7 if (y // 4) % 2 == 0 else 15
    sb.putpixel((x0, y), hx("#5a5a5a"))
save(sb, "stone_bricks")
save(noise("#a9a9a9", 3, 13, [("#9a9a9a", 8)]), "smooth_stone")
save(noise("#6b4c2e", 6, 14, [("#5a3f25", 18)]), "spruce")

# 전동차 차체(Create 산업용 철 느낌의 단순화): 차체·창·문
body = noise("#c9ced2", 3, 15, [("#b8bec3", 10)])
for x in range(16):
    body.putpixel((x, 0), hx("#e8ecef")); body.putpixel((x, 15), hx("#8d969c"))
for x in range(16):
    for y in (10, 11):
        body.putpixel((x, y), hx("#39C5BB"))
save(body, "car_body")
win = Image.new("RGBA", (16, 16), hx("#c9ced2"))
for y in range(2, 12):
    for x in range(2, 14):
        win.putpixel((x, y), hx("#2b3e57") if (x + y) % 9 else hx("#6f8db3"))
for x in range(16):
    for y in (12, 13):
        win.putpixel((x, y), hx("#39C5BB"))
save(win, "car_window")
door = Image.new("RGBA", (16, 16), hx("#aab2b8"))
for y in range(16):
    door.putpixel((7, y), hx("#5b646a")); door.putpixel((8, y), hx("#5b646a"))
for y in range(2, 9):
    for x in (3, 4, 5, 10, 11, 12):
        door.putpixel((x, y), hx("#2b3e57"))
save(door, "car_door")
glass = Image.new("RGBA", (16, 16), (170, 215, 235, 90))
for x in range(16):
    glass.putpixel((x, 0), hx("#9aa3a8")); glass.putpixel((x, 15), hx("#9aa3a8"))
for y in range(16):
    glass.putpixel((0, y), hx("#9aa3a8")); glass.putpixel((15, y), hx("#9aa3a8"))
save(glass, "door_glass")
# 마인카트(철 상자 느낌)
cs = Image.new("RGBA", (16, 16), hx("#8e959b"))
for x in range(16):
    cs.putpixel((x, 0), hx("#c3c9ce")); cs.putpixel((x, 15), hx("#4d5358")); cs.putpixel((x, 12), hx("#5f666b"))
for y in range(16):
    cs.putpixel((0, y), hx("#c3c9ce")); cs.putpixel((15, y), hx("#4d5358"))
for (x, y) in [(2, 2), (13, 2), (2, 13), (13, 13)]:
    cs.putpixel((x, y), hx("#e4e8eb"))
save(cs, "cart_side")
ct = Image.new("RGBA", (16, 16), hx("#3a3f44"))
for i in range(16):
    for (x, y) in [(i, 0), (i, 1), (i, 14), (i, 15), (0, i), (1, i), (14, i), (15, i)]:
        ct.putpixel((x, y), hx("#a4abb0"))
save(ct, "cart_top")
print("video assets written")
