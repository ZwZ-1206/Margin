import { chromium } from 'playwright';

const base = process.env.SHOT_BASE || 'http://localhost:4178';
const pages = [['home', ''], ['download', 'download'], ['changelog', 'changelog'], ['support', 'support']];
const viewports = [
  { name: 'desktop', width: 1440, height: 900 },
  { name: 'tablet', width: 820, height: 1180 },
  { name: 'mobile', width: 390, height: 844 },
];

const browser = await chromium.launch({ proxy: { server: 'per-context' } });
for (const vp of viewports) {
  const ctx = await browser.newContext({ viewport: { width: vp.width, height: vp.height } });
  const page = await ctx.newPage();
  for (const [name, hash] of pages) {
    await page.goto(`${base}/#/${hash}`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(350);
    await page.screenshot({ path: `shots/${vp.name}-${name}.png`, fullPage: true });
    console.log(vp.name, name, 'ok');
  }
  await ctx.close();
}

// 深色模式：桌面首页 + 移动端菜单展开
{
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await ctx.newPage();
  await page.addInitScript(() => localStorage.setItem('offscreen.site.theme', 'dark'));
  await page.goto(`${base}/#/`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(350);
  await page.screenshot({ path: 'shots/desktop-home-dark.png', fullPage: true });
  console.log('desktop home dark ok');
  await ctx.close();
}
{
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 } });
  const page = await ctx.newPage();
  await page.addInitScript(() => localStorage.setItem('offscreen.site.theme', 'dark'));
  await page.goto(`${base}/#/`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(300);
  await page.click('.nav-toggle');
  await page.waitForTimeout(300);
  await page.screenshot({ path: 'shots/mobile-menu-open-dark.png' });
  console.log('mobile menu dark ok');
  await ctx.close();
}

await browser.close();
console.log('ALL DONE');
