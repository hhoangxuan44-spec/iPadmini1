#!/usr/bin/env python3
# 从 js/lunar.js、js/mottos.js 生成 core/Lunar.java 与 core/Mottos.java
# 用法：python3 android/tools/gen_core.py
import os, re

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
OUT = os.path.join(ROOT, 'android/app/src/main/java/io/github/guangying/core')


def read(p):
    with open(os.path.join(ROOT, p), encoding='utf-8') as f:
        return f.read()


def jstr(s):
    # Java 字符串字面量转义（中文直接输出，源文件为 UTF-8）
    return '"' + s.replace('\\', '\\\\').replace('"', '\\"') + '"'


def js_unquote(s):
    return s.replace("\\'", "'").replace('\\\\', '\\')


lunar_js = read('js/lunar.js')

# 农历表
m = re.search(r'var LUNAR_INFO = \[(.*?)\];', lunar_js, re.S)
info_vals = re.findall(r'0x[0-9a-fA-F]+', m.group(1))
assert len(info_vals) == 201, len(info_vals)
info_rows = []
for i in range(0, len(info_vals), 8):
    info_rows.append('        ' + ', '.join(info_vals[i:i + 8]))
info_src = ',\n'.join(info_rows)

# 节气表
m = re.search(r'var TERM_TABLE = \[(.*?)\];', lunar_js, re.S)
terms = re.findall(r"'([0-9a-v]{24})'", m.group(1))
assert len(terms) == 99, len(terms)
term_rows = []
for i in range(0, len(terms), 3):
    term_rows.append('        ' + ', '.join(jstr(t) for t in terms[i:i + 3]))
term_src = ',\n'.join(term_rows)


def pairs(name):
    m = re.search(r'var %s = \{(.*?)\};' % name, lunar_js, re.S)
    return re.findall(r"'(\d+-\d+)': '([^']*)'", m.group(1))


def put_lines(name, ps):
    return '\n'.join('        %s.put(%s, %s);' % (name, jstr(k), jstr(v)) for k, v in ps)


solar_f = pairs('SOLAR_FESTIVALS')
lunar_f = pairs('LUNAR_FESTIVALS')
m = re.search(r'var TERM_NAMES = \[(.*?)\];', lunar_js, re.S)
term_names = ', '.join(jstr(x) for x in re.findall(r"'([^']*)'", m.group(1)))

LUNAR = '''// 本文件由 android/tools/gen_core.py 生成，请勿手改；改 js/lunar.js 后重新生成。
package io.github.guangying.core;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 农历 / 节气 / 节日（与 js/lunar.js 逐日一致）。农历 1900-2100，节气 2001-2099。 */
public final class Lunar {
    private Lunar() {}

    /** 农历日期。 */
    public static final class Day {
        public final int year, month, day;
        public final boolean isLeap;
        public final String monthName, dayName, ganzhi, animal;
        final int monthDays;

        Day(int year, int month, int day, boolean isLeap, int monthDays,
            String monthName, String dayName, String ganzhi, String animal) {
            this.year = year; this.month = month; this.day = day;
            this.isLeap = isLeap; this.monthDays = monthDays;
            this.monthName = monthName; this.dayName = dayName;
            this.ganzhi = ganzhi; this.animal = animal;
        }
    }

    /** 某一天的汇总信息。term 无节气时为空串（与 JS 一致，非 null）。 */
    public static final class Info {
        public final Day lunar;
        public final List<String> festivals;
        public final String term;

        Info(Day lunar, List<String> festivals, String term) {
            this.lunar = lunar; this.festivals = festivals; this.term = term;
        }
    }

    // 低 4 位 = 闰月月份；0x10000 位 = 闰月为大月；0x8000 >> (m-1) 位 = 第 m 月为大月
    private static final int[] LUNAR_INFO = {
@@INFO@@
    };

    private static final String GAN = "甲乙丙丁戊己庚辛壬癸";
    private static final String ZHI = "子丑寅卯辰巳午未申酉戌亥";
    private static final String ANIMAL = "鼠牛虎兔龙蛇马羊猴鸡狗猪";
    private static final String[] MONTH_CN = {"正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊"};
    private static final String[] DAY_TEN = {"初", "十", "廿", "三"};
    private static final String[] NUM_CN = {"", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十"};

    private static final String[] TERM_NAMES = {@@TERMNAMES@@};

    // 每年 24 个字符依次对应 小寒…冬至 所在日期，32 进制
    private static final String[] TERM_TABLE = {
@@TERMS@@
    };

    private static final Map<String, String> SOLAR_FESTIVALS = new HashMap<>();
    private static final Map<String, String> LUNAR_FESTIVALS = new HashMap<>();
    static {
@@SOLARF@@
@@LUNARF@@
    }

    private static int leapMonth(int y) { return LUNAR_INFO[y - 1900] & 0xf; }

    private static int leapDays(int y) {
        if (leapMonth(y) == 0) return 0;
        return (LUNAR_INFO[y - 1900] & 0x10000) != 0 ? 30 : 29;
    }

    private static int monthDays(int y, int m) {
        return (LUNAR_INFO[y - 1900] & (0x10000 >> m)) != 0 ? 30 : 29;
    }

    private static int yearDays(int y) {
        int sum = 348;
        for (int i = 0x8000; i > 0x8; i >>= 1) {
            if ((LUNAR_INFO[y - 1900] & i) != 0) sum += 1;
        }
        return sum + leapDays(y);
    }

    private static String dayName(int d) {
        if (d == 10) return "初十";
        if (d == 20) return "二十";
        if (d == 30) return "三十";
        return DAY_TEN[d / 10] + NUM_CN[d % 10];
    }

    /** 公历 -> 农历（对应 JS Lunar.toLunar）。 */
    public static Day toLunar(int y, int m, int d) {
        long offset = LocalDate.of(y, m, d).toEpochDay() - LocalDate.of(1900, 1, 31).toEpochDay();
        int ly, temp = 0;
        for (ly = 1900; ly < 2101 && offset > 0; ly++) {
            temp = yearDays(ly);
            offset -= temp;
        }
        if (offset < 0) { offset += temp; ly--; }

        int leap = leapMonth(ly), lm;
        boolean isLeap = false;
        for (lm = 1; lm < 13 && offset > 0; lm++) {
            if (leap > 0 && lm == leap + 1 && !isLeap) {
                --lm; isLeap = true; temp = leapDays(ly);
            } else {
                temp = monthDays(ly, lm);
            }
            if (isLeap && lm == leap + 1) isLeap = false;
            offset -= temp;
        }
        if (offset == 0 && leap > 0 && lm == leap + 1) {
            if (isLeap) { isLeap = false; } else { isLeap = true; --lm; }
        }
        if (offset < 0) { offset += temp; --lm; }
        int ld = (int) offset + 1;

        return new Day(ly, lm, ld, isLeap, isLeap ? leapDays(ly) : monthDays(ly, lm),
                (isLeap ? "闰" : "") + MONTH_CN[lm - 1] + "月", dayName(ld),
                "" + GAN.charAt((ly - 4) % 10) + ZHI.charAt((ly - 4) % 12),
                "" + ANIMAL.charAt((ly - 4) % 12));
    }

    private static int termDay(int y, int n) {
        return Character.digit(TERM_TABLE[y - 2001].charAt(n), 32);
    }

    /** 节气名，无则空串（对应 JS Lunar.solarTerm）。 */
    public static String solarTerm(int y, int m, int d) {
        if (y < 2001 || y > 2099) return "";
        int n1 = (m - 1) * 2, n2 = n1 + 1;
        if (termDay(y, n1) == d) return TERM_NAMES[n1];
        if (termDay(y, n2) == d) return TERM_NAMES[n2];
        return "";
    }

    /** 汇总某一天的信息（对应 JS Lunar.info(new Date(y, m-1, d))）。 */
    public static Info info(int year, int month1to12, int dayOfMonth) {
        int y = year, m = month1to12, d = dayOfMonth;
        Day l = toLunar(y, m, d);
        List<String> fest = new ArrayList<>();
        String s = SOLAR_FESTIVALS.get(m + "-" + d);
        if (s != null) fest.add(s);
        String lf = LUNAR_FESTIVALS.get(l.month + "-" + l.day);
        if (!l.isLeap && lf != null) fest.add(lf);
        if (!l.isLeap && l.month == 12 && l.day == l.monthDays) fest.add("除夕");
        // 母亲节：5 月第二个周日；父亲节：6 月第三个周日；感恩节：11 月第四个周四
        int wd = LocalDate.of(y, m, d).getDayOfWeek().getValue() % 7; // 周日 = 0，同 JS getDay
        int nth = (d - 1) / 7 + 1;
        if (m == 5 && wd == 0 && nth == 2) fest.add("母亲节");
        if (m == 6 && wd == 0 && nth == 3) fest.add("父亲节");
        if (m == 11 && wd == 4 && nth == 4) fest.add("感恩节");
        String term = solarTerm(y, m, d);
        if (!term.isEmpty()) fest.add(term);
        return new Info(l, Collections.unmodifiableList(fest), term);
    }
}
'''
LUNAR = (LUNAR.replace('@@INFO@@', info_src).replace('@@TERMNAMES@@', term_names)
         .replace('@@TERMS@@', term_src)
         .replace('@@SOLARF@@', put_lines('SOLAR_FESTIVALS', solar_f))
         .replace('@@LUNARF@@', put_lines('LUNAR_FESTIVALS', lunar_f)))

# 每日一言
mottos_js = read('js/mottos.js')
rows = re.findall(r"^\s*\['((?:[^'\\]|\\.)*)',\s*'((?:[^'\\]|\\.)*)'\],?\s*$", mottos_js, re.M)
assert len(rows) == len(re.findall(r"^\s*\['", mottos_js, re.M)) and rows
motto_src = ',\n'.join('        {%s, %s}' % (jstr(js_unquote(a)), jstr(js_unquote(b))) for a, b in rows)

MOTTOS = '''// 本文件由 android/tools/gen_core.py 生成，请勿手改；改 js/mottos.js 后重新生成。
package io.github.guangying.core;

import java.time.LocalDate;

/** 每日一言：固定种子洗牌后按日期取一句（与 js/wall.js 的 mottoOrder 一致）。 */
public final class Mottos {
    private Mottos() {}

    /** {原文, 出处} */
    public static final String[][] ALL = {
@@ROWS@@
    };

    private static final int[] ORDER = buildOrder(ALL.length);

    // 种子 7 的 LCG 洗牌，与 JS 完全相同
    private static int[] buildOrder(int n) {
        int[] idx = new int[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        int seed = 7;
        for (int j = n - 1; j > 0; j--) {
            seed = (seed * 9301 + 49297) % 233280;
            int k = (int) Math.floor((double) seed / 233280 * (j + 1));
            int t = idx[j]; idx[j] = idx[k]; idx[k] = t;
        }
        return idx;
    }

    /** 返回 {text, source}；日期按本地公历日，与 JS 的 UTC 天数一致。 */
    public static String[] forDay(int year, int month1to12, int dayOfMonth) {
        long day = LocalDate.of(year, month1to12, dayOfMonth).toEpochDay();
        int n = ALL.length;
        String[] m = ALL[ORDER[(int) (((day % n) + n) % n)]];
        return new String[] {m[0], m[1]};
    }
}
'''.replace('@@ROWS@@', motto_src)

for name, src in (('Lunar.java', LUNAR), ('Mottos.java', MOTTOS)):
    with open(os.path.join(OUT, name), 'w', encoding='utf-8', newline='\n') as f:
        f.write(src)
    print('wrote', name)
