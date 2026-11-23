package net.cpolar.mobile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;

/** 最近 N 秒上/下行速度曲线，双色描线 + 渐变填充。 */
public class Sparkline extends View {
    private double[] up = new double[0], down = new double[0];
    private double scale = 1024d;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public Sparkline(Context c) { super(c); }

    public void set(double[] u, double[] d) {
        up = u == null ? new double[0] : u;
        down = d == null ? new double[0] : d;
        double mx = 1024d;
        for (double v : up) if (v > mx) mx = v;
        for (double v : down) if (v > mx) mx = v;
        scale = mx * 1.15d;
        invalidate();
    }

    @Override protected void onDraw(Canvas cv) {
        super.onDraw(cv);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        grid.setColor(Ui.LINE);
        grid.setStrokeWidth(1f);
        cv.drawLine(0, h - 1f, w, h - 1f, grid);
        cv.drawLine(0, h / 2f, w, h / 2f, grid);

        if (up.length > 1) curve(cv, w, h, up, Ui.BLUE);
        if (down.length > 1) curve(cv, w, h, down, Ui.GREEN);
    }

    private void curve(Canvas cv, int w, int h, double[] v, int color) {
        int n = v.length;
        float bottom = h - 2f, top = 2f;
        path.reset();
        float[] xs = new float[n], ys = new float[n];
        for (int i = 0; i < n; i++) {
            xs[i] = w * i / (float) (n - 1);
            double t = Math.min(1d, v[i] / scale);
            ys[i] = bottom - (float) (t * (bottom - top));
        }
        path.moveTo(xs[0], ys[0]);
        for (int i = 1; i < n; i++) path.lineTo(xs[i], ys[i]);

        // 填充
        Path fp = new Path(path);
        fp.lineTo(w, bottom);
        fp.lineTo(0, bottom);
        fp.close();
        LinearGradient lg = new LinearGradient(0, top, 0, bottom,
                (color & 0x00FFFFFF) | 0x55000000,
                (color & 0x00FFFFFF) | 0x00000000,
                Shader.TileMode.CLAMP);
        fill.setShader(lg);
        cv.drawPath(fp, fill);
        fill.setShader(null);

        line.setColor(color);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2.5f);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        cv.drawPath(path, line);
    }
}
