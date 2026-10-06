package com.xperiatouch.clock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Small, font-independent line icons for the home screen. */
final class MinimalGlyphView extends View {
    static final int POWER = -10;
    static final int LIGHT = -11;
    static final int FAN = -12;
    static final int MODE = -13;
    static final int DEVICE = -14;
    static final int ALARM = -15;
    static final int SETTINGS = -16;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int kind;
    private int tint;

    MinimalGlyphView(Context context, int kind, int tint) {
        super(context);
        this.kind = kind;
        this.tint = tint;
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setKind(int kind) {
        this.kind = kind;
        invalidate();
    }

    void setTint(int tint) {
        this.tint = tint;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float side = Math.min(getWidth(), getHeight());
        if (side <= 0) return;
        canvas.save();
        canvas.translate((getWidth() - side) / 2f, (getHeight() - side) / 2f);
        canvas.scale(side / 24f, side / 24f);
        paint.setColor(tint);
        paint.setStrokeWidth(1.7f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStyle(Paint.Style.STROKE);
        if (kind == POWER) drawPower(canvas);
        else if (kind == LIGHT) drawLight(canvas);
        else if (kind == FAN) drawFan(canvas);
        else if (kind == MODE) drawMode(canvas);
        else if (kind == DEVICE) drawDevice(canvas);
        else if (kind == ALARM) drawAlarm(canvas);
        else if (kind == SETTINGS) drawSettings(canvas);
        else drawWeather(canvas, kind);
        canvas.restore();
    }

    private void drawWeather(Canvas canvas, int code) {
        if (code == 0) {
            drawSun(canvas, 12, 12, 4.2f);
            rays(canvas, 12, 12, 7f, 9.2f);
            return;
        }
        if (code == 1 || code == 2) {
            drawSun(canvas, 16.7f, 7.2f, 3.1f);
            rays(canvas, 16.7f, 7.2f, 5.1f, 6.3f);
        }
        Path cloud = new Path();
        cloud.moveTo(5.1f, 16f);
        cloud.cubicTo(2.9f, 16f, 2.8f, 12.4f, 5.4f, 11.6f);
        cloud.cubicTo(5.8f, 8.7f, 9.2f, 7.5f, 11.4f, 9.2f);
        cloud.cubicTo(13.2f, 6.2f, 18.4f, 7.4f, 18.4f, 11.1f);
        cloud.cubicTo(22.1f, 11.1f, 22.3f, 15.8f, 18.8f, 16f);
        cloud.close();
        canvas.drawPath(cloud, paint);
        if (code == 45 || code == 48) {
            canvas.drawLine(5f, 19f, 18.8f, 19f, paint);
            canvas.drawLine(7.4f, 21.5f, 16.5f, 21.5f, paint);
        } else if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) {
            canvas.drawLine(8f, 18f, 7f, 20.3f, paint);
            canvas.drawLine(13f, 18f, 12f, 20.3f, paint);
            canvas.drawLine(18f, 18f, 17f, 20.3f, paint);
        } else if ((code >= 71 && code <= 77) || (code >= 85 && code <= 86)) {
            snowflake(canvas, 8f, 20f);
            snowflake(canvas, 15.5f, 20f);
        } else if (code >= 95) {
            Path bolt = new Path();
            bolt.moveTo(13.2f, 17f);
            bolt.lineTo(10.5f, 20.5f);
            bolt.lineTo(13f, 20.5f);
            bolt.lineTo(11.7f, 23f);
            bolt.lineTo(16f, 18.5f);
            bolt.lineTo(13.7f, 18.5f);
            bolt.close();
            paint.setStyle(Paint.Style.FILL);
            canvas.drawPath(bolt, paint);
            paint.setStyle(Paint.Style.STROKE);
        }
    }

    private void drawSun(Canvas canvas, float x, float y, float radius) {
        canvas.drawCircle(x, y, radius, paint);
    }

    private void rays(Canvas canvas, float x, float y, float inner, float outer) {
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * i / 8;
            canvas.drawLine(x + (float) Math.cos(angle) * inner, y + (float) Math.sin(angle) * inner,
                    x + (float) Math.cos(angle) * outer, y + (float) Math.sin(angle) * outer, paint);
        }
    }

    private void snowflake(Canvas canvas, float x, float y) {
        for (int i = 0; i < 3; i++) {
            double angle = Math.PI * i / 3;
            float dx = (float) Math.cos(angle) * 1.8f;
            float dy = (float) Math.sin(angle) * 1.8f;
            canvas.drawLine(x - dx, y - dy, x + dx, y + dy, paint);
        }
    }

    private void drawPower(Canvas canvas) {
        Path arc = new Path();
        arc.addArc(4.2f, 4.2f, 19.8f, 19.8f, -48f, 276f);
        canvas.drawPath(arc, paint);
        canvas.drawLine(12f, 2.8f, 12f, 12.2f, paint);
    }

    private void drawSettings(Canvas canvas) {
        Path gear = new Path();
        for (int i = 0; i < 32; i++) {
            double angle = -Math.PI / 2 + (i + .5f) * Math.PI / 16;
            float radius = i % 4 == 1 || i % 4 == 2 ? 9.2f : 7.0f;
            float x = 12f + (float) Math.cos(angle) * radius;
            float y = 12f + (float) Math.sin(angle) * radius;
            if (i == 0) gear.moveTo(x, y);
            else gear.lineTo(x, y);
        }
        gear.close();
        canvas.drawPath(gear, paint);
        canvas.drawCircle(12f, 12f, 3f, paint);
    }

    private void drawAlarm(Canvas canvas) {
        canvas.drawCircle(12f, 13f, 7f, paint);
        canvas.drawLine(12f, 9f, 12f, 13f, paint);
        canvas.drawLine(12f, 13f, 15f, 14.5f, paint);
        canvas.drawArc(2.5f, 2.5f, 10.5f, 10.5f, 205f, 105f, false, paint);
        canvas.drawArc(13.5f, 2.5f, 21.5f, 10.5f, 230f, 105f, false, paint);
        canvas.drawLine(7.5f, 19f, 5.5f, 21.5f, paint);
        canvas.drawLine(16.5f, 19f, 18.5f, 21.5f, paint);
    }

    private void drawLight(Canvas canvas) {
        Path bulb = new Path();
        bulb.moveTo(7.4f, 10.2f);
        bulb.cubicTo(7.4f, 4.5f, 16.6f, 4.5f, 16.6f, 10.2f);
        bulb.cubicTo(16.6f, 12.5f, 14.2f, 13.6f, 14.1f, 16f);
        bulb.lineTo(9.9f, 16f);
        bulb.cubicTo(9.8f, 13.6f, 7.4f, 12.5f, 7.4f, 10.2f);
        canvas.drawPath(bulb, paint);
        canvas.drawLine(10f, 18.3f, 14f, 18.3f, paint);
        canvas.drawLine(10.7f, 20.5f, 13.3f, 20.5f, paint);
    }

    private void drawFan(Canvas canvas) {
        canvas.drawCircle(12f, 12f, 1.6f, paint);
        for (int i = 0; i < 3; i++) {
            canvas.save();
            canvas.rotate(i * 120f, 12f, 12f);
            Path blade = new Path();
            blade.moveTo(12f, 10.2f);
            blade.cubicTo(8.8f, 8.3f, 7.4f, 4.2f, 10.2f, 3.2f);
            blade.cubicTo(13.6f, 2f, 14.5f, 7.7f, 12f, 10.2f);
            canvas.drawPath(blade, paint);
            canvas.restore();
        }
    }

    private void drawMode(Canvas canvas) {
        canvas.drawLine(4f, 6f, 20f, 6f, paint);
        canvas.drawLine(4f, 12f, 20f, 12f, paint);
        canvas.drawLine(4f, 18f, 20f, 18f, paint);
        canvas.drawCircle(9f, 6f, 2f, paint);
        canvas.drawCircle(15f, 12f, 2f, paint);
        canvas.drawCircle(7f, 18f, 2f, paint);
    }

    private void drawDevice(Canvas canvas) {
        canvas.drawRoundRect(4.5f, 3.5f, 19.5f, 20.5f, 2.4f, 2.4f, paint);
        canvas.drawLine(8f, 7.5f, 16f, 7.5f, paint);
        canvas.drawLine(8f, 11f, 16f, 11f, paint);
        canvas.drawCircle(12f, 16f, 1.1f, paint);
    }
}
