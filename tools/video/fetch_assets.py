"""CI 전용: 마인크래프트·Create jar 에서 영상용 블록 텍스처를 뽑아 tools/video/assets-real 에 둔다(커밋하지 않음).

사용법: python3 tools/video/fetch_assets.py --mc <마인크래프트 jar> --create <Create Fly jar>
잔디·물·나뭇잎처럼 회색으로 들어 있는 텍스처는 게임 기본 색(평원 생물 군계)으로 물들이고, 움직이는 텍스처는 첫 프레임만 쓴다.
"""
import argparse
import io
import os
import zipfile

from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "assets-real")

# 영상 이름 → (jar, 경로, 물들일 색, 겹칠 경로, 불투명도)
MC = "mc"
CR = "create"
MAP = {
    "stone": (MC, "block/stone"), "deepslate": (MC, "block/deepslate"), "dirt": (MC, "block/dirt"),
    "grass_top": (MC, "block/grass_block_top", (124, 189, 107)),
    "grass_side": (MC, "block/grass_block_side", None, "block/grass_block_side_overlay"),
    "water": (MC, "block/water_still", (63, 118, 228), None, 190), "lava": (MC, "block/lava_still"),
    "rail": (MC, "block/rail"), "planks": (MC, "block/oak_planks"), "sandstone": (MC, "block/sandstone"),
    "snow": (MC, "block/snow"), "stone_bricks": (MC, "block/stone_bricks"), "smooth_stone": (MC, "block/smooth_stone"),
    "spruce": (MC, "block/spruce_planks"), "cobblestone": (MC, "block/cobblestone"), "gravel": (MC, "block/gravel"),
    "polished_granite": (MC, "block/polished_granite"), "bricks": (MC, "block/bricks"), "oak_log": (MC, "block/oak_log"),
    "spruce_log": (MC, "block/spruce_log"), "glass": (MC, "block/glass"), "gold_block": (MC, "block/gold_block"),
    "iron_block": (MC, "block/iron_block"), "redstone_block": (MC, "block/redstone_block"),
    "powered_rail_on": (MC, "block/powered_rail_on"), "netherrack": (MC, "block/netherrack"), "obsidian": (MC, "block/obsidian"),
    "portal": (MC, "block/nether_portal", None, None, 200), "soul_sand": (MC, "block/soul_sand"),
    "prismarine": (MC, "block/prismarine"), "hay": (MC, "block/hay_block_side"), "sand": (MC, "block/sand"),
    "snow_block": (MC, "block/snow"), "jungle_leaves": (MC, "block/jungle_leaves", (72, 181, 24)),
    "oak_leaves": (MC, "block/oak_leaves", (72, 181, 24)), "mossy_cobblestone": (MC, "block/mossy_cobblestone"),
    "car_body": (CR, "block/industrial_iron_block"), "andesite_casing": (CR, "block/andesite_casing"),
    "brass_casing": (CR, "block/brass_casing"), "railway_casing": (CR, "block/railway_casing"),
}

def load(z, ns, path):
    try:
        img = Image.open(io.BytesIO(z.read(f"assets/{ns}/textures/{path}.png"))).convert("RGBA")
    except KeyError:
        return None
    if img.height > img.width:
        img = img.crop((0, 0, img.width, img.width))
    return img.resize((16, 16), Image.NEAREST) if img.width != 16 else img

def tint(img, rgb):
    out = Image.new("RGBA", img.size)
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = img.getpixel((x, y))
            out.putpixel((x, y), (r * rgb[0] // 255, g * rgb[1] // 255, b * rgb[2] // 255, a))
    return out

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--mc", required=True)
    ap.add_argument("--create", required=True)
    a = ap.parse_args()
    jars = {MC: (zipfile.ZipFile(a.mc), "minecraft"), CR: (zipfile.ZipFile(a.create), "create")}
    os.makedirs(OUT, exist_ok=True)
    done = 0
    for name, spec in MAP.items():
        src, path = spec[0], spec[1]
        color = spec[2] if len(spec) > 2 else None
        overlay = spec[3] if len(spec) > 3 else None
        alpha = spec[4] if len(spec) > 4 else None
        z, ns = jars[src]
        img = load(z, ns, path)
        if img is None:
            print("missing", name, path)
            continue
        if color:
            img = tint(img, color)
        if overlay:
            ov = load(z, ns, overlay)
            if ov is not None:
                img.alpha_composite(tint(ov, (124, 189, 107)))
        if alpha is not None:
            img.putalpha(Image.eval(img.getchannel("A"), lambda v: min(v, alpha)))
        img.save(os.path.join(OUT, name + ".png"))
        done += 1
    # 차량 창·문: 산업용 철 판에 유리를 끼운 모양으로 합성
    body = load(jars[CR][0], "create", "block/industrial_iron_block")
    glass = load(jars[MC][0], "minecraft", "block/glass")
    if body is not None and glass is not None:
        win = body.copy()
        pane = Image.new("RGBA", (12, 8), (40, 62, 92, 255))
        pane.alpha_composite(glass.crop((2, 2, 14, 10)))
        win.alpha_composite(pane, (2, 2))
        win.save(os.path.join(OUT, "car_window.png"))
        door = body.copy()
        for y in range(16):
            for x in (7, 8):
                door.putpixel((x, y), (30, 32, 36, 255))
        small = Image.new("RGBA", (4, 6), (40, 62, 92, 255))
        door.alpha_composite(small, (2, 2)); door.alpha_composite(small, (10, 2))
        door.save(os.path.join(OUT, "car_door.png"))
        done += 2
    print(f"{done} textures -> {OUT}")

if __name__ == "__main__":
    main()
