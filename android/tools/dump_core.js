// 校验用：加载 js/lunar.js、js/mottos.js，按 wall.js 的洗牌逐日输出。参数：lunar|motto
const fs = require('fs'), vm = require('vm'), path = require('path');
const root = path.join(__dirname, '..', '..');
const ctx = { window: {} }; ctx.global = ctx; vm.createContext(ctx);
vm.runInContext(fs.readFileSync(path.join(root, 'js/lunar.js'), 'utf8'), ctx);
vm.runInContext(fs.readFileSync(path.join(root, 'js/mottos.js'), 'utf8') + ';this.MOTTOS=MOTTOS;', ctx);
const Lunar = ctx.Lunar, MOTTO = ctx.MOTTOS;
const mottoOrder = (function () {
  var idx = [], seed = 7;
  for (var i = 0; i < MOTTO.length; i++) idx.push(i);
  for (var j = idx.length - 1; j > 0; j--) {
    seed = (seed * 9301 + 49297) % 233280;
    var k = Math.floor(seed / 233280 * (j + 1));
    var t = idx[j]; idx[j] = idx[k]; idx[k] = t;
  }
  return idx;
})();
const lunar = process.argv[2] === 'lunar';
const out = [];
const [y0, y1] = lunar ? [1901, 2099] : [2026, 2030];
for (let y = y0; y <= y1; y++) for (let m = 1; m <= 12; m++) {
  const n = new Date(y, m, 0).getDate();
  for (let d = 1; d <= n; d++) {
    const date = new Date(y, m - 1, d);
    const ds = y + '-' + String(m).padStart(2, '0') + '-' + String(d).padStart(2, '0');
    if (lunar) {
      const i = Lunar.info(date), l = i.lunar;
      out.push([ds, l.year, l.month, l.day, l.isLeap, l.monthName, l.dayName, l.ganzhi, l.animal, i.festivals.join(','), i.term].join('\t'));
    } else {
      const day = Math.round(Date.UTC(y, m - 1, d) / 86400000);
      const t = MOTTO[mottoOrder[((day % MOTTO.length) + MOTTO.length) % MOTTO.length]];
      out.push([ds, t[0], t[1]].join('\t'));
    }
  }
}
process.stdout.write(out.join('\n') + '\n');
