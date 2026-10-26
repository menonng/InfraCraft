// 사용법:
//   node tools/video/render.mjs subway              → docs/videos/subway.mp4
//   node tools/video/render.mjs features --preview 3,20,40   → 해당 초의 프레임만 PNG 로 저장(확인용)
// 글꼴(Pretendard, Galmuri, 둘 다 OFL)은 처음 실행할 때 npm 에서 받아 tools/video/.fonts 에 둔다.
import { createRequire } from 'node:module';
import { spawn, execSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const { chromium } = createRequire(import.meta.url)('playwright');
const REPO = path.resolve(HERE, '../..');
const [video = 'subway', ...rest] = process.argv.slice(2);
const previewArg = rest.indexOf('--preview');
const preview = previewArg >= 0 ? rest[previewArg + 1].split(',').map(Number) : null;
const outDirArg = rest.indexOf('--out');
const outDir = outDirArg >= 0 ? rest[outDirArg + 1] : path.join(REPO, 'docs', 'videos');
const FPS = 30;

function ensureFonts() {
  const dir = path.join(HERE, '.fonts');
  const need = ['Pretendard-Regular', 'Pretendard-SemiBold', 'Pretendard-Bold', 'Pretendard-ExtraBold', 'Galmuri11'];
  if (need.every((n) => fs.existsSync(path.join(dir, n + '.woff2')))) return;
  fs.mkdirSync(dir, { recursive: true });
  const tmp = fs.mkdtempSync(path.join(dir, 'pkg-'));
  execSync('npm pack pretendard galmuri --silent', { cwd: tmp });
  for (const tgz of fs.readdirSync(tmp).filter((f) => f.endsWith('.tgz'))) execSync(`tar -xzf ${tgz}`, { cwd: tmp });
  const find = (name) => execSync(`find ${tmp} -name ${name}.woff2 | head -1`).toString().trim();
  for (const n of need) fs.copyFileSync(find(n), path.join(dir, n + '.woff2'));
  fs.rmSync(tmp, { recursive: true, force: true });
}

ensureFonts();
fs.mkdirSync(outDir, { recursive: true });
const browser = await chromium.launch({ args: ['--allow-file-access-from-files'] });
const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
page.on('pageerror', (e) => { console.error('page error:', e.message); process.exitCode = 1; });
await page.goto('file://' + path.join(HERE, 'index.html') + '?video=' + video);
await page.evaluate(() => window.ready);
const total = await page.evaluate(() => window.TOTAL);
const canvas = await page.$('#c');

if (preview) {
  for (const t of preview) {
    await page.evaluate((tt) => window.renderFrame(tt), t);
    const file = path.join(outDir, `${video}-${String(t).padStart(5, '0')}.png`);
    await canvas.screenshot({ path: file });
    console.log(file);
  }
} else {
  const out = path.join(outDir, `${video}.mp4`);
  const ff = spawn('ffmpeg', ['-y', '-loglevel', 'error', '-f', 'image2pipe', '-framerate', String(FPS), '-i', '-',
    '-c:v', 'libx264', '-preset', 'slow', '-crf', '22', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', out], { stdio: ['pipe', 'inherit', 'inherit'] });
  const frames = Math.round(total * FPS);
  for (let i = 0; i < frames; i++) {
    await page.evaluate((tt) => window.renderFrame(tt), i / FPS);
    const buf = await canvas.screenshot({ type: 'png' });
    if (!ff.stdin.write(buf)) await new Promise((r) => ff.stdin.once('drain', r));
    if (i % (FPS * 10) === 0) console.log(`${video}: ${Math.round((i / frames) * 100)}%`);
  }
  ff.stdin.end();
  await new Promise((r) => ff.on('close', r));
  console.log('wrote', out);
}
await browser.close();
