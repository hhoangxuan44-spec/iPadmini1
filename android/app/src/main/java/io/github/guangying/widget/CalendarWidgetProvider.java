package io.github.guangying.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.widget.RemoteViews;

import java.util.Calendar;

import io.github.guangying.R;

public class CalendarWidgetProvider extends AppWidgetProvider {

    private static final String[] WEEK_DAYS = {"日", "一", "二", "三", "四", "五", "六"};
    private static final String[] MONTH_NAMES = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};
    private static final String[] NUM_CN = {"一", "二", "三", "四", "五", "六", "七", "八", "九", "十", "十一", "十二"};

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.calendar_widget);
        Bitmap bitmap = renderCalendar(context);
        views.setImageViewBitmap(R.id.calendar_image, bitmap);
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static Bitmap renderCalendar(Context context) {
        int width = 800;
        int height = 1000;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // 背景
        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(Color.parseColor("#E61C1C1E")); // 半透明深色
        RectF bgRect = new RectF(0, 0, width, height);
        canvas.drawRoundRect(bgRect, 60, 60, bgPaint);

        Calendar now = Calendar.getInstance();
        int year = now.get(Calendar.YEAR);
        int month = now.get(Calendar.MONTH);
        int day = now.get(Calendar.DAY_OF_MONTH);

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);

        // 绘制头部：数字月份和英文月份
        textPaint.setTextSize(120);
        textPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        String monthNum = String.valueOf(month + 1);
        canvas.drawText(monthNum, 60, 160, textPaint);

        float monthNumWidth = textPaint.measureText(monthNum);

        textPaint.setTextSize(40);
        textPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        canvas.drawText(MONTH_NAMES[month].toUpperCase(), 60 + monthNumWidth + 30, 100, textPaint);

        textPaint.setTextSize(30);
        textPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        textPaint.setAlpha(150);
        canvas.drawText(year + " · " + NUM_CN[month] + "月", 60 + monthNumWidth + 30, 150, textPaint);

        // 绘制星期表头
        textPaint.setAlpha(150);
        textPaint.setTextSize(30);
        textPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));

        int cellWidth = (width - 120) / 7;
        int cellHeight = 70;
        int startY = 250;

        for (int i = 0; i < 7; i++) {
            float x = 60 + i * cellWidth + cellWidth / 2f;
            float textWidth = textPaint.measureText(WEEK_DAYS[i]);
            canvas.drawText(WEEK_DAYS[i], x - textWidth / 2f, startY, textPaint);
        }

        // 绘制日期
        textPaint.setAlpha(255);
        textPaint.setTextSize(36);
        textPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));

        Calendar cal = Calendar.getInstance();
        cal.set(year, month, 1);
        int firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1; // 0 = Sunday
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        int currentY = startY + cellHeight + 20;
        Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        highlightPaint.setColor(Color.WHITE);

        Paint todayTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        todayTextPaint.setColor(Color.BLACK);
        todayTextPaint.setTextSize(36);
        todayTextPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));

        for (int i = 1; i <= daysInMonth; i++) {
            int col = (firstDayOfWeek + i - 1) % 7;
            int row = (firstDayOfWeek + i - 1) / 7;
            float cx = 60 + col * cellWidth + cellWidth / 2f;
            float cy = currentY + row * cellHeight * 1.5f;

            if (i == day) {
                // Today highlight
                canvas.drawCircle(cx, cy - 12, 35, highlightPaint);
                String dStr = String.valueOf(i);
                float tw = todayTextPaint.measureText(dStr);
                canvas.drawText(dStr, cx - tw / 2f, cy, todayTextPaint);
            } else {
                String dStr = String.valueOf(i);
                float tw = textPaint.measureText(dStr);

                if (col == 0 || col == 6) {
                    textPaint.setAlpha(150); // Weekend
                } else {
                    textPaint.setAlpha(255);
                }

                canvas.drawText(dStr, cx - tw / 2f, cy, textPaint);
            }
        }

        return bitmap;
    }
}
