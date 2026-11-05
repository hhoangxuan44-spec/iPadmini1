package io.github.guangying;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/** 微光：一个 0–1000 的滑块同时控制屏幕亮度和系统「极暗」。 */
public class DimmerActivity extends Activity {
    private SeekBar bar;
    private TextView value, note, brightInfo;
    private LinearLayout perms;
    private float dp;
    private boolean quiet;                 // 程序设置滑块时不重复应用
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        final int pad = px(28);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(pad, pad, pad, pad);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(pad + i.left, pad + i.top, pad + i.right, pad + i.bottom);
            return WindowInsets.CONSUMED;
        });

        TextView title = text("微光", 30, Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        root.addView(title);
        root.addView(hint("一个滑块调到最亮，也调到比系统最低亮度还暗。上段调屏幕亮度，下段打开系统「极暗」。"));

        value = text("", 72, Color.WHITE);
        value.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        value.setGravity(Gravity.CENTER);
        root.addView(value, new LinearLayout.LayoutParams(-1, -2));
        brightInfo = text("", 13, 0xFF8E8E93);
        brightInfo.setGravity(Gravity.CENTER);
        root.addView(brightInfo, new LinearLayout.LayoutParams(-1, -2));

        // − 滑块 +
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, px(18), 0, px(6));
        row.addView(step("−", -1), new LinearLayout.LayoutParams(px(64), px(64)));
        bar = new SeekBar(this);
        bar.setMax(Dimmer.MAX);
        bar.setPadding(px(18), px(18), px(18), px(18));
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
        bar.setThumbTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
        bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF3A3A3C));
        bar.setSplitTrack(false);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (quiet) return;
                if (!Dimmer.canSecure(DimmerActivity.this) && p < Dimmer.SPLIT) {
                    p = Dimmer.SPLIT;                       // 没有权限时，极暗那一段不可用
                    quiet = true;
                    s.setProgress(p);
                    quiet = false;
                }
                set(p, false);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        row.addView(bar, new LinearLayout.LayoutParams(0, px(64), 1f));
        row.addView(step("+", 1), new LinearLayout.LayoutParams(px(64), px(64)));
        root.addView(row, new LinearLayout.LayoutParams(-1, -2));
        note = hint("");
        root.addView(note);

        // 预设
        LinearLayout presets = new LinearLayout(this);
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView t = text(Dimmer.PRESET_NAMES[i], 17, Color.WHITE);
            t.setGravity(Gravity.CENTER);
            t.setPadding(px(8), px(16), px(8), px(16));
            card(t, 0xFF1C1C1E);
            t.setOnClickListener(v -> set(Dimmer.preset(this, idx), true));
            t.setOnLongClickListener(v -> {
                Dimmer.savePreset(this, idx, bar.getProgress());
                Toast.makeText(this, "已把 " + bar.getProgress() + " 存为「" + Dimmer.PRESET_NAMES[idx] + "」", Toast.LENGTH_SHORT).show();
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i > 0) lp.leftMargin = px(10);
            presets.addView(t, lp);
        }
        root.addView(presets, new LinearLayout.LayoutParams(-1, -2));
        root.addView(hint("点一下套用，长按把当前值存为这个预设。快捷设置里可以添加「微光」开关：点一下在夜间预设和上次的日间值之间切换，长按打开这里。"));

        perms = new LinearLayout(this);
        perms.setOrientation(LinearLayout.VERTICAL);
        root.addView(perms, new LinearLayout.LayoutParams(-1, -2));

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPerms();
        quiet = true;
        bar.setProgress(Dimmer.read(this));
        quiet = false;
        show(bar.getProgress());
    }

    private void set(int s, boolean move) {
        if (move) {
            if (!Dimmer.canSecure(this) && s < Dimmer.SPLIT) s = Dimmer.SPLIT;
            quiet = true;
            bar.setProgress(s);
            quiet = false;
        }
        boolean ok = Dimmer.apply(this, s);
        show(s);
        if (!ok && !Dimmer.canWriteSettings(this)) note.setText("还没有「修改系统设置」的权限，往下看授权。");
    }

    private void show(int s) {
        value.setText(String.valueOf(s));
        int b = Dimmer.brightnessFor(s), lv = Dimmer.levelFor(s);
        brightInfo.setText("屏幕亮度 " + b + " / 255" + (lv > 0 ? "　·　极暗 " + lv + " / 100" : ""));
        note.setText(Dimmer.canSecure(this) ? "" : "极暗（0–" + Dimmer.SPLIT + "）需要先授权，授权前滑块停在 " + Dimmer.SPLIT + " 以上。");
    }

    /** 长按 − / + 连续微调 */
    private TextView step(String label, final int dir) {
        final TextView t = text(label, 28, Color.WHITE);
        t.setGravity(Gravity.CENTER);
        card(t, 0xFF1C1C1E);
        final Runnable[] rep = new Runnable[1];
        rep[0] = new Runnable() {
            @Override
            public void run() {
                nudge(dir * 5);
                main.postDelayed(this, 90);
            }
        };
        t.setOnTouchListener((v, e) -> {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    nudge(dir);
                    main.postDelayed(rep[0], 450);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    main.removeCallbacks(rep[0]);
                    return true;
                default:
                    return true;
            }
        });
        return t;
    }

    private void nudge(int d) {
        int lo = Dimmer.canSecure(this) ? 0 : Dimmer.SPLIT;
        set(Math.max(lo, Math.min(Dimmer.MAX, bar.getProgress() + d)), true);
    }

    private void refreshPerms() {
        perms.removeAllViews();
        if (!Dimmer.canWriteSettings(this)) {
            perms.addView(hint("① 需要「修改系统设置」权限才能调亮度。"));
            TextView b = text("授权修改系统设置", 16, Color.BLACK);
            b.setGravity(Gravity.CENTER);
            b.setPadding(px(16), px(14), px(16), px(14));
            card(b, Color.WHITE);
            b.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName()))));
            perms.addView(b, new LinearLayout.LayoutParams(-1, -2));
        }
        if (!Dimmer.canSecure(this)) {
            final String cmd = Dimmer.grantCommand(this);
            perms.addView(hint("② 极暗需要 WRITE_SECURE_SETTINGS 权限，这个权限只能用电脑授权一次（平板连电脑、开启 USB 调试，执行下面这条命令）："));
            TextView c = text(cmd, 14, 0xFFFFD60A);
            c.setTypeface(Typeface.MONOSPACE);
            c.setTextIsSelectable(true);
            c.setPadding(px(14), px(12), px(14), px(12));
            card(c, 0xFF1C1C1E);
            perms.addView(c, new LinearLayout.LayoutParams(-1, -2));
            TextView copy = text("复制命令", 16, Color.WHITE);
            copy.setGravity(Gravity.CENTER);
            copy.setPadding(px(16), px(12), px(16), px(12));
            card(copy, 0xFF2C2C2E);
            copy.setOnClickListener(v -> {
                ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("adb", cmd));
                Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = px(10);
            perms.addView(copy, lp);
        }
    }

    // ------------------------------------------------------------ 界面小工具

    private int px(float v) {
        return Math.round(v * dp);
    }

    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private TextView hint(String s) {
        TextView t = text(s, 13, 0xFF8E8E93);
        t.setPadding(px(4), px(8), px(4), px(14));
        t.setLineSpacing(0, 1.25f);
        return t;
    }

    private void card(View v, int color) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(px(14));
        g.setColor(color);
        v.setBackground(g);
    }
}
