"""클라이언트 게임테스트 스크린샷을 문서로 옮긴다.

사용법: ./gradlew :mc26.2:runClientGameTest 다음에 python3 tools/screenshots.py
  versions/26.2/build/run/clientGameTest/screenshots/0000_이름.png → docs/screenshots/이름.jpg (가로 854 그대로, 용량을 줄이려고 JPEG)
  docs/SCREENSHOTS.md 를 다시 쓴다.
"""
import os
import re
import shutil

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.join(HERE, "..")
SRC = os.path.join(REPO, "versions", "26.2", "build", "run", "clientGameTest", "screenshots")
DST = os.path.join(REPO, "docs", "screenshots")

SECTIONS = [
    ("다리 키트", "목조·석조 아치·구리 트러스, 옆·상판·아래에서 본 모습과 밤", r"^bridge_"),
    ("다리 키트 홀로그램", "들고 있으면 놓일 블록이 반투명하게 보여요", r"^hologram_"),
    ("역 이름의 언어", "자동으로 붙은 이름이 플레이어 언어를 따라요", r"^names_"),
]
SPOILER = ("히든 무기(스포일러)", "독수리자리 발전과제 탭, 별빛 좌표 지도, 검격·AS-01 레일건·AS-09 아홉 별", r"^astra_")

CAPTIONS = {
    "bridge_timber_side": "목조 다리: 지붕 달린 문, 랜턴 기둥, 가대 교각과 까치발",
    "bridge_stone_side": "석조 아치교: 원호 아치와 3칸 두께 교각",
    "bridge_stone_below": "석조 아치교 아래: 쐐기돌, 물가름 받침, 이끼 낀 돌",
    "bridge_copper_side": "구리 트러스교: Warren 트러스와 양 끝 등탑",
    "hologram_stone": "석조 아치교 키트를 들고 있을 때",
    "names_ko_kr": "한국어", "names_en_us": "English",
    "astra_advancements": "독수리자리 발전과제 탭", "astra_map": "별빛 좌표 지도(조준 중 M)",
    "astra_slash_a": "암흑 별빛 검격(모델)", "astra_rail_charge": "AS-01 별자리 레일건: 충전",
    "astra_rail_fire": "AS-01 발사", "astra_nine_cinematic_b": "AS-09 아홉 별: 우주 시네마틱",
    "astra_nine_projection": "하늘에 투영된 독수리자리", "astra_nine_falling": "별이 차례로 떨어진다",
    "astra_nine_altair_dome": "알타이르 착탄", "astra_nine_aftermath": "남은 별핵 수정과 크레이터",
}


def main():
    shots = sorted(f for f in os.listdir(SRC) if f.endswith(".png"))
    if os.path.isdir(DST):
        shutil.rmtree(DST)
    os.makedirs(DST)
    names = []
    for f in shots:
        name = re.sub(r"^\d+_", "", f)[:-4]
        Image.open(os.path.join(SRC, f)).convert("RGB").save(os.path.join(DST, name + ".jpg"), quality=88, optimize=True)
        names.append(name)
    out = ["# 실제 게임 화면", "",
           "CI 의 클라이언트 게임테스트(`./gradlew :mc26.2:runClientGameTest`, `versions/26.2/src/gametest`)가 실제 게임을 띄워 월드를 만들고 찍은 화면이에요.",
           "다시 만들려면 게임테스트를 돌린 뒤 `python3 tools/screenshots.py`.", ""]

    def table(sel):
        rows = []
        for i in range(0, len(sel), 2):
            pair = sel[i:i + 2]
            rows.append("| " + " | ".join(f"![{n}](screenshots/{n}.jpg)" for n in pair) + (" |   |" if len(pair) == 1 else " |"))
            rows.append("| " + " | ".join(CAPTIONS.get(n, n) for n in pair) + (" |   |" if len(pair) == 1 else " |"))
        return ["|   |   |", "|---|---|"] + rows

    for title, sub, pat in SECTIONS:
        sel = [n for n in names if re.match(pat, n)]
        if sel:
            out += [f"## {title}", sub, ""] + table(sel) + [""]
    sel = [n for n in names if re.match(SPOILER[2], n)]
    if sel:
        out += [f"<details><summary><b>{SPOILER[0]}</b>: {SPOILER[1]}</summary>", ""] + table(sel) + ["", "</details>", ""]
    with open(os.path.join(REPO, "docs", "SCREENSHOTS.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(out))
    print(f"{len(names)} screenshots -> docs/screenshots")


if __name__ == "__main__":
    main()
