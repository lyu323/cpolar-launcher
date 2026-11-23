package net.lyu323.cpolar;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;

/** 隐藏后台 / 隐私显示，沿用历史版本四项开关，默认全关、随时可恢复。 */
public final class Privacy {
    private Privacy() {}

    public static final String K_RECENTS = "hideRecents";
    public static final String K_SILENT = "silentNotify";
    public static final String K_LOCK = "lockPrivate";
    public static final String K_MASK = "maskUrl";

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("cpolar", Context.MODE_PRIVATE);
    }
    public static boolean recents(Context c) { return sp(c).getBoolean(K_RECENTS, false); }
    public static boolean silent(Context c) { return sp(c).getBoolean(K_SILENT, false); }
    public static boolean lock(Context c) { return sp(c).getBoolean(K_LOCK, false); }
    public static boolean mask(Context c) { return sp(c).getBoolean(K_MASK, false); }
    public static void set(Context c, String k, boolean v) {
        sp(c).edit().putBoolean(k, v).commit();
    }
    public static boolean any(Context c) {
        return recents(c) || silent(c) || lock(c) || mask(c);
    }
    public static String summary(Context c) {
        int n = 0;
        if (recents(c)) n++;
        if (silent(c)) n++;
        if (lock(c)) n++;
        if (mask(c)) n++;
        return n == 0 ? "全部关闭（默认）" : n + " 项已开启";
    }
    public static boolean applyRecents(Activity a) {
        if (android.os.Build.VERSION.SDK_INT < 21) return false;
        try {
            ActivityManager am = (ActivityManager) a.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.AppTask> tasks = am.getAppTasks();
            boolean done = false;
            for (ActivityManager.AppTask t : tasks) {
                t.setExcludeFromRecents(recents(a));
                done = true;
            }
            return done;
        } catch (Exception e) { return false; }
    }
    /** 通知里打码公网域名。 */
    public static String url(Context c, String real) {
        if (!mask(c) || real == null || real.length() == 0) return real;
        return "cpolar://••••••";
    }
}
