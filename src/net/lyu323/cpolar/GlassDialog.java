package net.lyu323.cpolar;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 液态动态玻璃弹窗 v2.8。
 *
 * 修掉 v2.7 两个真机问题（截图 3 / 4）：
 *  1) 「不能下滑 / 内容被截断」：旧版 window=WRAP_CONTENT、card=WRAP_CONTENT、
 *     ScrollView=AT_MOST(0.68 屏)，再叠加调用方自己又套一层 ScrollView，
 *     嵌套滚动 + 窗口裁剪导致列表划不动、关于页下半段看不见。
 *     现在：window=MATCH_PARENT，card 给明确高度（0.86 屏）并居中，
 *     ScrollView 用 weight=1 独占剩余空间 —— 唯一滚动容器，必定可滚。
 *  2) 「所有主题都要动态液态半透明玻璃」：光斑层铺满整个窗口；
 *     卡片按当前主题色板做成真半透明（三档玻璃强度）；
 *     非玻璃主题也从 HEADER/BLUE 生成同色系动态光斑。
 *
 * 新增：滚动到底提示、可下滑指示、按主题着色的自绘按钮、内容超高自动提示条。
 * Builder API 仍与 AlertDialog.Builder 兼容，MainActivity 里所有弹窗无需改调用。
 */
public class GlassDialog extends Dialog implements DialogInterface {

    public GlassDialog(Context c) {
        super(c);
        Window w0 = getWindow();
        if (w0 != null) w0.requestFeature(Window.FEATURE_NO_TITLE);
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Window w = getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setDimAmount(Ui.DARK ? 0.68f : 0.52f);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.MATCH_PARENT;
            lp.gravity = Gravity.CENTER;
            w.setAttributes(lp);
        }
    }

    public static final class Builder {
        private final Context c;
        private CharSequence title, message, tip, subtitle;
        private int tipKind = 0;
        private View custom;
        private CharSequence[] items;
        private boolean[] checked;
        private DialogInterface.OnClickListener itemClick, pos, neu, neg;
        private DialogInterface.OnMultiChoiceClickListener multiClick;
        private int singleChecked = -1;
        private CharSequence posTxt, neuTxt, negTxt;
        private boolean cancelable = true;
        private boolean scrollable = true;
        private boolean autoShrink = true;
        private GlassDialog dialog;

        public Builder(Context c) { this.c = c; }

        public Builder setTitle(CharSequence s) { title = s; return this; }
        public Builder setSubtitle(CharSequence s) { subtitle = s; return this; }
        public Builder setMessage(CharSequence s) { message = s; return this; }
        public Builder setView(View v) { custom = v; return this; }
        public Builder setCancelable(boolean v) { cancelable = v; return this; }
        public Builder setScrollable(boolean v) { scrollable = v; return this; }
        /** 内容不足一屏时自动收缩卡片（默认开）；关掉则始终 0.86 屏高。 */
        public Builder setAutoShrink(boolean v) { autoShrink = v; return this; }

        /** 系统玻璃风提示条，kind 0=💡 1=⚠ 2=ℹ */
        public Builder setTip(CharSequence s, int kind) {
            tip = s; tipKind = kind; return this;
        }

        public Builder setPositiveButton(CharSequence t, DialogInterface.OnClickListener l) {
            posTxt = t; pos = l; return this;
        }
        public Builder setNeutralButton(CharSequence t, DialogInterface.OnClickListener l) {
            neuTxt = t; neu = l; return this;
        }
        public Builder setNegativeButton(CharSequence t, DialogInterface.OnClickListener l) {
            negTxt = t; neg = l; return this;
        }
        public Builder setItems(CharSequence[] it, DialogInterface.OnClickListener l) {
            items = it; itemClick = l; return this;
        }
        public Builder setSingleChoiceItems(CharSequence[] it, int checkedIdx,
                                            DialogInterface.OnClickListener l) {
            items = it; singleChecked = checkedIdx; itemClick = l; return this;
        }
        public Builder setMultiChoiceItems(CharSequence[] it, boolean[] ck,
                                           DialogInterface.OnMultiChoiceClickListener l) {
            items = it; checked = ck; multiClick = l; return this;
        }

        public GlassDialog show() {
            GlassDialog d = new GlassDialog(c);
            dialog = d;
            d.setCancelable(cancelable);
            d.setCanceledOnTouchOutside(cancelable);

            int screenH = c.getResources().getDisplayMetrics().heightPixels;
            int cardH = (int) (screenH * 0.86f);
            int pad = Ui.dp(c, 18);

            android.widget.FrameLayout root = new android.widget.FrameLayout(c);
            root.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            // ① 动态光斑铺满整个窗口（卡片半透明，光斑从四周与背后透出来）
            GlassBackground g = new GlassBackground(c, true)
                    .setForceGlass(true)
                    .setThemeTint(true)
                    .setAlwaysAnimated(true);
            root.addView(g, new android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            // 点卡片外关闭：window 是全屏，系统自带 outside-touch 不再生效，
            // 这里用一层透明点击区接管；card 设 clickable 吞掉自己区域内的点击。
            final View outside = new View(c);
            outside.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (cancelable && dialog != null) dialog.dismiss();
                }
            });
            root.addView(outside, new android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            // ② 玻璃卡片：明确高度 + 居中
            LinearLayout card = new LinearLayout(c);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setClickable(true);
            card.setBackground(cardBg(c));
            card.setElevation(Ui.dp(c, 12));
            int cp = Ui.dp(c, 18);
            card.setPadding(cp, 0, cp, cp);
            android.widget.FrameLayout.LayoutParams clp =
                    new android.widget.FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, cardH, Gravity.CENTER);
            clp.setMargins(pad, Ui.dp(c, 12), pad, Ui.dp(c, 12));
            // FrameLayout.LayoutParams 的宽是 MATCH_PARENT，减去左右 margin 由系统处理
            root.addView(card, clp);

            // 顶条
            View accent = new View(c);
            accent.setBackground(new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{Ui.HEADER_START, Ui.HEADER_END, Ui.BLUE}));
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 3));
            alp.setMargins(-cp, 0, -cp, Ui.dp(c, 12));
            card.addView(accent, alp);

            // 标题区（固定在顶部，不参与滚动）
            if (title != null && title.length() > 0) {
                TextView t = new TextView(c);
                t.setText(title);
                t.setTextSize(19);
                t.setTypeface(Typeface.DEFAULT_BOLD);
                t.setTextColor(Ui.TEXT);
                card.addView(t, Ui.gap(c, 3));
                if (subtitle != null && subtitle.length() > 0) {
                    TextView st = new TextView(c);
                    st.setText(subtitle);
                    st.setTextSize(11);
                    st.setTextColor(Ui.MUTED);
                    card.addView(st, Ui.gap(c, 8));
                } else {
                    card.addView(spacer(c, 8));
                }
                View div = new View(c);
                div.setBackgroundColor(Ui.alpha(Ui.BLUE, 0x55));
                card.addView(div, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(c, 1))));
                card.addView(spacer(c, 10));
            }

            // ③ 唯一滚动容器：weight=1 独占剩余高度 —— 修复「不能下滑」
            ScrollView sv = new ScrollView(c);
            sv.setFillViewport(true);
            sv.setVerticalScrollBarEnabled(true);
            sv.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
            sv.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
            LinearLayout body = new LinearLayout(c);
            body.setOrientation(LinearLayout.VERTICAL);
            // 可读性底：玻璃很透时给内容区加一层极淡的底，保证文字对比度
            body.setBackground(Ui.round(Ui.alpha(Ui.DARK ? 0xFF0E1622 : 0xFFFFFFFF,
                    glassAlpha(c) >= 0xE0 ? 0x00 : 0x30), 14, c));
            int bp = Ui.dp(c, 4);
            body.setPadding(bp, bp, bp, bp);
            sv.addView(body, new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            if (tip != null && tip.length() > 0)
                body.addView(Ui.osTip(c, tip.toString(), tipKind), Ui.gap(c, 10));
            if (message != null && message.length() > 0) {
                TextView m = new TextView(c);
                m.setText(message);
                m.setTextSize(13);
                m.setTextColor(Ui.TEXT);
                m.setLineSpacing(Ui.dp(c, 3), 1f);
                body.addView(m, Ui.gap(c, 12));
            }
            if (custom != null) body.addView(custom, Ui.gap(c, 8));
            if (items != null) buildList(body);

            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            card.addView(sv, slp);

            // 「↓ 下滑查看更多」提示
            final TextView hint = new TextView(c);
            hint.setText("↓  下滑查看更多");
            hint.setTextSize(10);
            hint.setTextColor(Ui.MUTED);
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 2));
            card.addView(hint, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            sv.post(new Runnable() {
                @Override public void run() {
                    View child = sv.getChildAt(0);
                    boolean more = child != null && child.getHeight() > sv.getHeight() + 4;
                    hint.setVisibility(more ? View.VISIBLE : View.GONE);
                }
            });

            // ④ 按钮行固定在底部
            LinearLayout bar = new LinearLayout(c);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setPadding(0, Ui.dp(c, 10), 0, 0);
            boolean any = false;
            if (neuTxt != null) { bar.addView(mkBtn(neuTxt, 1, neu), wlp()); any = true; }
            if (negTxt != null) { bar.addView(mkBtn(negTxt, 2, neg), wlp()); any = true; }
            if (posTxt != null) { bar.addView(mkBtn(posTxt, 0, pos), wlp()); any = true; }
            if (any) card.addView(bar, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            d.setContentView(root);

            card.setAlpha(0f);
            card.setScaleX(0.95f);
            card.setScaleY(0.95f);
            d.show();
            card.post(new Runnable() {
                @Override public void run() {
                    // v2.9：内容不足 0.86 屏时按实际高度收缩。
                    // 图21 那种「0 条隧道」短内容却占满整屏、中间一大片空白，根因就是
                    // 固定 cardH + ScrollView weight=1 把空白全撑开了。
                    try {
                        View child = sv.getChildAt(0);
                        int bodyH = child != null ? child.getMeasuredHeight() : 0;
                        int others = 0;
                        for (int k = 0; k < card.getChildCount(); k++) {
                            View vv = card.getChildAt(k);
                            if (vv == sv) continue;
                            LinearLayout.LayoutParams lp2 =
                                    (LinearLayout.LayoutParams) vv.getLayoutParams();
                            others += vv.getMeasuredHeight();
                            if (lp2 != null) others += lp2.topMargin + lp2.bottomMargin;
                        }
                        int need = bodyH + others + card.getPaddingTop()
                                + card.getPaddingBottom() + Ui.dp(c, 6);
                        int minH = Ui.dp(c, 170);
                        if (need < cardH - Ui.dp(c, 20) && autoShrink) {
                            int target = Math.max(minH, Math.min(need, cardH));
                            LinearLayout.LayoutParams slp2 =
                                    (LinearLayout.LayoutParams) sv.getLayoutParams();
                            slp2.height = Math.max(Ui.dp(c, 40), bodyH + Ui.dp(c, 2));
                            slp2.weight = 0f;
                            sv.setLayoutParams(slp2);
                            hint.setVisibility(View.GONE);
                            android.widget.FrameLayout.LayoutParams flp =
                                    (android.widget.FrameLayout.LayoutParams) card.getLayoutParams();
                            flp.height = target;
                            card.setLayoutParams(flp);
                        } else {
                            hint.setVisibility(child != null && bodyH > sv.getHeight() + 4
                                    ? View.VISIBLE : View.GONE);
                        }
                    } catch (Exception ignored) {}
                    card.animate().alpha(1f).scaleX(1f).scaleY(1f)
                            .setDuration(300)
                            .setInterpolator(new DecelerateInterpolator(1.4f)).start();
                }
            });
            return d;
        }

        // ---------------------------------------------------------- 内部

        private int glassAlpha(Context ctx) {
            SharedPreferences p = ctx.getSharedPreferences("cpolar", Context.MODE_PRIVATE);
            int lvl = p.getInt("glassLevel", 1);   // 0 通透 / 1 标准 / 2 厚实
            return lvl == 0 ? 0xB4 : lvl == 2 ? 0xEE : 0xD2;
        }

        private GradientDrawable cardBg(Context ctx) {
            int a = glassAlpha(ctx);
            int base = Ui.DARK ? 0xFF121C28 : 0xFFFFFFFF;
            if (Ui.GLASS) {
                base = (Ui.CARD & 0x00FFFFFF) | 0xFF000000;
                if (Ui.DARK) base = 0xFF101C2C;
            }
            int top = Ui.alpha(base, Math.min(0xF6, a + 0x14));
            int bottom = Ui.alpha(base, a);
            GradientDrawable g = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR, new int[]{top, bottom});
            g.setCornerRadius(Ui.dp(ctx, 26));
            g.setStroke(Math.max(1, Ui.dp(ctx, 1)),
                    Ui.DARK ? 0x66FFFFFF : Ui.alpha(Ui.BLUE, 0x55));
            return g;
        }

        private void buildList(final LinearLayout body) {
            final java.util.ArrayList<TextView> marks =
                    new java.util.ArrayList<TextView>();
            for (int i = 0; i < items.length; i++) {
                final int idx = i;
                LinearLayout row = new LinearLayout(c);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setBackground(rowBg(c));
                int rp = Ui.dp(c, 12);
                row.setPadding(rp, rp, rp, rp);

                TextView mark = null;
                if (checked != null) {
                    mark = new TextView(c);
                    mark.setText(checked[i] ? "☑" : "☐");
                    mark.setTextSize(18);
                    mark.setTextColor(checked[i] ? Ui.BLUE : Ui.MUTED);
                    mark.setPadding(0, 0, Ui.dp(c, 10), 0);
                    row.addView(mark);
                    marks.add(mark);
                } else if (singleChecked >= 0) {
                    mark = new TextView(c);
                    mark.setText(i == singleChecked ? "◉" : "○");
                    mark.setTextSize(17);
                    mark.setTextColor(i == singleChecked ? Ui.BLUE : Ui.MUTED);
                    mark.setPadding(0, 0, Ui.dp(c, 10), 0);
                    row.addView(mark);
                    marks.add(mark);
                }

                TextView tx = new TextView(c);
                tx.setText(items[i]);
                tx.setTextSize(13);
                tx.setTextColor(Ui.TEXT);
                tx.setLineSpacing(Ui.dp(c, 2), 1f);
                tx.setSingleLine(false);
                tx.setLayoutParams(new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                row.addView(tx);

                if (checked == null && singleChecked < 0) {
                    TextView ar = new TextView(c);
                    ar.setText("›");
                    ar.setTextSize(18);
                    ar.setTextColor(Ui.MUTED);
                    row.addView(ar);
                }

                final TextView fm = mark;
                row.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        if (checked != null) {
                            checked[idx] = !checked[idx];
                            if (fm != null) {
                                fm.setText(checked[idx] ? "☑" : "☐");
                                fm.setTextColor(checked[idx] ? Ui.BLUE : Ui.MUTED);
                            }
                            if (multiClick != null)
                                multiClick.onClick(resolveDialog(v), idx, checked[idx]);
                        } else if (singleChecked >= 0) {
                            for (int k = 0; k < marks.size(); k++) {
                                TextView mk = marks.get(k);
                                mk.setText(k == idx ? "◉" : "○");
                                mk.setTextColor(k == idx ? Ui.BLUE : Ui.MUTED);
                            }
                            if (itemClick != null) itemClick.onClick(resolveDialog(v), idx);
                            dismissOf(v);   // 单选：选完即关（重复 dismiss 无害）
                        } else {
                            if (itemClick != null) itemClick.onClick(resolveDialog(v), idx);
                            dismissOf(v);
                        }
                    }
                });
                body.addView(row, Ui.gap(c, 5));
            }
        }

        private DialogInterface resolveDialog(View v) {
            return dialog != null ? dialog : new DialogInterface() {
                public void cancel() {}
                public void dismiss() {}
            };
        }

        private void dismissOf(View v) { if (dialog != null) dialog.dismiss(); }

        private LinearLayout.LayoutParams wlp() {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            p.setMargins(Ui.dp(c, 3), 0, Ui.dp(c, 3), 0);
            return p;
        }

        private Button mkBtn(CharSequence txt, final int style,
                             final DialogInterface.OnClickListener l) {
            Button b = new Button(c);
            b.setText(txt);
            b.setAllCaps(false);
            // v2.9：允许两行，避免三按钮并排时「拉取并启动所选」被截成「拉取并启动…」
            b.setSingleLine(false);
            b.setMaxLines(2);
            b.setEllipsize(TextUtils.TruncateAt.END);
            b.setTextSize(11.5f);
            b.setLineSpacing(Ui.dp(c, 1), 1f);
            b.setTypeface(Typeface.DEFAULT_BOLD);
            b.setMinHeight(Ui.dp(c, 42));
            b.setPadding(Ui.dp(c, 6), Ui.dp(c, 4), Ui.dp(c, 6), Ui.dp(c, 4));
            int r = Ui.dp(c, 12);
            // 主按钮改成主题色渐变（不再是一整块刺眼亮蓝），深/浅主题都协调
            GradientDrawable n = new GradientDrawable(
                    style == 0 ? GradientDrawable.Orientation.LEFT_RIGHT
                               : GradientDrawable.Orientation.TL_BR,
                    style == 0 ? new int[]{Ui.HEADER_START, Ui.BLUE}
                               : new int[]{Ui.alpha(Ui.CARD, 0xC0), Ui.alpha(Ui.CARD, 0xA0)});
            n.setCornerRadius(r);
            if (style != 0) n.setStroke(Math.max(1, Ui.dp(c, 1)),
                    style == 2 ? Ui.LINE : Ui.alpha(Ui.BLUE, 0xA0));
            GradientDrawable pr = new GradientDrawable();
            pr.setColor(style == 0 ? Ui.BLUE_DARK : Ui.BLUE_LIGHT);
            pr.setCornerRadius(r);
            StateListDrawable s = new StateListDrawable();
            s.addState(new int[]{android.R.attr.state_pressed}, pr);
            s.addState(new int[]{}, n);
            b.setBackground(s);
            b.setTextColor(style == 0 ? 0xFFFFFFFF : (style == 2 ? Ui.MUTED : Ui.BLUE));
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (l != null) l.onClick(resolveDialog(v), 0);
                    dismissOf(v);
                }
            });
            return b;
        }

        private StateListDrawable rowBg(Context ctx) {
            StateListDrawable s = new StateListDrawable();
            s.addState(new int[]{android.R.attr.state_pressed},
                    Ui.round(Ui.alpha(Ui.BLUE, 0x38), 12, ctx));
            s.addState(new int[]{},
                    Ui.round(Ui.alpha(Ui.CARD, Ui.GLASS ? 0x70 : 0xCC), 12, ctx));
            return s;
        }

        private View spacer(Context ctx, int dp) {
            View v = new View(ctx);
            v.setLayoutParams(new LinearLayout.LayoutParams(1, Ui.dp(ctx, dp)));
            return v;
        }
    }
}
