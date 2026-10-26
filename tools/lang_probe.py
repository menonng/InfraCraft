"""배포된 모드 jar 의 한국어(ko_kr) 번역 여부를 확인한다(CI 전용, Modrinth API 사용).

입력: 환경변수 PROJECTS = "라벨=모드린스ID_or_slug[@게임버전]" 를 ; 로 이은 것
출력: 각 프로젝트의 최신(또는 해당 게임 버전) 파일 안 assets/*/lang/ko_kr.json 키 수 / en_us.json 키 수
"""
import io
import json
import os
import urllib.request
import zipfile

API = "https://api.modrinth.com/v2"
HEADERS = {"User-Agent": "menonng/InfraCraft lang-probe"}


def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=60) as r:
        return r.read()


def probe(label, ref):
    slug, _, game = ref.partition("@")
    try:
        project = json.loads(get(f"{API}/project/{slug}"))
        q = f"{API}/project/{slug}/version"
        if game:
            q += "?game_versions=" + urllib.request.quote(json.dumps([game]))
        versions = json.loads(get(q))
        if not versions:
            return f"{label} | {slug} | 해당 버전 파일 없음"
        v = versions[0]
        f = next((x for x in v["files"] if x.get("primary")), v["files"][0])
        jar = zipfile.ZipFile(io.BytesIO(get(f["url"])))
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
                parts.append(f"{ns}: ko {koc}/en {enc}")
            else:
                parts.append(f"{ns}: 한국어 없음 (en {enc})")
        nested = [n for n in names if n.endswith(".jar")]
        if nested:
            parts.append(f"내장 jar {len(nested)}개(미검사)")
        return f"{label} | {project['title']} {v['version_number']} ({', '.join(v['game_versions'][-2:])}) | " + ("; ".join(parts) or "lang 파일 없음")
    except Exception as e:
        return f"{label} | {slug} | 확인 실패: {e}"


lines = [probe(*item.split("=", 1)) for item in os.environ["PROJECTS"].split(";") if "=" in item]
print("\n".join(lines))
with open("lang_report.txt", "w", encoding="utf-8") as out:
    out.write("\n".join(lines))
