package net.cpolar.mobile;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.TrafficStats;
import android.os.Build;
import android.os.PowerManager;
import android.os.Process;
import android.util.DisplayMetrics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 基础设备信息与适配度评估。
 *
 * 【隐私边界 —— 本类只读取以下公开/非敏感信息】
 *   品牌、型号、Android 版本、SDK 版本、CPU ABI 列表、屏幕分辨率/密度、
 *   总内存/可用内存、CPU 核数、系统深色模式、语言地区、
 *   通知权限状态、电池优化白名单状态、UID 流量统计是否可用、是否模拟器。
 *
 * 【明确不读取】
 *   IMEI / MEID / 序列号 / MAC / 广告 ID / 位置 / 通讯录 / 账号列表 /
 *   电话号码 / SIM 信息 / 已安装应用列表 / 剪贴板 / 截图 / 传感器数据。
 * Manifest 里也没有申请任何上述权限。
 */
public final class DeviceInfo {
    private DeviceInfo() {}

    public static final class Info {
        public String brand = "", model = "", android = "", sdk = "";
        public String abis = "", primaryAbi = "", screen = "", density = "";
        public String ram = "", availRam = "", cores = "";
        public String darkMode = "", locale = "", notify = "", battery = "";
        public String traffic = "", emulator = "", archNote = "";
        public long ramMB = 0;
        public int sdkInt = 0;
        public boolean hasArm64 = false, dark = false, notifyGranted = false;
        public boolean batteryWhitelisted = false, trafficSupported = false;
        public boolean emulatorBool = false;

        /** 兼容旧调用：逐行文本。 */
        public String[] rows() {
            String[][] pp = pairs();
            String[] out = new String[pp.length];
            for (int i = 0; i < pp.length; i++) out[i] = pp[i][0] + "  " + pp[i][1];
            return out;
        }

        /**
         * 结构化两列数据：key 固定短标签，value 单独一个 View，
         * 不再靠空格对齐，长内容自动换行也不会错位（v2.6 修截图里的折行问题）。
         */
        public String[][] pairs() {
            return new String[][]{
                    {"设备", brand + " " + model},
                    {"系统", "Android " + android + " · API " + sdk},
                    {"CPU 架构", abis},
                    {"首选 ABI", primaryAbi},
                    {"分辨率", screen.replaceAll("（约.*", "").trim()},
                    {"像素密度", density},
                    {"屏幕尺寸", screen.contains("约")
                            ? "约 " + screen.substring(screen.indexOf("约") + 1).replace("）", "").trim()
                            : "—"},
                    {"总内存", ram},
                    {"可用内存", availRam},
                    {"CPU 核数", cores + " 核"},
                    {"深色模式", darkMode},
                    {"语言地区", locale},
                    {"通知权限", notify},
                    {"电池优化", battery},
                    {"流量统计", traffic},
                    {"运行环境", emulator},
                    {"架构判定", archNote},
            };
        }
    }

    public static Info collect(Context c) {
        Info i = new Info();
        i.brand = safe(Build.BRAND);
        i.model = safe(Build.MODEL);
        i.android = safe(Build.VERSION.RELEASE);
        i.sdkInt = Build.VERSION.SDK_INT;
        i.sdk = String.valueOf(i.sdkInt);

        String[] abis = Build.SUPPORTED_ABIS != null
                ? Build.SUPPORTED_ABIS : new String[]{safe(Build.CPU_ABI)};
        i.abis = join(abis);
        i.primaryAbi = abis.length > 0 ? abis[0] : "未知";
        List<String> abiList = Arrays.asList(abis);
        i.hasArm64 = abiList.contains("arm64-v8a");
        i.archNote = i.hasArm64 ? "arm64-v8a（官方魔改内核，最佳）"
                : abiList.contains("armeabi-v7a") ? "armeabi-v7a（标准内核，认证可能不同）"
                : abiList.contains("x86_64") ? "x86_64（多为模拟器，仅标准内核）"
                : "未识别";

        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        double inch = Math.sqrt(Math.pow(dm.widthPixels / dm.xdpi, 2)
                + Math.pow(dm.heightPixels / dm.ydpi, 2));
        i.screen = dm.widthPixels + " × " + dm.heightPixels
                + "（约 " + fmt1(inch) + " 英寸）";
        i.density = dm.densityDpi + " dpi · x" + fmt1(dm.density);

        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mem = new ActivityManager.MemoryInfo();
        try { am.getMemoryInfo(mem); } catch (Exception ignored) {}
        i.ramMB = mem.totalMem / (1024L * 1024L);
        i.ram = Units.bytes(mem.totalMem, true);
        i.availRam = Units.bytes(mem.availMem, true);
        i.cores = String.valueOf(Runtime.getRuntime().availableProcessors());

        int night = c.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        i.dark = night == Configuration.UI_MODE_NIGHT_YES;
        i.darkMode = i.dark ? "已开启" : "未开启（浅色）";
        Locale l = Locale.getDefault();
        i.locale = l.getLanguage() + "_" + l.getCountry();

        if (Build.VERSION.SDK_INT >= 33) {
            i.notifyGranted = c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            i.notifyGranted = true;   // 33 以下无运行时通知权限
        }
        i.notify = i.notifyGranted ? "✓ 已授予" : "✗ 未授予（通知栏不显示）";

        if (Build.VERSION.SDK_INT >= 23) {
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            try {
                i.batteryWhitelisted = pm.isIgnoringBatteryOptimizations(c.getPackageName());
            } catch (Exception ignored) {}
        }
        i.battery = i.batteryWhitelisted ? "✓ 已加入白名单"
                : "✗ 未加入（后台可能被系统杀掉）";

        try {
            long rx = TrafficStats.getUidRxBytes(Process.myUid());
            i.trafficSupported = rx != TrafficStats.UNSUPPORTED;
        } catch (Throwable t) { i.trafficSupported = false; }
        i.traffic = i.trafficSupported ? "✓ 支持（速度/流量可显示）"
                : "✗ 不支持（速度显示不可用）";

        String fp = safe(Build.FINGERPRINT).toLowerCase();
        String hw = safe(Build.HARDWARE).toLowerCase();
        i.emulatorBool = fp.contains("generic") || fp.contains("emulator")
                || hw.contains("goldfish") || hw.contains("ranchu");
        i.emulator = i.emulatorBool ? "疑似模拟器 / 虚拟设备" : "实体设备";
        return i;
    }

    // ------------------------------------------------------------ 适配度评估

    public static final class Check {
        public String name, result, advice, actionKey = "";
        /** 0=通过 1=建议 2=风险 */
        public int level;
        public int score;   // 0..weight
        public int weight;
        Check(String n, String r, String a, int lv, int s, int w) {
            name = n; result = r; advice = a; level = lv; score = s; weight = w;
        }
        Check fix(String k) { actionKey = k; return this; }
    }

    public static final class Report {
        public List<Check> checks = new ArrayList<Check>();
        /** 不计分的补充观察，让评估更贴近真机情况。 */
        public List<String> extras = new ArrayList<String>();
        public int score = 0;
        public String level = "", summary = "";
        public int delta = 0;   // 相对满分的缺口
    }

    /**
     * 纯函数版评估（旧签名保留，供离线回归测试使用）。
     * 委托到带更多真机信号的新版本，默认「未被系统杀过」。
     */
    public static Report assess(boolean hasArm64, boolean isArmv7, boolean isX86,
                                int sdk, boolean notifyGranted,
                                boolean batteryWhitelisted, boolean trafficSupported,
                                long ramMB, int cores, boolean emulator,
                                boolean glassMotion, int themeId) {
        return assess(hasArm64, isArmv7, isX86, sdk, notifyGranted,
                batteryWhitelisted, trafficSupported, ramMB, cores, emulator,
                glassMotion, themeId, false, -1, false, true);
    }

    /**
     * 完整评估。每一项都给结论 + 可执行建议 + 修复入口 actionKey。
     * 权重合计 100：架构 25 / 系统 15 / 通知 12 / 电池 18 /
     * 流量统计 10 / 内存 10 / 运行形态 10。
     *
     * v2.6 调整：电池优化「未加白名单」不再一刀切扣 12 分，
     * 按是否真的被系统回收过细分（没被杀 → 扣 8，被杀过 → 扣 14），
     * 避免「只差一步设置」就掉到 88 分显得过重。
     */
    public static Report assess(boolean hasArm64, boolean isArmv7, boolean isX86,
                                int sdk, boolean notifyGranted,
                                boolean batteryWhitelisted, boolean trafficSupported,
                                long ramMB, int cores, boolean emulator,
                                boolean glassMotion, int themeId,
                                boolean wasKilled, int channelImportance,
                                boolean powerSave, boolean kernelPresent) {
        Report r = new Report();
        add(r, "CPU 架构与内核", hasArm64 ? "✓ arm64-v8a · 官方魔改内核"
                        : isArmv7 ? "◌ armeabi-v7a · 标准 frp 内核"
                        : isX86 ? "⚠ x86 架构 · 仅标准内核" : "⚠ 未识别架构",
                hasArm64 ? "已安装 arm64 版即可，内核与服务端认证天然配套。"
                        : "建议换回 arm64 设备安装 arm64 包；当前包内是标准 frp 内核，"
                          + "若出现 login to the server failed 属认证差异。",
                hasArm64 ? 0 : 1, hasArm64 ? 25 : (isArmv7 ? 15 : 8), 25)
                .fix(hasArm64 ? "" : "info");

        boolean sdkOk = sdk >= 23;
        boolean sdkHigh = sdk >= 33;
        add(r, "系统版本", sdkOk ? "✓ Android API " + sdk + "（≥23 可安装）"
                        : "✗ API " + sdk + " 过低",
                sdkOk ? (sdkHigh ? "API 33+ 需运行时通知权限，本 App 已自动申请。"
                                : "内核走 nativeLibraryDir，Android 10+ 的执行限制已规避。")
                      : "本 App 最低要求 Android 6.0（API 23）。",
                sdkOk ? 0 : 2, sdkOk ? 15 : 0, 15);

        boolean notifyOk = sdk < 33 || notifyGranted;
        add(r, "通知权限", notifyOk ? "✓ 可显示前台服务通知"
                        : "✗ 未授予 POST_NOTIFICATIONS",
                notifyOk ? "通知栏能显示隧道状态与流量。" : "点「一键获取权限」申请，否则通知栏不会出现。",
                notifyOk ? 0 : 2, notifyOk ? 12 : 0, 12)
                .fix(notifyOk ? "" : "notify");

        int batScore = batteryWhitelisted ? 18 : (wasKilled ? 4 : 10);
        String batRes = batteryWhitelisted ? "✓ 已忽略电池优化"
                : wasKilled ? "✗ 未加白名单，且上次服务疑似被系统回收"
                            : "◌ 未加入白名单（暂未发现被回收）";
        add(r, "后台保活", batRes,
                batteryWhitelisted ? "长时挂隧道较稳。"
                        : "点「一键获取权限」会直接弹系统确认框，点「允许」即可；"
                          + "国产 ROM 还要单独允许自启动和锁定后台。",
                batteryWhitelisted ? 0 : (wasKilled ? 2 : 1), batScore, 18)
                .fix(batteryWhitelisted ? "" : "battery");

        add(r, "流量统计", trafficSupported ? "✓ UID 计数可用" : "⚠ 设备不支持 UID 统计",
                trafficSupported ? "实时速度与本次/累计流量可正常显示。"
                        : "隧道本身不受影响，只是速度显示会标「不可用」。",
                trafficSupported ? 0 : 1, trafficSupported ? 10 : 4, 10);

        boolean lowEnd = ramMB > 0 && (ramMB < 3072 || cores <= 4);
        boolean glass = Theme.of(themeId).glass;   // 新增玻璃主题自动纳入评估
        add(r, "性能与主题", lowEnd ? "◌ " + ramMB + " MB / " + cores + " 核（中低端）"
                        : "✓ " + (ramMB > 0 ? ramMB + " MB" : "未知") + " / " + cores + " 核",
                (glass && glassMotion && lowEnd)
                        ? "当前是玻璃主题且开启动态光斑，建议关掉「液态玻璃动态光斑」省电。"
                        : "当前配置无额外持续重绘开销。",
                (glass && glassMotion && lowEnd) ? 1 : 0, 10, 10)
                .fix((glass && glassMotion && lowEnd) ? "motion" : "");

        add(r, "运行形态", emulator ? "◌ 虚拟设备" : "✓ 实体设备",
                emulator ? "模拟器上多为 x86 标准内核，真机效果以实体设备为准。"
                        : "实体设备上内核与权限行为符合预期。",
                emulator ? 1 : 0, emulator ? 5 : 10, 10);

        // ---- 不计分的补充观察 ----
        if (channelImportance == 0) {
            r.extras.add("通知渠道被关闭：即使权限已授予，通知也不会弹出。");
        } else if (channelImportance > 0) {
            r.extras.add("通知渠道重要性 " + channelImportance
                    + "（LOW 静默显示，适合常驻前台服务）。");
        }
        if (powerSave) r.extras.add("系统省电模式开启：会压后台与刷新率，建议关掉动态光斑。");
        if (!kernelPresent) r.extras.add("未找到 libfrpc.so：请确认安装的是完整 APK。");
        if (wasKilled) r.extras.add("检测到上次服务异常终止，优先修「后台保活」。");
        if (ramMB > 0 && lowEnd) r.extras.add("内存偏小 / 核数偏少：玻璃动态效果可减负。");
        if (glass && glassMotion) r.extras.add("玻璃主题动态光斑运行中（4 套玻璃主题均带流动效果）。");
        if (r.extras.isEmpty()) r.extras.add("无额外观察项。");

        int total = 0;
        for (Check c : r.checks) total += c.score;
        r.score = Math.max(0, Math.min(100, total));
        r.delta = 100 - r.score;
        r.level = r.score >= 90 ? "适配优秀" : r.score >= 75 ? "适配良好"
                : r.score >= 60 ? "基本可用" : "需要调整";
        r.summary = r.score >= 90 ? "这台设备跑本启动器基本不用额外设置。"
                : r.score >= 75 ? "整体没问题，按下方建议补一两项即可。"
                : r.score >= 60 ? "隧道能跑，但后台保活或通知可能受影响。"
                : "建议按下方风险项逐条处理，否则容易掉线或看不到状态。";
        return r;
    }

    private static Check add(Report r, String n, String res, String adv,
                             int lv, int s, int w) {
        Check c = new Check(n, res, adv, lv, s, w);
        r.checks.add(c);
        return c;
    }

    /** 用真实采集结果调用完整评估。 */
    public static Report assess(Context c) {
        Info i = collect(c);
        SharedPreferences sp = c.getSharedPreferences("cpolar", Context.MODE_PRIVATE);
        List<String> abis = Arrays.asList(i.abis.split(" · "));
        boolean killed = sp.getBoolean("serviceKilled", false);

        int channel = -1;
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                android.app.NotificationManager nm = (android.app.NotificationManager)
                        c.getSystemService(Context.NOTIFICATION_SERVICE);
                android.app.NotificationChannel ch = nm.getNotificationChannel("frp");
                if (ch != null) channel = ch.getImportance();
            } catch (Exception ignored) {}
        }
        boolean powerSave = false;
        try {
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            powerSave = pm.isPowerSaveMode();
        } catch (Exception ignored) {}
        boolean kernel = false;
        try {
            kernel = new java.io.File(c.getApplicationInfo().nativeLibraryDir,
                    "libfrpc.so").exists();
        } catch (Exception ignored) {}

        return assess(i.hasArm64, abis.contains("armeabi-v7a"),
                abis.contains("x86_64") || abis.contains("x86"),
                i.sdkInt, i.notifyGranted, i.batteryWhitelisted, i.trafficSupported,
                i.ramMB, parseInt(i.cores), i.emulatorBool,
                sp.getBoolean("glassMotion", true), sp.getInt("themeId", 0),
                killed, channel, powerSave, kernel);
    }

    private static int parseInt(String s) { try { return Integer.parseInt(s); } catch (Exception e) { return 4; } }
    private static String safe(String s) { return s == null ? "" : s; }
    private static String join(String[] a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length; i++) {
            if (i > 0) sb.append(" · ");
            sb.append(a[i]);
        }
        return sb.length() == 0 ? "未知" : sb.toString();
    }
    private static String fmt1(double v) {
        return String.format(Locale.US, "%.1f", v);
    }
}
