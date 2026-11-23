package net.cpolar.mobile;

/** 流量/速度单位格式化：B → KB → MB → GB，纯 Java 便于离线验证。 */
public final class Units {
    private Units() {}

    /** binary=true 用 1024 进制，false 用 1000 进制；标签统一 B/KB/MB/GB。 */
    public static String bytes(double b, boolean binary) {
        if (b < 0 || Double.isNaN(b) || Double.isInfinite(b)) b = 0;
        double base = binary ? 1024d : 1000d;
        if (b < base) return (long) Math.floor(b) + " B";
        double kb = b / base;
        if (kb < base) return one(kb) + " KB";
        double mb = kb / base;
        if (mb < base) return two(mb) + " MB";
        double gb = mb / base;
        if (gb < base) return two(gb) + " GB";
        return two(gb / base) + " TB";
    }

    public static String speed(double bytesPerSec, boolean binary) {
        return bytes(bytesPerSec, binary) + "/s";
    }

    /** 紧凑格式，用于顶部状态栏：数字和单位分开更省地方时直接用这个。 */
    public static String speedCompact(double bytesPerSec, boolean binary) {
        return speed(bytesPerSec, binary);
    }

    private static String one(double v) {
        return String.format(java.util.Locale.US, "%.1f", v);
    }

    private static String two(double v) {
        return String.format(java.util.Locale.US, "%.2f", v);
    }
}
