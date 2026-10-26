// 영상 ①: 지하철 건설 가이드
const SUBWAY = (() => {
  const L = 'tunnel_lining', LIGHT = 'tunnel_light', EDGE = 'platform_edge', TRACK = 'create_track';
  const ROCK = 'stone', DEEP = 'deepslate';

  // ── 1. 제목 ──
  function intro(ctx, t, d) {
    background(ctx, t, 'rgba(57,197,187,0.10)');
    const a = inOut(t, 0, d, 0.5);
    // 떠다니는 블록 몇 개
    const iso = new Iso(W / 2, 470, 34);
    const bl = [];
    const tiles = [[L, -2, 0], [EDGE, -1, 0], [TRACK, 0, 0], [EDGE, 1, 0], [L, 2, 0]];
    tiles.forEach(([tex, z], i) => {
      const p = pop(t, 0.3 + i * 0.12, 0.5);
      if (tex === TRACK) bl.push({ x: 0, y: 0, z, tex: L, alpha: p.alpha * a, lift: p.lift });
      else bl.push({ x: 0, y: 0, z, tex, alpha: p.alpha * a, lift: p.lift });
    });
    for (let x = -3; x <= 3; x++) for (const z of [-2, -1, 0, 1, 2]) {
      if (x === 0) continue;
      const p = pop(t, 0.6 + Math.abs(x) * 0.08 + (z + 2) * 0.03, 0.5);
      bl.push({ x, y: 0, z, tex: Math.abs(z) === 1 ? EDGE : L, alpha: p.alpha * a, lift: p.lift });
    }
    for (let x = -3; x <= 3; x++) bl.push({ x, y: 1, z: 0, tex: TRACK, flat: true, rotate: true, alpha: seg(t, 1.3, 1.8) * a, y0: 0 });
    for (const b of bl) if (b.flat) b.y = 0.96;
    drawBlocks(ctx, iso, bl);
    const ty = lerp(30, 0, easeOut(seg(t, 0.2, 1.0)));
    text(ctx, 'InfraCraft', W / 2, 180 + ty, { size: 34, font: 'pixel', align: 'center', color: C.sky, alpha: a, shadow: true });
    text(ctx, '지하철 건설 가이드', W / 2, 262 + ty, { size: 76, weight: 800, align: 'center', alpha: a * seg(t, 0.4, 1.1) });
    text(ctx, 'Create(Create Fly) 26.2 · 26.3 Fabric', W / 2, 318 + ty, { size: 26, align: 'center', color: C.dim, alpha: a * seg(t, 0.8, 1.5) });
  }

  // ── 2. 왜 지하철인가 ──
  function why(ctx, t, d) {
    background(ctx, t, 'rgba(255,0,69,0.08)');
    const a = inOut(t, 0, d);
    // 겉날개 금지
    const p1 = easeBack(seg(t, 0.2, 0.9));
    panel(ctx, 150, 170, 440, 300, { alpha: a * p1, accent: C.red });
    sprite(ctx, 'ender_rail_core', 370, 290, 120 * p1, a);
    ctx.save();
    ctx.globalAlpha = a * seg(t, 0.9, 1.3);
    ctx.strokeStyle = C.red;
    ctx.lineWidth = 10;
    ctx.beginPath(); ctx.arc(370, 290, 80, 0, Math.PI * 2); ctx.moveTo(313, 233); ctx.lineTo(427, 347); ctx.stroke();
    ctx.restore();
    text(ctx, '겉날개 금지', 370, 420, { size: 34, weight: 800, align: 'center', alpha: a * p1 });
    text(ctx, '얻으면 엔더 선로 핵으로 바뀝니다', 370, 452, { size: 20, align: 'center', color: C.dim, alpha: a * p1 });
    // 네더 8:1 → 1:1
    const p2 = easeBack(seg(t, 1.2, 1.9));
    panel(ctx, 690, 170, 440, 300, { alpha: a * p2, accent: C.orange });
    const swap = seg(t, 2.4, 3.0);
    const ratio = swap < 0.5 ? '8 : 1' : '1 : 1';
    const sc = 1 + 0.25 * Math.sin(Math.PI * swap);
    ctx.save();
    ctx.translate(910, 300); ctx.scale(sc, sc);
    text(ctx, ratio, 0, 0, { size: 96, weight: 800, align: 'center', color: swap < 0.5 ? C.dim : C.orange, alpha: a * p2 });
    ctx.restore();
    text(ctx, '네더 좌표 배율', 910, 420, { size: 34, weight: 800, align: 'center', alpha: a * p2 });
    text(ctx, '네더 지름길이 사라집니다', 910, 452, { size: 20, align: 'center', color: C.dim, alpha: a * p2 });
    caption(ctx, '01  왜 지하철인가', t < 3.6 ? '겉날개와 네더 지름길이 없는 세계' : '먼 거리는 직접 깐 인프라로 이동합니다', a * seg(t, 0.3, 0.8));
  }

  // ── 3. 키트 ──
  function kit(ctx, t, d) {
    background(ctx, t, 'rgba(255,204,17,0.07)');
    const a = inOut(t, 0, d);
    const items = [
      { name: '실드 굴진기', note: '5×4 터널을 한 칸씩', sprite: 'subway_shield' },
      { name: '터널 라이닝', note: '돌 8 + 점토 → 16', cube: L },
      { name: '터널 조명', note: '천장 조명', cube: LIGHT, glow: 'rgba(220,240,255,0.5)' },
      { name: '승강장 안전선', note: '노란 점자 블록', cube: EDGE },
      { name: '역 승강기', note: '지상까지 오르내림', cube: { top: 'station_elevator_top', side: 'station_elevator_side' } },
      { name: '지하 역사 설계도', note: '역사를 한 번에', sprite: 'station_blueprint' },
    ];
    items.forEach((it, i) => {
      const col = i % 3, row = Math.floor(i / 3);
      const x = 190 + col * 300, y = 150 + row * 230;
      const p = easeBack(seg(t, 0.2 + i * 0.18, 0.8 + i * 0.18));
      panel(ctx, x, y, 260, 200, { alpha: a * clamp(p) });
      if (it.sprite) sprite(ctx, it.sprite, x + 130, y + 78, 96 * p, a);
      else {
        const iso = new Iso(x + 130, y + 86, 40 * p);
        iso.cube(ctx, -0.5, 0, -0.5, it.cube, { alpha: a, glow: it.glow });
      }
      text(ctx, it.name, x + 130, y + 158, { size: 26, weight: 800, align: 'center', alpha: a * clamp(p) });
      text(ctx, it.note, x + 130, y + 186, { size: 18, align: 'center', color: C.dim, alpha: a * clamp(p) });
    });
    caption(ctx, '02  키트 구성', '기차는 Create가, 터널과 역사는 InfraCraft가 맡습니다', a * seg(t, 0.4, 0.9), C.yellow);
  }

  // ── 4. 굴진 ──
  function boring(ctx, t, d) {
    background(ctx, t, 'rgba(51,85,187,0.10)');
    const a = inOut(t, 0, d);
    const iso = new Iso(330, 330, 30);
    const N = 12;
    const sliceAt = (k) => 1.0 + k * 1.05;
    const blocks = [];
    let lining = 0, done = 0;
    for (let x = 0; x < N; x++) {
      const tk = sliceAt(x);
      if (t >= tk) done = x + 1;
      for (let z = -4; z <= 0; z++) for (let y = -2; y <= 5; y++) {
        const interior = z >= -2 && y >= 0 && y <= 3;
        const shell = !interior && ((z === -3 && y >= -1 && y <= 4) || ((y === -1 || y === 4) && z >= -3));
        const rock = (y <= -1 && z <= -3) ? DEEP : ROCK;
        // 단면도: 앞쪽 천장과 그 위 암반은 그리지 않는다(조명만 남김)
        const isLightCell = y === 4 && z === 0 && x % 8 === 0;
        if ((y === 4 && z >= -1 && !(isLightCell && t >= tk)) || (y === 5 && z >= -3)) continue;
        if (interior) {
          const v = vanish(t, tk + 0.12 + (3 - y) * 0.04 + (z + 2) * 0.03, 0.3);
          if (v.alpha > 0) blocks.push({ x, y, z, tex: rock, ...v, alpha: v.alpha * a });
        } else if (shell) {
          const isLight = y === 4 && z === 0 && x % 8 === 0;
          if (t < tk) blocks.push({ x, y, z, tex: rock, alpha: a });
          else {
            const p = pop(t, tk, 0.3);
            blocks.push({ x, y, z, tex: isLight ? LIGHT : L, alpha: a * p.alpha, lift: p.lift * 0.5, glow: isLight ? 'rgba(220,240,255,0.55)' : null });
            if (!isLight) lining++;
          }
        } else {
          blocks.push({ x, y, z, tex: rock, alpha: a });
        }
      }
      if (t >= tk + 0.5) blocks.push({ x, y: 0, z: 0, tex: TRACK, flat: true, rotate: true, alpha: a * seg(t, tk + 0.5, tk + 0.8) });
    }
    drawBlocks(ctx, iso, blocks);
    // 굴진기
    if (done < N) {
      const [sx, sy] = iso.pt(done + 0.1, 2.0, -1.0);
      const bob = Math.sin(t * 12) * 3;
      sprite(ctx, 'subway_shield', sx, sy + bob, 70, a, Math.sin(t * 20) * 0.08);
    }
    panel(ctx, 960, 100, 280, 112, { alpha: a, accent: C.blue });
    text(ctx, `단면 ${done} / ${N}`, 990, 144, { size: 28, weight: 800, alpha: a });
    text(ctx, `라이닝 ${lining}개 사용`, 990, 182, { size: 22, color: C.dim, alpha: a });
    const cap = t < 5 ? '굴진기를 정면으로 쓸 때마다 5×4 단면이 한 칸씩 뚫립니다'
      : t < 10 ? '둘레는 터널 라이닝으로 감싸고, 바닥 가운데에 트랙을 깝니다'
        : '8칸마다 천장에 조명이 달립니다 (앞쪽 절반을 잘라 낸 단면도)';
    caption(ctx, '03  굴진', cap, a * seg(t, 0.3, 0.8), C.blue);
    if (t > 6 && t < 12) actionbar(ctx, 'Create 트랙이 있으면 Create 트랙을, 없으면 바닐라 선로를 깝니다', W / 2, 590, C.yellow, a * inOut(t, 6, 12));
  }

  // ── 5. 경사 ──
  function slope(ctx, t, d) {
    background(ctx, t);
    const a = inOut(t, 0, d);
    const S = 34, ox = 220, oy = 150;
    const cols = 22, rows = 12;
    const step = (c) => Math.min(c, 9);         // 내려가는 칸 수
    const carveAt = (c) => 0.6 + c * 0.22;
    ctx.save();
    ctx.globalAlpha = a;
    ctx.imageSmoothingEnabled = false;
    for (let c = 0; c < cols; c++) for (let r = 0; r < rows; r++) {
      const floor = 2 + step(c);               // 바닥 칸(r) 위치
      const inside = r >= floor - 4 && r <= floor - 1;
      const isLining = r === floor || r === floor - 5;
      let tex = r > 8 ? 'deepslate' : r < 1 ? 'dirt' : 'stone';
      if (r === 0) tex = 'grass_side';
      const carved = t >= carveAt(c);
      if (carved && inside) {
        if (r === floor - 1) {
          // 트랙: 경사 구간은 대각선으로
          ctx.strokeStyle = '#d7dbd4'; ctx.lineWidth = 5;
          const x = ox + c * S, y = oy + r * S + S - 4;
          const down = c >= 1 && c <= 9;
          ctx.beginPath(); ctx.moveTo(x, down ? y - S : y); ctx.lineTo(x + S, y); ctx.stroke();
        }
        continue;
      }
      ctx.drawImage(IMG[carved && isLining ? L : tex], ox + c * S, oy + r * S, S, S);
    }
    ctx.restore();
    // 시선 화살표
    const p = easeOut(seg(t, 0.3, 1.0));
    panel(ctx, 900, 120, 320, 150, { alpha: a * p, accent: C.green });
    ctx.save();
    ctx.globalAlpha = a * p;
    ctx.strokeStyle = C.green; ctx.lineWidth = 6;
    ctx.beginPath(); ctx.moveTo(950, 175); ctx.lineTo(1010, 230); ctx.stroke();
    ctx.beginPath(); ctx.moveTo(1010, 230); ctx.lineTo(990, 228); ctx.moveTo(1010, 230); ctx.lineTo(1008, 210); ctx.stroke();
    ctx.restore();
    text(ctx, '40° 이상 아래 보기', 1040, 190, { size: 22, weight: 700, alpha: a * p });
    text(ctx, '→ 1:1 내리막', 1040, 222, { size: 22, color: C.green, weight: 700, alpha: a * p });
    caption(ctx, '04  경사 터널', '아래를 보고 쓰면 내리막, 위를 보고 쓰면 오르막 터널이 됩니다', a * seg(t, 0.3, 0.8), C.green);
  }

  // ── 6. 물과 용암 ──
  function water(ctx, t, d) {
    background(ctx, t, 'rgba(61,111,214,0.10)');
    const a = inOut(t, 0, d);
    const S = 44, ox = 360, oy = 175;
    // 정면 단면: 7×6 (라이닝 포함), 주변 2칸
    const cells = [];
    for (let c = -2; c <= 8; c++) for (let r = -2; r <= 7; r++) cells.push([c, r]);
    const wave = Math.sin(t * 3);
    ctx.save();
    ctx.globalAlpha = a;
    ctx.imageSmoothingEnabled = false;
    for (const [c, r] of cells) {
      const shell = (c === 0 || c === 6) && r >= 0 && r <= 5 || (r === 0 || r === 5) && c >= 0 && c <= 6;
      const inner = c >= 1 && c <= 5 && r >= 1 && r <= 4;
      const wet = c >= 5 || (c >= 4 && r <= 2);  // 오른쪽 위의 지하수
      let tex = wet ? 'water' : 'stone';
      if (r >= 6 && c <= 2) tex = 'lava';
      const linedAt = 1.2, clearAt = 2.6;
      if (shell && t >= linedAt + (c + r) * 0.05) tex = L;
      if (inner && t >= clearAt + (5 - r) * 0.08) {
        ctx.fillStyle = '#1b2438';
        ctx.fillRect(ox + c * S, oy + r * S, S, S);
        continue;
      }
      ctx.drawImage(IMG[tex], ox + c * S, oy + r * S, S, S);
      if (tex === 'water') { ctx.fillStyle = `rgba(160,200,255,${0.08 + 0.06 * wave})`; ctx.fillRect(ox + c * S, oy + r * S, S, S); }
    }
    ctx.restore();
    const stage = t < 1.2 ? 0 : t < 2.6 ? 1 : 2;
    const labels = ['단면 앞에 지하수와 용암', '① 라이닝을 먼저 둘러 막고', '② 안쪽을 비웁니다 — 물도 용암도 새지 않습니다'];
    caption(ctx, '05  물과 용암 차단', labels[stage], a * seg(t, 0.2, 0.6), C.sky);
    panel(ctx, 920, 160, 300, 140, { alpha: a * seg(t, 3.2, 3.6), accent: C.green });
    text(ctx, '기반암·상자 등은', 950, 215, { size: 24, weight: 700, alpha: a * seg(t, 3.2, 3.6) });
    text(ctx, '건드리지 않습니다', 950, 252, { size: 24, weight: 700, alpha: a * seg(t, 3.2, 3.6) });
  }

  // ── 7. 지하 역사 ──
  function station(ctx, t, d) {
    background(ctx, t, 'rgba(255,180,204,0.08)');
    const a = inOut(t, 0, d);
    const iso = new Iso(610, 330, 22);
    const blocks = [];
    const at = (k) => 0.6 + (k + 7) * 0.08;
    const shaftTop = 9;
    for (let k = -7; k <= 8; k++) for (let o = -5; o <= 0; o++) {
      const tk = at(k);
      const add = (y, tex, extra = {}) => { const p = pop(t, tk + y * 0.06, 0.35); blocks.push({ x: k, y, z: o, tex, alpha: a * p.alpha, lift: p.lift, ...extra }); };
      add(-1, k === 0 && o === 0 ? 'station' : L);
      if (o === -5) for (let y = 0; y <= 4; y++) add(y, L);
      if (o <= -2 && o >= -4) add(0, o === -2 ? EDGE : L);
      // 천장은 뒤쪽 두 줄만(안이 보이도록)
      if (o <= -4) add(5, Math.abs(o) === 4 && Math.floor(k) % 4 === 0 ? LIGHT : L, { glow: Math.abs(o) === 4 && k % 4 === 0 ? 'rgba(220,240,255,0.5)' : null });
      if (o === 0) blocks.push({ x: k, y: 0, z: 0, tex: TRACK, flat: true, rotate: true, alpha: a * seg(t, tk + 0.4, tk + 0.7), y: 0 });
    }
    // 승강기와 수직구
    const ev = { x: 6, z: -4 };
    const shaftAt = 2.6;
    const eP = pop(t, shaftAt, 0.4);
    blocks.push({ x: ev.x, y: 0, z: ev.z, tex: { top: 'station_elevator_top', side: 'station_elevator_side' }, alpha: a * eP.alpha, lift: eP.lift });
    for (let y = 6; y <= shaftTop; y++) {
      const p = pop(t, shaftAt + 0.2 + (y - 6) * 0.12, 0.3);
      for (const [dx, dz] of [[-1, 0], [0, -1], [1, 0]]) blocks.push({ x: ev.x + dx, y, z: ev.z + dz, tex: L, alpha: a * p.alpha * 0.9, lift: p.lift });
    }
    // 지표(잔디)
    for (let x = 3; x <= 9; x++) for (let z = -7; z <= -1; z++) {
      if (x === ev.x && z === ev.z) continue;
      const p = pop(t, 0.2, 0.4);
      blocks.push({ x, y: shaftTop + 1, z, tex: { top: 'grass_top', side: 'grass_side' }, alpha: a * p.alpha * 0.95 });
    }
    drawBlocks(ctx, iso, blocks);
    // 승강기를 타고 오르는 플레이어(점)
    const ride = seg(t, 5.2, 8.2);
    if (ride > 0) {
      const [px, py] = iso.pt(ev.x + 0.5, 1 + ride * (shaftTop + 0.2), ev.z + 0.5);
      ctx.save();
      ctx.globalAlpha = a;
      ctx.fillStyle = C.yellow;
      roundRect(ctx, px - 7, py - 30, 14, 30, 4); ctx.fill();
      ctx.restore();
      actionbar(ctx, '▲ 승강기', px + 60, py - 10, C.green, a);
    }
    const cap = t < 2.6 ? '트랙 위에서 노선 방향을 보고 설계도를 쓰면 역사가 지어집니다'
      : t < 5.2 ? '양쪽 승강장 · 안전선 · 천장 조명 · 승강기 수직구까지'
        : '승강기에 서면 지상까지 올라가고, 웅크리면 내려갑니다';
    caption(ctx, '06  지하 역사', cap, a * seg(t, 0.3, 0.8), C.pink);
  }

  // ── 8. 기차와 노선망 ──
  function network(ctx, t, d) {
    background(ctx, t, 'rgba(85,187,68,0.08)');
    const a = inOut(t, 0, d);
    const st = { A: [220, 420, '중앙역'], B: [620, 250, '항구역'], C: [1020, 420, '광산역'] };
    const path = [[220, 420], [420, 420], [520, 250], [620, 250], [760, 250], [880, 420], [1020, 420]];
    ctx.save();
    ctx.globalAlpha = a * seg(t, 0.2, 0.7);
    ctx.strokeStyle = '#3a455c'; ctx.lineWidth = 14; ctx.lineJoin = 'round';
    ctx.beginPath(); path.forEach(([x, y], i) => (i ? ctx.lineTo(x, y) : ctx.moveTo(x, y))); ctx.stroke();
    ctx.strokeStyle = '#8a93a6'; ctx.lineWidth = 3; ctx.setLineDash([10, 8]); ctx.stroke(); ctx.setLineDash([]);
    ctx.restore();
    // 측량된 구간 강조
    const lit = (from, to, at) => {
      const p = seg(t, at, at + 0.6);
      if (p <= 0) return;
      ctx.save(); ctx.globalAlpha = a * p; ctx.strokeStyle = C.green; ctx.lineWidth = 8; ctx.lineJoin = 'round';
      ctx.beginPath(); path.slice(from, to + 1).forEach(([x, y], i) => (i ? ctx.lineTo(x, y) : ctx.moveTo(x, y))); ctx.stroke(); ctx.restore();
    };
    lit(0, 3, 3.0); lit(3, 6, 5.6);
    for (const [, [x, y, name]] of Object.entries(st)) {
      const p = easeBack(seg(t, 0.3, 0.9));
      panel(ctx, x - 34, y - 34, 68, 68, { alpha: a * clamp(p), r: 12 });
      sprite(ctx, 'station', x, y, 48 * p, a);
      text(ctx, name, x, y + 64, { size: 22, weight: 700, align: 'center', alpha: a * clamp(p) });
    }
    // 기차(Create 기차를 단순화)
    const along = (u) => {
      const lens = []; let total = 0;
      for (let i = 1; i < path.length; i++) { const l = Math.hypot(path[i][0] - path[i - 1][0], path[i][1] - path[i - 1][1]); lens.push(l); total += l; }
      let dd = u * total;
      for (let i = 0; i < lens.length; i++) { if (dd <= lens[i]) { const f = dd / lens[i]; return [lerp(path[i][0], path[i + 1][0], f), lerp(path[i][1], path[i + 1][1], f)]; } dd -= lens[i]; }
      return path[path.length - 1];
    };
    const u = t < 1.0 ? 0 : t < 3.0 ? 0.42 * easeInOut(seg(t, 1.0, 3.0)) : t < 3.6 ? 0.42 : 0.42 + 0.58 * easeInOut(seg(t, 3.6, 5.6));
    const [tx, ty] = along(u);
    ctx.save(); ctx.globalAlpha = a;
    ctx.fillStyle = C.orange; roundRect(ctx, tx - 18, ty - 12, 36, 24, 6); ctx.fill();
    ctx.fillStyle = '#ffe7c4'; ctx.fillRect(tx - 10, ty - 6, 8, 8); ctx.fillRect(tx + 2, ty - 6, 8, 8);
    ctx.restore();
    actionbar(ctx, '노선 측량: 중앙역 → 항구역  512m · 25.6초', W / 2, 120, C.green, a * inOut(t, 3.0, 5.0));
    actionbar(ctx, '노선 측량: 항구역 → 광산역  498m · 24.9초', W / 2, 120, C.green, a * inOut(t, 5.6, 7.4));
    // 경로 카드
    const pc = easeOut(seg(t, 7.4, 8.0));
    panel(ctx, 380, 520, 520, 92, { alpha: a * pc, accent: C.sky });
    text(ctx, '중앙역 → 항구역 → 광산역', 410, 558, { size: 24, weight: 800, color: C.yellow, alpha: a * pc });
    text(ctx, '1.0km · 50.5초 · 웅크리고 승차권 → 급행 16.8초', 410, 592, { size: 20, color: C.dim, alpha: a * pc });
    const cap = t < 3.0 ? 'Create 기차역은 InfraCraft 노선망에 자동으로 등록됩니다'
      : t < 7.4 ? '기차가 역 사이를 달리면 그 구간이 노선으로 측량됩니다'
        : '승차권으로 역을 누르면 경로, 웅크리고 누르면 급행';
    caption(ctx, '07  기차와 노선망', cap, a * seg(t, 0.3, 0.8), C.green);
  }

  // ── 9. 마무리 ──
  function outro(ctx, t, d) {
    background(ctx, t, 'rgba(57,197,187,0.10)');
    const a = inOut(t, 0, d, 0.6);
    const p = easeBack(seg(t, 0.2, 0.9));
    // 책 + 레일 = 안내서
    const y = 290;
    panel(ctx, 300, y - 70, 140, 140, { alpha: a * clamp(p) });
    panel(ctx, 570, y - 70, 140, 140, { alpha: a * clamp(p) });
    panel(ctx, 840, y - 70, 140, 140, { alpha: a * clamp(p), stroke: C.yellow });
    ctx.save(); ctx.globalAlpha = a * clamp(p);
    ctx.fillStyle = '#7a3b1f'; roundRect(ctx, 340, y - 40, 60, 80, 6); ctx.fill();
    ctx.fillStyle = '#f2e6c8'; ctx.fillRect(352, y - 34, 44, 68);
    ctx.fillStyle = '#c0392b'; ctx.fillRect(340, y - 40, 12, 80);
    ctx.restore();
    sprite(ctx, 'rail', 640, y, 90 * p, a);
    ctx.save(); ctx.globalAlpha = a * clamp(p);
    ctx.fillStyle = '#5b2d8a'; roundRect(ctx, 880, y - 40, 60, 80, 6); ctx.fill();
    ctx.fillStyle = '#f2e6c8'; ctx.fillRect(892, y - 34, 44, 68);
    ctx.fillStyle = C.sky; ctx.fillRect(880, y - 40, 12, 80);
    ctx.restore();
    text(ctx, '+', 505, y + 20, { size: 64, weight: 800, align: 'center', color: C.dim, alpha: a });
    text(ctx, '=', 775, y + 20, { size: 64, weight: 800, align: 'center', color: C.dim, alpha: a });
    text(ctx, '게임 안에서는 「지하철 건설 안내서」로 다시 볼 수 있습니다', W / 2, 470, { size: 30, weight: 700, align: 'center', alpha: a * seg(t, 0.8, 1.3) });
    text(ctx, 'docs/SUBWAY_GUIDE.md · docs/CREATE_ADDONS.md', W / 2, 515, { size: 22, align: 'center', color: C.dim, alpha: a * seg(t, 1.0, 1.5) });
    text(ctx, 'InfraCraft', W / 2, 150, { size: 30, font: 'pixel', align: 'center', color: C.sky, alpha: a, shadow: true });
  }

  const scenes = [
    { from: 0, to: 5, draw: intro },
    { from: 5, to: 12, draw: why },
    { from: 12, to: 19, draw: kit },
    { from: 19, to: 35, draw: boring },
    { from: 35, to: 42, draw: slope },
    { from: 42, to: 49, draw: water },
    { from: 49, to: 60, draw: station },
    { from: 60, to: 71, draw: network },
    { from: 71, to: 77, draw: outro },
  ];
  return { total: 77, scenes };
})();
