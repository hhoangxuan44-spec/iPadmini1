package io.github.guangying.widget;

/** 月历组件：时钟、日期、农历、每日一言和本月日历，风格随壁纸变化。 */
public class CalendarWidget extends ThemedWidget {
    @Override
    int kind() {
        return Widgets.CALENDAR;
    }
}
