// Exports every little fumibako from docs/design/fumibako-studies.html as a standalone SVG:
// each finish, lid and tie, shut and open, at 120 x 80, in Aria's sakura ink by day, colors resolved.
// Usage (Playwright installed; the study wrapped in a page with <!doctype html>, <head> and <body>):
//   node tools/export_fumibako_svgs.js <study page.html> docs/design/fumibako-svg
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

(async () => {
  const [page, out] = process.argv.slice(2);
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1200, height: 900 } });
  await p.emulateMedia({ colorScheme: 'light' });
  await p.goto('file://' + page);
  await p.waitForTimeout(1500);
  const files = await p.evaluate(() => {
    // Day, so the inks are the day's.
    document.getElementById('time-hiru').click();
    const style = document.createElement('style');
    style.textContent = '.fbx, .fbx * { transition: none !important; }';
    document.head.appendChild(style);
    const host = document.createElement('div');
    host.style.cssText = 'position: absolute; left: 0; top: 0; --ink: var(--ink-sakura); --tc: var(--ink-sakura); --pat: var(--pat-dots);';
    document.body.appendChild(host);
    const canvas = document.createElement('canvas'); canvas.width = canvas.height = 1;
    const g = canvas.getContext('2d', { willReadFrequently: true });
    const hex = c => {
      if (!c || c === 'none') return 'none';
      g.clearRect(0, 0, 1, 1); g.fillStyle = '#000'; g.fillStyle = c; g.fillRect(0, 0, 1, 1);
      const [r, gg, bb] = g.getImageData(0, 0, 1, 1).data;
      return '#' + [r, gg, bb].map(v => v.toString(16).padStart(2, '0')).join('');
    };
    const shown = el => { let o = 1; for (let e = el; e && e.tagName !== 'svg'; e = e.parentElement) o *= +getComputedStyle(e).opacity; return o; };
    const m = x => +x.toFixed(3);
    const aria = { id: 'aria', name: 'Aria', ink: 'sakura', pat: 'dots', kanji: '桜' };
    const out = {};
    for (const finish of ['lacquer', 'washi', 'kiri']) for (const tie of ['bow', 'cross', 'mizuhiki', 'tassel']) for (const lid of ['plate', 'seal', 'engraved', 'gilded', 'kanji']) for (const open of [false, true]) {
      host.innerHTML = box(aria, { finish, lid, tie }, { open, W: 120, H: 80 });
      const svg = host.querySelector('svg');
      const root = svg.getScreenCTM().inverse();
      const layer = el => { for (let e = el.parentElement; e && e !== svg; e = e.parentElement) { const c = e.getAttribute('class'); if (c) return c.split(' ')[0]; } return 'box'; };
      const parts = [];
      let defs = '';
      // Washi's pattern, with its dots in the paper's color.
      const pat = svg.querySelector('pattern');
      if (pat) {
        const kids = [...pat.children].map(k => { const c = k.cloneNode(); c.removeAttribute('class'); c.setAttribute('fill', hex(getComputedStyle(k).fill)); const op = getComputedStyle(k).opacity; if (op !== '1') c.setAttribute('opacity', op); return c.outerHTML; }).join('');
        const pc = pat.cloneNode(); pc.setAttribute('id', 'pattern'); defs = `<defs>${pc.outerHTML.replace('</pattern>', kids + '</pattern>')}</defs>`;
      }
      for (const el of svg.querySelectorAll('rect, circle, ellipse, path, polygon, text')) {
        if (el.closest('pattern') || el.closest('defs')) continue;
        const op = shown(el); if (op < 0.01) continue;
        const cs = getComputedStyle(el);
        const ctm = root.multiply(el.getScreenCTM());
        const c = el.cloneNode(true);
        c.removeAttribute('class'); c.removeAttribute('style'); c.removeAttribute('transform');
        const fill = el.getAttribute('fill');
        c.setAttribute('fill', fill && fill.startsWith('url(') ? 'url(#pattern)' : hex(cs.fill));
        if (cs.stroke && cs.stroke !== 'none') { c.setAttribute('stroke', hex(cs.stroke)); c.setAttribute('stroke-width', parseFloat(cs.strokeWidth)); c.setAttribute('stroke-linecap', cs.strokeLinecap); c.setAttribute('stroke-linejoin', cs.strokeLinejoin); }
        if (op < 0.999) c.setAttribute('opacity', m(op));
        if (Math.abs(ctm.a - 1) > 1e-6 || Math.abs(ctm.b) > 1e-6 || Math.abs(ctm.c) > 1e-6 || Math.abs(ctm.d - 1) > 1e-6 || Math.abs(ctm.e) > 1e-6 || Math.abs(ctm.f) > 1e-6)
          c.setAttribute('transform', `matrix(${m(ctm.a)} ${m(ctm.b)} ${m(ctm.c)} ${m(ctm.d)} ${m(ctm.e)} ${m(ctm.f)})`);
        parts.push([layer(el), c.outerHTML]);
      }
      // Group the shapes by the layer they were drawn in (lid, inside, the tie's parts).
      const groups = []; let cur = null;
      for (const [name, html] of parts) { if (!cur || cur.name !== name) { cur = { name, items: [] }; groups.push(cur); } cur.items.push(html); }
      const body = groups.map(gr => `  <g id="${gr.name}">\n    ${gr.items.join('\n    ')}\n  </g>`).join('\n');
      const name = `${finish}/${tie}-${lid}-${open ? 'open' : 'shut'}.svg`;
      out[name] = `<svg xmlns="http://www.w3.org/2000/svg" width="288" height="216" viewBox="-12 -22 144 108">\n  <!-- Fumibako box: ${finish}, ${lid} on the lid, ${tie}, ${open ? 'open' : 'shut'}. 120 x 80 (the viewBox has room for the lid and loose cords). Aria's ink (sakura), autumn by day. -->\n  ${defs}\n${body}\n</svg>\n`;
    }
    return out;
  });
  for (const [name, text] of Object.entries(files)) {
    const file = path.join(out, name);
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, text);
  }
  console.log('wrote', Object.keys(files).length, 'files');
  await b.close();
})();
