// The pictures in the settings' card and scene choosers: the real page, rendered offline with sample data.
//   node tests/make_thumbs.mjs      (needs playwright and CHROMIUM, like page.test.mjs; run after a visible change)
// Writes android/app/src/main/res/drawable-nodpi/thumb_*.jpg
import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const OUT = path.join(ROOT, 'android/app/src/main/res/drawable-nodpi');
mkdirSync(OUT, { recursive: true });
const browser = await chromium.launch(process.env.CHROMIUM ? { executablePath: process.env.CHROMIUM } : {});

async function shot(name, hash, prepare) {
  const ctx = await browser.newContext({ viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 0.25 });
  await ctx.route(/^https?:/, r => {
    const u = r.request().url(), i = u.indexOf('/master/'), rel = i > 0 ? u.slice(i + 8) : '';
    if (/^assets\/(backgrounds|fonts)\//.test(rel)) return r.fulfill({ path: path.join(ROOT, rel), headers: { 'Access-Control-Allow-Origin': '*' } });
    return r.abort();
  });
  const p = await ctx.newPage();
  await p.goto('file://' + path.join(ROOT, 'dist/tv.html') + '#city=%D8%A7%D9%84%D8%B1%D9%8A%D8%A7%D8%B6&lat=24.6877&lon=46.7219&region=riyadh&teams=136013&' + hash);
  await p.waitForTimeout(1800);
  await p.evaluate(() => {
    window.applyNight = () => {};
    document.getElementById('dimmer').style.opacity = 0;
    document.querySelector('.stage').style.opacity = 1;
    window.photoTime = () => 'day';
    window.regionOfDay = () => 'madinah';
    applyBackground(true);
    FORECAST = [0, 1, 2, 3, 4].map(i => ({ date: new Date(Date.now() + i * 864e5).toISOString().slice(0, 10), code: 0, hi: 33 - i, lo: 22 - i }));
    const ev = (id, a, hrs) => ({ idEvent: id, strHomeTeam: 'Al-Hilal', idHomeTeam: '136013', strAwayTeam: a, idAwayTeam: '1',
      strTimestamp: new Date(Date.now() + hrs * 36e5).toISOString() });
    MATCHES['136013'] = ev('a', 'Al-Nassr', 30);
    NEXT_LIST['136013'] = [ev('a', 'Al-Nassr', 30), ev('b', 'Al-Ahli', 100)];
    renderMatches();
    if (CONFIG.panel !== 'none') renderPanel(false);
    if (CONFIG.panel === 'shows') document.getElementById('week-list').innerHTML =
      ['مسلسل جديد', 'موسم جديد', 'حلقة هذا الأسبوع'].map(s => `<li><span>${s}</span><span class="rt">٧٫٨</span></li>`).join('');
  });
  if (prepare) await p.evaluate(prepare);
  await p.waitForTimeout(1500);
  await p.screenshot({ path: path.join(OUT, name + '.jpg'), type: 'jpeg', quality: 72 });
  await ctx.close();
  console.log(name);
}

for (const panel of ['shows', 'prayers', 'forecast', 'matches', 'verse', 'adhkar', 'none']) await shot('thumb_panel_' + panel, 'panel=' + panel);
await shot('thumb_panel_round', 'panel=round&leagues=4328', `LEAGUE_ROUND['4328'] = 10; LEAGUE_EVENTS['4328'] = [30, 32, 34, 36, 38].map((h, i) => ({ idEvent: 'r' + i,
  strHomeTeam: ['Arsenal', 'Everton', 'Liverpool', 'Chelsea', 'Fulham'][i], strAwayTeam: ['Everton', 'Fulham', 'Brentford', 'Arsenal', 'Liverpool'][i],
  strTimestamp: new Date(Date.now() + h * 36e5).toISOString() })); renderPanel(false);`);
for (const scene of ['calm', 'faith', 'info']) await shot('thumb_scene_' + scene, 'panel=adhkar&scenes=' + scene, `tickScene = () => {}; setScene('${scene}');`);
await browser.close();
