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
print("video assets written")
