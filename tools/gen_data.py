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

CUBE_ALL = ["setts", "brick_paving", "flagstone", "asphalt", "road_line", "street_lamp", "signpost", "tunnel_lining", "tunnel_light", "platform_edge"]
RAILS = ["high_speed_rail", "express_rail", "blaze_rail"]
ITEMS = ["ticket", "road_paver", "track_layer", "tunnel_borer", "surveyor", "whistle",
         "iron_horseshoe", "diamond_horseshoe", "ender_rail_core", "subway_shield", "station_blueprint",
         "ground_station_blueprint", "harbor_blueprint", "metro_car_blueprint", "coach_blueprint",
         "timber_bridge_kit", "stone_bridge_kit", "copper_bridge_kit",
         "aquila_core", "astra_obliterator"] + [f"star_{k}" for k in
         ("altair", "tarazed", "alshain", "deneb_el_okab", "bezek", "chesus", "libertas", "phonix", "petra")]
BLOCKS = CUBE_ALL + RAILS + ["station", "rail_switch", "water_current", "boat_lift", "stable", "station_elevator",
                             "screen_door", "milestone"]

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
write(A, "models/block/station_elevator.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {
    "top": f"{NS}:block/station_elevator_top", "bottom": f"{NS}:block/station_elevator_side", "side": f"{NS}:block/station_elevator_side"}})
write(A, "blockstates/station_elevator.json", {"variants": {"": {"model": f"{NS}:block/station_elevator"}}})

write(A, "models/block/milestone.json", {
    "parent": "minecraft:block/block",
    "textures": {"side": f"{NS}:block/milestone", "top": f"{NS}:block/milestone_top", "particle": f"{NS}:block/milestone"},
    "elements": [
        {"from": [4, 0, 4], "to": [12, 13, 12], "faces": {
            "north": {"uv": [4, 3, 12, 16], "texture": "#side"}, "south": {"uv": [4, 3, 12, 16], "texture": "#side"},
            "east": {"uv": [4, 3, 12, 16], "texture": "#side"}, "west": {"uv": [4, 3, 12, 16], "texture": "#side"},
            "down": {"texture": "#top", "cullface": "down"}}},
        {"from": [3, 13, 3], "to": [13, 16, 13], "faces": {
            f: {"uv": [3, 0, 13, 3], "texture": "#side"} for f in ("north", "south", "east", "west")} | {
            "up": {"texture": "#top"}, "down": {"texture": "#top"}}},
    ]})
write(A, "blockstates/milestone.json", {"variants": {"": {"model": f"{NS}:block/milestone"}}})

# 승강장 안전문: 북쪽(선로 쪽)을 향한 모델을 돌려 쓴다. 판은 북쪽 면 0..2 에 붙는다.
def cuboid(a, b, tex):
    return {"from": a, "to": b, "faces": {f: {"texture": tex} for f in ("north", "south", "east", "west", "up", "down")}}
POSTS = [cuboid([0, 0, 0], [2, 16, 2], "#frame"), cuboid([14, 0, 0], [16, 16, 2], "#frame")]
DOOR_PARTS = {
    "lower": POSTS + [cuboid([2, 0, 0], [14, 3, 2], "#frame"), cuboid([2, 3, 0.5], [14, 16, 1.5], "#glass")],
    "upper": POSTS + [cuboid([2, 12, 0], [14, 16, 2], "#frame"), cuboid([2, 0, 0.5], [14, 12, 1.5], "#glass")],
    "lower_open": POSTS,
    "upper_open": POSTS + [cuboid([2, 12, 0], [14, 16, 2], "#frame")],
}
for name, elements in DOOR_PARTS.items():
    write(A, f"models/block/screen_door_{name}.json", {
        "textures": {"frame": f"{NS}:block/screen_door_frame", "glass": f"{NS}:block/screen_door_glass",
                     "particle": f"{NS}:block/screen_door_frame"},
        "elements": elements})
door_variants = {}
for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
    for half in ("lower", "upper"):
        for open_ in ("false", "true"):
            v = {"model": f"{NS}:block/screen_door_{half}{'_open' if open_ == 'true' else ''}"}
            if y:
                v["y"] = y
            door_variants[f"facing={facing},half={half},open={open_}"] = v
write(A, "blockstates/screen_door.json", {"variants": door_variants})

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
    if b == "screen_door":
        write(A, "models/item/screen_door.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/screen_door"}})
        item_def(b, f"{NS}:item/{b}")
        continue
    write(A, f"models/item/{b}.json", {"parent": f"{NS}:block/{b}"})
    item_def(b, f"{NS}:item/{b}")

for i in ITEMS:
    parent = "minecraft:item/handheld" if i in ("road_paver", "track_layer", "tunnel_borer", "subway_shield", "astra_obliterator") else "minecraft:item/generated"
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
    "polished_blackstone_brick_slab", "nether_brick_slab", "end_stone_brick_slab", "quartz_slab")
    + [f"{NS}:tunnel_lining", f"{NS}:platform_edge"])
tag(f"{NS}/tags/block/road_tier_3.json", [f"{NS}:asphalt", f"{NS}:road_line", f"{NS}:setts", f"{NS}:brick_paving", f"{NS}:flagstone"])
tag(f"{NS}/tags/block/paveable.json", mc(
    "dirt", "grass_block", "coarse_dirt", "podzol", "mycelium", "rooted_dirt", "mud", "clay", "moss_block",
    "sand", "red_sand", "gravel", "stone", "andesite", "diorite", "granite", "tuff", "deepslate", "calcite",
    "dirt_path", "snow_block", "netherrack", "soul_sand", "soul_soil", "blackstone", "basalt", "end_stone")
    + [f"#{NS}:road_tier_1", f"#{NS}:road_tier_2", f"#{NS}:road_tier_3"])
tag("minecraft/tags/block/mineable/pickaxe.json", [f"{NS}:{b}" for b in
    ("asphalt", "road_line", "setts", "brick_paving", "flagstone", "milestone", "station", "rail_switch", "water_current", "boat_lift", "tunnel_lining", "platform_edge", "station_elevator",
     "screen_door", *RAILS)])
tag("minecraft/tags/block/mineable/axe.json", [f"{NS}:signpost"])
tag("minecraft/tags/block/mineable/hoe.json", [f"{NS}:stable"])
tag("minecraft/tags/block/rails.json", [f"{NS}:{r}" for r in RAILS])
tag("minecraft/tags/item/rails.json", [f"{NS}:{r}" for r in RAILS])
TOOLS = [f"{NS}:road_paver", f"{NS}:track_layer", f"{NS}:tunnel_borer", f"{NS}:subway_shield"]
KITS = [f"{NS}:timber_bridge_kit", f"{NS}:stone_bridge_kit", f"{NS}:copper_bridge_kit"]
tag(f"{NS}/tags/item/enchantable/road_paver.json", [f"{NS}:road_paver"])
tag(f"{NS}/tags/item/enchantable/tunnel_borer.json", [f"{NS}:tunnel_borer"])
tag(f"{NS}/tags/item/enchantable/construction.json", TOOLS)
tag("minecraft/tags/item/enchantable/durability.json", TOOLS + KITS)
tag("minecraft/tags/item/enchantable/mining.json", [f"{NS}:tunnel_borer"])

# Create(Create Fly) 장식 돌: Create 가 없으면 무시되는 선택 항목으로 넣는다
CREATE_STONES = ["andesite", "asurine", "calcite", "crimsite", "deepslate", "diorite", "dripstone", "granite",
                 "limestone", "ochrum", "scorchia", "scoria", "tuff", "veridium"]
def optional(ids):
    return [{"id": i, "required": False} for i in ids]
create_road = []
for st in CREATE_STONES:
    create_road += [f"create:cut_{st}", f"create:cut_{st}_slab", f"create:polished_cut_{st}", f"create:polished_cut_{st}_slab",
                    f"create:cut_{st}_bricks", f"create:small_{st}_bricks", f"create:layered_{st}", f"create:{st}_pillar"]
create_road.append("create:industrial_iron_block")
with open(os.path.join(D, NS, "tags", "block", "road_tier_2.json"), encoding="utf-8") as f:
    tier2 = json.load(f)
tier2["values"] += optional(create_road)
write(D, f"{NS}/tags/block/road_tier_2.json", tier2)
with open(os.path.join(D, NS, "tags", "block", "paveable.json"), encoding="utf-8") as f:
    pave = json.load(f)
pave["values"] += optional([f"create:{x}" for x in ("asurine", "crimsite", "limestone", "ochrum", "scorchia", "scoria", "veridium")])
write(D, f"{NS}/tags/block/paveable.json", pave)

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

# ── 발전과제: 여정 기록(사용자 통계로 판정) ──
def stat_tick(stat, n):
    return {"trigger": "minecraft:tick", "conditions": {"player": [{"condition": "minecraft:entity_properties", "entity": "this",
        "predicate": {"type_specific": {"type": "minecraft:player", "stats": [
            {"type": "minecraft:custom", "stat": f"{NS}:{stat}", "value": {"min": n}}]}}}]}}

def advancement(name, icon, parent, criterion, frame="task", hidden=False, background=None):
    display = {"icon": {"id": icon}, "title": {"translate": f"advancements.{NS}.journey.{name}.title"},
               "description": {"translate": f"advancements.{NS}.journey.{name}.description"},
               "frame": frame, "show_toast": True, "announce_to_chat": True, "hidden": hidden}
    if background:
        display["background"] = background
    obj = {"display": display, "criteria": {"done": criterion}}
    if parent:
        obj["parent"] = f"{NS}:journey/{parent}"
    write(D, f"{NS}/advancement/journey/{name}.json", obj)

advancement("root", f"{NS}:setts", None, stat_tick("road_one_cm", 100), background="minecraft:block/dirt_path_top")
advancement("road_1km", f"{NS}:milestone", "root", stat_tick("road_one_cm", 100000))
advancement("first_bridge", f"{NS}:stone_bridge_kit", "root", stat_tick("bridge_blocks", 1))
advancement("first_station", f"{NS}:station", "root", stat_tick("stations_arrived", 1))
advancement("surveyor", f"{NS}:signpost", "first_station", stat_tick("routes_surveyed", 5), frame="goal")
advancement("rail_layer", f"{NS}:track_layer", "first_station", stat_tick("track_placed", 256))
advancement("tunneler", f"{NS}:subway_shield", "rail_layer", stat_tick("blocks_bored", 2000), frame="goal")
advancement("rider", f"{NS}:whistle", "root", stat_tick("horses_called", 1))
advancement("aquila", f"{NS}:astra_obliterator", "root",
            {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": f"{NS}:astra_obliterator"}]}},
            frame="challenge", hidden=True)

# ── 레시피 ──
RECIPES = [
    # (이름, 결과 수, 패턴, {키: 재료})  재료: "minecraft:x" 또는 "#minecraft:tag"
    ("setts", 8, ["GCG", "C C", "GCG"], {"G": "minecraft:polished_granite", "C": "minecraft:cobblestone"}),
    ("brick_paving", 8, ["BSB", "S S", "BSB"], {"B": "minecraft:bricks", "S": "minecraft:sand"}),
    ("flagstone", 8, ["SMS", "M M", "SMS"], {"S": "minecraft:smooth_stone", "M": "minecraft:moss_block"}),
    ("milestone", 1, ["S", "N", "S"], {"S": "minecraft:stone_bricks", "N": "minecraft:compass"}),
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
    ("road_paver", 1, ["III", "SIS", " S "], {"I": "minecraft:iron_ingot", "S": "minecraft:stick"}),
    ("track_layer", 1, ["RRR", "ISI", " S "], {"R": "minecraft:rail", "I": "minecraft:iron_ingot", "S": "minecraft:stick"}),
    ("tunnel_borer", 1, ["DID", "IPI", " S "], {"D": "minecraft:diamond", "I": "minecraft:iron_block", "P": "minecraft:piston", "S": "minecraft:stick"}),
    ("surveyor", 1, [" A ", "CSC", " S "], {"A": "minecraft:amethyst_shard", "C": "minecraft:copper_ingot", "S": "minecraft:stick"}),
    ("whistle", 1, [" GS"], {"G": "minecraft:gold_ingot", "S": "minecraft:string"}),
    ("iron_horseshoe", 2, ["I I", "I I", "III"], {"I": "minecraft:iron_ingot"}),
    ("ender_rail_core", 1, ["PEP", "ERE", "PEP"], {"P": "minecraft:popped_chorus_fruit", "E": "minecraft:ender_eye", "R": "minecraft:redstone_block"}),
    # 지하철 키트
    ("tunnel_lining", 16, ["SSS", "SCS", "SSS"], {"S": "#minecraft:stone_crafting_materials", "C": "minecraft:clay_ball"}),
    ("tunnel_light", 4, ["NGN", "GLG", "NGN"], {"N": "minecraft:iron_nugget", "G": "minecraft:glass_pane", "L": "minecraft:glowstone_dust"}),
    ("platform_edge", 8, ["LLL", "LYL", "LLL"], {"L": f"{NS}:tunnel_lining", "Y": "minecraft:yellow_dye"}),
    ("station_elevator", 1, ["IPI", "IRI", "IPI"], {"I": "minecraft:iron_ingot", "P": "minecraft:piston", "R": "minecraft:redstone_block"}),
    ("subway_shield", 1, ["III", "DTD", "III"], {"I": "minecraft:iron_block", "D": "minecraft:diamond", "T": f"{NS}:tunnel_borer"}),
    ("station_blueprint", 1, ["BPB", "PSP", "BPB"], {"B": "minecraft:blue_dye", "P": "minecraft:paper", "S": f"{NS}:station"}),
    ("screen_door", 2, ["IPI", "IPI"], {"I": "minecraft:iron_ingot", "P": "minecraft:glass_pane"}),
    # 다리 키트(내구도 30): 연장 + 그 다리의 재료 견본
    ("timber_bridge_kit", 1, ["SAS", "PPP", "L L"], {"S": "minecraft:string", "A": "minecraft:iron_axe", "P": "#minecraft:planks", "L": "#minecraft:logs"}),
    ("stone_bridge_kit", 1, ["CPC", "BBB", "B B"], {"C": "minecraft:chiseled_stone_bricks", "P": "minecraft:iron_pickaxe", "B": "minecraft:stone_bricks"}),
    ("copper_bridge_kit", 1, ["ILI", "CCC", "I I"], {"I": "minecraft:copper_ingot", "L": "minecraft:lightning_rod", "C": "minecraft:cut_copper"}),
    ("ground_station_blueprint", 1, ["BLB", "PSP", "BWB"], {"B": "minecraft:blue_dye", "L": "minecraft:lantern", "P": "minecraft:paper",
                                                            "S": f"{NS}:station", "W": "minecraft:spruce_slab"}),
]

# Create 가 있을 때만 쓰이는 레시피(없으면 fabric 조건으로 빠진다)
CREATE_RECIPES = [
    ("metro_car_blueprint", 1, ["BPB", "PCP", "BPB"], {"B": "minecraft:light_blue_dye", "P": "minecraft:paper", "C": "create:railway_casing"}),
    ("coach_blueprint", 1, ["RPR", "PCP", "RWR"], {"R": "minecraft:red_dye", "P": "minecraft:paper", "C": "create:railway_casing",
                                                   "W": "minecraft:spruce_planks"}),
]
# 히든: 독수리자리. 모두 서바이벌에서 구할 수 있는 재료지만 극악이다(위더 4마리, 엔더 드래곤, 고대 도시, 시련의 회당, 고고학 …)
HIDDEN_RECIPES = [
    ("star_altair", 1, ["DED", "EBE", "DED"], {"D": "minecraft:diamond_block", "E": "minecraft:end_crystal", "B": "minecraft:beacon"}),
    ("star_tarazed", 1, ["FBF", "BNB", "FBF"], {"F": "minecraft:fire_charge", "B": "minecraft:blaze_rod", "N": "minecraft:netherite_block"}),
    ("star_alshain", 1, ["GGG", "GTG", "GGG"], {"G": "minecraft:gold_block", "T": "minecraft:totem_of_undying"}),
    ("star_deneb_el_okab", 1, ["SRS", "RHR", "SRS"], {"S": "minecraft:shulker_shell", "R": f"{NS}:ender_rail_core", "H": "minecraft:dragon_head"}),
    ("star_bezek", 1, ["CAC", "AKA", "CAC"], {"C": "minecraft:sculk_catalyst", "A": "minecraft:amethyst_cluster", "K": "minecraft:recovery_compass"}),
    ("star_chesus", 1, ["BKB", "KHK", "BKB"], {"B": "minecraft:breeze_rod", "K": "minecraft:ominous_trial_key", "H": "minecraft:heavy_core"}),
    ("star_libertas", 1, ["NPN", "SHS", "NPN"], {"N": "minecraft:nautilus_shell", "P": "minecraft:prismarine_crystals",
                                                  "S": "minecraft:sniffer_egg", "H": "minecraft:heart_of_the_sea"}),
    ("star_phonix", 1, ["GFG", "FAF", "GFG"], {"G": "minecraft:ghast_tear", "F": "minecraft:fire_charge", "A": "minecraft:enchanted_golden_apple"}),
    ("star_petra", 1, ["ICI", "CLC", "ICI"], {"I": "minecraft:netherite_ingot", "C": "minecraft:crying_obsidian", "L": "minecraft:lodestone"}),
    # 성좌핵: 하늘의 독수리자리 모양대로 놓는다
    ("aquila_core", 1, ["DTC", "PAB", "FSL"], {"D": f"{NS}:star_deneb_el_okab", "T": f"{NS}:star_tarazed", "C": f"{NS}:star_chesus",
                                               "P": f"{NS}:star_petra", "A": f"{NS}:star_altair", "B": f"{NS}:star_bezek",
                                               "F": f"{NS}:star_phonix", "S": f"{NS}:star_alshain", "L": f"{NS}:star_libertas"}),
    ("astra_obliterator", 1, [" N ", "NCN", " W "], {"N": "minecraft:nether_star", "C": f"{NS}:aquila_core", "W": "minecraft:netherite_sword"}),
]

# 조합법이 없는 아이템(획득처는 Loot.kt·설계도)
REMOVED_RECIPES = ["diamond_horseshoe"]

GUIDE_PAGES = 9

def recipes(version_dir, new_format, chain_id=None):
    base = os.path.join(REPO, "versions", version_dir, "src", "main", "resources", "data", NS, "recipe")
    for name in REMOVED_RECIPES:
        stale = os.path.join(base, f"{name}.json")
        if os.path.exists(stale):
            os.remove(stale)
    for name, count, pattern, key in RECIPES + CREATE_RECIPES + HIDDEN_RECIPES:
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
        if any(v.startswith("create:") for v in key.values()):
            obj = {"fabric:load_conditions": [{"condition": "fabric:all_mods_loaded", "values": ["create"]}], **obj}
        full = os.path.join(base, f"{name}.json")
        os.makedirs(os.path.dirname(full), exist_ok=True)
        with open(full, "w", encoding="utf-8") as f:
            json.dump(obj, f, ensure_ascii=False, indent=2)
            f.write("\n")

def guide_recipe(version_dir, new_format):
    """책 + 레일 → 지하철 건설 안내서(번역 키를 쓰는 완성된 책)"""
    if new_format:
        pages = [{"translate": f"infracraft.book.subway.{i}"} for i in range(1, GUIDE_PAGES + 1)]
        ingredients = ["minecraft:book", "minecraft:rail"]
    else:
        pages = [json.dumps({"translate": f"infracraft.book.subway.{i}"}) for i in range(1, GUIDE_PAGES + 1)]
        ingredients = [{"item": "minecraft:book"}, {"item": "minecraft:rail"}]
    obj = {"type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": ingredients,
           "result": {"id": "minecraft:written_book", "count": 1, "components": {
               "minecraft:written_book_content": {"title": "지하철 건설 안내서", "author": "InfraCraft", "pages": pages}}}}
    full = os.path.join(REPO, "versions", version_dir, "src", "main", "resources", "data", NS, "recipe", "subway_guide.json")
    with open(full, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")

guide_recipe("26.2", True)
guide_recipe("26.3", True)
recipes("26.2", True)
recipes("26.3", True)

# ── 번역 ──
with open(os.path.join(HERE, "lang.json"), encoding="utf-8") as f:
    LANG = json.load(f)
for code in ("ko_kr", "en_us"):
    write(A, f"lang/{code}.json", {k: v[code] for k, v in LANG.items()})
print("data written")
