package net.lyu323.cpolar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 纯代码绘制的轻量 UI 组件 + 多主题色板。零第三方依赖。 */
public final class Ui {
    public static boolean DARK = false;
    public static boolean GLASS = false;
    public static int THEME_ID = 0;

    public static int BLUE = 0xFF1565C0;
    public static int BLUE_DARK = 0xFF0D47A1;
    public static int BLUE_LIGHT = 0xFFE3F2FD;
    public static int BG = 0xFFF3F6FA;
    public static int CARD = 0xFFFFFFFF;
    public static int LINE = 0xFFDDE4ED;
    public static int TEXT = 0xFF263238;
    public static int MUTED = 0xFF78909C;
    public static int GREEN = 0xFF2E7D32;
    public static int RED = 0xFFC62828;
    public static int LOG_BG = 0xFF1E272C;
    public static int LOG_FG = 0xFFCFD8DC;
    public static int INPUT_BG = 0xFFFAFCFF;
    public static int PREVIEW_BG = 0xFFF5F8FC;
    public static int BAR_BG = 0xFFFFFFFF;
    public static int WARN = 0xFFED6C02;
    public static int HEADER_START = 0xFF0D47A1;
    public static int HEADER_END = 0xFF1565C0;

    private static int GLASS_CARD = 0;
    private static int GLASS_STROKE = 0;

    private Ui() {}

    /** 兼容旧调用。 */
    public static void apply(boolean dark) { apply(dark ? 1 : 0); }

    /** 把主题色板刷到静态色值；所有组件在 recreate 后自动跟随。 */
    public static void apply(int themeId) {
        THEME_ID = Theme.clamp(themeId);
        Theme.Def d = Theme.of(THEME_ID);
        DARK = d.dark;
        GLASS = d.glass;
        BG = d.bg;
        CARD = d.card;
        LINE = d.line;
        TEXT = d.text;
        MUTED = d.muted;
        BLUE = d.blue;
        BLUE_DARK = d.blueDark;
        BLUE_LIGHT = d.blueLight;
        GREEN = d.green;
        RED = d.red;
        LOG_BG = d.logBg;
        LOG_FG = d.logFg;
        INPUT_BG = d.inputBg;
        PREVIEW_BG = d.previewBg;
        BAR_BG = d.barBg;
        WARN = d.warn;
        HEADER_START = d.headerStart;
        HEADER_END = d.headerEnd;
        GLASS_CARD = d.glassCard;
        GLASS_STROKE = d.glassStroke;
    }

    public static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static GradientDrawable round(int fill, int radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    public static GradientDrawable roundStroke(int fill, int stroke, int w, int radiusDp, Context c) {
        GradientDrawable g = round(fill, radiusDp, c);
        g.setStroke(GLASS ? Math.max(w, dp(c, 1)) : w, stroke);
        return g;
    }

    /** 卡片：玻璃主题用半透明填充 + 高光描边 + 阴影，模拟液态玻璃折射。 */
    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        if (GLASS) {
            // v2.7：对齐 OriginOS 系统玻璃 —— 大圆角、更实的白卡、细高光描边
            int top = DARK ? tint(GLASS_CARD, 0x0CFFFFFF) : 0xEEFFFFFF;
            int bottom = DARK ? GLASS_CARD : 0xDDEDF4FA;
            GradientDrawable g = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR, new int[]{top, bottom});
            g.setCornerRadius(dp(c, 24));
            g.setStroke(dp(c, 1), DARK ? GLASS_STROKE : 0x99FFFFFF);
            l.setBackground(g);
            l.setElevation(dp(c, 6));
        } else {
            l.setBackground(roundStroke(CARD, LINE, 1, 16, c));
        }
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        return l;
    }

    private static int tint(int c, int add) {
        int a = c >>> 24;
        int r = Math.min(255, ((c >> 16) & 255) + ((add >> 16) & 255));
        int g = Math.min(255, ((c >> 8) & 255) + ((add >> 8) & 255));
        int b = Math.min(255, (c & 255) + (add & 255));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** 玻璃主题下的小面板（域名块、预览块）。 */
    public static GradientDrawable panel(Context c, int radiusDp) {
        if (GLASS) {
            GradientDrawable g = new GradientDrawable();
            g.setColor(GLASS_CARD);
            g.setCornerRadius(dp(c, radiusDp));
            g.setStroke(dp(c, 1), GLASS_STROKE);
            return g;
        }
        return round(PREVIEW_BG, radiusDp, c);
    }

    public static TextView section(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(12);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(MUTED);
        t.setPadding(dp(c, 4), dp(c, 14), 0, dp(c, 7));
        return t;
    }

    // ---------------------------------------------------------- OriginOS 风组件

    /** 大标题 + 右上角胶囊标签（对齐系统「高级功能」页头）。 */
    public static LinearLayout osTitle(Context c, String title, String chip) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView t = new TextView(c);
        t.setText(title);
        t.setTextSize(22);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(TEXT);
        r.addView(t, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (chip != null && chip.length() > 0) r.addView(osChip(c, chip));
        return r;
    }

    /** 右上角小胶囊（紫底白字）。 */
    public static TextView osChip(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(11);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(0xFFFFFFFF);
        t.setBackground(round(alpha(BLUE, 0xCC), 12, c));
        int p = dp(c, 10);
        t.setPadding(p, dp(c, 5), p, dp(c, 5));
        return t;
    }

    /** 💡 / ⚠️ 提示条：浅灰圆角底 + 灰蓝说明文字。kind 0=提示 1=警告 2=说明 */
    public static TextView osTip(Context c, String s, int kind) {
        TextView t = new TextView(c);
        String ic = kind == 1 ? "⚠  " : kind == 2 ? "ℹ  " : "💡  ";
        t.setText(ic + s);
        t.setTextSize(kind == 1 ? 11 : 12);
        // 图23：浅色玻璃主题下 MUTED 在浅灰提示条上几乎看不清，改成深蓝灰
        t.setTextColor(kind == 1 ? WARN : (DARK ? MUTED : 0xFF3E536C));
        t.setLineSpacing(dp(c, 3), 1f);
        t.setBackground(round(kind == 1 ? alpha(WARN, 0x18)
                : alpha(MUTED, 0x12), 14, c));
        int p = dp(c, 12);
        t.setPadding(p, p, p, p);
        return t;
    }

    /** 分组行：主标题 + 副标题 + 右侧灰字 / 开关，底部细分割线。 */
    public static LinearLayout osRow(Context c, String title, String sub,
                                     String right, boolean showDivider) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int p = dp(c, 14);
        row.setPadding(p, p, p, p);

        LinearLayout tx = new LinearLayout(c);
        tx.setOrientation(LinearLayout.VERTICAL);
        TextView n = new TextView(c);
        n.setText(title);
        n.setTextSize(15);
        n.setTextColor(TEXT);
        tx.addView(n);
        if (sub != null && sub.length() > 0) {
            TextView d = new TextView(c);
            d.setText(sub);
            d.setTextSize(11);
            d.setTextColor(MUTED);
            d.setPadding(0, dp(c, 3), 0, 0);
            tx.addView(d);
        }
        row.addView(tx, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (right != null && right.length() > 0) {
            TextView v = new TextView(c);
            v.setText(right);
            v.setTextSize(12);
            v.setTextColor(MUTED);
            row.addView(v);
        }
        if (showDivider) {
            View div = new View(c);
            div.setBackgroundColor(alpha(LINE, 0xC0));
            // 用外层包裹实现底部分割线
            LinearLayout wrap = new LinearLayout(c);
            wrap.setOrientation(LinearLayout.VERTICAL);
            wrap.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            wrap.addView(div, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 1))));
            return wrap;
        }
        return row;
    }

    /** 胶囊开关：空心圆 / 实心圆，点击切换。 */
    public static TextView osSwitch(Context c, boolean on) {
        TextView t = new TextView(c);
        t.setText(on ? "●" : "○");
        t.setTextSize(22);
        t.setTextColor(on ? BLUE : MUTED);
        t.setPadding(dp(c, 8), dp(c, 2), dp(c, 2), dp(c, 2));
        t.setTag(Boolean.valueOf(on));
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean now = !Boolean.TRUE.equals(v.getTag());
                v.setTag(Boolean.valueOf(now));
                ((TextView) v).setText(now ? "●" : "○");
                ((TextView) v).setTextColor(now ? BLUE : MUTED);
            }
        });
        return t;
    }

    public static TextView label(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(12);
        t.setTextColor(MUTED);
        return t;
    }

    public static EditText edit(Context c, String hint, int minLines, boolean mono, boolean secret) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setTextSize(13);
        e.setTextColor(TEXT);
        e.setHintTextColor(DARK ? 0xFF6E7D8C : 0xFF9AA7B4);
        e.setBackground(GLASS
                ? panel(c, 10)
                : roundStroke(INPUT_BG, LINE, 1, 9, c));
        int p = dp(c, 10);
        e.setPadding(p, p, p, p);
        e.setSingleLine(minLines <= 1);
        if (minLines > 1) e.setMinLines(minLines);
        if (mono) e.setTypeface(Typeface.MONOSPACE);
        if (secret) e.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return e;
    }

    public static StateListDrawable btnBg(Context c, int fill, int pressed, int disabled, int radius) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{-android.R.attr.state_enabled}, round(disabled, radius, c));
        s.addState(new int[]{android.R.attr.state_pressed}, round(pressed, radius, c));
        s.addState(new int[]{}, round(fill, radius, c));
        return s;
    }

    /** style: 0=主按钮(蓝底白字) 1=次按钮(描边蓝字) 2=中性(描边灰字) 3=危险(描边红字) */
    public static Button button(Context c, String text, int style, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(12.5f);
        b.setMaxLines(2);
        b.setLineSpacing(dp(c, 1), 1f);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setMinHeight(dp(c, 42));
        b.setPadding(dp(c, 8), dp(c, 6), dp(c, 8), dp(c, 6));
        b.setOnClickListener(l);
        b.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        int fill = style == 0 ? BLUE : (GLASS ? GLASS_CARD : CARD);
        int stroke = style == 0 ? BLUE
                : style == 1 ? BLUE : style == 3 ? RED : (GLASS ? GLASS_STROKE : LINE);
        int txt = style == 0 ? 0xFFFFFFFF
                : style == 1 ? BLUE : style == 3 ? RED : TEXT;
        int radius = GLASS ? 14 : 10;
        int sw = style == 0 ? 0 : (GLASS ? 1 : 2);
        GradientDrawable n = roundStroke(fill, stroke, sw, radius, c);
        GradientDrawable p = roundStroke(style == 0 ? BLUE_DARK : BLUE_LIGHT, stroke,
                sw, radius, c);
        GradientDrawable d = roundStroke(DARK ? 0xFF26313D : 0xFFF5F5F5, LINE, 1, radius, c);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{-android.R.attr.state_enabled}, d);
        s.addState(new int[]{android.R.attr.state_pressed}, p);
        s.addState(new int[]{}, n);
        b.setBackground(s);
        if (GLASS && style != 0) b.setElevation(dp(c, 2));
        b.setTextColor(new ColorStateList(
                new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}},
                new int[]{DARK ? 0xFF6E7D8C : 0xFF9AA7B4, txt}));
        return b;
    }

    public static LinearLayout row(Context c, View... vs) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        for (View v : vs) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            p.setMargins(dp(c, 3), dp(c, 3), dp(c, 3), dp(c, 3));
            r.addView(v, p);
        }
        return r;
    }

    public static LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams gap(Context c, int dp) {
        LinearLayout.LayoutParams p = match();
        p.setMargins(0, 0, 0, dp(c, dp));
        return p;
    }

    /** 底部 Tab 按钮。 */
    public static Button tab(Context c, String icon, String text,
                             boolean active, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(icon + "\n" + text);
        b.setAllCaps(false);
        b.setSingleLine(false);
        b.setMaxLines(2);
        b.setTextSize(11);
        b.setLineSpacing(dp(c, 1), 1f);
        b.setTypeface(Typeface.DEFAULT);
        b.setBackground(round(active ? BLUE_LIGHT : 0x00000000, 10, c));
        b.setTextColor(active ? BLUE : MUTED);
        b.setMinHeight(dp(c, 52));
        b.setPadding(dp(c, 4), dp(c, 6), dp(c, 4), dp(c, 4));
        b.setOnClickListener(l);
        b.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return b;
    }

    public static TextView badge(Context c, String s, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(11);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(color);
        t.setBackground(roundStroke(0x00000000, color, 1, 8, c));
        int p = dp(c, 6);
        t.setPadding(p, dp(c, 2), p, dp(c, 2));
        return t;
    }

    /** 主题预览用的小色块。 */
    public static GradientDrawable swatch(Context c, int color, int radiusDp) {
        GradientDrawable g = round(color, radiusDp, c);
        g.setStroke(dp(c, 1), 0x33000000);
        return g;
    }

    public static int alpha(int color, int a) {
        return (a << 24) | (color & 0x00FFFFFF);
    }
}
