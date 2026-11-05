package io.github.guangying;

import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;

import io.github.guangying.data.Prefs;

/**
 * 微光：把「亮度 + 系统极暗」合成一个 0–1000 的刻度。
 *
 *   300–1000（亮）  关闭自适应亮度，按感知曲线映射到 screen_brightness 1–255；极暗关闭
 *   0–300（暗）     screen_brightness 固定为 1，极暗强度从 0 平滑升到 100
 * 两段在 300 处衔接：亮度正好是 1、极暗强度正好是 0，所以拖动时亮度连续变化。
 */
public final class Dimmer {
    private Dimmer() {}

    public static final int MAX = 1000, SPLIT = 300;
    private static final double GAMMA = 2.0;
    private static final String K_ACTIVE = "reduce_bright_colors_activated", K_LEVEL = "reduce_bright_colors_level";
    public static final String SECURE_PERMISSION = "android.permission.WRITE_SECURE_SETTINGS";

    /** 刻度 → 系统亮度 1–255 */
    public static int brightnessFor(int s) {
        if (s <= SPLIT) return 1;
        double t = (Math.min(s, MAX) - SPLIT) / (double) (MAX - SPLIT);
        return 1 + (int) Math.round(254 * Math.pow(t, GAMMA));
    }

    /** 刻度 → 极暗强度 0–100 */
    public static int levelFor(int s) {
        if (s >= SPLIT) return 0;
        return (int) Math.round(100.0 * (SPLIT - Math.max(s, 0)) / SPLIT);
    }

    /** 系统亮度 / 极暗强度 → 刻度（反过来读当前状态） */
    public static int scaleFor(int brightness, boolean dimActive, int level) {
        if (dimActive && level > 0) return (int) Math.round(SPLIT - level * SPLIT / 100.0);
        double t = Math.pow(Math.max(0, Math.min(254, brightness - 1)) / 254.0, 1.0 / GAMMA);
        return SPLIT + (int) Math.round(t * (MAX - SPLIT));
    }

    public static boolean canWriteSettings(Context ctx) {
        return Settings.System.canWrite(ctx);
    }

    public static boolean canSecure(Context ctx) {
        return ctx.checkSelfPermission(SECURE_PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    public static String grantCommand(Context ctx) {
        return "adb shell pm grant " + ctx.getPackageName() + " " + SECURE_PERMISSION;
    }

    /** 读当前刻度（读系统设置不需要权限） */
    public static int read(Context ctx) {
        try {
            int b = Settings.System.getInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128);
            boolean on = Settings.Secure.getInt(ctx.getContentResolver(), K_ACTIVE, 0) == 1;
            int lv = Settings.Secure.getInt(ctx.getContentResolver(), K_LEVEL, 0);
            return scaleFor(b, on, lv);
        } catch (RuntimeException e) {
            return 700;
        }
    }

    /**
     * 应用刻度。没有 WRITE_SETTINGS 时什么都不做（返回 false）；没有 WRITE_SECURE_SETTINGS 时只改亮度，
     * 不碰极暗（暗段无效，返回 true 但 secure 部分被跳过）。绝不抛异常。
     */
    public static boolean apply(Context ctx, int s) {
        s = Math.max(0, Math.min(MAX, s));
        if (!canWriteSettings(ctx)) return false;
        try {
            android.content.ContentResolver r = ctx.getContentResolver();
            Settings.System.putInt(r, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            Settings.System.putInt(r, Settings.System.SCREEN_BRIGHTNESS, brightnessFor(s));
            if (canSecure(ctx)) {
                int lv = levelFor(s);
                if (lv > 0) {
                    Settings.Secure.putInt(r, K_LEVEL, lv);
                    Settings.Secure.putInt(r, K_ACTIVE, 1);
                } else if (Settings.Secure.getInt(r, K_ACTIVE, 0) == 1) {
                    Settings.Secure.putInt(r, K_ACTIVE, 0);
                }
            }
            return true;
        } catch (RuntimeException e) {
            CrashLog.note(ctx, "微光", e);
            return false;
        }
    }

    // ------------------------------------------------------------ 预设与切换

    public static final String[] PRESETS = {"day", "read", "night"};
    public static final String[] PRESET_NAMES = {"白天", "阅读", "夜间"};
    private static final int[] PRESET_DEFAULT = {850, 560, 120};

    public static int preset(Context ctx, int i) {
        return new Prefs(ctx).dimPreset(PRESETS[i], PRESET_DEFAULT[i]);
    }

    public static void savePreset(Context ctx, int i, int s) {
        new Prefs(ctx).setDimPreset(PRESETS[i], s);
    }

    /** 快捷开关：夜间预设 ⇄ 上次的日间值。返回切换后的刻度，没有权限返回 -1。 */
    public static int toggle(Context ctx) {
        Prefs p = new Prefs(ctx);
        int night = preset(ctx, 2), cur = read(ctx);
        int target;
        if (cur <= night + 30) {
            target = p.dimLastDay(preset(ctx, 0));
        } else {
            p.setDimLastDay(cur);
            target = night;
        }
        return apply(ctx, target) ? target : -1;
    }

    public static boolean isNight(Context ctx) {
        return read(ctx) <= preset(ctx, 2) + 30;
    }
}
