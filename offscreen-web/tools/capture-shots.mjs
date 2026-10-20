import { chromium } from 'playwright';
const OUT = '../marketing/shots';
const b = await chromium.launch();
const ctx = await b.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 });
const page = await ctx.newPage();
await page.goto('http://localhost:45130/screens.html', { waitUntil: 'load' });
await page.waitForTimeout(600);
const ids = ['s1','s2','s3','s4','s5','s6'];
for (let i = 0; i < ids.length; i++) {
  const el = page.locator('#' + ids[i]);
  await el.screenshot({ path: `${OUT}/shot-0${i + 1}.png` });
  const box = await el.boundingBox();
  console.log(`shot-0${i + 1}.png  ${box.width}x${box.height}`);
}
await b.close();
