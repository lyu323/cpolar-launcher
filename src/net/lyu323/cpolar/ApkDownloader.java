package net.lyu323.cpolar;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

/**
 * v1.3.0：「本地下载」——从 GitHub Release 资产直下到 App 私有目录。
 *
 * 安全边界：
 *   · 只接受 https 且 host 在 GitHub 资产白名单内的地址，重定向后的 host 再校验一次；
 *   · 文件名白名单，防路径穿越；
 *   · 校验长度与 ZIP 魔数（PK），避免把错误页 / HTML 当 APK；
 *   · 不自动安装：下载完只把 content:// 交给系统安装界面，是否安装由用户确认。
 */
public final class ApkDownloader {
    private ApkDownloader() {}

    public static final String[] HOSTS = {
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "github-releases.githubusercontent.com",
        "githubusercontent.com",
    };

    public static boolean hostAllowed(String host) {
        if (host == null) return false;
        String h = host.toLowerCase();
        for (String a : HOSTS)
            if (h.equals(a) || h.endsWith("." + a)) return true;
        return false;
    }

    public static boolean allowed(String url) {
        try {
            URI u = new URI(url);
            if (!"https".equalsIgnoreCase(u.getScheme())) return false;
            if (!hostAllowed(u.getHost())) return false;
            String p = u.getPath();
            return p != null && (p.contains("/releases/download/") || p.endsWith(".apk"));
        } catch (Exception e) { return false; }
    }

    /** 与 ApkProvider.safeName 同口径；不引用 Android 类，便于离线测试。 */
    public static boolean safeName(String n) {
        return n != null && n.matches("[A-Za-z0-9._-]+\\.apk") && !n.contains("..");
    }

    public static String fileName(String url) {
        try {
            String p = new URI(url).getPath();
            if (p == null) return null;
            String n = p.substring(p.lastIndexOf('/') + 1);
            return safeName(n) ? n : null;
        } catch (Exception e) { return null; }
    }

    public interface Callback {
        void progress(long done, long total);
        void done(File file);
        void error(String msg);
    }

    public static void start(final Context c, final String url, final Callback cb) {
        if (!allowed(url)) { cb.error("下载地址不在 GitHub 资产白名单内"); return; }
        final String name = fileName(url);
        if (name == null) { cb.error("资产文件名不合法"); return; }
        final Handler h = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final File f = fetch(c, url, name, h, cb);
                    h.post(new Runnable() {
                        @Override public void run() { cb.done(f); }
                    });
                } catch (final Exception e) {
                    h.post(new Runnable() {
                        @Override public void run() { cb.error(friendly(e)); }
                    });
                }
            }
        }, "apk-download").start();
    }

    private static File fetch(Context c, String url, String name, Handler h,
                              final Callback cb) throws Exception {
        HttpURLConnection conn = null;
        File dir = ApkProvider.dir(c);
        File part = new File(dir, name + ".part");
        File out = new File(dir, name);
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(20000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("Accept", "application/octet-stream");
            conn.setRequestProperty("User-Agent", "CpolarLauncher/1.3.0");
            conn.connect();
            // 302 之后的最终 host 也必须属于 GitHub 资产 CDN
            if (!hostAllowed(conn.getURL().getHost()))
                throw new Exception("重定向到了非 GitHub 地址");
            int code = conn.getResponseCode();
            if (code != 200) throw new Exception("HTTP " + code);
            long total = conn.getHeaderFieldLong("Content-Length", -1L);
            InputStream in = conn.getInputStream();
            FileOutputStream fo = new FileOutputStream(part);
            byte[] buf = new byte[32768];
            long done = 0L, lastPost = 0L;
            int n;
            while ((n = in.read(buf)) > 0) {
                fo.write(buf, 0, n);
                done += n;
                if (done - lastPost > 262144L || done == total) {
                    lastPost = done;
                    final long d = done, tt = total;
                    h.post(new Runnable() {
                        @Override public void run() { cb.progress(d, tt); }
                    });
                }
            }
            fo.flush(); fo.close(); in.close();
            if (done < 1024L * 1024L) throw new Exception("文件过小，可能不是 APK");
            java.io.RandomAccessFile raf = new java.io.RandomAccessFile(part, "r");
            byte[] head = new byte[2];
            raf.read(head); raf.close();
            if (head[0] != 'P' || head[1] != 'K') throw new Exception("下载内容不是 APK（ZIP）");
            if (out.exists() && !out.delete()) throw new Exception("无法覆盖旧文件");
            if (!part.renameTo(out)) throw new Exception("写入失败");
            return out;
        } finally {
            if (conn != null) try { conn.disconnect(); } catch (Exception ignored) {}
        }
    }

    private static String friendly(Exception e) {
        String m = String.valueOf(e);
        if (m.contains("UnknownHost") || m.contains("ENETUNREACH") || m.contains("Unable to resolve"))
            return "网络不可用，无法下载";
        if (m.contains("timed out") || m.contains("SocketTimeout"))
            return "下载超时，请重试或换网络";
        if (m.contains("非 GitHub") || m.contains("白名单") || m.contains("不合法"))
            return m;
        return "下载失败：" + m;
    }
}
