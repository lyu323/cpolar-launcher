package net.cpolar.mobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.os.Build;

/**
 * 通知栏小图标：自绘白色 alpha 蒙版。
 *
 * Android 会把 smallIcon 当作蒙版并染成 Notification.setColor() 的颜色，
 * 所以这里只画白色形状，主题色由 FrpService 里 setColor(Ui.BLUE) 注入 ——
 * 切换主题时通知栏图标与强调色会跟着变，无需重新打包资源。
 */
public final class NotifyIcon {
    private NotifyIcon() {}

    public static Icon create(Context c) {
        int s = 96;
        Bitmap bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // 白色圆角底（会被系统染成主题色）
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.FILL);
        cv.drawRoundRect(new RectF(6, 6, 90, 90), 22, 22, p);

        // 挖出透明的 frp 字样（透明区不染色，形成反白字）
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        p.setColor(Color.TRANSPARENT);
        p.setTextSize(40);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = p.getFontMetrics();
        float baseline = 48 - (fm.ascent + fm.descent) / 2f;
        cv.drawText("frp", 48, baseline, p);
        p.setXfermode(null);

        if (Build.VERSION.SDK_INT >= 23) return Icon.createWithBitmap(bmp);
        return null;
    }
}
