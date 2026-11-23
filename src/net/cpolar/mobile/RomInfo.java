package net.cpolar.mobile;

import android.content.Context;
import android.os.Build;

/**
 * 架构分层说明 v2.7。
 *
 * 之前「架构判定」只有一句 arm64-v8a，容易把四件完全不同的事混在一起：
 *   ① CPU / ABI 架构（硬件指令集，决定装哪个 APK 和 libfrpc.so）
 *   ② 内核架构（Cpolar 官方魔改 frpc  vs  标准 fatedier/frp）
 *   ③ ROM 架构 / 系统形态（OriginOS、MIUI、ColorOS… 决定后台与权限策略）
 *   ④ 启动器架构（本 App 这套免 Root 的新方案，区别于旧的 Root/Termux 方案）
 *
 * 本类只做只读识别与说明文案，不申请权限、不读隐私、不上传。
 * classify() 是纯字符串函数，可在 JVM 离线测试里跑。
 */
public final class RomInfo {
    private RomInfo() {}

    public static final class Rom {
        public String name = "类原生 / AOSP";
        public String vendor = "原生 Android";
        public String group = "原生";
        public String background = "标准后台策略：电池优化白名单即可。";
        public String autoStartPage = "应用详情";
        public String tips = "";
    }

    /** 纯字符串分类，不依赖 Android 运行时，便于离线回归。 */
    public static Rom classify(String brand, String manufacturer,
                               String display, String fingerprint,
                               String romVersion) {
        String b = lower(brand), m = lower(manufacturer);
        String d = lower(display), f = lower(fingerprint), v = lower(romVersion);
        String all = b + " " + m + " " + d + " " + f + " " + v;
        Rom r = new Rom();

        if (b.contains("vivo") || m.contains("vivo") || b.contains("iqoo")
                || all.contains("originos") || v.contains("origin")) {
            r.name = "OriginOS";
            r.vendor = "vivo / iQOO";
            r.group = "国产深度定制";
            r.background = "除电池白名单外，必须单独允许「自启动」+「关联启动」，"
                    + "并在多任务卡片下拉锁定；否则锁屏几分钟后前台服务仍可能被回收。";
            r.autoStartPage = "i管家 → 权限管理 → 自启动";
            r.tips = "OriginOS 5~7 的高级功能（绕封杀 / 功能守护者）需要 ADB 或 Shizuku，"
                    + "本 App 不需要这些权限，走的是标准前台服务。";
        } else if (b.contains("xiaomi") || b.contains("redmi") || m.contains("xiaomi")
                || all.contains("miui") || all.contains("hyperos")) {
            r.name = all.contains("hyperos") ? "HyperOS" : "MIUI";
            r.vendor = "小米 / Redmi";
            r.group = "国产深度定制";
            r.background = "需要「自启动」+「省电策略：无限制」；"
                    + "MIUI 会在锁屏后清理后台，建议同时锁定多任务卡片。";
            r.autoStartPage = "安全中心 → 授权管理 → 自启动管理";
            r.tips = "若通知不显示，检查「通知管理 → 悬浮通知 / 锁屏通知」是否被关。";
        } else if (b.contains("huawei") || b.contains("honor") || m.contains("huawei")
                || all.contains("emui") || all.contains("harmony") || all.contains("magicui")) {
            r.name = all.contains("harmony") ? "HarmonyOS" :
                     all.contains("magicui") ? "MagicUI" : "EMUI";
            r.vendor = "华为 / 荣耀";
            r.group = "国产深度定制";
            r.background = "「启动管理」需手动改为「手动管理」并勾选允许自启动 / 关联启动 / 后台活动。";
            r.autoStartPage = "手机管家 → 应用启动管理";
            r.tips = "「睡眠待机优化」会断网，长时间挂隧道建议把本 App 加入保护名单。";
        } else if (b.contains("oppo") || b.contains("realme") || b.contains("oneplus")
                || m.contains("oppo") || all.contains("coloros")) {
            r.name = "ColorOS";
            r.vendor = "OPPO / 一加 / realme";
            r.group = "国产深度定制";
            r.background = "需要「允许自动启动」+「允许完全后台行为」，并关闭「睡眠待机优化」。";
            r.autoStartPage = "权限与隐私 → 自启动管理";
            r.tips = "ColorOS 的「电池优化」页面路径各版本不同，"
                    + "本 App 会直接弹系统确认框，不用自己找。";
        } else if (b.contains("meizu") || m.contains("meizu") || all.contains("flyme")) {
            r.name = "Flyme";
            r.vendor = "魅族";
            r.group = "国产深度定制";
            r.background = "需要在「手机管家 → 权限管理 → 后台管理」里允许后台运行。";
            r.autoStartPage = "手机管家 → 后台管理";
            r.tips = "";
        } else if (b.contains("samsung") || m.contains("samsung") || all.contains("oneui")) {
            r.name = "One UI";
            r.vendor = "三星";
            r.group = "国际厂商定制";
            r.background = "把本 App 加入「永不休眠应用程序」和「不受监控的应用程序」。";
            r.autoStartPage = "设置 → 电池 → 后台使用限制";
            r.tips = "";
        } else if (all.contains("lineage") || all.contains("aosp")
                || all.contains("pixel") || b.contains("google")) {
            r.name = "类原生 / AOSP";
            r.vendor = "原生 Android";
            r.group = "原生";
            r.background = "标准后台策略：加入电池优化白名单即可，无需自启动白名单。";
            r.autoStartPage = "应用详情";
            r.tips = "";
        }
        return r;
    }

    /** 只读系统属性，取不到返回空串（不申请权限、不读隐私）。 */
    public static String prop(String key) {
        try {
            Class<?> cl = Class.forName("android.os.SystemProperties");
            return (String) cl.getMethod("get", String.class).invoke(null, key);
        } catch (Throwable t) { return ""; }
    }

    public static Rom detect(Context c) {
        String b = Build.BRAND == null ? "" : Build.BRAND;
        String m = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER;
        String d = Build.DISPLAY == null ? "" : Build.DISPLAY;
        String f = Build.FINGERPRINT == null ? "" : Build.FINGERPRINT;
        String v = prop("ro.vivo.os.version") + " " + prop("ro.build.version.oplusrom")
                + " " + prop("ro.miui.ui.version.name") + " " + prop("ro.build.version.emui");
        return classify(b, m, d, f, v);
    }

    /** 四层架构说明：每层给「是什么 / 你这台的值 / 为什么重要」。 */
    public static String[][] layers(Context c, String abis, String primaryAbi,
                                    String kernelVersion, boolean cpolarKernel,
                                    String model, int sdk) {
        Rom rom = detect(c);
        return new String[][]{
            {"① CPU / ABI 架构", "硬件指令集",
             abis + "（首选 " + primaryAbi + "）",
             "决定装哪个 APK：arm64-v8a 用官方魔改内核包；"
             + "armeabi-v7a / x86 只能用标准 frp 内核。"
             + "\n" + abiAdvice(primaryAbi, model)},
            {"② 内核架构", "frpc 程序形态",
             (cpolarKernel ? "Cpolar 官方魔改内核" : "标准 fatedier/frp 内核")
                     + " · " + kernelVersion,
             cpolarKernel
                 ? "官方魔改版自己从 Cpolar API 拉配置，只认 -c 一个参数，"
                   + "INI 格式、不支持 TOML / --strict-config，也不能附加 -u/-p 之类 flag。"
                   + "\n日志里「启动参数 -c <配置文件>」就是这个意思。"
                 : "标准 frp 支持 TOML 与 --strict-config，但拿不到 Cpolar 的隧道认证，"
                   + "可能出现 login to the server failed。"},
            {"③ ROM 架构 / 系统形态", "厂商定制层",
             rom.vendor + " · " + rom.name + " · Android " + sdk,
             "ROM 决定「后台能不能活」，和 CPU 架构完全是两回事。"
             + "\n本机策略：" + rom.background
             + "\n自启动入口：" + rom.autoStartPage
             + (rom.tips.length() > 0 ? "\n补充：" + rom.tips : "")},
            {"④ 启动器架构（全新架构）", "本 App 的应用层方案",
             "免 Root · 前台服务 specialUse · v2 API + OAuth · UID 流量",
             "「全新架构」指的是这套启动器本身，不是 ROM 也不是 CPU："
             + "\n· 免 Root，直接执行打包进 APK 的 libfrpc.so，不需要 Termux / 第三方 frpc"
             + "\n· 前台服务（foregroundServiceType=specialUse）常驻，通知栏显示状态与流量"
             + "\n· v2 API + OAuth 登录一次、静默续期；配置从面板拉取"
             + "\n· 自绘通知图标、20+ 套主题、液态玻璃 UI、快捷设置磁贴"
             + "\n· UID 流量统计只按本应用计数，不读别的应用数据"
             + "\n对比旧方案：Root / Termux 手动跑 frpc、无前台服务、无状态通知、无自动重连。"},
        };
    }

    /**
     * v2.9：按真实首选 ABI 给结论，不再无条件写「是 64 位 ARM」。
     * 之前 x86 / 32 位 ARM 设备也会看到这句错话；机型名同样是运行时传进来的。
     */
    public static String abiAdvice(String abi, String model) {
        String a = lower(abi);
        String dev = "你这台 " + model;
        if (a.contains("arm64") || a.contains("aarch64"))
            return dev + " 的首选 ABI 是 " + abi + "（64 位 ARM），"
                    + "装 arm64 版 APK 就能用官方魔改内核。";
        if (a.contains("armeabi"))
            return dev + " 是 32 位 ARM（" + abi + "），"
                    + "请装全架构 APK，用标准 frp 内核。";
        if (a.contains("x86"))
            return dev + " 是 x86（" + abi + "），"
                    + "请装全架构 APK，用标准 frp 内核；模拟器也常是这个值。";
        return dev + " 的首选 ABI 是 " + abi
                + "，若不是 arm64-v8a 请装全架构 APK。";
    }

    private static String lower(String s) { return s == null ? "" : s.toLowerCase(); }
}
