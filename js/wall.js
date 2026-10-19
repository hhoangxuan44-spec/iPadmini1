/*
 * 光影月历 —— 壁纸轮播 + 月历
 * 只使用 ES5 + 带前缀的 CSS，保证 iPad mini 1（iOS 9.3.5 Safari）可用。
 *
 * 每张图片自带一套「日历主题」（构建时分析得出，见 build/theme.py）：
 *   t.style  minimal 极简 / glass 玻璃 / magazine 杂志 / band 色带 / paper 宣纸
 *   t.pos    tl tr bl br（角落）· l r（侧栏）· b（底部）
 *   t.ink    light 浅色字 / dark 深色字
 *   t.accent 点缀色；t.bg 色带或纸张颜色
 */
(function () {
  'use strict';

  var cfg = window.CONFIG || {};
  var photos = (window.PHOTOS || []).slice();

  var MONTH_EN = ['January', 'February', 'March', 'April', 'May', 'June', 'July',
    'August', 'September', 'October', 'November', 'December'];
  var NUM_CN = ['一', '二', '三', '四', '五', '六', '七', '八', '九', '十', '十一', '十二'];
  var WEEK_CN = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'];
  var WEEK_SHORT = ['日', '一', '二', '三', '四', '五', '六'];
  var FEST_SHORT = { '劳动节': '劳动', '国庆节': '国庆', '儿童节': '儿童', '教师节': '教师',
    '妇女节': '妇女', '青年节': '青年', '建党节': '建党', '建军节': '建军', '植树节': '植树',
    '情人节': '情人', '愚人节': '愚人', '平安夜': '平安夜', '圣诞节': '圣诞', '母亲节': '母亲',
    '父亲节': '父亲', '感恩节': '感恩', '元宵节': '元宵', '端午节': '端午', '中秋节': '中秋',
    '重阳节': '重阳', '腊八节': '腊八', '中元节': '中元' };

  // ------------------------------------------------------------ 环境
  var ua = navigator.userAgent;
  var iosVer = /OS (\d+)_\d+(_\d+)? like Mac OS X/.exec(ua);
  // iOS 10 及以下（iPad mini 1 / iPad 2 / iPad 3 等）视为老设备：关掉耗性能的效果
  var LITE = (iosVer && +iosVer[1] <= 10) || /[?&]lite=1/.test(location.search);
  // 安卓 App 里运行时：app=wall 动态壁纸 / dream 屏保 / full 全屏摆件；it、ib 是上下要避开的状态栏与 Dock（CSS 像素）
  function param(k) { var m = new RegExp('[?&]' + k + '=([\\w.]+)').exec(location.search); return m ? m[1] : ''; }
  var APP = param('app');
  var WALL = APP === 'wall';
  var INSET_T = +param('it') || 0, INSET_B = +param('ib') || 0;
  var hiDPI = !LITE && (!!APP || Math.max(screen.width, screen.height) * (window.devicePixelRatio || 1) > 1400);

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
  function esc(s) {
    return String(s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }
  function hasClass(e, c) { return (' ' + e.className + ' ').indexOf(' ' + c + ' ') >= 0; }
  function toggleClass(e, c, on) {
    var has = hasClass(e, c);
    if (on && !has) e.className += ' ' + c;
    if (!on && has) e.className = (' ' + e.className + ' ').replace(' ' + c + ' ', ' ').replace(/^\s+|\s+$/g, '');
  }
  function sameDay(a, b) {
    return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
  }
  function minutesOf(hhmm) { var p = String(hhmm).split(':'); return (+p[0]) * 60 + (+p[1] || 0); }
  function setTransform(e, t) { e.style.webkitTransform = t; e.style.transform = t; }

  // ------------------------------------------------------------ 尺寸
  var W = 0, H = 0, PORTRAIT = false;
  var stage = $('stage');

  function measure() {
    W = window.innerWidth;
    H = window.innerHeight;
    PORTRAIT = H > W * 1.05;
    var s, h = H - INSET_T - INSET_B;
    if (PORTRAIT) s = W < 600 ? W / 470 : Math.min(W / 768, h / 1024);
    else s = Math.min(W / 1024, h / 768);
    if (WALL) s *= 0.9;          // 桌面上还有图标，日历稍微小一点
    document.documentElement.style.fontSize = (16 * s) + 'px';
    toggleClass(document.body, 'portrait', PORTRAIT);
    toggleClass(document.body, 'landscape', !PORTRAIT);
  }

  // ------------------------------------------------------------ 内容块
  function lunarLine(d) {
    var info = Lunar.info(d), l = info.lunar;
    var chips = '';
    for (var i = 0; i < info.festivals.length && i < 2; i++) {
      chips += '<span class="chip acc-bg">' + esc(info.festivals[i]) + '</span>';
    }
    return '农历' + l.monthName + l.dayName + '<span class="sep">·</span>' + l.ganzhi + l.animal + '年' + chips;
  }

  // 每日一言：按日期在固定洗牌后的列表里取一句（同一天始终是同一句）
  var MOTTO = window.MOTTOS || [];
  var mottoOrder = (function () {
    var idx = [], seed = 7;
    for (var i = 0; i < MOTTO.length; i++) idx.push(i);
    for (var j = idx.length - 1; j > 0; j--) {
      seed = (seed * 9301 + 49297) % 233280;
      var k = Math.floor(seed / 233280 * (j + 1));
      var t = idx[j]; idx[j] = idx[k]; idx[k] = t;
    }
    return idx;
  })();
  function mottoHTML(now) {
    if (!MOTTO.length) return '';
    var day = Math.round(Date.UTC(now.getFullYear(), now.getMonth(), now.getDate()) / 86400000);
    var m = MOTTO[mottoOrder[((day % MOTTO.length) + MOTTO.length) % MOTTO.length]];
    return '<div class="motto">' + esc(m[0]) + '<span>' + esc(m[1]) + '</span></div>';
  }

  function timeHTML(now) {
    var h = now.getHours();
    var hs = cfg.use24h === false ? (h % 12 || 12) : pad(h);
    return '<div class="time num">' + hs + '<span class="colon">:</span>' + pad(now.getMinutes()) + '</div>';
  }

  // 时钟块：不同风格用不同的版式
  function clockHTML(now, style) {
    var date = (now.getMonth() + 1) + '月' + now.getDate() + '日<span class="sep">·</span>' + WEEK_CN[now.getDay()];
    if (style === 'poster') {
      return '<div class="p-row"><div class="p-day num">' + now.getDate() + '</div>' +
        '<div class="p-meta"><div class="p-week">' + WEEK_CN[now.getDay()] + '</div>' +
        '<div class="p-mon num">' + MONTH_EN[now.getMonth()] + ' ' + now.getFullYear() + '</div>' +
        '<div class="lunar">' + lunarLine(now) + '</div>' + timeHTML(now) + '</div></div>' + mottoHTML(now);
    }
    if (style === 'vpoem') {
      return timeHTML(now) + '<div class="date">' + date + '</div>';
    }
    return timeHTML(now) + '<div class="date">' + date + '</div>' +
      '<div class="lunar">' + lunarLine(now) + '</div>' + mottoHTML(now);
  }

  function cellSub(info) {
    if (info.festivals.length) {
      var f = info.festivals[0];
      return { text: FEST_SHORT[f] || f, fest: true };
    }
    var l = info.lunar;
    if (l.day === 1) return { text: l.monthName, fest: false };
    return { text: l.dayName, fest: false };
  }

  function monthHTML(y, m, today) {
    var first = new Date(y, m, 1).getDay();
    var days = new Date(y, m + 1, 0).getDate();
    var html = '<div class="m-head">' +
        '<span class="m-num num">' + (m + 1) + '</span>' +
        '<span class="m-names"><b class="num">' + MONTH_EN[m] + '</b><i>' + y + ' · ' + NUM_CN[m] + '月</i></span>' +
      '</div><div class="grid">';
    for (var w = 0; w < 7; w++) html += '<em class="' + (w === 0 || w === 6 ? 'we' : '') + '">' + WEEK_SHORT[w] + '</em>';
    for (var i = 0; i < first; i++) html += '<i class="blank"></i>';
    for (var d = 1; d <= days; d++) {
      var date = new Date(y, m, d);
      var info = Lunar.info(date);
      var sub = cellSub(info);
      var cls = 'c';
      var wd = date.getDay();
      if (wd === 0 || wd === 6) cls += ' we';
      if (sub.fest) cls += ' fest';
      if (sameDay(date, today)) cls += ' today';
      html += '<i class="' + cls + '"><b class="num">' + d + '</b><s>' + esc(sub.text) + '</s></i>';
    }
    return html + '</div>';
  }

  // 竖排：每个字一格、每个分句一列、从右往左排。
  // 不用 writing-mode，任何设备（含 iOS 9 和缺少竖排字体度量的系统）效果都一致。
  function vcol(text, cls) {
    var h = '<div class="vc ' + cls + '">';
    for (var i = 0; i < text.length; i++) {
      var ch = text.charAt(i);
      if (ch === ' ' || ch === '·') { h += '<i class="sp"></i>'; continue; }
      h += '<i>' + esc(ch) + '</i>';
    }
    return h + '</div>';
  }
  function verticalHTML(p) {
    var cols = vcol(p.title.replace(/\s*·\s*/g, '·'), 'vp-title');
    var parts = p.poem[0].split(/[，。；？！、：]/);
    for (var i = 0; i < parts.length; i++) if (parts[i]) cols += vcol(parts[i], 'vp-poem');
    cols += vcol(p.poem[1].replace(/[《》]/g, '·').replace(/·$/, ''), 'vp-src');
    return cols;
  }

  function storyHTML(p, noPoem) {
    var credit = p.artist ? '© ' + esc(p.artist) + (p.license ? ' · ' + esc(p.license) : '') : '';
    var poem = !noPoem && p.poem && p.poem[0] ?
      '<div class="s-poem">' + esc(p.poem[0]) + '<span>' + esc(p.poem[1]) + '</span></div>' : '';
    return '<div class="s-title">' + esc(p.title) + '</div>' +
      (p.sub ? '<div class="s-sub">' + esc(p.sub) + '</div>' : '') + poem +
      (p.desc ? '<div class="s-desc">' + esc(p.desc) + '</div>' : '') +
      (credit ? '<div class="s-credit">' + credit + '</div>' : '');
  }

  // ------------------------------------------------------------ 一张壁纸
  var monthOffset = 0;

  function viewMonth() {
    var t = new Date();
    return new Date(t.getFullYear(), t.getMonth() + monthOffset, 1);
  }

  function buildSlide(p) {
    var t = p.t || { style: 'glass', pos: 'br', ink: 'light', accent: '#ffd60a' };
    var now = new Date();
    var vm = viewMonth();
    var cls = 'slide st-' + t.style + ' pos-' + t.pos + ' ink-' + t.ink + ' k-' + (p.kind || 'nature');
    var s = el('div', cls);
    s._p = p;
    s._t = t;

    var ph = el('div', 'photo', s);
    ph.style.backgroundColor = t.bg || '#111';
    s._photoEl = ph;
    s._fullEl = ph;
    var shade = el('div', 'shade', s);

    if (t.style === 'gallery') {
      // 画廊：墙面是同一幅画的模糊放大，画作完整挂在墙上（卡纸 + 画框）
      if (p.b) ph.style.backgroundImage = 'url("' + p.b + '")';
      shade.style.backgroundColor = t.bg || '#151515';
      var art = el('div', 'art', s);
      s._artEl = art;
      s._fullEl = el('div', 'art-img', art);
    } else if (!LITE && !WALL && cfg.kenBurns !== false) {
      // 推拉动画在这页真正露出来时才开始（见 startKenBurns）
      s._kb = ' kb kb' + (1 + Math.floor(Math.random() * 4));
      var dur = (cfg.intervalMinutes || 5) * 60 + 20;
      ph.style.webkitAnimationDuration = ph.style.animationDuration = dur + 's';
    }

    // 日历与说明放在 ui 层里；App 里 ui 层会让开状态栏和 Dock
    var ui = el('div', 'ui', s);
    ui.style.top = INSET_T + 'px';
    ui.style.bottom = INSET_B + 'px';
    s._ui = ui;
    var panel = el('div', 'panel' + (t.style === 'glass' ? ' glass' : ''), ui);
    if (t.style === 'band' || t.style === 'paper' || t.style === 'vpoem') {
      el('div', 'panel-bg', panel).style.backgroundColor = t.bg;
    }
    var inner = el('div', 'panel-in', panel);
    if (p.kind === 'east' || t.style === 'vpoem') {
      el('div', 'seal', inner, NUM_CN[vm.getMonth()].split('').join('<br>') + '<br>月');
    }
    if (t.style === 'vpoem' && p.poem) el('div', 'vp', inner, verticalHTML(p));
    el('div', 'b-clock', inner, clockHTML(now, t.style));
    el('div', 'b-month', inner, monthHTML(vm.getFullYear(), vm.getMonth(), now));
    el('div', 'b-story', inner, storyHTML(p));

    // 极简 / 玻璃 / 海报 / 诗笺：图片说明放在对角的下方
    var fl = el('div', 'story-float' + (t.style === 'glass' ? ' glass' : ''), ui);
    el('div', 'b-story', el('div', 'panel-in', fl), storyHTML(p, t.style === 'vpoem'));

    paint(s);
    return s;
  }

  // 主题色
  function paint(s) {
    var a = s._t.accent || '#ffd60a';
    var i, list = s.querySelectorAll('.acc-bg, .c.today b');
    for (i = 0; i < list.length; i++) list[i].style.backgroundColor = a;
    list = s.querySelectorAll('.m-num, .m-names b, .s-sub');
    for (i = 0; i < list.length; i++) list[i].style.color = a;
    list = s.querySelectorAll('.c.fest s');
    for (i = 0; i < list.length; i++) list[i].style.color = a;
    if (s._t.style === 'band') {
      list = s.querySelectorAll('.panel-bg');
      for (i = 0; i < list.length; i++) list[i].style.borderTopColor = a;
    }
  }

  // 照片在屏幕上的 cover 几何
  function coverGeom(p) {
    var iw = p.w || W, ih = p.h || H;
    var sc = Math.max(W / iw, H / ih);
    return { x: (W - iw * sc) / 2, y: (H - ih * sc) / 2, w: iw * sc, h: ih * sc };
  }

  // 画廊：在留给画作的区域里，按原比例放下「画 + 卡纸 + 画框」
  function layoutArt(s) {
    var a = s._artEl;
    if (!a) return;
    var p = s._p, rem = parseFloat(document.documentElement.style.fontSize) || 16;
    var x0, y0, aw, ah;
    if (PORTRAIT) {
      x0 = 1.6 * rem; y0 = 2 * rem + INSET_T; aw = W - 3.2 * rem; ah = (H - INSET_T - INSET_B) * 0.5;
    } else {
      var side = 24.5 * rem;                  // 右（或左）侧留给说明牌和月历
      x0 = s._t.pos === 'l' ? side : 2.6 * rem;
      y0 = 2.6 * rem + INSET_T; aw = W - side - 2.6 * rem; ah = H - INSET_T - INSET_B - 5.2 * rem;
    }
    var pad = 2 * (1.5 + 0.45) * rem;         // 卡纸 + 画框
    var ratio = (p.w || 4) / (p.h || 3);
    var iw = Math.min(aw - pad, (ah - pad) * ratio);
    var ih = iw / ratio;
    a.style.width = iw + 'px';
    a.style.height = ih + 'px';
    a.style.left = (x0 + (aw - iw - pad) / 2) + 'px';
    a.style.top = (y0 + (ah - ih - pad) / 2) + 'px';
  }

  function layoutSlide(s) { layoutArt(s); layoutGlass(s); }

  // 玻璃：在卡片里放一层预模糊的同一张图，与背景对齐
  function layoutGlass(s) {
    var p = s._p;
    var gs = s.querySelectorAll('.glass');
    var g = coverGeom(p);
    for (var i = 0; i < gs.length; i++) {
      var c = gs[i];
      if (!c.offsetWidth) continue;
      if (!c._bg) {
        var clip = el('div', 'glass-clip');
        c.insertBefore(clip, c.firstChild);
        c._bg = el('div', 'glass-bg', clip);
        if (p.b) c._bg.style.backgroundImage = 'url("' + p.b + '")';
      }
      c._bg.style.left = (g.x - c.offsetLeft - s._ui.offsetLeft) + 'px';
      c._bg.style.top = (g.y - c.offsetTop - s._ui.offsetTop) + 'px';
      c._bg.style.width = g.w + 'px';
      c._bg.style.height = g.h + 'px';
    }
  }

  // ------------------------------------------------------------ 轮播
  //
  // 为了换页顺滑（老设备尤其明显）：
  //   1. 下一页提前准备好：图片解码、排版、玻璃都在换页前完成，并且已经铺在当前页的下面
  //   2. 分段切换：旧日历先淡出 → 只把上层照片淡出、露出早已就位的下一页 → 新日历再轻轻浮现
  //      任何时刻屏幕上都只有一套日历，不会出现两套文字叠在一起的残影
  var queue = [], cur = null, nxt = null, timer = null, busy = false, wantReveal = false, preparing = false;
  var shownAt = 0, paused = false;
  var UI_OUT = 450, PHOTO_FADE = 1600, UI_IN_AT = 1000;

  function shuffle(a) {
    for (var i = a.length - 1; i > 0; i--) {
      var j = Math.floor(Math.random() * (i + 1));
      var t = a[i]; a[i] = a[j]; a[j] = t;
    }
    return a;
  }

  // ---- 应景选图：季节、早晚、节日会提高相应图片出现的机会（仍然是随机的）
  function scene(now) {
    var m = now.getMonth() + 1, h = now.getHours() + now.getMinutes() / 60;
    var season = m >= 3 && m <= 5 ? 'spring' : m >= 6 && m <= 8 ? 'summer' : m >= 9 && m <= 11 ? 'autumn' : 'winter';
    var part = (h >= 19 || h < 5) ? 'night' : h < 8.5 ? 'dawn' : h >= 16.5 ? 'dusk' : 'day';
    var l = Lunar.info(now).lunar, fest = [];
    if (!l.isLeap) {
      if (l.month === 8 && l.day >= 13 && l.day <= 17) fest.push('moon');               // 中秋
      if ((l.month === 12 && l.day >= 23) || (l.month === 1 && l.day <= 15)) fest.push('festive'); // 春节
      if (l.month === 1 && l.day >= 13 && l.day <= 15) fest.push('moon');               // 元宵
      if (l.month === 7 && l.day >= 5 && l.day <= 7) fest.push('stars');                // 七夕
      if (l.month === 9 && l.day >= 7 && l.day <= 9) fest.push('mountain');             // 重阳登高
    }
    return { season: season, part: part, fest: fest, key: season + part + fest.join() };
  }

  function weight(p, c) {
    var t = p.tags || [], w = 1;
    function has(x) { return t.indexOf(x) >= 0; }
    if (has('spring') || has('summer') || has('autumn') || has('winter')) w *= has(c.season) ? 3 : 0.35;
    var nightly = has('night') || has('stars');
    if (c.part === 'night') {
      if (nightly || has('moon')) w *= 3; else if (has('dawn')) w *= 0.5;
    } else {
      if (nightly) w *= 0.3;
      if (c.part === 'dawn' && has('dawn')) w *= 2.5;
      if (c.part === 'dusk' && has('dusk')) w *= 2.5;
    }
    for (var i = 0; i < c.fest.length; i++) if (has(c.fest[i])) w *= 6;
    return w;
  }

  // ---- 离线：断网（或连续加载失败）时只轮播离线图包里的图（p.o）
  var failStreak = 0;
  // App 自己缓存全部图片，不需要离线图包
  function offlineMode() { return !APP && (navigator.onLine === false || failStreak >= 3); }
  function usable(p, offline) {
    if (p._failAt && Date.now() - p._failAt < 30 * 60000) return false;   // 半小时内加载失败过
    return !offline || !!p.o;
  }

  var recent = [], sceneKey = '';
  function nextPhoto() {
    var c = scene(new Date());
    var offline = offlineMode();
    var key = c.key + (offline ? '|off' : '');
    if (!queue.length || key !== sceneKey) {
      // 加权随机排序（权重越高越可能排在前面），最近出现过的图先不放进来
      sceneKey = key;
      var keep = Math.min(30, Math.floor(photos.length / 3));
      var pool = [];
      for (var i = 0; i < photos.length; i++) {
        var p = photos[i];
        if (!usable(p, offline)) continue;
        if (recent.indexOf(p) >= 0 && photos.length > keep + 1) continue;
        pool.push({ p: p, k: Math.pow(Math.random(), 1 / weight(p, c)) });
      }
      if (!pool.length) {          // 万一一张都不符合，就不挑了，全部参与
        for (var k = 0; k < photos.length; k++) pool.push({ p: photos[k], k: Math.random() });
      }
      pool.sort(function (a, b) { return b.k - a.k; });
      queue = [];
      for (var j = 0; j < pool.length; j++) queue.push(pool[j].p);
    }
    var next = queue.shift();
    recent.push(next);
    if (recent.length > Math.min(30, Math.floor(photos.length / 3))) recent.shift();
    return next;
  }

  // 离线图包里只存了 1280 像素的版本，断网时都用它
  function srcOf(p) { return hiDPI && p.l && !offlineMode() ? p.l : p.m; }

  function startKenBurns(s) {
    if (s._kb) { s._photoEl.className += s._kb; s._kb = null; }
  }

  function release(s) {
    if (!s || !s.parentNode) return;
    s._photoEl.style.backgroundImage = 'none';
    s._fullEl.style.backgroundImage = 'none';
    var gb = s.querySelectorAll('.glass-bg');
    for (var i = 0; i < gb.length; i++) gb[i].style.backgroundImage = 'none';
    s.parentNode.removeChild(s);
  }

  // 加载图片并搭好一页（不显示）
  function loadSlide(p, cb) {
    var img = new Image();
    var done = false;
    function ok() {
      if (done) return;
      done = true;
      var s = buildSlide(p);
      s._fullEl.style.backgroundImage = 'url("' + img.src + '")';
      cb(s);
    }
    img.onload = function () { failStreak = 0; ok(); };
    img.onerror = function () {
      if (done) return;
      done = true;
      p._failAt = Date.now();
      failStreak++;
      cb(null);
    };
    img.src = srcOf(p);
  }

  // 提前准备下一页，铺在当前页下面（被当前页完全挡住）
  function prepare() {
    if (nxt || preparing || photos.length < 2) return;
    preparing = true;
    loadSlide(nextPhoto(), function (s) {
      preparing = false;
      if (!s) { setTimeout(prepare, 1000); return; }
      toggleClass(s, 'ui-off', true);
      stage.insertBefore(s, cur || null);
      layoutSlide(s);
      nxt = s;
      if (wantReveal) { wantReveal = false; reveal(); }
    });
  }

  function reveal() {
    if (busy) return;
    if (!nxt) { wantReveal = true; prepare(); return; }
    busy = true;
    var old = cur, s = nxt;
    nxt = null;
    cur = s;
    refreshSlide(s);           // 准备之后可能过了几分钟，先刷新时钟和月历
    shownAt = Date.now();
    startKenBurns(s);

    toggleClass(old, 'boot', false);
    toggleClass(old, 'ui-off', true);                                    // ① 旧日历淡出
    setTimeout(function () { toggleClass(old, 'out', true); }, UI_OUT);  // ② 旧照片淡出
    setTimeout(function () { toggleClass(s, 'ui-off', false); }, UI_OUT + UI_IN_AT); // ③ 新日历浮现
    setTimeout(function () {
      release(old);
      busy = false;
      schedule();
      setTimeout(prepare, 8000);                                         // 空闲时再准备下一页
    }, UI_OUT + PHOTO_FADE + 100);
  }

  function advance() {
    if (busy || !photos.length) return;
    reveal();
  }

  function schedule() {
    clearTimeout(timer);
    if (paused) return;
    var left = (cfg.intervalMinutes || 5) * 60000 - (Date.now() - shownAt);
    timer = setTimeout(advance, Math.max(left, 1000));
  }

  function boot(p) {
    loadSlide(p, function (s) {
      if (!s) { boot(nextPhoto()); return; }
      toggleClass(s, 'boot', true);
      // 开场淡入结束后移除动画，否则 animation 的 fill 会锁住 opacity，之后无法淡出
      setTimeout(function () { toggleClass(s, 'boot', false); }, 2000);
      stage.appendChild(s);
      layoutSlide(s);
      startKenBurns(s);
      cur = s;
      shownAt = Date.now();
      schedule();
      setTimeout(prepare, 3000);
    });
  }

  // ------------------------------------------------------------ 月份切换
  var monthTimer = null;
  var toastEl = $('toast'), toastTimer = null;

  function toast(text) {
    toastEl.innerHTML = text;
    toggleClass(toastEl, 'on', true);
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { toggleClass(toastEl, 'on', false); }, 1600);
  }

  function refreshSlide(s) {
    if (!s) return;
    var vm = viewMonth(), now = new Date(), i;
    var cs = s.querySelectorAll('.b-clock');
    for (i = 0; i < cs.length; i++) cs[i].innerHTML = clockHTML(now, s._t.style);
    var ms = s.querySelectorAll('.b-month');
    for (i = 0; i < ms.length; i++) ms[i].innerHTML = monthHTML(vm.getFullYear(), vm.getMonth(), now);
    var seal = s.querySelector('.seal');
    if (seal) seal.innerHTML = NUM_CN[vm.getMonth()].split('').join('<br>') + '<br>月';
    paint(s);
    layoutSlide(s);
  }

  function renderMonths() {
    refreshSlide(cur);
    refreshSlide(nxt);
  }

  function shiftMonth(delta) {
    monthOffset = delta === 0 ? 0 : monthOffset + delta;
    renderMonths();
    var vm = viewMonth();
    toast(monthOffset === 0 ? '本月' : vm.getFullYear() + ' 年 ' + (vm.getMonth() + 1) + ' 月');
    clearTimeout(monthTimer);
    if (monthOffset !== 0) {
      monthTimer = setTimeout(function () { shiftMonth(0); }, (cfg.returnToMonthSeconds || 60) * 1000);
    }
  }

  // ------------------------------------------------------------ 手势
  var sx = 0, sy = 0, st = 0;
  document.addEventListener('touchmove', function (e) { e.preventDefault(); }, false);
  document.addEventListener('touchstart', function (e) {
    var t = e.touches[0];
    sx = t.clientX; sy = t.clientY; st = Date.now();
  }, false);
  document.addEventListener('touchend', function (e) {
    var t = e.changedTouches[0];
    var dx = t.clientX - sx, dy = t.clientY - sy;
    if (Math.abs(dx) > 50 && Math.abs(dx) > Math.abs(dy)) {
      shiftMonth(dx < 0 ? 1 : -1);           // 左滑看下个月
    } else if (Math.abs(dx) < 12 && Math.abs(dy) < 12 && Date.now() - st < 600) {
      advance();                             // 轻点换下一张
    }
  }, false);
  // 电脑浏览器
  document.addEventListener('click', function () { if (!('ontouchstart' in window)) advance(); }, false);
  document.addEventListener('keydown', function (e) {
    if (e.keyCode === 37) shiftMonth(-1);
    if (e.keyCode === 39) shiftMonth(1);
    if (e.keyCode === 32 || e.keyCode === 13) advance();
  }, false);

  // ------------------------------------------------------------ 时钟 / 跨天 / 夜间
  var lastMin = -1, lastDay = new Date();
  function tick() {
    var now = new Date();
    if (now.getMinutes() !== lastMin) {
      lastMin = now.getMinutes();
      if (cur) {
        var cs = cur.querySelectorAll('.b-clock');
        for (var i = 0; i < cs.length; i++) cs[i].innerHTML = clockHTML(now, cur._t.style);
        if (!sameDay(now, lastDay)) { lastDay = now; renderMonths(); }
        paint(cur);
      }
      if (cfg.night && cfg.night.enabled && !/[?&]night=0/.test(location.search)) {
        var m = now.getHours() * 60 + now.getMinutes();
        var a = minutesOf(cfg.night.start), b = minutesOf(cfg.night.end);
        var on = a < b ? (m >= a && m < b) : (m >= a || m < b);
        $('night').style.opacity = on ? cfg.night.opacity : 0;
      }
      // 每天凌晨 4 点整页刷新，防止老设备长时间运行后内存堆积
      if (now.getHours() === 4 && now.getMinutes() === 0 && performanceAge() > 120000) location.reload();
    }
    setTimeout(tick, 1000 - now.getMilliseconds() + 5);
  }
  var bootAt = Date.now();
  function performanceAge() { return Date.now() - bootAt; }

  // ------------------------------------------------------------ 离线缓存（Application Cache，iOS 9 支持）
  // 第一次联网打开时，把网页和离线图包存到本地；之后网站更新时在后台下载新版本，下载完自动刷新。
  (function () {
    var ac = window.applicationCache;
    if (!ac || !ac.addEventListener) return;
    var first = ac.status === ac.UNCACHED || ac.status === ac.DOWNLOADING;
    ac.addEventListener('progress', function (e) {
      if (first && e.total) toast('离线图包下载中 ' + e.loaded + '/' + e.total);
    }, false);
    ac.addEventListener('cached', function () { toast('离线图包已就绪，断网也能用'); }, false);
    ac.addEventListener('updateready', function () {
      try { ac.swapCache(); } catch (e) { /* 忽略 */ }
      if (!busy) location.reload();
    }, false);
    ac.addEventListener('error', function () { /* 断网或服务器不可用，继续用已缓存的版本 */ }, false);
    // 联网状态变化时重新排队，让轮播立刻切换到合适的图片
    window.addEventListener('online', function () { failStreak = 0; queue = []; }, false);
    window.addEventListener('offline', function () { queue = []; }, false);
  })();

  // ------------------------------------------------------------ 安卓 App 调用
  // 壁纸被应用挡住时暂停换图；回到桌面时刷新时钟，超时了就立刻换一张
  window.WallApp = {
    next: function () { advance(); },
    pause: function () { paused = true; clearTimeout(timer); },
    resume: function () {
      if (!paused) return;
      paused = false;
      lastMin = -1;
      if (cur && !sameDay(new Date(), lastDay)) { lastDay = new Date(); renderMonths(); }
      else refreshSlide(cur);
      schedule();
    }
  };

  // ------------------------------------------------------------ 启动
  var resizeTimer = null;
  window.addEventListener('resize', function () {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(function () { measure(); if (cur) layoutSlide(cur); if (nxt) layoutSlide(nxt); }, 80);
  }, false);
  window.addEventListener('orientationchange', function () {
    setTimeout(function () { measure(); if (cur) layoutSlide(cur); if (nxt) layoutSlide(nxt); }, 400);
  }, false);

  measure();
  toggleClass(document.body, 'lite', LITE);
  if (APP) toggleClass(document.body, 'app-' + APP, true);
  if (!photos.length) {
    toggleClass(document.body, 'empty', true);
  } else {
    // ?photo=3 打开指定图片（调试用）
    var m = /[?&]photo=(\d+)/.exec(location.search);
    boot(m ? photos[+m[1] % photos.length] : nextPhoto());
  }
  tick();
})();
