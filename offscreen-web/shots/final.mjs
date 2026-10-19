import { chromium } from 'playwright';
const base = 'http://localhost:45123';
const b = await chromium.launch({ proxy: { server: 'per-context' } });
const shot = async (name, hash, vp, full = false) => {
  const ctx = await b.newContext({ viewport: vp });
  const page = await ctx.newPage();
  try {
    await page.goto(`${base}/#/${hash}`, { waitUntil: 'load', timeout: 20000 });
    await page.waitForTimeout(1000);
    const title = await page.title();
    if (!title.includes('Margin')) throw new Error('wrong app served: ' + title);
    await page.screenshot({ path: `shots/final-${name}.png`, fullPage: full });
    console.log(name, 'ok -', title);
  } catch (e) {
    console.log(name, 'FAIL', e.message.slice(0, 90));
  }
  await ctx.close();
};
await shot('desktop-home', '', { width: 1440, height: 950 });
await shot('desktop-download', 'download', { width: 1440, height: 950 });
await shot('desktop-changelog', 'changelog', { width: 1440, height: 950 });
await shot('mobile-download', 'download', { width: 390, height: 844 });
await b.close();
console.log('DONE');
