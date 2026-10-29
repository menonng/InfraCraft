"""아스트라 오브리터레이터 관련 아이템 11개(별조각 9 · 아퀼라 성좌핵 · 아스트라 오브리터레이터)의 32x32 움직이는 텍스처와
독수리자리 발전과제 탭의 우주 배경을 그린다. 이름 그라데이션 색도 여기서 정한다(아이템 코드와 같은 값).

사용법: python3 tools/gen_astra.py   (tools/gen_textures.py 다음에 실행)
"""
import json
import math
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "common", "resources", "assets", "infracraft")
TEX = os.path.join(ROOT, "textures")
S = 32

# (키, 별자리 평면 좌표, 별 색) — Astra.kt 의 STARS 와 같다
STARS = [
    ("altair", 0.0, 0.0, "#EAF4FF"), ("tarazed", -0.7, 1.0, "#FF9A3C"), ("alshain", 0.7, -1.0, "#FFE27A"),
    ("deneb_el_okab", -2.9, 3.3, "#BFD7FF"), ("bezek", 2.3, -2.4, "#FFC531"), ("chesus", 3.6, 1.4, "#A8F0FF"),
    ("libertas", -0.6, -3.0, "#9BFFB0"), ("phonix", -1.9, -1.1, "#FF4E6A"), ("petra", -3.8, 0.1, "#C8B6FF"),
]
LINES = [(1, 0), (0, 2), (0, 7), (7, 8), (7, 3), (0, 4), (4, 5), (7, 6)]
# 발전과제 원 위의 순서(반시계, 30°부터 40°씩, 알타이르는 맨 아래 270°)
RING = ["chesus", "tarazed", "deneb_el_okab", "petra", "phonix", "libertas", "altair", "alshain", "bezek"]
# 별의 밝기(겉보기 등급 순): 십자 광채 길이
MAG = {"altair": 1.0, "tarazed": 0.85, "alshain": 0.72, "deneb_el_okab": 0.74, "bezek": 0.62, "chesus": 0.6,
       "libertas": 0.58, "phonix": 0.6, "petra": 0.6}


def hx(s):
    s = s.lstrip("#")
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def to_hex(c):
    return "#%02X%02X%02X" % c


# ───────────── 이름 그라데이션(EnderShower Gradient 생성기와 같은 방식: 시작·끝 두 색, 글자마다 선형 보간) ─────────────

def gradient_pairs():
    color = {k: c for k, _, _, c in STARS}
    out = {}
    for i, k in enumerate(RING):
        out[f"star_{k}"] = (color[k], color[RING[(i + 1) % len(RING)]])
    out["aquila_core"] = ("#C8B6FF", "#FFE27A")
    out["astra_obliterator"] = ("#8A4DFF", "#F2F7FF")
    return out


def gradient_text(word, start, end):
    """EnderShower/Gradient 의 update_gradient 와 같은 계산: 첫 글자 = 시작색, 마지막 글자 = 끝색, 가운데는 i/(n-1) 로 보간(소수점 버림)"""
    a, b = hx(start), hx(end)
    n = len(word)
    out = []
    for i, ch in enumerate(word):
        if n == 1 or i == 0:
            c = a
        elif i == n - 1:
            c = b
        else:
            c = tuple(int(a[j] + i * (b[j] - a[j]) / (n - 1)) for j in range(3))
        out.append((ch, to_hex(c)))
    return out


# ───────────── 그리기 도구 ─────────────

def new():
    return Image.new("RGBA", (S, S), (0, 0, 0, 0))


def put(img, x, y, c, a=255):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), (*c, a))


def blend(img, x, y, c, a):
    """기존 픽셀 위에 알파로 덧칠(빛은 더하기에 가깝게)"""
    if not (0 <= x < img.width and 0 <= y < img.height) or a <= 0:
        return
    r, g, b, oa = img.getpixel((x, y))
    t = a / 255
    if oa == 0:
        img.putpixel((x, y), (*c, int(a)))
        return
    nc = (int(r + (c[0] - r) * t), int(g + (c[1] - g) * t), int(b + (c[2] - b) * t))
    img.putpixel((x, y), (*nc, max(oa, int(a))))


def outline(img, color, alpha=255):
    """불투명 픽셀 둘레(상하좌우)에 1픽셀 윤곽"""
    src = img.copy()
    for y in range(img.height):
        for x in range(img.width):
            if src.getpixel((x, y))[3] > 40:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                xx, yy = x + dx, y + dy
                if 0 <= xx < img.width and 0 <= yy < img.height and src.getpixel((xx, yy))[3] > 160:
                    img.putpixel((x, y), (*color, alpha))
                    break


def flare(img, cx, cy, length, color, diag=0, core=(255, 255, 255)):
    """십자 광채: 가로·세로로 길게, 대각선은 짧게. 끝으로 갈수록 투명"""
    for d in range(1, length + 1):
        a = int(255 * (1 - d / (length + 1)) ** 1.3)
        for x, y in ((cx - d, cy), (cx + d, cy), (cx, cy - d), (cx, cy + d)):
            blend(img, x, y, mix(core, color, d / (length + 1)), a)
    for d in range(1, diag + 1):
        a = int(200 * (1 - d / (diag + 1)))
        for x, y in ((cx - d, cy - d), (cx + d, cy + d), (cx - d, cy + d), (cx + d, cy - d)):
            blend(img, x, y, mix(core, color, 0.4), a)
    put(img, cx, cy, core)


def sparkle(img, x, y, color, phase):
    """작은 반짝임(+): phase 0~1"""
    a = int(255 * max(0.0, math.sin(phase * math.pi)))
    if a < 30:
        return
    blend(img, x, y, (255, 255, 255), a)
    if a > 140:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            blend(img, x + dx, y + dy, color, a // 2)


def save_anim(frames, path, frametime=2):
    sheet = Image.new("RGBA", (S, S * len(frames)), (0, 0, 0, 0))
    for i, f in enumerate(frames):
        sheet.alpha_composite(f, (0, S * i))
    full = os.path.join(TEX, path + ".png")
    os.makedirs(os.path.dirname(full), exist_ok=True)
    sheet.save(full)
    with open(full + ".mcmeta", "w") as f:
        json.dump({"animation": {"frametime": frametime, "interpolate": True}}, f)


# ───────────── 별조각: 깎인 결정 + 속에서 타오르는 별 + 십자 광채 + 둘레 반짝임 ─────────────

def shard(key, color_hex, seed):
    c = hx(color_hex)
    rnd = random.Random(seed)
    hi = mix(c, (255, 255, 255), 0.62)
    light = mix(c, (255, 255, 255), 0.28)
    base = c
    shade = mix(c, (24, 20, 70), 0.45)
    deep = mix(c, (10, 8, 34), 0.7)
    edge = mix(c, (8, 4, 24), 0.82)
    top, bottom, mid = 4, 28, 13  # 꼭짓점과 가장 넓은 줄
    halfw = 7 if key != "tarazed" else 8
    sparks = [(rnd.randint(3, 28), rnd.randint(3, 28), rnd.random()) for _ in range(5)]
    sparks = [(x, y, p) for x, y, p in sparks if abs(x - 16) > 6 or abs(y - 16) > 9][:3]
    frames = []
    N = 12
    for fi in range(N):
        t = fi / N
        img = new()
        for y in range(top, bottom + 1):
            w = halfw * ((y - top) / (mid - top) if y <= mid else (bottom - y) / (bottom - mid))
            for x in range(S):
                dx = x + 0.5 - 16
                if abs(dx) > w + 0.15:
                    continue
                upper = y <= mid
                left = dx < 0
                col = (light if left else base) if upper else (shade if left else deep)
                # 가운데 능선과 안쪽 면(테이블)
                if abs(dx) < 0.9:
                    col = mix(col, hi, 0.35)
                inner = halfw * 0.45 * ((y - top) / (mid - top) if upper else (bottom - y) / (bottom - mid))
                if abs(dx) < inner and abs(y - mid) < 7:
                    col = mix(col, hi, 0.3 * (1 - abs(y - mid) / 7))
                put(img, x, y, col)
        outline(img, edge)
        # 결정 둘레의 은은한 빛무리(반투명): 박자에 따라 숨쉬듯 커졌다 작아진다
        breath = 0.5 + 0.5 * math.sin(t * 2 * math.pi)
        mask = img.copy()
        for y in range(S):
            for x in range(S):
                if mask.getpixel((x, y))[3] > 0:
                    continue
                d = min((math.hypot(x - xx, y - yy) for xx, yy in ((16, mid), (16, top + 3), (16, bottom - 3))), default=99)
                a = int(70 * max(0.0, 1 - (d - 4) / (6 + 3 * breath)))
                if a > 4:
                    img.putpixel((x, y), (*mix(c, (255, 255, 255), 0.3), a))
        # 윗면 왼쪽의 비스듬한 반사광
        for i in range(4):
            put(img, 12 + i // 2, 8 + i, mix(hi, (255, 255, 255), 0.5), 230)
        # 속의 별: 맥동하는 빛무리
        pulse = 0.5 + 0.5 * math.sin(t * 2 * math.pi)
        for y in range(mid - 4, mid + 5):
            for x in range(12, 21):
                d = math.hypot(x + 0.5 - 16, y + 0.5 - (mid + 0.5))
                a = int(255 * max(0.0, 1 - d / (2.6 + pulse * 1.6)) ** 1.5)
                blend(img, x, y, mix((255, 255, 255), c, d / 5), a)
        L = int(round((6 + 9 * pulse) * MAG[key]))
        flare(img, 16, mid, L, c, diag=1 + int(2 * pulse * MAG[key]))
        flare(img, 15, mid, max(1, L - 2), c, diag=0)
        # 둘레의 작은 반짝임(서로 다른 박자)
        for x, y, p in sparks:
            sparkle(img, x, y, hi, (t + p) % 1.0)
        frames.append(img)
    return frames


# ───────────── 아퀼라 성좌핵: 성운이 소용돌이치는 구, 그 안의 독수리자리, 기울어진 황금 혼천의 고리 ─────────────

def value_noise(seed, n=8):
    r = random.Random(seed)
    g = [[r.random() for _ in range(n)] for _ in range(n)]

    def f(x, y):
        x %= n; y %= n
        x0, y0 = int(x), int(y)
        fx, fy = x - x0, y - y0
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy)
        a = g[y0][x0]; b = g[y0][(x0 + 1) % n]; c = g[(y0 + 1) % n][x0]; d = g[(y0 + 1) % n][(x0 + 1) % n]
        return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy
    return f


def core_frames():
    nz1, nz2 = value_noise(7, 6), value_noise(19, 9)
    cx = cy = 15.5
    R = 11.2
    pts = []
    for k, x, y, col in STARS:
        pts.append((16 + x * 2.1 - 0.3, 16 - y * 2.1 + 0.6, hx(col), k))
    frames = []
    N = 16
    gold = [hx("#FFF1B0"), hx("#FFCC11"), hx("#D99A1E"), hx("#8C5A12")]
    for fi in range(N):
        t = fi / N
        img = new()
        # 뒤쪽 고리(구에 가려지는 반)
        ring = []
        tilt = math.radians(-22)
        for i in range(160):
            a = i / 160 * 2 * math.pi
            ex, ey = math.cos(a) * 15.2, math.sin(a) * 4.6
            rx = ex * math.cos(tilt) - ey * math.sin(tilt)
            ry = ex * math.sin(tilt) + ey * math.cos(tilt)
            ring.append((a, cx + rx, cy + ry, math.sin(a) > 0))
        for a, x, y, front in ring:
            if not front:
                put(img, int(x), int(y), mix(gold[2], (40, 30, 60), 0.45))
        # 구
        for y in range(S):
            for x in range(S):
                dx, dy = x + 0.5 - (cx + 0.5), y + 0.5 - (cy + 0.5)
                d = math.hypot(dx, dy)
                if d > R:
                    continue
                # 소용돌이: 각도를 시간과 반지름에 따라 비튼 좌표로 성운을 그린다
                ang = math.atan2(dy, dx) + t * 2 * math.pi * 0.25 + d * 0.09
                u, v = math.cos(ang) * d / 3.2 + 4, math.sin(ang) * d / 3.2 + 4
                n = 0.65 * nz1(u, v) + 0.35 * nz2(u * 1.7, v * 1.7)
                col = mix(hx("#070414"), hx("#1B0E4A"), n * 1.4)
                col = mix(col, hx("#4A1C7A"), max(0, n - 0.55) * 2.2)
                col = mix(col, hx("#0E3A6A"), max(0, 0.45 - n) * 1.2)
                # 구 음영: 왼쪽 위가 밝고 가장자리는 어둡게, 테두리에 얇은 림 라이트
                lit = max(0.0, -(dx + dy) / (R * 1.6))
                col = mix(col, hx("#6A5CFF"), lit * 0.35)
                col = mix(col, (0, 0, 0), max(0.0, d / R - 0.75) * 1.3)
                if d > R - 1.1:
                    col = mix(col, hx("#9FD8FF"), 0.55 if dx + dy < 0 else 0.2)
                put(img, x, y, col)
        # 별자리 선
        for a, b in LINES:
            x0, y0, _, _ = pts[a]; x1, y1, _, _ = pts[b]
            n = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
            for i in range(n + 1):
                x = x0 + (x1 - x0) * i / n; y = y0 + (y1 - y0) * i / n
                blend(img, int(x), int(y), hx("#8FB0FF"), 110)
        # 별(제각각 반짝임)
        for i, (x, y, col, k) in enumerate(pts):
            tw = 0.5 + 0.5 * math.sin((t + i * 0.37) * 2 * math.pi)
            put(img, int(x), int(y), mix(col, (255, 255, 255), 0.4 + 0.5 * tw))
            if k == "altair" or tw > 0.8:
                flare(img, int(x), int(y), 1 + int(tw * (2 if k == "altair" else 1)), col)
        # 앞쪽 고리 + 고리를 따라 도는 빛구슬
        for a, x, y, front in ring:
            if front:
                shade = 0.5 + 0.5 * math.cos(a)
                put(img, int(x), int(y), mix(gold[1], gold[0], shade * 0.6))
                if 0 <= int(y) + 1 < S:
                    blend(img, int(x), int(y) + 1, gold[3], 160)
        for j in range(3):
            a = (t + j / 3) * 2 * math.pi
            ex, ey = math.cos(a) * 15.2, math.sin(a) * 4.6
            x = cx + ex * math.cos(tilt) - ey * math.sin(tilt)
            y = cy + ex * math.sin(tilt) + ey * math.cos(tilt)
            if math.sin(a) > 0 or math.hypot(x - cx, y - cy) > R:
                flare(img, int(x), int(y), 2, gold[1], core=(255, 250, 220))
        frames.append(img)
    return frames


# ───────────── 아스트라 오브리터레이터: 밤하늘이 흐르는 칼날, 독수리 날개 코등이, 알타이르 보석 ─────────────

def weapon_frames():
    p0 = (3.5, 28.5)   # 폼멜 쪽
    p1 = (29.0, 3.0)   # 칼끝
    ax = (p1[0] - p0[0], p1[1] - p0[1])
    L = math.hypot(*ax)
    ux, uy = ax[0] / L, ax[1] / L
    vx, vy = -uy, ux  # 칼날의 옆 방향(+v 는 오른쪽 아래)
    rnd = random.Random(42)
    specks = [(rnd.uniform(10, L - 2), rnd.uniform(-1.9, 1.9), rnd.choice([s[3] for s in STARS]), rnd.random()) for _ in range(18)]
    gold = [hx("#FFF1B0"), hx("#FFCC11"), hx("#D99A1E"), hx("#8C5A12")]
    N = 16
    frames = []
    for fi in range(N):
        t = fi / N
        img = new()
        sweep = (t * 1.6 % 1.6) * (L + 6) - 3  # 칼날을 따라 흐르는 빛줄기 위치
        for y in range(S):
            for x in range(S):
                px_, py_ = x + 0.5 - p0[0], y + 0.5 - p0[1]
                u = px_ * ux + py_ * uy
                v = px_ * vx + py_ * vy
                col = None
                if 9.5 <= u <= L:
                    w = 3.4 * (1 - ((u - 9.5) / (L - 9.5)) ** 2.6) + 0.4
                    if abs(v) <= w:
                        e = abs(v) / w
                        # 칼날 속: 깊은 밤하늘
                        col = mix(hx("#06020F"), hx("#1E0A45"), 0.5 + 0.5 * math.sin(u * 0.45 - t * 6.28))
                        if e > 0.62:  # 날: 위는 밝은 별빛, 아래는 보랏빛
                            col = mix(hx("#C9A6FF"), hx("#F4F8FF"), (e - 0.62) / 0.38) if v < 0 else mix(hx("#5A2A9A"), hx("#8A4DFF"), (e - 0.62) / 0.38)
                        elif abs(v) < 0.45:  # 가운데 홈
                            col = mix(col, hx("#3A1A70"), 0.6)
                        g = math.exp(-((u - sweep) ** 2) / 6.0)
                        if g > 0.05:
                            col = mix(col, (255, 255, 255), g * (0.75 if e > 0.5 else 0.45))
                elif 7.6 <= u < 9.5 and abs(v) <= 7.2:
                    # 코등이: 독수리 날개(양 끝이 칼끝 쪽으로 휘어 오른다)
                    curl = (abs(v) / 7.2) ** 2 * 2.2
                    if 7.6 + curl - 0.2 <= u <= 9.5 + curl * 0.6:
                        k = (u - 7.6) / 2.0
                        col = mix(gold[0], gold[2], k)
                        if abs(v) > 5.6:
                            col = mix(col, gold[3], 0.35)
                        if int(abs(v) * 1.3) % 2 == 0 and abs(v) > 1.6:
                            col = mix(col, gold[1], 0.5)  # 깃털 결
                elif 2.6 <= u < 7.6 and abs(v) <= 1.25:
                    # 손잡이: 짙은 보라 감개, 비스듬한 결
                    col = hx("#4A0A78") if int((u + v * 1.5) * 1.2) % 2 == 0 else hx("#2A0548")
                    if v < -0.5:
                        col = mix(col, hx("#8A3CC8"), 0.35)
                elif 0.4 <= u < 2.6 and math.hypot(u - 1.5, v) <= 1.9:
                    col = mix(gold[1], gold[3], (math.hypot(u - 1.5, v) / 1.9))
                if col:
                    put(img, x, y, col)
        outline(img, hx("#0A0418"))
        # 칼날 속을 흐르는 별
        for (u0, v0, scol, p) in specks:
            u = 10 + ((u0 - 10 + t * (L - 10) * 1.0) % (L - 11))
            x = p0[0] + ux * u + vx * v0
            y = p0[1] + uy * u + vy * v0
            tw = 0.5 + 0.5 * math.sin((t * 2 + p) * 2 * math.pi)
            blend(img, int(x), int(y), mix(hx(scol), (255, 255, 255), 0.4), int(140 + 115 * tw))
        # 코등이 가운데 알타이르 보석
        gx, gy = p0[0] + ux * 8.5, p0[1] + uy * 8.5
        flare(img, int(gx), int(gy), 1 + int(1.5 + 1.5 * math.sin(t * 2 * math.pi)), hx("#BFD7FF"))
        # 폼멜의 분홍 보석
        put(img, int(p0[0] + ux * 1.5), int(p0[1] + uy * 1.5), hx("#FFB4CC"))
        # 칼끝의 별: 크게 맥동하는 십자 광채
        pulse = 0.5 + 0.5 * math.sin(t * 2 * math.pi * 2)
        flare(img, int(p1[0]) - 1, int(p1[1]) + 1, 2 + int(pulse * 3), hx("#C9A6FF"), diag=1 + int(pulse * 2))
        frames.append(img)
    return frames


# ───────────── 발전과제 탭 배경: 이어 붙여도 이음매가 없는 512x512 우주 + 바닐라용 16x16 타일 ─────────────

def periodic_noise(seed, size, cells):
    r = random.Random(seed)
    g = [[r.random() for _ in range(cells)] for _ in range(cells)]

    def f(x, y):
        fx, fy = x / size * cells, y / size * cells
        x0, y0 = int(fx) % cells, int(fy) % cells
        tx, ty = fx - int(fx), fy - int(fy)
        tx = tx * tx * (3 - 2 * tx); ty = ty * ty * (3 - 2 * ty)
        a = g[y0][x0]; b = g[y0][(x0 + 1) % cells]; c = g[(y0 + 1) % cells][x0]; d = g[(y0 + 1) % cells][(x0 + 1) % cells]
        return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty
    return f


def space(size=512, seed=3):
    octaves = [periodic_noise(seed + i, size, c) for i, c in enumerate((4, 8, 16, 32))]
    band = periodic_noise(seed + 9, size, 4)
    img = Image.new("RGBA", (size, size), (0, 0, 0, 255))
    pix = img.load()
    for y in range(size):
        for x in range(size):
            n = sum(o(x, y) * w for o, w in zip(octaves, (0.5, 0.27, 0.15, 0.08)))
            # 비스듬한 은하수 띠(주기적이도록 대각선 좌표를 크기로 감는다)
            b = band((x + y) % size, (x - y) % size)
            m = max(0.0, n - 0.48) * 2.6
            col = mix(hx("#03020A"), hx("#0B0726"), n)
            col = mix(col, hx("#3A1366"), m * 0.9)
            col = mix(col, hx("#6A1B5A"), max(0.0, m - 0.45) * 0.8)
            col = mix(col, hx("#0E3A5A"), max(0.0, b - 0.62) * 1.6)
            pix[x, y] = (*col, 255)
    rnd = random.Random(seed * 7)
    for _ in range(2600):
        x, y = rnd.randrange(size), rnd.randrange(size)
        br = rnd.random() ** 3
        c = mix((150, 160, 210), (255, 255, 255), br)
        if rnd.random() < 0.12:
            c = mix(c, hx(rnd.choice([s[3] for s in STARS])), 0.6)
        r0, g0, b0, _ = pix[x, y]
        a = 0.35 + 0.65 * br
        pix[x, y] = (int(r0 + (c[0] - r0) * a), int(g0 + (c[1] - g0) * a), int(b0 + (c[2] - b0) * a), 255)
    for _ in range(40):  # 큰 별: 십자 광채
        x, y = rnd.randrange(size), rnd.randrange(size)
        c = hx(rnd.choice([s[3] for s in STARS]))
        L = rnd.randint(2, 5)
        for d in range(-L, L + 1):
            a = 1 - abs(d) / (L + 1)
            for (xx, yy) in (((x + d) % size, y), (x, (y + d) % size)):
                r0, g0, b0, _ = pix[xx, yy]
                cc = mix(c, (255, 255, 255), a)
                pix[xx, yy] = (int(r0 + (cc[0] - r0) * a), int(g0 + (cc[1] - g0) * a), int(b0 + (cc[2] - b0) * a), 255)
    return img


def tile16():
    img = Image.new("RGBA", (16, 16), (5, 3, 16, 255))
    rnd = random.Random(5)
    for _ in range(7):
        x, y = rnd.randrange(16), rnd.randrange(16)
        img.putpixel((x, y), (*mix((120, 130, 200), (255, 255, 255), rnd.random()), 255))
    return img


def main():
    for i, (k, _, _, col) in enumerate(STARS):
        save_anim(shard(k, col, 100 + i), f"item/star_{k}")
    save_anim(core_frames(), "item/aquila_core", 2)
    save_anim(weapon_frames(), "item/astra_obliterator", 2)
    bg = os.path.join(TEX, "gui", "advancements", "backgrounds")
    os.makedirs(bg, exist_ok=True)
    space().save(os.path.join(bg, "astra_space.png"))
    tile16().save(os.path.join(bg, "astra.png"))
    with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "astra_gradients.json"), "w", encoding="utf-8") as f:
        json.dump(gradient_pairs(), f, ensure_ascii=False, indent=1)
    print("astra textures written")


if __name__ == "__main__":
    main()
