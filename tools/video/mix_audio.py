"""CI 전용: render.mjs 가 남긴 효과음 이벤트(<영상>.sfx.json)를 마인크래프트·Create 의 실제 소리로 섞어 WAV 로 만든다.

사용법: python3 tools/video/mix_audio.py <영상>.sfx.json --index <에셋 색인 json> --objects <에셋 objects 폴더> \
            --create <Create Fly jar> --out <영상>.wav
이벤트: [초, 이름, 음량, 음높이, (길이)]. 같은 이름의 변형(돌 소리 1~4 등)은 번갈아 쓴다. 소리 파일은 저장소에 넣지 않는다.
"""
import argparse
import json
import os
import subprocess
import tempfile
import zipfile

import numpy as np

RATE = 44100

# 이름 → 후보 소리(마인크래프트 에셋 키, 또는 create: 로 시작하면 Create jar 안 경로)
SOUNDS = {
    "whoosh": ["ui/toast/in"], "click": ["random/click"], "pop": ["random/pop"], "wood_click": ["random/wood_click"],
    "place_stone": [f"dig/stone{i}" for i in range(1, 5)], "place_wood": [f"dig/wood{i}" for i in range(1, 5)],
    "place_gravel": [f"dig/gravel{i}" for i in range(1, 5)], "place_grass": [f"dig/grass{i}" for i in range(1, 5)],
    "step_stone": [f"step/stone{i}" for i in range(1, 7)], "step_wood": [f"step/wood{i}" for i in range(1, 7)],
    "step_gravel": [f"step/gravel{i}" for i in range(1, 5)], "step_grass": [f"step/grass{i}" for i in range(1, 7)],
    "glass": [f"random/glass{i}" for i in range(1, 4)], "minecart": ["minecart/base"], "minecart_inside": ["minecart/inside"],
    "bell": ["block/bell/bell_use01"], "pling": ["note/pling"], "harp": ["note/harp"], "note_bell": ["note/bell"],
    "icechime": ["note/icechime"], "xylophone": ["note/iron_xylophone"], "flute": ["note/flute"], "bass": ["note/bass"],
    "orb": ["random/orb"], "levelup": ["random/levelup"], "complete": ["ui/toast/challenge_complete"],
    "door_open": [f"block/iron_door/open{i}" for i in range(1, 5)], "door_close": [f"block/iron_door/close{i}" for i in range(1, 5)],
    "piston_out": ["tile/piston/out"], "piston_in": ["tile/piston/in"],
    "water": ["liquid/water"], "splash": ["liquid/splash"], "swim": [f"liquid/swim{i}" for i in range(1, 6)],
    "paddle": [f"entity/boat/paddle_water{i}" for i in range(1, 9)],
    "gallop": [f"mob/horse/gallop{i}" for i in range(1, 5)], "horse": ["mob/horse/idle1"], "horse_armor": ["mob/horse/armor"],
    "anvil": ["random/anvil_use"], "portal": ["portal/trigger"], "portal_travel": ["portal/travel"], "fire": ["fire/fire"],
    "enchant": [f"block/enchantment_table/enchant{i}" for i in range(1, 4)], "beacon": ["block/beacon/activate"],
    "beacon_power": ["block/beacon/power1"], "amethyst": [f"block/amethyst/resonate{i}" for i in range(1, 5)],
    "shimmer": ["block/amethyst/shimmer"], "twinkle": ["fireworks/twinkle1"], "blast": ["fireworks/largeblast1"],
    "explode": ["random/explode1"], "draw": [f"ui/cartography_table/drawmap{i}" for i in range(1, 4)],
    "cut": ["ui/stonecutter/cut1", "ui/stonecutter/cut2"], "lantern": [f"block/lantern/place{i}" for i in range(1, 6)],
    "chain": [f"block/chain/step{i}" for i in range(1, 7)], "barrel": ["block/barrel/open1", "block/barrel/open2"],
    "anchor": ["block/respawn_anchor/charge1"], "rain": ["ambient/weather/rain1"],
    "train_whistle": ["create:whistle_train"], "train": ["create:train", "create:train2", "create:train3"],
    "steam": ["create:steam"], "desk_bell": ["create:desk_bell"], "cogs": ["create:cogs"], "confirm": ["create:confirm_2"],
    "fwoomp": ["create:fwoomp"], "clipboard": ["create:clipboard_check"], "spout": ["create:spout"],
}

def decode(data):
    with tempfile.NamedTemporaryFile(suffix=".ogg", delete=False) as f:
        f.write(data)
        name = f.name
    try:
        raw = subprocess.run(["ffmpeg", "-v", "error", "-i", name, "-f", "f32le", "-ac", "1", "-ar", str(RATE), "-"],
                             capture_output=True, check=True).stdout
    finally:
        os.unlink(name)
    return np.frombuffer(raw, dtype=np.float32).copy()

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("events")
    ap.add_argument("--index", required=True)
    ap.add_argument("--objects", required=True)
    ap.add_argument("--create", required=True)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    spec = json.load(open(a.events))
    objects = json.load(open(a.index))["objects"]
    cz = zipfile.ZipFile(a.create)
    cache, counters = {}, {}

    def clip(key):
        if key in cache:
            return cache[key]
        data = None
        if key.startswith("create:"):
            try:
                data = cz.read(f"assets/create/sounds/{key[7:]}.ogg")
            except KeyError:
                pass
        else:
            o = objects.get(f"minecraft/sounds/{key}.ogg")
            if o:
                h = o["hash"]
                p = os.path.join(a.objects, h[:2], h)
                if os.path.exists(p):
                    data = open(p, "rb").read()
        cache[key] = decode(data) if data else None
        if data is None:
            print("missing sound", key)
        return cache[key]

    total = int((spec["total"] + 1.0) * RATE)
    mix = np.zeros(total, dtype=np.float32)
    for ev in spec["events"]:
        t, name, vol, pitch = ev[0], ev[1], ev[2], ev[3]
        dur = ev[4] if len(ev) > 4 else None
        keys = SOUNDS.get(name)
        if not keys:
            print("unknown sfx", name)
            continue
        n = counters.get(name, 0)
        counters[name] = n + 1
        snd = clip(keys[n % len(keys)])
        if snd is None or len(snd) == 0:
            continue
        if pitch and abs(pitch - 1.0) > 1e-3:  # 음높이: 재생 속도를 바꾼다(마인크래프트와 같은 방식)
            idx = np.arange(0, len(snd) - 1, pitch)
            snd = np.interp(idx, np.arange(len(snd)), snd).astype(np.float32)
        if dur:  # 길이 제한(반복되는 소리용): 끝을 부드럽게 줄인다
            m = int(dur * RATE)
            if len(snd) < m:
                snd = np.tile(snd, m // len(snd) + 1)
            snd = snd[:m].copy()
            fade = min(len(snd), int(0.25 * RATE))
            snd[-fade:] *= np.linspace(1, 0, fade, dtype=np.float32)
        start = int(t * RATE)
        end = min(total, start + len(snd))
        mix[start:end] += snd[: end - start] * float(vol)
    peak = float(np.max(np.abs(mix))) or 1.0
    mix *= min(1.0, 0.89 / peak)
    pcm = (mix * 32767).astype(np.int16)
    import wave
    with wave.open(a.out, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(pcm.tobytes())
    print(f"mixed {len(spec['events'])} events -> {a.out}")

if __name__ == "__main__":
    main()
