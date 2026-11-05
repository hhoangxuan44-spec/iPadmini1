package io.github.guangying;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** 快捷设置开关：点一下在「夜间预设」和「上次的日间值」之间切换；长按打开微光。 */
public class DimTile extends TileService {
    @Override
    public void onStartListening() {
        refresh();
    }

    @Override
    public void onClick() {
        if (!Dimmer.canWriteSettings(this)) {              // 还没授权：打开微光去授权
            open();
            return;
        }
        Dimmer.toggle(this);
        refresh();
    }

    private void open() {
        Intent i = new Intent(this, DimmerActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE));
        } else {
            startActivityAndCollapse(i);
        }
    }

    private void refresh() {
        Tile t = getQsTile();
        if (t == null) return;
        boolean night = Dimmer.canWriteSettings(this) && Dimmer.isNight(this);
        t.setState(night ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel("微光");
        t.setIcon(Icon.createWithResource(this, R.drawable.ic_tile_dim));
        if (Build.VERSION.SDK_INT >= 29) {
            t.setSubtitle(Dimmer.canWriteSettings(this) ? (night ? "夜间" : "日间") : "点按授权");
        }
        t.updateTile();
    }
}
