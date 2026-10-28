"""docs/VIDEOS.md 생성기: tools/video/videos.json 의 영상마다 썸네일(jpg)과 MP4 링크를 표로 싣는다.

사용법: python3 tools/video/gallery.py  (영상은 videos 워크플로가 만들어 docs/videos 에 둔다)
"""
import json
import os
import subprocess

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.normpath(os.path.join(HERE, "..", ".."))
VID = os.path.join(REPO, "docs", "videos")


def duration(path):
    try:
        out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path],
                             capture_output=True, text=True).stdout.strip()
        s = int(float(out))
        return f"{s // 60}:{s % 60:02d}"
    except Exception:
        return ""


def main():
    vids = json.load(open(os.path.join(HERE, "videos.json"), encoding="utf-8"))
    lines = ["# 영상 가이드", "",
             "Create 의 Ponder 처럼 기능마다 짧은 영상이 있어요. 블록은 **게임의 실제 텍스처**, 소리는 **마인크래프트·Create 의 실제 효과음**으로 만들었어요",
             "(CI 가 렌더링할 때 게임 파일에서 읽고, 저장소에는 영상만 들어가요). 썸네일을 누르면 MP4 가 열려요.", ""]
    overview = [v for v in vids if not v["id"].startswith("g_")]
    guides = [v for v in vids if v["id"].startswith("g_")]

    def table(items, cols):
        out = []
        for i in range(0, len(items), cols):
            row = items[i:i + cols]
            out.append("| " + " | ".join(f"[![{v['title']}](videos/{v['id']}.jpg)](videos/{v['id']}.mp4)" for v in row) + " |")
            if i == 0:
                out.insert(len(out) - 1, "| " + " | ".join(" " for _ in row) + " |")
                out.insert(len(out) - 1, "|" + "---|" * len(row))
            out.append("| " + " | ".join(f"**{v['title']}** {duration(os.path.join(VID, v['id'] + '.mp4'))}<br>{v['sub']}" for v in row) + " |")
        return out

    lines += ["## 한눈에 보기", ""] + table(overview, 2) + ["", "## 기능별 가이드", ""] + table(guides, 3)
    with open(os.path.join(REPO, "docs", "VIDEOS.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print("docs/VIDEOS.md written:", len(vids), "videos")


if __name__ == "__main__":
    main()
