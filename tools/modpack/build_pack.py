"""CI 전용: tools/modpack/pack.json 의 모드를 찾아 Modrinth 모드팩(.mrpack)과 한국어 리소스팩을 만든다.

사용법: python3 tools/modpack/build_pack.py --infracraft <InfraCraft jar> --mc <마인크래프트 jar> --out <폴더>
  - 모드 파일은 Modrinth 또는 GitHub 릴리스에서 찾는다(필수 의존 모드는 Modrinth 에서 따라 받는다).
    .mrpack 은 내려받을 주소만 담고, InfraCraft 는 overrides 에 넣는다.
  - 한국어: Fly 포트에 한국어가 없거나 모자라면, 원본 Create 애드온(Create Fly 가 아닌 원작)의 ko_kr 로 채운다.
  - 서버 시험용으로 모든 jar 를 <out>/mods 에 둔다.
"""
import argparse
import hashlib
import io
import json
import os
import re
import time
import urllib.parse
import urllib.request
import zipfile

API = "https://api.modrinth.com/v2"
H = {"User-Agent": "menonng/InfraCraft modpack-builder"}
HERE = os.path.dirname(os.path.abspath(__file__))


def get(url, headers=None, raw=False):
    hdr = dict(H, **(headers or {}))
    for i in range(4):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=hdr), timeout=120) as r:
                data = r.read()
                return data if raw else json.loads(data)
        except Exception as e:  # 잠깐의 네트워크 오류는 다시 시도
            err = e
            time.sleep(2 ** i)
    raise err


def gh_headers():
    tok = os.environ.get("GH_TOKEN")
    return {"Authorization": "Bearer " + tok} if tok else {}


def modrinth_version(project, mc, version=None, loaders=("fabric",)):
    q = f"{API}/project/{project}/version?loaders=" + urllib.parse.quote(json.dumps(list(loaders)))
    if mc:
        q += "&game_versions=" + urllib.parse.quote(json.dumps([mc]))
    vs = get(q)
    if version:
        vs = [v for v in vs if v["version_number"] == version] or vs
    return vs[0] if vs else None


def github_asset(repo, mc):
    """릴리스 중 이름이나 태그에 마인크래프트 버전과 fabric 이 들어간 jar(sources 제외)"""
    rels = get(f"https://api.github.com/repos/{repo}/releases?per_page=30", gh_headers())
    for rel in rels:
        for a in rel.get("assets", []):
            n = a["name"]
            if not n.endswith(".jar") or re.search(r"sources|dev|slim", n, re.I):
                continue
            hay = (n + " " + rel.get("tag_name", "") + " " + rel.get("name", "")).lower()
            if mc in hay and "neoforge" not in hay:
                return a["browser_download_url"], n
    return None


def lang_files(jar_bytes):
    """jar 안의 (네임스페이스 → {언어: dict})"""
    out = {}
    try:
        z = zipfile.ZipFile(io.BytesIO(jar_bytes))
    except zipfile.BadZipFile:
        return out
    for n in z.namelist():
        m = re.match(r"assets/([^/]+)/lang/(en_us|ko_kr)\.json$", n)
        if m:
            try:
                out.setdefault(m.group(1), {})[m.group(2)] = json.loads(z.read(n).decode("utf-8-sig"))
            except Exception:
                pass
        # 중첩 jar(jar-in-jar)도 본다
        if n.startswith("META-INF/jars/") and n.endswith(".jar"):
            for ns, langs in lang_files(z.read(n)).items():
                out.setdefault(ns, {}).update({k: v for k, v in langs.items() if k not in out.get(ns, {})})
    return out


def original_ko(slug):
    """원작 애드온의 최신 jar(로더 상관없이)에서 ko_kr"""
    try:
        vs = get(f"{API}/project/{slug}/version")
    except Exception:
        return {}
    for v in vs[:6]:
        f = next((x for x in v["files"] if x.get("primary")), v["files"][0])
        langs = lang_files(get(f["url"], raw=True))
        ko = {ns: l["ko_kr"] for ns, l in langs.items() if "ko_kr" in l}
        if ko:
            return ko
    return {}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--infracraft", required=True)
    ap.add_argument("--mc", required=True)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    pack = json.load(open(os.path.join(HERE, "pack.json"), encoding="utf-8"))
    mc = pack["minecraft"]
    os.makedirs(os.path.join(a.out, "mods"), exist_ok=True)
    files, report, seen_projects = [], [], set()
    jars = {}

    def add_file(mod_id, url, name, data, client_only=False, source=""):
        path = f"mods/{name}"
        if any(f["path"] == path for f in files):
            return
        files.append({"path": path, "hashes": {"sha1": hashlib.sha1(data).hexdigest(), "sha512": hashlib.sha512(data).hexdigest()},
                      "env": {"client": "required", "server": "unsupported" if client_only else "required"},
                      "downloads": [url], "fileSize": len(data)})
        jars[mod_id] = data
        if not client_only:
            open(os.path.join(a.out, "mods", name), "wb").write(data)
        report.append((mod_id, name, source))

    def add_modrinth(mod_id, project, version=None, client_only=False):
        v = modrinth_version(project, mc, version)
        if not v:
            return False
        if v["project_id"] in seen_projects:
            return True
        seen_projects.add(v["project_id"])
        f = next((x for x in v["files"] if x.get("primary")), v["files"][0])
        add_file(mod_id, f["url"], f["filename"], get(f["url"], raw=True), client_only, f"Modrinth {v['version_number']}")
        for dep in v.get("dependencies", []):
            if dep.get("dependency_type") == "required" and dep.get("project_id") and dep["project_id"] not in seen_projects:
                add_modrinth(dep["project_id"], dep["project_id"])
        return True

    missing = []
    for m in pack["mods"]:
        ok = False
        if m.get("modrinth"):
            try:
                ok = add_modrinth(m["id"], m["modrinth"], m.get("version"), m.get("client", False))
            except Exception as e:
                print("modrinth failed", m["id"], e)
        if not ok and m.get("github"):
            try:
                hit = github_asset(m["github"], mc)
                if hit:
                    data = get(hit[0], raw=True)
                    add_file(m["id"], hit[0], hit[1], data, m.get("client", False), f"GitHub {m['github']}")
                    ok = True
            except Exception as e:
                print("github failed", m["id"], e)
        if not ok:
            missing.append(m)
            print("NOT FOUND", m["id"])

    # InfraCraft 자체는 overrides 에
    ic = open(a.infracraft, "rb").read()
    jars["infracraft"] = ic
    open(os.path.join(a.out, "mods", "infracraft.jar"), "wb").write(ic)

    # ── 한국어 리소스팩 ──
    mcz = zipfile.ZipFile(a.mc)
    ver = json.loads(mcz.read("version.json"))
    pv = ver.get("pack_version", {})
    res_format = pv.get("resource_major", pv.get("resource", 0)) if isinstance(pv, dict) else pv
    ko_pack, coverage = {}, []
    by_id = {m["id"]: m for m in pack["mods"]}
    for mod_id, data in jars.items():
        langs = lang_files(data)
        orig = original_ko(by_id[mod_id]["original"]) if by_id.get(mod_id, {}).get("original") else {}
        for ns, l in langs.items():
            en = l.get("en_us", {})
            if not en:
                continue
            have = l.get("ko_kr", {})
            # InfraCraft 에 이미 넣어 둔 번역(Enchantment Industry, Dragons Plus, Deco)
            ic_ko = lang_files(ic).get(ns, {}).get("ko_kr", {}) if mod_id != "infracraft" else {}
            fill = {}
            for k in en:
                if k in have:
                    continue
                if k in orig.get(ns, {}):
                    fill[k] = orig[ns][k]
                elif k in ic_ko:
                    fill[k] = ic_ko[k]
            if fill:
                ko_pack.setdefault(ns, {}).update(fill)
            done = len([k for k in en if k in have or k in fill])
            coverage.append((mod_id, ns, len(have), len(fill), done, len(en)))
    rp = io.BytesIO()
    with zipfile.ZipFile(rp, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("pack.mcmeta", json.dumps({"pack": {"description": "InfraCraft 모드팩 한국어 보완(원작 Create 애드온 번역)",
                                                       "pack_format": res_format, "min_format": res_format, "max_format": res_format}},
                                             ensure_ascii=False, indent=1))
        for ns, d in sorted(ko_pack.items()):
            z.writestr(f"assets/{ns}/lang/ko_kr.json", json.dumps(d, ensure_ascii=False, indent=1))
    rp_bytes = rp.getvalue()
    open(os.path.join(a.out, "InfraCraft-KO.zip"), "wb").write(rp_bytes)

    # ── .mrpack ──
    index = {"formatVersion": 1, "game": "minecraft", "versionId": time.strftime("%Y.%m.%d"), "name": pack["name"],
             "summary": pack["summary"], "files": files,
             "dependencies": {"minecraft": mc, "fabric-loader": pack["loader"]}}
    with zipfile.ZipFile(os.path.join(a.out, "InfraCraft-26.2.mrpack"), "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("modrinth.index.json", json.dumps(index, ensure_ascii=False, indent=1))
        z.writestr("overrides/mods/infracraft.jar", ic)
        z.writestr("overrides/resourcepacks/InfraCraft-KO.zip", rp_bytes)
        z.writestr("overrides/options.txt", 'lang:ko_kr\nresourcePacks:["vanilla","fabric","file/InfraCraft-KO.zip"]\n')

    with open(os.path.join(a.out, "REPORT.md"), "w", encoding="utf-8") as f:
        f.write(f"# 모드팩 빌드 보고\n\n마인크래프트 {mc}, Fabric Loader {pack['loader']}, 리소스팩 형식 {res_format}\n\n")
        f.write("## 들어간 모드\n| id | 파일 | 출처 |\n|---|---|---|\n")
        for r in report:
            f.write(f"| {r[0]} | {r[1]} | {r[2]} |\n")
        f.write("\n## 찾지 못한 모드\n")
        for m in missing:
            f.write(f"- {m['id']} ({m.get('curseforge') and 'CurseForge 전용: ' + m['curseforge'] or m.get('github') or m.get('modrinth')})\n")
        f.write("\n## 한국어\n| 모드 | 네임스페이스 | 원래 ko | 채운 키 | 합계 | 영어 키 |\n|---|---|---|---|---|---|\n")
        for c in coverage:
            f.write(f"| {c[0]} | {c[1]} | {c[2]} | {c[3]} | {c[4]} ({100 * c[4] // max(c[5], 1)}%) | {c[5]} |\n")
    print(open(os.path.join(a.out, "REPORT.md"), encoding="utf-8").read())


if __name__ == "__main__":
    main()
