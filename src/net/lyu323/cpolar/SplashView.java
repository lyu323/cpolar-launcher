package net.lyu323.cpolar;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * v1.3.0：纯 Canvas 手写的 3D 动态启动特效（零第三方依赖、零 XML）。
 *
 * 画面层次：径向渐变夜空 → 60 个环绕粒子 → 外层反向立方体线框 →
 * 旋转二十面体（三层描边模拟辉光）→ 三条倾斜光环 → 中心光晕 →
 * 扫描光带 → 应用名 / 版本 / 呼吸进度条。
 *
 * 由 MainActivity 覆盖在 decorView 上，约 2.3 秒后淡出移除；
 * 设置页可关闭（splashEnabled）。
 */
public final class SplashView extends View {
    private static final int BG_IN = 0xFF080D1E;
    private static final int BG_OUT = 0xFF04060F;
    private static final int CYAN = 0xFF22E6FF;
    private static final int VIOLET = 0xFF9B6BFF;

    private final String appName, version;
    private final int accent;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sub = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[][] ico = Fx3d.vertices();
    private final int[][] icoE = Fx3d.edges();
    private final float[][] cube = Fx3d.cubeVertices();
    private final int[][] cubeE = Fx3d.cubeEdges();
    private final float[] seed = new float[180];

    private float t = 0f;
    private int w = 0, hh = 0;
    private RadialGradient sky;
    private ValueAnimator anim;

    public SplashView(Context c, String appName, String version, int accent) {
        super(c);
        this.appName = appName;
        this.version = version;
        this.accent = accent;
        for (int i = 0; i < seed.length; i++) seed[i] = (float) Math.random();
        line.setStyle(Paint.Style.STROKE);
        glow.setStyle(Paint.Style.STROKE);
        text.setTextAlign(Paint.Align.CENTER);
        sub.setTextAlign(Paint.Align.CENTER);
        setClickable(true);
    }

    /** 开始播放；结束回调 onDone（主线程）。 */
    public void start(final Runnable onDone) {
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(2300);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                t = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                if (onDone != null) onDone.run();
            }
        });
        anim.start();
    }

    @Override protected void onSizeChanged(int x, int y, int ox, int oy) {
        w = x; hh = y;
        sky = new RadialGradient(w * 0.5f, hh * 0.40f, Math.max(w, hh) * 0.85f,
                new int[]{mix(BG_IN, accent, 0.16f), BG_IN, BG_OUT},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
    }

    @Override protected void onDetachedFromWindow() {
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas c) {
        float cx = w * 0.5f, cy = hh * 0.40f;
        float R = Math.min(w, hh) * 0.27f;
        float focal = R * 2.6f, camera = R * 3.1f;
        float ax = t * 2.1f, ay = t * 3.4f, az = t * 0.7f;
        float fadeIn = Math.min(1f, t / 0.14f);
        int alpha = (int) (255 * fadeIn);

        // ① 夜空
        fill.setShader(sky);
        fill.setAlpha(alpha);
        c.drawRect(0, 0, w, hh, fill);
        fill.setShader(null);

        // ② 环绕粒子（60 个，深度决定大小与亮度）
        for (int i = 0; i < 60; i++) {
            float a = seed[i * 3] * 6.283f + t * (0.5f + seed[i * 3 + 1]) * 2.0f;
            float rr = R * (1.15f + seed[i * 3 + 1] * 1.35f);
            float yy = (seed[i * 3 + 2] - 0.5f) * R * 2.4f;
            float[] p = Fx3d.rotate((float) Math.cos(a) * rr, yy,
                    (float) Math.sin(a) * rr * 0.55f, 0.35f, ay * 0.45f, 0f);
            float[] q = Fx3d.project(p[0], p[1], p[2], focal, camera);
            if (!Fx3d.finite(q)) continue;
            float dep = Math.max(0f, Math.min(1f, (q[3] - camera + R) / (R * 2f)));
            fill.setColor(i % 3 == 0 ? CYAN : (i % 3 == 1 ? accent : VIOLET));
            fill.setAlpha((int) (alpha * (0.20f + 0.55f * (1f - dep))));
            c.drawCircle(cx + q[0], cy + q[1], (0.7f + 2.4f * q[2] / 90f), fill);
        }

        // ③ 外层反向立方体线框
        drawWire(c, cube, cubeE, cx, cy, R * 1.72f, -ax * 0.55f, -ay * 0.5f, az,
                focal, camera, VIOLET, alpha * 0.30f, 1.1f, false);

        // ④ 三条倾斜光环
        for (int ring = 0; ring < 3; ring++) {
            float tilt = 0.45f + ring * 0.55f;
            float rr = R * (1.02f + ring * 0.20f);
            line.setColor(ring == 1 ? CYAN : mix(accent, VIOLET, ring * 0.5f));
            line.setStrokeWidth(1.2f + ring * 0.4f);
            line.setAlpha((int) (alpha * (0.20f + 0.16f * ring)));
            float[] prev = null;
            for (int k = 0; k <= 48; k++) {
                float a = k / 48f * 6.283f;
                float[] p = Fx3d.rotate((float) Math.cos(a) * rr, 0f,
                        (float) Math.sin(a) * rr, tilt, ay * (0.6f + ring * 0.2f), 0f);
                float[] q = Fx3d.project(p[0], p[1], p[2], focal, camera);
                if (!Fx3d.finite(q)) { prev = null; continue; }
                float[] cur = new float[]{cx + q[0], cy + q[1]};
                if (prev != null) c.drawLine(prev[0], prev[1], cur[0], cur[1], line);
                prev = cur;
            }
        }

        // ⑤ 中心光晕
        fill.setShader(new RadialGradient(cx, cy, R * 1.25f,
                new int[]{mix(accent, Color.WHITE, 0.35f), Color.TRANSPARENT},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        fill.setAlpha((int) (alpha * (0.16f + 0.06f * (float) Math.sin(t * 6f))));
        c.drawCircle(cx, cy, R * 1.25f, fill);
        fill.setShader(null);

        // ⑥ 二十面体：三层描边（宽低 alpha → 细高 alpha）模拟辉光
        drawWire(c, ico, icoE, cx, cy, R, ax, ay, az, focal, camera,
                CYAN, alpha * 0.16f, 9f, true);
        drawWire(c, ico, icoE, cx, cy, R, ax, ay, az, focal, camera,
                accent, alpha * 0.48f, 4f, true);
        drawWire(c, ico, icoE, cx, cy, R, ax, ay, az, focal, camera,
                Color.WHITE, alpha * 0.85f, 1.35f, true);
        // 顶点亮点
        for (float[] v : ico) {
            float[] p = Fx3d.rotate(v[0] * R, v[1] * R, v[2] * R, ax, ay, az);
            float[] q = Fx3d.project(p[0], p[1], p[2], focal, camera);
            if (!Fx3d.finite(q)) continue;
            fill.setColor(Color.WHITE);
            fill.setAlpha((int) (alpha * (0.45f + 0.45f * (1f - q[3] / (camera + R)))));
            c.drawCircle(cx + q[0], cy + q[1], 1.6f + 2.6f * q[2] / 90f, fill);
        }

        // ⑦ 扫描光带
        float sy2 = (t * 1.6f % 1f) * hh;
        fill.setShader(new LinearGradient(0, sy2 - 60, 0, sy2 + 60,
                new int[]{Color.TRANSPARENT, mix(CYAN, Color.TRANSPARENT, 0.72f),
                        Color.TRANSPARENT}, new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP));
        fill.setAlpha((int) (alpha * 0.30f));
        c.drawRect(0, sy2 - 60, w, sy2 + 60, fill);
        fill.setShader(null);

        // ⑧ 文字与进度条
        text.setColor(Color.WHITE);
        text.setTextSize(Math.min(w, hh) * 0.052f);
        text.setAlpha(alpha);
        text.setFakeBoldText(true);
        c.drawText(appName, cx, hh * 0.735f, text);
        sub.setColor(mix(CYAN, Color.WHITE, 0.35f));
        sub.setTextSize(Math.min(w, hh) * 0.024f);
        sub.setAlpha((int) (alpha * 0.82f));
        c.drawText("v" + version + "  ·  3D 启动特效  ·  正在初始化",
                cx, hh * 0.785f, sub);
        float pw = w * 0.34f, px = cx - pw / 2f, py = hh * 0.845f;
        fill.setColor(Color.WHITE);
        fill.setAlpha((int) (alpha * 0.14f));
        c.drawRoundRect(px, py, px + pw, py + 3f, 2f, 2f, fill);
        fill.setColor(CYAN);
        fill.setAlpha(alpha);
        float prog = pw * (0.18f + 0.82f * (0.5f - 0.5f * (float) Math.cos(t * 3.14159f)));
        c.drawRoundRect(px, py, px + prog, py + 3f, 2f, 2f, fill);
    }

    private void drawWire(Canvas c, float[][] vs, int[][] es, float cx, float cy,
                          float R, float ax, float ay, float az, float focal,
                          float camera, int color, float a, float width,
                          boolean depthFade) {
        glow.setColor(color);
        glow.setStrokeWidth(width);
        glow.setStrokeCap(Paint.Cap.ROUND);
        float cache[][] = new float[vs.length][];
        for (int i = 0; i < vs.length; i++) {
            float[] p = Fx3d.rotate(vs[i][0] * R, vs[i][1] * R, vs[i][2] * R, ax, ay, az);
            float[] q = Fx3d.project(p[0], p[1], p[2], focal, camera);
            cache[i] = Fx3d.finite(q) ? new float[]{cx + q[0], cy + q[1], q[3]} : null;
        }
        for (int[] e : es) {
            float[] u = cache[e[0]], v = cache[e[1]];
            if (u == null || v == null) continue;
            float f = depthFade
                    ? Math.max(0.25f, Math.min(1f, 1.25f - (u[2] + v[2]) / (2f * (camera + R))))
                    : 0.7f;
            glow.setAlpha((int) (a * f));
            c.drawLine(u[0], u[1], v[0], v[1], glow);
        }
    }

    private static int mix(int a, int b, float f) {
        f = Math.max(0f, Math.min(1f, f));
        int ar = Color.red(a), ag = Color.green(a), ab = Color.blue(a);
        int br = Color.red(b), bg = Color.green(b), bb = Color.blue(b);
        return Color.argb(255,
                (int) (ar + (br - ar) * f),
                (int) (ag + (bg - ag) * f),
                (int) (ab + (bb - ab) * f));
    }
}
