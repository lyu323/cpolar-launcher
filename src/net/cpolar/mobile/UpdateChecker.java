package net.cpolar.mobile;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * v1.1.0：连接本项目 GitHub 仓库检查更新。
 *
 * 设计边界（与免责声明一致）：
 *   · 只在用户主动点「检查更新」时联网，启动 / 后台 / 隧道运行期间**不自动联网**；
 *   · 只 GET api.github.com 的公开 Release 信息，纯读取，不上传任何设备 / 账号 / 令牌数据；
 *   · 不内置任何令牌，不做自动下载、不静默安装，发现新版只给 GitHub 页面与下载直链；
 *   · 解析用正则手写，不依赖 org.json，可离线单元测试。
 *
 * 数据源：https://api.github.com/repos/lyu323/cpolar-launcher/releases/latest
 */
public final class UpdateChecker {
    private UpdateChecker() {}

    /** 本 App 对应的开源仓库（各连各的，不互相混用）。 */
    public static final String REPO = "lyu323/cpolar-launcher";
    public static final String REPO_URL = "https://github.com/" + REPO;
    public static final String RELEASES_URL = REPO_URL + "/releases";
    public static final String LATEST_URL = RELEASES_URL + "/latest";
    public static final String API_LATEST =
            "https://api.github.com/repos/" + REPO + "/releases/latest";
    public static final String STAR_URL = REPO_URL;
    public static final String CHANGELOG_URL = REPO_URL + "/blob/main/CHANGELOG.md";
    public static final String LICENSE_URL = REPO_URL + "/blob/main/LICENSE";

    /** 同账号下的另一个项目（只作展示入口，不用于本 App 的更新检查）。 */
    public static final String OTHER_REPO = "lyu323/chmlfrp-launcher";
    public static final String OTHER_REPO_URL = "https://github.com/" + OTHER_REPO;

    /** 当前版本，必须与 AndroidManifest.xml / build.sh 的 versionName 一致。 */
    public static final String APP_VERSION = "1.1.0";

    public static final class Info {
        public boolean ok = false;          // 请求与解析是否成功
        public boolean newer = false;       // 远端版本是否比本机新
        public String tag = "";             // v3.1.0
        public String name = "";            // Release 标题
        public String body = "";            // 更新说明
        public String htmlUrl = "";         // Release 页面
        public String apkUrl = "";          // APK 下载直链
        public String sourceUrl = "";       // 源码 zip 直链
        public String error = "";           // 失败原因（说人话）
        public String current = APP_VERSION; // 本次比较所用的本机版本

        public String status() {
            if (!ok) return "检查失败：" + error;
            if (newer) return "发现新版本 " + tag + " · 当前 " + current;
            return "当前已是最新版本：" + current;
        }
    }

    // ---------------------------------------------------------------- 纯逻辑（可离线测）

    /** 去掉 v / V 前缀与空白，只留数字点分段。 */
    public static String normalize(String v) {
        if (v == null) return "";
        String s = v.trim();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        return s.trim();
    }

    /** 语义化版本比较：a>b 返回 1，a<b 返回 -1，相等 0。非数字段按 0 处理。 */
    public static int compare(String a, String b) {
        String[] x = normalize(a).split("\\."), y = normalize(b).split("\\.");
        int n = Math.max(x.length, y.length);
        for (int i = 0; i < n; i++) {
            int p = num(i < x.length ? x[i] : "0");
            int q = num(i < y.length ? y[i] : "0");
            if (p > q) return 1;
            if (p < q) return -1;
        }
        return 0;
    }

    private static int num(String s) {
        try { return Integer.parseInt(s.replaceAll("[^0-9]", "")); }
        catch (Exception e) { return 0; }
    }

    /** 手写取 JSON 字符串：正确处理 \\" \\\\ \\n \\r \\t 等转义，不依赖 org.json。 */
    public static String jsonString(String json, String key) {
        if (json == null) return "";
        int k = json.indexOf("\"" + key + "\"");
        if (k < 0) return "";
        int c = json.indexOf(':', k + key.length() + 2);
        if (c < 0) return "";
        int q = json.indexOf('"', c + 1);
        if (q < 0) return "";
        StringBuilder b = new StringBuilder();
        for (int i = q + 1; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '"') break;
            if (ch == '\\' && i + 1 < json.length()) {
                char n = json.charAt(++i);
                if (n == 'n') b.append('\n');
                else if (n == 'r') b.append('\r');
                else if (n == 't') b.append('\t');
                else if (n == 'u' && i + 4 < json.length()) {
                    try { b.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16)); }
                    catch (Exception e) { b.append('?'); }
                    i += 4;
                } else b.append(n);
            } else b.append(ch);
        }
        return b.toString();
    }

    /** 收集全部 browser_download_url（不误判其它字段）。 */
    public static List<String> downloadUrls(String json) {
        List<String> out = new ArrayList<String>();
        if (json == null) return out;
        Matcher m = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"")
                .matcher(json);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    /** 挑最合适的 APK：优先 arm64，其次任意 .apk。 */
    public static String pickApk(List<String> urls) {
        String any = "";
        for (String u : urls) {
            if (!u.endsWith(".apk")) continue;
            if (any.length() == 0) any = u;
            if (u.contains("arm64")) return u;
        }
        return any;
    }

    public static String pickSource(List<String> urls) {
        for (String u : urls) if (u.endsWith(".zip") && u.contains("source")) return u;
        for (String u : urls) if (u.endsWith(".zip")) return u;
        return "";
    }

    /** 解析 GitHub /releases/latest 的响应体。 */
    public static Info parse(String json, String currentVersion) {
        Info i = new Info();
        if (json == null || json.length() == 0) { i.error = "响应为空"; return i; }
        i.tag = jsonString(json, "tag_name");
        if (i.tag.length() == 0) {
            String msg = jsonString(json, "message");
            i.error = msg.length() > 0 ? "GitHub 返回：" + msg : "未找到 Release 信息";
            return i;
        }
        i.name = jsonString(json, "name");
        i.body = jsonString(json, "body");
        i.htmlUrl = jsonString(json, "html_url");
        if (i.htmlUrl.length() == 0) i.htmlUrl = LATEST_URL;
        List<String> urls = downloadUrls(json);
        i.apkUrl = pickApk(urls);
        i.sourceUrl = pickSource(urls);
        i.current = currentVersion;
        i.ok = true;
        i.newer = compare(i.tag, currentVersion) > 0;
        return i;
    }

    public static Info parse(String json) { return parse(json, APP_VERSION); }

    // ---------------------------------------------------------------- 网络（仅手动触发）

    /** 同步 GET 公开 API；调用方必须放在子线程。 */
    public static String fetch() throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(API_LATEST).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(10000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "CpolarLauncher/1.1.0");
            c.setRequestProperty("Accept", "application/vnd.github+json");
            c.connect();
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            if (in != null) {
                byte[] buf = new byte[4096];
                int n, total = 0;
                while ((n = in.read(buf)) > 0 && total < 262144) {
                    bos.write(buf, 0, n); total += n;
                }
                in.close();
            }
            String body = new String(bos.toByteArray(), "UTF-8");
            if (code == 403) return "{\"message\":\"GitHub API 限流（每小时 60 次），请稍后再试\"}";
            if (code == 404) return "{\"message\":\"该仓库暂无正式 Release\"}";
            if (code >= 400) return "{\"message\":\"HTTP " + code + "\"}";
            return body;
        } finally {
            if (c != null) try { c.disconnect(); } catch (Exception ignored) {}
        }
    }

    public interface Callback { void done(Info info); }

    /** 异步检查，结果回调到主线程。 */
    public static void checkAsync(final Callback cb) {
        final Handler h = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override public void run() {
                final Info r;
                try { r = parse(fetch(), APP_VERSION); }
                catch (final Exception e) {
                    h.post(new Runnable() {
                        @Override public void run() {
                            Info f = new Info();
                            f.error = friendly(e);
                            cb.done(f);
                        }
                    });
                    return;
                }
                h.post(new Runnable() {
                    @Override public void run() { cb.done(r); }
                });
            }
        }, "update-check").start();
    }

    private static String friendly(Exception e) {
        String m = String.valueOf(e);
        if (m.contains("UnknownHost") || m.contains("Unable to resolve") || m.contains("ENETUNREACH"))
            return "网络不可用，无法连接 GitHub";
        if (m.contains("timed out") || m.contains("SocketTimeout"))
            return "连接 GitHub 超时";
        if (m.contains("SSL") || m.contains("Cert"))
            return "证书校验失败";
        return "无法连接 GitHub，可手动打开 Releases 页";
    }

    /** 用浏览器打开 GitHub 页面；不指定包名，交给系统。 */
    public static boolean open(Context c, String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (Exception e) { return false; }
    }
}
