package net.lyu323.cpolar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

/**
 * 液态玻璃背景 v2.6：底色渐变 + 四团漂移光晕 + 呼吸缩放 + 斜向流光扫描。
 *
 * 所有玻璃主题（液态玻璃 / 暗夜玻璃 / 玫瑰金玻璃 / 午夜蓝玻璃）默认都有动态效果；
 * 「液态玻璃动态光斑」开关只控制强度：关掉时画一帧静态光斑，零持续重绘。
 * 弹窗通过 setDialogMode(true) 强制开启动态并放大位移幅度，小尺寸下也看得出流动。
 */
public class GlassBackground extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler h = new Handler(Looper.getMainLooper());
    private float phase = 0f;
    private boolean running = false;
    private boolean animated = true;
    private boolean dialogMode = false;
    private boolean alwaysAnimated = false;
    private boolean forceGlass = false;
    private boolean themeTint = false;
    private Theme.Def d;
    private int cBg, c1, c2, c3;

    public GlassBackground(Context c) {
        this(c, false);
    }

    public GlassBackground(Context c, boolean dialog) {
        super(c);
        d = Theme.of(Ui.THEME_ID);
        dialogMode = dialog;
        animated = c.getSharedPreferences("cpolar", Context.MODE_PRIVATE)
                .getBoolean("glassMotion", true);
        if (dialogMode) alwaysAnimated = true;   // 弹窗里始终液态流动
        palette();
        setWillNotDraw(false);
    }

    /** 非玻璃主题下也呈现玻璃质感（弹窗用），取当前主题明暗给一套中性光斑。 */
    public GlassBackground setForceGlass(boolean v) {
        forceGlass = v;
        palette();
        return this;
    }

    /**
     * 弹窗专用：无论当前是 21 套里的哪一套，都用它的 HEADER/BLUE 生成同色系光斑。
     * 这样「所有主题都有动态液态半透明玻璃弹窗」，而不是固定蓝紫。
     */
    public GlassBackground setThemeTint(boolean v) {
        themeTint = v;
        palette();
        return this;
    }

    private void palette() {
        if (themeTint) {
            // 每套主题自己的三团光晕：顶栏起色 / 顶栏终色 / 强调色，再混一点互补色
            cBg = Ui.DARK ? mix(0xFF080E18, Ui.HEADER_START, 0.16f)
                          : mix(0xFFEDF2FA, Ui.HEADER_END, 0.12f);
            c1 = Ui.alpha(Ui.HEADER_START, 0x8A);
            c2 = Ui.alpha(Ui.BLUE, 0x88);
            c3 = Ui.alpha(mix(Ui.HEADER_END, 0xFF66E0C8, 0.42f), 0x80);
            return;
        }
        if (d.glass || !forceGlass) {
            cBg = d.spotBg; c1 = d.spot1; c2 = d.spot2; c3 = d.spot3;
        } else if (Ui.DARK) {
            cBg = 0xFF0B1220;
            c1 = 0x774A80FF; c2 = 0x66B84AF0; c3 = 0x5528C8C0;
        } else {
            cBg = 0xFFEAF1FB;
            c1 = 0x8860B8FF; c2 = 0x88F2A0D8; c3 = 0x8878E8D8;
        }
    }

    /** 允许调用方强制动态（例如预览卡）。 */
    public GlassBackground setAlwaysAnimated(boolean v) {
        alwaysAnimated = v;
        return this;
    }

    /** 省电模式可由外部临时降级。 */
    public GlassBackground setMotion(boolean v) {
        animated = v;
        return this;
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            phase += dialogMode ? 0.030f : 0.018f;
            invalidate();
            h.postDelayed(this, dialogMode ? 55 : 70);   // 弹窗 ~18fps / 背景 ~14fps
        }
    };

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (animated || alwaysAnimated) {
            running = true;
            h.post(tick);
        } else {
            invalidate();   // 静态光斑：只绘制一次，之后零 CPU/GPU 重绘
        }
    }

    @Override protected void onDetachedFromWindow() {
        running = false;
        h.removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas cv) {
        super.onDraw(cv);
        int w = getWidth(), hh = getHeight();
        if (w <= 0 || hh <= 0) return;

        float amp = dialogMode ? 1.55f : 1.0f;
        float breathe = 1f + 0.10f * fsin(phase * 1.6f);

        // ① 底色：随相位在 spotBg / spot1 之间缓慢流动
        float t = 0.10f + 0.10f * (1f + fsin(phase * 0.45f)) / 2f;
        LinearGradient base = new LinearGradient(0, 0,
                w * (0.35f + 0.12f * fsin(phase * 0.3f)), hh,
                cBg, mix(cBg, c1, t), Shader.TileMode.CLAMP);
        p.setShader(base);
        cv.drawRect(0, 0, w, hh, p);

        float s = Math.min(w, hh);

        // ② 四团光晕：位置漂移 + 半径呼吸
        blob(cv, w * (0.16f + 0.18f * amp * fsin(phase)),
                hh * (0.14f + 0.14f * amp * fcos(phase * 0.8f)),
                s * (dialogMode ? 0.72f : 0.62f) * breathe, c1);

        blob(cv, w * (0.86f + 0.14f * amp * fcos(phase * 1.1f)),
                hh * (0.44f + 0.18f * amp * fsin(phase * 0.7f)),
                s * (dialogMode ? 0.66f : 0.55f) / breathe, c2);

        blob(cv, w * (0.40f + 0.20f * amp * fsin(phase * 0.6f + 1f)),
                hh * (0.90f + 0.12f * amp * fcos(phase)),
                s * (dialogMode ? 0.70f : 0.58f) * (2f - breathe), c3);

        blob(cv, w * (0.70f + 0.16f * amp * fcos(phase * 0.9f + 2f)),
                hh * (0.78f + 0.14f * amp * fsin(phase * 1.3f)),
                s * 0.40f * breathe, mix(c1, c2, 0.5f));

        // ③ 斜向流光带：一条很淡的高光缓慢扫过，模拟玻璃反射
        float sweep = (phase * 0.09f) % 1.6f - 0.3f;   // -0.3 .. 1.3
        float cx = w * sweep;
        int hi = 0x22FFFFFF;
        LinearGradient sheen = new LinearGradient(cx - w * 0.22f, 0,
                cx + w * 0.22f, hh,
                new int[]{0x00FFFFFF, hi, 0x00FFFFFF},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        p.setShader(sheen);
        cv.drawRect(0, 0, w, hh, p);

        // ④ 顶部高光线，让玻璃有边缘厚度
        p.setShader(null);
        p.setColor(0x33FFFFFF);
        cv.drawRect(0, 0, w, Math.max(2, dp(1.2f)), p);
    }

    private void blob(Canvas cv, float cx, float cy, float r, int color) {
        if (r <= 0) return;
        RadialGradient g = new RadialGradient(cx, cy, r,
                color, fade(color), Shader.TileMode.CLAMP);
        p.setShader(g);
        cv.drawCircle(cx, cy, r, p);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private static int fade(int c) {
        return (c & 0x00FFFFFF) | 0x00000000;
    }

    private static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000
                | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8)
                | (int) (ab + (bb - ab) * t);
    }

    private static float fsin(float v) { return (float) Math.sin(v); }
    private static float fcos(float v) { return (float) Math.cos(v); }
}
