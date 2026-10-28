"""조합법 그림 생성기: 조합대 모양 그림(3×3 + 결과)과 아이템 아이콘을 PNG 로 만든다.

사용법(CI 에서 실행):
  python3 tools/recipes/render_recipes.py --jar <마인크래프트 jar> [--jar <Create jar>] \
      --mod common/resources --recipes versions/26.2/src/main/resources/data/infracraft/recipe --out <출력 폴더>

텍스처는 jar 와 모드 리소스에서 그때그때 읽고, 결과 그림만 저장한다(바닐라 텍스처 파일 자체는 저장소에 넣지 않는다).
블록 아이템은 모델의 elements 를 인벤토리처럼 비스듬히(위·북·동 면) 그리고, 평면 아이템은 layer 텍스처를 그린다.
"""
import argparse
import io
import json
import math
import os
import zipfile

from PIL import Image, ImageDraw

# ───────────── 리소스 읽기 ─────────────

class Res:
    def __init__(self, jars, mod_dirs):
        self.zips = [zipfile.ZipFile(j) for j in jars]
        self.dirs = mod_dirs
        self.cache = {}

    def read(self, path):
        for d in self.dirs:
            full = os.path.join(d, path)
            if os.path.exists(full):
                with open(full, "rb") as f:
                    return f.read()
        for z in self.zips:
            try:
                return z.read(path)
            except KeyError:
                pass
        return None

    def json(self, path):
        b = self.read(path)
        return json.loads(b) if b else None

    def texture(self, ref):
        if ref in self.cache:
            return self.cache[ref]
        ns, path = split_id(ref)
        b = self.read(f"assets/{ns}/textures/{path}.png")
        if b is None:
            img = missing()
        else:
            img = Image.open(io.BytesIO(b)).convert("RGBA")
            if img.height > img.width:  # 움직이는 텍스처: 첫 프레임
                img = img.crop((0, 0, img.width, img.width))
            if img.width != 16:
                img = img.resize((16, 16), Image.NEAREST)
        self.cache[ref] = img
        return img

def split_id(ref, default_ns="minecraft"):
    ref = ref.lstrip("#")
    if ":" in ref:
        ns, path = ref.split(":", 1)
    else:
        ns, path = default_ns, ref
    return ns, path

def missing():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    for y in range(16):
        for x in range(16):
            if (x // 8 + y // 8) % 2 == 0:
                img.putpixel((x, y), (248, 0, 248, 255))
    return img

# ───────────── 모델 해석 ─────────────

def item_model_ref(res, item_id):
    ns, path = split_id(item_id)
    d = res.json(f"assets/{ns}/items/{path}.json")
    if d:
        m = d.get("model", {})
        # select/condition/range_dispatch 는 기본(fallback) 또는 첫 모델
        while isinstance(m, dict) and m.get("type") != "minecraft:model":
            nxt = m.get("fallback") or m.get("on_false") or (m.get("cases") or [{}])[0].get("model") or m.get("model")
            if not nxt:
                break
            m = nxt
        if isinstance(m, dict) and "model" in m and isinstance(m["model"], str):
            return m["model"]
    return f"{ns}:item/{path}"

def resolve_model(res, ref):
    """부모를 따라가며 textures 를 합치고, 처음 나오는 elements 와 생성(평면) 여부를 돌려준다."""
    textures, elements, generated = {}, None, False
    seen = 0
    while ref and seen < 16:
        seen += 1
        if ref in ("builtin/generated", "minecraft:builtin/generated"):
            generated = True
            break
        ns, path = split_id(ref)
        m = res.json(f"assets/{ns}/models/{path}.json")
        if m is None:
            break
        for k, v in m.get("textures", {}).items():
            textures.setdefault(k, v)
        if elements is None and "elements" in m:
            elements = m["elements"]
        ref = m.get("parent")
        if ref and split_id(ref)[1] in ("item/generated", "item/handheld", "item/handheld_rod"):
            generated = True
            break
    return textures, elements, generated

def tex_lookup(textures, key):
    v = key
    for _ in range(10):
        if not v.startswith("#"):
            return v
        v = textures.get(v[1:], "")
    return None

# ───────────── 아이콘 그리기 ─────────────

S = 4  # 1 텍셀 = 4 픽셀(아이콘 64px)

def flat_icon(res, textures):
    out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for i in range(5):
        t = textures.get(f"layer{i}")
        if not t:
            continue
        ref = tex_lookup(textures, t) if t.startswith("#") else t
        if ref:
            out.alpha_composite(res.texture(ref))
    return out.resize((16 * S, 16 * S), Image.NEAREST)

COS30, SIN30 = math.cos(math.radians(30)), math.sin(math.radians(30))
R2 = math.sqrt(0.5)

def project(p):
    x, y, z = p[0] - 8, p[1] - 8, p[2] - 8
    sx = R2 * (x + z)
    fwd = R2 * (-x + z)
    sy = -y * COS30 - fwd * SIN30
    return sx, sy

def depth(p):
    x, y, z = p[0] - 8, p[1] - 8, p[2] - 8
    return R2 * (-x + z) * COS30 - y * SIN30

FACE_SHADE = {"up": 1.0, "north": 0.8, "east": 0.6}

def face_quad(name, f, t):
    """면의 네 꼭짓점: (u0,v0)→(u1,v0)→(u1,v1)→(u0,v1) 순서에 맞춘다."""
    (x0, y0, z0), (x1, y1, z1) = f, t
    if name == "up":      # 위에서 보면 u→+x, v→+z
        return [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)]
    if name == "north":   # 북쪽에서 보면 u→-x, v→-y
        return [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)]
    if name == "east":    # 동쪽에서 보면 u→-z, v→-y
        return [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)]
    return None

def default_uv(name, f, t):
    (x0, y0, z0), (x1, y1, z1) = f, t
    if name == "up":
        return [x0, z0, x1, z1]
    if name == "north":
        return [16 - x1, 16 - y1, 16 - x0, 16 - y0]
    if name == "east":
        return [16 - z1, 16 - y1, 16 - z0, 16 - y0]

def block_icon(res, textures, elements):
    size = 16 * S
    scale = size / 26.0  # 블록 하나(대각선 약 22.6)가 아이콘 안에 들어가도록
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    faces = []
    for el in elements:
        if el.get("rotation") and el["rotation"].get("angle", 0) not in (0, 0.0):
            continue  # 기울어진 장식 요소는 생략
        f, t = el["from"], el["to"]
        for name in ("up", "north", "east"):
            fd = el.get("faces", {}).get(name)
            if not fd:
                continue
            quad = face_quad(name, f, t)
            cx = sum(p[0] for p in quad) / 4; cy = sum(p[1] for p in quad) / 4; cz = sum(p[2] for p in quad) / 4
            faces.append((depth((cx, cy, cz)), name, quad, fd, f, t))
    faces.sort(key=lambda x: -x[0])
    for _, name, quad, fd, f, t in faces:
        ref = tex_lookup(textures, fd.get("texture", ""))
        tex = res.texture(ref) if ref else missing()
        u0, v0, u1, v1 = fd.get("uv") or default_uv(name, f, t)
        rot = fd.get("rotation", 0)
        shade = FACE_SHADE[name]
        nu = max(1, round(abs(u1 - u0))); nv = max(1, round(abs(v1 - v0)))
        P0, P1, P3 = quad[0], quad[1], quad[3]
        for j in range(nv):
            for i in range(nu):
                a, b = i / nu, j / nv
                a2, b2 = (i + 1) / nu, (j + 1) / nv
                # 텍스처 좌표(회전 반영)
                ua, va = a, b
                for _ in range(rot // 90):
                    ua, va = va, 1 - ua
                tu = int(min(15, max(0, u0 + (u1 - u0) * (ua + 0.5 / nu))))
                tv = int(min(15, max(0, v0 + (v1 - v0) * (va + 0.5 / nv))))
                c = tex.getpixel((tu, tv))
                if c[3] < 20:
                    continue
                c = (int(c[0] * shade), int(c[1] * shade), int(c[2] * shade), 255 if c[3] > 200 else c[3])
                pts = []
                for (aa, bb) in ((a, b), (a2, b), (a2, b2), (a, b2)):
                    p = [P0[k] + (P1[k] - P0[k]) * aa + (P3[k] - P0[k]) * bb for k in range(3)]
                    sx, sy = project(p)
                    pts.append((size / 2 + sx * scale, size / 2 + sy * scale + scale * 0.5))
                draw.polygon(pts, fill=c)
    return img

def icon(res, item_id):
    textures, elements, generated = resolve_model(res, item_model_ref(res, item_id))
    if generated or not elements:
        if textures.get("layer0"):
            return flat_icon(res, textures)
        if elements is None and not generated:
            return missing().resize((16 * S, 16 * S), Image.NEAREST)
    return block_icon(res, textures, elements)

# ───────────── 조합대 그림 ─────────────

TAG_PICK = {
    "minecraft:sand": "minecraft:sand", "minecraft:coals": "minecraft:coal", "minecraft:planks": "minecraft:oak_planks",
    "minecraft:stone_crafting_materials": "minecraft:cobblestone", "minecraft:wool": "minecraft:white_wool",
    "minecraft:logs": "minecraft:oak_log",
}

def ingredient_item(res, ing):
    if isinstance(ing, dict):
        ing = ("#" + ing["tag"]) if "tag" in ing else ing.get("item")
    if isinstance(ing, list):
        ing = ing[0]
    if ing.startswith("#"):
        tag = ing[1:]
        if tag in TAG_PICK:
            return TAG_PICK[tag]
        ns, path = split_id(tag)
        d = res.json(f"data/{ns}/tags/item/{path}.json")
        if d and d.get("values"):
            v = d["values"][0]
            v = v["id"] if isinstance(v, dict) else v
            return ingredient_item(res, v)
        return tag
    return ing

PX = 3  # GUI 1픽셀 = 3픽셀
BG, LIGHT, DARK, SLOT = (198, 198, 198), (255, 255, 255), (85, 85, 85), (139, 139, 139)

DIGITS = {  # 3×5 숫자
    "0": ["111", "101", "101", "101", "111"], "1": ["010", "110", "010", "010", "111"], "2": ["111", "001", "111", "100", "111"],
    "3": ["111", "001", "111", "001", "111"], "4": ["101", "101", "111", "001", "001"], "5": ["111", "100", "111", "001", "111"],
    "6": ["111", "100", "111", "101", "111"], "7": ["111", "001", "010", "010", "010"], "8": ["111", "101", "111", "101", "111"],
    "9": ["111", "101", "111", "001", "111"],
}

def bevel(d, x0, y0, x1, y1, fill, tl, br, w=PX):
    d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=fill)
    d.rectangle([x0, y0, x1 - 1, y0 + w - 1], fill=tl); d.rectangle([x0, y0, x0 + w - 1, y1 - 1], fill=tl)
    d.rectangle([x0, y1 - w, x1 - 1, y1 - 1], fill=br); d.rectangle([x1 - w, y0, x1 - 1, y1 - 1], fill=br)

def slot(img, d, x, y, size, item_img):
    bevel(d, x, y, x + size * PX, y + size * PX, SLOT, (55, 55, 55), LIGHT)
    if item_img is not None:
        inner = 16 * PX
        it = item_img.resize((inner, inner), Image.NEAREST)
        off = (size * PX - inner) // 2
        img.alpha_composite(it, (x + off, y + off))

def count_text(d, x, y, n):
    s = str(n)
    u = PX
    cx = x - len(s) * 4 * u - u
    for ch in s:
        for r, row in enumerate(DIGITS[ch]):
            for c, bit in enumerate(row):
                if bit == "1":
                    d.rectangle([cx + (c + 1) * u, y + (r + 1) * u, cx + (c + 2) * u - 1, y + (r + 2) * u - 1], fill=(63, 63, 63))
                    d.rectangle([cx + c * u, y + r * u, cx + (c + 1) * u - 1, y + (r + 1) * u - 1], fill=(255, 255, 255))
        cx += 4 * u

def recipe_image(res, recipe):
    grid = [[None] * 3 for _ in range(3)]
    if recipe["type"].endswith("crafting_shaped"):
        for r, row in enumerate(recipe["pattern"]):
            for c, ch in enumerate(row):
                if ch != " ":
                    grid[r][c] = ingredient_item(res, recipe["key"][ch])
    else:
        for i, ing in enumerate(recipe["ingredients"]):
            grid[i // 3][i % 3] = ingredient_item(res, ing)
    result = recipe["result"]
    W, H = (7 + 54 + 6 + 22 + 6 + 26 + 7) * PX, (7 + 54 + 7) * PX
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    bevel(d, 0, 0, W, H, BG, LIGHT, DARK, 2 * PX)
    icons = {}
    for r in range(3):
        for c in range(3):
            it = grid[r][c]
            if it and it not in icons:
                icons[it] = icon(res, it)
            slot(img, d, (7 + c * 18) * PX, (7 + r * 18) * PX, 18, icons.get(it) if it else None)
    # 화살표
    ax, ay = (7 + 54 + 6) * PX, (7 + 27 - 4) * PX
    d.rectangle([ax, ay + 2 * PX, ax + 15 * PX, ay + 6 * PX - 1], fill=SLOT)
    d.polygon([(ax + 14 * PX, ay - 2 * PX), (ax + 22 * PX, ay + 4 * PX), (ax + 14 * PX, ay + 10 * PX)], fill=SLOT)
    rx, ry = (7 + 54 + 6 + 22 + 6) * PX, (7 + 27 - 13) * PX
    slot(img, d, rx, ry, 26, icon(res, result["id"]))
    n = result.get("count", 1)
    if n > 1:
        count_text(d, rx + 23 * PX, ry + 16 * PX, n)
    return img

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--jar", action="append", default=[])
    ap.add_argument("--mod", action="append", default=[])
    ap.add_argument("--recipes", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--icons", nargs="*", default=[])
    a = ap.parse_args()
    res = Res(a.jar, a.mod)
    os.makedirs(os.path.join(a.out, "recipes"), exist_ok=True)
    os.makedirs(os.path.join(a.out, "icons"), exist_ok=True)
    for fn in sorted(os.listdir(a.recipes)):
        if not fn.endswith(".json"):
            continue
        with open(os.path.join(a.recipes, fn), encoding="utf-8") as f:
            recipe = json.load(f)
        recipe_image(res, recipe).save(os.path.join(a.out, "recipes", fn[:-5] + ".png"))
        print("recipe", fn)
    for item in a.icons:
        icon(res, item).save(os.path.join(a.out, "icons", split_id(item)[1] + ".png"))
        print("icon", item)

if __name__ == "__main__":
    main()
