/*
 * 光影台历 —— 主程序
 * 只使用 ES5 + 带前缀的 CSS，保证 iPad mini 1（iOS 9.3.5 Safari）可用。
 */
(function () {
  'use strict';

  var cfg = window.CONFIG || {};
  var photos = (window.PHOTOS || []).slice();

  var MONTH_EN = ['JANUARY', 'FEBRUARY', 'MARCH', 'APRIL', 'MAY', 'JUNE', 'JULY',
    'AUGUST', 'SEPTEMBER', 'OCTOBER', 'NOVEMBER', 'DECEMBER'];
  var MONTH_CN = ['一月', '二月', '三月', '四月', '五月', '六月', '七月',
    '八月', '九月', '十月', '十一月', '十二月'];
  var WEEK_CN = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'];
  var WEEK_EN = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
  var WEEK_SHORT = ['日', '一', '二', '三', '四', '五', '六'];

  var FLIP_MS = 820;      // 翻页动画时长（与 CSS 一致）
  var LIFT_DEG = 104;     // 纸页翻起的最大角度

  // ------------------------------------------------------------ 小工具
  function $(id) { return document.getElementById(id); }
  function pad(n) { return n < 10 ? '0' + n : '' + n; }
  function el(tag, cls, parent, html) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (html !== undefined) e.innerHTML = html;
    if (parent) parent.appendChild(e);
    return e;
  }
  function hasClass(e, c) { return (' ' + e.className + ' ').indexOf(' ' + c + ' ') >= 0; }
  function addClass(e, c) { if (!hasClass(e, c)) e.className += ' ' + c; }
  function removeClass(e, c) {
    e.className = (' ' + e.className + ' ').replace(' ' + c + ' ', ' ').replace(/^\s+|\s+$/g, '');
  }
  function toggleClass(e, c, on) { if (on) addClass(e, c); else removeClass(e, c); }
  function setTransform(e, t) { e.style.webkitTransform = t; e.style.transform = t; }
  function setTransition(e, t) { e.style.webkitTransition = t ? t.replace(/transform/g, '-webkit-transform') : t; e.style.transition = t; }
  function dayStart(d) { return new Date(d.getFullYear(), d.getMonth(), d.getDate()); }
  function addDays(d, n) { return new Date(d.getFullYear(), d.getMonth(), d.getDate() + n); }
  function sameDay(a, b) {
    return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
  }
  function dayIndex(d) { return Math.round(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()) / 86400000); }
  function minutesOf(hhmm) { var p = String(hhmm).split(':'); return (+p[0]) * 60 + (+p[1] || 0); }
  function store(k, v) {
    try { if (v === undefined) return localStorage.getItem(k); localStorage.setItem(k, v); } catch (e) { /* 隐私模式 */ }
    return null;
  }
  function esc(s) {
    return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  // ------------------------------------------------------------ 每天一张图（固定种子洗牌，同一天永远是同一张）
  (function shuffle() {
    var seed = 20261010;
    function rnd() { // mulberry32
      seed = (seed + 0x6D2B79F5) | 0;
      var t = Math.imul ? Math.imul(seed ^ (seed >>> 15), 1 | seed) : (seed ^ (seed >>> 15)) * (1 | seed);
      t = (t + (Math.imul ? Math.imul(t ^ (t >>> 7), 61 | t) : (t ^ (t >>> 7)) * (61 | t))) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    }
    for (var i = photos.length - 1; i > 0; i--) {
      var j = Math.floor(rnd() * (i + 1));
      var t = photos[i]; photos[i] = photos[j]; photos[j] = t;
    }
  })();

  function photoFor(d) {
    if (!photos.length) return null;
    var n = photos.length;
    return photos[((dayIndex(d) % n) + n) % n];
  }

  var hiDPI = Math.max(screen.width, screen.height) * (window.devicePixelRatio || 1) > 1400;
  function srcOf(p) { return hiDPI && p.l ? p.l : p.m; }

  // ------------------------------------------------------------ 尺寸与布局
  var W = 0, H = 0, REM = 16, OV = 0;
  var stage = $('stage');

  function measure() {
    W = window.innerWidth;
    H = window.innerHeight;
    var short = Math.min(W, H);
    var s = short < 600 ? short / 430 : short / 768;
    // 横屏手机高度太矮时再缩一点，保证卡片放得下
    if (W > H && H / s < 23 * 16) s = H / (23 * 16);
    REM = 16 * s;
    OV = Math.round(Math.max(W, H) * 0.035); // 视差需要的出血
    document.documentElement.style.fontSize = REM + 'px';
    var b = document.body;
    toggleClass(b, 'portrait', H > W);
    toggleClass(b, 'landscape', W >= H);
    toggleClass(b, 'narrow', W / REM < 46);
    toggleClass(b, 'short', H / REM < 30);
    stage.style.webkitPerspective = stage.style.perspective = Math.round(Math.max(W, H) * 1.6) + 'px';
  }

  // 照片在屏幕上的覆盖几何（background-size: cover 的手动版本）
  function coverGeom(p) {
    var cw = W + OV * 2, ch = H + OV * 2;
    var iw = p ? p.w : cw, ih = p ? p.h : ch;
    var sc = Math.max(cw / iw, ch / ih);
    return { x: -OV + (cw - iw * sc) / 2, y: -OV + (ch - ih * sc) / 2, w: iw * sc, h: ih * sc };
  }

  // ------------------------------------------------------------ 玻璃
  // 每块玻璃里放一层“预模糊的同一张照片”，与背景精确对齐，再轻微放大模拟厚玻璃折射。
  var lumCache = {};

  function blurLum(p, cb) {
    if (!p) return cb(null);
    if (lumCache[p.b]) return cb(lumCache[p.b]);
    var img = new Image();
    img.onload = function () {
      try {
        var c = document.createElement('canvas');
        c.width = 32; c.height = Math.max(1, Math.round(32 * img.height / img.width));
        var x = c.getContext('2d');
        x.drawImage(img, 0, 0, c.width, c.height);
        lumCache[p.b] = { w: c.width, h: c.height, d: x.getImageData(0, 0, c.width, c.height).data };
        cb(lumCache[p.b]);
      } catch (e) { cb(null); }
    };
    img.onerror = function () { cb(null); };
    img.src = p.b;
  }

  function regionLum(p, data, r) {
    if (!data) return p ? p.lum : 80;
    var g = coverGeom(p);
    var x0 = Math.floor((r.x - g.x) / g.w * data.w), x1 = Math.ceil((r.x + r.w - g.x) / g.w * data.w);
    var y0 = Math.floor((r.y - g.y) / g.h * data.h), y1 = Math.ceil((r.y + r.h - g.y) / g.h * data.h);
    x0 = Math.max(0, x0); y0 = Math.max(0, y0); x1 = Math.min(data.w, x1); y1 = Math.min(data.h, y1);
    var sum = 0, n = 0;
    for (var y = y0; y < y1; y++) {
      for (var x = x0; x < x1; x++) {
        var i = (y * data.w + x) * 4;
        sum += data.d[i] * 0.299 + data.d[i + 1] * 0.587 + data.d[i + 2] * 0.114;
        n++;
      }
    }
    return n ? sum / n : p.lum;
  }

  // 结构：.glass（圆角+投影） > .glass-clip（裁切，内含模糊底图和高光） + .glass-tint + 内容
  function initGlass(g) {
    if (g._bg) return;
    var clip = el('div', 'glass-clip');
    g.insertBefore(clip, g.firstChild);
    g._bg = el('div', 'glass-bg', clip);
    g._spec = el('div', 'spec', clip);
  }

  // rect：玻璃相对于照片坐标系（屏幕）的位置
  function alignGlass(g, p, rect, lumData) {
    initGlass(g);
    var bg = g._bg;
    var geo = coverGeom(p);
    bg.style.left = (geo.x - rect.x) + 'px';
    bg.style.top = (geo.y - rect.y) + 'px';
    bg.style.width = geo.w + 'px';
    bg.style.height = geo.h + 'px';
    bg.style.backgroundImage = p ? 'url("' + p.b + '")' : '';
    // 以玻璃中心为原点放大一点点 → 折射感
    var ox = rect.x - geo.x + rect.w / 2, oy = rect.y - geo.y + rect.h / 2;
    bg.style.webkitTransformOrigin = bg.style.transformOrigin = ox + 'px ' + oy + 'px';
    g._rect = rect;
    var lum = regionLum(p, lumData, rect);
    toggleClass(g, 'tone-light', lum > 150);
    toggleClass(g, 'tone-mid', lum > 105 && lum <= 150);
  }

  // ------------------------------------------------------------ 一页纸
  function buildDateCard(sheet, d, info, p) {
    var l = info.lunar;
    var start = new Date(d.getFullYear(), 0, 1);
    var doy = Math.round((dayStart(d) - start) / 86400000) + 1;
    var chips = '';
    for (var i = 0; i < info.festivals.length; i++) chips += '<span class="chip">' + esc(info.festivals[i]) + '</span>';
    var card = el('div', 'glass card date-card', sheet,
      '<div class="glass-tint"></div>' +
      '<div class="card-body">' +
        '<div class="dc-top"><span class="dc-month">' + MONTH_EN[d.getMonth()] + '</span>' +
          '<span class="dc-year">' + d.getFullYear() + '</span></div>' +
        '<div class="dc-main">' +
          '<div class="dc-day">' + d.getDate() + '</div>' +
          '<div class="dc-side">' +
            '<div class="dc-week">' + WEEK_CN[d.getDay()] + '</div>' +
            '<div class="dc-weeken">' + WEEK_EN[d.getDay()] + '</div>' +
          '</div>' +
        '</div>' +
        '<div class="dc-lunar"><span>农历' + l.monthName + l.dayName + '</span>' +
          '<span class="dot">·</span><span>' + l.ganzhi + l.animal + '年</span>' + chips + '</div>' +
        '<div class="dc-foot">' + MONTH_CN[d.getMonth()] + ' · 第 ' + doy + ' 天</div>' +
      '</div>');
    if (p && p.accent) {
      var cs = card.querySelectorAll('.chip');
      for (var k = 0; k < cs.length; k++) cs[k].style.backgroundColor = p.accent;
    }
    return card;
  }

  function buildMonthCard(sheet, d, today) {
    var y = d.getFullYear(), m = d.getMonth();
    var first = new Date(y, m, 1).getDay();
    var days = new Date(y, m + 1, 0).getDate();
    var html = '<div class="glass-tint"></div><div class="card-body">' +
      '<div class="mc-head"><span class="mc-title">' + MONTH_CN[m] + '</span><span class="mc-sub">' + y + '</span></div>' +
      '<div class="mc-grid">';
    for (var w = 0; w < 7; w++) html += '<b class="' + (w === 0 || w === 6 ? 'we' : '') + '">' + WEEK_SHORT[w] + '</b>';
    for (var i = 0; i < first; i++) html += '<i class="blank"></i>';
    for (var dd = 1; dd <= days; dd++) {
      var date = new Date(y, m, dd);
      var cls = 'c';
      var wd = date.getDay();
      if (wd === 0 || wd === 6) cls += ' we';
      if (dd === d.getDate()) cls += ' sel';
      if (sameDay(date, today)) cls += ' today';
      if (Lunar.info(date).festivals.length) cls += ' fest';
      html += '<i class="' + cls + '" data-d="' + dd + '">' + dd + '</i>';
    }
    html += '</div></div>';
    var card = el('div', 'glass card month-card', sheet, html);
    card.setAttribute('data-ym', y + '-' + m);
    return card;
  }

  function makeSheet(d) {
    var p = photoFor(d);
    var info = Lunar.info(d);
    var sheet = el('div', 'sheet' + (p ? '' : ' nophoto'));
    sheet._date = d;
    sheet._photo = p;
    var ph = el('div', 'photo', sheet);
    sheet._photoEl = ph;
    var blur = el('div', 'photo-blur', ph);
    var full = el('div', 'photo-full', ph);
    el('div', 'vignette', sheet);
    el('div', 'shade', sheet);
    buildDateCard(sheet, d, info, p);
    buildMonthCard(sheet, d, today);

    if (p) {
      blur.style.backgroundImage = 'url("' + p.b + '")';
      var img = new Image();
      img.onload = function () {
        full.style.backgroundImage = 'url("' + img.src + '")';
        addClass(full, 'loaded');
      };
      img.src = srcOf(p);
    }
    return sheet;
  }

  function layoutSheet(sheet) {
    var p = sheet._photo;
    var geo = coverGeom(p);
    var ph = sheet._photoEl;
    ph.style.left = geo.x + 'px';
    ph.style.top = geo.y + 'px';
    ph.style.width = geo.w + 'px';
    ph.style.height = geo.h + 'px';
    var cards = sheet.querySelectorAll('.glass');
    blurLum(p, function (data) {
      for (var i = 0; i < cards.length; i++) {
        var c = cards[i];
        if (c.offsetWidth === 0) continue; // 窄屏隐藏的月历
        alignGlass(c, p, { x: c.offsetLeft, y: c.offsetTop, w: c.offsetWidth, h: c.offsetHeight }, data);
      }
    });
  }

  // 顶部浮层的玻璃跟随当前页的照片
  function layoutHud() {
    if (!cur) return;
    var p = cur._photo;
    var gs = document.querySelectorAll('#hud .glass');
    blurLum(p, function (data) {
      for (var i = 0; i < gs.length; i++) {
        var g = gs[i];
        if (g.offsetWidth === 0) continue;
        var r = g.getBoundingClientRect();
        alignGlass(g, p, { x: r.left, y: r.top, w: r.width, h: r.height }, data);
      }
    });
    var credit = $('credit');
    var title = p ? p.title : '光影台历';
    $('credit-title').textContent = title;
    $('credit-more').textContent = p && p.artist ? '© ' + p.artist + (p.license ? ' · ' + p.license : '') : '';
    credit.style.display = title ? '' : 'none';
    toggleClass($('btn-today'), 'hidden', sameDay(cur._date, today));
  }

  // ------------------------------------------------------------ 倾斜：视差 + 高光
  var tilt = { x: 0, y: 0, tx: 0, ty: 0, running: false, b0: null, g0: null };

  function applyTilt() {
    var px = -tilt.x * OV * 0.85, py = -tilt.y * OV * 0.85;
    var move = 'translate3d(' + px.toFixed(1) + 'px,' + py.toFixed(1) + 'px,0)';
    var spec = 'translate3d(' + (tilt.x * 22).toFixed(1) + '%,' + (tilt.y * 22).toFixed(1) + '%,0)';
    if (cur) {
      setTransform(cur._photoEl, move);
      var gs = cur.querySelectorAll('.glass');
      for (var i = 0; i < gs.length; i++) {
        if (gs[i]._bg) setTransform(gs[i]._bg, move + ' scale(1.06)');
        if (gs[i]._spec) setTransform(gs[i]._spec, spec);
      }
    }
    var hs = document.querySelectorAll('#hud .glass');
    for (var k = 0; k < hs.length; k++) {
      if (hs[k]._bg) setTransform(hs[k]._bg, move + ' scale(1.06)');
      if (hs[k]._spec) setTransform(hs[k]._spec, spec);
    }
  }

  var raf = window.requestAnimationFrame || window.webkitRequestAnimationFrame ||
    function (f) { return setTimeout(f, 16); };

  function tiltLoop() {
    var dx = tilt.tx - tilt.x, dy = tilt.ty - tilt.y;
    if (Math.abs(dx) < 0.002 && Math.abs(dy) < 0.002) { tilt.running = false; return; }
    tilt.x += dx * 0.1;
    tilt.y += dy * 0.1;
    if (!flipping) applyTilt();
    raf(tiltLoop);
  }
  function kickTilt() { if (!tilt.running) { tilt.running = true; raf(tiltLoop); } }

  function clamp(v, a) { return v < -a ? -a : v > a ? a : v; }

  if (cfg.tiltEffects !== false) {
    window.addEventListener('deviceorientation', function (e) {
      if (e.beta === null || e.gamma === null) return;
      var b = e.beta, g = e.gamma;
      if (tilt.b0 === null) { tilt.b0 = b; tilt.g0 = g; }
      // 慢慢把“中立位置”追到当前姿态，这样无论怎么摆放，静止时都居中
      tilt.b0 += (b - tilt.b0) * 0.004;
      tilt.g0 += (g - tilt.g0) * 0.004;
      var db = b - tilt.b0, dg = g - tilt.g0, x, y;
      var o = window.orientation || 0;
      if (o === 90) { x = db; y = -dg; } else if (o === -90) { x = -db; y = dg; } else if (o === 180) { x = -dg; y = -db; } else { x = dg; y = db; }
      tilt.tx = clamp(x / 18, 1);
      tilt.ty = clamp(y / 18, 1);
      kickTilt();
    }, false);
    window.addEventListener('mousemove', function (e) {
      tilt.tx = (e.clientX / W * 2 - 1) * 0.7;
      tilt.ty = (e.clientY / H * 2 - 1) * 0.7;
      kickTilt();
    }, false);
  }

  // ------------------------------------------------------------ 翻页
  var today = dayStart(new Date());
  var cur = null;
  var flipping = false;
  var lastTouch = Date.now();

  function liftTransform(p) {
    return 'translate3d(0,0,0) rotateX(' + (-p * LIFT_DEG).toFixed(2) + 'deg)';
  }
  function setLift(sheet, p) {
    setTransform(sheet, liftTransform(p));
    sheet.style.opacity = p < 0.72 ? 1 : Math.max(0, 1 - (p - 0.72) / 0.22);
    sheet._shade.style.opacity = (p * 0.45).toFixed(3);
  }
  function setUnder(sheet, p) {
    // 下面那页：被上面纸页的影子盖住，翻起越多越亮
    sheet._shade.style.opacity = ((1 - p) * 0.5).toFixed(3);
  }

  function mount(sheet, onTop) {
    sheet._shade = sheet.querySelector('.shade');
    if (onTop || !cur) stage.appendChild(sheet);
    else stage.insertBefore(sheet, cur);
    layoutSheet(sheet);
  }

  function release(sheet) {
    if (!sheet || !sheet.parentNode) return;
    var layers = sheet.querySelectorAll('.photo-full, .photo-blur, .glass-bg');
    for (var i = 0; i < layers.length; i++) layers[i].style.backgroundImage = 'none';
    sheet.parentNode.removeChild(sheet);
  }

  function settle(sheet) {
    cur = sheet;
    setTransform(cur, '');
    cur.style.opacity = 1;
    cur._shade.style.opacity = 0;
    flipping = false;
    layoutHud();
    applyTilt();
    preload();
  }

  // 用动画把 sheet 的翻起程度从 from 过渡到 to
  function animate(sheet, under, to, done) {
    // 翻走时最后才淡出；翻回来时一开始就要不透明
    var fade = to > 0 ? 'opacity ' + Math.round(FLIP_MS * 0.35) + 'ms ease-in ' + Math.round(FLIP_MS * 0.55) + 'ms'
                      : 'opacity ' + Math.round(FLIP_MS * 0.25) + 'ms ease-out';
    setTransition(sheet, 'transform ' + FLIP_MS + 'ms cubic-bezier(.32,.08,.24,1), ' + fade);
    sheet._shade.style.webkitTransition = sheet._shade.style.transition = 'opacity ' + FLIP_MS + 'ms ease';
    if (under) under._shade.style.webkitTransition = under._shade.style.transition = 'opacity ' + FLIP_MS + 'ms ease';
    void sheet.offsetWidth;
    setLift(sheet, to);
    if (under) setUnder(under, to);
    setTimeout(function () {
      setTransition(sheet, '');
      sheet._shade.style.webkitTransition = sheet._shade.style.transition = '';
      if (under) under._shade.style.webkitTransition = under._shade.style.transition = '';
      done();
    }, FLIP_MS + 40);
  }

  function goTo(d, fromDrag) {
    if (flipping || !cur || sameDay(d, cur._date)) return;
    flipping = true;
    hideHint();
    var forward = d > cur._date;
    var sheet = makeSheet(d);
    var old = cur;
    if (forward) {
      // 当前页向上翻走，露出下面的新一页
      mount(sheet, false);
      setUnder(sheet, 0);
      animate(old, sheet, 1, function () { release(old); settle(sheet); });
    } else {
      // 上一页从上方翻落盖下来
      mount(sheet, true);
      setLift(sheet, 1);
      setUnder(old, 0);
      animate(sheet, old, 0, function () { release(old); settle(sheet); });
    }
  }

  function next() { if (cur) goTo(addDays(cur._date, 1)); }
  function prev() { if (cur) goTo(addDays(cur._date, -1)); }

  // 预加载前后两天的大图
  var preloaded = [];
  function preload() {
    preloaded = [];
    var ds = [addDays(cur._date, 1), addDays(cur._date, -1)];
    for (var i = 0; i < ds.length; i++) {
      var p = photoFor(ds[i]);
      if (!p) continue;
      var a = new Image(); a.src = srcOf(p); preloaded.push(a);
      var b = new Image(); b.src = p.b; preloaded.push(b);
    }
  }

  // ------------------------------------------------------------ 手势：跟手翻页
  var drag = null;

  document.addEventListener('touchmove', function (e) { e.preventDefault(); }, false);

  document.addEventListener('touchstart', function (e) {
    lastTouch = Date.now();
    if (flipping || e.touches.length > 1) return;
    var t = e.touches[0];
    drag = { x: t.clientX, y: t.clientY, t: Date.now(), mode: null, sheet: null, p: 0, lastY: t.clientY, lastT: Date.now(), v: 0 };
  }, false);

  document.addEventListener('touchmove', function (e) {
    if (!drag || flipping) return;
    var t = e.touches[0];
    var dx = t.clientX - drag.x, dy = t.clientY - drag.y;
    if (!drag.mode) {
      if (Math.abs(dx) < 8 && Math.abs(dy) < 8) return;
      if (Math.abs(dy) > Math.abs(dx)) {
        drag.mode = dy < 0 ? 'next' : 'prev';
        hideHint();
        var d = addDays(cur._date, drag.mode === 'next' ? 1 : -1);
        drag.sheet = makeSheet(d);
        if (drag.mode === 'next') { mount(drag.sheet, false); setUnder(drag.sheet, 0); } else { mount(drag.sheet, true); setLift(drag.sheet, 1); }
      } else {
        drag.mode = 'h';
      }
    }
    if (drag.mode === 'next' || drag.mode === 'prev') {
      var now = Date.now();
      drag.v = (t.clientY - drag.lastY) / Math.max(1, now - drag.lastT);
      drag.lastY = t.clientY; drag.lastT = now;
      var p = Math.max(0, Math.min(1, -dy / (H * 0.75)));
      if (drag.mode === 'prev') p = Math.max(0, Math.min(1, dy / (H * 0.75)));
      drag.p = p;
      if (drag.mode === 'next') { setLift(cur, p); setUnder(drag.sheet, p); } else { setLift(drag.sheet, 1 - p); setUnder(cur, p); }
    }
  }, false);

  document.addEventListener('touchend', function (e) {
    if (!drag) return;
    var d = drag; drag = null;
    var t = e.changedTouches[0];
    var dx = t.clientX - d.x, dy = t.clientY - d.y;
    if (d.mode === 'next' || d.mode === 'prev') {
      var fling = d.mode === 'next' ? d.v < -0.5 : d.v > 0.5;
      var commit = d.p > 0.28 || fling;
      var old = cur, sheet = d.sheet;
      flipping = true;
      if (d.mode === 'next') {
        animate(old, sheet, commit ? 1 : 0, function () {
          if (commit) { release(old); settle(sheet); } else { release(sheet); settle(old); }
        });
      } else {
        animate(sheet, old, commit ? 0 : 1, function () {
          if (commit) { release(old); settle(sheet); } else { release(sheet); settle(old); }
        });
      }
      return;
    }
    if (d.mode === 'h') {
      if (Math.abs(dx) > 50) { if (dx < 0) next(); else prev(); }
      return;
    }
    if (Date.now() - d.t < 500) onTap(e.target);
  }, false);

  // iOS 13+ 需要用户点一下才允许读取陀螺仪（iOS 9 / 安卓不需要）
  var askedMotion = false;
  function askMotion() {
    if (askedMotion) return;
    askedMotion = true;
    var D = window.DeviceOrientationEvent;
    if (cfg.tiltEffects !== false && D && typeof D.requestPermission === 'function') {
      try { D.requestPermission(); } catch (e) { /* 忽略 */ }
    }
  }

  function onTap(target) {
    askMotion();
    var n = target;
    while (n && n !== document.body) {
      if (n.getAttribute && n.getAttribute('data-d') && hasClass(n, 'c')) {
        var cd = cur._date;
        goTo(new Date(cd.getFullYear(), cd.getMonth(), +n.getAttribute('data-d')));
        return;
      }
      if (n.id === 'btn-today') { goTo(today); return; }
      if (n.id === 'btn-full') { fullscreen(); return; }
      if (n.id === 'credit') { toggleClass(n, 'open', !hasClass(n, 'open')); setTimeout(layoutHud, 20); setTimeout(layoutHud, 420); return; }
      n = n.parentNode;
    }
  }

  // 电脑浏览器：点击 / 键盘 / 滚轮
  document.addEventListener('click', function (e) { if (!('ontouchstart' in window)) onTap(e.target); }, false);
  document.addEventListener('keydown', function (e) {
    lastTouch = Date.now();
    if (e.keyCode === 38 || e.keyCode === 37) prev();
    if (e.keyCode === 40 || e.keyCode === 39 || e.keyCode === 32) next();
  }, false);
  var wheelLock = 0;
  window.addEventListener('wheel', function (e) {
    lastTouch = Date.now();
    if (Date.now() - wheelLock < 900 || Math.abs(e.deltaY) < 20) return;
    wheelLock = Date.now();
    if (e.deltaY > 0) next(); else prev();
  }, false);

  // ------------------------------------------------------------ 全屏（安卓 Chrome 等支持的浏览器）
  var root = document.documentElement;
  var reqFS = root.requestFullscreen || root.webkitRequestFullscreen;
  function isFS() { return !!(document.fullscreenElement || document.webkitFullscreenElement); }
  function fullscreen() { if (reqFS && !isFS()) reqFS.call(root); }
  function updateFSButton() {
    toggleClass($('btn-full'), 'hidden', !reqFS || isFS() || window.navigator.standalone);
    setTimeout(onResize, 300);
  }
  document.addEventListener('fullscreenchange', updateFSButton, false);
  document.addEventListener('webkitfullscreenchange', updateFSButton, false);

  // ------------------------------------------------------------ 提示
  var hint = $('hint');
  function hideHint() { addClass(hint, 'gone'); store('cal.hint', '1'); }
  if (!store('cal.hint')) {
    addClass(hint, 'show');
    setTimeout(hideHint, 6000);
  } else {
    addClass(hint, 'gone');
  }

  // ------------------------------------------------------------ 时钟 / 跨天 / 夜间
  var lastMinute = -1;
  function tick() {
    var now = new Date();
    if (now.getMinutes() !== lastMinute) {
      lastMinute = now.getMinutes();
      $('clock-text').textContent = pad(now.getHours()) + ':' + pad(now.getMinutes());
    }
    var t = dayStart(now);
    if (!sameDay(t, today)) {
      var wasToday = cur && sameDay(cur._date, today);
      today = t;
      if (wasToday) goTo(today); // 午夜自动翻到新的一天
    }
    if (cur && !flipping && !drag && !sameDay(cur._date, today) &&
        Date.now() - lastTouch > (cfg.returnToTodayMinutes || 3) * 60000) {
      lastTouch = Date.now();
      goTo(today);
    }
    if (cfg.night && cfg.night.enabled) {
      var m = now.getHours() * 60 + now.getMinutes();
      var a = minutesOf(cfg.night.start), b = minutesOf(cfg.night.end);
      var on = a < b ? (m >= a && m < b) : (m >= a || m < b);
      $('night').style.opacity = on ? cfg.night.opacity : 0;
    }
    setTimeout(tick, 1000 - now.getMilliseconds() + 5);
  }

  // ------------------------------------------------------------ 启动
  var resizeTimer = null;
  function onResize() {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(function () {
      measure();
      if (cur) layoutSheet(cur);
      layoutHud();
      applyTilt();
    }, 60);
  }
  window.addEventListener('resize', onResize, false);
  window.addEventListener('orientationchange', function () {
    tilt.b0 = null;
    setTimeout(onResize, 350);
  }, false);

  measure();
  if (!cfg.showClock) $('clock').style.display = 'none';
  if (!photos.length) addClass(document.body, 'empty');
  updateFSButton();

  // 支持 ?date=2026-10-01 直接打开某天（调试用）
  var m = /[?&]date=(\d{4})-(\d{1,2})-(\d{1,2})/.exec(location.search);
  var startDate = m ? new Date(+m[1], +m[2] - 1, +m[3]) : today;
  cur = makeSheet(startDate);
  mount(cur, true);
  settle(cur);
  tick();
  setTimeout(function () { addClass(document.body, 'ready'); }, 30);

  // 每天凌晨 4 点整页刷新一次，防止老设备长时间运行后内存堆积
  setInterval(function () {
    var n = new Date();
    if (n.getHours() === 4 && n.getMinutes() === 0 && Date.now() - lastTouch > 60000) location.reload();
  }, 50000);
})();
