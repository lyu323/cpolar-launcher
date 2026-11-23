package net.lyu323.cpolar;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 免责声明 v1.0 —— 按事实写，不谎称「完全不联网」。
 *
 * 与 ChmlFrp 启动器不同，这里必须额外讲清一件重要的事：
 * APK 里打包的不是开源内核，而是 **cpolar 官方闭源客户端二进制**，
 * 它来自 GitHub 第三方仓库内附的官方 Linux ARM 发布包（2022 年版）。
 * 版权归 cpolar 官方，本启动器只是把它当作子进程启动并管理。
 */
public final class Disclaimer {
    private Disclaimer() {}

    public static final String VERSION = "1.1";
    /** 展示用；实际跳转统一交给 QqHelper（直达官方加好友页）。 */
    public static final String QQ_URL = QqHelper.QQ_URL;

    public static final String BINARY_FROM =
            "GitHub 仓库 cmilove92/cpolar 内附的官方 cpolar Linux ARM 客户端\n"
          + "（ELF 32-bit ARM · 静态 Go · 2022 年构建，版本较旧）";

    public static final String SHORT =
        "本应用是第三方本地启动器，不是 cpolar 官方客户端，与官方无隶属关系。\n\n"
      + "· App 本身：只在你手机本地生成 cpolar.yml、启动/停止客户端进程、显示公网地址，"
      + "不提供、不托管、不自行中转任何服务器。\n"
      + "· 内含二进制：APK 里打包的是 cpolar 官方闭源 Linux ARM 客户端（见下方来源），"
      + "版权归官方；本 App 不含该客户端源码，也不修改它。\n"
      + "· 网络行为：客户端会连接 cpolar 官方服务（api.cpolar.com、cpolard.cpolar.com:4443、"
      + "官方面板与分配的 *.cpolar 域名），并在本机开 4040 / 9200 管理端口。"
      + "除此之外本 App 不向任何第三方地址发送数据，无广告/统计/推送 SDK。\n"
      + "· 更新检查：仅在你手动点「检查更新」时访问 GitHub 公开 API 读取本项目 Release 版本信息，"
      + "只读取、不上传任何数据；不点不联网，不自动下载或安装。\n"
      + "· 本机数据：authtoken、配置、日志、地址记录只存本机，卸载即清除；"
      + "不读 IMEI / MAC / 位置 / 通讯录 / 应用列表 / 剪贴板。\n"
      + "· 免费版：随机公网域名约 24 小时变化、带宽约 1M，付费固定域名需自行在官方面板配置。\n"
      + "· 风险自担：隧道等于把本机端口开放到公网，只映射必要服务并设强密码。";

    public static String full() {
        StringBuilder b = new StringBuilder();
        b.append("一、应用性质\n")
         .append("本应用（下称「本启动器」）是第三方开发的 Android 本地工具，"
                 + "仅用于在用户设备本机启动 cpolar 客户端进程、管理其 YAML 配置并展示公网地址。"
                 + "本启动器不是 cpolar 官方客户端，与 cpolar / bestExpresser 及其运营方"
                 + "无任何隶属、授权或合作关系。\n\n");

        b.append("二、内含的官方闭源二进制\n")
         .append("APK 的 lib/armeabi-v7a/libcpolar.so 是 cpolar 官方 Linux ARM 客户端。\n")
         .append("来源：").append(BINARY_FROM).append("。\n")
         .append("该二进制为闭源软件，版权与商标归 cpolar 官方所有；"
                 + "本启动器不提供其源码、不做逆向、不做修改，仅按官方命令行参数调用"
                 + "（start-all / -config / -log=stdout 等）。"
                 + "重新分发请遵守 cpolar 官方服务条款与下载页面的许可说明。\n")
         .append("由于该包构建于 2022 年，可能与当前官方服务端不兼容；"
                 + "设置页提供「导入自定义内核」，可把官网下载的最新 ARM/ARM64 客户端替换进来。\n\n");

        b.append("三、本启动器只负责本地运行\n")
         .append("· 不提供、不部署、不托管、不租用任何服务器或节点，不自行中转流量；\n")
         .append("· 不代管账号，不代签，不绕过任何官方鉴权或人机验证；\n")
         .append("· authtoken 由用户在官方面板「验证」页自行复制，写入本机 cpolar.yml，"
                 + "不出现在命令行参数里，不上传、不共享；\n")
         .append("· 不申请定位、通讯录、电话、短信、相机、麦克风、外部存储权限，"
                 + "不读取设备标识符或账号列表。\n\n");

        b.append("四、唯一的外部连接\n")
         .append("为使隧道可用，官方客户端会且仅会连接：\n")
         .append("· api.cpolar.com（账号/令牌/隧道接口）\n")
         .append("· cpolard.cpolar.com:4443（隧道控制通道）\n")
         .append("· dashboard.cpolar.com（仅由浏览器跳转，App 自身不请求）\n")
         .append("· 分配给你的 *.cpolar.top / *.cpolar.cn / *.cpolar.io 公网域名\n")
         .append("· 本机 127.0.0.1:4040（隧道检查接口）与 127.0.0.1:9200（Web UI）\n")
         .append("唯一的例外是手动触发的更新检查：点「检查更新」时访问 api.github.com 的公开 "
                 + "Release 接口，仅 GET 版本信息，不发送任何本机数据，不自动下载、不自动安装；"
                 + "不点不联网。\n")
         .append("本启动器自身只访问本机 4040 接口读取地址，不主动访问任何第三方服务器。"
                 + "不含广告、统计、崩溃上报或推送 SDK。\n\n");

        b.append("五、风险与责任\n")
         .append("· 启动隧道等同于将本机指定端口暴露到公网，请只映射必要服务、"
                 + "设置高强度密码；\n")
         .append("· 免费版随机域名会变化、带宽较小、可能空闲回收，"
                 + "节点可用性、套餐规则、账号状态均由官方决定，本启动器不作可用性承诺；\n")
         .append("· 因使用本启动器或 cpolar 服务导致的账号异常、数据泄露、服务中断、"
                 + "流量费用、合规问题，由使用者自行承担；\n")
         .append("· 本启动器按「现状」提供，不附带任何明示或默示担保。\n\n");

        b.append("六、开源与许可\n")
         .append("本启动器自身为原生 Java 编写，零第三方依赖；"
                 + "内含的 cpolar 客户端为闭源软件，请遵守其官方条款。\n\n");
        b.append("继续使用即表示你已阅读并同意以上条款。");
        return b.toString();
    }

    public static String[] hosts() {
        return new String[]{
            "api.cpolar.com            官方账号 / 令牌 / 隧道接口",
            "cpolard.cpolar.com:4443   官方隧道控制通道",
            "dashboard.cpolar.com      官方面板（仅浏览器跳转）",
            "*.cpolar.top / .cn / .io  分配给你的公网隧道域名",
            "127.0.0.1:4040           本机检查接口（读取公网地址）",
            "127.0.0.1:9200           本机 Web UI（可选开启）",
            "api.github.com          检查更新：仅手动点按钮时读取公开 Release 信息",
            "github.com              项目主页 / Releases 下载（仅浏览器跳转）",
        };
    }

    public static boolean agreed(Context c) {
        return sp(c).getString("disclaimerVersion", "").equals(VERSION);
    }
    public static void agree(Context c) {
        sp(c).edit().putString("disclaimerVersion", VERSION)
                .putLong("disclaimerAt", System.currentTimeMillis()).commit();
    }
    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("cpolar", Context.MODE_PRIVATE);
    }
}
