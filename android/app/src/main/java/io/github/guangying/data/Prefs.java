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
