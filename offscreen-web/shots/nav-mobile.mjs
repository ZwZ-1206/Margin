import { chromium } from 'playwright';
const b = await chromium.launch({ proxy: { server: 'per-context' } });
const ctx = await b.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 });
const page = await ctx.newPage();
await page.goto('http://localhost:45124/#/sponsor', { waitUntil: 'load' });
await page.waitForTimeout(900);
await page.screenshot({ path: 'shots/check-sponsor-mobile.png' });
console.log('mobile ok, h2 =', await page.evaluate(() => document.querySelector('h2')?.textContent.trim()));
await b.close();
