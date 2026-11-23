package net.lyu323.cpolar;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 公网地址提取（两条通道，都不需要登录官方面板）。
 *
 * ① 进程 stdout：cpolar 启动后会打印
 *      Forwarding  https://xxxx.cpolar.top -> http://localhost:8080
 *      Forwarding  tcp://1.tcp.vip.cpolar.cn:20779 -> tcp://127.0.0.1:22
 * ② 本地检查接口：二进制内置 http://127.0.0.1:4040/api/tunnels
 *      （strings 里可见 %s/api/tunnels 与 127.0.0.1:4040），
 *      返回类似 ngrok 的 JSON，含 public_url / name / proto。
 *
 * 全部用正则，不依赖 org.json，离线可测。
 */
public final class CpolarUrls {
    private CpolarUrls() {}

    public static final String API_4040 = "http://127.0.0.1:4040/api/tunnels";
    public static final String WEB_9200 = "http://127.0.0.1:9200/";
    public static final String WEB_4040 = "http://127.0.0.1:4040/";

    public static final class U {
        public String url = "", name = "", proto = "", local = "";
        public U(String url, String name, String proto, String local) {
            this.url = url; this.name = name; this.proto = proto; this.local = local;
        }
        public boolean web() { return url.startsWith("http://") || url.startsWith("https://"); }
    }

    private static final Pattern FWD = Pattern.compile(
            "(?i)Forwarding\\s+((?:https?|tcp|udp)://[^\\s]+)\\s*->\\s*(\\S+)");

    /** 解析一行 stdout，命中返回 U，否则 null。 */
    public static U parseForwarding(String line) {
        if (line == null) return null;
        Matcher m = FWD.matcher(line);
        if (!m.find()) return null;
        String url = m.group(1);
        String local = m.group(2);
        String proto = url.startsWith("tcp://") ? "tcp"
                : url.startsWith("udp://") ? "udp"
                : url.startsWith("https://") ? "https" : "http";
        return new U(url, "", proto, local);
    }

    /** 从 /api/tunnels 的 JSON 里提取全部公网地址。 */
    public static List<U> parseApi(String json) {
        List<U> out = new ArrayList<U>();
        if (json == null) return out;
        // 每个 tunnel 对象里取 public_url / name / proto / addr（字段名不同版本略有差异）
        Matcher obj = Pattern.compile("\\{(?:[^{}]|\\{[^{}]*\\})*\\}").matcher(json);
        while (obj.find()) {
            String block = obj.group();
            String url = str(block, "public_url", "url", "PublicUrl");
            if (url.length() == 0 || !(url.startsWith("http") || url.startsWith("tcp")
                    || url.startsWith("udp"))) continue;
            String name = str(block, "name", "Name", "tunnel_name");
            String proto = str(block, "proto", "Proto", "protocol");
            if (proto.length() == 0)
                proto = url.startsWith("https") ? "https"
                      : url.startsWith("tcp") ? "tcp" : "http";
            String local = str(block, "addr", "local_addr", "Addr", "config.addr");
            out.add(new U(url, name, proto, local));
        }
        // 兼容 {"tunnels":[...]} 里只有 URL 数组的极简返回
        if (out.isEmpty()) {
            Matcher m = Pattern.compile("\"(?:public_url|url)\"\\s*:\\s*\"(https?://[^\"]+|tcp://[^\"]+)\"")
                    .matcher(json);
            while (m.find()) out.add(new U(m.group(1), "", "", ""));
        }
        return out;
    }

    private static String str(String s, String... keys) {
        for (String k : keys) {
            Matcher m = Pattern.compile("\"" + Pattern.quote(k)
                    + "\"\\s*:\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE).matcher(s);
            if (m.find()) return m.group(1).trim();
        }
        return "";
    }

    /** 去重合并（stdout 与 API 可能拿到同一条）。 */
    public static List<U> merge(List<U> a, List<U> b) {
        List<U> out = new ArrayList<U>();
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        for (List<U> src : new List[]{a, b}) {
            if (src == null) continue;
            for (U u : src) if (seen.add(u.url)) out.add(u);
        }
        return out;
    }

    /** 序列化到 sp（本机缓存，供 UI 显示）。 */
    public static String serialize(List<U> l) {
        StringBuilder b = new StringBuilder();
        if (l != null) for (U u : l)
            b.append(u.url).append('|').append(u.name).append('|').append(u.proto)
             .append('|').append(u.local).append('\n');
        return b.toString();
    }

    public static List<U> deserialize(String s) {
        List<U> out = new ArrayList<U>();
        if (s == null) return out;
        for (String ln : s.split("\\n")) {
            String[] p = ln.split("\\|", -1);
            if (p.length < 1 || p[0].length() == 0) continue;
            out.add(new U(p[0], p.length > 1 ? p[1] : "",
                    p.length > 2 ? p[2] : "", p.length > 3 ? p[3] : ""));
        }
        return out;
    }

    public static String asText(List<U> l) {
        StringBuilder b = new StringBuilder("Cpolar 公网地址\n");
        if (l == null || l.isEmpty()) return b.append("（暂无，启动隧道后自动显示）").toString();
        for (U u : l) b.append(u.proto).append("  ").append(u.url).append('\n');
        return b.toString();
    }
}
