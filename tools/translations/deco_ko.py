"""Create Deco 한국어 번역 생성기.

Create Deco 의 이름은 [재료] × [부품] 조합이 대부분이라 규칙으로 옮기고, Create 공식 한국어의 용어(황동 창살, 산업용 철 유리창,
깎인 안산암 벽돌 반 블록 …)와 바닐라 한국어의 색 이름(파란색, 하늘색, 회백색 …)을 그대로 따른다.
사용법: python3 deco_ko.py en_us.json ko_kr.json
"""
import json
import re
import sys

METAL = {"Andesite": "안산암", "Brass": "황동", "Copper": "구리", "Iron": "철", "Industrial Iron": "산업용 철",
         "Zinc": "아연", "Gold": "금", "Netherite": "네더라이트", "Andesite Alloy": "안산암 합금"}
# 바닐라 한국어의 염료 색 이름
DYE = {"White": "하얀색", "Orange": "주황색", "Magenta": "자홍색", "Light Blue": "하늘색", "Yellow": "노란색", "Lime": "연두색",
       "Pink": "분홍색", "Gray": "회색", "Light Gray": "회백색", "Cyan": "청록색", "Purple": "보라색", "Blue": "파란색",
       "Brown": "갈색", "Green": "초록색", "Red": "빨간색", "Black": "검은색"}
# Create Deco 벽돌 종류(색과 분위기를 살린 이름)
BRICK = {"Blue": "파란", "Red": "빨간", "Dean": "담황", "Dusk": "땅거미", "Pearl": "진주", "Scarlet": "진홍", "Umber": "암갈",
         "Verdant": "신록"}
BRICK_STYLE = {"Corner": "모서리", "Long": "긴", "Short": "짧은", "Tiled": "타일"}
BRICK_AGE = {"Cracked": "금 간", "Mossy": "이끼 낀"}
BRICK_PART = {"Bricks": "벽돌", "Brick Slab": "벽돌 반 블록", "Brick Stairs": "벽돌 계단", "Brick Wall": "벽돌 담장"}
METAL_PART = {
    "Bars Overlay": "창살 덧판", "Bars": "창살", "Catwalk Railing": "캣워크 난간", "Catwalk Stairs": "캣워크 계단",
    "Catwalk": "캣워크", "Door": "문", "Mesh Fence": "철망 울타리", "Sheet Metal": "판금", "Sheet": "판",
    "Support Wedge": "지지대 쐐기", "Support": "지지대", "Train Hull": "기차 외판", "Trapdoor": "다락문",
    "Window Pane": "유리판", "Window": "유리창", "Ladder": "사다리", "Ingot": "주괴", "Nugget": "조각",
    "Coin Stack": "동전 더미", "Coin": "동전", "Cage Lamp": "보호망 등",
}
DECAL = {"Creeper": "크리퍼", "Cross": "십자", "Down Arrow": "아래 화살표", "Down Left Arrow": "왼쪽 아래 화살표",
         "Down Right Arrow": "오른쪽 아래 화살표", "Up Arrow": "위 화살표", "Up Left Arrow": "왼쪽 위 화살표",
         "Up Right Arrow": "오른쪽 위 화살표", "Left Arrow": "왼쪽 화살표", "Right Arrow": "오른쪽 화살표",
         "Electrical": "감전 주의", "Fire": "화기 주의", "Fire Diamond": "위험물 표지", "Flow": "흐름 방향", "Fluid": "유체",
         "Ice": "결빙 주의", "No Entry": "진입 금지", "Radioactive": "방사능 주의", "Skull": "해골", "Warning": "경고"}
SPECIAL = {"Create Deco Bricks": "Create Deco 벽돌", "Create Deco Props": "Create Deco 소품"}


def longest(d, text, at_start=True):
    for k in sorted(d, key=len, reverse=True):
        if (text.startswith(k + " ") or text == k) if at_start else (text.endswith(" " + k) or text == k):
            return k
    return None


def translate(en):
    if en in SPECIAL:
        return SPECIAL[en]
    # "BrassCoin Stack Block" 처럼 붙어 있는 원문 오타를 바로잡고 옮긴다
    m = re.fullmatch(r"(.+?) ?Coin Stack Block", en)
    if m:
        return f"{METAL[m.group(1)]} 동전 더미 블록"
    if en.endswith(" Decal"):
        return DECAL[en[:-6]] + " 데칼"
    if en.endswith(" Placard"):
        return DYE[en[:-8]] + " 플래카드"
    if en.endswith(" Shipping Container"):
        return DYE[en[: -len(" Shipping Container")]] + " 화물 컨테이너"
    if en.startswith("Locked "):
        rest = translate(en[7:])
        return "잠긴 " + rest
    # 벽돌: [금 간/이끼 낀] [모서리/긴/짧은/타일] 색 벽돌 [반 블록/계단/담장]
    part = longest(BRICK_PART, en, at_start=False)
    if part:
        head = en[: -len(part)].strip().split(" ")
        age = style = ""
        if head and head[0] in BRICK_AGE:
            age = BRICK_AGE[head.pop(0)] + " "
        if head and head[0] in BRICK_STYLE:
            style = BRICK_STYLE[head.pop(0)] + " "
        color = " ".join(head)
        return f"{age}{style}{BRICK[color]} {BRICK_PART[part]}"
    # 색 + 금속 + 보호망 등
    if en.endswith(" Cage Lamp"):
        rest = en[: -len(" Cage Lamp")]
        dye = longest(DYE, rest)
        metal = rest[len(dye) + 1:]
        return f"{DYE[dye]} {METAL[metal]} 보호망 등"
    # 금속 + 부품
    metal = longest(METAL, en)
    if metal:
        mpart = en[len(metal) + 1:]
        if mpart in METAL_PART:
            return f"{METAL[metal]} {METAL_PART[mpart]}"
    raise KeyError(en)


if __name__ == "__main__":
    src = json.load(open(sys.argv[1], encoding="utf-8"))
    out, missing = {}, []
    for k, v in src.items():
        try:
            out[k] = translate(v)
        except KeyError:
            missing.append((k, v))
    json.dump(out, open(sys.argv[2], "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    print(f"{len(out)}/{len(src)} translated")
    for k, v in missing:
        print("MISSING", k, v)
