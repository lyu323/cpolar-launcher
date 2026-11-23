package net.cpolar.mobile;

import android.content.SharedPreferences;

/** 通知栏展开区可显示的字段，用位掩码保存，供设置页与前台服务共用。 */
public final class NotifyFields {
    private NotifyFields() {}

    public static final String[] KEYS = {
            "state",      // 状态 + 运行时长
            "tunnel",     // 隧道名 + ID
            "node",       // 节点 + 协议
            "local",      // 本地服务地址
            "addr",       // 连接地址 / 域名
            "kernel",     // 内核版本
            "guard",      // 守护 / 重连上限
            "net",        // 当前网络类型
            "theme",      // 当前主题
            "realtime",   // 实时速度
            "session",    // 本次会话流量
            "total",      // 历史累计流量
            "peak",       // 峰值速度
            "conn",       // 连接数 / 面板状态
            "units",      // 单位进制 / 统计口径
    };

    public static final String[] LABELS = {
            "运行状态 · 已运行时长",
            "隧道名 · ID",
            "节点 · 协议",
            "本地服务地址",
            "连接地址 / 域名",
            "内核版本",
            "守护 · 重连上限",
            "当前网络（WiFi / 蜂窝）",
            "当前主题",
            "实时上/下行速度",
            "本次会话流量",
            "历史累计流量",
            "峰值速度",
            "连接数 · 面板状态",
            "单位进制 · 统计说明",
    };

    public static int count() { return KEYS.length; }

    public static long bit(int i) { return 1L << i; }

    public static long all() { return (1L << KEYS.length) - 1L; }

    /** 紧凑预设：状态 + 实时 + 本次。 */
    public static long compact() {
        return bit(0) | bit(9) | bit(10);
    }

    public static boolean has(long mask, int i) {
        return (mask & bit(i)) != 0;
    }

    public static boolean has(long mask, String key) {
        for (int i = 0; i < KEYS.length; i++) {
            if (KEYS[i].equals(key)) return has(mask, i);
        }
        return false;
    }

    /** 读到全 0 视为「未设置」回退全量，避免用户全勾掉后通知栏空白。 */
    public static long read(SharedPreferences sp) {
        long m = sp.getLong("notifyFields", all());
        return m == 0 ? all() : m;
    }

    public static long from(boolean[] checked) {
        long m = 0;
        for (int i = 0; i < KEYS.length && i < checked.length; i++) {
            if (checked[i]) m |= bit(i);
        }
        return m;
    }
}
