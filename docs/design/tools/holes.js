// Checks that every {{hole}} in a design-canvas board resolves to a value from renderVals() in its
// default state. Known false alarms: the literals true and false (hint-placeholder-val), a loop
// variable reused across loops, and lists nested three deep. Usage: node holes.js Board.dc.html [...]
const fs = require('fs');
for (const f of process.argv.slice(2)) {
  const src = fs.readFileSync(f, 'utf8');
  const m = src.match(/<script type="text\/x-dc"[^>]*>([\s\S]*?)<\/script>/);
  const C = new Function('DCLogic', m[1] + '\nreturn Component;')(class { constructor(p) { this.props = p; } setState() {} });
  const v = new C({}).renderVals();
  const tpl = src.slice(0, src.indexOf('<script type="text/x-dc"'));
  const loops = {};
  for (const lm of tpl.matchAll(/<sc-for list="\{\{([\w.]+)\}\}" as="(\w+)"/g)) loops[lm[2]] = lm[1];
  const missing = new Set();
  for (const h of tpl.matchAll(/\{\{([\w.]+)\}\}/g)) {
    const parts = h[1].split('.');
    if (parts.length === 1) { if (!(parts[0] in v)) missing.add(h[1]); continue; }
    const listPath = loops[parts[0]];
    if (!listPath) { missing.add(h[1] + ' (unknown loop var)'); continue; }
    // resolve the list (may itself be nested: r.sealOpts)
    const lp = listPath.split('.');
    let lists = lp.length === 1 ? [v[lp[0]]] : (v[loops[lp[0]]] || []).map((x) => x[lp[1]]);
    const items = lists.flat().filter(Boolean);
    if (!items.length) { missing.add(h[1] + ' (empty list in default state)'); continue; }
    if (!items.some((it) => parts[1] in it)) missing.add(h[1]);
  }
  console.log(f, missing.size ? 'MISSING: ' + [...missing].join(', ') : 'all holes resolve');
}
