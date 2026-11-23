package net.lyu323.cpolar;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限体检与「一键获取权限」v2.6。
 *
 * 相比 v2.5 的三处硬优化：
 *  1) 电池优化从「跳设置页自己找」改成 ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
 *     —— 系统直接弹「允许后台运行？」对话框，用户点一下即可，不用在列表里翻。
 *  2) 自启动按品牌直达厂商权限管理页（vivo/iQOO、小米、华为、OPPO、一加…），
 *     全部失败再回退到应用详情。
 *  3) 通知渠道用 NotificationManager 实际读 importance，不再只写「由系统决定」；
 *     并区分「未授予 / 被永久拒绝」两种状态，给不同引导。
 *
 * 仍然不申请任何隐私权限（定位、通讯录、电话、存储、相机等一律不涉及）。
 */
public final class Perm {
    private Perm() {}

    public static final int REQ_NOTIFY = 41;
    public static final int REQ_BATTERY = 42;

    public static final class Item {
        public String name, state, how, tip, icon;
        /** 0=已就绪 1=建议 2=需处理 3=需手动确认 */
        public int level;
        public boolean ok, auto;
        public Runnable action;
        Item(String n, String s, String h, String t, String ic,
             int lv, boolean o, boolean a) {
            name = n; state = s; how = h; tip = t; icon = ic;
            level = lv; ok = o; auto = a;
        }
    }

    public static List<Item> check(final Activity a) {
        List<Item> out = new ArrayList<Item>();

        // ① 通知权限：唯一能自动弹框申请的一项
        if (Build.VERSION.SDK_INT >= 33) {
            boolean granted = a.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    == PackageManager.PERMISSION_GRANTED;
            boolean deniedForever = !granted
                    && !a.shouldShowRequestPermissionRationale(
                            "android.permission.POST_NOTIFICATIONS");
            Item it = new Item("通知权限",
                    granted ? "已授予 · 通知栏可显示" :
                            deniedForever ? "曾拒绝且不再询问 · 需去设置开启" : "未授予 · 通知栏不显示",
                    granted ? "无需操作" :
                            deniedForever ? "跳应用通知设置手动打开" : "一键自动申请（系统弹框）",
                    granted ? "前台服务通知、隧道状态与实时流量都能正常显示。"
                            : "没有这个权限，前台服务仍在跑，但通知栏完全看不到。",
                    granted ? "✓" : "!", granted ? 0 : 2, granted,
                    granted || !deniedForever);
            if (!granted) {
                if (deniedForever) {
                    it.action = new Runnable() {
                        @Override public void run() { a.startActivity(appNotification(a)); }
                    };
                } else {
                    it.action = new Runnable() {
                        @Override public void run() {
                            a.requestPermissions(new String[]{
                                    "android.permission.POST_NOTIFICATIONS"}, REQ_NOTIFY);
                        }
                    };
                }
            }
            out.add(it);
        } else {
            out.add(new Item("通知权限", "Android 13 以下自动授予", "无需操作",
                    "系统自动授予，通知栏可正常显示。", "✓", 0, true, true));
        }

        // ② 电池优化：v2.6 直接弹系统对话框
        boolean ignoring = false;
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                PowerManager pm = (PowerManager) a.getSystemService(Context.POWER_SERVICE);
                ignoring = pm.isIgnoringBatteryOptimizations(a.getPackageName());
            } catch (Exception ignored) {}
        }
        Item bat = new Item("忽略电池优化",
                ignoring ? "已加入白名单 · 后台较稳" : "未加入 · 长时间挂隧道可能被系统回收",
                ignoring ? "无需操作" : "一键弹出系统确认框，点「允许」即可",
                ignoring ? "长时后台运行的基础已就绪。"
                        : "这是上一轮适配度唯一扣分的那项。点一下系统弹框就能补上，"
                          + "不用再去设置里翻列表。",
                ignoring ? "✓" : "⚡", ignoring ? 0 : 1, ignoring, false);
        bat.action = new Runnable() {
            @Override public void run() { requestBatteryWhitelist(a); }
        };
        out.add(bat);

        // ③ 自启动 / 后台运行：厂商页直达
        Item boot = new Item("自启动 / 后台锁定",
                "系统不提供查询接口，需你确认一次",
                "按 " + vendorName() + " 直达权限页，失败会回退应用详情",
                "国产 ROM（vivo/iQOO、小米、华为、OPPO 等）除了电池白名单，"
                        + "还要单独允许自启动并在多任务卡片里下拉锁定。",
                "◌", 3, false, false);
        boot.action = new Runnable() {
            @Override public void run() { a.startActivity(vendorAutoStart(a)); }
        };
        out.add(boot);

        // ④ 通知渠道：实际读 importance
        String chanState;
        int chanLevel;
        if (Build.VERSION.SDK_INT >= 26) {
            int imp = -1;
            try {
                NotificationManager nm = (NotificationManager)
                        a.getSystemService(Context.NOTIFICATION_SERVICE);
                NotificationChannel ch = nm.getNotificationChannel("frp");
                if (ch != null) imp = ch.getImportance();
            } catch (Exception ignored) {}
            if (imp == NotificationManager.IMPORTANCE_NONE) {
                chanState = "渠道被关闭 · 通知不会弹出"; chanLevel = 2;
            } else if (imp == NotificationManager.IMPORTANCE_LOW
                    || imp == NotificationManager.IMPORTANCE_MIN) {
                chanState = "低重要性 · 静默显示（前台服务推荐）"; chanLevel = 0;
            } else if (imp > 0) {
                chanState = "渠道正常 · 重要性 " + imp; chanLevel = 0;
            } else {
                chanState = "尚未创建 · 首次启动隧道后自动生成"; chanLevel = 1;
            }
        } else {
            chanState = "Android 8 以下无渠道概念"; chanLevel = 0;
        }
        Item chan = new Item("通知渠道 / 锁屏显示", chanState,
                "跳本应用通知设置，可关掉锁屏上的连接域名",
                "不希望在锁屏露出域名时，在这里把「锁屏通知」改为隐藏内容。",
                chanLevel == 2 ? "!" : "◌", chanLevel, chanLevel == 0, false);
        chan.action = new Runnable() {
            @Override public void run() { a.startActivity(appNotification(a)); }
        };
        out.add(chan);

        // ⑤ 省电模式（观察项，不需要授权）
        boolean powerSave = false;
        try {
            PowerManager pm = (PowerManager) a.getSystemService(Context.POWER_SERVICE);
            powerSave = pm.isPowerSaveMode();
        } catch (Exception ignored) {}
        out.add(new Item("系统省电模式", powerSave ? "已开启 · 建议关闭动态光斑" : "未开启",
                powerSave ? "设置页关掉「液态玻璃动态光斑」更省电" : "无需操作",
                powerSave ? "省电模式下系统会压后台与刷新率，玻璃动态效果也更耗电。"
                        : "当前没有系统级省电压制。",
                powerSave ? "◌" : "✓", powerSave ? 1 : 0, !powerSave, true));

        // ⑥ 前台服务权限
        out.add(new Item("前台服务权限", "已在 Manifest 声明，安装即获得", "无需操作",
                "隧道以 foregroundServiceType=specialUse 常驻，不需要额外授权。",
                "✓", 0, true, true));
        return out;
    }

    // ------------------------------------------------------------ 电池白名单

    /** 系统直接弹确认框；失败再回退设置列表。 */
    public static void requestBatteryWhitelist(Activity a) {
        if (Build.VERSION.SDK_INT < 23) return;
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + a.getPackageName()));
            a.startActivityForResult(i, REQ_BATTERY);
            return;
        } catch (Exception ignored) {}
        try {
            a.startActivity(new Intent(
                    Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        } catch (Exception e) {
            a.startActivity(appDetails(a));
        }
    }

    // ------------------------------------------------------------ 厂商自启动

    public static String vendorName() {
        String b = Build.BRAND == null ? "" : Build.BRAND.toLowerCase();
        String m = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase();
        if (b.contains("vivo") || b.contains("iqoo") || m.contains("vivo")) return "vivo / iQOO";
        if (b.contains("xiaomi") || b.contains("redmi") || m.contains("xiaomi")) return "小米 / Redmi";
        if (b.contains("huawei") || b.contains("honor") || m.contains("huawei")) return "华为 / 荣耀";
        if (b.contains("oppo") || b.contains("realme") || m.contains("oppo")) return "OPPO / 一加 / realme";
        if (b.contains("meizu") || m.contains("meizu")) return "魅族";
        if (b.contains("samsung") || m.contains("samsung")) return "三星";
        return "本机";
    }

    /** 逐个尝试厂商自启动管理页，全部不可用时回退应用详情。 */
    public static Intent vendorAutoStart(Context c) {
        String[][] targets = {
            // vivo / iQOO
            {"com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartActivityManager"},
            {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartActivityManager"},
            {"com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.PurviewTabActivity"},
            // 小米 / Redmi
            {"com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"},
            // 华为 / 荣耀
            {"com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
            {"com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity"},
            // OPPO / 一加 / realme
            {"com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"},
            {"com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"},
            // 魅族
            {"com.meizu.safe", "com.meizu.safe.security.SecGuardMainActivity"},
            // 三星
            {"com.samsung.android.lool",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"},
        };
        for (String[] t : targets) {
            try {
                Intent i = new Intent();
                i.setComponent(new ComponentName(t[0], t[1]));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (c.getPackageManager().resolveActivity(i, 0) != null) return i;
            } catch (Exception ignored) {}
        }
        return appDetails(c);
    }

    public static Intent appDetails(Context c) {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + c.getPackageName()));
    }

    public static Intent appNotification(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, c.getPackageName());
            if (c.getPackageManager().resolveActivity(i, 0) != null) return i;
        }
        return appDetails(c);
    }

    // ------------------------------------------------------------ 兼容旧调用

    /** 返回「自动申请了几项 / 有几项仍需手动」。 */
    public static String autoGrant(Activity a, List<Item> items) {
        int auto = 0, manual = 0;
        for (Item it : items) {
            if (it.ok) continue;
            if (it.auto) {
                if (it.action != null) { it.action.run(); auto++; }
            } else manual++;
        }
        return auto + "|" + manual;
    }

    public static String summary(List<Item> items) {
        int ok = 0, warn = 0, manual = 0;
        for (Item it : items) {
            if (it.ok) ok++;
            else if (it.auto) warn++;
            else manual++;
        }
        return "已就绪 " + ok + " 项 · 可自动申请 " + warn
                + " 项 · 需手动 " + manual + " 项";
    }
}
