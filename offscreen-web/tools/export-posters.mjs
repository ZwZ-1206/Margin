import { chromium } from 'playwright';
const b = await chromium.launch();
const sizes = [ {q:'916', h:1920, dir:''}, {q:'34', h:1440, dir:'34/'} ];
const names = ['01-屏幕洞察','02-专注计时','03-应用限额','04-桌面小部件','05-每周回顾','06-隐私与免费'];
for (const s of sizes) {
  const ctx = await b.newContext({ viewport: { width: 1080, height: s.h }, deviceScaleFactor: 1 });
  const page = await ctx.newPage();
  for (let i = 1; i <= 6; i++) {
    await page.goto(`http://localhost:45130/posters.html?p=${i}&size=${s.q}`, { waitUntil: 'load' });
    await page.waitForTimeout(350);
    const el = page.locator(`#p${i}`);
    const path = `../marketing/posters/${s.dir}poster-${names[i-1]}.png`;
    await el.screenshot({ path });
    const box = await el.boundingBox();
    console.log(`${path}  ${box.width}x${box.height}`);
  }
  await ctx.close();
}
await b.close();
