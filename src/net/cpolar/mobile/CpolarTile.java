package net.cpolar.mobile;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** 下拉快捷设置磁贴：一键启停，沿用历史版本的交互。 */
@TargetApi(24)
public class CpolarTile extends TileService {
    @Override public void onStartListening() {
        super.onStartListening();
        Tile t = getQsTile();
        if (t == null) return;
        boolean run = CpolarService.isRunning();
        t.setState(run ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel(run ? "Cpolar 运行中" : "启动 Cpolar");
        t.updateTile();
    }

    @Override public void onClick() {
        super.onClick();
        boolean run = CpolarService.isRunning();
        Intent s = new Intent(this, CpolarService.class)
                .setAction(run ? CpolarService.ACTION_STOP : CpolarService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(s);
        else startService(s);
        Tile t = getQsTile();
        if (t != null) {
            t.setState(run ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE);
            t.setLabel(run ? "启动 Cpolar" : "Cpolar 运行中");
            t.updateTile();
        }
    }
}
