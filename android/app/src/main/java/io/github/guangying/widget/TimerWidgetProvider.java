package io.github.guangying.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.widget.RemoteViews;

import io.github.guangying.R;

/**
 * 定时器组件（静音）：点数字在常用时长之间切换（5 / 10 / 15 / 25 / 30 / 45 / 60 分钟），
 * −5 / +5 微调，中间按钮开始或暂停，右边按钮重置。时间到了在组件上显示「时间到」，不发声。
 */
public class TimerWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_TOGGLE = "io.github.guangying.ACTION_TOGGLE";
    public static final String ACTION_RESET = "io.github.guangying.ACTION_RESET";
    public static final String ACTION_MINUS = "io.github.guangying.ACTION_MINUS";
    public static final String ACTION_PLUS = "io.github.guangying.ACTION_PLUS";
    public static final String ACTION_CYCLE = "io.github.guangying.ACTION_CYCLE";
    public static final String ACTION_DONE = "io.github.guangying.ACTION_DONE";

    private static final String PREFS_NAME = "io.github.guangying.TimerWidgetPrefs";
    private static final String P_RUNNING = "running";
    private static final String P_END = "end";            // 运行中：到点的时刻（elapsedRealtime）
    private static final String P_LEFT = "left";          // 停止时：剩余毫秒
    private static final String P_DURATION = "duration";  // 重置回到的时长
    private static final String P_DONE = "done";

    private static final long MIN = 60000L;
    private static final long[] PRESETS = {5 * MIN, 10 * MIN, 15 * MIN, 25 * MIN, 30 * MIN, 45 * MIN, 60 * MIN};

    @Override
    public void onUpdate(Context context, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) update(context, mgr, id);
    }

    private static void update(Context ctx, AppWidgetManager mgr, int id) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean running = sp.getBoolean(P_RUNNING, false);
        long now = SystemClock.elapsedRealtime();
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.timer_widget);

        if (running) {
            long end = sp.getLong(P_END, now);
            v.setChronometer(R.id.timer_chronometer, end, "%s", true);
            v.setImageViewResource(R.id.btn_toggle, android.R.drawable.ic_media_pause);
        } else if (sp.getBoolean(P_DONE, false)) {
            v.setChronometer(R.id.timer_chronometer, now, "时间到", false);
            v.setImageViewResource(R.id.btn_toggle, android.R.drawable.ic_media_play);
        } else {
            long left = sp.getLong(P_LEFT, 25 * MIN);
            v.setChronometer(R.id.timer_chronometer, now + left, clock(left), false);
            v.setImageViewResource(R.id.btn_toggle, android.R.drawable.ic_media_play);
        }
        v.setOnClickPendingIntent(R.id.btn_toggle, pending(ctx, ACTION_TOGGLE, 0));
        v.setOnClickPendingIntent(R.id.btn_reset, pending(ctx, ACTION_RESET, 1));
        v.setOnClickPendingIntent(R.id.btn_minus, pending(ctx, ACTION_MINUS, 2));
        v.setOnClickPendingIntent(R.id.btn_plus, pending(ctx, ACTION_PLUS, 3));
        v.setOnClickPendingIntent(R.id.timer_chronometer, pending(ctx, ACTION_CYCLE, 4));
        mgr.updateAppWidget(id, v);
    }

    private static String clock(long ms) {
        long s = Math.max(0, (ms + 999) / 1000);
        return String.format(java.util.Locale.US, "%02d:%02d", s / 60, s % 60);
    }

    private static PendingIntent pending(Context ctx, String action, int code) {
        Intent i = new Intent(ctx, TimerWidgetProvider.class).setAction(action);
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void alarm(Context ctx, long endElapsed) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = pending(ctx, ACTION_DONE, 5);
        am.cancel(pi);
        if (endElapsed > 0) am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endElapsed, pi);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        String a = intent.getAction();
        if (a == null) return;
        SharedPreferences sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = sp.edit();
        boolean running = sp.getBoolean(P_RUNNING, false);
        long now = SystemClock.elapsedRealtime();
        long duration = sp.getLong(P_DURATION, 25 * MIN);
        long left = sp.getLong(P_LEFT, duration);
        if (running) left = Math.max(0, sp.getLong(P_END, now) - now);

        switch (a) {
            case ACTION_TOGGLE:
                if (running) {                                      // 暂停
                    ed.putBoolean(P_RUNNING, false).putLong(P_LEFT, left);
                    alarm(ctx, 0);
                } else {                                            // 开始（走完了再按就从头开始）
                    if (left <= 0 || sp.getBoolean(P_DONE, false)) left = duration;
                    ed.putBoolean(P_RUNNING, true).putBoolean(P_DONE, false).putLong(P_END, now + left);
                    alarm(ctx, now + left);
                }
                break;
            case ACTION_RESET:
                ed.putBoolean(P_RUNNING, false).putBoolean(P_DONE, false).putLong(P_LEFT, duration);
                alarm(ctx, 0);
                break;
            case ACTION_MINUS:
            case ACTION_PLUS: {
                long d = a.equals(ACTION_PLUS) ? 5 * MIN : -5 * MIN;
                if (running) {
                    long nl = Math.max(MIN, left + d);
                    ed.putLong(P_END, now + nl);
                    alarm(ctx, now + nl);
                } else {
                    long nd = Math.max(MIN, Math.min(180 * MIN, (sp.getBoolean(P_DONE, false) ? duration : left) + d));
                    ed.putLong(P_LEFT, nd).putLong(P_DURATION, nd).putBoolean(P_DONE, false);
                }
                break;
            }
            case ACTION_CYCLE:
                if (!running) {                                     // 在常用时长之间切换
                    long next = PRESETS[0];
                    for (long p : PRESETS) {
                        if (p > duration) {
                            next = p;
                            break;
                        }
                    }
                    ed.putLong(P_LEFT, next).putLong(P_DURATION, next).putBoolean(P_DONE, false);
                }
                break;
            case ACTION_DONE:
                ed.putBoolean(P_RUNNING, false).putBoolean(P_DONE, true).putLong(P_LEFT, 0);
                break;
            default:
                return;
        }
        ed.apply();
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        for (int id : mgr.getAppWidgetIds(new ComponentName(ctx, TimerWidgetProvider.class))) update(ctx, mgr, id);
    }
}
