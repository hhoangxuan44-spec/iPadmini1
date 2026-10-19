package io.github.guangying.widget;

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

public class TimerWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_TOGGLE = "io.github.guangying.ACTION_TOGGLE";
    public static final String ACTION_RESET = "io.github.guangying.ACTION_RESET";

    private static final String PREFS_NAME = "io.github.guangying.TimerWidgetPrefs";
    private static final String PREF_IS_RUNNING = "is_running";
    private static final String PREF_TIME_LEFT = "time_left";
    private static final String PREF_LAST_TICK = "last_tick";

    private static final long DEFAULT_DURATION = 25 * 60 * 1000; // 25 mins

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
    }

    private static void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.timer_widget);

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isRunning = prefs.getBoolean(PREF_IS_RUNNING, false);
        long timeLeft = prefs.getLong(PREF_TIME_LEFT, DEFAULT_DURATION);

        if (isRunning) {
            long lastTick = prefs.getLong(PREF_LAST_TICK, SystemClock.elapsedRealtime());
            long elapsed = SystemClock.elapsedRealtime() - lastTick;
            timeLeft = Math.max(0, timeLeft - elapsed);

            views.setChronometer(R.id.timer_chronometer, SystemClock.elapsedRealtime() + timeLeft, "%s", true);
            views.setImageViewResource(R.id.btn_toggle, android.R.drawable.ic_media_pause);
        } else {
            // Not running, format text manually
            int m = (int) (timeLeft / 60000);
            int s = (int) ((timeLeft % 60000) / 1000);
            String text = String.format("%02d:%02d", m, s);
            // Stop chronometer and set text by hacking Chronometer's base
            views.setChronometer(R.id.timer_chronometer, SystemClock.elapsedRealtime() + timeLeft, text, false);
            views.setImageViewResource(R.id.btn_toggle, android.R.drawable.ic_media_play);
        }

        // Toggle Intent
        Intent toggleIntent = new Intent(context, TimerWidgetProvider.class);
        toggleIntent.setAction(ACTION_TOGGLE);
        PendingIntent pendingToggle = PendingIntent.getBroadcast(context, 0, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btn_toggle, pendingToggle);

        // Reset Intent
        Intent resetIntent = new Intent(context, TimerWidgetProvider.class);
        resetIntent.setAction(ACTION_RESET);
        PendingIntent pendingReset = PendingIntent.getBroadcast(context, 1, resetIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btn_reset, pendingReset);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isRunning = prefs.getBoolean(PREF_IS_RUNNING, false);
        long timeLeft = prefs.getLong(PREF_TIME_LEFT, DEFAULT_DURATION);
        long lastTick = prefs.getLong(PREF_LAST_TICK, SystemClock.elapsedRealtime());

        if (ACTION_TOGGLE.equals(intent.getAction())) {
            if (isRunning) {
                // Pause it
                long elapsed = SystemClock.elapsedRealtime() - lastTick;
                timeLeft = Math.max(0, timeLeft - elapsed);
                prefs.edit().putBoolean(PREF_IS_RUNNING, false).putLong(PREF_TIME_LEFT, timeLeft).apply();
            } else {
                // Start it
                if (timeLeft == 0) timeLeft = DEFAULT_DURATION; // auto reset if started from 0
                prefs.edit().putBoolean(PREF_IS_RUNNING, true).putLong(PREF_LAST_TICK, SystemClock.elapsedRealtime()).putLong(PREF_TIME_LEFT, timeLeft).apply();
            }
        } else if (ACTION_RESET.equals(intent.getAction())) {
            prefs.edit().putBoolean(PREF_IS_RUNNING, false).putLong(PREF_TIME_LEFT, DEFAULT_DURATION).apply();
        } else {
            return;
        }

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context, TimerWidgetProvider.class));
        for (int id : appWidgetIds) {
            updateWidget(context, appWidgetManager, id);
        }
    }
}
