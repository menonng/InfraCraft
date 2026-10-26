"""배포된 모드 jar 의 한국어(ko_kr) 번역 여부를 확인한다(CI 전용, Modrinth API 사용).

입력: 환경변수 PROJECTS = "라벨=모드린스ID_or_slug[@게임버전]" 를 ; 로 이은 것
출력: 각 프로젝트의 최신(또는 해당 게임 버전) 파일 안 assets/*/lang/ko_kr.json 키 수 / en_us.json 키 수
"""
import io
import json
import os
import urllib.error
import urllib.parse
import urllib.request
import zipfile

API = "https://api.modrinth.com/v2"
HEADERS = {"User-Agent": "menonng/InfraCraft lang-probe"}


def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=60) as r:
        return r.read()


def scan(jar, prefix):
    """jar 안의 en_us/ko_kr 를 비교한다. 내장 jar(Jar-in-Jar)도 한 단계 더 들어가 본다."""
    names = jar.namelist()
    parts = []
    for en in [n for n in names if n.endswith("/lang/en_us.json")]:
        ns = en.split("/")[1]
        ko = en.replace("en_us.json", "ko_kr.json")
        try:
            enc = len(json.loads(jar.read(en)))
        except Exception:
            enc = "?"
        if ko in names:
            try:
                koc = len(json.loads(jar.read(ko)))
            except Exception:
                koc = "?"
            parts.append(f"{prefix}{ns}: ko {koc}/en {enc}")
        else:
            parts.append(f"{prefix}{ns}: 한국어 없음 (en {enc})")
    if not prefix:
        for nested in [n for n in names if n.endswith(".jar")]:
            try:
                parts += scan(zipfile.ZipFile(io.BytesIO(jar.read(nested))), "[" + nested.rsplit("/", 1)[-1][:40] + "] ")
            except Exception:
                pass
    return parts


def probe(label, ref):
    slug, _, game = ref.partition("@")
    try:
        try:
            project = json.loads(get(f"{API}/project/{slug}"))
        except urllib.error.HTTPError:
            # slug 를 모르면 검색 결과 첫 번째를 쓴다
            hits = json.loads(get(f"{API}/search?limit=1&query=" + urllib.parse.quote(slug.replace("-", " "))))["hits"]
            if not hits:
                return f"{label} | {slug} | 프로젝트를 찾지 못함"
            slug = hits[0]["slug"]
            project = json.loads(get(f"{API}/project/{slug}"))
        q = f"{API}/project/{slug}/version"
        if game:
            q += "?game_versions=" + urllib.parse.quote(json.dumps([game]))
        versions = json.loads(get(q))
        if not versions:
            return f"{label} | {slug} | 해당 버전 파일 없음"
        v = versions[0]
        f = next((x for x in v["files"] if x.get("primary")), v["files"][0])
        parts = scan(zipfile.ZipFile(io.BytesIO(get(f["url"]))), "")
        # 라이브러리성 내장 jar(번역 없는 것)는 줄여서 보여 준다
        parts = [x for x in parts if not (x.startswith("[") and "en 0)" in x)]
        return f"{label} | {project['title']} {v['version_number']} ({', '.join(v['game_versions'][-2:])}) | " + ("; ".join(parts) or "lang 파일 없음")
    except Exception as e:
        return f"{label} | {slug} | 확인 실패: {e}"


lines = [probe(*item.split("=", 1)) for item in os.environ["PROJECTS"].split(";") if "=" in item]
print("\n".join(lines))
with open("lang_report.txt", "w", encoding="utf-8") as out:
    out.write("\n".join(lines))
