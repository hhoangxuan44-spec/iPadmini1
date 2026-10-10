/*
 * 农历 / 节气 / 节日（ES5，兼容 iOS 9 Safari）
 * 农历数据覆盖 1900-2100，节气数据覆盖 2001-2099。
 */
(function (global) {
  'use strict';

  // 每个值：低 4 位 = 闰月月份（0 表示无闰月）；0x10000 位 = 闰月为大月；
  // 0x8000 >> (m-1) 位 = 第 m 月为大月（30 天）。
  var LUNAR_INFO = [
    0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2,
    0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977,
    0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970,
    0x06566, 0x0d4a0, 0x0ea50, 0x16a95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950,
    0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557,
    0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573, 0x052b0, 0x0a9a8, 0x0e950, 0x06aa0,
    0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0,
    0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6,
    0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570,
    0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x05ac0, 0x0ab60, 0x096d5, 0x092e0,
    0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5,
    0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930,
    0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530,
    0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45,
    0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0,
    0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x1a6c4, 0x0aae0,
    0x092e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4,
    0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0,
    0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160,
    0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252,
    0x0d520
  ];

  var GAN = '甲乙丙丁戊己庚辛壬癸';
  var ZHI = '子丑寅卯辰巳午未申酉戌亥';
  var ANIMAL = '鼠牛虎兔龙蛇马羊猴鸡狗猪';
  var MONTH_CN = ['正', '二', '三', '四', '五', '六', '七', '八', '九', '十', '冬', '腊'];
  var DAY_TEN = ['初', '十', '廿', '三'];
  var NUM_CN = ['', '一', '二', '三', '四', '五', '六', '七', '八', '九', '十'];

  function leapMonth(y) { return LUNAR_INFO[y - 1900] & 0xf; }
  function leapDays(y) {
    if (!leapMonth(y)) return 0;
    return (LUNAR_INFO[y - 1900] & 0x10000) ? 30 : 29;
  }
  function monthDays(y, m) { return (LUNAR_INFO[y - 1900] & (0x10000 >> m)) ? 30 : 29; }
  function yearDays(y) {
    var sum = 348, i;
    for (i = 0x8000; i > 0x8; i >>= 1) {
      if (LUNAR_INFO[y - 1900] & i) sum += 1;
    }
    return sum + leapDays(y);
  }

  function dayName(d) {
    if (d === 10) return '初十';
    if (d === 20) return '二十';
    if (d === 30) return '三十';
    return DAY_TEN[Math.floor(d / 10)] + NUM_CN[d % 10];
  }

  // 公历 -> 农历
  function toLunar(y, m, d) {
    var offset = Math.round((Date.UTC(y, m - 1, d) - Date.UTC(1900, 0, 31)) / 86400000);
    var ly, temp = 0;
    for (ly = 1900; ly < 2101 && offset > 0; ly++) {
      temp = yearDays(ly);
      offset -= temp;
    }
    if (offset < 0) { offset += temp; ly--; }

    var leap = leapMonth(ly), isLeap = false, lm;
    for (lm = 1; lm < 13 && offset > 0; lm++) {
      if (leap > 0 && lm === leap + 1 && !isLeap) {
        --lm; isLeap = true; temp = leapDays(ly);
      } else {
        temp = monthDays(ly, lm);
      }
      if (isLeap && lm === leap + 1) isLeap = false;
      offset -= temp;
    }
    if (offset === 0 && leap > 0 && lm === leap + 1) {
      if (isLeap) { isLeap = false; } else { isLeap = true; --lm; }
    }
    if (offset < 0) { offset += temp; --lm; }
    var ld = offset + 1;

    return {
      year: ly,
      month: lm,
      day: ld,
      isLeap: isLeap,
      monthDays: isLeap ? leapDays(ly) : monthDays(ly, lm),
      ganzhi: GAN.charAt((ly - 4) % 10) + ZHI.charAt((ly - 4) % 12),
      animal: ANIMAL.charAt((ly - 4) % 12),
      monthName: (isLeap ? '闰' : '') + MONTH_CN[lm - 1] + '月',
      dayName: dayName(ld)
    };
  }

  // 二十四节气（2001-2099，按北京时间由天文算法预先算出）
  // 每年 24 个字符依次对应 小寒…冬至 所在日期，字符为 32 进制的日。
  var TERM_NAMES = ['小寒', '大寒', '立春', '雨水', '惊蛰', '春分', '清明', '谷雨',
    '立夏', '小满', '芒种', '夏至', '小暑', '大暑', '立秋', '处暑',
    '白露', '秋分', '寒露', '霜降', '立冬', '小雪', '大雪', '冬至'];
  var TERM_TABLE = [
    '5k4i5k5k5l5l7n7n7n8n7m7m', '5k4j6l5k6l6l7n8n8n8n7m7m', '6k4j6l5k6l6m7n8n8n9o8n7m', '6l4j5k4k5l5l7m7n7n8n7m7l', '5k4i5k5k5l5l7n7n7n8n7m7m',
    '5k4j6l5k5l6l7n7n8n8n7m7m', '6k4j6l5k6l6m7n8n8n9o8n7m', '6l4j5k4k5k5l7m7n7m8n7m7l', '5k4i5k4k5l5l7n7n7n8n7m7m', '5k4j6l5k5l6l7n7n8n8n7m7m',
    '6k4j6l5k6l6m7n8n8n8o8n7m', '6l4j5k4k5k5l7m7n7m8n7m7l', '5k4i5k4k5l5l7m7n7n8n7m7m', '5k4j5l5k5l6l7n7n8n8n7m7m', '6k4j6l5k6l6m7n8n8n8o8m7m',
    '6k4j5k4j5k5l6m7n7m8n7m7l', '5k3i5k4k5l5l7m7n7n8n7m7m', '5k4j5l5k5l6l7n7n8n8n7m7m', '5k4j6l5k6l6l7n8n8n8o8m7m', '6k4j5k4j5k5l6m7m7m8n7m6l',
    '5k3i5k4k5l5l7m7n7n8n7m7l', '5k4j5k5k5l6l7n7n7n8n7m7m', '5k4j6l5k6l6l7n8n8n8o8m7m', '6k4j5k4j5k5l6m7m7m8n7m6l', '5k3i5k4k5l5l7m7n7n8n7m7l',
    '5k4i5k5k5l5l7n7n7n8n7m7m', '5k4j6l5k6l6l7n8n8n8n7m7m', '6k4j5k4j5k5l6m7m7m8n7m6l', '5k3i5k4k5l5l7m7n7n8n7m7l', '5k4i5k5k5l5l7n7n7n8n7m7m',
    '5k4j6l5k6l6l7n8n8n8n7m7m', '6k4j5k4j5k5l6m7m7m8n7m6l', '5k3i5k4k5l5l7m7n7n8n7m7l', '5k4i5k5k5l5l7n7n7n8n7m7m', '5k4j6l5k5l6l7n7n8n8n7m7m',
    '6k4j5k4j5k5l6m7m7m8n7m6l', '5k3i5k4k5l5l7m7n7m8n7m7l', '5k4i5k5k5l5l7n7n7n8n7m7m', '5k4j6l5k5l6l7n7n8n8n7m7m', '6k4j5k4j5k5l6m7m7m7n7m6l',
    '5k3i5k4k5k5l7m7n7m8n7m7l', '5k4i5k4k5l5l7m7n7n8n7m7m', '5k4j6l5k5l6l7n7n8n8n7m7m', '6k4j5k4j5k5l6m7m7m7n7m6l', '5k3i5k4j5k5l7m7n7m8n7m7l',
    '5k4i5k4k5l5l7m7n7n8n7m7m', '5k4j6l5k5l6l7n7n8n8n7m7m', '6k4j5k4j5k5k6m7m7m7n7l6l', '5j3i5k4j5k5l6m7m7m8n7m7l', '5k3i5k4k5l5l7m7n7n8n7m7m',
    '5k4j5k5k5l6l7n7n7n8n7m7m', '5k4j5k4j5k5k6m7m7m7n7l6l', '5j3i5k4j5k5l6m7m7m8n7m7l', '5k3i5k4k5l5l7m7n7n8n7m7l', '5k4j5k5k5l5l7n7n7n8n7m7m',
    '5k4j5k4j5k5k6m7m7m7n7l6l', '5j3i5k4j5k5l6m7m7m8n7m6l', '5k3i5k4k5l5l7m7n7n8n7m7l', '5k4i5k5k5l5l7n7n7n8n7m7m', '5k4j5k4j5k5k6m7m7m7m6l6l',
    '5j3i5k4j5k5l6m7m7m8n7m6l', '5k3i5k4k5l5l7m7n7n8n7m7l', '5k4i5k5k5l5l7n7n7n8n7m7m', '5k4j5k4j5k5k6m7m7m7m6l6l', '5j3i5k4j5k5l6m7m7m8n7m6l',
    '5k3i5k4k5l5l7m7n7n8n7m7l', '5k4i5k5k5l5l7n7n7n8n7m7m', '5k4j5k4j4k5k6m6m7m7m6l6l', '5j3i5k4j5k5l6m7m7m8n7m6l', '5k3i5k4k5k5l7m7n7m8n7m7l',
    '5k4i5k5k5l5l7n7n7n8n7m7m', '5k4j5k4j4k5k6m6m7m7m6l6l', '5j3i5k4j5k5l6m7m7m7n7m6l', '5k3i5k4k5k5l7m7n7m8n7m7l', '5k4i5k4k5l5l7m7n7n8n7m7m',
    '5k4j5k4j4k5k6m6m7m7m6l6l', '5j3i5k4j5k5l6m7m7m7n7m6l', '5k3i5k4j5k5l6m7n7m8n7m7l', '5k4i5k4k5l5l7m7n7n8n7m7m', '5k4j4k4j4k5k6m6m7m7m6l6l',
    '5j3i5k4j5k5k6m7m7m7n7l6l', '5j3i5k4j5k5l6m7m7m8n7m7l', '5k3i5k4k5l5l7m7n7n8n7m7m', '5k4j4j4j4k4k6m6m6m7m6l6l', '4j3i5k4j5k5k6m7m7m7n7l6l',
    '5j3i5k4j5k5l6m7m7m8n7m7l', '5k3i5k4k5l5l7m7n7n8n7m7m', '5k4j4j4j4k4k6m6m6m7m6l6l', '4j3i5k4j5k5k6m7m7m7m7l6l', '5j3i5k4j5k5l6m7m7m8n7m6l',
    '5k3i5k4k5l5l7m7n7n8n7m7l', '5k4i4j4j4k4k6m6m6m7m6l6l', '4j3i5k4j5k5k6m7m7m7m6l6l', '5j3i5k4j5k5l6m7m7m8n7m6l', '5k3i5k4k5l5l7m7n7n8n7m7l',
    '5k4i4j4j4k4k6m6m6m7m6l6l', '4j3i5k4j4k5k6m6m7m7m6l6l', '5j3i5k4j5k5l6m7m7m8n7m6l', '5k3i5k4k5k5l7m7n7n8n7m7l'
  ];

  function termDay(y, n) {
    return parseInt(TERM_TABLE[y - 2001].charAt(n), 32);
  }

  function solarTerm(y, m, d) {
    if (y < 2001 || y > 2099) return '';
    var n1 = (m - 1) * 2, n2 = n1 + 1;
    if (termDay(y, n1) === d) return TERM_NAMES[n1];
    if (termDay(y, n2) === d) return TERM_NAMES[n2];
    return '';
  }

  var SOLAR_FESTIVALS = {
    '1-1': '元旦', '2-14': '情人节', '3-8': '妇女节', '3-12': '植树节',
    '4-1': '愚人节', '5-1': '劳动节', '5-4': '青年节', '6-1': '儿童节',
    '7-1': '建党节', '8-1': '建军节', '9-10': '教师节', '10-1': '国庆节',
    '12-24': '平安夜', '12-25': '圣诞节'
  };
  var LUNAR_FESTIVALS = {
    '1-1': '春节', '1-15': '元宵节', '2-2': '龙抬头', '5-5': '端午节',
    '7-7': '七夕', '7-15': '中元节', '8-15': '中秋节', '9-9': '重阳节',
    '12-8': '腊八节', '12-23': '小年'
  };

  // 汇总某一天的信息
  function info(date) {
    var y = date.getFullYear(), m = date.getMonth() + 1, d = date.getDate();
    var l = toLunar(y, m, d);
    var fest = [];
    if (SOLAR_FESTIVALS[m + '-' + d]) fest.push(SOLAR_FESTIVALS[m + '-' + d]);
    if (!l.isLeap && LUNAR_FESTIVALS[l.month + '-' + l.day]) fest.push(LUNAR_FESTIVALS[l.month + '-' + l.day]);
    if (!l.isLeap && l.month === 12 && l.day === l.monthDays) fest.push('除夕');
    // 母亲节：5 月第二个周日；父亲节：6 月第三个周日；感恩节：11 月第四个周四
    var wd = date.getDay(), nth = Math.floor((d - 1) / 7) + 1;
    if (m === 5 && wd === 0 && nth === 2) fest.push('母亲节');
    if (m === 6 && wd === 0 && nth === 3) fest.push('父亲节');
    if (m === 11 && wd === 4 && nth === 4) fest.push('感恩节');
    var term = solarTerm(y, m, d);
    if (term) fest.push(term);
    return { lunar: l, festivals: fest, term: term };
  }

  global.Lunar = { toLunar: toLunar, solarTerm: solarTerm, info: info };
})(this);
