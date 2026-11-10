package io.github.guangying.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/** 设置与当前状态。壁纸、桌面组件、主界面都在同一个进程里，共用这份数据。 */
public final class Prefs {
    /** 日历组件放在桌面的哪个位置：壁纸会优先挑这个位置画面安静的图片 */
    public static final String[] POSITIONS = {"tl", "tr", "bl", "br", "l", "r"};
    public static final String[] POSITION_NAMES = {"左上", "右上", "左下", "右下", "左侧", "右侧"};

    /** 主题：界面上的一类对应图库里的一个或几个 kind */
    public static final String[][] THEMES = {
            {"自然", "nature"}, {"动物与天象", "animal", "sky"}, {"城市建筑", "city"},
            {"中国风光", "china"}, {"东方绘画", "east"}, {"西方绘画", "west"}};
    private static final String ALL_KINDS = "nature,animal,sky,city,china,east,west";

    /** 换图模式 */
    public static final String[] MODES = {"scene", "bright", "night", "art"};
    public static final String[] MODE_NAMES = {"应景", "明亮清爽", "静夜", "名画"};
    public static final String[] MODE_HINTS = {
            "跟着季节、时段和节日挑图：冬天多雪景，夜里多星空月色，清晨多日出，中秋多月亮。",
            "多挑色彩明亮通透的照片，少出暗调、灰调和单色的图。",
            "白天和「应景」一样；晚上 7 点到早上 5 点只出星空、月色和夜景。",
            "只看东西方名画（北斋、广重、宋元山水、莫奈、凡·高……），不受上面主题的限制。"};

    private final SharedPreferences sp;

    public Prefs(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences("guangying", Context.MODE_PRIVATE);
    }

    public String position() {
        return sp.getString("pos", "tl");
    }

    public void setPosition(String p) {
        sp.edit().putString("pos", p).apply();
    }

    /** 选中的 kind；默认全部 */
    public java.util.Set<String> kinds() {
        return new java.util.HashSet<>(java.util.Arrays.asList(sp.getString("kinds", ALL_KINDS).split(",")));
    }

    public void setKinds(java.util.Set<String> kinds) {
        sp.edit().putString("kinds", android.text.TextUtils.join(",", kinds)).apply();
    }

    public String mode() {
        return sp.getString("mode", "scene");
    }

    public void setMode(String m) {
        sp.edit().putString("mode", m).apply();
    }

    public int intervalMinutes() {
        return sp.getInt("interval", 5);
    }

    /** 壁纸的缓慢推拉与翻页视差 */
    public boolean motion() {
        return sp.getBoolean("motion", true);
    }

    public void setMotion(boolean on) {
        sp.edit().putBoolean("motion", on).apply();
    }

    /** 兼容模式：用静态壁纸代替动态壁纸（系统里没有动态壁纸入口时） */
    public boolean staticMode() {
        return sp.getBoolean("static", false);
    }

    public void setStaticMode(boolean on) {
        sp.edit().putBoolean("static", on).apply();
    }

    /** 兼容模式上一次换壁纸的结果：什么时候试的、成没成、原因 */
    public void setStaticResult(boolean ok, String note) {
        long now = System.currentTimeMillis();
        SharedPreferences.Editor e = sp.edit().putLong("static_tried", now).putString("static_note", note == null ? "" : note);
        if (ok) e.putLong("static_ok", now);
        e.putBoolean("static_last_ok", ok).apply();
    }

    public long staticTriedAt() {
        return sp.getLong("static_tried", 0);
    }

    public long staticOkAt() {
        return sp.getLong("static_ok", 0);
    }

    public boolean staticLastOk() {
        return sp.getBoolean("static_last_ok", true);
    }

    public String staticNote() {
        return sp.getString("static_note", "");
    }

    public boolean nightDim() {
        return sp.getBoolean("night", true);
    }

    /** 上次同步图库清单的时间 */
    public long lastSync() {
        return sp.getLong("sync", 0);
    }

    public void setLastSync(long t) {
        sp.edit().putLong("sync", t).apply();
    }

    public String currentId() {
        return sp.getString("cur", "");
    }

    public long shownAt() {
        return sp.getLong("shown", 0);
    }

    public void setCurrent(String id, long at) {
        List<String> r = recent();
        r.remove(id);
        r.add(id);
        while (r.size() > 120) r.remove(0);
        sp.edit().putString("cur", id).putLong("shown", at).putString("recent", join(r)).apply();
    }

    public List<String> recent() {
        List<String> out = new ArrayList<>();
        String s = sp.getString("recent", "");
        if (!s.isEmpty()) for (String x : s.split("\\|")) out.add(x);
        return out;
    }

    private static String join(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) sb.append('|');
            sb.append(l.get(i));
        }
        return sb.toString();
    }
}
