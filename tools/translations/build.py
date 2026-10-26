"""다른 모드의 한국어 번역을 InfraCraft 리소스로 만든다.

  python3 tools/translations/build.py

각 모드의 영어 원문(sources/*.en_us.json)과 번역 모듈을 비교해 빠진 키, 자리표시자(%1$s), 강조 표시(_…_) 개수가
어긋나면 실패한다. 결과는 common/resources/assets/<모드 ID>/lang/ko_kr.json 에 쓴다.
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
ASSETS = os.path.join(HERE, "..", "..", "common", "resources", "assets")

import deco_ko  # noqa: E402
import dragons_plus_ko  # noqa: E402
import enchantment_industry_ko  # noqa: E402


def load(ns):
    with open(os.path.join(HERE, "sources", f"{ns}.en_us.json"), encoding="utf-8") as f:
        return json.load(f)


def deco():
    return {k: deco_ko.translate(v) for k, v in load("createdeco").items()}


MODS = {
    "create_enchantment_industry": lambda: enchantment_industry_ko.KO,
    "create_dragons_plus": lambda: dragons_plus_ko.KO,
    "createdeco": deco,
}


def check(ns, en, ko):
    problems = []
    for k, v in en.items():
        if k not in ko:
            problems.append(f"빠진 키 {k}")
            continue
        if sorted(re.findall(r"%\d\$s|%s|%d", v)) != sorted(re.findall(r"%\d\$s|%s|%d", ko[k])):
            problems.append(f"자리표시자 불일치 {k}")
        if v.count("_") % 2 == 0 and v.count("_") and ko[k].count("_") % 2:
            problems.append(f"강조 표시 짝 안 맞음 {k}")
    problems += [f"원문에 없는 키 {k}" for k in ko if k not in en]
    return problems


ok = True
for ns, make in MODS.items():
    en, ko = load(ns), make()
    problems = check(ns, en, ko)
    if problems:
        ok = False
        print(f"[{ns}] 문제 {len(problems)}개:", *problems[:20], sep="\n  ")
        continue
    out = os.path.join(ASSETS, ns, "lang", "ko_kr.json")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        json.dump({k: ko[k] for k in en}, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print(f"[{ns}] {len(en)}개 → {os.path.relpath(out, os.path.join(HERE, '..', '..'))}")
sys.exit(0 if ok else 1)
