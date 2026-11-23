package net.cpolar.mobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** 开机自启：仅在用户开启、且配置有效时才拉起服务。 */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        if (i == null || !Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) return;
        android.content.SharedPreferences sp =
                c.getSharedPreferences("cpolar", Context.MODE_PRIVATE);
        if (!sp.getBoolean("autostart", false)) return;
        String token = sp.getString("token", "").trim();
        if (token.length() == 0) return;
        if (CpolarConfig.validate(CpolarConfig.read(c)).length() > 0) return;
        Intent s = new Intent(c, CpolarService.class).setAction(CpolarService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s);
        else c.startService(s);
    }
}
