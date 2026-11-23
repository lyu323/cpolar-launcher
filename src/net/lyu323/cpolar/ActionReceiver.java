package net.lyu323.cpolar;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.widget.Toast;

/** 通知栏 / 磁贴的动作广播：启停、重启、复制地址、看日志、地址列表。 */
public class ActionReceiver extends BroadcastReceiver {

    public static final String A_STOP = "net.lyu323.cpolar.STOP";
    public static final String A_START = "net.lyu323.cpolar.START";
    public static final String A_RESTART = "net.lyu323.cpolar.RESTART";
    public static final String A_COPY = "net.lyu323.cpolar.COPY_URL";
    public static final String A_LOG = "net.lyu323.cpolar.OPEN_LOG";
    public static final String A_URLS = "net.lyu323.cpolar.OPEN_URLS";

    @Override public void onReceive(Context c, Intent i) {
        String a = i == null ? "" : i.getAction();
        if (a == null) a = "";
        if (A_COPY.equals(a)) { copy(c); return; }
        if (A_LOG.equals(a)) { openPage(c, 2, false); return; }
        if (A_URLS.equals(a)) { openPage(c, 0, true); return; }
        Intent s = new Intent(c, CpolarService.class);
        if (A_STOP.equals(a)) s.setAction(CpolarService.ACTION_STOP);
        else if (A_START.equals(a)) s.setAction(CpolarService.ACTION_START);
        else if (A_RESTART.equals(a)) s.setAction(CpolarService.ACTION_RESTART);
        else return;
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s);
        else c.startService(s);
    }

    private void openPage(Context c, int page, boolean urls) {
        Intent i = new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("fromNotify", true)
                .putExtra("page", page)
                .putExtra("showUrls", urls);
        try { c.startActivity(i); }
        catch (Exception e) { Toast.makeText(c, "请打开 Cpolar 启动器", Toast.LENGTH_SHORT).show(); }
    }

    private void copy(Context c) {
        String s = CpolarUrls.asText(CpolarUrls.deserialize(
                c.getSharedPreferences("cpolar", Context.MODE_PRIVATE)
                        .getString("urls", "")));
        try {
            ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Cpolar 地址", s));
            Toast.makeText(c, "已复制全部公网地址", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(c, "复制失败", Toast.LENGTH_SHORT).show();
        }
    }
}
