package net.lyu323.cpolar;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * cpolar.yml 的生成、解析与校验。
 *
 * 官方文档明确：YAML 缩进敏感、每级两个空格、一定不能用 TAB。
 * 这里不引入任何第三方 YAML 库，手写最小子集解析器，保证零依赖、可离线测试。
 *
 *   authtoken: xxxxxx
 *   tunnels:
 *     web:
 *       addr: 8080
 *       proto: http
 *       region: cn_vip
 *     ssh:
 *       addr: 22
 *       proto: tcp
 *       region: cn
 *
 * 启动方式：cpolar start-all -config=<yml> -log=stdout
 * 一个进程就能带多条隧道，对应历史版本里的「多隧道」需求。
 */
public final class CpolarConfig {
    private CpolarConfig() {}

    public static final String FILE = "cpolar.yml";

    public static File file(Context c) { return new File(c.getFilesDir(), FILE); }

    /** 生成完整 YAML。 */
    public static String build(String token, List<CpolarTunnel> tunnels) {
        StringBuilder b = new StringBuilder();
        b.append("# 由 Cpolar 启动器生成 · 仅保存在本机\n");
        b.append("authtoken: ").append(token == null ? "" : token.trim()).append('\n');
        b.append("tunnels:\n");
        if (tunnels != null) {
            for (CpolarTunnel t : tunnels) {
                if (t == null || !t.valid()) continue;
                b.append(t.toYaml());
            }
        }
        return b.toString();
    }

    /** 默认示例配置（两个隧道，官方安装后默认给的也是 web + ssh 两个）。 */
    public static String demo(String token) {
        List<CpolarTunnel> l = new ArrayList<CpolarTunnel>();
        l.add(new CpolarTunnel("web", "http", "8080", "cn_vip"));
        l.add(new CpolarTunnel("ssh", "tcp", "8022", "cn"));
        return build(token, l);
    }

    /** 校验：返回错误描述，空串表示通过。 */
    public static String validate(String text) {
        if (text == null || text.trim().length() == 0) return "配置为空";
        if (text.indexOf('\t') >= 0) return "检测到 TAB 键，YAML 不允许用 TAB 缩进，请改成空格";
        String[] lines = text.split("\\r?\\n", -1);
        boolean hasToken = false, hasTunnels = false;
        int tunnelCount = 0;
        for (String ln : lines) {
            if (ln.trim().length() == 0 || ln.trim().startsWith("#")) continue;
            if (ln.startsWith(" ")) {
                int n = 0;
                while (n < ln.length() && ln.charAt(n) == ' ') n++;
                if (n % 2 != 0) return "缩进必须是 2 个空格的倍数（第 " + n + " 个空格）";
                continue;
            }
            if (ln.startsWith("authtoken:")) hasToken = true;
            if (ln.startsWith("tunnels:")) hasTunnels = true;
            if (ln.startsWith("  ") && ln.trim().endsWith(":") && !ln.trim().startsWith("addr"))
                tunnelCount++;
        }
        if (!hasToken) return "缺少 authtoken 字段";
        if (!hasTunnels) return "缺少 tunnels 字段";
        return "";
    }

    /** 从 YAML 里取 authtoken（用于 UI 回填）。 */
    public static String tokenOf(String text) {
        if (text == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?m)^\\s*authtoken\\s*:\\s*[\"']?([^\"'\\s#]+)")
                .matcher(text);
        return m.find() ? m.group(1).trim() : "";
    }

    /** 解析出隧道列表，兼容两空格/四空格缩进。 */
    public static List<CpolarTunnel> parse(String text) {
        List<CpolarTunnel> out = new ArrayList<CpolarTunnel>();
        if (text == null) return out;
        CpolarTunnel cur = null;
        for (String raw : text.split("\\r?\\n", -1)) {
            String ln = raw.replace("\t", "  ");
            String s = ln.trim();
            if (s.length() == 0 || s.startsWith("#")) continue;
            int indent = 0;
            while (indent < ln.length() && ln.charAt(indent) == ' ') indent++;
            if (s.equals("tunnels:") || s.startsWith("authtoken:")) continue;
            if (indent == 2 && s.endsWith(":")) {
                cur = new CpolarTunnel();
                cur.name = s.substring(0, s.length() - 1).trim();
                out.add(cur);
                continue;
            }
            if (cur == null || indent < 4 || s.indexOf(':') < 0) continue;
            int p = s.indexOf(':');
            String k = s.substring(0, p).trim().toLowerCase();
            String v = s.substring(p + 1).trim().replaceAll("^[\"']|[\"']$", "");
            if (k.equals("addr")) cur.addr = v;
            else if (k.equals("proto")) cur.proto = v;
            else if (k.equals("region")) cur.region = v;
            else if (k.equals("hostname")) cur.hostname = v;
            else if (k.equals("subdomain")) cur.subdomain = v;
            else if (k.equals("remote_addr")) cur.remoteAddr = v;
            else if (k.equals("inspect")) cur.inspect = v;
        }
        // 只保留有效段
        List<CpolarTunnel> ok = new ArrayList<CpolarTunnel>();
        for (CpolarTunnel t : out) if (!t.name.isEmpty()) ok.add(t);
        return ok;
    }

    public static int count(String text) { return parse(text).size(); }

    public static void write(Context c, String text) throws Exception {
        FileOutputStream os = new FileOutputStream(file(c));
        os.write(text.getBytes("UTF-8"));
        os.close();
    }

    public static String read(Context c) {
        File f = file(c);
        if (!f.exists()) return "";
        try {
            FileInputStream is = new FileInputStream(f);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            is.close();
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Exception e) { return ""; }
    }

    /** 反序列化：本机 sp 里保存的隧道清单（纯文本行，简单可靠）。 */
    public static String serialize(List<CpolarTunnel> l) {
        StringBuilder b = new StringBuilder();
        for (CpolarTunnel t : l) {
            b.append(t.name).append('|').append(t.proto).append('|').append(t.addr)
             .append('|').append(t.region).append('|').append(t.hostname)
             .append('|').append(t.subdomain).append('|').append(t.remoteAddr)
             .append('\n');
        }
        return b.toString();
    }

    public static List<CpolarTunnel> deserialize(String s) {
        List<CpolarTunnel> out = new ArrayList<CpolarTunnel>();
        if (s == null) return out;
        for (String ln : s.split("\\n")) {
            String[] p = ln.split("\\|", -1);
            if (p.length < 4) continue;
            CpolarTunnel t = new CpolarTunnel(p[0], p[1], p[2], p[3]);
            if (p.length > 4) t.hostname = p[4];
            if (p.length > 5) t.subdomain = p[5];
            if (p.length > 6) t.remoteAddr = p[6];
            out.add(t);
        }
        return out;
    }
}
