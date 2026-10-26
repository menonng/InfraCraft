// InfraCraft 설명 영상 엔진: 캔버스에 시간 t(초)의 한 프레임을 그린다. render.mjs 가 프레임을 캡처해 MP4 로 만든다.
const W = 1280, H = 720;
const C = {
  red: '#FF0045', orange: '#FF7E00', yellow: '#FFCC11', green: '#55BB44', sky: '#39C5BB', blue: '#3355BB',
  purple: '#660099', pink: '#FFB4CC', bg: '#0e131e', bg2: '#182033', panel: 'rgba(16,22,36,0.86)',
  text: '#f3f5f9', dim: '#9aa5bb', line: 'rgba(255,255,255,0.12)',
};
const IMG = {};

function loadImages(map) {
  return Promise.all(Object.entries(map).map(([name, src]) => new Promise((ok, fail) => {
    const im = new Image();
    im.onload = () => { IMG[name] = im; ok(); };
    im.onerror = () => fail(new Error('image ' + src));
    im.src = src;
  })));
}

// ── 시간 도우미 ──
const clamp = (v, a = 0, b = 1) => Math.max(a, Math.min(b, v));
const lerp = (a, b, t) => a + (b - a) * t;
const seg = (t, a, b) => clamp((t - a) / (b - a));
const easeOut = (t) => 1 - Math.pow(1 - t, 3);
const easeInOut = (t) => (t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2);
const easeBack = (t) => { const c1 = 1.70158, c3 = c1 + 1; return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2); };
// 장면 안에서 a..b 구간 동안 나타났다가(fade in) 끝에서 사라지는(fade out) 알파
const inOut = (t, a, b, f = 0.35) => Math.min(seg(t, a, a + f), 1 - seg(t, b - f, b));

// ── 배경 ──
function background(ctx, t, hue = 0) {
  const g = ctx.createLinearGradient(0, 0, 0, H);
  g.addColorStop(0, '#121a2b');
  g.addColorStop(1, '#0a0e17');
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, W, H);
  // 천천히 흐르는 점 격자
  ctx.fillStyle = 'rgba(255,255,255,0.05)';
  const off = (t * 8) % 40;
  for (let y = -40; y < H + 40; y += 40) for (let x = -40; x < W + 40; x += 40) {
    ctx.fillRect(x + off, y + off * 0.5, 2, 2);
  }
  if (hue) {
    const r = ctx.createRadialGradient(W * 0.7, H * 0.3, 10, W * 0.7, H * 0.3, 700);
    r.addColorStop(0, hue);
    r.addColorStop(1, 'rgba(0,0,0,0)');
    ctx.fillStyle = r;
    ctx.fillRect(0, 0, W, H);
  }
}

// ── 글자 ──
const FONT = { ui: 'Pretendard', pixel: 'Galmuri11' };
function text(ctx, str, x, y, o = {}) {
  const size = o.size || 28;
  ctx.save();
  ctx.globalAlpha *= o.alpha ?? 1;
  ctx.font = `${o.weight || 600} ${size}px "${o.font === 'pixel' ? FONT.pixel : FONT.ui}"`;
  ctx.textAlign = o.align || 'left';
  ctx.textBaseline = o.baseline || 'alphabetic';
  if (o.shadow) {
    ctx.fillStyle = 'rgba(0,0,0,0.55)';
    ctx.fillText(str, x + (o.font === 'pixel' ? 3 : 0), y + (o.font === 'pixel' ? 3 : 2));
  }
  ctx.fillStyle = o.color || C.text;
  ctx.fillText(str, x, y);
  const w = ctx.measureText(str).width;
  ctx.restore();
  return w;
}

function roundRect(ctx, x, y, w, h, r) {
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + w, y, x + w, y + h, r);
  ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r);
  ctx.arcTo(x, y, x + w, y, r);
  ctx.closePath();
}

function panel(ctx, x, y, w, h, o = {}) {
  ctx.save();
  ctx.globalAlpha *= o.alpha ?? 1;
  roundRect(ctx, x, y, w, h, o.r ?? 14);
  ctx.fillStyle = o.fill || C.panel;
  ctx.fill();
  if (o.stroke !== false) {
    ctx.strokeStyle = o.stroke || C.line;
    ctx.lineWidth = 1.5;
    ctx.stroke();
  }
  if (o.accent) {
    ctx.fillStyle = o.accent;
    roundRect(ctx, x, y, 6, h, 3);
    ctx.fill();
  }
  ctx.restore();
}

// 화면 아래 자막과 왼쪽 위 장 제목
function caption(ctx, chapter, str, alpha = 1, color = C.sky) {
  if (alpha <= 0) return;
  ctx.save();
  ctx.globalAlpha = alpha;
  ctx.font = '600 30px "Pretendard"';
  const w = Math.max(ctx.measureText(str).width + 64, 360);
  panel(ctx, (W - w) / 2, H - 92, w, 58, { r: 29, fill: 'rgba(8,12,20,0.82)' });
  text(ctx, str, W / 2, H - 52, { size: 30, align: 'center' });
  if (chapter) {
    ctx.fillStyle = color;
    roundRect(ctx, 34, 30, 8, 36, 4);
    ctx.fill();
    text(ctx, chapter, 54, 59, { size: 26, font: 'pixel', color: C.text, shadow: true });
  }
  ctx.restore();
}

// 마인크래프트 액션바처럼 보이는 픽셀 글씨 메시지
function actionbar(ctx, str, x, y, color, alpha = 1) {
  if (alpha <= 0) return;
  text(ctx, str, x, y, { size: 24, font: 'pixel', color, align: 'center', shadow: true, alpha });
}

// 진행 막대
function progress(ctx, t, total) {
  ctx.fillStyle = 'rgba(255,255,255,0.08)';
  ctx.fillRect(0, H - 6, W, 6);
  ctx.fillStyle = C.sky;
  ctx.fillRect(0, H - 6, W * clamp(t / total), 6);
}

function sprite(ctx, name, x, y, size, alpha = 1, rot = 0) {
  const im = IMG[name];
  if (!im) return;
  ctx.save();
  ctx.globalAlpha *= alpha;
  ctx.imageSmoothingEnabled = false;
  ctx.translate(x, y);
  if (rot) ctx.rotate(rot);
  ctx.drawImage(im, -size / 2, -size / 2, size, size);
  ctx.restore();
}

// ── 등각 투영 블록 ──
// 화면 좌표: sx = ox + (x - z) * a, sy = oy + (x + z) * a / 2 - y * a
class Iso {
  constructor(ox, oy, a) { this.ox = ox; this.oy = oy; this.a = a; }
  pt(x, y, z) { return [this.ox + (x - z) * this.a, this.oy + (x + z) * this.a / 2 - y * this.a]; }
  face(ctx, img, p0, pu, pv, shade, alpha) {
    if (!img) return;
    ctx.save();
    ctx.globalAlpha *= alpha;
    ctx.setTransform((pu[0] - p0[0]) / 16, (pu[1] - p0[1]) / 16, (pv[0] - p0[0]) / 16, (pv[1] - p0[1]) / 16, p0[0], p0[1]);
    ctx.imageSmoothingEnabled = false;
    ctx.drawImage(img, 0, 0, 16, 16);
    if (shade > 0) {
      ctx.fillStyle = `rgba(0,0,0,${shade})`;
      ctx.fillRect(0, 0, 16, 16);
    }
    ctx.restore();
  }
  // tex: 문자열(모든 면 같음) 또는 {top, side}
  cube(ctx, x, y, z, tex, o = {}) {
    const alpha = o.alpha ?? 1;
    if (alpha <= 0.01) return;
    const s = o.scale ?? 1;
    const lift = o.lift ?? 0;
    const cx = x + 0.5, cz = z + 0.5;
    const x0 = cx - s / 2, x1 = cx + s / 2, z0 = cz - s / 2, z1 = cz + s / 2;
    const y0 = y + lift, y1 = y0 + s;
    const top = IMG[typeof tex === 'string' ? tex : tex.top];
    const side = IMG[typeof tex === 'string' ? tex : tex.side];
    const front = IMG[typeof tex === 'string' ? tex : (tex.front || tex.side)];
    // 위
    this.face(ctx, top, this.pt(x0, y1, z0), this.pt(x1, y1, z0), this.pt(x0, y1, z1), 0, alpha);
    // 남쪽(+z, 왼쪽 아래로 보이는 면)
    this.face(ctx, front, this.pt(x0, y1, z1), this.pt(x1, y1, z1), this.pt(x0, y0, z1), 0.22, alpha);
    // 동쪽(+x, 오른쪽 아래로 보이는 면)
    this.face(ctx, side, this.pt(x1, y1, z1), this.pt(x1, y1, z0), this.pt(x1, y0, z1), 0.38, alpha);
    if (o.glow) {
      const [gx, gy] = this.pt(cx, y1, cz);
      const r = ctx.createRadialGradient(gx, gy, 2, gx, gy, this.a * 3);
      r.addColorStop(0, o.glow);
      r.addColorStop(1, 'rgba(0,0,0,0)');
      ctx.save();
      ctx.globalAlpha *= alpha;
      ctx.fillStyle = r;
      ctx.fillRect(gx - this.a * 3, gy - this.a * 3, this.a * 6, this.a * 6);
      ctx.restore();
    }
  }
  // 바닥에 붙은 평면(선로 등)
  flat(ctx, x, y, z, texName, o = {}) {
    const alpha = o.alpha ?? 1;
    if (alpha <= 0.01) return;
    const h = y + 0.04 + (o.lift ?? 0);
    if (o.rotate) this.face(ctx, IMG[texName], this.pt(x + 1, h, z), this.pt(x + 1, h, z + 1), this.pt(x, h, z), 0, alpha);
    else this.face(ctx, IMG[texName], this.pt(x, h, z), this.pt(x + 1, h, z), this.pt(x, h, z + 1), 0, alpha);
  }
}

// 블록 목록을 뒤에서 앞으로 그린다
function drawBlocks(ctx, iso, list) {
  list.sort((p, q) => (p.x + p.z) - (q.x + q.z) || p.y - q.y || (p.flat ? 1 : 0) - (q.flat ? 1 : 0));
  for (const b of list) {
    if (b.flat) iso.flat(ctx, b.x, b.y, b.z, b.tex, b);
    else iso.cube(ctx, b.x, b.y, b.z, b.tex, b);
  }
}

// 등장 애니메이션: 위에서 살짝 떨어지며 나타남
function pop(t, at, dur = 0.35) {
  const p = seg(t, at, at + dur);
  return { alpha: p, lift: (1 - easeOut(p)) * 0.8 };
}
// 사라짐: 줄어들며 사라짐
function vanish(t, at, dur = 0.3) {
  const p = seg(t, at, at + dur);
  return { alpha: 1 - p, scale: 1 - 0.6 * easeOut(p), lift: 0.4 * p };
}

// 장면 묶음 실행
function runScenes(ctx, scenes, t) {
  for (const s of scenes) {
    if (t >= s.from - 0.001 && t < s.to + 0.001) s.draw(ctx, t - s.from, s.to - s.from);
  }
}
