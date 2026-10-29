"""CI 전용: tools/modpack/pack.json 의 모드를 찾아 Modrinth 모드팩(.mrpack)과 한국어 리소스팩을 만든다.

사용법: python3 tools/modpack/build_pack.py --infracraft <InfraCraft jar> --mc <마인크래프트 jar> --out <폴더>
  - 모드 파일은 Modrinth 또는 GitHub 릴리스에서 찾는다(필수 의존 모드는 Modrinth 에서 따라 받는다).
    .mrpack 은 내려받을 주소만 담고, InfraCraft 는 overrides 에 넣는다.
  - 한국어: Fly 포트에 한국어가 없거나 모자라면, 원본 Create 애드온(Create Fly 가 아닌 원작)의 ko_kr 로 채운다.
  - 서버 시험용으로 모든 jar 를 <out>/mods 에 둔다.
  - CurseForge: 환경 변수 CURSEFORGE_API_KEY 가 있으면 pack.json 의 curseforge 슬러그로도 찾는다(작성자가 외부 배포를
    막은 파일은 받지 않는다).
  - 한 파일 모드팩(zip): --zip <파일> 이면 CurseForge 모드팩 형식(manifest.json + overrides)의 zip 을 만든다.
    CurseForge 앱·Prism Launcher·ATLauncher 에서 가져오기로 설치한다.
      · CurseForge 에서 찾은 모드는 manifest.json 에 프로젝트·파일 번호로 적는다(런처가 받는다, 재배포 아님)
      · Modrinth·GitHub 에서 받은 모드는 overrides/mods 에 jar 로 넣는다. 라이선스가 재배포를 허락하는 것만
        (--all-jars 를 주면 전부: 개인용, 공개하지 말 것)
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


CF_API = "https://api.curseforge.com/v1"


def cf_headers():
    key = os.environ.get("CURSEFORGE_API_KEY")
    return {"x-api-key": key, "Accept": "application/json"} if key else None


def curseforge_file(slug, mc):
    """CurseForge 프로젝트(마인크래프트 모드)의 26.2 Fabric 최신 파일: (주소, 파일 이름, 배포 허용, 라이선스 힌트)"""
    h = cf_headers()
    if not h:
        return None
    found = get(f"{CF_API}/mods/search?gameId=432&classId=6&slug={urllib.parse.quote(slug)}", h).get("data", [])
    if not found:
        return None
    mod = found[0]
    files = get(f"{CF_API}/mods/{mod['id']}/files?gameVersion={urllib.parse.quote(mc)}&modLoaderType=4&pageSize=20", h).get("data", [])
    files = [x for x in files if x.get("isAvailable", True) and x.get("releaseType", 1) in (1, 2, 3)]
    if not files:
        return None
    fl = max(files, key=lambda x: x.get("fileDate", ""))
    allowed = bool(mod.get("allowModDistribution")) and fl.get("downloadUrl")
    return (fl.get("downloadUrl"), fl["fileName"], bool(allowed), mod.get("links", {}).get("sourceUrl") or "", mod["id"], fl["id"])


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


# 번역 문장을 다시 나눠 줄 수 있는 라이선스(SPDX). 모든 권리 보유(ARR)나 자체 라이선스(예: Create 에셋)는 가져오지 않는다.
REDISTRIBUTABLE = re.compile(r"^(MIT|Apache-2\.0|MPL-2\.0|CC0-1\.0|CC-BY(-SA)?-[0-9.]+|BSD-[23]-Clause|Unlicense|ISC|Zlib|"
                             r"(L|A)?GPL-[23]\.0(-only|-or-later)?)$")
LICENSE_LOG = []


def original_ko(slug):
    """원작 애드온의 최신 jar(로더 상관없이)에서 ko_kr. 라이선스가 재배포를 허락할 때만"""
    try:
        lic = (get(f"{API}/project/{slug}").get("license") or {}).get("id", "")
    except Exception:
        lic = ""
    if not REDISTRIBUTABLE.match(lic or ""):
        LICENSE_LOG.append((slug, lic or "?", "skipped"))
        return {}
    LICENSE_LOG.append((slug, lic, "used"))
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
    ap.add_argument("--zip", help="한 파일 모드팩(zip) 경로")
    ap.add_argument("--all-jars", action="store_true", help="재배포 허락과 상관없이 모든 jar 를 넣는다(개인용)")
    ap.add_argument("--version", default=time.strftime("%Y.%m.%d"), help="모드팩 버전")
    a = ap.parse_args()
    pack = json.load(open(os.path.join(HERE, "pack.json"), encoding="utf-8"))
    mc = pack["minecraft"]
    os.makedirs(os.path.join(a.out, "mods"), exist_ok=True)
    files, report, seen_projects = [], [], set()
    jars = {}

    broken = {m["id"]: m["server_broken"] for m in pack["mods"] if m.get("server_broken")}

    licenses = {}  # mod_id → (라이선스, 재배포 가능)
    cf_refs = []  # (mod_id, projectID, fileID, 파일 이름)
    sources = {}  # mod_id → 원본 소스 주소(GPL 계열 고지용)

    def add_file(mod_id, url, name, data, client_only=False, source=""):
        client_only = client_only or mod_id in broken
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
        try:
            proj = get(f"{API}/project/{v['project_id']}")
        except Exception:
            proj = {}
        lic = (proj.get("license") or {}).get("id", "")
        if proj.get("source_url"):
            sources[mod_id] = proj["source_url"]
        licenses[mod_id] = (lic or "?", bool(REDISTRIBUTABLE.match(lic or "")))
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
                    try:
                        lic = ((get(f"https://api.github.com/repos/{m['github']}", gh_headers()).get("license") or {}).get("spdx_id") or "")
                    except Exception:
                        lic = ""
                    licenses[m["id"]] = (lic or "?", bool(REDISTRIBUTABLE.match(lic or "")) and not m.get("note"))
                    sources[m["id"]] = f"https://github.com/{m['github']}"
                    ok = True
            except Exception as e:
                print("github failed", m["id"], e)
        if not ok and m.get("curseforge"):
            try:
                hit = curseforge_file(m["curseforge"], mc)
                if hit:
                    # zip(CurseForge 형식)에는 번호로만 적는다. 서버 시험용 jar 는 작성자가 외부 배포를 허용했을 때만 받는다
                    cf_refs.append((m["id"], hit[4], hit[5], hit[1]))
                    report.append((m["id"], hit[1], f"CurseForge {m['curseforge']} (manifest)"))
                    if hit[2]:
                        jars[m["id"]] = get(hit[0], raw=True)
                        open(os.path.join(a.out, "mods", hit[1]), "wb").write(jars[m["id"]])
                    ok = True
            except Exception as e:
                print("curseforge failed", m["id"], e)
        if ok and m["id"] not in sources and m.get("github"):
            sources[m["id"]] = f"https://github.com/{m['github']}"
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
        credits = ["InfraCraft 모드팩 한국어 보완 리소스팩 — 출처와 라이선스", "",
                   "- InfraCraft 가 직접 번역한 문장: Create: Enchantment Industry, Create: Dragons Plus (LGPL-3.0-or-later 원문의 번역이므로 같은 라이선스),",
                   "  Create Deco (CC0-1.0). LGPL 전문은 InfraCraft 저장소 licenses/ 에 있습니다.", "",
                   "- 원작 애드온에서 가져온 문장(라이선스가 재배포를 허락하는 것만):"]
        credits += [f"  {slug}: {lic} ({state})" for slug, lic, state in LICENSE_LOG]
        z.writestr("CREDITS.txt", "\n".join(credits) + "\n")
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

    # ── 한 파일 모드팩(zip, CurseForge 형식) ──
    if a.zip:
        names = {r[0]: r[1] for r in report}
        cf_ids = {r[0] for r in cf_refs}
        put, skipped = [], []
        manifest = {"minecraft": {"version": mc, "modLoaders": [{"id": f"fabric-{pack['loader']}", "primary": True}]},
                    "manifestType": "minecraftModpack", "manifestVersion": 1, "name": pack["name"], "version": a.version,
                    "author": "menonng", "files": [{"projectID": p, "fileID": fid, "required": True} for (_, p, fid, _) in cf_refs],
                    "overrides": "overrides"}
        with zipfile.ZipFile(a.zip, "w", zipfile.ZIP_DEFLATED) as z:
            z.writestr("manifest.json", json.dumps(manifest, ensure_ascii=False, indent=2))
            for mod_id, data in jars.items():
                if mod_id in cf_ids:
                    continue
                if mod_id == "infracraft":
                    z.writestr("overrides/mods/infracraft.jar", data); continue
                lic, ok = licenses.get(mod_id, ("?", False))
                if not ok and not a.all_jars:
                    skipped.append((mod_id, lic)); continue
                z.writestr(f"overrides/mods/{names.get(mod_id, mod_id + '.jar')}", data)
                put.append((mod_id, lic))
            z.writestr("overrides/resourcepacks/InfraCraft-KO.zip", rp_bytes)
            z.writestr("overrides/options.txt", 'lang:ko_kr\nresourcePacks:["vanilla","fabric","file/InfraCraft-KO.zip"]\n')
            readme = [f"InfraCraft 모드팩 {a.version} (Minecraft {mc}, Fabric Loader {pack['loader']})", "",
                      "설치: CurseForge 앱, Prism Launcher, ATLauncher 에서 이 zip 을 '모드팩 가져오기(Import)' 하세요.",
                      "  - manifest.json 의 모드는 런처가 CurseForge 에서 받습니다.",
                      "  - overrides/mods 의 모드는 zip 에 들어 있습니다(라이선스가 재배포를 허락하는 것).", "",
                      "CurseForge 에서 받는 모드:"] + [f"  {i}: {n} (project {p}, file {fid})" for (i, p, fid, n) in cf_refs]
            readme += ["", "zip 에 들어 있는 모드(라이선스):"] + [f"  {i}: {l}" + (f"  소스: {sources[i]}" if i in sources else "") for i, l in put]
            if skipped:
                readme += ["", "재배포가 허락되지 않아(또는 라이선스를 확인하지 못해) 넣지 않은 모드: 직접 받아 mods 에 넣으세요"] + [
                    f"  {i}: {l}  {sources.get(i, '')}" for i, l in skipped]
            if missing:
                readme += ["", "찾지 못했거나 직접 받아야 하는 모드:"] + [
                    f"  {m['id']}: {m.get('note') or ('https://www.curseforge.com/minecraft/mc-mods/' + m['curseforge'] if m.get('curseforge') else 'https://github.com/' + m['github'] if m.get('github') else m.get('modrinth'))}"
                    for m in missing]
            z.writestr("README-INFRACRAFT-MODPACK.txt", "\n".join(readme) + "\n")
            z.writestr("modlist.html", "<ul>" + "".join(f"<li>{i}</li>" for i in sorted(set(names) | cf_ids)) + "</ul>")
        print(f"zip: {a.zip} (curseforge {len(cf_refs)}, jars {len(put)}, skipped {len(skipped)}, missing {len(missing)})")

    with open(os.path.join(a.out, "REPORT.md"), "w", encoding="utf-8") as f:
        f.write(f"# 모드팩 빌드 보고\n\n마인크래프트 {mc}, Fabric Loader {pack['loader']}, 리소스팩 형식 {res_format}\n\n")
        f.write("## 들어간 모드\n| id | 파일 | 출처 |\n|---|---|---|\n")
        for r in report:
            f.write(f"| {r[0]} | {r[1]} | {r[2]} |\n")
        f.write("\n## 찾지 못한 모드\n")
        for m in missing:
            f.write(f"- {m['id']} ({m.get('note') or (m.get('curseforge') and 'CurseForge 전용: ' + m['curseforge']) or m.get('github') or m.get('modrinth')})\n")
        f.write("\n## 전용 서버에서 빠지는 모드\n")
        for k, why in broken.items():
            f.write(f"- {k}: {why}\n")
        f.write("\n## 한국어\n| 모드 | 네임스페이스 | 원래 ko | 채운 키 | 합계 | 영어 키 |\n|---|---|---|---|---|---|\n")
        for c in coverage:
            f.write(f"| {c[0]} | {c[1]} | {c[2]} | {c[3]} | {c[4]} ({100 * c[4] // max(c[5], 1)}%) | {c[5]} |\n")
    print(open(os.path.join(a.out, "REPORT.md"), encoding="utf-8").read())


if __name__ == "__main__":
    main()
