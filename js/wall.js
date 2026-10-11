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
  var hiDPI = !LITE && Math.max(screen.width, screen.height) * (window.devicePixelRatio || 1) > 1400;

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
    var s;
    if (PORTRAIT) s = W < 600 ? W / 470 : Math.min(W / 768, H / 1024);
    else s = Math.min(W / 1024, H / 768);
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

  function clockHTML(now) {
    var h = now.getHours();
    var hs = cfg.use24h === false ? (h % 12 || 12) : pad(h);
    return '<div class="time num">' + hs + '<span class="colon">:</span>' + pad(now.getMinutes()) + '</div>' +
      '<div class="date">' + (now.getMonth() + 1) + '月' + now.getDate() + '日<span class="sep">·</span>' +
        WEEK_CN[now.getDay()] + '</div>' +
      '<div class="lunar">' + lunarLine(now) + '</div>';
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

  function storyHTML(p) {
    var credit = p.artist ? '© ' + esc(p.artist) + (p.license ? ' · ' + esc(p.license) : '') : '';
    return '<div class="s-title">' + esc(p.title) + '</div>' +
      (p.sub ? '<div class="s-sub">' + esc(p.sub) + '</div>' : '') +
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
    if (!LITE && cfg.kenBurns !== false) {
      ph.className += ' kb kb' + (1 + Math.floor(Math.random() * 4));
      var dur = (cfg.intervalMinutes || 5) * 60 + 20;
      ph.style.webkitAnimationDuration = ph.style.animationDuration = dur + 's';
    }
    el('div', 'shade', s);

    var panel = el('div', 'panel' + (t.style === 'glass' ? ' glass' : ''), s);
    if (t.style === 'band' || t.style === 'paper') el('div', 'panel-bg', panel).style.backgroundColor = t.bg;
    var inner = el('div', 'panel-in', panel);
    if (p.kind === 'east') {
      el('div', 'seal', inner, NUM_CN[vm.getMonth()].split('').join('<br>') + '<br>月');
    }
    el('div', 'b-clock', inner, clockHTML(now));
    el('div', 'b-month', inner, monthHTML(vm.getFullYear(), vm.getMonth(), now));
    el('div', 'b-story', inner, storyHTML(p));

    // 极简 / 玻璃：图片说明放在对角的下方
    var fl = el('div', 'story-float' + (t.style === 'glass' ? ' glass' : ''), s);
    el('div', 'b-story', el('div', 'panel-in', fl), storyHTML(p));

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
      c._bg.style.left = (g.x - c.offsetLeft) + 'px';
      c._bg.style.top = (g.y - c.offsetTop) + 'px';
      c._bg.style.width = g.w + 'px';
      c._bg.style.height = g.h + 'px';
    }
  }

  // ------------------------------------------------------------ 轮播
  var queue = [], cur = null, timer = null, busy = false;

  function shuffle(a) {
    for (var i = a.length - 1; i > 0; i--) {
      var j = Math.floor(Math.random() * (i + 1));
      var t = a[i]; a[i] = a[j]; a[j] = t;
    }
    return a;
  }

  function nextPhoto() {
    if (!queue.length) {
      queue = shuffle(photos.slice());
      if (cur && queue.length > 1 && queue[queue.length - 1] === cur._p) queue.unshift(queue.pop());
    }
    return queue.pop();
  }

  function srcOf(p) { return hiDPI && p.l ? p.l : p.m; }

  function show(p) {
    busy = true;
    var img = new Image();
    var done = false;
    function go() {
      if (done) return;
      done = true;
      var s = buildSlide(p);
      s._photoEl.style.backgroundImage = 'url("' + img.src + '")';
      stage.appendChild(s);
      layoutGlass(s);
      void s.offsetWidth;
      toggleClass(s, 'in', true);
      var old = cur;
      cur = s;
      setTimeout(function () {
        if (old && old.parentNode) {
          old._photoEl.style.backgroundImage = 'none';
          var gb = old.querySelectorAll('.glass-bg');
          for (var i = 0; i < gb.length; i++) gb[i].style.backgroundImage = 'none';
          old.parentNode.removeChild(old);
        }
        busy = false;
      }, 2400);
      schedule();
      preload();
    }
    img.onload = go;
    img.onerror = function () { busy = false; done = true; setTimeout(advance, 500); };
    img.src = srcOf(p);
    setTimeout(function () { if (!done && img.complete) go(); }, 8000);
  }

  var upcoming = null, upImg = null;
  function preload() {
    upcoming = nextPhoto();
    upImg = new Image();
    upImg.src = srcOf(upcoming);
    var b = new Image();
    b.src = upcoming.b;
  }

  function advance() {
    if (busy || !photos.length) return;
    var p = upcoming || nextPhoto();
    upcoming = null;
    show(p);
  }

  function schedule() {
    clearTimeout(timer);
    timer = setTimeout(advance, (cfg.intervalMinutes || 5) * 60000);
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

  function renderMonths() {
    if (!cur) return;
    var vm = viewMonth(), now = new Date();
    var ms = cur.querySelectorAll('.b-month');
    for (var i = 0; i < ms.length; i++) ms[i].innerHTML = monthHTML(vm.getFullYear(), vm.getMonth(), now);
    var seal = cur.querySelector('.seal');
    if (seal) seal.innerHTML = NUM_CN[vm.getMonth()].split('').join('<br>') + '<br>月';
    paint(cur);
    layoutGlass(cur);
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
        for (var i = 0; i < cs.length; i++) cs[i].innerHTML = clockHTML(now);
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

  // ------------------------------------------------------------ 启动
  var resizeTimer = null;
  window.addEventListener('resize', function () {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(function () { measure(); if (cur) layoutGlass(cur); }, 80);
  }, false);
  window.addEventListener('orientationchange', function () {
    setTimeout(function () { measure(); if (cur) layoutGlass(cur); }, 400);
  }, false);

  measure();
  toggleClass(document.body, 'lite', LITE);
  if (!photos.length) {
    toggleClass(document.body, 'empty', true);
  } else {
    // ?photo=3 打开指定图片（调试用）
    var m = /[?&]photo=(\d+)/.exec(location.search);
    show(m ? photos[+m[1] % photos.length] : nextPhoto());
  }
  tick();
})();
