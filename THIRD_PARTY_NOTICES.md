# Third-party notices / 제3자 고지

InfraCraft's own code and generated assets are © menonng (see `fabric.mod.json`).
InfraCraft 의 코드와 직접 생성한 에셋(텍스처·데이터)은 menonng 의 것입니다(`fabric.mod.json` 참고).

## Bundled in the mod jar / 모드 jar 에 함께 들어 있는 것

| File | Source | License |
|---|---|---|
| `assets/create_enchantment_industry/lang/ko_kr.json` | Korean translation of the English strings of [Create: Enchantment Industry](https://github.com/DragonsPlusMinecraft/CreateEnchantmentIndustry) (DragonsPlus; Fly port by [GravisLudio](https://github.com/GravisLudio/create-enchantment-industry-fly-26.2)) | LGPL-3.0-or-later, same as the original strings. Full text: `licenses/LGPL-3.0-and-GPL-3.0.txt` |
| `assets/create_dragons_plus/lang/ko_kr.json` | Korean translation of the English strings of [Create: Dragons Plus](https://github.com/DragonsPlusMinecraft/CreateDragonsPlus) (Fly port by [GravisLudio](https://github.com/GravisLudio/create-dragons-plus-fly-26.2)) | LGPL-3.0-or-later. Full text: `licenses/LGPL-3.0-and-GPL-3.0.txt` |
| `assets/createdeco/lang/ko_kr.json` | Korean translation of the English strings of [Create Deco](https://github.com/talrey/CreateDeco) | CC0-1.0 (public domain), same as the original |

These three files are licensed separately from the rest of InfraCraft under the license shown.
위 세 파일은 InfraCraft 의 나머지와 따로, 표에 적힌 라이선스를 따릅니다.

## In this repository only / 저장소에만 있는 것

- `tools/translations/sources/*.en_us.json`: the original English strings of the three mods above (same licenses), used to check the translations for missing keys and placeholders.
- `docs/videos/*.mp4`, `docs/recipes/*.png`: rendered from Minecraft and Create Fly game files when the documentation is built (gameplay media). The game asset files themselves are not stored in the repository.
- `licenses/LGPL-3.0-and-GPL-3.0.txt`: the GNU LGPL 3.0 together with the GNU GPL 3.0 it builds on.

## Integration without bundling / 함께 넣지 않고 연동만 하는 것

- [Create](https://github.com/Creators-of-Create/Create) / [Create Fly](https://github.com/ZurrTum/Create-Fly): read through reflection at runtime; nothing from Create (code or assets) is included. Create's assets are All Rights Reserved.
- Fabric API, Fabric Language Kotlin (Apache-2.0): runtime dependencies, not bundled.

## Inspiration only / 아이디어만 참고한 것

- Astra Obliterator's skill presentation takes the flow of [The Shooting Star [Demo]](https://www.curseforge.com/minecraft/mc-mods/the-shooting-star-demo) by rimuru_dev (All Rights Reserved) as a reference: cinematic space view, constellation projection, stars falling in turn. No code, models, textures or sounds from it are used; everything was written and generated for InfraCraft.
- Bridge kit designs follow common building patterns from public Minecraft bridge tutorials; no schematics were copied.

## Modpack / 모드팩

The `.mrpack` only lists download addresses for other mods; it bundles InfraCraft itself and the `InfraCraft-KO` resource pack.
That pack contains InfraCraft's own translations above and, where an original addon's license allows redistribution, that addon's Korean strings (see `CREDITS.txt` inside the pack).
