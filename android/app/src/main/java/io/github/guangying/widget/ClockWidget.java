package io.github.guangying.widget;

/** 时钟组件：大字时间、日期、农历，样式随壁纸变化。 */
public class ClockWidget extends ThemedWidget {
    @Override
    int kind() {
        return Widgets.CLOCK;
    }
}
