import { chromium } from 'playwright';
const base = 'http://localhost:45124';
const b = await chromium.launch({ proxy: { server: 'per-context' } });
const ctx = await b.newContext({ viewport: { width: 1320, height: 900 } });
const page = await ctx.newPage();
const errs = [];
page.on('pageerror', e => errs.push(e.message));

await page.goto(`${base}/#/support`, { waitUntil: 'load' });
await page.waitForTimeout(800);
const title = await page.title();
console.log('TITLE:', title);
console.log('NAV:', await page.evaluate(() => [...document.querySelectorAll('.site-nav a')].map(a => a.textContent.trim()).join(' | ')));
console.log('ACTIVE:', await page.evaluate(() => document.querySelector('.site-nav a.active')?.textContent.trim()));
console.log('H2:', await page.evaluate(() => document.querySelector('h2')?.textContent.trim()));
await page.screenshot({ path: 'shots/check-support.png' });

await page.goto(`${base}/#/sponsor`, { waitUntil: 'load' });
await page.waitForTimeout(800);
console.log('--- /#/sponsor ---');
console.log('ACTIVE:', await page.evaluate(() => document.querySelector('.site-nav a.active')?.textContent.trim()));
console.log('H2:', await page.evaluate(() => document.querySelector('h2')?.textContent.trim()));
console.log('QR loaded:', await page.evaluate(() => {
  const img = document.querySelector('.qr-img-lg');
  return img ? (img.complete && img.naturalWidth > 0) : 'no-img';
}));
await page.screenshot({ path: 'shots/check-sponsor.png' });
console.log('ERRORS:', JSON.stringify(errs));
await b.close();
