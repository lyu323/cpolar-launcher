package net.lyu323.cpolar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** 上/下行双色速度条，按峰值归一化，零流量也能看到底色。 */
public class SpeedBar extends View {
    private float up = 0f, down = 0f;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    public SpeedBar(Context c) { super(c); }

    /** 传入 B/s，内部按 max(当前, 峰值, 1KB) 归一化。 */
    public void set(double upBps, double downBps, double peak) {
        double scale = Math.max(1024d, Math.max(peak, Math.max(upBps, downBps)));
        up = (float) Math.min(1d, upBps / scale);
        down = (float) Math.min(1d, downBps / scale);
        if (upBps < 1 && downBps < 1) { up = 0; down = 0; }
        invalidate();
    }

    @Override protected void onDraw(Canvas cv) {
        super.onDraw(cv);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float barH = Math.max(6f, h / 2f - 6f);
        float radius = barH / 2f;
        float left = 0f, right = w;

        // 上行（蓝）
        r.set(left, 0, right, barH);
        p.setColor(Ui.LINE);
        cv.drawRoundRect(r, radius, radius, p);
        if (up > 0.002f) {
            r.set(left, 0, left + (right - left) * up, barH);
            p.setColor(Ui.BLUE);
            cv.drawRoundRect(r, radius, radius, p);
        }

        // 下行（绿）
        float y = barH + 8f;
        r.set(left, y, right, y + barH);
        p.setColor(Ui.LINE);
        cv.drawRoundRect(r, radius, radius, p);
        if (down > 0.002f) {
            r.set(left, y, left + (right - left) * down, y + barH);
            p.setColor(Ui.GREEN);
            cv.drawRoundRect(r, radius, radius, p);
        }
    }
}
