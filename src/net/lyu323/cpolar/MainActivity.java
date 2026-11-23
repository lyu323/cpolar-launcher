package net.lyu323.cpolar;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Cpolar 启动器 v1.2.0 · 主界面
 *
 * 「结合历史聊天记录」：沿用前几版启动器的设计语言与工程约定
 *   · 纯 Java 原生、零第三方依赖、21 套主题 + 动态液态玻璃弹窗
 *   · 四页底部导航、通知栏启停、隐藏后台四项、免责声明、联系作者
 *   · 多隧道 + 显示所有隧道（这里对应 cpolar.yml 的 tunnels 多段 + start-all）
 *   · 本机配置 / 日志 / 地址全部只存本机，不上传
 *
 * 与 ChmlFrp 版的区别：没有 OAuth / 签到 / 面板隧道列表，
 * 认证改为官方 authtoken，隧道由本机 YAML 定义，公网地址由
 * stdout 的 Forwarding 行与本地 4040 检查接口获取。
 */
public class MainActivity extends Activity {

    static final String PREFS = "cpolar";
    static final int REQ_KERNEL = 71;
    static final int REQ_YML = 72;

    SharedPreferences sp;
    final Handler h = new Handler(Looper.getMainLooper());

    EditText etToken, etYaml;
    TextView tvLog, tvStatus, tvUptime, tvBinary, tvAddrCount, tvTunnelCount, tvHint;
    LinearLayout tunnelBox, urlBox;
    ScrollView[] pages = new ScrollView[4];
    Button[] tabs = new Button[4];
    View[] tabInd = new View[4];
    Button btnStart, btnStop, btnTheme, btnPill;
    CheckBox cbOnlyErrors, cbAutostart, cbWebUI, cbNotifyEvents, cbThemeColor;
    CheckBox cbHideRecents, cbSilent, cbLock, cbMask;
    LinearLayout cardHost;

    int themeId = 0, page = 0;
    long logPos = 0;
    String logFilter = "";
    final List<CpolarTunnel> tunnels = new ArrayList<CpolarTunnel>();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        themeId = sp.getInt("themeId", 0);
        Ui.apply(Theme.resolve(this, themeId));
        buildUi();
        loadPrefs();
        switchPage(sp.getInt("page", 0));
        if (!new File(getFilesDir(), "runtime.log").exists()) {
            CpolarService.append(this, "[提示] Cpolar 启动器 v1.2.0 · 基于官方文档与 GitHub 仓库构建");
            CpolarService.append(this, "[提示] 流程：官方面板复制 authtoken → 添加隧道 → 生成配置 → 启动");
            CpolarService.append(this, "[提示] 一条 start-all 命令可同时启动 cpolar.yml 里所有隧道");
            CpolarService.append(this, "[提示] 公网地址由进程输出与本机 4040 检查接口自动获取");
            CpolarService.append(this, "[提示] 内置客户端为 2022 年官方 ARM 版，设置页可导入最新内核");
            CpolarService.append(this, "[提示] v1.2.0：联系作者改用 QQ 官方直达通道，装了 QQ 不再跳浏览器");
            CpolarService.append(this, "[提示] v1.2.0：包名 net.lyu323.cpolar · 检查更新连接本项目 GitHub Releases");
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                   != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
        Privacy.applyRecents(this);
        handleNotifyIntent(getIntent());
        if (!Disclaimer.agreed(this)) showDisclaimer(true);
        h.post(ticker);
        refreshAll();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshAll();
    }

    @Override protected void onDestroy() {
        savePrefs();
        super.onDestroy();
    }

    // =============================================================== UI 骨架

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.addView(header());

        FrameLayout host = new FrameLayout(this);
        for (int i = 0; i < 4; i++) {
            ScrollView sv = new ScrollView(this);
            sv.setFillViewport(true);
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            int p = Ui.dp(this, 12);
            box.setPadding(p, p, p, Ui.dp(this, 20));
            sv.addView(box, new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            pages[i] = sv;
            host.addView(sv, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            buildPage(i, box);
        }
        root.addView(host, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(tabBar());
        root.addView(bottomBar());
        setContentView(root);
    }

    private View header() {
        LinearLayout hd = new LinearLayout(this);
        hd.setOrientation(LinearLayout.HORIZONTAL);
        hd.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Ui.HEADER_START, Ui.HEADER_END}));
        int p = Ui.dp(this, 16);
        hd.setPadding(p, Ui.dp(this, 14), p, Ui.dp(this, 14));
        hd.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = new TextView(this);
        t.setText("Cpolar 启动器");
        t.setTextSize(22);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(0xFFFFFFFF);
        titleRow.addView(t, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView author = new TextView(this);
        author.setText("✉ 联系作者");
        author.setTextSize(10);
        author.setTypeface(Typeface.DEFAULT_BOLD);
        author.setTextColor(0xFFFFFFFF);
        author.setBackground(Ui.round(0x33FFFFFF, 14, this));
        int ap = Ui.dp(this, 9);
        author.setPadding(ap, Ui.dp(this, 5), ap, Ui.dp(this, 5));
        author.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { authorDialog(); }
        });
        titleRow.addView(author);
        texts.addView(titleRow);

        TextView s = new TextView(this);
        s.setText("本地启动 cpolar 官方客户端 · 免 Root · v1.2.0");
        s.setTextSize(11);
        s.setTextColor(0xFFD6E4FF);
        s.setSingleLine(true);
        s.setPadding(0, Ui.dp(this, 3), 0, 0);
        texts.addView(s);
        TextView qq = new TextView(this);
        qq.setText("🔗 点击加作者 QQ 好友 · 多隧道 / 玻璃弹窗 / 隐藏后台");
        qq.setTextSize(10);
        qq.setTextColor(0xFFBBDEFB);
        qq.setPaintFlags(qq.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        qq.setSingleLine(true);
        qq.setEllipsize(TextUtils.TruncateAt.END);
        qq.setPadding(0, Ui.dp(this, 2), 0, 0);
        qq.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openQq(); }
        });
        texts.addView(qq);
        tvUptime = new TextView(this);
        tvUptime.setText("未运行");
        tvUptime.setTextSize(11);
        tvUptime.setTextColor(0xFFBBDEFB);
        tvUptime.setPadding(0, Ui.dp(this, 2), 0, 0);
        texts.addView(tvUptime);
        hd.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        btnTheme = new Button(this);
        btnTheme.setText("◐");
        btnTheme.setTextSize(18);
        btnTheme.setAllCaps(false);
        btnTheme.setBackground(Ui.round(0x33FFFFFF, 20, this));
        btnTheme.setTextColor(0xFFFFFFFF);
        btnTheme.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { themeDialog(); }
        });
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                Ui.dp(this, 38), Ui.dp(this, 38));
        tp.setMargins(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
        hd.addView(btnTheme, tp);

        btnPill = new Button(this);
        btnPill.setText("○ 未运行");
        btnPill.setTextSize(11);
        btnPill.setAllCaps(false);
        btnPill.setTextColor(0xFFFFFFFF);
        btnPill.setBackground(Ui.round(0x33FFFFFF, 16, this));
        btnPill.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { switchPage(2); }
        });
        hd.addView(btnPill);
        return hd;
    }

    private void buildPage(int i, LinearLayout box) {
        if (i == 0) consolePage(box);
        else if (i == 1) configPage(box);
        else if (i == 2) logPage(box);
        else settingsPage(box);
    }

    private View tabBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackground(Ui.roundStroke(Ui.BAR_BG, Ui.LINE, 1, 0, this));
        bar.setPadding(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
        String[] ic = {"⌂", "⚙", "☰", "⋯"};
        String[] tx = {"控制台", "配置", "日志", "设置"};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);
            col.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 2));
            Button b = new Button(this);
            b.setText(ic[i] + "  " + tx[i]);
            b.setTextSize(11);
            b.setAllCaps(false);
            b.setBackground(Ui.round(0x00000000, 10, this));
            b.setTextColor(Ui.MUTED);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { switchPage(idx); }
            });
            tabs[i] = b;
            col.addView(b, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 36)));
            View ind = new View(this);
            ind.setBackgroundColor(Ui.BLUE);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    Ui.dp(this, 24), Math.max(2, Ui.dp(this, 2)));
            lp.topMargin = Ui.dp(this, 2);
            col.addView(ind, lp);
            tabInd[i] = ind;
            bar.addView(col, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        return bar;
    }

    private View bottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackground(Ui.roundStroke(Ui.BAR_BG, Ui.LINE, 1, 0, this));
        bar.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 10));
        btnStart = Ui.button(this, "▶  启动全部隧道", 0, new View.OnClickListener() {
            @Override public void onClick(View v) {
                persistAll();
                startService(new Intent(MainActivity.this, CpolarService.class)
                        .setAction(CpolarService.ACTION_START));
                toast("已请求启动");
            }
        });
        btnStop = Ui.button(this, "■  停止", 3, new View.OnClickListener() {
            @Override public void onClick(View v) {
                startService(new Intent(MainActivity.this, CpolarService.class)
                        .setAction(CpolarService.ACTION_STOP));
                toast("已请求停止");
            }
        });
        bar.addView(btnStart, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams sp2 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        sp2.setMargins(Ui.dp(this, 8), 0, 0, 0);
        bar.addView(btnStop, sp2);
        return bar;
    }

    private void switchPage(int p) {
        page = p;
        for (int i = 0; i < 4; i++) {
            pages[i].setVisibility(i == p ? View.VISIBLE : View.GONE);
            tabs[i].setTextColor(i == p ? Ui.BLUE : Ui.MUTED);
            tabInd[i].setVisibility(i == p ? View.VISIBLE : View.INVISIBLE);
        }
        sp.edit().putInt("page", p).commit();
    }

    // =============================================================== 页 0 控制台

    private void consolePage(LinearLayout box) {
        // 状态卡
        LinearLayout st = Ui.card(this);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView h = new TextView(this);
        h.setText("运行状态");
        h.setTextSize(15);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setTextColor(Ui.TEXT);
        head.addView(h, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tvStatus = Ui.badge(this, "○ 未运行", Ui.MUTED);
        head.addView(tvStatus);
        st.addView(head, Ui.gap(this, 4));
        tvTunnelCount = new TextView(this);
        tvTunnelCount.setText("隧道 0 条");
        tvTunnelCount.setTextSize(12);
        tvTunnelCount.setTextColor(Ui.MUTED);
        st.addView(tvTunnelCount, Ui.gap(this, 8));
        tvAddrCount = new TextView(this);
        tvAddrCount.setText("公网地址 0 个");
        tvAddrCount.setTextSize(12);
        tvAddrCount.setTextColor(Ui.MUTED);
        st.addView(tvAddrCount);
        tvBinary = new TextView(this);
        tvBinary.setText(CpolarService.binaryInfo(this));
        tvBinary.setTextSize(11);
        tvBinary.setTextColor(Ui.MUTED);
        st.addView(tvBinary, Ui.gap(this, 8));
        tvHint = new TextView(this);
        tvHint.setText("提示：免费版随机域名约 24 小时变化，带宽约 1M");
        tvHint.setTextSize(10);
        tvHint.setTextColor(Ui.MUTED);
        st.addView(tvHint, Ui.gap(this, 6));
        box.addView(st, Ui.gap(this, 10));

        // 认证卡
        box.addView(Ui.section(this, "账号认证"), Ui.gap(this, 8));
        LinearLayout auth = Ui.card(this);
        etToken = Ui.edit(this, "粘贴官方面板「验证」页的 authtoken", 1, false, true);
        auth.addView(etToken, Ui.gap(this, 6));
        auth.addView(Ui.row(this,
                Ui.button(this, "显示 / 隐藏", 2, new View.OnClickListener() {
                    @Override public void onClick(View v) { toggleToken(); }
                }),
                Ui.button(this, "粘贴导入", 1, new View.OnClickListener() {
                    @Override public void onClick(View v) { pasteToken(); }
                }),
                Ui.button(this, "官方面板", 2, new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        open("https://dashboard.cpolar.com/", "已打开官方面板");
                    }
                })), Ui.gap(this, 6));
        TextView tk = new TextView(this);
        tk.setText("authtoken 只写入本机 cpolar.yml，不出现在命令行参数、不上传。");
        tk.setTextSize(10);
        tk.setTextColor(Ui.MUTED);
        auth.addView(tk);
        box.addView(auth, Ui.gap(this, 10));

        // 所有隧道
        box.addView(Ui.section(this, "所有隧道（start-all 一次全部启动）"), Ui.gap(this, 8));
        LinearLayout tc = Ui.card(this);
        tunnelBox = new LinearLayout(this);
        tunnelBox.setOrientation(LinearLayout.VERTICAL);
        tc.addView(tunnelBox);
        tc.addView(Ui.row(this,
                Ui.button(this, "＋ 添加隧道", 1, new View.OnClickListener() {
                    @Override public void onClick(View v) { addTunnelDialog(-1); }
                }),
                Ui.button(this, "生成配置", 2, new View.OnClickListener() {
                    @Override public void onClick(View v) { persistAll(); toast("配置已生成"); }
                }),
                Ui.button(this, "清空", 3, new View.OnClickListener() {
                    @Override public void onClick(View v) { clearTunnels(); }
                })), Ui.gap(this, 8));
        TextView tn = new TextView(this);
        tn.setText("一个 frpc/cpolar 进程天然能带多个 proxy 段；不同隧道请绑定不同本地端口。");
        tn.setTextSize(10);
        tn.setTextColor(Ui.MUTED);
        tc.addView(tn);
        box.addView(tc, Ui.gap(this, 10));

        // 公网地址
        box.addView(Ui.section(this, "公网地址（启动后自动显示）"), Ui.gap(this, 8));
        LinearLayout uc = Ui.card(this);
        urlBox = new LinearLayout(this);
        urlBox.setOrientation(LinearLayout.VERTICAL);
        uc.addView(urlBox);
        uc.addView(Ui.row(this,
                Ui.button(this, "复制全部", 2, new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        copy(CpolarUrls.asText(CpolarUrls.deserialize(sp.getString("urls", ""))));
                    }
                }),
                Ui.button(this, "Web UI 9200", 2, new View.OnClickListener() {
                    @Override public void onClick(View v) { open(CpolarUrls.WEB_9200, "已打开本机 9200"); }
                }),
                Ui.button(this, "检查 4040", 2, new View.OnClickListener() {
                    @Override public void onClick(View v) { open(CpolarUrls.WEB_4040, "已打开本机 4040"); }
                })), Ui.gap(this, 8));
        box.addView(uc, Ui.gap(this, 10));

        box.addView(Ui.button(this, "⚡ 生成配置并启动全部隧道", 0,
                new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        persistAll();
                        String err = CpolarConfig.validate(etYaml.getText().toString());
                        if (err.length() > 0) { toast("配置有误：" + err); return; }
                        startService(new Intent(MainActivity.this, CpolarService.class)
                                .setAction(CpolarService.ACTION_START));
                        toast("已请求启动");
                    }
                }), Ui.gap(this, 6));
    }

    // =============================================================== 页 1 配置

    private void configPage(LinearLayout box) {
        box.addView(Ui.osTitle(this, "cpolar.yml", "本机配置"), Ui.gap(this, 8));
        box.addView(Ui.osTip(this,
                "官方配置是 YAML，缩进敏感、每级两个空格、一定不能用 TAB。"
                + "启动命令：cpolar start-all -config=<本机文件> -log=stdout", 2), Ui.gap(this, 10));

        LinearLayout c = Ui.card(this);
        etYaml = Ui.edit(this, "authtoken: ...\ntunnels:\n  web:\n    addr: 8080", 12, true, false);
        c.addView(etYaml, Ui.gap(this, 6));
        c.addView(Ui.row(this,
                Ui.button(this, "校验并保存", 1, new View.OnClickListener() {
                    @Override public void onClick(View v) { saveYaml(); }
                }),
                Ui.button(this, "恢复示例", 2, new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        etYaml.setText(CpolarConfig.demo(etToken.getText().toString().trim()));
                        toast("已恢复示例（web + ssh）");
                    }
                }),
                Ui.button(this, "导入文件", 2, new View.OnClickListener() {
                    @Override public void onClick(View v) { pickFile(REQ_YML); }
                })), Ui.gap(this, 6));
        box.addView(c, Ui.gap(this, 12));

        box.addView(Ui.section(this, "客户端内核"), Ui.gap(this, 8));
        LinearLayout kc = Ui.card(this);
        kc.addView(Ui.osRow(this, "当前内核", CpolarService.binaryInfo(this), "", true), Ui.match());
        kc.addView(Ui.osRow(this, "来源", Disclaimer.BINARY_FROM, "", true), Ui.match());
        kc.addView(Ui.osRow(this, "更新",
                "官网下载最新 ARM / ARM64 客户端 zip，用下方按钮导入即可替换内置旧版", "", false),
                Ui.match());
        kc.addView(Ui.button(this, "导入自定义内核（zip / 可执行文件）", 1,
                new View.OnClickListener() {
                    @Override public void onClick(View v) { pickFile(REQ_KERNEL); }
                }), Ui.gap(this, 8));
        kc.addView(Ui.button(this, "恢复内置内核", 2, new View.OnClickListener() {
            @Override public void onClick(View v) {
                new File(getFilesDir(), "bin/cpolar").delete();
                toast("已恢复内置内核");
                refreshAll();
            }
        }));
        box.addView(kc, Ui.gap(this, 12));

        box.addView(Ui.section(this, "官方文档"), Ui.gap(this, 8));
        LinearLayout dc = Ui.card(this);
        dc.addView(Ui.button(this, "打开官方在线文档", 2, new View.OnClickListener() {
            @Override public void onClick(View v) { open("https://www.cpolar.com/docs", "已打开官方文档"); }
        }), Ui.gap(this, 6));
        dc.addView(Ui.button(this, "打开官方面板 · 在线隧道列表", 2, new View.OnClickListener() {
            @Override public void onClick(View v) {
                open("https://dashboard.cpolar.com/status", "已打开官方面板");
            }
        }), Ui.gap(this, 6));
        box.addView(dc, Ui.gap(this, 10));
    }

    // =============================================================== 页 2 日志

    private void logPage(LinearLayout box) {
        box.addView(Ui.osTitle(this, "运行日志", "实时"), Ui.gap(this, 8));
        LinearLayout c = Ui.card(this);
        cbOnlyErrors = new CheckBox(this);
        cbOnlyErrors.setText("只看异常 / 错误行");
        cbOnlyErrors.setTextSize(12);
        cbOnlyErrors.setTextColor(Ui.TEXT);
        c.addView(cbOnlyErrors, Ui.gap(this, 4));
        c.addView(Ui.row(this,
                Ui.button(this, "搜索", 2, new View.OnClickListener() {
                    @Override public void onClick(View v) { logSearchDialog(); }
                }),
                Ui.button(this, "复制全部", 2, new View.OnClickListener() {
                    @Override public void onClick(View v) { copy(redact(CpolarService.readLog(MainActivity.this))); }
                }),
                Ui.button(this, "分享(脱敏)", 2, new View.OnClickListener() {
                    @Override public void onClick(View v) { shareLog(); }
                }),
                Ui.button(this, "清空", 3, new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        new File(getFilesDir(), "runtime.log").delete();
                        logPos = 0;
                        tvLog.setText("");
                        toast("日志已清空");
                    }
                })), Ui.gap(this, 6));
        tvLog = new TextView(this);
        tvLog.setTextSize(11);
        tvLog.setTextColor(Ui.LOG_FG);
        tvLog.setTypeface(Typeface.MONOSPACE);
        tvLog.setBackgroundColor(Ui.LOG_BG);
        tvLog.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        tvLog.setTextIsSelectable(true);
        c.addView(tvLog, Ui.match());
        box.addView(c, Ui.gap(this, 10));
        TextView tip = new TextView(this);
        tip.setText("日志里若出现 authentication failed / token 无效，请到官方面板重新复制 authtoken。"
                + "分享默认打码 token 与公网域名。");
        tip.setTextSize(10);
        tip.setTextColor(Ui.MUTED);
        box.addView(tip);
    }

    // =============================================================== 页 3 设置

    private void settingsPage(LinearLayout box) {
        cardHost = box;
        box.addView(Ui.osTitle(this, "设置", "v1.2.0"), Ui.gap(this, 8));

        box.addView(Ui.section(this, "外观"), Ui.gap(this, 8));
        LinearLayout look = Ui.card(this);
        look.addView(Ui.button(this, "◐ 选择主题（" + Theme.count() + " 套 · 5 套玻璃）", 1,
                new View.OnClickListener() {
                    @Override public void onClick(View v) { themeDialog(); }
                }), Ui.gap(this, 6));
        cbThemeColor = check("通知栏强调色跟随主题", "notifyThemeColor", true);
        look.addView(cbThemeColor, Ui.gap(this, 4));
        box.addView(look, Ui.gap(this, 10));

        box.addView(Ui.section(this, "启动与通知"), Ui.gap(this, 8));
        LinearLayout run = Ui.card(this);
        cbAutostart = check("开机自动启动隧道", "autostart", false);
        cbWebUI = check("同时开启本机 Web UI（9200）", "webui", true);
        cbNotifyEvents = check("客户端退出时单独提醒", "notifyEvents", true);
        run.addView(cbAutostart, Ui.gap(this, 4));
        run.addView(cbWebUI, Ui.gap(this, 4));
        run.addView(cbNotifyEvents, Ui.gap(this, 6));
        run.addView(Ui.button(this, "🔑 权限体检 / 电池优化", 1, new View.OnClickListener() {
            @Override public void onClick(View v) { permissionsDialog(); }
        }), Ui.gap(this, 6));
        box.addView(run, Ui.gap(this, 10));

        box.addView(Ui.section(this, "隐藏后台 / 隐私显示"), Ui.gap(this, 8));
        LinearLayout priv = Ui.card(this);
        cbHideRecents = privCheck("不在最近任务里显示本 App", Privacy.K_RECENTS);
        cbSilent = privCheck("通知最小化", Privacy.K_SILENT);
        cbLock = privCheck("锁屏隐藏通知内容", Privacy.K_LOCK);
        cbMask = privCheck("通知里打码公网域名", Privacy.K_MASK);
        priv.addView(Ui.osTip(this,
                "Android 8+ 前台服务必须有通知，系统不允许完全隐藏，"
                + "只能降到最低优先级/最小化；隐藏后台不等于保活。", 1), Ui.gap(this, 8));
        priv.addView(Ui.button(this, "全部恢复默认", 2, new View.OnClickListener() {
            @Override public void onClick(View v) {
                for (String k : new String[]{Privacy.K_RECENTS, Privacy.K_SILENT,
                        Privacy.K_LOCK, Privacy.K_MASK}) Privacy.set(MainActivity.this, k, false);
                syncPrivacy();
                Privacy.applyRecents(MainActivity.this);
                toast("已恢复默认显示");
            }
        }), Ui.gap(this, 6));
        box.addView(priv, Ui.gap(this, 10));

        box.addView(Ui.section(this, "版本更新"), Ui.gap(this, 8));
        LinearLayout upd = Ui.card(this);
        upd.addView(Ui.osTip(this,
                "检查更新连接本项目 GitHub 仓库的 Releases（api.github.com 公开接口），"
                + "只在点按钮时联网、只读取版本信息，不上传任何数据；发现新版只给下载入口，不自动安装。", 2),
                Ui.gap(this, 8));
        upd.addView(Ui.row(this,
                Ui.button(this, "检查更新", 2, new View.OnClickListener() {
                    @Override public void onClick(View v) { checkUpdate(); }
                }),
                Ui.button(this, "项目地址", 2, new View.OnClickListener() {
                    @Override public void onClick(View v) { openGithub(UpdateChecker.REPO_URL); }
                })), Ui.gap(this, 6));
        upd.addView(Ui.row(this,
                Ui.button(this, "在 GitHub 点个 Star", 2, new View.OnClickListener() {
                    @Override public void onClick(View v) { openGithub(UpdateChecker.STAR_URL); }
                }),
                Ui.button(this, "更新日志", 2, new View.OnClickListener() {
                    @Override public void onClick(View v) { openGithub(UpdateChecker.CHANGELOG_URL); }
                })), Ui.gap(this, 6));
        TextView uv = new TextView(this);
        uv.setText("当前版本 " + UpdateChecker.APP_VERSION
                + " · 数据源：github.com/" + UpdateChecker.REPO + "/releases（不点不联网）");
        uv.setTextSize(10);
        uv.setTextColor(Ui.MUTED);
        upd.addView(uv, Ui.gap(this, 6));
        tvLastCheck = new TextView(this);
        tvLastCheck.setTextSize(10);
        tvLastCheck.setTextColor(Ui.MUTED);
        upd.addView(tvLastCheck, Ui.gap(this, 4));
        updateLastCheckLabel();
        box.addView(upd, Ui.gap(this, 10));

        box.addView(Ui.section(this, "声明与作者"), Ui.gap(this, 8));
        LinearLayout about = Ui.card(this);
        about.addView(Ui.button(this, "免责声明 / 完整条款", 1, new View.OnClickListener() {
            @Override public void onClick(View v) { showDisclaimer(false); }
        }), Ui.gap(this, 6));
        about.addView(Ui.button(this, "网络与隐私 · 域名清单", 2, new View.OnClickListener() {
            @Override public void onClick(View v) { hostsDialog(); }
        }), Ui.gap(this, 6));
        about.addView(Ui.button(this, "✉ 联系作者 / 加 QQ 好友", 1, new View.OnClickListener() {
            @Override public void onClick(View v) { authorDialog(); }
        }), Ui.gap(this, 6));
        about.addView(Ui.button(this, "复制脱敏运行诊断", 2, new View.OnClickListener() {
            @Override public void onClick(View v) { copyDiagnostics(); }
        }), Ui.gap(this, 6));
        about.addView(Ui.button(this, "关于 / 使用帮助", 2, new View.OnClickListener() {
            @Override public void onClick(View v) { aboutDialog(); }
        }), Ui.gap(this, 6));
        box.addView(about, Ui.gap(this, 10));
    }

    private CheckBox check(String text, final String key, boolean def) {
        CheckBox cb = new CheckBox(this);
        cb.setText(text);
        cb.setTextSize(12);
        cb.setTextColor(Ui.TEXT);
        cb.setChecked(sp.getBoolean(key, def));
        cb.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                sp.edit().putBoolean(key, ((CheckBox) v).isChecked()).commit();
                startService(new Intent(MainActivity.this, CpolarService.class)
                        .setAction(CpolarService.ACTION_REFRESH));
            }
        });
        return cb;
    }

    private CheckBox privCheck(String text, final String key) {
        CheckBox cb = new CheckBox(this);
        cb.setText(text);
        cb.setTextSize(12);
        cb.setTextColor(Ui.TEXT);
        cb.setChecked(sp.getBoolean(key, false));
        cb.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean on = ((CheckBox) v).isChecked();
                Privacy.set(MainActivity.this, key, on);
                if (Privacy.K_RECENTS.equals(key)) {
                    if (!Privacy.applyRecents(MainActivity.this))
                        toast("本系统不支持隐藏最近任务");
                } else {
                    startService(new Intent(MainActivity.this, CpolarService.class)
                            .setAction(CpolarService.ACTION_REFRESH));
                }
                toast(Privacy.summary(MainActivity.this));
            }
        });
        return cb;
    }

    private void syncPrivacy() {
        if (cbHideRecents != null) cbHideRecents.setChecked(Privacy.recents(this));
        if (cbSilent != null) cbSilent.setChecked(Privacy.silent(this));
        if (cbLock != null) cbLock.setChecked(Privacy.lock(this));
        if (cbMask != null) cbMask.setChecked(Privacy.mask(this));
    }

    // =============================================================== 隧道增删改

    private void refreshTunnels() {
        tunnelBox.removeAllViews();
        if (tunnels.isEmpty()) {
            TextView e = new TextView(this);
            e.setText("还没有隧道 · 点「＋ 添加隧道」，或用「恢复示例」");
            e.setTextSize(12);
            e.setTextColor(Ui.MUTED);
            tunnelBox.addView(e, Ui.gap(this, 6));
            return;
        }
        for (int i = 0; i < tunnels.size(); i++) {
            final int idx = i;
            final CpolarTunnel t = tunnels.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(Ui.roundStroke(Ui.alpha(Ui.CARD, Ui.GLASS ? 0x70 : 0xFF),
                    Ui.LINE, 1, 12, this));
            int p = Ui.dp(this, 10);
            row.setPadding(p, p, p, p);
            LinearLayout tx = new LinearLayout(this);
            tx.setOrientation(LinearLayout.VERTICAL);
            TextView n = new TextView(this);
            n.setText(t.title());
            n.setTextSize(13);
            n.setTypeface(Typeface.DEFAULT_BOLD);
            n.setTextColor(Ui.TEXT);
            tx.addView(n);
            TextView sub = new TextView(this);
            sub.setText(t.sub());
            sub.setTextSize(10);
            sub.setTextColor(Ui.MUTED);
            tx.addView(sub);
            row.addView(tx, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView edit = new TextView(this);
            edit.setText("编辑");
            edit.setTextSize(12);
            edit.setTextColor(Ui.BLUE);
            edit.setPadding(Ui.dp(this, 8), 0, 0, 0);
            edit.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { addTunnelDialog(idx); }
            });
            row.addView(edit);
            tunnelBox.addView(row, Ui.gap(this, 6));
        }
    }

    private void addTunnelDialog(final int idx) {
        final boolean edit = idx >= 0 && idx < tunnels.size();
        final CpolarTunnel t = edit ? tunnels.get(idx) : new CpolarTunnel();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 4);
        box.setPadding(pad, pad, pad, pad);
        final EditText name = Ui.edit(this, "隧道名称（如 web / ssh，不可重复）", 1, false, false);
        final EditText port = Ui.edit(this, "本地端口（如 8080）", 1, false, false);
        final EditText host = Ui.edit(this, "自定义域名（可选，付费套餐）", 1, false, false);
        final EditText remote = Ui.edit(this, "保留 TCP 地址（可选，付费套餐）", 1, false, false);
        name.setText(t.name);
        port.setText(t.addr);
        host.setText(t.hostname);
        remote.setText(t.remoteAddr);
        box.addView(Ui.label(this, "名称"));
        box.addView(name);
        box.addView(Ui.label(this, "本地端口"));
        box.addView(port);
        box.addView(Ui.label(this, "协议 / 地区"));
        final String[] protos = {"http", "tcp", "udp"};
        final String[] regions = {"cn", "cn_vip", "us", "hk"};
        final int[] pIdx = {0}, rIdx = {0};
        for (int i = 0; i < protos.length; i++) if (protos[i].equals(t.proto)) pIdx[0] = i;
        for (int i = 0; i < regions.length; i++) if (regions[i].equals(t.region)) rIdx[0] = i;
        final TextView protoTv = new TextView(this);
        protoTv.setText("协议：" + t.proto + "    地区：" + t.region + "   （点击切换）");
        protoTv.setTextSize(12);
        protoTv.setTextColor(Ui.BLUE);
        protoTv.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        protoTv.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new GlassDialog.Builder(MainActivity.this)
                        .setTitle("协议")
                        .setSingleChoiceItems(protos, pIdx[0], new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                pIdx[0] = w;
                                protoTv.setText("协议：" + protos[w] + "    地区：" + regions[rIdx[0]]
                                        + "   （点击切换）");
                            }
                        })
                        .setNegativeButton("取消", null).show();
            }
        });
        box.addView(protoTv);
        final TextView regionTv = new TextView(this);
        regionTv.setText("切换地区（cn / cn_vip / us / hk）");
        regionTv.setTextSize(12);
        regionTv.setTextColor(Ui.BLUE);
        regionTv.setPadding(0, 0, 0, Ui.dp(this, 8));
        regionTv.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new GlassDialog.Builder(MainActivity.this)
                        .setTitle("地区")
                        .setTip("默认不填为 us；国内建议 cn 或 cn_vip", 2)
                        .setSingleChoiceItems(regions, rIdx[0], new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                rIdx[0] = w;
                                protoTv.setText("协议：" + protos[pIdx[0]] + "    地区：" + regions[w]
                                        + "   （点击切换）");
                            }
                        })
                        .setNegativeButton("取消", null).show();
            }
        });
        box.addView(regionTv);
        box.addView(Ui.label(this, "付费固定地址（可留空）"));
        box.addView(host);
        box.addView(remote);

        GlassDialog.Builder b = new GlassDialog.Builder(this)
                .setTitle(edit ? "编辑隧道" : "添加隧道")
                .setSubtitle("对应 cpolar.yml 里 tunnels 下的一个段")
                .setView(box)
                .setPositiveButton(edit ? "保存" : "添加", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        CpolarTunnel n = new CpolarTunnel();
                        n.name = CpolarTunnel.safeName(name.getText().toString());
                        n.addr = port.getText().toString().trim();
                        n.proto = protos[pIdx[0]];
                        n.region = regions[rIdx[0]];
                        n.hostname = host.getText().toString().trim();
                        n.remoteAddr = remote.getText().toString().trim();
                        if (!n.valid()) { toast("请填写合法名称与 1-65535 端口"); return; }
                        for (int i = 0; i < tunnels.size(); i++) {
                            if (i != idx && tunnels.get(i).name.equals(n.name)) {
                                toast("隧道名称不能重复"); return;
                            }
                        }
                        if (edit) tunnels.set(idx, n); else tunnels.add(n);
                        persistAll();
                        refreshTunnels();
                        toast(edit ? "已保存" : "已添加：" + n.name);
                    }
                });
        if (edit) b.setNeutralButton("删除", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int w) {
                tunnels.remove(idx);
                persistAll();
                refreshTunnels();
                toast("已删除");
            }
        });
        b.setNegativeButton("取消", null).show();
    }

    private void clearTunnels() {
        new GlassDialog.Builder(this)
                .setTitle("清空所有隧道")
                .setMessage("将删除本机保存的全部隧道定义（不影响官方面板）。")
                .setPositiveButton("确认清空", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        tunnels.clear();
                        persistAll();
                        refreshTunnels();
                        toast("已清空");
                    }
                })
                .setNegativeButton("取消", null).show();
    }

    // =============================================================== 地址展示

    private void refreshUrls() {
        urlBox.removeAllViews();
        List<CpolarUrls.U> urls = CpolarUrls.deserialize(sp.getString("urls", ""));
        if (urls.isEmpty()) {
            TextView e = new TextView(this);
            e.setText("启动隧道后，公网地址会自动出现在这里（stdout + 本机 4040 双通道）");
            e.setTextSize(11);
            e.setTextColor(Ui.MUTED);
            urlBox.addView(e, Ui.gap(this, 4));
            return;
        }
        for (int i = 0; i < urls.size(); i++) {
            final CpolarUrls.U u = urls.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(Ui.roundStroke(Ui.alpha(Ui.CARD, Ui.GLASS ? 0x70 : 0xFF),
                    Ui.LINE, 1, 12, this));
            int p = Ui.dp(this, 10);
            row.setPadding(p, p, p, p);
            LinearLayout tx = new LinearLayout(this);
            tx.setOrientation(LinearLayout.VERTICAL);
            TextView n = new TextView(this);
            n.setText(u.url);
            n.setTextSize(12);
            n.setTypeface(Typeface.DEFAULT_BOLD);
            n.setTextColor(u.web() ? Ui.BLUE : Ui.TEXT);
            n.setTextIsSelectable(true);
            tx.addView(n);
            TextView sub = new TextView(this);
            sub.setText((u.proto.length() > 0 ? u.proto : "http")
                    + (u.name.length() > 0 ? " · " + u.name : "")
                    + (u.local.length() > 0 ? " · " + u.local : ""));
            sub.setTextSize(10);
            sub.setTextColor(Ui.MUTED);
            tx.addView(sub);
            row.addView(tx, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView cp = new TextView(this);
            cp.setText("复制");
            cp.setTextSize(12);
            cp.setTextColor(Ui.BLUE);
            cp.setPadding(Ui.dp(this, 8), 0, 0, 0);
            cp.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { copy(u.url); }
            });
            row.addView(cp);
            if (u.web()) {
                TextView op = new TextView(this);
                op.setText("打开");
                op.setTextSize(12);
                op.setTextColor(Ui.BLUE);
                op.setPadding(Ui.dp(this, 8), 0, 0, 0);
                op.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { open(u.url, "已打开公网地址"); }
                });
                row.addView(op);
            }
            urlBox.addView(row, Ui.gap(this, 6));
        }
    }

    // =============================================================== 弹窗：主题 / 免责 / 作者 / 关于

    private void themeDialog() {
        final CharSequence[] items = new CharSequence[Theme.count()];
        for (int i = 0; i < Theme.count(); i++) {
            Theme.Def d = Theme.of(i);
            items[i] = (i == themeId ? "✓  " : "   ") + d.name
                    + (d.glass ? "  ◈ 玻璃" : d.dark ? "  ☾ 深色" : "  ☀ 浅色")
                    + "\n   " + d.desc;
        }
        new GlassDialog.Builder(this)
                .setTitle("选择主题（" + Theme.count() + " 套 · 5 套液态玻璃）")
                .setSubtitle("当前：" + Theme.of(themeId).name)
                .setTip("所有弹窗都会按当前主题套用动态液态半透明玻璃", 0)
                .setSingleChoiceItems(items, themeId, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        sp.edit().putInt("themeId", w).commit();
                        themeId = w;
                        Ui.apply(Theme.resolve(MainActivity.this, w));
                        recreate();
                    }
                })
                .setNegativeButton("取消", null).show();
    }

    private void showDisclaimer(final boolean first) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 4);
        box.setPadding(pad, pad, pad, pad);
        box.addView(Ui.osTip(this,
                "一句话：本 App 只负责在你手机本地启动 cpolar 官方客户端、管理配置、显示地址；"
                + "客户端本身会连接 cpolar 官方服务。", 2), Ui.gap(this, 10));
        TextView t = new TextView(this);
        t.setText(Disclaimer.SHORT);
        t.setTextSize(12);
        t.setTextColor(Ui.TEXT);
        t.setLineSpacing(Ui.dp(this, 4), 1f);
        box.addView(t);
        TextView src = new TextView(this);
        src.setText("\n二进制来源\n" + Disclaimer.BINARY_FROM);
        src.setTextSize(10);
        src.setTextColor(Ui.MUTED);
        box.addView(src, Ui.gap(this, 10));

        GlassDialog.Builder b = new GlassDialog.Builder(this)
                .setTitle("免责声明 · 使用前请阅读")
                .setSubtitle("第三方本地启动器 · 非官方客户端")
                .setView(box)
                .setPositiveButton("我已阅读并同意", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        Disclaimer.agree(MainActivity.this);
                        if (first) {
                            toast("感谢确认 · 设置页可随时重看");
                            CpolarService.append(MainActivity.this, "[声明] 已阅读并同意免责声明");
                        }
                    }
                })
                .setNeutralButton("查看完整条款", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        d.dismiss();
                        fullDisclaimer();
                    }
                });
        if (first) b.setCancelable(false);
        else b.setNegativeButton("关闭", null);
        b.show();
    }

    private void fullDisclaimer() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 4);
        box.setPadding(pad, pad, pad, pad);
        TextView t = new TextView(this);
        t.setText(Disclaimer.full());
        t.setTextSize(12);
        t.setTextColor(Ui.TEXT);
        t.setLineSpacing(Ui.dp(this, 4), 1f);
        box.addView(t);
        new GlassDialog.Builder(this)
                .setTitle("完整免责声明")
                .setTip("可下滑查看全部六节", 2)
                .setView(box)
                .setPositiveButton("我已阅读并同意", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        Disclaimer.agree(MainActivity.this);
                        toast("已记录");
                    }
                })
                .setNeutralButton("复制条款", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { copy(Disclaimer.full()); }
                })
                .setNegativeButton("关闭", null).show();
    }

    private void hostsDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 4);
        box.setPadding(pad, pad, pad, pad);
        box.addView(Ui.osTip(this,
                "本启动器自身只访问本机 4040；下列地址由官方 cpolar 客户端连接。"
                + "可在系统「应用联网管理」里核对。", 2), Ui.gap(this, 10));
        for (String x : Disclaimer.hosts())
            box.addView(Ui.osRow(this, x, "", "", true), Ui.match());
        new GlassDialog.Builder(this)
                .setTitle("网络与隐私 · 域名清单")
                .setView(box)
                .setPositiveButton("知道了", null)
                .setNeutralButton("完整声明", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { fullDisclaimer(); }
                }).show();
    }

    /** v1.2.0：内置官方落地页直达 QQ 加好友页，零联网、零剪贴板写入。 */
    private void openQq() {
        final String uin = sp.getString("qqUin", "").trim();
        final String landing = sp.getString("qqLanding", QqHelper.QQ_LANDING_DEFAULT);
        final String rawUin = sp.getString("qqRawUin", QqHelper.QQ_RAWUIN_DEFAULT);
        final String cardType = sp.getString("qqCardType", QqHelper.QQ_CARDTYPE_DEFAULT);
        QqHelper.Result r = QqHelper.launch(this, landing, rawUin, cardType, uin);
        toast(r.message);
        CpolarService.append(this, "[作者] " + r.detail + " · " + r.message + " · 本机直达（无需联网）");
    }

    /** 可选：联网向 QQ 官方刷新落地页，失败也不影响内置直达。 */
    private void refreshQqLanding() {
        toast("正在向 QQ 官方解析加好友页…");
        new Thread(new Runnable() {
            @Override public void run() {
                final String enc = QqHelper.resolveNetwork().encode();
                h.post(new Runnable() {
                    @Override public void run() {
                        QqHelper.Resolve res = QqHelper.Resolve.decode(enc);
                        if ("offline".equals(res.source)) {
                            toast("解析失败，已保留内置加好友页，仍可直达");
                            CpolarService.append(MainActivity.this,
                                    "[作者] 加好友页刷新失败，继续使用内置落地页");
                            return;
                        }
                        sp.edit().putString("qqLanding", res.landing)
                                .putString("qqRawUin", res.rawUin)
                                .putString("qqCardType", res.cardType)
                                .putString("qqResolveSource", res.source).commit();
                        toast("已刷新 QQ 官方加好友页");
                        CpolarService.append(MainActivity.this,
                                "[作者] 已刷新官方落地页 · 来源=" + res.source);
                    }
                });
            }
        }, "qq-resolve").start();
    }

    /** 弹窗里独立的「用浏览器打开」，不冒充 QQ 跳转、不复制。 */
    private void openQqInBrowser() {
        QqHelper.Result r = QqHelper.openBrowser(this);
        toast(r.message);
        CpolarService.append(this, "[作者] " + r.detail + " · " + r.message);
    }

    private void authorDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 4);
        box.setPadding(pad, pad, pad, pad);
        box.addView(Ui.osTitle(this, "联系作者", "v1.2.0"), Ui.gap(this, 8));
        box.addView(Ui.osTip(this,
                "已修复「装了 QQ 却跳浏览器」：现在走 QQ 官方落地页的 Android 通道"
                + "（mqqopensdkapi://bizAgent/qm/qr）直达加好友页，落地页内置、点击无需联网。"
                + "也不会再自动复制链接 —— 只有点「复制链接」才写剪贴板。", 2),
                Ui.gap(this, 10));

        box.addView(Ui.section(this, "本机检测"), Ui.gap(this, 8));
        box.addView(Ui.osRow(this, QqHelper.status(this), QqHelper.diagnose(this),
                "", true), Ui.match());
        String qs = sp.getString("qqResolveSource", "");
        box.addView(Ui.osRow(this, "加好友页解析",
                "内置官方落地页（带 k）· 点击直达，无需联网"
                        + (qs.length() > 0 ? " · 已刷新（" + qs + "）" : ""),
                "", false), Ui.match());
        box.addView(Ui.button(this, "↻ 刷新加好友页（联网解析一次）", 2,
                new View.OnClickListener() {
                    @Override public void onClick(View v) { refreshQqLanding(); }
                }), Ui.gap(this, 6));

        box.addView(Ui.section(this, "作者 QQ 号（可选，直达指定资料卡）"), Ui.gap(this, 8));
        final EditText etUin = Ui.edit(this,
                "填纯数字 QQ 号，留空则走官方加好友页", 1, false, false);
        etUin.setText(sp.getString("qqUin", ""));
        box.addView(etUin, Ui.gap(this, 4));
        TextView uinTip = new TextView(this);
        uinTip.setText("留空即可：会用官方短链直达；填了号码则优先打开该 QQ 的资料卡。");
        uinTip.setTextSize(10);
        uinTip.setTextColor(Ui.MUTED);
        box.addView(uinTip);

        box.addView(Ui.section(this, "QQ 加好友链接"), Ui.gap(this, 8));
        TextView url = new TextView(this);
        url.setText(QqHelper.QQ_URL);
        url.setTextSize(12);
        url.setTextColor(Ui.BLUE);
        url.setTextIsSelectable(true);
        url.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8));
        box.addView(url);
        box.addView(Ui.osRow(this, "用途", "问题反馈 / 功能建议 / 隧道排障", "", true), Ui.match());
        box.addView(Ui.osRow(this, "隐私",
                "点击只跳转 QQ / 浏览器，本 App 不收集、不上传任何设备或账号信息", "", false),
                Ui.match());
        // 全 App 唯一写 QQ 链接到剪贴板的地方，且 3 秒内重复点不重复写
        box.addView(Ui.button(this, "复制链接", 2, new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean wrote = QqHelper.copyLink(MainActivity.this);
                toast(wrote ? "已复制加好友链接" : "链接已在剪贴板，无需重复复制");
            }
        }), Ui.gap(this, 6));

        new GlassDialog.Builder(this)
                .setTitle("✉ 联系作者")
                .setSubtitle("直达 QQ 加好友页 · 不再自动复制")
                .setView(box)
                .setPositiveButton("打开 QQ 加好友", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        sp.edit().putString("qqUin", etUin.getText().toString().trim()).commit();
                        openQq();
                    }
                })
                .setNeutralButton("用浏览器打开", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { openQqInBrowser(); }
                })
                .setNegativeButton("关闭", null).show();
    }

    /** v1.2.0：检查更新缓存有效期 10 分钟，期间重复点击不重复联网。 */
    private static final long UPDATE_CACHE_MS = 10L * 60 * 1000;

    /** 设置页「版本更新」卡里的「上次检查」行，检查完即时刷新。 */
    TextView tvLastCheck;

    /** v1.2.0：手动检查更新，连接本项目 GitHub Releases（只读取，不上传）。 */
    private void checkUpdate() { checkUpdate(false); }

    /** 10 分钟内复用上次结果，避免重复联网；force=true 时强制刷新。 */
    private void checkUpdate(final boolean force) {
        final long at = sp.getLong("updateAt", 0L);
        String cached = sp.getString("updateJson", "");
        if (!force && cached.length() > 0 && System.currentTimeMillis() - at < UPDATE_CACHE_MS) {
            showUpdateResult(UpdateChecker.parse(cached), true, at);
            return;
        }
        toast(force ? "正在强制刷新 GitHub Release…" : "正在连接 GitHub 检查更新…");
        UpdateChecker.checkAsync(new UpdateChecker.Callback() {
            @Override public void done(final UpdateChecker.Info info) {
                if (info.ok && info.raw.length() > 0) {
                    sp.edit().putString("updateJson", info.raw)
                            .putLong("updateAt", System.currentTimeMillis()).commit();
                    updateLastCheckLabel();
                }
                showUpdateResult(info, false, System.currentTimeMillis());
            }
        });
    }

    private String timeAgo(long at) {
        if (at <= 0L) return "尚未检查";
        long d = System.currentTimeMillis() - at;
        if (d < 60000L) return "刚刚";
        if (d < 3600000L) return (d / 60000L) + " 分钟前";
        return android.text.format.DateFormat.getTimeFormat(this)
                .format(new java.util.Date(at));
    }

    private void updateLastCheckLabel() {
        if (tvLastCheck != null)
            tvLastCheck.setText("上次检查：" + timeAgo(sp.getLong("updateAt", 0L))
                    + " · 10 分钟内重复点不重复联网");
    }

    private void openGithub(String url) {
        if (!UpdateChecker.open(MainActivity.this, url)) toast("无法打开浏览器");
    }

    /** MIT 许可全文内联展示，不必跳浏览器。 */
    private static final String MIT_LICENSE =
            "MIT License\n\nCopyright (c) 2026 lyu323\n\n"
          + "Permission is hereby granted, free of charge, to any person obtaining a copy\n"
          + "of this software and associated documentation files (the \"Software\"), to deal\n"
          + "in the Software without restriction, including without limitation the rights\n"
          + "to use, copy, modify, merge, publish, distribute, sublicense, and/or sell\n"
          + "copies of the Software, and to permit persons to whom the Software is\n"
          + "furnished to do so, subject to the following conditions:\n\n"
          + "The above copyright notice and this permission notice shall be included in all\n"
          + "copies or substantial portions of the Software.\n\n"
          + "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR\n"
          + "IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,\n"
          + "FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE\n"
          + "AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER\n"
          + "LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,\n"
          + "OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE\n"
          + "SOFTWARE.\n\n"
          + "注：第三方内核二进制不在本许可范围内，权利归其原作者所有。";

    private void licenseDialog() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        b.addView(Ui.osTitle(this, "开源许可声明", "MIT"), Ui.gap(this, 8));
        b.addView(Ui.osTip(this,
                "本 App 自己编写的 Java 源码、资源、构建脚本与文档采用 MIT 许可。"
                + "第三方内核二进制与官方服务条款不在本许可范围内。", 2), Ui.gap(this, 10));
        TextView t = new TextView(this);
        t.setText(MIT_LICENSE);
        t.setTextSize(11);
        t.setTextColor(Ui.TEXT);
        t.setTextIsSelectable(true);
        b.addView(t, Ui.gap(this, 8));
        new GlassDialog.Builder(this)
                .setTitle("MIT License")
                .setSubtitle("可下滑查看完整条款 · 文本已可选中")
                .setView(b)
                .setPositiveButton("知道了", null)
                .setNeutralButton("复制条款", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { copy(MIT_LICENSE); }
                })
                .setNegativeButton("在 GitHub 查看", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        openGithub(UpdateChecker.LICENSE_URL);
                    }
                }).show();
    }

    /** 长按版本号复制构建信息，方便直接发给作者排障。 */
    private void bindVersionLongClick(TextView v) {
        v.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View view) {
                copy("Cpolar 启动器 " + UpdateChecker.APP_VERSION + "\n"
                        + "包名 " + getPackageName() + "\n"
                        + "仓库 github.com/" + UpdateChecker.REPO + "\n"
                        + "versionCode " + "3");
                toast("已复制构建信息");
                return true;
            }
        });
    }

    /** 结果弹窗：和「设置 / 关于」同一套液态玻璃卡片。 */
    private void showUpdateResult(final UpdateChecker.Info info,
                                  final boolean cached, final long at) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        box.addView(Ui.osTitle(this, "检查更新", UpdateChecker.APP_VERSION), Ui.gap(this, 8));
        box.addView(Ui.osTip(this, info.status(), info.ok && info.newer ? 1 : 2), Ui.gap(this, 10));

        box.addView(Ui.section(this, "数据源"), Ui.gap(this, 8));
        box.addView(Ui.osRow(this, "GitHub 仓库", UpdateChecker.REPO, "", true), Ui.match());
        box.addView(Ui.osRow(this, "联网时机", "仅手动点「检查更新」时读取公开 Release 信息，不上传任何数据", "", true), Ui.match());
        box.addView(Ui.osRow(this, "本机版本", UpdateChecker.APP_VERSION + " · " + getPackageName(), "", true), Ui.match());
        box.addView(Ui.osRow(this, cached ? "本次结果" : "上次检查",
                cached ? "使用了 10 分钟内缓存（" + timeAgo(at) + "）" : "刚刚联网读取", "", false), Ui.match());
        if (info.ok) {
            box.addView(Ui.section(this, "最新版本"), Ui.gap(this, 8));
            String title = info.tag + (info.name.length() > 0 ? " · " + info.name : "");
            box.addView(Ui.osRow(this, "Release", title, "", true), Ui.match());
            box.addView(Ui.osRow(this, "下载页", info.htmlUrl, "", false), Ui.match());
            if (info.body.length() > 0) {
                String brief = info.body.length() > 420 ? info.body.substring(0, 420) + "…" : info.body;
                box.addView(Ui.section(this, "更新说明"), Ui.gap(this, 8));
                box.addView(Ui.osTip(this, brief, 0), Ui.gap(this, 8));
            }
            box.addView(Ui.row(this,
                    Ui.button(this, "复制 APK 下载链接", 2, new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            if (info.apkUrl.length() == 0) { toast("该 Release 暂无 APK 资产"); return; }
                            copy(info.apkUrl); toast("已复制 APK 下载直链");
                        }
                    }),
                    Ui.button(this, "↻ 强制刷新", 2, new View.OnClickListener() {
                        @Override public void onClick(View v) { checkUpdate(true); }
                    })), Ui.gap(this, 8));
        }

        final String page = info.ok && info.htmlUrl.length() > 0
                ? info.htmlUrl : UpdateChecker.LATEST_URL;
        final String apk = info.apkUrl.length() > 0 ? info.apkUrl : UpdateChecker.RELEASES_URL;
        new GlassDialog.Builder(this)
                .setTitle(info.ok && info.newer ? "发现新版本" : "检查更新")
                .setSubtitle(cached ? "本机缓存 · 未消耗流量" : "连接本项目 GitHub Releases · 只读取版本信息")
                .setView(box)
                .setPositiveButton("打开 Releases / 下载页", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { openGithub(page); }
                })
                .setNeutralButton(info.ok && info.apkUrl.length() > 0 ? "直接下载 APK" : "项目主页",
                        new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        openGithub(info.ok && info.apkUrl.length() > 0 ? apk : UpdateChecker.REPO_URL);
                    }
                })
                .setNegativeButton("关闭", null).show();
    }

    /** 图二风格的项目 / 更新行，设置页与关于页共用。 */
    private void addProjectRows(LinearLayout box) {
        box.addView(Ui.section(this, "项目与更新"), Ui.gap(this, 8));
        View r1 = Ui.osRow(this, "检查更新", "连接本项目 GitHub Releases，手动检查新版本（10 分钟缓存）", "", true);
        r1.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { checkUpdate(); }
        });
        box.addView(r1, Ui.match());
        View r2 = Ui.osRow(this, "项目地址", UpdateChecker.REPO_URL, "", true);
        r2.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openGithub(UpdateChecker.REPO_URL); }
        });
        box.addView(r2, Ui.match());
        View r3 = Ui.osRow(this, "在 GitHub 点个 Star", UpdateChecker.STAR_URL, "", true);
        r3.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openGithub(UpdateChecker.STAR_URL); }
        });
        box.addView(r3, Ui.match());
        View r4 = Ui.osRow(this, "更新日志", "CHANGELOG.md", "", true);
        r4.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openGithub(UpdateChecker.CHANGELOG_URL); }
        });
        box.addView(r4, Ui.match());
        View r5 = Ui.osRow(this, "开源许可声明", "MIT · 点开看全文（不必跳浏览器）", "", true);
        r5.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { licenseDialog(); }
        });
        box.addView(r5, Ui.match());
        View r6 = Ui.osRow(this, "同账号下的另一个项目", UpdateChecker.OTHER_REPO, "", false);
        r6.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openGithub(UpdateChecker.OTHER_REPO_URL); }
        });
        box.addView(r6, Ui.match());
    }

    private void aboutDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 4);
        box.setPadding(pad, pad, pad, pad);
        box.addView(Ui.osTitle(this, "使用帮助", "v1.2.0"), Ui.gap(this, 8));
        box.addView(Ui.osTip(this,
                "本启动器按 cpolar 官方文档实现：authtoken 认证、cpolar.yml 多隧道、"
                + "start-all 一次启动、本机 4040 检查接口获取公网地址。", 2), Ui.gap(this, 10));
        box.addView(Ui.section(this, "使用流程"));
        box.addView(Ui.osRow(this, "① 复制 authtoken", "官方面板 → 验证 → 复制令牌", "", true), Ui.match());
        box.addView(Ui.osRow(this, "② 添加隧道", "名称 / 协议 / 本地端口 / 地区，可加多条", "", true), Ui.match());
        box.addView(Ui.osRow(this, "③ 生成配置并启动", "自动写 cpolar.yml，start-all 一次跑全部", "", true), Ui.match());
        box.addView(Ui.osRow(this, "④ 复制公网地址", "控制台自动显示，可打开 / 复制 / 分享", "", false), Ui.match());
        box.addView(Ui.section(this, "实现要点"));
        box.addView(Ui.osTip(this,
                "· 客户端来自 GitHub 仓库 cmilove92/cpolar 内附的官方 ARM 二进制（闭源，2022）\n"
                + "· Android 10+ 从 nativeLibraryDir 执行 libcpolar.so，不放 filesDir\n"
                + "· 环境变量指向系统 CA 目录 /system/etc/security/cacerts 以适配 Android TLS\n"
                + "· 免费版随机域名约 24 小时变化；固定域名需在官方面板配置\n"
                + "· 21 套主题、动态玻璃弹窗、通知栏、隐藏后台沿用历史启动器", 0), Ui.gap(this, 10));
        addProjectRows(box);
        box.addView(Ui.section(this, "v1.2.0 新增（本版）"));
        box.addView(Ui.osTip(this,
                "· 修复「装了 QQ 却跳浏览器」：补齐包可见性声明，改用 QQ 官方落地页的 Android 通道"
                + " mqqopensdkapi://bizAgent/qm/qr 直达加好友页，内置官方落地页零联网\n"
                + "· 不再自动复制链接：只有点「复制链接」才写剪贴板，3 秒内不重复写\n"
                + "· 联系作者弹窗新增本机检测、可选作者 QQ 号、独立「用浏览器打开」按钮\n"
                + "· 统一改用 net.lyu323.cpolar 包名，与开源仓库归属一致\n"
                + "· 检查更新加 10 分钟缓存、强制刷新、复制 APK 下载直链、上次检查时间\n"
                + "· 关于页 MIT 许可内联弹窗、长按版本号复制构建信息", 0), Ui.gap(this, 10));

        box.addView(Ui.section(this, "v1.2.0 新增"));
        box.addView(Ui.osTip(this,
                "· 新增「检查更新」：连接本项目 GitHub Releases，手动点击才联网，只读取版本信息\n"
                + "· 设置页「版本更新」卡片：检查更新 / 项目地址 / Star / 更新日志\n"
                + "· 关于页新增项目与更新行，含另一个项目仓库入口\n"
                + "· 免责声明与域名清单如实补充 GitHub 更新检查（不点不联网）", 0), Ui.gap(this, 10));
        TextView v = new TextView(this);
        v.setText("\n版本 1.2.0 · 包名 net.lyu323.cpolar\n"
                + "内含 cpolar 官方闭源客户端，版权归官方所有");
        v.setTextSize(10);
        v.setTextColor(Ui.MUTED);
        box.addView(v);
        bindVersionLongClick(v);
        TextView verTip = new TextView(this);
        verTip.setText("长按版本号可复制构建信息（版本 / 包名 / 仓库）");
        verTip.setTextSize(10);
        verTip.setTextColor(Ui.MUTED);
        box.addView(verTip);
        new GlassDialog.Builder(this)
                .setTitle("使用帮助 / 关于")
                .setView(box)
                .setPositiveButton("知道了", null)
                .setNeutralButton("免责声明", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { fullDisclaimer(); }
                })
                .setNegativeButton("域名清单", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { hostsDialog(); }
                }).show();
    }

    private void permissionsDialog() {
        final List<Perm.Item> items = Perm.check(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 4);
        box.setPadding(pad, pad, pad, pad);
        box.addView(Ui.osTip(this, Perm.summary(items), 2), Ui.gap(this, 10));
        for (Perm.Item it : items)
            box.addView(Ui.osRow(this, (it.ok ? "✓  " : "!  ") + it.name, it.state + " · " + it.tip,
                    it.ok ? "已就绪" : "待处理", true), Ui.match());
        new GlassDialog.Builder(this)
                .setTitle("权限体检")
                .setView(box)
                .setPositiveButton("自动申请可授权项", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        toast(Perm.autoGrant(MainActivity.this, items));
                    }
                })
                .setNeutralButton("电池优化", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        Perm.requestBatteryWhitelist(MainActivity.this);
                    }
                })
                .setNegativeButton("关闭", null).show();
    }

    private void logSearchDialog() {
        final EditText e = Ui.edit(this, "输入关键词，如 Forwarding / authentication / ERROR",
                1, false, false);
        e.setText(logFilter);
        new GlassDialog.Builder(this)
                .setTitle("搜索日志")
                .setView(e)
                .setPositiveButton("筛选", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        logFilter = e.getText().toString().trim();
                        logPos = 0;
                        tvLog.setText("");
                        refreshLog();
                        toast(logFilter.length() > 0 ? "已筛选：" + logFilter : "已显示全部");
                    }
                })
                .setNegativeButton("取消", null).show();
    }

    // =============================================================== 文件导入

    private void pickFile(int req) {
        try {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("*/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(i, "选择文件"), req);
        } catch (Exception e) { toast("无法打开文件选择器"); }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        if (req == REQ_YML) importYml(data.getData());
        else if (req == REQ_KERNEL) importKernel(data.getData());
    }

    private void importYml(Uri u) {
        try {
            String s = readUri(u, 262144);
            String err = CpolarConfig.validate(s);
            if (err.length() > 0) { toast("YAML 校验失败：" + err); return; }
            etYaml.setText(s);
            CpolarConfig.write(this, s);
            String tk = CpolarConfig.tokenOf(s);
            if (tk.length() > 0) { etToken.setText(tk); sp.edit().putString("token", tk).commit(); }
            tunnels.clear();
            tunnels.addAll(CpolarConfig.parse(s));
            persistAll();
            refreshTunnels();
            toast("已导入配置：" + tunnels.size() + " 条隧道");
        } catch (Exception e) { toast("导入失败：" + e.getMessage()); }
    }

    private void importKernel(Uri u) {
        try {
            InputStream is = getContentResolver().openInputStream(u);
            byte[] head = new byte[4];
            int n = is.read(head);
            is.close();
            boolean zip = n >= 2 && head[0] == 'P' && head[1] == 'K';
            File out = new File(getFilesDir(), "bin/cpolar");
            out.getParentFile().mkdirs();
            if (zip) {
                ZipInputStream zis = new ZipInputStream(getContentResolver().openInputStream(u));
                ZipEntry ze;
                boolean found = false;
                while ((ze = zis.getNextEntry()) != null) {
                    String nm = ze.getName().toLowerCase();
                    if (ze.isDirectory() || !(nm.endsWith("cpolar") || nm.endsWith("cpolar.so"))) continue;
                    FileOutputStream os = new FileOutputStream(out);
                    byte[] buf = new byte[8192];
                    int k;
                    while ((k = zis.read(buf)) > 0) os.write(buf, 0, k);
                    os.close();
                    found = true;
                    break;
                }
                zis.close();
                if (!found) { toast("zip 里没找到 cpolar 可执行文件"); return; }
            } else {
                FileOutputStream os = new FileOutputStream(out);
                InputStream in = getContentResolver().openInputStream(u);
                byte[] buf = new byte[8192];
                int k;
                while ((k = in.read(buf)) > 0) os.write(buf, 0, k);
                in.close();
                os.close();
            }
            out.setExecutable(true, false);
            toast("已导入内核：" + (out.length() / 1024 / 1024) + " MB");
            CpolarService.append(this, "[内核] 已导入自定义客户端：" + out.length() + " 字节");
            refreshAll();
        } catch (Exception e) { toast("导入失败：" + e.getMessage()); }
    }

    private String readUri(Uri u, int max) throws Exception {
        InputStream is = getContentResolver().openInputStream(u);
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[4096];
        int n, total = 0;
        while ((n = is.read(b)) > 0) {
            bos.write(b, 0, n);
            total += n;
            if (total > max) break;
        }
        is.close();
        return new String(bos.toByteArray(), "UTF-8");
    }

    // =============================================================== 数据持久化

    private void persistAll() {
        String token = etToken.getText().toString().trim();
        sp.edit().putString("token", token)
                .putString("tunnels", CpolarConfig.serialize(tunnels)).commit();
        String yml = CpolarConfig.build(token, tunnels);
        etYaml.setText(yml);
        try { CpolarConfig.write(this, yml); } catch (Exception ignored) {}
        savePrefs();
    }

    private void saveYaml() {
        String s = etYaml.getText().toString();
        String err = CpolarConfig.validate(s);
        if (err.length() > 0) { toast("配置有误：" + err); return; }
        try {
            CpolarConfig.write(this, s);
            String tk = CpolarConfig.tokenOf(s);
            if (tk.length() > 0) { etToken.setText(tk); sp.edit().putString("token", tk).commit(); }
            tunnels.clear();
            tunnels.addAll(CpolarConfig.parse(s));
            sp.edit().putString("tunnels", CpolarConfig.serialize(tunnels)).commit();
            refreshTunnels();
            toast("已保存：" + tunnels.size() + " 条隧道");
        } catch (Exception e) { toast("保存失败：" + e.getMessage()); }
    }

    private void savePrefs() {
        sp.edit()
                .putString("token", etToken == null ? "" : etToken.getText().toString().trim())
                .putString("yml", etYaml == null ? "" : etYaml.getText().toString())
                .putString("tunnels", CpolarConfig.serialize(tunnels))
                .putBoolean("autostart", cbAutostart != null && cbAutostart.isChecked())
                .putBoolean("webui", cbWebUI == null || cbWebUI.isChecked())
                .putBoolean("notifyEvents", cbNotifyEvents == null || cbNotifyEvents.isChecked())
                .putBoolean("notifyThemeColor", cbThemeColor == null || cbThemeColor.isChecked())
                .putInt("themeId", themeId)
                .putInt("page", page)
                .commit();
    }

    private void loadPrefs() {
        etToken.setText(sp.getString("token", ""));
        tunnels.clear();
        tunnels.addAll(CpolarConfig.deserialize(sp.getString("tunnels", "")));
        String yml = sp.getString("yml", "");
        if (yml.length() == 0) yml = CpolarConfig.read(this);
        if (yml.length() == 0) yml = CpolarConfig.demo(etToken.getText().toString().trim());
        etYaml.setText(yml);
        if (cbAutostart != null) cbAutostart.setChecked(sp.getBoolean("autostart", false));
        if (cbWebUI != null) cbWebUI.setChecked(sp.getBoolean("webui", true));
        if (cbNotifyEvents != null) cbNotifyEvents.setChecked(sp.getBoolean("notifyEvents", true));
        if (cbThemeColor != null) cbThemeColor.setChecked(sp.getBoolean("notifyThemeColor", true));
        syncPrivacy();
        refreshTunnels();
        refreshUrls();
    }

    // =============================================================== 刷新 / ticker

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            refreshAll();
            h.postDelayed(this, 2000);
        }
    };

    private void refreshAll() {
        boolean run = CpolarService.isRunning() || sp.getBoolean("running", false);
        if (tvStatus != null) {
            tvStatus.setText(run ? "● 运行中" : "○ 未运行");
            tvStatus.setTextColor(0xFFFFFFFF);
            tvStatus.setBackground(Ui.round(run ? 0xFF2E7D32 : 0x66FFFFFF, 12, this));
        }
        if (btnPill != null) btnPill.setText(run ? "● 运行中" : "○ 未运行");
        if (tvUptime != null) {
            if (run && CpolarService.startAt() > 0)
                tvUptime.setText("已运行 " + fmt(System.currentTimeMillis()
                        - CpolarService.startAt()) + " · ↑↓ 由客户端转发");
            else tvUptime.setText("未运行 · 本地服务端口不会被暴露");
        }
        if (tvTunnelCount != null)
            tvTunnelCount.setText("隧道 " + tunnels.size() + " 条 · "
                    + CpolarConfig.count(CpolarConfig.read(this)) + " 段已写入配置");
        if (tvAddrCount != null)
            tvAddrCount.setText("公网地址 "
                    + CpolarUrls.deserialize(sp.getString("urls", "")).size() + " 个");
        if (tvBinary != null) tvBinary.setText(CpolarService.binaryInfo(this));
        if (btnStart != null) btnStart.setText(run ? "●  运行中" : "▶  启动全部隧道");
        refreshLog();
        refreshUrls();
    }

    private void refreshLog() {
        if (tvLog == null) return;
        String all = CpolarService.readLog(this);
        if (all.length() < logPos) { logPos = 0; tvLog.setText(""); }
        String add = all.substring((int) logPos);
        logPos = all.length();
        if (add.length() == 0) return;
        StringBuilder keep = new StringBuilder();
        for (String ln : add.split("\\n")) {
            if (cbOnlyErrors != null && cbOnlyErrors.isChecked()
                    && !errorLine(ln)) continue;
            if (logFilter.length() > 0
                    && !ln.toLowerCase().contains(logFilter.toLowerCase())) continue;
            keep.append(ln).append('\n');
        }
        if (keep.length() == 0) return;
        tvLog.append(keep.toString());
        // 限制 UI 里显示的最近 400 行，避免大日志卡
        String cur = tvLog.getText().toString();
        String[] lines = cur.split("\\n");
        if (lines.length > 400) {
            StringBuilder b = new StringBuilder();
            for (int i = lines.length - 400; i < lines.length; i++)
                b.append(lines[i]).append('\n');
            tvLog.setText(b.toString());
        }
    }

    private static boolean errorLine(String s) {
        String l = s.toLowerCase();
        return l.contains("error") || l.contains("fail") || l.contains("错误")
                || l.contains("失败") || l.contains("token") || l.contains("认证")
                || l.contains("exit") || l.contains("panic");
    }

    private static String fmt(long ms) {
        long s = ms / 1000;
        return String.format("%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
    }

    private void handleNotifyIntent(Intent i) {
        if (i == null || !i.getBooleanExtra("fromNotify", false)) return;
        int p = i.getIntExtra("page", 0);
        if (p >= 0 && p <= 3) switchPage(p);
    }

    private void applyTheme(int id) {
        themeId = id;
        sp.edit().putInt("themeId", id).commit();
        Ui.apply(Theme.resolve(this, id));
        recreate();
    }

    private void toggleToken() {
        boolean secret = (etToken.getInputType() & android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0;
        if (secret) {
            etToken.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                    | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        } else {
            etToken.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                    | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        etToken.setSelection(etToken.getText().length());
    }

    private void pasteToken() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData d = cm.getPrimaryClip();
            if (d == null || d.getItemCount() == 0) { toast("剪贴板是空的"); return; }
            String s = String.valueOf(d.getItemAt(0).getText()).trim();
            // 兼容 "cpolar authtoken xxxx" 整行粘贴
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(?i)authtoken\\s+([A-Za-z0-9_\\-]{8,})").matcher(s);
            if (m.find()) s = m.group(1);
            if (s.length() < 8) { toast("剪贴板内容不像 authtoken"); return; }
            etToken.setText(s);
            persistAll();
            toast("已导入 authtoken（本机）");
        } catch (Exception e) { toast("读取剪贴板失败"); }
    }

    private void open(String url, String tip) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            toast(tip);
        } catch (Exception e) { copy(url); toast("无法打开，链接已复制"); }
    }

    private void copy(String s) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Cpolar", s));
            toast("已复制");
        } catch (Exception e) { toast("复制失败"); }
    }

    private String redact(String s) {
        if (s == null) return "";
        String t = s;
        String tok = sp.getString("token", "").trim();
        if (tok.length() > 4) t = t.replace(tok, "[TOKEN]");
        t = t.replaceAll("(?i)(authtoken\\s*[:=]\\s*)\\S+", "$1[TOKEN]");
        t = t.replaceAll("(https?://)[a-z0-9.\\-]+\\.cpolar\\.(top|cn|io)", "$1[DOMAIN]");
        t = t.replaceAll("(tcp://)[a-z0-9.\\-]+\\.cpolar\\.(cn|top):[0-9]+", "$1[ADDR]");
        return t + "\n\n（已脱敏：authtoken / 公网域名）";
    }

    private void shareLog() {
        String s = redact(CpolarService.readLog(this));
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, s);
            i.putExtra(Intent.EXTRA_SUBJECT, "Cpolar 启动器 运行日志");
            startActivity(Intent.createChooser(i, "分享日志"));
        } catch (Exception e) { toast("分享失败"); }
    }

    private void copyDiagnostics() {
        DeviceInfo.Info i = DeviceInfo.collect(this);
        StringBuilder b = new StringBuilder("Cpolar 启动器 运行诊断（已脱敏）\n");
        b.append("版本   1.2.0\n")
         .append("设备   ").append(i.brand).append(' ').append(i.model).append('\n')
         .append("系统   Android ").append(i.android).append(" · API ").append(i.sdkInt).append('\n')
         .append("ABI    ").append(i.primaryAbi).append('\n')
         .append("ROM    ").append(RomInfo.detect(this).name).append('\n')
         .append("内核   ").append(CpolarService.binaryInfo(this)).append('\n')
         .append("隧道   ").append(tunnels.size()).append(" 条\n")
         .append("地址   ").append(CpolarUrls.deserialize(sp.getString("urls", "")).size())
         .append(" 个\n")
         .append("状态   ").append(CpolarService.stateLabel()).append('\n')
         .append("\n不含 authtoken、配置内容与公网地址明文。");
        copy(b.toString());
    }

    private void toast(String s) {
        String t = s == null ? "" : s;
        if (t.length() > 42) t = t.substring(0, 42) + "…";
        Toast to = Toast.makeText(this, t, Toast.LENGTH_SHORT);
        try {
            to.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, Ui.dp(this, 110));
        } catch (Exception ignored) {}
        to.show();
    }
}
