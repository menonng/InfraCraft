// 기능별 짧은 가이드 영상(Create 의 Ponder 처럼 한 기능씩). 모두 같은 틀: 제목 카드 → 블록 디오라마 시연 → 끝 카드.
const GUIDES = (() => {
  const L = 'tunnel_lining', LIGHT = 'tunnel_light', EDGE = 'platform_edge', TRACK = 'create_track';
  const GRASS = { top: 'grass_top', side: 'grass_side' };
  const DOOR = { top: 'screen_door_frame', side: 'screen_door_glass', front: 'screen_door_glass' };
  const ELEV = { top: 'station_elevator_top', side: 'station_elevator_side' };

  // ── 공용 장면 ──
  function titleCard(meta) {
    return {
      draw(ctx, t, d) {
        background(ctx, t, meta.tint);
        const a = inOut(t, 0, d, 0.4);
        const p = easeBack(seg(t, 0.15, 0.8));
        if (meta.cube) {
          const iso = new Iso(W / 2, 330, 64 * clamp(p));
          iso.cube(ctx, -0.5, 0, -0.5, meta.cube, { alpha: a, glow: meta.glow });
        } else sprite(ctx, meta.sprite, W / 2, 300, 150 * clamp(p), a);
        text(ctx, 'InfraCraft 가이드', W / 2, 120, { size: 26, font: 'pixel', align: 'center', color: meta.color, alpha: a, shadow: true });
        text(ctx, meta.title, W / 2, 470, { size: 60, weight: 800, align: 'center', alpha: a * seg(t, 0.35, 0.9) });
        text(ctx, meta.sub, W / 2, 525, { size: 25, align: 'center', color: C.dim, alpha: a * seg(t, 0.6, 1.2) });
      },
      sfx: () => [[0.15, 'pling', 0.5, 1.33], [0.3, meta.cube ? 'place_stone' : 'pop', 0.7, 1.0], [0.7, 'harp', 0.35, 1.6]],
    };
  }
  function endCard(meta) {
    return {
      draw(ctx, t, d) {
        background(ctx, t, meta.tint);
        const a = inOut(t, 0, d, 0.4);
        text(ctx, 'InfraCraft', W / 2, 300, { size: 40, font: 'pixel', align: 'center', color: meta.color, alpha: a, shadow: true });
        text(ctx, meta.title, W / 2, 360, { size: 30, weight: 700, align: 'center', alpha: a });
        text(ctx, '조합법은 docs/ITEMS.md · 게임 안 레시피 책', W / 2, 410, { size: 21, align: 'center', color: C.dim, alpha: a * seg(t, 0.3, 0.8) });
      },
      sfx: () => [[0.1, 'note_bell', 0.4, 1.0], [0.35, 'note_bell', 0.4, 1.26]],
    };
  }
  function make(meta, demos) {
    const scenes = [];
    let t = 0;
    const add = (dur, s) => { scenes.push({ from: t, to: t + dur, draw: s.draw, sfx: s.sfx }); t += dur; };
    add(3.2, titleCard(meta));
    for (const s of demos) add(s.dur, s);
    add(2.6, endCard(meta));
    return { total: t, scenes, meta };
  }
  const cap = (meta, t, list) => {
    let s = list[0][1];
    for (const [at, str] of list) if (t >= at) s = str;
    return s;
  };
  // 사람(노란 막대)
  function person(ctx, iso, x, y, z, a, col = C.yellow) {
    const [sx, sy] = iso.pt(x, y, z);
    ctx.save(); ctx.globalAlpha = a; ctx.fillStyle = col; roundRect(ctx, sx - 6, sy - 28, 12, 28, 4); ctx.fill(); ctx.restore();
  }
  const cart = (bl, x, y, z, a) => bl.push({ x, y, z, tex: { top: 'cart_top', side: 'cart_side' }, scale: 0.8, alpha: a });
  const boat = (bl, x, y, z, a) => bl.push({ x, y, z, tex: 'planks', scale: 0.75, alpha: a });

  // ═══════════════ 1. 도로와 옛길 ═══════════════
  const roadsMeta = { id: 'g_roads', title: '도로와 옛길', sub: '깐 길 위에서 걸음이 가벼워집니다', sprite: null, cube: 'setts', color: C.orange, tint: 'rgba(255,126,0,0.08)' };
  const roads = make(roadsMeta, [{
    dur: 12,
    draw(ctx, t, d) {
      background(ctx, t, roadsMeta.tint);
      const a = inOut(t, 0, d);
      const iso = new Iso(230, 250, 30);
      const tiers = [['gravel', 1], ['cobblestone', 2], ['setts', 3], ['brick_paving', 3], ['flagstone', 3], ['asphalt', 3]];
      const bl = [];
      for (let x = 0; x < 18; x++) for (let z = -1; z <= 1; z++) {
        const i = Math.min(5, Math.floor(x / 3));
        const p = pop(t, 0.1 + x * 0.05 + (z + 1) * 0.02, 0.35);
        bl.push({ x, y: 0, z, tex: tiers[i][0] === 'asphalt' && z === 0 ? 'road_line' : tiers[i][0], alpha: a * p.alpha, lift: p.lift });
      }
      bl.push({ x: 9, y: 1, z: -2, tex: { top: 'milestone_top', side: 'milestone' }, scale: 0.6, alpha: a * seg(t, 1.6, 2.0) });
      drawBlocks(ctx, iso, bl);
      const walk = seg(t, 1.5, 10.5) * 18;
      if (walk < 17.5) person(ctx, iso, walk, 1, 0.5, a);
      const names = ['흙길·자갈 1등급', '돌 2등급', '포석길', '벽돌 포장길', '판석길', '아스팔트'];
      names.forEach((n, i) => { const [lx, ly] = iso.pt(i * 3 + 1.5, 0, 2.7); text(ctx, n, lx, ly + 30, { size: 16, weight: 700, align: 'center', color: i >= 2 ? C.yellow : C.dim, alpha: a }); });
      if (walk > 8.6 && walk < 12) { const [mx, my] = iso.pt(9.5, 2.2, -1.5); actionbar(ctx, '이정석  ↗ 광산역 1.2km', mx, my, C.sky, a); }
      panel(ctx, 930, 90, 310, 130, { alpha: a, accent: C.orange });
      text(ctx, '3등급(+32%)', 960, 132, { size: 24, weight: 800, alpha: a });
      text(ctx, '포석 · 벽돌 · 판석 · 아스팔트', 960, 168, { size: 18, color: C.dim, alpha: a });
      text(ctx, '정취는 달라도 속도는 같습니다', 960, 196, { size: 18, color: C.dim, alpha: a });
      caption(ctx, roadsMeta.title, cap(roadsMeta, t, [[0, '흙길 → 돌길 → 옛길, 등급이 높을수록 빨라집니다'], [5, '포석·벽돌·판석길은 아스팔트와 같은 3등급입니다'], [8.5, '길가의 이정석을 지나면 가까운 역까지의 방향과 거리를 알려 줍니다']]), a * seg(t, 0.3, 0.8), C.orange);
    },
    sfx: (d) => [...Array.from({ length: 18 }, (_, x) => [0.1 + x * 0.05, ['place_gravel', 'place_stone'][x < 3 ? 0 : 1], 0.35, 1.0]),
      ...every(1.5, 10.5, 0.42, 'step_stone', 0.35, 1.0, 0.03), [1.6, 'place_stone', 0.6, 0.8], [6.0, 'xylophone', 0.4, 1.2]],
  }]);

  // ═══════════════ 2. 등급 선로 ═══════════════
  const railsMeta = { id: 'g_rails', title: '등급 선로', sub: '전원이 들어오면 최고 속도가 오릅니다', cube: null, sprite: 'express_rail_on', color: C.sky, tint: 'rgba(57,197,187,0.08)' };
  const rails = make(railsMeta, [{
    dur: 12,
    draw(ctx, t, d) {
      background(ctx, t, railsMeta.tint);
      const a = inOut(t, 0, d);
      const iso = new Iso(200, 200, 26);
      const rows = [['rail', '바닐라 0.4', 0.4], ['high_speed_rail_on', '고속 0.6', 0.6], ['express_rail_on', '특급 0.8', 0.8], ['blaze_rail_on', '블레이즈 1.1 (네더)', 1.1]];
      const bl = [];
      rows.forEach(([tex], r) => {
        const z = -r * 3;
        for (let x = 0; x < 20; x++) {
          bl.push({ x, y: -1, z, tex: r === 3 ? 'netherrack' : 'stone', alpha: a });
          bl.push({ x, y: 0, z, tex, flat: true, rotate: true, alpha: a });
          if (x % 5 === 2 && r > 0) bl.push({ x, y: -1, z: z - 1, tex: 'redstone_block', alpha: a });
        }
      });
      const run = seg(t, 1.0, 9.0);
      rows.forEach(([, , v], r) => { const x = Math.min(19, run * 8 * v * 2.6); cart(bl, x, 0, -r * 3, a); });
      drawBlocks(ctx, iso, bl);
      rows.forEach(([, label], r) => { const [lx, ly] = iso.pt(-0.6, 0.5, -r * 3 + 0.5); text(ctx, label, lx - 10, ly, { size: 17, weight: 700, align: 'right', color: C.dim, alpha: a }); });
      caption(ctx, railsMeta.title, cap(railsMeta, t, [[0, '같은 시간 동안 등급이 높은 선로가 더 멀리 갑니다 (블록/틱)'], [5, '커브·경사·선로 끝 앞에서는 자동으로 감속합니다'], [8, '블레이즈 선로는 네더에서만, 블레이즈 가루 1개로 128블록']]), a * seg(t, 0.3, 0.8), C.sky);
    },
    sfx: () => [[1.0, 'minecart', 0.5, 1.0, 8.0], [1.1, 'minecart_inside', 0.25, 1.3, 7.5], [8.2, 'fire', 0.5, 1.0, 2.0]],
  }]);

  // ═══════════════ 3. 역 · 승차권 · 분기기 ═══════════════
  const stMeta = { id: 'g_stations', title: '역 · 승차권 · 분기기', sub: '목적지를 정하면 카트가 알아서 갈림길을 고릅니다', sprite: 'ticket', color: C.yellow, tint: 'rgba(255,204,17,0.07)' };
  const stations = make(stMeta, [{
    dur: 13,
    draw(ctx, t, d) {
      background(ctx, t, stMeta.tint);
      const a = inOut(t, 0, d);
      const iso = new Iso(220, 230, 28);
      const bl = [];
      for (let x = 0; x < 9; x++) { bl.push({ x, y: -1, z: 0, tex: x === 7 ? 'rail_switch' + (t > 4 && t < 7 ? '_on' : '') : 'stone', alpha: a }); bl.push({ x, y: 0, z: 0, tex: 'rail', flat: true, rotate: true, alpha: a }); }
      for (let x = 9; x < 18; x++) { bl.push({ x, y: -1, z: 0, tex: x === 15 ? 'station' : 'stone', alpha: a }); bl.push({ x, y: 0, z: 0, tex: 'rail', flat: true, rotate: true, alpha: a }); }
      for (let i = 0; i < 9; i++) { const x = 8 + i, z = -1 - Math.min(i, 3); bl.push({ x, y: -1, z, tex: x === 14 ? 'station' : 'stone', alpha: a }); bl.push({ x, y: 0, z, tex: 'rail', flat: true, rotate: true, alpha: a }); }
      const m = seg(t, 0.8, 7.5);
      let cx = 0, cz = 0;
      if (m < 0.55) cx = m / 0.55 * 8; else { const f = (m - 0.55) / 0.45; cx = 8 + f * 6; cz = -Math.min(f * 6, 3) - 1 * (f > 0 ? 1 : 0); }
      cart(bl, cx, 0, cz, a);
      drawBlocks(ctx, iso, bl);
      sprite(ctx, 'ticket', ...iso.pt(cx + 0.5, 1.6, cz + 0.5), 34, a);
      const [sx, sy] = iso.pt(14.5, 0.5, -3.5); text(ctx, '광산역', sx, sy - 40, { size: 20, weight: 800, align: 'center', alpha: a });
      const [hx, hy] = iso.pt(15.5, 0.5, 0.5); text(ctx, '항구역', hx, hy + 40, { size: 20, weight: 800, align: 'center', color: C.dim, alpha: a });
      if (t > 7.6 && t < 11) actionbar(ctx, '도착: 광산역', W / 2, 600, C.green, a * inOut(t, 7.6, 11));
      panel(ctx, 930, 90, 310, 120, { alpha: a, accent: C.yellow });
      text(ctx, '승차권 목적지: 광산역', 960, 132, { size: 21, weight: 800, alpha: a });
      text(ctx, '분기기에 광산역 등록 → 전환', 960, 168, { size: 18, color: C.dim, alpha: a });
      caption(ctx, stMeta.title, cap(stMeta, t, [[0, '승차권을 허공에 쓰면 목적지가 바뀝니다. 역에 쓰면 그 역이 목적지가 됩니다'], [4, '분기기에 승차권을 쓰면 그 목적지로 가는 카트가 올 때 선로를 바꿉니다'], [7.6, '목적지 역에 닿으면 섭니다. 다른 역은 지나갑니다']]), a * seg(t, 0.3, 0.8), C.yellow);
    },
    sfx: () => [[0.5, 'click', 0.6, 1.2], [0.9, 'minecart', 0.45, 1.0, 6.6], [4.1, 'piston_out', 0.6, 1.0], [4.15, 'click', 0.5, 0.9], [7.6, 'bell', 0.6, 1.0], [7.0, 'piston_in', 0.5, 1.0]],
  }]);

  // ═══════════════ 4. ATS ═══════════════
  const atsMeta = { id: 'g_ats', title: 'ATS · 충돌 방지', sub: '카트가 앞을 보고 스스로 멈춥니다', sprite: 'high_speed_rail_on', color: C.yellow, tint: 'rgba(255,204,17,0.06)' };
  const ats = make(atsMeta, [{
    dur: 12,
    draw(ctx, t, d) {
      background(ctx, t, atsMeta.tint);
      const a = inOut(t, 0, d);
      const iso = new Iso(250, 225, 30);
      const N = 22, bl = [];
      for (let x = 0; x < N; x++) for (const z of [0, -5]) { bl.push({ x, y: -1, z, tex: x === 13 && z === 0 ? 'station' : 'stone', alpha: a }); bl.push({ x, y: 0, z, tex: 'rail', flat: true, rotate: true, alpha: a }); }
      const dt = 1 / 60;
      let lead = 2, fol = -4, vf = 0, l = 0, r = N - 1;
      for (let s = 0; s < t; s += dt) {
        lead = s < 6.5 ? Math.min(13, lead + 6 * dt) : lead + 7 * dt;
        vf = Math.min(6, Math.max(0, (lead - fol - 5) * 3), vf + 10 * dt); fol += vf * dt;
        const gap = r - l - 1; if (s < 8) { const v = Math.min(6, Math.max(0, (gap - 8) * 3)); l += v * dt; r -= v * dt; } else l -= 2 * dt;
      }
      for (const [x, z] of [[lead, 0], [fol, 0], [l, -5], [r, -5]]) if (x > -0.5 && x < N - 0.5) cart(bl, x, 0, z, a);
      drawBlocks(ctx, iso, bl);
      if (t > 1.5 && lead - fol - 1 < 7 && lead < N - 0.5 && fol > -0.5) { const [px, py] = iso.pt(fol + 0.5, 2.2, 0.5); actionbar(ctx, `ATS · ${Math.round(lead - fol - 1)}칸`, px, py, C.yellow, a); }
      if (r - l - 1 <= 8.1 && t < 8) for (const x of [l, r]) { const [px, py] = iso.pt(x + 0.5, 2.2, -4.5); actionbar(ctx, 'ATS', px, py, C.orange, a); }
      if (t > 8) { const [px, py] = iso.pt(l + 0.5, 2.2, -4.5); actionbar(ctx, '◀ 물러남', px, py, C.sky, a); }
      caption(ctx, atsMeta.title, cap(atsMeta, t, [[0, '카트는 선로를 따라 앞 24칸을 살핍니다'], [3, '앞차가 서 있으면 4칸 뒤에서 기다렸다가, 비면 다시 출발합니다'], [7, '단선에서 맞은편 차가 오면 8칸 앞에서 둘 다 섭니다'], [9.5, '그래서 지하철 키트는 기본이 복선입니다']]), a * seg(t, 0.3, 0.8), C.yellow);
    },
    sfx: () => [[0.3, 'minecart', 0.45, 1.0, 11], [2.2, 'xylophone', 0.45, 0.8], [2.6, 'xylophone', 0.45, 0.8], [4.4, 'xylophone', 0.45, 0.8], [6.6, 'pling', 0.4, 1.4], [5.2, 'xylophone', 0.45, 0.7], [5.6, 'xylophone', 0.45, 0.7], [8.1, 'wood_click', 0.6, 1.0]],
  }]);

  // ═══════════════ 5. 노선망 · 이정표 · 이정석 ═══════════════
  const netMeta = { id: 'g_network', title: '노선망 · 이정표', sub: '실제로 달린 길만 노선이 됩니다', cube: 'signpost', color: C.green, tint: 'rgba(85,187,68,0.08)' };
  const network = make(netMeta, [{
    dur: 12,
    draw(ctx, t, d) {
      background(ctx, t, netMeta.tint);
      const a = inOut(t, 0, d);
      const N = { A: [200, 380], B: [420, 220], C: [460, 520], D: [720, 330], E: [960, 200], F: [1060, 480] };
      const E = [['A', 'B'], ['A', 'C'], ['B', 'D'], ['C', 'D'], ['D', 'E'], ['D', 'F'], ['E', 'F']];
      const best = ['A', 'B', 'D', 'F'];
      ctx.save(); ctx.globalAlpha = a; ctx.lineCap = 'round';
      E.forEach(([p, q], i) => { const s = seg(t, 0.3 + i * 0.4, 0.8 + i * 0.4); ctx.strokeStyle = '#3a455c'; ctx.lineWidth = 8; ctx.beginPath(); ctx.moveTo(...N[p]); ctx.lineTo(lerp(N[p][0], N[q][0], s), lerp(N[p][1], N[q][1], s)); ctx.stroke(); });
      const hl = seg(t, 4.5, 6.0);
      for (let i = 0; i < best.length - 1; i++) { const s = clamp(hl * (best.length - 1) - i); if (s <= 0) continue; const [p, q] = [N[best[i]], N[best[i + 1]]]; ctx.strokeStyle = C.green; ctx.lineWidth = 10; ctx.beginPath(); ctx.moveTo(...p); ctx.lineTo(lerp(p[0], q[0], s), lerp(p[1], q[1], s)); ctx.stroke(); }
      ctx.restore();
      for (const [k, [x, y]] of Object.entries(N)) { sprite(ctx, 'station', x, y, 44, a); text(ctx, k + '역', x, y + 44, { size: 18, weight: 700, align: 'center', alpha: a }); }
      if (t > 7) { panel(ctx, 820, 560, 400, 70, { alpha: a, accent: C.sky }); text(ctx, '↗ D역 820m   → F역 1.6km', 850, 603, { size: 21, weight: 700, alpha: a }); }
      caption(ctx, netMeta.title, cap(netMeta, t, [[0, '기차·카트·보트가 역 사이를 실제로 달리면 그 구간이 노선이 됩니다'], [4.5, '승차권으로 역을 누르면 가장 빠른 경로를 알려 줍니다. 순간이동은 없습니다'], [7, '이정표·이정석은 가까운 역의 방향과 거리를 알려 줍니다']]), a * seg(t, 0.3, 0.8), C.green);
    },
    sfx: () => [...[0.3, 0.7, 1.1, 1.5, 1.9, 2.3, 2.7].map((t, i) => [t, 'draw', 0.5, 1.0 + i * 0.02]), [4.5, 'pling', 0.4, 1.0], [5.0, 'pling', 0.4, 1.2], [5.5, 'pling', 0.4, 1.4], [7.0, 'wood_click', 0.6, 1.0]],
  }]);

  // ═══════════════ 6. 실드 굴진기 · 복선 터널 ═══════════════
  const shMeta = { id: 'g_shield', title: '실드 굴진기 · 복선 터널', sub: '두 선로 사이에 서서 앞을 보고 쓰세요', sprite: 'subway_shield', color: C.blue, tint: 'rgba(51,85,187,0.10)' };
  const shield = make(shMeta, [{
    dur: 15,
    draw(ctx, t, d) {
      background(ctx, t, shMeta.tint);
      const a = inOut(t, 0, d);
      const iso = new Iso(330, 300, 22);
      const N = 10, bl = [];
      const at = (k) => 1.0 + k * 1.15;
      let done = 0;
      for (let x = 0; x < N; x++) {
        const tk = at(x); if (t >= tk) done = x + 1;
        for (let z = -12; z <= 0; z++) for (let y = -2; y <= 6; y++) {
          const interior = z >= -11 && z <= -1 && y >= 0 && y <= 4;
          const shell = !interior && ((z === -12 && y >= -1 && y <= 5) || ((y === -1 || y === 5) && z >= -12 && z <= 0) || (z === 0 && y >= -1 && y <= 5));
          if ((y === 5 && z >= -8) || (y === 6) || (z === 0 && y >= 0)) continue; // 앞쪽 벽과 천장은 잘라 낸 단면
          const rock = y <= -1 ? 'deepslate' : 'stone';
          if (interior) { const v = vanish(t, tk + 0.1 + (4 - y) * 0.03, 0.3); if (v.alpha > 0) bl.push({ x, y, z, tex: rock, ...v, alpha: v.alpha * a }); }
          else if (shell) { const isL = y === 5 && (z === -9 || z === -3) && x % 8 === 0; if (t < tk) bl.push({ x, y, z, tex: rock, alpha: a }); else { const p = pop(t, tk, 0.3); bl.push({ x, y, z, tex: isL ? LIGHT : L, alpha: a * p.alpha, lift: p.lift * 0.5 }); } }
          else bl.push({ x, y, z, tex: rock, alpha: a });
        }
        for (const z of [-3, -9]) if (t >= tk + 0.4) bl.push({ x, y: 0, z, tex: TRACK, flat: true, rotate: true, alpha: a * seg(t, tk + 0.4, tk + 0.7) });
      }
      drawBlocks(ctx, iso, bl);
      if (done < N) { const [sx, sy] = iso.pt(done + 0.1, 2, -6); sprite(ctx, 'subway_shield', sx, sy + Math.sin(t * 12) * 3, 64, a, Math.sin(t * 20) * 0.08); }
      panel(ctx, 960, 100, 280, 112, { alpha: a, accent: C.blue });
      text(ctx, `단면 ${done} / ${N}`, 990, 144, { size: 28, weight: 800, alpha: a });
      text(ctx, '폭 11 × 높이 5 · 선로 2줄', 990, 182, { size: 19, color: C.dim, alpha: a });
      caption(ctx, shMeta.title, cap(shMeta, t, [[0, '쓸 때마다 앞쪽 단면이 한 칸씩 뚫립니다 (앞쪽 벽은 잘라 낸 단면도)'], [5, '라이닝으로 먼저 감싸고 비우므로 물과 용암이 새지 않습니다'], [9, '양옆에 선로 두 줄: 상행·하행이 서로 마주치지 않습니다'], [12.5, '아래나 위를 보고 쓰면 경사 터널이 됩니다']]), a * seg(t, 0.3, 0.8), C.blue);
    },
    sfx: () => { const out = []; for (let k = 0; k < 10; k++) { const tk = 1.0 + k * 1.15; out.push([tk, 'place_stone', 0.7, 0.8], [tk + 0.12, 'place_stone', 0.5, 1.0], [tk + 0.25, 'place_gravel', 0.4, 1.0], [tk + 0.45, 'chain', 0.4, 1.2]); } return out; },
  }]);

  // ═══════════════ 7. 지하 역사 ═══════════════
  const hallMeta = { id: 'g_hall', title: '지하 역사', sub: '상대식 복선 승강장 · 안전문 · 승강기', sprite: 'station_blueprint', color: C.pink, tint: 'rgba(255,180,204,0.08)' };
  const hall = make(hallMeta, [{
    dur: 16,
    draw(ctx, t, d) {
      background(ctx, t, hallMeta.tint);
      const a = inOut(t, 0, d);
      const iso = new Iso(470, 470, 21);
      const bl = [];
      const doorsOpen = t > 8.4 && t < 11.6;
      const doorA = 1 - 0.85 * (seg(t, 8.2, 8.6) - seg(t, 11.4, 11.8));
      for (let k = -7; k <= 8; k++) for (let o = -9; o <= 0; o++) {
        const add = (y, tex, ex = {}) => { const p = pop(t, 0.3 + (k + 7) * 0.05 + Math.max(0, y) * 0.04, 0.35); bl.push({ x: k, y, z: o, tex, alpha: a * p.alpha, lift: p.lift, ...ex }); };
        add(-1, L);
        if (o === -9) for (let y = 0; y <= 5; y++) add(y, L);
        if (o <= -6 && o >= -8) { add(0, L); add(1, o === -6 ? EDGE : (k === 6 && o === -8 ? ELEV : L)); }
        if (o === -6) for (const y of [2, 3]) bl.push({ x: k, y, z: o, tex: DOOR, alpha: a * doorA * seg(t, 2.0, 2.6) });
        if (o <= -8) add(6, o === -8 && Math.floor(k) % 4 === 0 ? LIGHT : L);
        if (o === -3) bl.push({ x: k, y: 0, z: o, tex: TRACK, flat: true, rotate: true, alpha: a * seg(t, 1.0, 1.4) });
      }
      // 앞쪽 선로(1번선)는 바닥만, 뒤쪽(2번선)에 차량이 선다
      const arrive = easeOut(seg(t, 4.6, 8.0));
      const cx = lerp(-22, -4, arrive);
      if (t > 4.6) for (let i = 0; i < 9; i++) {
        const x = cx + i; if (x < -7.5) continue;
        for (const o of [-5, -4, -3, -2, -1]) bl.push({ x, y: 1, z: o, tex: 'car_body', alpha: a });
        const door = i === 2 || i === 6;
        for (const y of [2, 3]) { if (door && doorsOpen) continue; bl.push({ x, y, z: -5, tex: door ? 'car_door' : (y === 3 ? 'car_window' : 'car_body'), alpha: a }); }
        for (const o of [-5, -4]) bl.push({ x, y: 4, z: o, tex: 'car_body', alpha: a });
      }
      drawBlocks(ctx, iso, bl);
      const walk = seg(t, 9.0, 11.0), ride = seg(t, 12.0, 15.0);
      if (walk > 0) { const px = lerp(cx + 2.5, 6.5, walk), pz = lerp(-4.5, -7.5, walk); const [sx, sy] = iso.pt(px, 2 + ride * 8, pz); ctx.save(); ctx.globalAlpha = a; ctx.fillStyle = C.yellow; roundRect(ctx, sx - 6, sy - 26, 12, 26, 4); ctx.fill(); ctx.restore(); if (ride > 0) actionbar(ctx, '▲ 승강기', sx + 70, sy - 10, C.green, a); }
      caption(ctx, hallMeta.title, cap(hallMeta, t, [[0, '두 선로 사이에 서서 설계도를 쓰면 상대식 복선 역사가 지어집니다'], [2.2, '승강장은 선로보다 한 칸 높고, 가장자리에 안전선과 안전문이 섭니다'], [4.6, 'Create 전동차 바닥과 승강장 높이가 같아 턱 없이 탑니다'], [8.4, '차량이 멈추면 안전문이 열리고, 떠나면 닫힙니다'], [12, '승강기는 순간이동이 아니라 수직구로 실제로 올라갑니다']]), a * seg(t, 0.3, 0.8), C.pink);
    },
    sfx: () => [...every(0.3, 1.1, 0.08, 'place_stone', 0.3, 1.0, 0.05), [2.0, 'glass', 0.35, 1.3], [4.6, 'train', 0.6, 1.0, 3.6], [7.8, 'steam', 0.4, 1.0], [8.2, 'door_open', 0.7, 1.1], [11.4, 'door_close', 0.7, 1.1], [12.0, 'piston_out', 0.5, 0.8], [12.1, 'beacon_power', 0.3, 1.4], [14.9, 'pling', 0.4, 1.6]],
  }]);

  // ═══════════════ 8. 지상 역사 ═══════════════
  const surMeta = { id: 'g_surface', title: '지상 역사', sub: '지붕 아래 벤치와 등불, 시골 간이역', sprite: 'ground_station_blueprint', color: C.orange, tint: 'rgba(255,126,0,0.07)' };
  const surface = make(surMeta, [{
    dur: 12,
    draw(ctx, t, d) {
      background(ctx, t, surMeta.tint);
      const a = inOut(t, 0, d);
      const iso = new Iso(420, 330, 22);
      const bl = [];
      for (let k = -7; k <= 8; k++) for (let o = -9; o <= 9; o++) {
        const ao = Math.abs(o), p = pop(t, 0.2 + (k + 7) * 0.05, 0.35);
        if (ao < 6) { bl.push({ x: k, y: -1, z: o, tex: GRASS, alpha: a }); if (ao === 3) bl.push({ x: k, y: 0, z: o, tex: 'rail', flat: true, rotate: true, alpha: a * p.alpha }); continue; }
        if (ao > 8) { bl.push({ x: k, y: -1, z: o, tex: GRASS, alpha: a }); continue; }
        bl.push({ x: k, y: 0, z: o, tex: 'stone_bricks', alpha: a * p.alpha, lift: p.lift });
        bl.push({ x: k, y: 1, z: o, tex: ao === 6 ? EDGE : 'smooth_stone', alpha: a * p.alpha, lift: p.lift });
        if (o < 0 || t > 2.6) {
          const r = seg(t, 2.6 + (k + 7) * 0.04, 3.0 + (k + 7) * 0.04);
          if (ao === 8 && Math.floor(k + 70) % 4 === 2) for (let y = 2; y <= 4; y++) bl.push({ x: k, y, z: o, tex: 'spruce_log', alpha: a * r });
          if (o < 0) bl.push({ x: k, y: 5, z: o, tex: 'spruce', alpha: a * r, scale: 1 });
        }
      }
      drawBlocks(ctx, iso, bl);
      if (t > 3.4) for (const k of [-4, 0, 4]) { const [lx, ly] = iso.pt(k + 0.5, 4.6, -6.5); const g = ctx.createRadialGradient(lx, ly, 1, lx, ly, 34); g.addColorStop(0, 'rgba(255,200,90,0.85)'); g.addColorStop(1, 'rgba(255,200,90,0)'); ctx.save(); ctx.globalAlpha = a * seg(t, 3.4, 4.0); ctx.fillStyle = g; ctx.fillRect(lx - 34, ly - 34, 68, 68); ctx.restore(); }
      const tx = lerp(-25, 25, seg(t, 6.0, 11.0));
      for (let i = 0; i < 4; i++) { const x = tx + i * 1.2; if (x > -7.5 && x < 8.5) { const [cx, cy] = iso.pt(x, 0.6, 3.5); ctx.save(); ctx.globalAlpha = a; ctx.fillStyle = i === 0 ? C.orange : '#c9ced2'; roundRect(ctx, cx - 14, cy - 16, 28, 18, 5); ctx.fill(); ctx.restore(); } }
      caption(ctx, surMeta.title, cap(surMeta, t, [[0, '지상 선로 위에서도 복선 역사가 한 번에 지어집니다'], [2.6, '가문비나무 기둥과 지붕, 벤치, 매달린 등불, 이정표'], [6, '기차가 지나가는 모습을 승강장에서 바라보세요']]), a * seg(t, 0.3, 0.8), C.orange);
    },
    sfx: () => [...every(0.2, 1.0, 0.1, 'place_stone', 0.25, 1.0, 0.04), ...every(2.6, 3.3, 0.1, 'place_wood', 0.35, 1.0, 0.04), [3.4, 'lantern', 0.6, 1.0], [3.6, 'lantern', 0.5, 1.1], [5.8, 'train_whistle', 0.5, 1.0], [6.0, 'train', 0.5, 1.0, 5.0]],
  }]);

  // ═══════════════ 9. 전동차 · 객차 도면 ═══════════════
  const carMeta = { id: 'g_cars', title: '전동차 · 객차 도면', sub: 'Create 블록으로 차체를 짓고, Create 방식으로 조립합니다', sprite: 'metro_car_blueprint', color: C.sky, tint: 'rgba(57,197,187,0.08)' };
  const cars = make(carMeta, [{
    dur: 14,
    draw(ctx, t, d) {
      background(ctx, t, carMeta.tint);
      const a = inOut(t, 0, d);
      const iso = new Iso(330, 360, 30);
      const bl = [];
      for (let k = -1; k <= 10; k++) { bl.push({ x: k, y: -1, z: 0, tex: 'stone', alpha: a }); bl.push({ x: k, y: 0, z: 0, tex: TRACK, flat: true, rotate: true, alpha: a }); }
      const move = easeInOut(seg(t, 10.5, 13.5)) * 6;
      for (let k = 0; k < 9; k++) for (let o = -2; o <= 2; o++) {
        const p = pop(t, 0.6 + k * 0.12 + (o + 2) * 0.03, 0.3);
        const x = k + move;
        bl.push({ x, y: 1, z: o, tex: 'car_body', alpha: a * p.alpha, lift: p.lift });
        if (o === -2) for (const y of [2, 3]) { const door = k === 2 || k === 6; const q = pop(t, 2.2 + k * 0.1 + y * 0.05, 0.3); bl.push({ x, y, z: o, tex: door ? 'car_door' : (y === 3 ? 'car_window' : 'car_body'), alpha: a * q.alpha, lift: q.lift }); }
        if (o <= -1) { const q = pop(t, 3.8 + k * 0.08, 0.3); bl.push({ x, y: 4, z: o, tex: k % 2 === 0 && o === -1 && k > 0 && k < 8 ? LIGHT : 'car_body', alpha: a * q.alpha, lift: q.lift }); }
      }
      for (const k of [1, 7]) if (t > 6.5) bl.push({ x: k + move, y: 0, z: 0, tex: 'railway_casing', scale: 0.9, alpha: a * seg(t, 6.5 + (k === 7 ? 0.6 : 0), 6.9 + (k === 7 ? 0.6 : 0)) });
      drawBlocks(ctx, iso, bl);
      if (t > 5.2 && t < 6.6) { const [gx, gy] = iso.pt(4.5, 2.5, 0); ctx.save(); ctx.globalAlpha = a * inOut(t, 5.2, 6.6) * 0.5; ctx.strokeStyle = C.sky; ctx.setLineDash([8, 6]); ctx.lineWidth = 3; ctx.strokeRect(gx - 220, gy - 110, 440, 200); ctx.restore(); text(ctx, '강력 접착제', gx, gy - 120, { size: 20, weight: 800, align: 'center', color: C.sky, alpha: a * inOut(t, 5.2, 6.6) }); }
      if (t > 8.2 && t < 10.4) actionbar(ctx, 'Create 기차 정거장: 조립 모드 → 조립', W / 2, 600, C.green, a * inOut(t, 8.2, 10.4));
      caption(ctx, carMeta.title, cap(carMeta, t, [[0, '트랙 위에서 도면을 쓰면 인벤토리의 Create 블록으로 차체를 짓습니다'], [3.8, '폭 5 × 길이 9: 산업용 철, 유리창, 기차 문, 롱시트, 앞뒤 기차 조종기'], [5.2, '차체 전체에 강력 접착제가 붙습니다'], [6.5, '표시된 두 칸에 기차 케이스로 대차를 놓습니다'], [8.2, '정거장에서 조립하면 지하철이 됩니다. 차량 자체는 Create 기차입니다']]), a * seg(t, 0.3, 0.8), C.sky);
    },
    sfx: () => [...every(0.6, 2.0, 0.12, 'place_stone', 0.35, 0.9, 0.04), ...every(2.2, 3.4, 0.12, 'glass', 0.25, 1.2, 0.05), ...every(3.8, 4.6, 0.1, 'place_stone', 0.3, 1.0, 0.04), [5.2, 'spout', 0.6, 1.0], [5.4, 'shimmer', 0.4, 1.2], [6.5, 'cogs', 0.5, 1.0], [7.1, 'cogs', 0.5, 1.1], [8.4, 'confirm', 0.6, 1.0], [10.4, 'train_whistle', 0.6, 1.0], [10.6, 'train', 0.5, 1.0, 3.0]],
  }]);

  // ═══════════════ 10. 항구와 교역 ═══════════════
  const harMeta = { id: 'g_harbor', title: '항구와 교역', sub: '배를 타고 먼 항구까지, 직접', sprite: 'harbor_blueprint', color: C.sky, tint: 'rgba(57,197,187,0.09)' };
  const harbor = make(harMeta, [{
    dur: 14,
    draw(ctx, t, d) {
      background(ctx, t, harMeta.tint);
      const a = inOut(t, 0, d);
      const iso = new Iso(330, 215, 26);
      const bl = [];
      const SNOW = { top: 'snow', side: 'dirt' };
      for (let x = 0; x <= 17; x++) for (let z = -7; z <= 0; z++) {
        const left = x <= 3, right = x >= 14, pierL = x >= 4 && x <= 6 && (z === -4 || z === -3), pierR = x >= 11 && x <= 13 && (z === -4 || z === -3);
        if (left || right) { bl.push({ x, y: -1, z, tex: 'dirt', alpha: a }); bl.push({ x, y: 0, z, tex: left ? GRASS : SNOW, alpha: a }); if (left && z === -6 && x === 1) bl.push({ x, y: 1, z, tex: 'jungle_leaves', alpha: a }); }
        else { const pp = pierL ? pop(t, 0.4 + (x - 4) * 0.2, 0.3) : { alpha: 1, lift: 0 }; bl.push({ x, y: -1, z, tex: pierL || pierR ? 'planks' : 'water', alpha: a * (pierL ? pp.alpha : 1), lift: pierL ? pp.lift : 0 }); }
      }
      bl.push({ x: 4, y: 0, z: -4, tex: { top: 'trading_post_top', side: 'trading_post_side' }, alpha: a * seg(t, 1.2, 1.6) });
      bl.push({ x: 13, y: 0, z: -4, tex: { top: 'trading_post_top', side: 'trading_post_side' }, alpha: a });
      const sail = easeInOut(seg(t, 4.0, 9.0));
      const bx = lerp(6.8, 10.2, sail), bz = -2 + Math.sin(sail * Math.PI) * -1.2;
      boat(bl, bx, -0.45, bz, a * seg(t, 3.6, 4.0));
      drawBlocks(ctx, iso, bl);
      if (t > 2.4) { const [cx, cy] = iso.pt(bx + 0.5, 1.2, bz + 0.5); sprite(ctx, 'cargo_crate', cx, cy, 34, a * seg(t, 2.4, 2.8)); }
      panel(ctx, 960, 100, 280, 112, { alpha: a * seg(t, 4.0, 4.4), accent: C.sky });
      text(ctx, `항해 ${Math.round(2600 * sail).toLocaleString()}m`, 990, 144, { size: 28, weight: 800, alpha: a * seg(t, 4.0, 4.4) });
      text(ctx, '열대우림 → 설원', 990, 182, { size: 20, color: C.dim, alpha: a * seg(t, 4.0, 4.4) });
      if (t > 9.4) { panel(ctx, 660, 580, 520, 56, { alpha: a, accent: C.green }); text(ctx, '코코아 콩 64 → 얼음 39 · 꽁꽁 언 얼음 38 · 눈덩이 38', 685, 616, { size: 19, weight: 700, alpha: a }); }
      if (t > 11) { const [ex, ey] = iso.pt(13.5, 3.2, -3.5); actionbar(ctx, '먼 항해의 선물: 스니퍼 알', ex, ey, C.pink, a); }
      caption(ctx, harMeta.title, cap(harMeta, t, [[0, '물가에서 항구 설계도를 쓰면 잔교·계선주·교역소·물속 항구 역이 생깁니다'], [2.4, '교역소에 그 지역 특산품을 들고 쓰면 화물 상자가 됩니다'], [4.0, '배를 타고 200블록 넘게 떨어진 다른 지역 항구로 직접 갑니다'], [9.4, '닿으면 그곳 특산품으로 바뀝니다. 멀수록 많이, 가끔 진귀품도']]), a * seg(t, 0.3, 0.8), C.sky);
    },
    sfx: () => [0.4, 0.6, 0.8].map((t) => [t, 'place_wood', 0.6, 1.0]).concat([[1.2, 'barrel', 0.6, 1.0], [2.4, 'pop', 0.5, 1.0], [2.5, 'clipboard', 0.5, 1.0], [3.6, 'splash', 0.4, 1.0], ...every(4.0, 9.0, 0.7, 'paddle', 0.45, 1.0, 0.03), [9.2, 'bell', 0.6, 1.0], [9.5, 'orb', 0.5, 1.0], [9.7, 'levelup', 0.4, 1.0], [11.0, 'twinkle', 0.6, 1.0]]),
  }]);

  // ═══════════════ 11. 물살 · 보트 리프트 ═══════════════
  const watMeta = { id: 'g_water', title: '물살과 보트 리프트', sub: '운하로 잇는 물길', cube: { top: 'water_current_top', side: 'water_current_side' }, color: C.blue, tint: 'rgba(61,111,214,0.10)' };
  const water = make(watMeta, [{
    dur: 11,
    draw(ctx, t, d) {
      background(ctx, t, watMeta.tint);
      const a = inOut(t, 0, d);
      const iso = new Iso(250, 320, 28);
      const bl = [];
      for (let x = 0; x < 12; x++) for (let z = -2; z <= 0; z++) { bl.push({ x, y: -2, z, tex: z === -1 && x % 3 === 1 ? { top: 'water_current_top', side: 'water_current_side' } : 'stone_bricks', alpha: a }); bl.push({ x, y: -1, z, tex: 'water', alpha: a * 0.85 }); }
      for (let y = -1; y <= 5; y++) bl.push({ x: 13, y, z: -1, tex: y === -1 ? { top: 'boat_lift_top', side: 'boat_lift_side' } : 'water', alpha: a * (y === -1 ? 1 : 0.7) });
      const bx = Math.min(12.4, seg(t, 0.5, 5.5) * 13);
      const by = bx >= 12.4 ? lerp(-0.4, 5.2, seg(t, 5.8, 9.5)) : -0.4;
      boat(bl, bx < 12.4 ? bx : 13, by, -1, a);
      drawBlocks(ctx, iso, bl);
      caption(ctx, watMeta.title, cap(watMeta, t, [[0, '물살 블록은 위쪽 물의 보트를 바라보는 방향으로 밉니다'], [5.8, '보트 리프트는 물기둥으로 보트를 올립니다. 웅크리면 내려갑니다']]), a * seg(t, 0.3, 0.8), C.blue);
    },
    sfx: () => [[0.3, 'water', 0.5, 1.0, 10.5], ...every(0.6, 5.4, 0.9, 'swim', 0.3, 1.0, 0.04), [5.8, 'beacon_power', 0.3, 1.6], ...every(6.0, 9.5, 0.6, 'swim', 0.35, 1.2, 0.04), [9.6, 'splash', 0.5, 1.0]],
  }]);

  // ═══════════════ 12. 말 ═══════════════
  const horMeta = { id: 'g_horses', title: '말 · 편자 · 호루라기', sub: '말과 함께 길을 갑니다', sprite: 'diamond_horseshoe', color: C.pink, tint: 'rgba(255,180,204,0.08)' };
  const horses = make(horMeta, [{
    dur: 13,
    draw(ctx, t, d) {
      background(ctx, t, horMeta.tint);
      const a = inOut(t, 0, d);
      const iso = new Iso(230, 260, 28);
      const bl = [];
      for (let x = 0; x < 18; x++) for (let z = -1; z <= 1; z++) bl.push({ x, y: 0, z, tex: x < 9 ? 'cobblestone' : 'setts', alpha: a });
      bl.push({ x: 2, y: 1, z: -3, tex: { top: 'stable_top', side: 'stable_side' }, alpha: a });
      drawBlocks(ctx, iso, bl);
      const hx = t < 4 ? 2.4 : lerp(2.4, 17, easeInOut(seg(t, 6, 11)));
      const [sx, sy] = iso.pt(hx, 1, 0.5);
      ctx.save(); ctx.globalAlpha = a; ctx.fillStyle = '#8a5a33'; roundRect(ctx, sx - 26, sy - 30, 52, 22, 8); ctx.fill(); roundRect(ctx, sx + 18, sy - 46, 14, 24, 5); ctx.fill();
      ctx.fillStyle = '#5a3a1e'; for (const dx of [-20, -8, 8, 20]) ctx.fillRect(sx + dx - 2, sy - 10, 4, 12 + Math.sin(t * 18 + dx) * (t > 6 && t < 11 ? 3 : 0)); ctx.restore();
      if (t > 1.5 && t < 4) sprite(ctx, 'diamond_horseshoe', sx, sy - 70, 44, a * inOut(t, 1.5, 4));
      if (t > 4 && t < 6) sprite(ctx, 'whistle', sx - 80, sy - 60, 44, a * inOut(t, 4, 6));
      panel(ctx, 930, 90, 310, 150, { alpha: a, accent: C.pink });
      text(ctx, '편자', 960, 130, { size: 22, weight: 800, alpha: a }); text(ctx, '쇠·다이아몬드: 도로 보너스 ↑', 960, 160, { size: 17, color: C.dim, alpha: a });
      text(ctx, '호루라기 · 마구간', 960, 196, { size: 22, weight: 800, alpha: a }); text(ctx, '불러오기 · 맡기기', 960, 224, { size: 17, color: C.dim, alpha: a });
      caption(ctx, horMeta.title, cap(horMeta, t, [[0, '편자를 박으면 말도 도로 보너스를 더 받습니다 (다이아몬드 편자는 상자에서만)'], [4, '호루라기로 말을 묶어 두면 부를 때 달려옵니다'], [6, '포장된 길을 따라 말을 달려 보세요']]), a * seg(t, 0.3, 0.8), C.pink);
    },
    sfx: () => [[1.6, 'anvil', 0.4, 1.4], [2.0, 'horse_armor', 0.6, 1.0], [4.1, 'flute', 0.6, 1.3], [4.4, 'flute', 0.6, 1.6], [5.0, 'horse', 0.6, 1.0], ...every(6.0, 11.0, 0.32, 'gallop', 0.5, 1.0, 0.03)],
  }]);

  // ═══════════════ 13. 건설 장비 ═══════════════
  const toolMeta = { id: 'g_tools', title: '건설 장비', sub: '걸으며 깔고, 뚫고, 잽니다', sprite: 'road_paver', color: C.blue, tint: 'rgba(51,85,187,0.08)' };
  const tools = make(toolMeta, [{
    dur: 14,
    draw(ctx, t, d) {
      background(ctx, t, toolMeta.tint);
      const a = inOut(t, 0, d);
      const iso = new Iso(250, 300, 22);
      const bl = [];
      const width = t < 4 ? 3 : t < 7 ? 5 : 7;
      for (let x = 0; x < 14; x++) for (let z = -3; z <= 3; z++) {
        const inside = Math.abs(z) <= (width - 1) / 2 && t > 0.8 + x * 0.25;
        bl.push({ x, y: 0, z, tex: inside ? 'setts' : GRASS, alpha: a });
      }
      drawBlocks(ctx, iso, bl);
      const [px, py] = iso.pt(Math.min(13, (t - 0.8) * 4) + 0.5, 1, 0.5);
      if (t > 0.8 && t < 4.5) person(ctx, iso, Math.min(13, (t - 0.8) * 4) + 0.5, 1, 0.5, a);
      sprite(ctx, 'road_paver', px + 20, py - 50, 40, a * (t < 8 ? 1 : 0));
      if (t > 8.5) {
        panel(ctx, 760, 330, 470, 210, { alpha: a, accent: C.sky });
        sprite(ctx, 'surveyor', 810, 380, 48, a);
        text(ctx, '측량기', 850, 390, { size: 24, weight: 800, alpha: a });
        text(ctx, '거리 312m · 높이 +14 · 경사 4.5%', 790, 440, { size: 20, alpha: a });
        text(ctx, '교량 블록 936개 (폭 3)', 790, 480, { size: 20, color: C.dim, alpha: a });
      }
      text(ctx, `포장 폭 ${width}칸`, 250, 560, { size: 22, weight: 800, align: 'center', color: C.yellow, alpha: a * (t < 8.5 ? 1 : 0.4) });
      caption(ctx, toolMeta.title, cap(toolMeta, t, [[0, '도로 포장기를 켜고 걸으면 왼손의 블록을 폭 3칸으로 깝니다'], [4, '넓은 포장 인챈트로 5칸, 7칸까지 넓어집니다'], [8.5, '측량기로 두 지점을 찍으면 거리·높이·경사와 교량 재료를 알려 줍니다']]), a * seg(t, 0.3, 0.8), C.blue);
    },
    sfx: () => [[0.6, 'click', 0.6, 1.0], ...every(0.8, 8.0, 0.25, 'place_stone', 0.25, 1.0, 0.05), [4.0, 'enchant', 0.4, 1.0], [7.0, 'enchant', 0.4, 1.2], [8.6, 'draw', 0.6, 1.0], [9.0, 'draw', 0.6, 1.1], [9.6, 'pling', 0.4, 1.3]],
  }]);

  // ═══════════════ 14. 겉날개 금지 · 네더 1:1 ═══════════════
  const nethMeta = { id: 'g_nether', title: '겉날개 금지 · 네더 1:1', sub: '지름길 대신, 길을', sprite: 'ender_rail_core', color: C.red, tint: 'rgba(255,0,69,0.08)' };
  const nether = make(nethMeta, [{
    dur: 13,
    draw(ctx, t, d) {
      background(ctx, t, nethMeta.tint);
      const a = inOut(t, 0, d);
      // 왼쪽: 겉날개 → 엔더 선로 핵
      const p = seg(t, 0.8, 2.4);
      panel(ctx, 80, 110, 480, 300, { alpha: a, accent: C.purple });
      ctx.save(); ctx.globalAlpha = a * (1 - p); ctx.fillStyle = '#8f8fb3'; ctx.beginPath(); ctx.moveTo(250, 200); ctx.lineTo(200, 330); ctx.lineTo(260, 300); ctx.closePath(); ctx.fill(); ctx.beginPath(); ctx.moveTo(390, 200); ctx.lineTo(440, 330); ctx.lineTo(380, 300); ctx.closePath(); ctx.fill(); ctx.restore();
      sprite(ctx, 'ender_rail_core', 320, 260, 110 * p, a);
      text(ctx, '겉날개 → 엔더 선로 핵', 320, 385, { size: 22, weight: 800, align: 'center', alpha: a });
      // 오른쪽: 네더 1:1 + 블레이즈 선로
      const iso = new Iso(700, 330, 24);
      const bl = [];
      for (let x = 0; x < 16; x++) { bl.push({ x, y: -1, z: 0, tex: 'netherrack', alpha: a * seg(t, 4, 4.6) }); bl.push({ x, y: 0, z: 0, tex: 'blaze_rail_on', flat: true, rotate: true, alpha: a * seg(t, 4, 4.6) }); }
      for (let y = 0; y < 4; y++) for (const zz of [-2, 2]) bl.push({ x: 0, y, z: zz, tex: 'obsidian', alpha: a * seg(t, 4, 4.6) });
      for (let y = 0; y < 4; y++) for (let zz = -1; zz <= 1; zz++) bl.push({ x: 0, y, z: zz, tex: 'portal', alpha: a * seg(t, 4.4, 5.0) * 0.8 });
      if (t > 6) cart(bl, Math.min(15, (t - 6) * 3), 0, 0, a);
      drawBlocks(ctx, iso, bl);
      if (t > 5) { panel(ctx, 720, 520, 480, 70, { alpha: a, accent: C.orange }); text(ctx, '네더 좌표 = 오버월드 좌표 (1:1)', 750, 563, { size: 22, weight: 800, alpha: a }); }
      caption(ctx, nethMeta.title, cap(nethMeta, t, [[0, '겉날개는 쓸 수 없습니다. 손에 들어오면 엔더 선로 핵으로 바뀝니다'], [4, '네더는 1:1, 지름길이 아닙니다'], [6, '대신 블레이즈 선로가 네더에서만 가장 빠르게 달립니다 (블레이즈 가루 연료)']]), a * seg(t, 0.3, 0.8), C.red);
    },
    sfx: () => [[0.8, 'shimmer', 0.6, 1.0], [2.2, 'anchor', 0.5, 1.2], [4.4, 'portal', 0.4, 1.0], [6.0, 'minecart', 0.45, 1.2, 5.0], [6.1, 'fire', 0.4, 1.0, 5.0]],
  }]);

  // ═══════════════ 15. 인챈트 ═══════════════
  const enMeta = { id: 'g_enchant', title: '인챈트', sub: '길 위의 사람을 위한 마법부여', sprite: 'road_paver', color: C.purple, tint: 'rgba(102,0,153,0.10)' };
  const enchant = make(enMeta, [{
    dur: 11,
    draw(ctx, t, d) {
      background(ctx, t, enMeta.tint);
      const a = inOut(t, 0, d);
      const list = [['로드러너', '신발', '도로 보너스 ↑'], ['관성', '바지', '보너스가 천천히 빠짐'], ['기수', '바지', '말의 도로 보너스 ↑'], ['차장', '투구', '카트 최고 속도 ↑'], ['넓은 포장', '도로 포장기', '폭 5 · 7'], ['굴착 반경', '터널 굴착기', '5×5 · 7×7'], ['절약', '건설 장비', '재료 절약'], ['영혼 가속 V 외', '바닐라', '최대 레벨 확장']];
      list.forEach(([n, slot, eff], i) => {
        const col = i % 2, row = Math.floor(i / 2);
        const x = 110 + col * 540, y = 110 + row * 120;
        const p = easeBack(seg(t, 0.3 + i * 0.35, 0.8 + i * 0.35));
        panel(ctx, x, y, 500, 100, { alpha: a * clamp(p), accent: C.purple });
        text(ctx, n, x + 30, y + 44, { size: 26, weight: 800, color: C.pink, alpha: a * clamp(p) });
        text(ctx, `${slot} · ${eff}`, x + 30, y + 78, { size: 19, color: C.dim, alpha: a * clamp(p) });
      });
      caption(ctx, enMeta.title, '마법부여대에서 나오고, Enchantment Industry 의 블레이즈 마법부여기로도 붙습니다', a * seg(t, 0.3, 0.8), C.purple);
    },
    sfx: () => [...Array.from({ length: 8 }, (_, i) => [0.35 + i * 0.35, 'enchant', 0.35, 1.0 + i * 0.03]), [3.4, 'levelup', 0.4, 1.2]],
  }]);

  const all = { g_roads: roads, g_rails: rails, g_stations: stations, g_ats: ats, g_network: network, g_shield: shield, g_hall: hall,
    g_surface: surface, g_cars: cars, g_harbor: harbor, g_water: water, g_horses: horses, g_tools: tools, g_nether: nether, g_enchant: enchant };
  return all;
})();
