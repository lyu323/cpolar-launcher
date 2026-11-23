package net.cpolar.mobile;

/** 一条 cpolar 隧道（对应 cpolar.yml 里 tunnels 下的一个段）。 */
public final class CpolarTunnel {
    public String name = "";
    public String proto = "http";        // http / tcp / udp
    public String addr = "8080";         // 本地端口
    public String region = "cn";         // cn / cn_vip / us / hk
    public String hostname = "";         // 自定义域名（付费）
    public String subdomain = "";        // 二级子域名（付费）
    public String remoteAddr = "";       // 固定 TCP 地址（付费）
    public String inspect = "true";      // 是否开请求检查

    public CpolarTunnel() {}

    public CpolarTunnel(String name, String proto, String addr, String region) {
        this.name = name;
        this.proto = proto;
        this.addr = addr;
        this.region = region;
    }

    public String title() {
        return (name.length() > 0 ? name : "未命名") + "  ·  " + proto + "  ·  " + addr;
    }

    public String sub() {
        return "本地 127.0.0.1:" + addr + "   ·   地区 " + region
                + (hostname.length() > 0 ? "\n固定域名 " + hostname : "")
                + (subdomain.length() > 0 ? "\n子域名 " + subdomain : "")
                + (remoteAddr.length() > 0 ? "\n保留地址 " + remoteAddr : "");
    }

    /** 序列化成 YAML 段（缩进两空格，绝不用 TAB）。 */
    public String toYaml() {
        StringBuilder b = new StringBuilder();
        b.append("  ").append(safeName(name)).append(":\n");
        b.append("    addr: ").append(addr.trim()).append('\n');
        b.append("    proto: ").append(proto.trim()).append('\n');
        b.append("    region: ").append(region.trim()).append('\n');
        if (hostname.length() > 0) b.append("    hostname: ").append(hostname.trim()).append('\n');
        if (subdomain.length() > 0) b.append("    subdomain: ").append(subdomain.trim()).append('\n');
        if (remoteAddr.length() > 0) b.append("    remote_addr: ").append(remoteAddr.trim()).append('\n');
        if ("false".equals(inspect)) b.append("    inspect: false\n");
        return b.toString();
    }

    /** 隧道名只允许字母数字下划线中划线，避免 YAML 解析炸掉。 */
    public static String safeName(String s) {
        String t = s == null ? "" : s.trim().replaceAll("[^\\u4e00-\\u9fa5A-Za-z0-9_\\-]", "_");
        return t.length() > 0 ? t : "tunnel";
    }

    public boolean valid() {
        return name.trim().length() > 0
                && addr.trim().matches("[0-9]{1,5}")
                && Integer.parseInt(addr.trim()) > 0
                && Integer.parseInt(addr.trim()) < 65536
                && (proto.equals("http") || proto.equals("tcp") || proto.equals("udp"));
    }
}
