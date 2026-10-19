import { chromium } from 'playwright';
const b = await chromium.launch({ proxy: { server: 'per-context' } });
const ctx = await b.newContext({ viewport: { width: 1440, height: 1000 } });
const page = await ctx.newPage();
const errs = [];
page.on('pageerror', e => errs.push(e.message));
await page.goto('http://localhost:4180/#/download', { waitUntil: 'load' });
await page.waitForTimeout(1200);
console.log('TITLE:', await page.title());
console.log('QR img loaded:', await page.evaluate(() => {
  const img = document.querySelector('.sponsor-card img.qr-img, img.qr-img');
  return img ? img.complete && img.naturalWidth > 0 : 'no-img';
}));
console.log('version text:', await page.evaluate(() => {
  const el = [...document.querySelectorAll('.v')].map(e => e.textContent).join(' | ');
  return el.slice(0, 120);
}));
console.log('errors:', JSON.stringify(errs.slice(0,3)));
await page.screenshot({ path: 'shots/final-download.png', fullPage: false });
await b.close();
