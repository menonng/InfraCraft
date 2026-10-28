"""docs/ITEMS.md 생성기: 모든 InfraCraft 아이템의 조합법(그림) 또는 획득처.

사용법: python3 tools/recipes/items_doc.py
 - 이름: tools/lang.json (한국어)
 - 조합법: versions/26.2/.../recipe/*.json, 그림: docs/recipes/<이름>.png (recipe-images 워크플로가 만든다)
 - 구조물 상자: common/kotlin/.../system/Loot.kt 의 표를 그대로 읽는다
"""
import json
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.normpath(os.path.join(HERE, "..", ".."))
RECIPES = os.path.join(REPO, "versions", "26.2", "src", "main", "resources", "data", "infracraft", "recipe")
LANG = json.load(open(os.path.join(REPO, "tools", "lang.json"), encoding="utf-8"))
LOOT_KT = open(os.path.join(REPO, "common", "kotlin", "dev", "menonng", "infracraft", "system", "Loot.kt"), encoding="utf-8").read()

# 재료 이름(바닐라 한국어 공식 명칭 / Create 한국어 번역)
VANILLA_KO = {
    "gravel": "자갈", "sand": "모래", "#sand": "모래", "#coals": "석탄", "white_dye": "흰색 염료", "gold_nugget": "금 조각",
    "lantern": "랜턴", "iron_bars": "철창", "#planks": "판자", "stick": "막대기", "iron_ingot": "철 주괴", "gold_ingot": "금 주괴",
    "redstone": "레드스톤 가루", "blaze_rod": "블레이즈 막대기", "compass": "나침반", "smooth_stone": "매끄러운 돌",
    "comparator": "레드스톤 비교기", "prismarine": "프리즈머린", "kelp": "켈프", "prismarine_bricks": "프리즈머린 벽돌",
    "soul_sand": "영혼 모래", "hay_block": "건초 더미", "paper": "종이", "rail": "레일", "iron_block": "철 블록",
    "diamond": "다이아몬드", "piston": "피스톤", "amethyst_shard": "자수정 조각", "copper_ingot": "구리 주괴", "string": "실",
    "popped_chorus_fruit": "튀긴 후렴과", "ender_eye": "엔더의 눈", "redstone_block": "레드스톤 블록",
    "#stone_crafting_materials": "돌 종류", "clay_ball": "점토 덩이", "iron_nugget": "철 조각", "glass_pane": "유리판",
    "glowstone_dust": "발광석 가루", "yellow_dye": "노란색 염료", "blue_dye": "파란색 염료", "light_blue_dye": "하늘색 염료",
    "red_dye": "빨간색 염료", "spruce_slab": "가문비나무 반 블록", "spruce_planks": "가문비나무 판자", "book": "책", "polished_granite": "윤나는 화강암", "cobblestone": "조약돌", "bricks": "벽돌", "moss_block": "이끼 블록", "stone_bricks": "석재 벽돌",
}
CREATE_KO = {"railway_casing": "기차 케이스"}

LOOT_KO = {
    "VILLAGE_WEAPONSMITH": "마을 무기 대장장이 상자", "VILLAGE_TOOLSMITH": "마을 도구 대장장이 상자",
    "DESERT_PYRAMID": "사막 피라미드 상자", "SHIPWRECK_MAP": "난파선 지도 상자", "SHIPWRECK_SUPPLY": "난파선 보급품 상자",
    "UNDERWATER_RUIN_BIG": "큰 해저 유적 상자", "VILLAGE_FISHER": "마을 어부 집 상자",
}

# 아이템별 기타 획득처(조합·상자 외)
OTHER = {
    "trading_post": "**항구 설계도**를 쓰면 잔교 입구에 설치돼요. 부수면 아이템으로 회수해 옮길 수 있어요.",
    "cargo_crate": "**교역소**에 그 항구의 특산품을 들고 쓰면 받아요(최대 64개를 담아요).",
}

CATEGORIES = [
    ("도로", ["setts", "brick_paving", "flagstone", "asphalt", "road_line", "milestone", "street_lamp", "signpost", "road_paver"]),
    ("철도", ["high_speed_rail", "express_rail", "blaze_rail", "ender_rail_core", "station", "rail_switch", "ticket", "track_layer", "surveyor"]),
    ("지하철", ["tunnel_borer", "subway_shield", "tunnel_lining", "tunnel_light", "platform_edge", "screen_door", "station_elevator", "subway_guide"]),
    ("역사 설계도", ["station_blueprint", "ground_station_blueprint", "harbor_blueprint"]),
    ("차량 도면 (Create)", ["metro_car_blueprint", "coach_blueprint"]),
    ("수운·항구", ["water_current", "boat_lift", "trading_post", "cargo_crate"]),
    ("말", ["stable", "whistle", "iron_horseshoe", "diamond_horseshoe"]),
]

def name(item):
    if item == "subway_guide":
        return "지하철 건설 안내서"
    for k in (f"block.infracraft.{item}", f"item.infracraft.{item}"):
        if k in LANG:
            return LANG[k]["ko_kr"]
    return item

def en_name(item):
    if item == "subway_guide":
        return "Subway Construction Guide"
    for k in (f"block.infracraft.{item}", f"item.infracraft.{item}"):
        if k in LANG:
            return LANG[k]["en_us"]
    return item

def ing_name(ref):
    tag = ref.startswith("#")
    ns, _, path = ref.lstrip("#").partition(":")
    if ns == "infracraft":
        return name(path)
    if ns == "create":
        return CREATE_KO.get(path, path) + "(Create)"
    return VANILLA_KO.get(("#" if tag else "") + path, path)

def ingredients(recipe):
    counts = {}
    if recipe["type"].endswith("shaped"):
        for row in recipe["pattern"]:
            for ch in row:
                if ch != " ":
                    ref = recipe["key"][ch]
                    counts[ref] = counts.get(ref, 0) + 1
    else:
        for ref in recipe["ingredients"]:
            counts[ref] = counts.get(ref, 0) + 1
    return " + ".join(f"{ing_name(r)} {n}" for r, n in counts.items())

def loot_sources():
    out = {}
    for table, item, pct in re.findall(r"BuiltInLootTables\.(\w+) to \(ModItems\.(\w+) to (\d+)\)", LOOT_KT):
        out.setdefault(item.lower(), []).append((LOOT_KO.get(table, table), int(pct)))
    return out

def main():
    loot = loot_sources()
    lines = ["# 아이템 조합법·획득처", "",
             "InfraCraft 의 모든 아이템은 **조합하거나, 또는 다른 곳에서 얻어요.** 조합할 수 있는 아이템은 조합대 그림을 함께 실었어요.",
             "조합법이 없는 아이템은 구조물 상자나 교역, 설계도로 얻어요. 게임 안에서는 레시피 책(또는 JEI·EMI)으로도 볼 수 있어요.", "",
             "| 얻는 법 | 아이템 |", "|---|---|"]
    crafted, found = [], []
    for _, items in CATEGORIES:
        for it in items:
            (crafted if os.path.exists(os.path.join(RECIPES, f"{it}.json")) else found).append(name(it))
    lines += [f"| 조합 ({len(crafted)}) | " + ", ".join(crafted) + " |", f"| 획득 ({len(found)}) | " + ", ".join(found) + " |", ""]
    lines += ["> 그림은 `recipe-images` 워크플로가 게임 jar 의 텍스처로 그려요(`tools/recipes/render_recipes.py`). "
              "이 문서는 `python3 tools/recipes/items_doc.py` 로 다시 만들어요.", ""]
    for cat, items in CATEGORIES:
        lines += [f"## {cat}", ""]
        for it in items:
            icon = f"recipes/icons/{it}.png"
            head = f"### <img src=\"{icon}\" width=\"32\" height=\"32\" align=\"top\"> {name(it)}" if it != "subway_guide" else f"### {name(it)}"
            lines += [head, f"<sub>{en_name(it)} · `infracraft:{it}`</sub>" if it != "subway_guide" else "<sub>완성된 책(바닐라 아이템)</sub>", ""]
            path = os.path.join(RECIPES, f"{it}.json")
            if os.path.exists(path):
                recipe = json.load(open(path, encoding="utf-8"))
                n = recipe["result"].get("count", 1)
                cond = "fabric:load_conditions" in recipe
                lines += [f"**조합** → {n}개" + (" · *Create 가 있을 때만*" if cond else ""), "",
                          f"![{name(it)} 조합법](recipes/{it}.png)", "", f"재료: {ingredients(recipe)}", ""]
            else:
                lines += ["**획득**", ""]
                for where, pct in loot.get(it, []):
                    lines.append(f"- {where}: {pct}%")
                if it in OTHER:
                    lines.append(f"- {OTHER[it]}")
                lines.append("")
    with open(os.path.join(REPO, "docs", "ITEMS.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines).rstrip() + "\n")
    print("docs/ITEMS.md written:", len(crafted), "crafted,", len(found), "found")

if __name__ == "__main__":
    main()
