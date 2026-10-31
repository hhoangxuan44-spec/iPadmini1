/*
 * 光影月历 —— 壁纸轮播 + 月历
 * 只使用 ES5 + 带前缀的 CSS，保证 iPad mini 1（iOS 9.3.5 Safari）可用。
 *
 * 每张图片自带一套「日历主题」（构建时分析得出，见 build/theme.py）：
 *   t.style  minimal 极简 / glass 玻璃 / magazine 杂志 / band 色带 / poster 海报 / paper 宣纸 / gallery 画廊 / vpoem 诗笺
 *   t.pos    tl tr bl br（角落）· l r（侧栏）· b（底部）
 *   t.ink    light 浅色字 / dark 深色字
 *   t.accent 点缀色；t.bg 色带或纸张颜色
 *   p.L      画面里的光：[光源 x, 光源 y, 方向性, 高光色, 阴影色]
 *
 * 光影：所有卡片的投影方向、边缘高光、阴影颜色都由这张照片的光决定（applyLight），
 * 像是真的把一块玻璃、一张纸放进了这张照片的光线里。
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
  // 动态光影：触摸时的光斑与倾斜、视差、定时扫过的高光、数字滚动、月历翻页。老设备和壁纸模式不开。
  var DYN = !LITE && !WALL && !/[?&]fx=0/.test(location.search);

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
  function setTransition(e, t) { e.style.webkitTransition = t; e.style.transition = t; }
  function closest(e, cls) { while (e && e !== document) { if (e.className && hasClass(e, cls)) return e; e = e.parentNode; } return null; }
  var raf = window.requestAnimationFrame || window.webkitRequestAnimationFrame || function (f) { return setTimeout(f, 16); };

  // ------------------------------------------------------------ 颜色与光
  function hex2rgb(h) {
    var m = /^#?([0-9a-f]{6})$/i.exec(h || '');
    if (!m) return [255, 255, 255];
    var n = parseInt(m[1], 16);
    return [n >> 16, (n >> 8) & 255, n & 255];
  }
  function rgba(c, a) {
    return 'rgba(' + Math.round(c[0]) + ',' + Math.round(c[1]) + ',' + Math.round(c[2]) + ',' + (Math.round(a * 1000) / 1000) + ')';
  }
  function mixc(a, b, t) { return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t]; }
  function n2(n) { return Math.round(n * 100) / 100; }
  var DEFAULT_L = [0.32, 0.08, 0.45, '#fff0dc', '#10101a'];

  /**
   * 在 (cx, cy)（相对于整张壁纸）这个位置上看到的光：
   *   d  指向光源的单位向量（光总是带一点来自上方的天光）
   *   k  光的方向性 0-1
   *   hl 高光色；sh 阴影色；wh 偏白的高光色
   */
  function litAt(s, cx, cy) {
    var L = s._p.L || DEFAULT_L;
    var lx = L[0] * W, ly = Math.min(L[1], 0.6) * H;
    var vx = (lx - cx) / W, vy = (ly - cy) / H - 0.3;
    var len = Math.sqrt(vx * vx + vy * vy);
    var d = len < 0.07 ? [-0.45, -0.89] : [vx / len, vy / len];
    var hl = hex2rgb(L[3]);
    return { d: d, k: L[2], hl: hl, sh: hex2rgb(L[4]), wh: mixc(hl, [255, 255, 255], 0.55) };
  }
  // 渐变的方向：从受光的一侧指向背光的一侧
  function gradAngle(d) { return Math.round(Math.atan2(-d[0], d[1]) * 180 / Math.PI); }

  // 投影：贴地的接触阴影 + 顺光的投射阴影 + 很大的环境阴影；颜色取这张照片最暗处的颜色
  function shadowStack(li, elev, strength) {
    var d = li.d, k = li.k, g = strength || 1;
    var reach = elev * (0.5 + 0.55 * k);
    var ox = -d[0] * reach, oy = -d[1] * reach + elev * 0.3;
    var blur = elev * (1.75 - 0.55 * k);
    return [
      '0 .05rem .12rem ' + rgba(li.sh, 0.5 * g),
      n2(ox * 0.45) + 'rem ' + n2(oy * 0.45) + 'rem ' + n2(blur * 0.4) + 'rem ' + rgba(li.sh, 0.4 * g),
      n2(ox) + 'rem ' + n2(oy) + 'rem ' + n2(blur) + 'rem ' + n2(-elev * 0.3) + 'rem ' + rgba(li.sh, (0.46 + 0.18 * k) * g),
      '0 ' + n2(elev * 1.5) + 'rem ' + n2(elev * 3) + 'rem ' + n2(-elev * 0.9) + 'rem rgba(0,0,0,' + n2(0.3 * g) + ')'
    ].join(',');
  }

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

  // 数字一位一格，换分钟时只有变了的那一位滚动
  function dg(ch) { return '<span class="dg"><i>' + ch + '</i></span>'; }
  function timeHTML(now) {
    var h = now.getHours();
    var hs = cfg.use24h === false ? String(h % 12 || 12) : pad(h);
    var mm = pad(now.getMinutes());
    var hh = '';
    for (var i = 0; i < hs.length; i++) hh += dg(hs.charAt(i));
    return '<div class="time num">' + hh + '<span class="colon">:</span>' + dg(mm.charAt(0)) + dg(mm.charAt(1)) + '</div>';
  }
  function timeDigits(now) {
    var h = now.getHours();
    var hs = cfg.use24h === false ? String(h % 12 || 12) : pad(h);
    return (hs + pad(now.getMinutes())).split('');
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
      var isToday = sameDay(date, today);
      if (isToday) cls += ' today';
      html += '<i class="' + cls + '">' + (isToday ? '<u></u>' : '') + '<b class="num">' + d + '</b><s>' + esc(sub.text) + '</s></i>';
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

  // 调试：?style=glass 把当前图片强行换成某种风格（位置、颜色按风格补全）
  function forceStyle(t, style) {
    var o = {}, k;
    for (k in t) if (t.hasOwnProperty(k)) o[k] = t[k];
    o.style = style;
    var side = t.pos === 'l' || t.pos === 'r';
    if (style === 'magazine' || style === 'vpoem' || style === 'gallery') o.pos = side ? t.pos : (/r/.test(t.pos) ? 'r' : 'l');
    else if (style === 'band') o.pos = 'b';
    else if (t.pos.length !== 2) o.pos = 'tl';
    if (style === 'paper' || style === 'vpoem') { o.bg = '#f1eada'; o.ink = 'dark'; o.accent = '#b23a2e'; }
    if (style === 'band' && t.style !== 'band') { o.bg = '#2a3350'; o.ink = 'light'; }
    if (style === 'gallery') { o.bg = '#2a2420'; o.ink = 'light'; }
    return o;
  }
  var FORCE = param('style');

  // 材质层：贴图、光、边缘、触摸光斑、定时扫过的高光。各风格的样子由 CSS 决定，方向和颜色由 applyLight 填
  function layers(parent) {
    el('div', 'm-tex', parent);
    el('div', 'm-light', parent);
    el('div', 'm-spot', parent);
    el('div', 'm-sweep', parent);
  }

  function buildSlide(p) {
    var t = p.t || { style: 'glass', pos: 'br', ink: 'light', accent: '#ffd60a' };
    if (FORCE && /^(minimal|glass|magazine|band|poster|paper|gallery|vpoem)$/.test(FORCE)) t = forceStyle(t, FORCE);
    var now = new Date();
    var vm = viewMonth();
    var cls = 'slide st-' + t.style + ' pos-' + t.pos + ' ink-' + t.ink + ' k-' + (p.kind || 'nature') + (DYN ? ' dyn' : '');
    var s = el('div', cls);
    s._p = p;
    s._t = t;

    // 第 0 层：远景（照片、暗角、光晕、颗粒）
    var bg = el('div', 'pl pl-bg', s);
    s._bgEl = bg;
    var ph = el('div', 'photo', bg);
    ph.style.backgroundColor = t.bg || '#111';
    s._photoEl = ph;
    s._fullEl = ph;
    var shade = el('div', 'shade', bg);
    if (t.style === 'gallery') el('div', 'wall-tex', bg);
    s._atmos = el('div', 'atmos', bg);

    if (t.style === 'gallery') {
      // 画廊：墙面是同一幅画的模糊放大，画作完整挂在墙上（线脚画框 + 卡纸 + 玻璃）
      if (p.b) ph.style.backgroundImage = 'url("' + p.b + '")';
      shade.style.backgroundColor = t.bg || '#151515';
      var plArt = el('div', 'pl pl-art', s);
      s._artPlane = plArt;
      var art = el('div', 'art', plArt);
      s._artEl = art;
      el('div', 'art-rim', art);
      var mat = el('div', 'art-mat', art);
      var pic = el('div', 'art-img', mat);
      s._fullEl = pic;
      el('div', 'art-glare', pic);
      el('div', 'art-shine', pic);
    } else if (!LITE && !WALL && cfg.kenBurns !== false) {
      // 推拉动画在这页真正露出来时才开始（见 startKenBurns）
      s._kb = ' kb kb' + (1 + Math.floor(Math.random() * 4));
      var dur = (cfg.intervalMinutes || 5) * 60 + 20;
      ph.style.webkitAnimationDuration = ph.style.animationDuration = dur + 's';
    }

    // 日历与说明放在 ui 层里；App 里 ui 层会让开状态栏和 Dock
    var ui = el('div', 'pl ui', s);
    ui.style.top = INSET_T + 'px';
    ui.style.bottom = INSET_B + 'px';
    s._ui = ui;
    var tilt = el('div', 'tilt', ui);
    var glass = t.style === 'glass';
    var panel = el('div', 'panel' + (glass ? ' glass' : ''), tilt);
    var plate = null;
    if (glass) {
      plate = el('div', 'glass-clip', panel);
      el('div', 'glass-bg', plate);
      layers(plate);
      el('div', 'm-rim', panel);
    } else if (t.style === 'band' || t.style === 'paper' || t.style === 'vpoem') {
      plate = el('div', 'panel-bg', panel);
      plate.style.backgroundColor = t.bg;
      layers(plate);
      el('div', 'm-rim', panel);
    }
    var inner = el('div', 'panel-in', panel);
    if (t.style === 'vpoem') el('div', 'rod', panel);
    if (p.kind === 'east' || t.style === 'vpoem') {
      el('div', 'seal', inner, NUM_CN[vm.getMonth()].split('').join('<br>') + '<br>月');
    }
    if (t.style === 'vpoem' && p.poem) el('div', 'vp', inner, verticalHTML(p));
    el('div', 'b-clock', inner, clockHTML(now, t.style));
    var bm = el('div', 'b-month', inner);
    el('div', 'mpage', bm, monthHTML(vm.getFullYear(), vm.getMonth(), now));
    el('div', 'b-story', inner, storyHTML(p));

    // 极简 / 玻璃 / 海报 / 诗笺：图片说明放在对角的下方
    var tilt2 = el('div', 'tilt', ui);
    var fl = el('div', 'story-float' + (glass ? ' glass' : ''), tilt2);
    if (glass) {
      var plate2 = el('div', 'glass-clip', fl);
      el('div', 'glass-bg', plate2);
      layers(plate2);
      el('div', 'm-rim', fl);
    }
    el('div', 'b-story', el('div', 'panel-in', fl), storyHTML(p, t.style === 'vpoem'));

    // 长按时的画面说明
    var cap = el('div', 'peek-cap', s);
    cap.innerHTML = '<b>' + esc(p.title) + '</b>' + (p.sub ? '<i>' + esc(p.sub) + '</i>' : '') +
      (p.poem && p.poem[0] ? '<p>' + esc(p.poem[0]) + '<span>' + esc(p.poem[1]) + '</span></p>' : '');

    paint(s);
    return s;
  }

  // 主题色
  function paint(s) {
    var a = s._t.accent || '#ffd60a', ac = hex2rgb(a);
    var i, list = s.querySelectorAll('.acc-bg, .c.today b, .c.today u');
    for (i = 0; i < list.length; i++) list[i].style.backgroundColor = a;
    list = s.querySelectorAll('.c.today b');
    for (i = 0; i < list.length; i++) {
      list[i].style.boxShadow = '0 .12rem .55rem ' + rgba(ac, 0.55) + ', inset 0 .06rem 0 rgba(255,255,255,.38), inset 0 -.08rem .12rem rgba(0,0,0,.14)';
    }
    list = s.querySelectorAll('.m-num, .m-names b, .s-sub');
    for (i = 0; i < list.length; i++) list[i].style.color = a;
    list = s.querySelectorAll('.c.fest s');
    for (i = 0; i < list.length; i++) list[i].style.color = a;
    if (s._t.style === 'band') {
      list = s.querySelectorAll('.panel-bg');
      for (i = 0; i < list.length; i++) {
        list[i].style.borderTopColor = a;
        list[i].style.boxShadow = '0 -.1rem 1.4rem ' + rgba(ac, 0.28);
      }
    }
  }

  // 照片在屏幕上的 cover 几何
  function coverGeom(p) {
    var iw = p.w || W, ih = p.h || H;
    var sc = Math.max(W / iw, H / ih);
    return { x: (W - iw * sc) / 2, y: (H - ih * sc) / 2, w: iw * sc, h: ih * sc };
  }

  // 画廊：在留给画作的区域里，按原比例放下「画 + 卡纸 + 画框」
  var FRAME = 0.62, MAT = 1.6;
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
    var padx = 2 * (FRAME + MAT) * rem;       // 画框 + 卡纸
    var ratio = (p.w || 4) / (p.h || 3);
    var iw = Math.min(aw - padx, (ah - padx) * ratio);
    var ih = iw / ratio;
    a.style.width = (iw + padx) + 'px';
    a.style.height = (ih + padx) + 'px';
    a.style.left = (x0 + (aw - iw - padx) / 2) + 'px';
    a.style.top = (y0 + (ah - ih - padx) / 2) + 'px';
  }

  function layoutSlide(s) { layoutArt(s); layoutGlass(s); applyLight(s); }

  // 玻璃：在卡片里放一层预模糊的同一张图，与背景对齐
  function layoutGlass(s) {
    var p = s._p;
    var gs = s.querySelectorAll('.glass');
    var g = coverGeom(p);
    for (var i = 0; i < gs.length; i++) {
      var c = gs[i];
      if (!c.offsetWidth) continue;
      var bgEl = c.querySelector('.glass-bg');
      if (!bgEl) continue;
      if (!bgEl._set) {
        bgEl._set = true;
        if (p.b) bgEl.style.backgroundImage = 'url("' + p.b + '")';
      }
      bgEl.style.left = (g.x - c.offsetLeft - s._ui.offsetLeft) + 'px';
      bgEl.style.top = (g.y - c.offsetTop - s._ui.offsetTop) + 'px';
      bgEl.style.width = g.w + 'px';
      bgEl.style.height = g.h + 'px';
    }
  }

  // ------------------------------------------------------------ 光影：按这张照片的光给每块材质上色、投影
  function applyLight(s) {
    var t = s._t, st = t.style, ink = t.ink, i;
    var ui = s._ui;
    var boxes = s.querySelectorAll('.panel, .story-float');
    for (i = 0; i < boxes.length; i++) {
      var c = boxes[i];
      if (!c.offsetWidth || !c.querySelector('.panel-bg, .glass-clip')) continue;
      var cx = ui.offsetLeft + c.offsetLeft + c.offsetWidth / 2, cy = ui.offsetTop + c.offsetTop + c.offsetHeight / 2;
      var li = litAt(s, cx, cy);
      var light = c.querySelector('.m-light'), rim = c.querySelector('.m-rim');
      var tl = closest(c, 'tilt');
      if (tl) tl.style.webkitTransformOrigin = tl.style.transformOrigin = n2(cx) + 'px ' + n2(cy - ui.offsetTop) + 'px';
      var d = li.d, ang = gradAngle(d);
      var hx = Math.round(50 + d[0] * 62), hy = Math.round(50 + d[1] * 62);
      if (hasClass(c, 'glass')) {
        c.style.boxShadow = shadowStack(li, 1.15, 1);
        var dark = ink === 'dark';        // 浅色玻璃（深色字）
        var hw = li.wh;
        light.style.backgroundImage =
          'radial-gradient(circle at ' + hx + '% ' + hy + '%, ' + rgba(hw, (dark ? 0.5 : 0.3) * (0.4 + li.k)) + ', ' + rgba(hw, 0) + ' 58%),' +
          (dark ?
            'linear-gradient(' + ang + 'deg, rgba(255,255,255,.62), rgba(255,255,255,.36) 55%, ' + rgba(mixc([255, 255, 255], li.sh, 0.25), 0.3) + ')' :
            'linear-gradient(' + ang + 'deg, ' + rgba(hw, 0.3) + ', ' + rgba(hw, 0.06) + ' 40%, ' + rgba(li.sh, 0.12) + ' 62%, ' + rgba(li.sh, 0.34) + ')');
        var ra = 0.42 + 0.4 * li.k;
        rim.style.boxShadow = [
          'inset ' + n2(-d[0] * 1.3) + 'px ' + n2(-d[1] * 1.3) + 'px 0 0 ' + rgba(hw, ra),
          'inset ' + n2(d[0] * 1) + 'px ' + n2(d[1] * 1) + 'px 0 0 rgba(255,255,255,' + (dark ? 0.35 : 0.13) + ')',
          'inset 0 0 0 1px rgba(255,255,255,' + (dark ? 0.4 : 0.1) + ')',
          'inset ' + n2(-d[0] * 0.7) + 'rem ' + n2(-d[1] * 0.8) + 'rem 1.3rem -.9rem ' + rgba(hw, (dark ? 0.6 : 0.4) * (0.5 + li.k * 0.5)),
          'inset ' + n2(d[0] * 0.7) + 'rem ' + n2(d[1] * 0.9) + 'rem 1.5rem -1rem ' + rgba(li.sh, dark ? 0.2 : 0.4)
        ].join(',');
      } else if (st === 'paper' || st === 'vpoem') {
        c.style.boxShadow = shadowStack(li, 0.95, 1);
        light.style.backgroundImage =
          'radial-gradient(circle at ' + hx + '% ' + hy + '%, rgba(255,252,240,' + n2(0.12 + 0.16 * li.k) + '), rgba(255,252,240,0) 62%),' +
          'linear-gradient(' + ang + 'deg, rgba(255,250,236,.38), rgba(255,250,236,0) 46%, ' + rgba(mixc([120, 88, 52], li.sh, 0.25), 0.13) + ')';
        rim.style.boxShadow = [
          'inset ' + n2(-d[0] * 1.2) + 'px ' + n2(-d[1] * 1.2) + 'px 0 0 rgba(255,253,244,.85)',
          'inset ' + n2(d[0] * 1) + 'px ' + n2(d[1] * 1.4) + 'px 0 0 ' + rgba(mixc([140, 108, 70], li.sh, 0.2), 0.28),
          'inset 0 0 2.4rem rgba(138,102,58,.2)'
        ].join(',');
      } else if (st === 'band') {
        light.style.backgroundImage =
          'linear-gradient(180deg, rgba(255,255,255,.2), rgba(255,255,255,.04) 38%, rgba(0,0,0,.0) 55%, rgba(0,0,0,.2)),' +
          'radial-gradient(ellipse at ' + Math.round(L_(s)[0] * 100) + '% 0%, ' + rgba(li.wh, 0.22 * (0.4 + li.k)) + ', ' + rgba(li.wh, 0) + ' 55%)';
        rim.style.boxShadow = 'inset 0 1px 0 rgba(255,255,255,.28)';
      }
    }
    applyArtLight(s);
  }
  function L_(s) { return s._p.L || DEFAULT_L; }

  // 画廊：画框、卡纸、玻璃反光、落在墙上的影子、射灯
  function applyArtLight(s) {
    var a = s._artEl;
    if (!a) return;
    var cx = a.offsetLeft + a.offsetWidth / 2, cy = a.offsetTop + a.offsetHeight / 2;
    var li = litAt(s, cx, cy), d = li.d;
    // 画挂在墙上，离墙很近：影子短而重，朝背光方向
    a.style.boxShadow = [
      '0 .05rem .1rem rgba(0,0,0,.6)',
      n2(-d[0] * 0.35) + 'rem ' + n2(-d[1] * 0.35 + 0.2) + 'rem .5rem ' + rgba(li.sh, 0.55),
      n2(-d[0] * 1.1) + 'rem ' + n2(-d[1] * 1.1 + 0.7) + 'rem 1.8rem -.1rem ' + rgba(li.sh, 0.55),
      '0 2.4rem 4.2rem -1.2rem rgba(0,0,0,.55)'
    ].join(',');
    var rim = a.querySelector('.art-rim');
    rim.style.boxShadow = [
      'inset 0 0 0 1px rgba(0,0,0,.7)',
      'inset ' + n2(-d[0] * 2) + 'px ' + n2(-d[1] * 2) + 'px 0 1px ' + rgba(mixc(li.wh, [255, 200, 140], 0.5), 0.5),
      'inset ' + n2(d[0] * 2) + 'px ' + n2(d[1] * 2) + 'px 0 1px rgba(0,0,0,.45)',
      'inset 0 0 0 .22rem rgba(0,0,0,.18)'
    ].join(',');
    var img = a.querySelector('.art-img');
    // 卡纸压在画上，在画面靠背光一侧投下一道影子
    img.style.boxShadow = [
      '0 0 0 .13rem #fbf8f0',
      '0 0 0 .17rem rgba(60,48,32,.4)',
      'inset ' + n2(-d[0] * 0.14) + 'rem ' + n2(-d[1] * 0.14) + 'rem .5rem rgba(0,0,0,.45)',
      'inset 0 0 0 1px rgba(0,0,0,.2)'
    ].join(',');
    var glare = a.querySelector('.art-glare');
    var ang = gradAngle(d);
    glare.style.backgroundImage =
      'linear-gradient(' + (ang + 90) + 'deg, rgba(255,255,255,0) 28%, ' + rgba(li.wh, 0.1 + 0.08 * li.k) + ' 38%, rgba(255,255,255,0) 47%, rgba(255,255,255,0) 60%, ' + rgba(li.wh, 0.05) + ' 68%, rgba(255,255,255,0) 76%)';
    // 墙面的射灯：从画的上方打下来，颜色取这张照片的高光色，偏暖
    var g = a.offsetLeft + a.offsetWidth / 2, pct = Math.round(g / W * 100);
    var warm = mixc(li.wh, [255, 226, 180], 0.55);
    s._atmos.style.backgroundImage =
      'radial-gradient(ellipse 62% 78% at ' + pct + '% -8%, ' + rgba(warm, 0.3) + ', ' + rgba(warm, 0.08) + ' 55%, ' + rgba(warm, 0) + ' 82%),' +
      'radial-gradient(ellipse 120% 100% at 50% 40%, rgba(0,0,0,0) 45%, rgba(0,0,0,.42))';
  }

  // 其余风格的暗角与高光晕：最亮的地方有一团柔和的光，四角略暗
  function applyAtmos(s) {
    if (s._t.style === 'gallery') return;
    var L = L_(s), hl = hex2rgb(L[3]), sh = hex2rgb(L[4]);
    var east = s._p.kind === 'east' || s._p.kind === 'west';
    var k = (east ? 0.45 : 1) * (0.5 + 0.5 * L[2]);
    s._atmos.style.backgroundImage =
      'radial-gradient(ellipse 46% 52% at ' + Math.round(L[0] * 100) + '% ' + Math.round(L[1] * 100) + '%, ' + rgba(hl, 0.16 * k) + ', ' + rgba(hl, 0) + '),' +
      'radial-gradient(ellipse 130% 120% at 50% 46%, rgba(0,0,0,0) 56%, ' + rgba(sh, 0.34 * (east ? 0.6 : 1)) + ')';
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
    // 清透鲜亮的图优先；暗调、灰调、单色图降低出现频率
    if (typeof p.v === 'number') w *= 0.45 + Math.max(0, Math.min(100, p.v)) / 100 * 1.5;
    // 古画/西方绘画少一些，和照片搭配着出现
    if (p.kind === 'east' || p.kind === 'west') w *= 0.5;
    if (has('spring') || has('summer') || has('autumn') || has('winter')) w *= has(c.season) ? 2 : 0.6;
    var nightly = has('night') || has('stars');
    if (c.part === 'night') {
      if (nightly || has('moon')) w *= 1.8; else if (has('dawn')) w *= 0.6;
    } else {
      if (nightly) w *= 0.4;
      if (c.part === 'dawn' && has('dawn')) w *= 1.8;
      if (c.part === 'dusk' && has('dusk')) w *= 1.8;
    }
    for (var i = 0; i < c.fest.length; i++) if (has(c.fest[i])) w *= 4;
    if (slowNet() && p.o) w *= 2;          // 网速慢时，已经在本地的图优先
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
    var key = c.key + (offline ? '|off' : '') + (slowNet() ? '|slow' : '');
    if (!queue.length || key !== sceneKey) {
      // 加权随机排序（权重越高越可能排在前面），最近出现过的图先不放进来
      sceneKey = key;
      var keep = Math.min(120, Math.floor(photos.length / 2));
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
    if (recent.length > Math.min(120, Math.floor(photos.length / 2))) recent.shift();
    return next;
  }

  // 网速慢：加载一张图花了很久，之后半小时改用小图，并更多地挑已经存在本地的离线图包里的图
  var slowUntil = 0;
  function slowNet() { return Date.now() < slowUntil; }
  // 离线图包里只存了 1280 像素的版本，断网和网速慢时都用它
  function srcOf(p) { return hiDPI && p.l && !offlineMode() && !slowNet() ? p.l : p.m; }

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

  // 加载图片并搭好一页（不显示）。网络卡住时 onload / onerror 可能永远不来，所以加超时。
  function loadSlide(p, cb) {
    var img = new Image();
    var done = false, t0 = Date.now(), timer = null;
    function fail() {
      if (done) return;
      done = true;
      clearTimeout(timer);
      img.onload = img.onerror = null;
      p._failAt = Date.now();
      failStreak++;
      slowUntil = Date.now() + 30 * 60000;
      cb(null);
    }
    function ok() {
      if (done) return;
      done = true;
      clearTimeout(timer);
      if (Date.now() - t0 > 12000) slowUntil = Date.now() + 30 * 60000;
      var s = buildSlide(p);
      s._fullEl.style.backgroundImage = 'url("' + img.src + '")';
      cb(s);
    }
    img.onload = function () { failStreak = 0; ok(); };
    img.onerror = fail;
    timer = setTimeout(fail, 60000);
    img.src = srcOf(p);
  }

  function prepared(s) { applyAtmos(s); layoutSlide(s); }

  // 提前准备下一页，铺在当前页下面（被当前页完全挡住）
  function prepare() {
    if (nxt || preparing || photos.length < 2) return;
    preparing = true;
    loadSlide(nextPhoto(), function (s) {
      preparing = false;
      if (!s) { setTimeout(prepare, Math.min(60000, 3000 * (failStreak + 1))); return; }
      toggleClass(s, 'ui-off', true);
      stage.insertBefore(s, cur || null);
      prepared(s);
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
    resetFx(old);

    toggleClass(old, 'boot', false);
    toggleClass(old, 'ui-off', true);                                    // ① 旧日历淡出
    setTimeout(function () { toggleClass(old, 'out', true); }, UI_OUT);  // ② 旧照片淡出
    setTimeout(function () { toggleClass(s, 'ui-off', false); }, UI_OUT + UI_IN_AT); // ③ 新日历浮现
    setTimeout(function () { sweep(s); }, UI_OUT + UI_IN_AT + 1500);     // ④ 一道光从卡片上掠过
    setTimeout(function () {
      release(old);
      busy = false;
      schedule();
      setTimeout(prepare, 2500);                                         // 马上开始准备下一页：网慢时有整整五分钟可以慢慢下载
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
    // 先用 160 像素的模糊小图（几 KB）把画面铺出来，大图好了再淡入：网慢时一打开不是黑屏
    var placeholder = null;
    if (p.b && !(cur)) {
      placeholder = el('div', 'boot-ph', stage);
      placeholder.style.backgroundImage = 'url("' + p.b + '")';
    }
    loadSlide(p, function (s) {
      if (!s) { setTimeout(function () { boot(nextPhoto()); }, 2000); return; }
      toggleClass(s, 'boot', true);
      // 开场淡入结束后移除动画，否则 animation 的 fill 会锁住 opacity，之后无法淡出
      setTimeout(function () { toggleClass(s, 'boot', false); if (placeholder && placeholder.parentNode) placeholder.parentNode.removeChild(placeholder); }, 2000);
      stage.appendChild(s);
      prepared(s);
      startKenBurns(s);
      cur = s;
      shownAt = Date.now();
      schedule();
      setTimeout(prepare, 2500);
      setTimeout(function () { sweep(s); }, 2600);
    });
  }

  // ------------------------------------------------------------ 光的动效：定时掠过的高光
  function sweep(s) {
    if (!DYN || !s || s !== cur) return;
    toggleClass(s, 'sweeping', true);
    setTimeout(function () { toggleClass(s, 'sweeping', false); }, 3200);
  }
  (function loop() {
    setTimeout(function () { sweep(cur); loop(); }, 38000 + Math.random() * 30000);
  })();

  // ------------------------------------------------------------ 月份切换
  var monthTimer = null;
  var toastEl = $('toast'), toastTimer = null;

  function toast(text) {
    toastEl.innerHTML = text;
    toggleClass(toastEl, 'on', true);
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { toggleClass(toastEl, 'on', false); }, 1600);
  }

  // dir：1 下个月（旧的一页向上翻走）、-1 上个月（新的一页从上面落下）；不给就直接换
  function refreshSlide(s, dir) {
    if (!s) return;
    var vm = viewMonth(), now = new Date(), i;
    var cs = s.querySelectorAll('.b-clock');
    for (i = 0; i < cs.length; i++) cs[i].innerHTML = clockHTML(now, s._t.style);
    var pages = s.querySelectorAll('.mpage');
    for (i = 0; i < pages.length; i++) {
      var pg = pages[i];
      if (dir && DYN && pg.offsetWidth) flipPage(pg, monthHTML(vm.getFullYear(), vm.getMonth(), now), dir);
      else pg.innerHTML = monthHTML(vm.getFullYear(), vm.getMonth(), now);
    }
    var seal = s.querySelector('.seal');
    if (seal) seal.innerHTML = NUM_CN[vm.getMonth()].split('').join('<br>') + '<br>月';
    paint(s);
    layoutSlide(s);
  }

  // 台历翻页：旧的一页以上边为轴向上翻起、变暗、消失，露出下一个月（往回翻则相反）
  function flipPage(pg, html, dir) {
    var host = pg.parentNode;
    var stale = host.querySelectorAll('.mold');
    for (var i = 0; i < stale.length; i++) host.removeChild(stale[i]);
    var old = pg.cloneNode(true);
    old.className = 'mpage mold ' + (dir > 0 ? 'fl-out' : 'fl-under');
    old.style.width = pg.offsetWidth + 'px';
    pg.innerHTML = html;
    el('div', 'm-fold', old);
    host.insertBefore(old, pg);
    pg.className = dir > 0 ? 'mpage fl-reveal' : 'mpage fl-in';
    setTimeout(function () { if (old.parentNode) old.parentNode.removeChild(old); pg.className = 'mpage'; }, 800);
  }

  function renderMonths(dir) {
    refreshSlide(cur, dir);
    refreshSlide(nxt);
  }

  function shiftMonth(delta) {
    monthOffset = delta === 0 ? 0 : monthOffset + delta;
    renderMonths(delta);
    var vm = viewMonth();
    toast(monthOffset === 0 ? '本月' : vm.getFullYear() + ' 年 ' + (vm.getMonth() + 1) + ' 月');
    clearTimeout(monthTimer);
    if (monthOffset !== 0) {
      monthTimer = setTimeout(function () { shiftMonth(0); }, (cfg.returnToMonthSeconds || 60) * 1000);
    }
  }

  // ------------------------------------------------------------ 手势与触摸时的光影
  //   轻点：换下一张　左右滑：翻月份　按住不动：隐去日历，看画面和说明　按住拖动：卡片跟着手指微微倾斜、光斑跟随手指
  var sx = 0, sy = 0, st = 0, pressing = false, moved = false, peeked = false, holdTimer = null, px = 0, py = 0;
  var fxQueued = false;

  function cards(s) { return s ? s.querySelectorAll('.tilt') : []; }

  function fxFrame() {
    fxQueued = false;
    if (!cur) return;
    var nx = (px - W / 2) / (W / 2), ny = (py - H / 2) / (H / 2);
    // 远景、画作、卡片三层的位移不同，错开的量就是景深
    setTransform(cur._bgEl, 'translate3d(' + n2(-nx * W * 0.007) + 'px,' + n2(-ny * H * 0.007) + 'px,0) scale(1.03)');
    if (cur._artPlane) setTransform(cur._artPlane, 'translate3d(' + n2(-nx * W * 0.003) + 'px,' + n2(-ny * H * 0.003) + 'px,0)');
    setTransform(cur._ui, 'translate3d(' + n2(nx * W * 0.0025) + 'px,' + n2(ny * H * 0.0025) + 'px,0)');
    var style = cur._t.style, flat = style === 'magazine' || style === 'band' || style === 'minimal' || style === 'poster';
    var tl = cards(cur), i;
    for (i = 0; i < tl.length; i++) {
      var c = tl[i].firstChild;
      if (!c || !c.offsetWidth) continue;
      var cx = cur._ui.offsetLeft + c.offsetLeft + c.offsetWidth / 2, cy = cur._ui.offsetTop + c.offsetTop + c.offsetHeight / 2;
      var dx = Math.max(-1, Math.min(1, (px - cx) / (W * 0.5))), dy = Math.max(-1, Math.min(1, (py - cy) / (H * 0.5)));
      if (flat) dx = dy = 0;
      setTransform(tl[i], 'perspective(70rem) rotateX(' + n2(-dy * 2.6) + 'deg) rotateY(' + n2(dx * 2.6) + 'deg)');
      // 光斑：手指在卡片范围内时，在手指下面亮起一团光
      var sp = c.querySelector('.m-spot');
      if (sp) {
        var lx = px - (cx - c.offsetWidth / 2), ly = py - (cy - c.offsetHeight / 2);
        var inside = lx >= 0 && ly >= 0 && lx <= c.offsetWidth && ly <= c.offsetHeight;
        setTransform(sp, 'translate3d(' + n2(lx) + 'px,' + n2(ly) + 'px,0)');
        sp.style.opacity = inside && pressing ? 1 : 0;
      }
    }
    var sh = cur._artEl && cur._artEl.querySelector('.art-shine');
    if (sh) {
      var r = sh.parentNode.getBoundingClientRect();
      var ix = px - r.left, iy = py - r.top;
      setTransform(sh, 'translate3d(' + n2(ix) + 'px,' + n2(iy) + 'px,0)');
      sh.style.opacity = pressing && ix >= 0 && iy >= 0 && ix <= r.width && iy <= r.height ? 1 : 0;
    }
  }
  function queueFx() { if (!DYN || fxQueued) return; fxQueued = true; raf(fxFrame); }

  function resetFx(s) {
    if (!s || !DYN) return;
    var all = [s._bgEl, s._ui, s._artPlane], i;
    for (i = 0; i < all.length; i++) if (all[i]) { setTransition(all[i], ''); setTransform(all[i], all[i] === s._bgEl ? 'scale(1.03)' : ''); }
    var tl = cards(s);
    for (i = 0; i < tl.length; i++) setTransform(tl[i], '');
    var sp = s.querySelectorAll('.m-spot');
    for (i = 0; i < sp.length; i++) sp[i].style.opacity = 0;
    var sh = s.querySelectorAll('.art-shine');
    for (i = 0; i < sh.length; i++) sh[i].style.opacity = 0;
  }

  function pointerDown(x, y) {
    sx = px = x; sy = py = y; st = Date.now();
    pressing = true; moved = false; peeked = false;
    clearTimeout(holdTimer);
    holdTimer = setTimeout(function () {
      if (!pressing || moved || !cur) return;
      peeked = true;
      toggleClass(cur, 'peek', true);
    }, 650);
    if (DYN && cur) {
      var all = [cur._bgEl, cur._ui, cur._artPlane], i;
      for (i = 0; i < all.length; i++) if (all[i]) setTransition(all[i], 'none');
      var tl = cards(cur);
      for (i = 0; i < tl.length; i++) setTransition(tl[i], 'none');
      queueFx();
    }
  }
  function pointerMove(x, y) {
    px = x; py = y;
    if (pressing && (Math.abs(x - sx) > 10 || Math.abs(y - sy) > 10)) {
      moved = true;
      clearTimeout(holdTimer);
    }
    queueFx();
  }
  function pointerUp(x, y, cancel) {
    var dx = x - sx, dy = y - sy;
    clearTimeout(holdTimer);
    var wasPeek = peeked;
    pressing = false;
    if (cur) toggleClass(cur, 'peek', false);
    if (DYN && cur) {
      // 松手后慢慢回位
      var all = [cur._bgEl, cur._ui, cur._artPlane], i;
      for (i = 0; i < all.length; i++) if (all[i]) setTransition(all[i], '-webkit-transform .9s cubic-bezier(.2,.8,.2,1), transform .9s cubic-bezier(.2,.8,.2,1)');
      var tl = cards(cur);
      for (i = 0; i < tl.length; i++) setTransition(tl[i], '-webkit-transform .9s cubic-bezier(.2,.8,.2,1), transform .9s cubic-bezier(.2,.8,.2,1)');
      px = W / 2; py = H / 2;
      queueFx();
    }
    if (cancel || wasPeek) return;
    if (Math.abs(dx) > 50 && Math.abs(dx) > Math.abs(dy)) {
      shiftMonth(dx < 0 ? 1 : -1);           // 左滑看下个月
    } else if (Math.abs(dx) < 12 && Math.abs(dy) < 12 && Date.now() - st < 600) {
      advance();                             // 轻点换下一张
    }
  }

  document.addEventListener('touchmove', function (e) { e.preventDefault(); }, false);
  document.addEventListener('touchstart', function (e) {
    var t = e.touches[0];
    pointerDown(t.clientX, t.clientY);
  }, false);
  document.addEventListener('touchmove', function (e) {
    var t = e.touches[0];
    pointerMove(t.clientX, t.clientY);
  }, false);
  document.addEventListener('touchend', function (e) {
    var t = e.changedTouches[0];
    pointerUp(t.clientX, t.clientY, false);
  }, false);
  document.addEventListener('touchcancel', function (e) {
    var t = e.changedTouches[0];
    pointerUp(t.clientX, t.clientY, true);
  }, false);
  // 电脑浏览器：鼠标悬停时卡片和光斑跟着走，按住不动看画面，点击换下一张
  var hasTouch = 'ontouchstart' in window;
  if (!hasTouch) {
    document.addEventListener('mousedown', function (e) { pointerDown(e.clientX, e.clientY); }, false);
    document.addEventListener('mousemove', function (e) { pointerMove(e.clientX, e.clientY); }, false);
    document.addEventListener('mouseup', function (e) { pointerUp(e.clientX, e.clientY, false); }, false);
  }
  document.addEventListener('keydown', function (e) {
    if (e.keyCode === 37) shiftMonth(-1);
    if (e.keyCode === 39) shiftMonth(1);
    if (e.keyCode === 32 || e.keyCode === 13) advance();
  }, false);

  // ------------------------------------------------------------ 时钟 / 跨天 / 夜间
  var lastMin = -1, lastDay = new Date();

  // 换分钟：只有变了的那一位往上滚一格
  function rollTime(s, now) {
    var digits = timeDigits(now);
    var times = s.querySelectorAll('.time'), i, j;
    for (i = 0; i < times.length; i++) {
      var cells = times[i].querySelectorAll('.dg');
      for (j = 0; j < cells.length && j < digits.length; j++) {
        var cell = cells[j], oldv = cell.firstChild ? cell.firstChild.textContent : '';
        if (oldv === digits[j]) continue;
        if (DYN && s === cur) {
          cell.innerHTML = '<i class="out">' + oldv + '</i><i class="in">' + digits[j] + '</i>';
          (function (c, v) { setTimeout(function () { c.innerHTML = '<i>' + v + '</i>'; }, 700); })(cell, digits[j]);
        } else {
          cell.innerHTML = '<i>' + digits[j] + '</i>';
        }
      }
    }
  }

  function tick() {
    var now = new Date();
    if (now.getMinutes() !== lastMin) {
      var first = lastMin < 0;
      lastMin = now.getMinutes();
      if (cur) {
        var first1 = cur.querySelector('.time .dg');
        var count = cur.querySelectorAll('.time')[0] ? cur.querySelectorAll('.time')[0].querySelectorAll('.dg').length : 0;
        if (first || !first1 || !sameDay(now, lastDay) || count !== timeDigits(now).length) {
          var cs = cur.querySelectorAll('.b-clock');
          for (var i = 0; i < cs.length; i++) cs[i].innerHTML = clockHTML(now, cur._t.style);
        } else {
          rollTime(cur, now);
        }
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
    // 新版本下载完：换上并刷新页面。正在换图时等一会儿再试，不能错过
    function applyUpdate() {
      try { ac.swapCache(); } catch (e) { /* 忽略 */ }
      (function go() { if (busy) setTimeout(go, 3000); else location.reload(); })();
    }
    ac.addEventListener('updateready', applyUpdate, false);
    // 主屏幕 App 常年不重启：每小时、以及每次回到前台时主动问一次有没有新版本
    function check() { try { if (navigator.onLine !== false) ac.update(); } catch (e) { /* 忽略 */ } }
    setInterval(check, 3600000);
    document.addEventListener('visibilitychange', function () { if (!document.hidden) check(); }, false);
    window.addEventListener('pageshow', check, false);
    if (ac.status === ac.UPDATEREADY) applyUpdate();
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
  function relayout() {
    measure();
    if (cur) layoutSlide(cur);
    if (nxt) layoutSlide(nxt);
  }
  window.addEventListener('resize', function () {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(relayout, 80);
  }, false);
  window.addEventListener('orientationchange', function () { setTimeout(relayout, 400); }, false);

  measure();
  toggleClass(document.body, 'lite', LITE);
  toggleClass(document.body, 'dyn', DYN);
  if (APP) toggleClass(document.body, 'app-' + APP, true);
  function start() {
    // ?photo=3 打开指定图片（调试用）
    var m = /[?&]photo=(\d+)/.exec(location.search);
    boot(m ? photos[+m[1] % photos.length] : nextPhoto());
  }
  if (!photos.length) {
    // 图库没加载出来（离线缓存更新到一半等）：绕过缓存再取一次，不行才提示
    toggleClass(document.body, 'empty', true);
    var rs = document.createElement('script');
    rs.src = 'data/photos.js?r=' + Date.now();
    rs.onload = function () {
      photos = (window.PHOTOS || []).slice();
      if (photos.length) { toggleClass(document.body, 'empty', false); start(); }
    };
    document.getElementsByTagName('head')[0].appendChild(rs);
  } else {
    start();
  }
  tick();
})();
