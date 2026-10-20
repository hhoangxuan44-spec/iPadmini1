package io.github.guangying.widget;

/** 画意组件：这张壁纸的标题、出处、诗句和描述。 */
public class StoryWidget extends ThemedWidget {
    @Override
    int kind() {
        return Widgets.STORY;
    }
}
