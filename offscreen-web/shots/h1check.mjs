import { chromium } from 'playwright';
const b = await chromium.launch({ proxy: { server: 'per-context' } });
for (const w of [390, 820, 1440]) {
  const ctx = await b.newContext({ viewport: { width: w, height: 900 } });
  const page = await ctx.newPage();
  await page.goto('http://localhost:4179/#/', { waitUntil: 'load' });
  await page.waitForTimeout(500);
  const info = await page.evaluate(() => {
    const h1 = document.querySelector('.hero h1');
    const r = h1.getBoundingClientRect();
    return { text: h1.innerText.replace(/\n/g, ' / '), lines: h1.getClientRects().length, width: Math.round(r.width), scroll: h1.scrollWidth };
  });
  console.log(w, JSON.stringify(info));
  await ctx.close();
}
await b.close();
