package net.lyu323.cpolar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Cpolar 启动器前台服务。
 *
 * 沿用历史工程里验证过的做法：把官方客户端打包成 lib/<abi>/libcpolar.so，
 * 由系统解压到 nativeLibraryDir，再用 ProcessBuilder 直接执行
 * （Android 10+ 禁止从 filesDir 执行二进制，放 nativeLibraryDir 才能跑）。
 *
 * 启动命令（官方文档）：
 *   cpolar start-all -config=<cpolar.yml> -log=stdout [-dashboard=on]
 * 一个进程带多条隧道；不用 -daemon=on，否则进程脱离控制无法停止。
 */
public class CpolarService extends Service {

    public static final String ACTION_START = "net.lyu323.cpolar.SVC_START";
    public static final String ACTION_STOP = "net.lyu323.cpolar.SVC_STOP";
    public static final String ACTION_RESTART = "net.lyu323.cpolar.SVC_RESTART";
    public static final String ACTION_REFRESH = "net.lyu323.cpolar.SVC_REFRESH";

    public static final String CH_MAIN = "cpolar";
    public static final String CH_EVENT = "cpolar_event";
    public static final int NOTIFY_ID = 1;
    public static final int EVENT_ID = 2;

    private static volatile boolean running = false;
    private static volatile Process proc = null;
    private static volatile long startAt = 0L;
    private static volatile boolean userStopped = false;

    private Thread readerThread, pollThread;
    private volatile boolean polling = false;
    private final Handler h = new Handler(Looper.getMainLooper());

    public static boolean isRunning() { return running; }
    public static long startAt() { return startAt; }

    public static String stateLabel() {
        if (running) return "● 运行中";
        return "○ 未运行";
    }

    private SharedPreferences sp() {
        return getSharedPreferences("cpolar", Context.MODE_PRIVATE);
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        Ui.apply(Theme.resolve(this, sp().getInt("themeId", 0)));
        createChannels();
        startForeground(NOTIFY_ID, buildNotification());
    }

    @Override public int onStartCommand(Intent i, int flags, int startId) {
        String a = i == null ? "" : i.getAction();
        if (ACTION_STOP.equals(a)) { stopTunnel(); }
        else if (ACTION_RESTART.equals(a)) { stopTunnel(); h.postDelayed(new Runnable() {
            @Override public void run() { startTunnel(); }
        }, 600); }
        else if (ACTION_REFRESH.equals(a)) { refreshNotify(); }
        else if (ACTION_START.equals(a)) { startTunnel(); }
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        stopTunnel();
        super.onDestroy();
    }

    // ------------------------------------------------------------ 进程

    /** 优先使用用户导入的内核，其次用 APK 内置的官方 ARM 客户端。 */
    public static File binary(Context c) {
        File custom = new File(c.getFilesDir(), "bin/cpolar");
        if (custom.exists() && custom.length() > 100000) return custom;
        return new File(c.getApplicationInfo().nativeLibraryDir, "libcpolar.so");
    }

    public static String binaryInfo(Context c) {
        File f = binary(c);
        if (!f.exists()) return "未找到客户端二进制";
        boolean custom = f.getPath().contains("/bin/cpolar");
        return (custom ? "已导入自定义内核" : "内置官方 ARM 客户端")
                + "  ·  " + (f.length() / 1024 / 1024) + " MB";
    }

    private void startTunnel() {
        if (running) { toast("隧道已在运行"); return; }
        String yml = CpolarConfig.read(this);
        String err = CpolarConfig.validate(yml);
        if (err.length() > 0) {
            // 没有配置时用本机保存的 token 与隧道清单生成一份
            String token = sp().getString("token", "").trim();
            List<CpolarTunnel> list = CpolarConfig.deserialize(sp().getString("tunnels", ""));
            if (token.length() == 0 || list.isEmpty()) {
                append(this, "[启动] 中止：" + err + "（请先填写 Token 并添加隧道）");
                toast("配置不完整：" + err);
                refreshNotify();
                return;
            }
            yml = CpolarConfig.build(token, list);
            try { CpolarConfig.write(this, yml); } catch (Exception ignored) {}
        }
        File bin = binary(this);
        if (!bin.exists()) {
            append(this, "[启动] 中止：找不到 cpolar 客户端二进制（" + bin + "）");
            toast("缺少客户端，请在设置页导入内核");
            refreshNotify();
            return;
        }
        try { bin.setExecutable(true, false); } catch (Exception ignored) {}

        File cfg = CpolarConfig.file(this);
        List<String> cmd = new ArrayList<String>();
        cmd.add(bin.getAbsolutePath());
        cmd.add("start-all");
        cmd.add("-config=" + cfg.getAbsolutePath());
        cmd.add("-log=stdout");
        if (sp().getBoolean("webui", true)) cmd.add("-dashboard=on");

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(getFilesDir());
            pb.redirectErrorStream(true);
            pb.environment().put("HOME", getFilesDir().getAbsolutePath());
            pb.environment().put("TMPDIR", getCacheDir().getAbsolutePath());
            // Android 的 CA 证书不在 Go 默认搜索路径里，指向系统 cacerts
            pb.environment().put("SSL_CERT_DIR", "/system/etc/security/cacerts");
            pb.environment().put("SSL_CERT_FILE", "/system/etc/security/cacerts");
            pb.environment().put("CPOLAR_CHILD", "1");
            proc = pb.start();
            running = true;
            userStopped = false;
            startAt = System.currentTimeMillis();
            sp().edit().putBoolean("running", true).putLong("startAt", startAt)
                    .putString("exit", "").commit();
            append(this, "[启动] " + CpolarConfig.count(yml) + " 条隧道 · 命令 "
                    + cmd.toString().replace(sp().getString("token", ""), "[TOKEN]"));
            append(this, "[启动] 内核：" + binaryInfo(this));
            startReader();
            startPolling();
            refreshNotify();
        } catch (Exception e) {
            append(this, "[启动] 失败：" + e);
            toast("启动失败：" + e.getMessage());
            running = false;
            refreshNotify();
        }
    }

    private void stopTunnel() {
        userStopped = true;
        polling = false;
        Process p = proc;
        proc = null;
        if (p != null) {
            try { p.destroy(); } catch (Exception ignored) {}
        }
        if (readerThread != null) { try { readerThread.interrupt(); } catch (Exception ignored) {} }
        if (pollThread != null) { try { pollThread.interrupt(); } catch (Exception ignored) {} }
        running = false;
        startAt = 0;
        sp().edit().putBoolean("running", false).putString("exit", "user").commit();
        append(this, "[停止] 已请求停止（不会自动重连）");
        refreshNotify();
    }

    private void startReader() {
        final Process p = proc;
        readerThread = new Thread(new Runnable() {
            @Override public void run() {
                BufferedReader br = null;
                try {
                    br = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
                    String line;
                    while ((line = br.readLine()) != null) {
                        append(CpolarService.this, line);
                        CpolarUrls.U u = CpolarUrls.parseForwarding(line);
                        if (u != null) {
                            List<CpolarUrls.U> l = CpolarUrls.deserialize(sp().getString("urls", ""));
                            l.add(u);
                            saveUrls(CpolarUrls.merge(l, new ArrayList<CpolarUrls.U>()));
                            refreshNotify();
                        }
                    }
                } catch (Exception ignored) {
                } finally {
                    if (br != null) try { br.close(); } catch (Exception ignored) {}
                }
                int code = -1;
                try { code = p.waitFor(); } catch (Exception ignored) {}
                final int exit = code;
                h.post(new Runnable() {
                    @Override public void run() {
                        if (proc != p) return;   // 已被新进程替换
                        running = false;
                        proc = null;
                        sp().edit().putBoolean("running", false)
                                .putString("exit", String.valueOf(exit)).commit();
                        append(CpolarService.this, "[进程] 客户端已退出，code=" + exit);
                        if (!userStopped) {
                            postEvent("隧道已断开", "cpolar 客户端退出（code=" + exit
                                    + "），到日志页看原因");
                        }
                        refreshNotify();
                    }
                });
            }
        }, "cpolar-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    /** 轮询本机 4040 检查接口，比解析 stdout 更可靠（免费版域名会变）。 */
    private void startPolling() {
        polling = true;
        pollThread = new Thread(new Runnable() {
            @Override public void run() {
                while (polling && running) {
                    String json = get(CpolarUrls.API_4040);
                    if (json.length() > 0) {
                        List<CpolarUrls.U> api = CpolarUrls.parseApi(json);
                        if (!api.isEmpty()) {
                            List<CpolarUrls.U> old = CpolarUrls.deserialize(sp().getString("urls", ""));
                            saveUrls(CpolarUrls.merge(old, api));
                            refreshNotify();
                        }
                    }
                    try { Thread.sleep(6000); } catch (Exception e) { return; }
                }
            }
        }, "cpolar-poll");
        pollThread.setDaemon(true);
        pollThread.start();
    }

    private void saveUrls(List<CpolarUrls.U> l) {
        sp().edit().putString("urls", CpolarUrls.serialize(l))
                .putLong("urlsAt", System.currentTimeMillis()).commit();
    }

    private String get(String urlStr) {
        HttpURLConnection c = null;
        try {
            URL u = new URL(urlStr);
            c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(1500);
            c.setReadTimeout(1500);
            c.setRequestMethod("GET");
            if (c.getResponseCode() != 200) return "";
            InputStream is = c.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n, total = 0;
            while ((n = is.read(b)) > 0) {
                bos.write(b, 0, n);
                total += n;
                if (total > 65536) break;
            }
            is.close();
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Exception e) { return ""; }
        finally { if (c != null) c.disconnect(); }
    }

    // ------------------------------------------------------------ 通知

    private void createChannels() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel m = new NotificationChannel(CH_MAIN, "Cpolar 隧道",
                NotificationManager.IMPORTANCE_LOW);
        m.setDescription("常驻运行状态与公网地址");
        nm.createNotificationChannel(m);
        NotificationChannel e = new NotificationChannel(CH_EVENT, "隧道事件",
                NotificationManager.IMPORTANCE_DEFAULT);
        e.setDescription("客户端退出等可清除提醒");
        nm.createNotificationChannel(e);
    }

    private Notification buildNotification() {
        Ui.apply(Theme.resolve(this, sp().getInt("themeId", 0)));
        List<CpolarUrls.U> urls = CpolarUrls.deserialize(sp().getString("urls", ""));
        int tunnels = CpolarConfig.count(CpolarConfig.read(this));
        String state = running ? "● 运行中" : "○ 未运行";
        String title = "Cpolar · " + state + (tunnels > 1 ? " · " + tunnels + " 条隧道" : "");

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) b = new Notification.Builder(this, CH_MAIN);
        else b = new Notification.Builder(this);
        Icon icon = NotifyIcon.create(this);
        b.setSmallIcon(icon != null ? icon
                : Icon.createWithResource(this, android.R.drawable.stat_sys_upload));
        Intent back = new Intent(this, MainActivity.class).putExtra("fromNotify", true);
        PendingIntent pi = PendingIntent.getActivity(this, 0, back,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        String first = urls.isEmpty() ? (running ? "正在获取公网地址…" : "点「启动隧道」即可连接")
                : Privacy.url(this, urls.get(0).url);
        b.setContentTitle(title).setContentText(first).setContentIntent(pi)
                .setOngoing(running).setOnlyAlertOnce(true);

        StringBuilder big = new StringBuilder();
        big.append(state);
        if (running && startAt > 0)
            big.append(" · 已运行 ").append(fmt(System.currentTimeMillis() - startAt));
        big.append('\n');
        big.append("隧道  ").append(tunnels).append(" 条 · 地址 ").append(urls.size()).append(" 个\n");
        int show = Math.min(urls.size(), 4);
        for (int i = 0; i < show; i++)
            big.append(urls.get(i).proto).append("  ")
               .append(Privacy.url(this, urls.get(i).url)).append('\n');
        if (urls.size() > show) big.append("… 还有 ").append(urls.size() - show).append(" 个\n");
        if (!running) big.append("\n点此返回启动器，或点下方「▶ 启动隧道」");
        b.setStyle(new Notification.BigTextStyle()
                .setBigContentTitle(title).bigText(big.toString().trim()));

        if (sp().getBoolean("notifyThemeColor", true)) {
            b.setColor(Ui.BLUE);
            // 深色/玻璃主题下 colorized 会让动作文字失去对比度（历史版本踩过的坑）
            if (Build.VERSION.SDK_INT >= 26 && !Ui.DARK && !Ui.GLASS && !Privacy.silent(this))
                b.setColorized(true);
        }
        if (Build.VERSION.SDK_INT < 26 && Privacy.silent(this))
            b.setPriority(Notification.PRIORITY_MIN);
        b.setVisibility(Privacy.lock(this) ? Notification.VISIBILITY_PRIVATE
                : Notification.VISIBILITY_PUBLIC);

        if (Build.VERSION.SDK_INT >= 20) {
            b.addAction(new Notification.Action.Builder(
                    running ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                    running ? "停止" : "▶ 启动隧道",
                    act(running ? ActionReceiver.A_STOP : ActionReceiver.A_START, 1)).build());
            b.addAction(new Notification.Action.Builder(
                    android.R.drawable.ic_media_ff, running ? "⟳ 重启" : "看日志",
                    act(running ? ActionReceiver.A_RESTART : ActionReceiver.A_LOG, 2)).build());
            b.addAction(new Notification.Action.Builder(
                    android.R.drawable.ic_menu_save, "复制地址",
                    act(ActionReceiver.A_COPY, 3)).build());
        }
        return b.build();
    }

    private PendingIntent act(String a, int req) {
        return PendingIntent.getBroadcast(this, req,
                new Intent(this, ActionReceiver.class).setAction(a),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void refreshNotify() {
        try {
            NotificationManager nm = (NotificationManager)
                    getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify(NOTIFY_ID, buildNotification());
        } catch (Exception ignored) {}
    }

    private void postEvent(String t, String x) {
        if (!sp().getBoolean("notifyEvents", true)) return;
        try {
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) b = new Notification.Builder(this, CH_EVENT);
            else b = new Notification.Builder(this);
            Icon icon = NotifyIcon.create(this);
            b.setSmallIcon(icon != null ? icon
                    : Icon.createWithResource(this, android.R.drawable.stat_sys_upload));
            b.setContentTitle(t).setContentText(x).setAutoCancel(true)
                    .setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE)
                    .setContentIntent(PendingIntent.getActivity(this, 4,
                            new Intent(this, MainActivity.class).putExtra("page", 2),
                            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            NotificationManager nm = (NotificationManager)
                    getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify(EVENT_ID, b.build());
        } catch (Exception ignored) {}
    }

    private void toast(final String s) {
        h.post(new Runnable() {
            @Override public void run() {
                android.widget.Toast.makeText(CpolarService.this, s,
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static String fmt(long ms) {
        long s = ms / 1000;
        return String.format("%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
    }

    // ------------------------------------------------------------ 日志

    public static void append(Context c, String s) {
        if (s == null) return;
        try {
            File f = new File(c.getFilesDir(), "runtime.log");
            FileOutputStream os = new FileOutputStream(f, true);
            os.write((new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA)
                    .format(new java.util.Date()) + "  " + s + "\n").getBytes("UTF-8"));
            os.close();
        } catch (Exception ignored) {}
    }

    public static String readLog(Context c) {
        File f = new File(c.getFilesDir(), "runtime.log");
        if (!f.exists()) return "";
        try {
            FileInputStream is = new FileInputStream(f);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = is.read(b)) > 0) bos.write(b, 0, n);
            is.close();
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Exception e) { return ""; }
    }
}
