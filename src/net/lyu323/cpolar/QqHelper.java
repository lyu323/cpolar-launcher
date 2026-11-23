package net.lyu323.cpolar;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 「联系作者」QQ 跳转（v1.2.0 移植自 ChmlFrp 启动器）。
 *
 * 真机反馈（部分国产 Android ROM）：
 *   装了 QQ，点「打开 QQ 加好友」却只进 QQ 消息列表（主界面），没到加好友页；
 *   而且每次点击都写一次剪贴板，日志里连刷 5 条「链接已复制」。
 *
 * 两个根因：
 *   ① v3.0 每个通道都先 queryIntentActivities 再 startActivity。
 *      在 OriginOS 上，带 setPackage 的 https intent 查询会「假阴性」，
 *      于是所有通道被跳过，只剩 mqq:// 兜底 —— 而 mqq:// 只是 QQ 主界面，
 *      不是加好友页。这一版改成「直接 startActivity，靠 try/catch 判成败」，
 *      查询结果只用于诊断，不再当门槛。
 *   ② QQ 官方短链落地页在 Android 上真正用的 scheme 是
 *      mqqopensdkapi://bizAgent/qm/qr?url=<urlencoded 落地页>，
 *      v3.0 没走这条，自然到不了原生加好友 / 申请加群页。
 *
 * 这一版：
 *   ① 一次联网解析短链 302 → 官方落地页（带 k），本机缓存，之后点击秒开；
 *   ② 主通道用官方同款 mqqopensdkapi://bizAgent/qm/qr 直达加好友页；
 *   ③ 用户填了 QQ 号则优先 mqqapi 资料卡 / mqqwpa 会话；
 *   ④ 解析出页面里的 rawuin 时，补一条 mqqapi 资料卡通道；
 *   ⑤ 全程不再自动复制：只有用户点「复制链接」才写剪贴板，且 3 秒内不重复写。
 */
public final class QqHelper {
    private QqHelper() {}

    public static final String QQ_URL = "https://qm.qq.com/q/9J7OMIFrzy";
    public static final String QQ_PKG = "com.tencent.mobileqq";
    public static final String TIM_PKG = "com.tencent.tim";

    /**
     * 短链 302 后的官方落地页（k 已解析内置），参数与 QQ 官方页面 Android 分支一致。
     * 内置后点击「打开 QQ 加好友」完全不需要联网，不受证书 / 超时 / 弱网影响。
     * 作者若重置了链接，可在联系作者弹窗点「刷新加好友页」联网更新缓存。
     */
    public static final String QQ_LANDING_DEFAULT =
            "https://qm.qq.com/cgi-bin/qm/qr?k=jSf98Ern6upxZs0sX7-agx2UNQ_Lj7TF"
                    + "&jump_from=&auth=&app_name=&authSig=&source_id=3_40001";
    public static final String QQ_RAWUIN_DEFAULT = "3013884522";
    public static final String QQ_CARDTYPE_DEFAULT = "group";

    /** 短链解析结果（可经 bg/done 用 encode() 传递）。 */
    public static final class Resolve {
        public String landing = "";   // 官方落地页 URL（带 k 与官方同款参数）
        public String rawUin = "";    // 落地页里的 rawuin
        public String cardType = "person";
        public String source = "";    // network / fallback / offline

        public String encode() {
            return landing + "|" + rawUin + "|" + cardType + "|" + source;
        }

        public static Resolve decode(String s) {
            Resolve r = new Resolve();
            if (s == null) return r;
            String[] p = s.split("\\|", -1);
            if (p.length > 0) r.landing = p[0];
            if (p.length > 1) r.rawUin = p[1];
            if (p.length > 2 && p[2].length() > 0) r.cardType = p[2];
            if (p.length > 3) r.source = p[3];
            if (r.landing.length() == 0) r.landing = QQ_URL;
            return r;
        }
    }

    public static final class Result {
        public boolean ok = false;
        public boolean qqInstalled = false;
        public String channel = "";
        public String message = "";
        public String detail = "";
    }

    // ------------------------------------------------------------ 包检测

    public static boolean installed(Context c) {
        return pkg(c, QQ_PKG) || pkg(c, TIM_PKG);
    }

    private static boolean pkg(Context c, String name) {
        try {
            PackageManager pm = c.getPackageManager();
            pm.getPackageInfo(name, 0);                       // 需要 <queries> 才可见
            return pm.getLaunchIntentForPackage(name) != null;
        } catch (Exception e) { return false; }
    }

    public static String status(Context c) {
        boolean qq = pkg(c, QQ_PKG), tim = pkg(c, TIM_PKG);
        if (qq) return "✓ 已检测到 QQ（" + QQ_PKG + "）";
        if (tim) return "✓ 已检测到 TIM（" + TIM_PKG + "）";
        return "⚠ 未检测到 QQ / TIM，将使用浏览器";
    }

    /** 仅用于诊断展示；绝不再作为「能不能跳」的前置判断。 */
    private static boolean can(Context c, Intent i) {
        try {
            List<?> l = c.getPackageManager().queryIntentActivities(i, 0);
            return l != null && !l.isEmpty();
        } catch (Exception e) { return false; }
    }

    private static Intent view(String url) {
        return new Intent(Intent.ACTION_VIEW, Uri.parse(url));
    }

    /** 直接拉起，用异常判成败。v3.0 的「先查再跳」在 OriginOS 上会假阴性。 */
    private static boolean fire(Context c, String url, String pkg) {
        try {
            Intent i = view(url);
            if (pkg != null) i.setPackage(pkg);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (Exception e) { return false; }
    }

    /** 自定义 scheme：先指定 QQ 包，再不带包，最后 TIM 包。 */
    private static boolean fireAny(Context c, String url) {
        if (fire(c, url, QQ_PKG)) return true;
        if (fire(c, url, null)) return true;
        return pkg(c, TIM_PKG) && fire(c, url, TIM_PKG);
    }

    /** https 短链：只交给 QQ / TIM，绝不给系统默认浏览器（那正是旧 bug）。 */
    private static boolean fireQqPkg(Context c, String url) {
        if (fire(c, url, QQ_PKG)) return true;
        return pkg(c, TIM_PKG) && fire(c, url, TIM_PKG);
    }

    private static Result done(Result r, boolean ok, String ch, String msg) {
        r.ok = ok;
        r.channel = ch;
        r.message = msg;
        r.detail = "通道=" + ch + " · QQ=" + (r.qqInstalled ? "已安装" : "未安装");
        return r;
    }

    // ------------------------------------------------------------ 短链解析

    private static String param(String s, String key) {
        if (s == null) return "";
        Matcher m = Pattern.compile("[?&]" + key + "=([^&#\\s\"'<]+)").matcher(s);
        return m.find() ? m.group(1) : "";
    }

    /** 按官方落地页 Android 分支拼内层 URL（source_id=3_40001）。 */
    public static String buildLanding(String k) {
        return "https://qm.qq.com/cgi-bin/qm/qr?k=" + k
                + "&jump_from=&auth=&app_name=&authSig=&source_id=3_40001";
    }

    private static HttpURLConnection openConn(String url, boolean follow) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(6000);
        c.setReadTimeout(8000);
        c.setInstanceFollowRedirects(follow);
        c.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 "
                        + "Chrome/120 Mobile Safari/537.36");
        return c;
    }

    private static String readBody(HttpURLConnection c) {
        try {
            InputStream in = c.getInputStream();
            if (in == null) return "";
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n, total = 0;
            while ((n = in.read(buf)) > 0 && total < 131072) {
                bos.write(buf, 0, n);
                total += n;
            }
            in.close();
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Exception e) { return ""; }
    }

    /**
     * 手动读短链 302 的 Location 拿 k（HttpURLConnection 自动跟随时 getURL()
     * 仍返回原始 URL，拿不到 k），再取落地页 HTML 里的 rawuin / card_type。
     * 不抛异常；失败时退回短链，QQ 的 bizAgent 会自己跟随重定向。
     * 主流程不依赖这里：官方落地页已内置，本方法只用于「刷新加好友页」。
     */
    public static Resolve resolveNetwork() {
        Resolve r = new Resolve();
        HttpURLConnection c1 = null, c2 = null;
        try {
            c1 = openConn(QQ_URL, false);
            c1.connect();
            String loc = c1.getHeaderField("Location");
            if (loc != null && loc.startsWith("/"))
                loc = "https://qm.qq.com" + loc;
            String k = param(loc, "k");
            String html = "";
            if (loc != null && loc.length() > 0) {
                c2 = openConn(loc, true);
                c2.connect();
                html = readBody(c2);
            }
            Matcher mu = Pattern.compile("var\\s+rawuin\\s*=\\s*(\\d{5,12})").matcher(html);
            if (mu.find()) r.rawUin = mu.group(1);
            Matcher mc = Pattern.compile("card_type=(\\w+)").matcher(html);
            if (mc.find()) r.cardType = mc.group(1);
            r.landing = k.length() > 0 ? buildLanding(k) : QQ_URL;
            r.source = k.length() > 0 ? "network" : "fallback";
        } catch (Exception e) {
            r.landing = QQ_URL;
            r.source = "offline";
        } finally {
            if (c1 != null) try { c1.disconnect(); } catch (Exception ignored) {}
            if (c2 != null) try { c2.disconnect(); } catch (Exception ignored) {}
        }
        return r;
    }

    // ------------------------------------------------------------ 主入口

    /**
     * 直达 QQ 官方加好友页。全程不写剪贴板。
     * @param landing  resolveNetwork() 得到的落地页；空则退回短链
     * @param rawUin   落地页里的 rawuin，用作资料卡备用通道
     * @param cardType 落地页里的 card_type
     * @param userUin  用户在弹窗里填的 QQ 号，可为空
     */
    public static Result launch(Context c, String landing, String rawUin,
                                String cardType, String userUin) {
        Result r = new Result();
        r.qqInstalled = installed(c);

        String face = (landing == null || landing.length() == 0) ? QQ_URL : landing;
        String enc;
        try {
            enc = URLEncoder.encode(face, "UTF-8").replace("+", "%20");
        } catch (Exception e) { enc = face; }

        String n = userUin == null ? "" : userUin.trim().replaceAll("[^0-9]", "");
        String raw = rawUin == null ? "" : rawUin.trim().replaceAll("[^0-9]", "");
        String ct = (cardType == null || cardType.length() == 0) ? "person" : cardType;

        // ① 用户明确填了 QQ 号：资料卡（带「加好友」按钮）优先，再退会话
        if (r.qqInstalled && n.length() >= 5) {
            String card = "mqqapi://card/show_pslcard?src_type=internal&version=1&uin="
                    + n + "&card_type=person&source=qrcode&jump_from=cpolar";
            if (fireAny(c, card))
                return done(r, true, "mqqapi-card", "已直达 QQ 资料卡 / 加好友页（" + n + "）");
            String wpa = "mqqwpa://im/chat?chat_type=wpa&uin=" + n
                    + "&version=1&src_type=web";
            if (fireAny(c, wpa))
                return done(r, true, "mqqwpa", "已打开 QQ 加好友 / 会话页（" + n + "）");
            String mqq = "mqq://im/chat?chat_type=wpa&uin=" + n + "&version=1&src_type=web";
            if (fireAny(c, mqq))
                return done(r, true, "mqq", "已打开 QQ 会话页（" + n + "）");
        }

        // ② 官方落地页在 Android 上用的就是这条：直达原生加好友 / 申请加群页
        if (r.qqInstalled) {
            String biz = "mqqopensdkapi://bizAgent/qm/qr?url=" + enc;
            if (fireAny(c, biz))
                return done(r, true, "mqqopensdkapi", "已直达 QQ 官方加好友页");
            String biz2 = "mqqapi://bizAgent/qm/qr?url=" + enc;
            if (fireAny(c, biz2))
                return done(r, true, "mqqapi-biz", "已直达 QQ 官方加好友页");
        }

        // ③ 落地页解析出的号码：资料卡备用通道
        if (r.qqInstalled && raw.length() >= 5) {
            String card = "mqqapi://card/show_pslcard?src_type=internal&version=1&uin="
                    + raw + "&card_type=" + ct + "&source=qrcode&jump_from=cpolar";
            if (fireAny(c, card))
                return done(r, true, "mqqapi-card", "已打开 QQ 资料卡（链接解析号 " + raw + "）");
        }

        // ④ 短链强制交给 QQ（保留 v3.0 通道，但不再被 can() 拦掉）
        if (r.qqInstalled && fireQqPkg(c, QQ_URL))
            return done(r, true, "qq-web", "已拉起 QQ 打开加好友页");

        // ⑤ 确实没装 QQ / TIM 才用浏览器
        if (!r.qqInstalled) {
            if (fire(c, QQ_URL, null))
                return done(r, true, "browser", "未检测到 QQ，已用浏览器打开加好友页");
            return done(r, false, "none", "没有可打开链接的应用，请点「复制链接」手动打开");
        }

        // ⑥ 最后兜底：只打开 QQ，不再自动复制；明确告诉用户去点复制
        if (fireAny(c, "mqq://"))
            return done(r, false, "qq-app",
                    "未能直达加好友页，已打开 QQ；请点「复制链接」手动粘贴");
        return done(r, false, "none", "未能打开 QQ，请点「复制链接」手动打开");
    }

    /** 弹窗里独立的「用浏览器打开」：明确、不冒充 QQ 跳转、不复制。 */
    public static Result openBrowser(Context c) {
        Result r = new Result();
        r.qqInstalled = installed(c);
        if (fire(c, QQ_URL, null))
            return done(r, true, "browser", "已用浏览器打开加好友页");
        return done(r, false, "none", "无法打开浏览器，请点「复制链接」手动打开");
    }

    // ------------------------------------------------------------ 剪贴板（唯一入口）

    private static volatile long lastCopyAt = 0L;

    /**
     * 只有用户主动点「复制链接」才会调用。3 秒内重复点不重复写剪贴板。
     * @return true 表示本次真的写了剪贴板
     */
    public static boolean copyLink(Context c) {
        long now = System.currentTimeMillis();
        if (now - lastCopyAt < 3000L) return false;
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    c.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("作者 QQ 链接", QQ_URL));
            lastCopyAt = now;
            return true;
        } catch (Exception e) { return false; }
    }

    // ------------------------------------------------------------ 诊断

    private static boolean schemeVisible(Context c, String scheme) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(scheme + "://x/y"));
        return can(c, i);
    }

    public static String diagnose(Context c) {
        boolean qq = pkg(c, QQ_PKG), tim = pkg(c, TIM_PKG);
        Intent q = view(QQ_URL);
        q.setPackage(QQ_PKG);
        return "QQ 跳转检测：QQ=" + (qq ? "已安装" : "未安装")
                + " · TIM=" + (tim ? "已安装" : "未安装")
                + " · mqqopensdkapi=" + (schemeVisible(c, "mqqopensdkapi") ? "可见" : "不可见")
                + " · mqqapi=" + (schemeVisible(c, "mqqapi") ? "可见" : "不可见")
                + " · 短链可被 QQ 接管=" + (qq && can(c, q) ? "是" : "否")
                + " · 主通道=mqqopensdkapi://bizAgent/qm/qr（直达加好友页）";
    }
}
