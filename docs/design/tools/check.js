// Runs a design-canvas board's DCLogic and reports how many values renderVals() returns, or the error.
// Usage: node check.js Board.dc.html [...]
const fs = require('fs');
for (const f of process.argv.slice(2)) {
  const src = fs.readFileSync(f, 'utf8');
  const m = src.match(/<script type="text\/x-dc"[^>]*>([\s\S]*?)<\/script>/);
  try {
    const C = new Function('DCLogic', m[1] + '\nreturn Component;')(class { constructor(p) { this.props = p; } setState() {} });
    const c = new C({});
    const v = c.renderVals();
    console.log(f, 'ok', Object.keys(v).length, 'values');
  } catch (e) { console.log(f, 'ERROR', e.message); }
}
