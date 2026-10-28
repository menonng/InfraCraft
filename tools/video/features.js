// 영상 ②: InfraCraft 주요 기능
const FEATURES = (() => {
  const L = 'tunnel_lining';

  function intro(ctx, t, d) {
    background(ctx, t, 'rgba(255,126,0,0.08)');
    const a = inOut(t, 0, d, 0.5);
    const icons = ['asphalt', 'road_line', 'express_rail_on', 'station', 'rail_switch_on', 'water_current_top', 'boat_lift_top', 'stable_top', 'tunnel_light', 'blaze_rail_on'];
    icons.forEach((n, i) => {
      const p = easeBack(seg(t, 0.2 + i * 0.07, 0.7 + i * 0.07));
      const x = W / 2 + (i - (icons.length - 1) / 2) * 92;
      ctx.save(); ctx.globalAlpha = a * clamp(p); ctx.imageSmoothingEnabled = false;
      ctx.drawImage(IMG[n], x - 36, 430 - 36 + (1 - p) * 30, 72, 72); ctx.restore();
    });
    text(ctx, 'InfraCraft', W / 2, 200, { size: 34, font: 'pixel', align: 'center', color: C.orange, alpha: a, shadow: true });
    text(ctx, '주요 기능 한눈에 보기', W / 2, 285, { size: 72, weight: 800, align: 'center', alpha: a * seg(t, 0.3, 1.0) });
    text(ctx, '순간이동 없이, 직접 깐 길로 세계를 여행합니다', W / 2, 340, { size: 28, align: 'center', color: C.dim, alpha: a * seg(t, 0.7, 1.4) });
  }

  // ── 도로 ──
  function roads(ctx, t, d) {
    background(ctx, t);
    const a = inOut(t, 0, d);
    const iso = new Iso(250, 250, 26);
    const tiers = [['dirt', '흙길·자갈', 0.08], ['stone', '석재·벽돌', 0.18], ['asphalt', '아스팔트', 0.32]];
    const blocks = [];
    for (let x = 0; x < 15; x++) for (let z = -1; z <= 1; z++) {
      const tier = Math.floor(x / 5);
      const tex = tier === 2 && z === 0 ? 'road_line' : tiers[tier][0];
      const p = pop(t, 0.2 + x * 0.04, 0.35);
      blocks.push({ x, y: 0, z, tex, alpha: a * p.alpha, lift: p.lift });
    }
    drawBlocks(ctx, iso, blocks);
    // 걷는 플레이어
    const walk = seg(t, 1.0, 8.0);
    const px = walk * 17;
    const [sx, sy] = iso.pt(px, 1, 0.5);
    if (px < 15.5) {
      ctx.save(); ctx.globalAlpha = a; ctx.fillStyle = C.yellow; roundRect(ctx, sx - 7, sy - 30, 14, 30, 4); ctx.fill(); ctx.restore();
    }
    for (let i = 0; i < 3; i++) {
      const [lx, ly] = iso.pt(i * 5 + 2.5, 0, 2.6);
      text(ctx, `${i + 1}단계 ${tiers[i][1]}`, lx, ly + 34, { size: 18, weight: 700, align: 'center', color: C.dim, alpha: a });
    }
    // 속도 보너스 그래프(관성)
    const gx = 760, gy = 140, gw = 460, gh = 300;
    panel(ctx, gx - 20, gy - 40, gw + 40, gh + 90, { alpha: a, accent: C.orange });
    text(ctx, '속도 보너스', gx, gy - 8, { size: 22, weight: 800, alpha: a });
    ctx.save(); ctx.globalAlpha = a;
    ctx.strokeStyle = C.line; ctx.lineWidth = 1;
    for (const v of [0.08, 0.18, 0.32]) {
      const y = gy + gh - v / 0.36 * gh;
      ctx.beginPath(); ctx.moveTo(gx, y); ctx.lineTo(gx + gw, y); ctx.stroke();
      text(ctx, `+${Math.round(v * 100)}%`, gx + gw + 4, y + 5, { size: 14, color: C.dim });
    }
    // 시뮬레이션: 칸마다 목표 보너스, 오르기 0.012/틱, 내리기 0.03/틱(관성)
    let bonus = 0; const pts = [];
    const steps = 160;
    for (let i = 0; i <= steps; i++) {
      const xx = (i / steps) * 17;
      const target = xx < 15 ? tiers[Math.floor(xx / 5)][2] : 0;
      bonus = bonus < target ? Math.min(target, bonus + 0.012 * 2.5) : Math.max(target, bonus - 0.03 * 1.2);
      pts.push([gx + (i / steps) * gw, gy + gh - bonus / 0.36 * gh]);
    }
    const shown = Math.floor(walk * steps);
    ctx.strokeStyle = C.orange; ctx.lineWidth = 4; ctx.lineJoin = 'round';
    ctx.beginPath(); pts.slice(0, shown + 1).forEach(([x, y], i) => (i ? ctx.lineTo(x, y) : ctx.moveTo(x, y))); ctx.stroke();
    ctx.restore();
    const cap = t < 5 ? '도로 등급이 높을수록 이동 속도 보너스가 커집니다 (말도 적용)'
      : '연속된 도로 위에서 보너스가 서서히 쌓이고, 벗어나면 서서히 빠집니다';
    caption(ctx, '01  도로', cap, a * seg(t, 0.3, 0.8), C.orange);
    actionbar(ctx, '로드러너 인챈트 · 편자 · 관성 인챈트로 더 강해집니다', W / 2, 600, C.yellow, a * inOut(t, 6.5, d));
  }

  // ── 철도 ──
  function rails(ctx, t, d) {
    background(ctx, t, 'rgba(57,197,187,0.08)');
    const a = inOut(t, 0, d);
    const rows = [['rail', '바닐라 전동 레일', 0.4], ['high_speed_rail_on', '고속 선로', 0.6], ['express_rail_on', '특급 선로', 0.8], ['blaze_rail_on', '블레이즈 선로 (네더)', 1.1]];
    rows.forEach(([tex, name, v], i) => {
      const y = 150 + i * 92;
      const p = easeOut(seg(t, 0.3 + i * 0.25, 1.3 + i * 0.25));
      sprite(ctx, tex, 120, y + 22, 64, a);
      text(ctx, name, 170, y + 30, { size: 24, weight: 700, alpha: a });
      const bw = 520 * (v / 1.1) * p;
      ctx.save(); ctx.globalAlpha = a;
      ctx.fillStyle = [C.dim, C.sky, C.purple === '#660099' ? '#8a3cc7' : C.purple, C.orange][i];
      roundRect(ctx, 420, y + 6, Math.max(bw, 1), 34, 8); ctx.fill(); ctx.restore();
      text(ctx, `${v} 블록/틱 · ${Math.round(v * 20 * 3.6)}km/h`, 430 + bw, y + 31, { size: 20, weight: 700, color: C.text, alpha: a * p });
    });
    // 커브 감속
    const ca = seg(t, 5.0, 5.5);
    panel(ctx, 820, 470, 400, 120, { alpha: a * ca, accent: C.sky });
    ctx.save(); ctx.globalAlpha = a * ca;
    ctx.strokeStyle = '#8a93a6'; ctx.lineWidth = 6; ctx.lineJoin = 'round';
    ctx.beginPath(); ctx.moveTo(850, 560); ctx.lineTo(1050, 560); ctx.quadraticCurveTo(1090, 560, 1090, 520); ctx.lineTo(1090, 490); ctx.stroke();
    const m = seg(t, 5.5, 9.0);
    const cx = m < 0.7 ? lerp(850, 1050, easeOut(m / 0.7)) : 1050 + (m - 0.7) / 0.3 * 40;
    const cy = m < 0.7 ? 560 : 560 - (m - 0.7) / 0.3 * 60;
    ctx.fillStyle = C.orange; roundRect(ctx, cx - 12, cy - 9, 24, 18, 5); ctx.fill();
    ctx.restore();
    text(ctx, m > 0.55 && m < 0.95 ? '커브 앞 자동 감속' : '', 1010, 512, { size: 18, weight: 700, color: C.yellow, align: 'center', alpha: a });
    caption(ctx, '02  철도', t < 5 ? '등급 선로는 전원이 들어오면 최고 속도를 올립니다' : '커브·경사·선로 끝 앞에서는 자동으로 감속합니다', a * seg(t, 0.3, 0.8), C.sky);
  }

  // ── 역·분기기·승차권 ──
  function switching(ctx, t, d) {
    background(ctx, t, 'rgba(255,204,17,0.06)');
    const a = inOut(t, 0, d);
    ctx.save(); ctx.globalAlpha = a; ctx.lineCap = 'round'; ctx.lineJoin = 'round';
    ctx.strokeStyle = '#3a455c'; ctx.lineWidth = 16;
    ctx.beginPath(); ctx.moveTo(120, 360); ctx.lineTo(560, 360); ctx.lineTo(1160, 360); ctx.stroke();
    ctx.beginPath(); ctx.moveTo(560, 360); ctx.lineTo(720, 220); ctx.lineTo(1160, 220); ctx.stroke();
    ctx.restore();
    // 분기기
    const on = t > 2.4 && t < 4.6;
    sprite(ctx, on ? 'rail_switch_on' : 'rail_switch', 560, 410, 56, a);
    text(ctx, '분기기', 560, 460, { size: 18, weight: 700, align: 'center', color: C.dim, alpha: a });
    // 역
    for (const [x, y, name] of [[1160, 220, '광산역'], [1160, 360, '항구역']]) {
      sprite(ctx, 'station', x, y, 52, a);
      text(ctx, name, x, y + 50, { size: 20, weight: 700, align: 'center', alpha: a });
    }
    // 카트: 목적지 광산역 → 분기
    const m = seg(t, 0.8, 6.0);
    let cx, cy;
    if (m < 0.45) { cx = lerp(120, 560, m / 0.45); cy = 360; } else if (m < 0.62) { const f = (m - 0.45) / 0.17; cx = lerp(560, 720, f); cy = lerp(360, 220, f); } else { cx = lerp(720, 1110, easeOut((m - 0.62) / 0.38)); cy = 220; }
    ctx.save(); ctx.globalAlpha = a; ctx.fillStyle = C.orange; roundRect(ctx, cx - 18, cy - 13, 36, 26, 6); ctx.fill(); ctx.restore();
    sprite(ctx, 'ticket', cx, cy - 36, 34, a);
    panel(ctx, 140, 130, 330, 110, { alpha: a * seg(t, 0.3, 0.8), accent: C.yellow });
    text(ctx, '승차권 목적지: 광산역', 170, 175, { size: 22, weight: 800, alpha: a });
    text(ctx, '분기기에 등록된 목적지면 선로 전환', 170, 210, { size: 18, color: C.dim, alpha: a });
    actionbar(ctx, '도착: 광산역', W / 2, 140, C.green, a * inOut(t, 6.0, d));
    const cap = t < 2.4 ? '승차권으로 목적지를 정하면 카트가 그 역에서 섭니다'
      : t < 6.0 ? '분기기는 목적지별로 레드스톤 신호를 내서 선로를 바꿉니다' : '바닐라 카트 노선용 기능입니다 (Create 기차는 Create 신호·계획표)';
    caption(ctx, '03  역 · 승차권 · 분기기', cap, a * seg(t, 0.3, 0.8), C.yellow);
  }

  // ── 자동 열차 정지(ATS) ──
  function ats(ctx, t, d) {
    background(ctx, t, 'rgba(255,204,17,0.06)');
    const a = inOut(t, 0, d);
    const iso = new Iso(250, 225, 30);
    const N = 22;
    const bl = [];
    for (let x = 0; x < N; x++) for (const z of [0, -5]) {
      const p = pop(t, 0.1 + x * 0.02, 0.3);
      bl.push({ x, y: -1, z, tex: x === 13 && z === 0 ? 'station' : 'stone', alpha: a * p.alpha, lift: p.lift });
      bl.push({ x, y: 0, z, tex: 'rail', flat: true, rotate: true, alpha: a * p.alpha });
    }
    // 1/60초 단위 적분(칸/초). 위: 같은 방향, 아래: 단선 맞은편
    const dt = 1 / 60;
    let lead = 2, fol = -4, vf = 0, l = 0, r = N - 1;
    for (let s2 = 0; s2 < t; s2 += dt) {
      const vl = s2 < 5.6 ? 6 : 7;
      lead = s2 < 5.6 ? Math.min(13, lead + vl * dt) : lead + vl * dt;
      vf = Math.min(6, Math.max(0, (lead - fol - 1 - 4) * 3), vf + 10 * dt);
      fol += vf * dt;
      const gap = r - l - 1;
      if (s2 < 6.5) { const v = Math.min(6, Math.max(0, (gap - 8) * 3)); l += v * dt; r -= v * dt; } else l -= 2 * dt;
    }
    const cart = (x, z) => { if (x > -0.5 && x < N - 0.5) bl.push({ x, y: 0, z, tex: { top: 'cart_top', side: 'cart_side' }, scale: 0.8, alpha: a }); };
    cart(lead, 0); cart(fol, 0); cart(l, -5); cart(r, -5);
    drawBlocks(ctx, iso, bl);
    const gapLabel = (x0, x1, z, col, show) => {
      if (!show) return;
      const [ax, ay] = iso.pt(x0 + 0.9, 1.3, z + 0.5), [bx, by] = iso.pt(x1 + 0.1, 1.3, z + 0.5);
      ctx.save(); ctx.globalAlpha = a; ctx.strokeStyle = col; ctx.lineWidth = 3; ctx.setLineDash([6, 5]);
      ctx.beginPath(); ctx.moveTo(ax, ay); ctx.lineTo(bx, by); ctx.stroke(); ctx.restore();
      text(ctx, `${Math.round(x1 - x0 - 1)}칸`, (ax + bx) / 2, (ay + by) / 2 - 10, { size: 20, weight: 800, align: 'center', color: col, alpha: a, shadow: true });
    };
    const folGap = lead - fol - 1;
    const braking = t > 1.2 && folGap < 7 && lead < N - 0.5;
    gapLabel(fol, lead, 0, C.yellow, braking && fol > -0.5);
    if (braking && fol > -0.5) { const [px, py] = iso.pt(fol + 0.5, 2.2, 0.5); actionbar(ctx, 'ATS', px, py, C.yellow, a); }
    const stopped = r - l - 1 <= 8.1;
    gapLabel(l, r, -5, C.orange, stopped);
    if (stopped && t < 6.5) for (const x of [l, r]) { const [px, py] = iso.pt(x + 0.5, 2.2, -4.5); actionbar(ctx, 'ATS', px, py, C.orange, a); }
    if (t > 6.5) { const [px, py] = iso.pt(l + 0.5, 2.2, -4.5); actionbar(ctx, '◀ 물러남', px, py, C.sky, a * seg(t, 6.5, 6.9)); }
    panel(ctx, 940, 100, 300, 150, { alpha: a, accent: C.yellow });
    text(ctx, '자동 열차 정지 (ATS)', 970, 142, { size: 24, weight: 800, alpha: a });
    text(ctx, '앞: 같은 방향 · 4칸 간격', 970, 180, { size: 19, color: C.dim, alpha: a });
    text(ctx, '뒤: 단선 맞은편 · 8칸 정지', 970, 212, { size: 19, color: C.dim, alpha: a });
    const cap = t < 3.0 ? '카트가 선로를 따라 앞 24칸을 살핍니다'
      : t < 6.0 ? '앞차가 서 있으면 4칸 뒤에서 기다렸다가, 앞이 비면 다시 출발합니다'
        : '단선에서 맞은편 차가 오면 둘 다 섭니다. 복선이나 교행 구간을 두세요';
    caption(ctx, '04  ATS · 충돌 방지', cap, a * seg(t, 0.3, 0.8), C.yellow);
  }

  // ── 노선망 ──
  function network(ctx, t, d) {
    background(ctx, t, 'rgba(85,187,68,0.08)');
    const a = inOut(t, 0, d);
    const N = { A: [200, 380], B: [420, 220], C: [460, 520], D: [720, 330], E: [960, 200], F: [1060, 480] };
    const E = [['A', 'B'], ['A', 'C'], ['B', 'D'], ['C', 'D'], ['D', 'E'], ['D', 'F'], ['E', 'F']];
    const best = ['A', 'B', 'D', 'F'];
    ctx.save(); ctx.globalAlpha = a; ctx.lineCap = 'round';
    E.forEach(([p, q], i) => {
      const s = seg(t, 0.2 + i * 0.12, 0.6 + i * 0.12);
      ctx.strokeStyle = '#3a455c'; ctx.lineWidth = 8;
      ctx.beginPath(); ctx.moveTo(...N[p]); ctx.lineTo(lerp(N[p][0], N[q][0], s), lerp(N[p][1], N[q][1], s)); ctx.stroke();
    });
    const hl = seg(t, 2.4, 4.0);
    for (let i = 0; i < best.length - 1; i++) {
      const s = clamp(hl * (best.length - 1) - i);
      if (s <= 0) continue;
      const [p, q] = [N[best[i]], N[best[i + 1]]];
      ctx.strokeStyle = C.green; ctx.lineWidth = 10;
      ctx.beginPath(); ctx.moveTo(...p); ctx.lineTo(lerp(p[0], q[0], s), lerp(p[1], q[1], s)); ctx.stroke();
    }
    ctx.restore();
    for (const [k, [x, y]] of Object.entries(N)) {
      sprite(ctx, 'station', x, y, 46, a);
      text(ctx, k + '역', x, y + 46, { size: 18, weight: 700, align: 'center', alpha: a });
    }
    // 경로를 따라 실제로 달리는 기차
    const ride = seg(t, 4.2, 7.6);
    if (ride > 0) {
      const legs = best.length - 1;
      const f = Math.min(ride * legs, legs - 1e-6);
      const i = Math.floor(f), u = f - i;
      const [p, q] = [N[best[i]], N[best[i + 1]]];
      const x = lerp(p[0], q[0], u), y = lerp(p[1], q[1], u);
      ctx.save(); ctx.globalAlpha = a; ctx.fillStyle = C.orange; roundRect(ctx, x - 16, y - 11, 32, 22, 6); ctx.fill(); ctx.restore();
      panel(ctx, 860, 560, 360, 70, { alpha: a, accent: C.sky });
      text(ctx, ride < 1 ? '창밖을 보며 F역으로 가는 중…' : 'F역에 도착했습니다', 890, 603, { size: 20, weight: 700, alpha: a });
    }
    const cap = t < 2.4 ? 'Create 기차와 카트가 실제로 달린 구간만 노선이 됩니다'
      : t < 4.2 ? '목적지까지 가장 빠른 경로를 찾아 승차권으로 안내합니다' : '가는 건 직접 — 순간이동은 없습니다';
    caption(ctx, '05  노선망', cap, a * seg(t, 0.3, 0.8), C.green);
  }

  // ── 건설 장비 ──
  function tools(ctx, t, d) {
    background(ctx, t, 'rgba(51,85,187,0.08)');
    const a = inOut(t, 0, d);
    const cards = [[60, '도로 포장기', 'road_paver', C.orange], [460, '터널 굴착기', 'tunnel_borer', C.blue], [860, '측량기', 'surveyor', C.sky]];
    cards.forEach(([x, name, icon, col], i) => {
      const p = easeBack(seg(t, 0.2 + i * 0.2, 0.8 + i * 0.2));
      panel(ctx, x, 110, 360, 440, { alpha: a * clamp(p), accent: col });
      sprite(ctx, icon, x + 60, 160, 56, a * clamp(p));
      text(ctx, name, x + 100, 170, { size: 26, weight: 800, alpha: a * clamp(p) });
    });
    // 포장기: 폭 3 → 5 → 7
    const width = t < 3 ? 3 : t < 5.5 ? 5 : 7;
    const iso1 = new Iso(240, 300, 16);
    const b1 = [];
    for (let x = 0; x < 8; x++) for (let z = -3; z <= 3; z++) {
      const inside = Math.abs(z) <= (width - 1) / 2;
      b1.push({ x, y: 0, z, tex: inside && t > 0.8 + x * 0.12 ? 'asphalt' : { top: 'grass_top', side: 'grass_side' }, alpha: a });
    }
    drawBlocks(ctx, iso1, b1);
    text(ctx, `폭 ${width}칸 (넓은 포장 ${(width - 3) / 2}단계)`, 240, 500, { size: 20, weight: 700, align: 'center', color: C.yellow, alpha: a });
    // 굴착기: 3×3 → 5×5 단면
    const r = t < 4.5 ? 1 : 2;
    const S = 26, cx0 = 640, cy0 = 350;
    ctx.save(); ctx.globalAlpha = a; ctx.imageSmoothingEnabled = false;
    for (let i = -3; i <= 3; i++) for (let j = -3; j <= 3; j++) {
      const dug = Math.abs(i) <= r && Math.abs(j) <= r && t > 1.2 + (i + j + 6) * 0.05;
      if (dug) { ctx.fillStyle = '#1b2438'; ctx.fillRect(cx0 + i * S - S / 2, cy0 + j * S - S / 2, S, S); }
      else ctx.drawImage(IMG.stone, cx0 + i * S - S / 2, cy0 + j * S - S / 2, S, S);
    }
    ctx.restore();
    text(ctx, `${2 * r + 1}×${2 * r + 1} (굴착 반경 ${r - 1}단계)`, 640, 500, { size: 20, weight: 700, align: 'center', color: C.yellow, alpha: a });
    // 측량기: 두 점 → 교량
    const A = [930, 400], B = [1150, 290];
    ctx.save(); ctx.globalAlpha = a;
    ctx.fillStyle = C.red; ctx.beginPath(); ctx.arc(...A, 8, 0, Math.PI * 2); ctx.fill();
    ctx.fillStyle = C.red; ctx.beginPath(); ctx.arc(...B, 8 * seg(t, 1.5, 1.8), 0, Math.PI * 2); ctx.fill();
    const bp = seg(t, 2.5, 5.0);
    ctx.strokeStyle = '#9a6b3f'; ctx.lineWidth = 14;
    ctx.beginPath(); ctx.moveTo(...A); ctx.lineTo(lerp(A[0], B[0], bp), lerp(A[1], B[1], bp)); ctx.stroke();
    ctx.restore();
    text(ctx, '수평 246m · 경사 45% · 블록 741개', 1040, 470, { size: 17, weight: 700, align: 'center', color: C.dim, alpha: a * seg(t, 1.8, 2.2) });
    text(ctx, '웅크리고 찍으면 교량 건설', 1040, 500, { size: 20, weight: 700, align: 'center', color: C.yellow, alpha: a * seg(t, 2.5, 3.0) });
    caption(ctx, '06  건설 장비', '걸으면서 도로·선로를 깔고, 터널을 뚫고, 교량을 놓습니다', a * seg(t, 0.3, 0.8), C.blue);
  }

  // ── 말 ──
  function horses(ctx, t, d) {
    background(ctx, t, 'rgba(255,180,204,0.07)');
    const a = inOut(t, 0, d);
    const items = [['iron_horseshoe', '철 편자', '도로 보너스 +25%'], ['diamond_horseshoe', '다이아몬드 편자', '도로 보너스 +50%'], ['whistle', '말 호루라기', '묶은 말이 달려옵니다'], ['stable_top', '마구간', '말을 맡기고 꺼내기']];
    items.forEach(([icon, name, note], i) => {
      const x = 110 + i * 275, p = easeBack(seg(t, 0.2 + i * 0.2, 0.8 + i * 0.2));
      panel(ctx, x, 170, 240, 260, { alpha: a * clamp(p) });
      sprite(ctx, icon, x + 120, 260, 100 * p, a);
      text(ctx, name, x + 120, 365, { size: 26, weight: 800, align: 'center', alpha: a * clamp(p) });
      text(ctx, note, x + 120, 400, { size: 18, align: 'center', color: C.dim, alpha: a * clamp(p) });
    });
    caption(ctx, '08  말', '측량기로 말을 클릭하면 속도·점프·체력을 감정합니다', a * seg(t, 0.3, 0.8), C.pink);
  }

  // ── 수운 ──
  function water(ctx, t, d) {
    background(ctx, t, 'rgba(61,111,214,0.10)');
    const a = inOut(t, 0, d);
    const S = 48;
    ctx.save(); ctx.globalAlpha = a; ctx.imageSmoothingEnabled = false;
    for (let i = 0; i < 12; i++) {
      ctx.drawImage(IMG.water, 100 + i * S, 300, S, S);
      if (i % 3 === 1) ctx.drawImage(IMG.water_current_top, 100 + i * S, 348, S, S); else ctx.drawImage(IMG.stone, 100 + i * S, 348, S, S);
    }
    // 물살 화살표
    ctx.fillStyle = 'rgba(255,255,255,0.7)';
    for (let i = 0; i < 6; i++) { const x = 100 + ((t * 90 + i * 100) % 576); ctx.fillRect(x, 322, 18, 3); }
    // 보트
    const bx = 110 + ((t * 70) % 520);
    ctx.fillStyle = '#9a6b3f'; roundRect(ctx, bx, 288, 60, 18, 6); ctx.fill();
    // 보트 리프트
    for (let j = 0; j < 6; j++) ctx.drawImage(IMG.water, 920, 130 + j * S, S, S);
    ctx.drawImage(IMG.boat_lift_top, 920, 130 + 6 * S, S, S);
    const ly = 130 + 5 * S - ((t * 60) % (5 * S));
    ctx.fillStyle = '#9a6b3f'; roundRect(ctx, 910, ly, 68, 18, 6); ctx.fill();
    ctx.restore();
    text(ctx, '물살 블록', 390, 440, { size: 24, weight: 800, align: 'center', alpha: a });
    text(ctx, '보트 리프트', 944, 500, { size: 24, weight: 800, align: 'center', alpha: a });
    caption(ctx, '09  수운', '물살 블록이 보트를 밀고, 보트 리프트가 물기둥으로 올립니다. 물속 역은 항구', a * seg(t, 0.3, 0.8), C.sky);
  }

  // ── 역사 세 가지 ──
  function stations(ctx, t, d) {
    background(ctx, t, 'rgba(255,180,204,0.07)');
    const a = inOut(t, 0, d);
    const cards = [[50, '지하 역사', 'station_blueprint', C.pink], [460, '지상 역사', 'ground_station_blueprint', C.orange], [870, '항구', 'harbor_blueprint', C.sky]];
    cards.forEach(([x, name, icon, col], i) => {
      const p = clamp(easeBack(seg(t, 0.2 + i * 0.25, 0.8 + i * 0.25)));
      panel(ctx, x, 100, 360, 470, { alpha: a * p, accent: col });
      sprite(ctx, icon, x + 50, 145, 48, a * p);
      text(ctx, name, x + 86, 155, { size: 26, weight: 800, alpha: a * p });
    });
    const build = (i) => seg(t, 0.8 + i * 0.6, 2.4 + i * 0.6);
    // 지하: 단면(선로·높은 승강장·안전문)
    {
      const iso = new Iso(170, 400, 22), bl = [], g = build(0);
      for (let x = 0; x < 6; x++) {
        for (let z = -4; z <= 0; z++) bl.push({ x, y: -1, z, tex: 'tunnel_lining', alpha: a * g });
        for (const z of [-4, -3]) bl.push({ x, y: 0, z, tex: z === -3 ? 'platform_edge' : 'tunnel_lining', alpha: a * g });
        for (const y of [1, 2]) bl.push({ x, y, z: -3, tex: { top: 'screen_door_frame', side: 'screen_door_glass', front: 'screen_door_glass' }, alpha: a * g * 0.9 });
        for (let y = 0; y <= 3; y++) bl.push({ x, y, z: -5, tex: 'tunnel_lining', alpha: a * g });
        bl.push({ x, y: 0, z: 0, tex: 'create_track', flat: true, rotate: true, alpha: a * g });
      }
      for (const b of bl) if (b.flat) b.y = -0.04;
      drawBlocks(ctx, iso, bl);
    }
    // 지상: 돌벽돌 승강장, 가문비나무 기둥과 지붕
    {
      const iso = new Iso(590, 420, 22), bl = [], g = build(1);
      for (let x = 0; x < 6; x++) {
        for (let z = -4; z <= -2; z++) {
          bl.push({ x, y: 0, z, tex: z === -2 ? 'platform_edge' : 'stone_bricks', alpha: a * g });
          bl.push({ x, y: 4, z, tex: 'spruce', alpha: a * seg(t, 2.4, 3.2) });
        }
        if (x % 3 === 1) for (let y = 1; y <= 3; y++) bl.push({ x, y, z: -4, tex: 'spruce', alpha: a * g });
        bl.push({ x, y: 0, z: 0, tex: 'rail', flat: true, rotate: true, alpha: a * g });
        bl.push({ x, y: -1, z: 0, tex: { top: 'grass_top', side: 'grass_side' }, alpha: a * g });
        bl.push({ x, y: -1, z: -1, tex: { top: 'grass_top', side: 'grass_side' }, alpha: a * g });
      }
      for (const b of bl) if (b.flat) b.y = -0.04;
      drawBlocks(ctx, iso, bl);
      for (const x of [1, 4]) { const [lx, ly] = iso.pt(x + 0.5, 3.4, -2.5); ctx.save(); ctx.globalAlpha = a * seg(t, 2.6, 3.2);
        const r = ctx.createRadialGradient(lx, ly, 1, lx, ly, 30); r.addColorStop(0, 'rgba(255,200,90,0.9)'); r.addColorStop(1, 'rgba(255,200,90,0)');
        ctx.fillStyle = r; ctx.fillRect(lx - 30, ly - 30, 60, 60); ctx.restore(); }
    }
    // 항구: 물 위 잔교와 교역소
    {
      const iso = new Iso(990, 400, 22), bl = [], g = build(2);
      for (let x = 0; x < 7; x++) for (let z = -4; z <= 1; z++) bl.push({ x, y: -1, z, tex: 'water', alpha: a });
      for (let x = 0; x < 6; x++) for (let z = -2; z <= -1; z++) bl.push({ x, y: 0, z, tex: 'planks', alpha: a * g });
      bl.push({ x: 0, y: 1, z: -2, tex: { top: 'trading_post_top', side: 'trading_post_side' }, alpha: a * seg(t, 2.8, 3.3) });
      drawBlocks(ctx, iso, bl);
      const bx = lerp(1060, 1010, easeOut(seg(t, 3.4, 5.4)));
      ctx.save(); ctx.globalAlpha = a * seg(t, 3.4, 3.8); ctx.fillStyle = '#9a6b3f'; roundRect(ctx, bx, 455, 56, 16, 6); ctx.fill(); ctx.restore();
    }
    const notes = [[230, '높은 승강장 · 안전문 · 승강기'], [640, '지붕 · 벤치 · 등불 · 이정표'], [1050, '잔교 · 계선주 · 교역소']];
    notes.forEach(([x, s2], i) => text(ctx, s2, x, 540, { size: 19, weight: 700, align: 'center', color: C.dim, alpha: a * build(i) }));
    const cap = t < 4.5 ? '설계도 하나로 지하·지상·항구 역사를 짓습니다'
      : '승강장은 선로보다 한 칸 높아 Create 차량과 턱 없이 이어집니다. 항구 설계도는 난파선에서 찾습니다';
    caption(ctx, '07  역사 세 가지', cap, a * seg(t, 0.3, 0.8), C.pink);
  }

  // ── 항구 교역 ──
  function trade(ctx, t, d) {
    background(ctx, t, 'rgba(57,197,187,0.09)');
    const a = inOut(t, 0, d);
    const iso = new Iso(330, 215, 26);
    const bl = [];
    const GRASS = { top: 'grass_top', side: 'grass_side' }, SNOW = { top: 'snow', side: 'dirt' };
    for (let x = 0; x <= 17; x++) for (let z = -7; z <= 0; z++) {
      const left = x <= 3, right = x >= 14;
      const pierL = x >= 4 && x <= 6 && (z === -4 || z === -3);
      const pierR = x >= 11 && x <= 13 && (z === -4 || z === -3);
      if (left || right) {
        const p = pop(t, 0.1 + (left ? x : 17 - x) * 0.05, 0.35);
        bl.push({ x, y: -1, z, tex: 'dirt', alpha: a * p.alpha, lift: p.lift });
        bl.push({ x, y: 0, z, tex: left ? GRASS : SNOW, alpha: a * p.alpha, lift: p.lift });
      } else bl.push({ x, y: -1, z, tex: pierL || pierR ? 'planks' : 'water', alpha: a });
    }
    bl.push({ x: 4, y: 0, z: -4, tex: { top: 'trading_post_top', side: 'trading_post_side' }, alpha: a * seg(t, 0.4, 0.8) });
    bl.push({ x: 13, y: 0, z: -4, tex: { top: 'trading_post_top', side: 'trading_post_side' }, alpha: a * seg(t, 0.4, 0.8) });
    const sail = easeInOut(seg(t, 2.6, 6.4));
    const bx = lerp(6.8, 10.2, sail), bz = -2 + Math.sin(sail * Math.PI) * -1.2;
    bl.push({ x: bx, y: -0.45, z: bz, tex: 'planks', scale: 0.75, alpha: a * seg(t, 2.2, 2.6) });
    drawBlocks(ctx, iso, bl);
    if (t > 1.0) { const [cx, cy] = iso.pt(bx + 0.5, 1.2, bz + 0.5); sprite(ctx, 'cargo_crate', cx, cy, 34, a * seg(t, 1.0, 1.4)); }
    const [lx, ly] = iso.pt(1.5, 1.8, -3.5), [rx, ry] = iso.pt(16, 2.6, -2);
    text(ctx, '열대우림 항구', lx, ly, { size: 22, weight: 800, align: 'center', alpha: a, shadow: true });
    text(ctx, '설원 항구', rx, ry, { size: 22, weight: 800, align: 'center', alpha: a, shadow: true });
    panel(ctx, 960, 100, 280, 112, { alpha: a * seg(t, 2.6, 3.0), accent: C.sky });
    text(ctx, `항해 ${Math.round(2600 * sail).toLocaleString()}m`, 990, 144, { size: 28, weight: 800, alpha: a * seg(t, 2.6, 3.0) });
    text(ctx, '200블록 넘게 · 다른 지역', 990, 182, { size: 20, color: C.dim, alpha: a * seg(t, 2.6, 3.0) });
    const c1 = easeOut(seg(t, 0.6, 1.2));
    panel(ctx, 100, 580, 520, 56, { alpha: a * c1, accent: C.green });
    text(ctx, '선적: 코코아 콩 ×64 → 화물 상자', 125, 616, { size: 20, weight: 700, alpha: a * c1 });
    const c2 = easeOut(seg(t, 6.6, 7.2));
    panel(ctx, 660, 580, 520, 56, { alpha: a * c2, accent: C.sky });
    text(ctx, '하역: 얼음 39 · 꽁꽁 언 얼음 38 · 눈덩이 38 (1.8배)', 685, 616, { size: 20, weight: 700, alpha: a * c2 });
    if (t > 7.6) { const [ex, ey] = iso.pt(13.5, 3.2, -3.5); actionbar(ctx, '먼 항해의 선물: 스니퍼 알', ex, ey, C.pink, a * seg(t, 7.6, 8.0)); }
    const cap = t < 2.4 ? '교역소에 그 항구의 특산품을 들고 쓰면 화물 상자가 됩니다'
      : t < 6.6 ? '배를 타고 200블록 넘게 떨어진 다른 지역 항구로 직접 갑니다'
        : '도착하면 그곳 특산품으로 바뀝니다. 멀수록 많이, 가끔 진귀품도';
    caption(ctx, '10  항구 교역', cap, a * seg(t, 0.3, 0.8), C.sky);
  }

  // ── 규칙과 네더 ──
  function nether(ctx, t, d) {
    background(ctx, t, 'rgba(255,0,69,0.08)');
    const a = inOut(t, 0, d);
    const iso = new Iso(330, 290, 30);
    const bl = [];
    for (let x = 0; x < 8; x++) {
      bl.push({ x, y: 0, z: 0, tex: 'deepslate', alpha: a });
      bl.push({ x, y: 1, z: 0, tex: 'blaze_rail_on', flat: true, rotate: true, alpha: a });
    }
    for (const b of bl) if (b.flat) b.y = 0.96;
    drawBlocks(ctx, iso, bl);
    const fuel = 1 - (t % 3) / 3;
    panel(ctx, 760, 170, 420, 150, { alpha: a, accent: C.orange });
    text(ctx, '블레이즈 선로 (네더 전용)', 790, 215, { size: 24, weight: 800, alpha: a });
    text(ctx, '블레이즈 가루 1개 = 128블록', 790, 250, { size: 20, color: C.dim, alpha: a });
    ctx.save(); ctx.globalAlpha = a; ctx.fillStyle = 'rgba(255,255,255,0.1)'; roundRect(ctx, 790, 272, 360, 18, 9); ctx.fill();
    ctx.fillStyle = C.orange; roundRect(ctx, 790, 272, 360 * fuel, 18, 9); ctx.fill(); ctx.restore();
    panel(ctx, 760, 350, 420, 110, { alpha: a * seg(t, 1.5, 2.0), accent: C.purple });
    text(ctx, '포탈 양쪽 16블록 안의 역 → 포탈 노선', 790, 395, { size: 20, weight: 700, alpha: a * seg(t, 1.5, 2.0) });
    text(ctx, '네더 좌표는 오버월드와 1:1', 790, 430, { size: 20, color: C.dim, alpha: a * seg(t, 1.5, 2.0) });
    caption(ctx, '11  네더', '네더는 지름길이 아니라, 연료를 태우는 위험한 고속 노선입니다', a * seg(t, 0.3, 0.8), C.red);
  }

  // ── Create 연동과 인챈트 ──
  function compat(ctx, t, d) {
    background(ctx, t, 'rgba(102,0,153,0.10)');
    const a = inOut(t, 0, d);
    panel(ctx, 80, 110, 540, 460, { alpha: a, accent: C.sky });
    text(ctx, 'Create 연동', 110, 160, { size: 30, weight: 800, alpha: a });
    const lines = ['Create 기차 정거장 → 노선망 역 자동 등록', 'Create 기차 운행 → 노선 자동 측량', '전동차·객차 도면 → Create 블록으로 차체', '지하철 키트가 Create 트랙을 깝니다', 'Create 장식 돌 = 도로 2등급', '차량 자체는 Create와 애드온의 것'];
    lines.forEach((s, i) => text(ctx, '• ' + s, 110, 215 + i * 54, { size: 22, weight: 600, alpha: a * seg(t, 0.4 + i * 0.25, 0.8 + i * 0.25) }));
    panel(ctx, 660, 110, 540, 460, { alpha: a, accent: C.purple });
    text(ctx, '인챈트', 690, 160, { size: 30, weight: 800, alpha: a });
    const ench = [['로드러너', '신발 · 도로 보너스'], ['관성', '바지 · 보너스 유지'], ['기수', '바지 · 말 도로 보너스'], ['차장', '투구 · 카트 최고 속도'], ['넓은 포장', '포장기 폭'], ['굴착 반경', '굴착기 범위'], ['절약', '재료 절약'], ['영혼 가속 V 외', '바닐라 레벨 확장']];
    ench.forEach(([n, note], i) => {
      const al = a * seg(t, 1.0 + i * 0.2, 1.4 + i * 0.2);
      text(ctx, n, 690, 212 + i * 44, { size: 22, weight: 800, color: C.pink, alpha: al });
      text(ctx, note, 870, 212 + i * 44, { size: 20, color: C.dim, alpha: al });
    });
    caption(ctx, '12  Create 연동 · 인챈트', 'Enchantment Industry 블레이즈 마법부여기로도 붙일 수 있습니다', a * seg(t, 0.3, 0.8), C.purple);
  }

  function outro(ctx, t, d) {
    background(ctx, t, 'rgba(255,126,0,0.08)');
    const a = inOut(t, 0, d, 0.6);
    text(ctx, 'InfraCraft', W / 2, 260, { size: 40, font: 'pixel', align: 'center', color: C.orange, alpha: a, shadow: true });
    text(ctx, '길을 깔면, 세계가 가까워집니다', W / 2, 340, { size: 44, weight: 800, align: 'center', alpha: a * seg(t, 0.3, 0.9) });
    text(ctx, '26.2 · 26.3 Fabric  ·  Create Fly와 함께', W / 2, 400, { size: 24, align: 'center', color: C.dim, alpha: a * seg(t, 0.6, 1.2) });
  }

  const scenes = [
    { from: 0, to: 4, draw: intro },
    { from: 4, to: 14, draw: roads },
    { from: 14, to: 24, draw: rails },
    { from: 24, to: 32, draw: switching },
    { from: 32, to: 41, draw: ats },
    { from: 41, to: 49, draw: network },
    { from: 49, to: 58, draw: tools },
    { from: 58, to: 67, draw: stations },
    { from: 67, to: 72, draw: horses },
    { from: 72, to: 78, draw: water },
    { from: 78, to: 88, draw: trade },
    { from: 88, to: 94, draw: nether },
    { from: 94, to: 102, draw: compat },
    { from: 102, to: 106, draw: outro },
  ];
  return { total: 106, scenes };
})();
