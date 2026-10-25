"""InfraCraft 데이터/에셋 JSON 생성기.

사용법: python3 tools/gen_data.py
 - 공통(모든 버전): common/resources 아래 모델·블록 상태·루트 테이블·태그·인챈트·번역
 - 레시피: 1.21.1 은 옛 재료 형식, 1.21.11/26.3 은 새 문자열 형식
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.join(HERE, "..")
COMMON = os.path.join(REPO, "common", "resources")
NS = "infracraft"

def write(base, path, obj):
    full = os.path.join(base, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")

A = os.path.join(COMMON, "assets", NS)
D = os.path.join(COMMON, "data")

CUBE_ALL = ["asphalt", "road_line", "street_lamp", "signpost"]
RAILS = ["high_speed_rail", "express_rail", "blaze_rail"]
ITEMS = ["ticket", "coupler", "road_paver", "track_layer", "tunnel_borer", "surveyor", "whistle",
         "iron_horseshoe", "diamond_horseshoe", "ender_rail_core"]
BLOCKS = CUBE_ALL + RAILS + ["station", "rail_switch", "water_current", "boat_lift", "stable"]

def item_def(name, model):
    # 1.21.4+ 아이템 정의(1.21.1 은 이 폴더를 무시한다)
    write(A, f"items/{name}.json", {"model": {"type": "minecraft:model", "model": model}})

# ── 블록 모델·상태 ──
for b in CUBE_ALL:
    write(A, f"models/block/{b}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{b}"}})
    write(A, f"blockstates/{b}.json", {"variants": {"": {"model": f"{NS}:block/{b}"}}})

write(A, "models/block/station.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {
    "top": f"{NS}:block/station_side", "bottom": f"{NS}:block/station_side", "side": f"{NS}:block/station"}})
write(A, "blockstates/station.json", {"variants": {"": {"model": f"{NS}:block/station"}}})

for sfx in ("", "_on"):
    write(A, f"models/block/rail_switch{sfx}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/rail_switch{sfx}"}})
write(A, "blockstates/rail_switch.json", {"variants": {
    "powered=false": {"model": f"{NS}:block/rail_switch"}, "powered=true": {"model": f"{NS}:block/rail_switch_on"}}})

for b in ("water_current", "boat_lift"):
    write(A, f"models/block/{b}.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": f"{NS}:block/{b}_top", "bottom": f"{NS}:block/{b}_side", "side": f"{NS}:block/{b}_side"}})
write(A, "blockstates/boat_lift.json", {"variants": {"": {"model": f"{NS}:block/boat_lift"}}})
write(A, "blockstates/water_current.json", {"variants": {
    f"facing={f}": ({"model": f"{NS}:block/water_current", "y": y} if y else {"model": f"{NS}:block/water_current"})
    for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})

write(A, "models/block/stable.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {
    "top": f"{NS}:block/stable_top", "bottom": f"{NS}:block/stable_side", "side": f"{NS}:block/stable_side"}})
write(A, "blockstates/stable.json", {"variants": {"": {"model": f"{NS}:block/stable"}}})

# 선로: 바닐라 전동 레일과 같은 모양 템플릿을 쓴다
for r in RAILS:
    for sfx in ("", "_on"):
        tex = f"{NS}:block/{r}{sfx}"
        write(A, f"models/block/{r}{sfx}.json", {"parent": "minecraft:block/rail_flat", "textures": {"rail": tex}})
        write(A, f"models/block/{r}_raised_ne{sfx}.json", {"parent": "minecraft:block/template_rail_raised_ne", "textures": {"rail": tex}})
        write(A, f"models/block/{r}_raised_sw{sfx}.json", {"parent": "minecraft:block/template_rail_raised_sw", "textures": {"rail": tex}})
    variants = {}
    for powered in ("false", "true"):
        sfx = "_on" if powered == "true" else ""
        m = lambda n: f"{NS}:block/{n}{sfx}"
        variants[f"powered={powered},shape=north_south"] = {"model": m(r)}
        variants[f"powered={powered},shape=east_west"] = {"model": m(r), "y": 90}
        variants[f"powered={powered},shape=ascending_north"] = {"model": f"{NS}:block/{r}_raised_ne{sfx}"}
        variants[f"powered={powered},shape=ascending_east"] = {"model": f"{NS}:block/{r}_raised_ne{sfx}", "y": 90}
        variants[f"powered={powered},shape=ascending_south"] = {"model": f"{NS}:block/{r}_raised_sw{sfx}"}
        variants[f"powered={powered},shape=ascending_west"] = {"model": f"{NS}:block/{r}_raised_sw{sfx}", "y": 90}
    write(A, f"blockstates/{r}.json", {"variants": variants})
    write(A, f"models/item/{r}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:block/{r}"}})
    item_def(r, f"{NS}:item/{r}")

for b in BLOCKS:
    if b in RAILS:
        continue
    write(A, f"models/item/{b}.json", {"parent": f"{NS}:block/{b}"})
    item_def(b, f"{NS}:item/{b}")

for i in ITEMS:
    parent = "minecraft:item/handheld" if i in ("road_paver", "track_layer", "tunnel_borer") else "minecraft:item/generated"
    write(A, f"models/item/{i}.json", {"parent": parent, "textures": {"layer0": f"{NS}:item/{i}"}})
    item_def(i, f"{NS}:item/{i}")

# ── 루트 테이블 ──
for b in BLOCKS:
    write(D, f"{NS}/loot_table/blocks/{b}.json", {"type": "minecraft:block", "pools": [{
        "rolls": 1, "bonus_rolls": 0,
        "entries": [{"type": "minecraft:item", "name": f"{NS}:{b}"}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        "random_sequence": f"{NS}:blocks/{b}"})

# ── 태그 ──
def tag(path, values):
    write(D, path, {"replace": False, "values": values})

mc = lambda *n: [f"minecraft:{x}" for x in n]
tag(f"{NS}/tags/block/road_tier_1.json", mc("dirt_path", "gravel", "coarse_dirt", "packed_mud", "rooted_dirt"))
tag(f"{NS}/tags/block/road_tier_2.json", mc(
    "cobblestone", "mossy_cobblestone", "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "smooth_stone",
    "polished_andesite", "polished_diorite", "polished_granite", "bricks", "mud_bricks",
    "sandstone", "smooth_sandstone", "cut_sandstone", "red_sandstone", "smooth_red_sandstone", "cut_red_sandstone",
    "cobbled_deepslate", "polished_deepslate", "deepslate_bricks", "deepslate_tiles", "tuff_bricks", "polished_tuff",
    "polished_blackstone", "polished_blackstone_bricks", "nether_bricks", "red_nether_bricks", "polished_basalt", "smooth_basalt",
    "end_stone_bricks", "purpur_block", "quartz_block", "smooth_quartz", "prismarine_bricks",
    "stone_brick_slab", "smooth_stone_slab", "cobblestone_slab", "brick_slab", "polished_andesite_slab",
    "sandstone_slab", "smooth_sandstone_slab", "cobbled_deepslate_slab", "deepslate_brick_slab", "deepslate_tile_slab",
    "polished_blackstone_brick_slab", "nether_brick_slab", "end_stone_brick_slab", "quartz_slab"))
tag(f"{NS}/tags/block/road_tier_3.json", [f"{NS}:asphalt", f"{NS}:road_line"])
tag(f"{NS}/tags/block/paveable.json", mc(
    "dirt", "grass_block", "coarse_dirt", "podzol", "mycelium", "rooted_dirt", "mud", "clay", "moss_block",
    "sand", "red_sand", "gravel", "stone", "andesite", "diorite", "granite", "tuff", "deepslate", "calcite",
    "dirt_path", "snow_block", "netherrack", "soul_sand", "soul_soil", "blackstone", "basalt", "end_stone")
    + [f"#{NS}:road_tier_1", f"#{NS}:road_tier_2", f"#{NS}:road_tier_3"])
tag("minecraft/tags/block/mineable/pickaxe.json", [f"{NS}:{b}" for b in
    ("asphalt", "road_line", "station", "rail_switch", "water_current", "boat_lift", *RAILS)])
tag("minecraft/tags/block/mineable/axe.json", [f"{NS}:signpost"])
tag("minecraft/tags/block/mineable/hoe.json", [f"{NS}:stable"])
tag("minecraft/tags/block/rails.json", [f"{NS}:{r}" for r in RAILS])
tag("minecraft/tags/item/rails.json", [f"{NS}:{r}" for r in RAILS])
TOOLS = [f"{NS}:road_paver", f"{NS}:track_layer", f"{NS}:tunnel_borer"]
tag(f"{NS}/tags/item/enchantable/road_paver.json", [f"{NS}:road_paver"])
tag(f"{NS}/tags/item/enchantable/tunnel_borer.json", [f"{NS}:tunnel_borer"])
tag(f"{NS}/tags/item/enchantable/construction.json", TOOLS)
tag("minecraft/tags/item/enchantable/durability.json", TOOLS)
tag("minecraft/tags/item/enchantable/mining.json", [f"{NS}:tunnel_borer"])

# ── 인챈트 ──
def ench(name, items, slots, max_level, weight, effects=None, primary=None):
    e = {
        "description": {"translate": f"enchantment.{NS}.{name}"},
        "supported_items": items,
        "weight": weight,
        "max_level": max_level,
        "min_cost": {"base": 10, "per_level_above_first": 10},
        "max_cost": {"base": 40, "per_level_above_first": 10},
        "anvil_cost": 4,
        "slots": slots,
    }
    if primary:
        e["primary_items"] = primary
    if effects:
        e["effects"] = effects
    write(D, f"{NS}/enchantment/{name}.json", e)

ench("roadrunner", "#minecraft:enchantable/foot_armor", ["feet"], 3, 3, effects={
    "minecraft:attributes": [{
        "id": f"{NS}:enchantment.roadrunner",
        "attribute": f"{NS}:road_affinity",
        "amount": {"type": "minecraft:linear", "base": 0.15, "per_level_above_first": 0.15},
        "operation": "add_value"}]})
ench("momentum", "#minecraft:enchantable/leg_armor", ["legs"], 2, 3)
ench("equestrian", "#minecraft:enchantable/leg_armor", ["legs"], 3, 3)
ench("conductor", "#minecraft:enchantable/head_armor", ["head"], 3, 2)
ench("wide_paving", f"#{NS}:enchantable/road_paver", ["mainhand"], 2, 3)
ench("bore_radius", f"#{NS}:enchantable/tunnel_borer", ["mainhand"], 2, 2)
ench("thrift", f"#{NS}:enchantable/construction", ["mainhand"], 3, 2)
ALL_ENCH = [f"{NS}:{n}" for n in ("roadrunner", "momentum", "equestrian", "conductor", "wide_paving", "bore_radius", "thrift")]
for t in ("in_enchanting_table", "non_treasure", "tradeable", "on_random_loot", "on_traded_equipment"):
    tag(f"minecraft/tags/enchantment/{t}.json", ALL_ENCH)

# ── 레시피 ──
RECIPES = [
    # (이름, 결과 수, 패턴, {키: 재료})  재료: "minecraft:x" 또는 "#minecraft:tag"
    ("asphalt", 8, ["GSG", "SCS", "GSG"], {"G": "minecraft:gravel", "S": "#minecraft:sand", "C": "#minecraft:coals"}),
    ("road_line", 4, [" W ", "AAA", " A "], {"W": "minecraft:white_dye", "A": f"{NS}:asphalt"}),
    ("street_lamp", 1, ["NLN", " I ", " I "], {"N": "minecraft:gold_nugget", "L": "minecraft:lantern", "I": "minecraft:iron_bars"}),
    ("signpost", 2, ["PPP", "PPP", " S "], {"P": "#minecraft:planks", "S": "minecraft:stick"}),
    ("high_speed_rail", 6, ["IGI", "ISI", "IRI"], {"I": "minecraft:iron_ingot", "G": "minecraft:gold_ingot", "S": "minecraft:stick", "R": "minecraft:redstone"}),
    ("express_rail", 16, ["G G", "GCG", "GRG"], {"G": "minecraft:gold_ingot", "C": f"{NS}:ender_rail_core", "R": "minecraft:redstone"}),
    ("blaze_rail", 6, ["IBI", "ISI", "IRI"], {"I": "minecraft:gold_ingot", "B": "minecraft:blaze_rod", "S": "minecraft:stick", "R": "minecraft:redstone"}),
    ("station", 1, ["GCG", "GRG", "SSS"], {"G": "minecraft:gold_ingot", "C": "minecraft:compass", "R": "minecraft:redstone", "S": "minecraft:smooth_stone"}),
    ("rail_switch", 1, ["RCR", "SSS"], {"R": "minecraft:redstone", "C": "minecraft:comparator", "S": "minecraft:smooth_stone"}),
    ("water_current", 4, ["PPP", "KRK", "PPP"], {"P": "minecraft:prismarine", "K": "minecraft:kelp", "R": "minecraft:redstone"}),
    ("boat_lift", 1, ["PPP", "SRS", "PPP"], {"P": "minecraft:prismarine_bricks", "S": "minecraft:soul_sand", "R": "minecraft:redstone"}),
    ("stable", 1, ["PHP", "H H", "PHP"], {"P": "#minecraft:planks", "H": "minecraft:hay_block"}),
    ("ticket", 8, ["PPP", "PNP", "PPP"], {"P": "minecraft:paper", "N": "minecraft:gold_nugget"}),
    ("coupler", 1, ["  I", " C ", "I  "], {"I": "minecraft:iron_ingot", "C": "CHAIN"}),
    ("road_paver", 1, ["III", "SIS", " S "], {"I": "minecraft:iron_ingot", "S": "minecraft:stick"}),
    ("track_layer", 1, ["RRR", "ISI", " S "], {"R": "minecraft:rail", "I": "minecraft:iron_ingot", "S": "minecraft:stick"}),
    ("tunnel_borer", 1, ["DID", "IPI", " S "], {"D": "minecraft:diamond", "I": "minecraft:iron_block", "P": "minecraft:piston", "S": "minecraft:stick"}),
    ("surveyor", 1, [" A ", "CSC", " S "], {"A": "minecraft:amethyst_shard", "C": "minecraft:copper_ingot", "S": "minecraft:stick"}),
    ("whistle", 1, [" GS"], {"G": "minecraft:gold_ingot", "S": "minecraft:string"}),
    ("iron_horseshoe", 2, ["I I", "I I", "III"], {"I": "minecraft:iron_ingot"}),
    ("diamond_horseshoe", 2, ["D D", "D D", "DDD"], {"D": "minecraft:diamond"}),
    ("ender_rail_core", 1, ["PEP", "ERE", "PEP"], {"P": "minecraft:popped_chorus_fruit", "E": "minecraft:ender_eye", "R": "minecraft:redstone_block"}),
]

def recipes(version_dir, new_format, chain_id):
    base = os.path.join(REPO, "versions", version_dir, "src", "main", "resources", "data", NS, "recipe")
    for name, count, pattern, key in RECIPES:
        k = {}
        for sym, ing in key.items():
            if ing == "CHAIN":
                ing = chain_id
            if new_format:
                k[sym] = ing
            else:
                k[sym] = {"tag": ing[1:]} if ing.startswith("#") else {"item": ing}
        obj = {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern, "key": k,
               "result": {"id": f"{NS}:{name}", "count": count}}
        full = os.path.join(base, f"{name}.json")
        os.makedirs(os.path.dirname(full), exist_ok=True)
        with open(full, "w", encoding="utf-8") as f:
            json.dump(obj, f, ensure_ascii=False, indent=2)
            f.write("\n")

recipes("1.21.1", False, "minecraft:chain")
recipes("1.21.11", True, "minecraft:iron_chain")
recipes("26.3", True, "minecraft:iron_chain")

# ── 번역 ──
with open(os.path.join(HERE, "lang.json"), encoding="utf-8") as f:
    LANG = json.load(f)
for code in ("ko_kr", "en_us"):
    write(A, f"lang/{code}.json", {k: v[code] for k, v in LANG.items()})
print("data written")
