package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/** Original, static outline icons. No emoji font, bitmap, animation or external artwork. */
final class WeatherIcon extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final int kind;
    private final boolean day;

    WeatherIcon(int kind, boolean day, int color) {
        this.kind = kind;
        this.day = day;
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override public void draw(Canvas canvas) {
        int saved = canvas.save();
        canvas.translate(getBounds().left, getBounds().top);
        canvas.scale(getBounds().width() / 24f, getBounds().height() / 24f);
        if (kind == WeatherReading.SUN) sun(canvas, 12, 12, 4);
        else if (kind == WeatherReading.MOON) moon(canvas, 2, 2);
        else {
            if (kind == WeatherReading.PARTLY) {
                if (day) sun(canvas, 8, 7, 2.8f);
                else {
                    int small = canvas.save();
                    canvas.scale(0.65f, 0.65f);
                    moon(canvas, 2, 2);
                    canvas.restoreToCount(small);
                }
            }
            cloud(canvas);
            if (kind == WeatherReading.RAIN) {
                for (int x = 8; x <= 18; x += 5) canvas.drawLine(x, 18, x - 1.5f, 21, paint);
            } else if (kind == WeatherReading.SNOW) {
                for (int x = 8; x <= 18; x += 5) {
                    canvas.drawLine(x - 1, 19, x + 1, 21, paint);
                    canvas.drawLine(x - 1, 21, x + 1, 19, paint);
                }
            } else if (kind == WeatherReading.FOG) {
                canvas.drawLine(5, 19, 20, 19, paint);
                canvas.drawLine(8, 22, 17, 22, paint);
            } else if (kind == WeatherReading.STORM) {
                path.reset();
                path.moveTo(13, 17); path.lineTo(10, 20); path.lineTo(14, 20); path.lineTo(11, 23);
                canvas.drawPath(path, paint);
            }
        }
        canvas.restoreToCount(saved);
    }

    private void sun(Canvas canvas, float x, float y, float radius) {
        canvas.drawCircle(x, y, radius, paint);
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            float dx = (float) Math.cos(angle), dy = (float) Math.sin(angle);
            canvas.drawLine(x + dx * (radius + 2), y + dy * (radius + 2),
                    x + dx * (radius + 4), y + dy * (radius + 4), paint);
        }
    }

    private void moon(Canvas canvas, float x, float y) {
        path.reset();
        path.moveTo(x + 12, y + 2);
        path.cubicTo(x + 3, y - 1, x - 2, y + 11, x + 5, y + 16);
        path.cubicTo(x + 10, y + 20, x + 17, y + 16, x + 18, y + 11);
        path.cubicTo(x + 11, y + 14, x + 7, y + 7, x + 12, y + 2);
        canvas.drawPath(path, paint);
    }

    private void cloud(Canvas canvas) {
        path.reset();
        path.moveTo(6, 16);
        path.cubicTo(1, 16, 1, 10, 6, 10);
        path.cubicTo(6, 4, 15, 3, 17, 10);
        path.cubicTo(24, 8, 25, 16, 19, 16);
        path.close();
        canvas.drawPath(path, paint);
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
