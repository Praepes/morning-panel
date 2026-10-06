package com.morningpanel.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;
import java.util.Calendar;
import java.util.Locale;

/** Clock artwork sized to its available area, also used by the style previews. */
final class ClockFaceView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final TextPaint previewTimePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Rect bounds = new Rect();
    private final Typeface serif = Typeface.SERIF;
    private final Typeface classic;
    private final int ink, muted, accent;
    private final float density;
    private int style = AppPrefs.CLOCK_STACKED;
    private int hour = -1, minute = -1;
    private String hours = "00", minutes = "00", time = "00:00";

    ClockFaceView(Context context, Typeface serif, int ink, int muted, int accent) {
        super(context);
        this.classic = serif == null ? Typeface.SERIF : serif;
        this.ink = ink;
        this.muted = muted;
        this.accent = accent;
        density = getResources().getDisplayMetrics().density;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setTime(System.currentTimeMillis());
    }

    static String styleName(int style) {
        switch (style) {
            case AppPrefs.CLOCK_DIAL: return "表盘与数字";
            case AppPrefs.CLOCK_HORIZONTAL: return "横排数字";
            default: return "时分上下排";
        }
    }

    void setClockStyle(int value) {
        value = AppPrefs.normalizeClockStyle(value);
        if (style == value) return;
        style = value;
        updateDescription();
        invalidate();
    }

    void setTime(long timestamp) {
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(timestamp);
        int nextHour = now.get(Calendar.HOUR_OF_DAY), nextMinute = now.get(Calendar.MINUTE);
        if (hour == nextHour && minute == nextMinute) return;
        hour = nextHour;
        minute = nextMinute;
        hours = String.format(Locale.US, "%02d", hour);
        minutes = String.format(Locale.US, "%02d", minute);
        time = hours + ":" + minutes;
        updateDescription();
        invalidate();
    }

    private void updateDescription() {
        setContentDescription("当前时间 " + time + "，" + styleName(style) + "，长按切换时钟样式");
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth() - getPaddingLeft() - getPaddingRight();
        float height = getHeight() - getPaddingTop() - getPaddingBottom();
        if (width <= 0 || height <= 0) return;
        canvas.save();
        canvas.translate(getPaddingLeft(), getPaddingTop());
        if (style == AppPrefs.CLOCK_DIAL) drawDial(canvas, width, height);
        else if (style == AppPrefs.CLOCK_HORIZONTAL) drawHorizontal(canvas, width, height);
        else drawStacked(canvas, width, height);
        canvas.restore();
    }

    private void prepareText(Typeface face, float size, int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(face);
        paint.setTextSize(size);
        paint.setTextScaleX(1f);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(color);
    }

    private float textSizeFor(Typeface face, String reference, float maxWidth, float maxHeight) {
        prepareText(face, 100f, ink);
        paint.getTextBounds(reference, 0, reference.length(), bounds);
        return Math.max(1f, 100f * Math.min(maxWidth / Math.max(1f, paint.measureText(reference)),
                maxHeight / Math.max(1f, bounds.height())));
    }

    private void centeredText(Canvas canvas, String value, float x, float y, Typeface face, float size, int color) {
        prepareText(face, size, color);
        paint.getTextBounds(value, 0, value.length(), bounds);
        canvas.drawText(value, x, y - (bounds.top + bounds.bottom) / 2f, paint);
    }

    private void drawStacked(Canvas canvas, float width, float height) {
        float totalHeight = Math.min(height * .56f, width * .88f);
        float gap = totalHeight * .16f, lineHeight = (totalHeight - gap) / 2f;
        float size = textSizeFor(classic, "88", width * .64f, lineHeight), center = height / 2f;
        centeredText(canvas, hours, width / 2f, center - gap / 2f - lineHeight / 2f, classic, size, ink);
        centeredText(canvas, minutes, width / 2f, center + gap / 2f + lineHeight / 2f, classic, size, ink);
        paint.setColor(Color.argb(64, Color.red(muted), Color.green(muted), Color.blue(muted)));
        paint.setStrokeWidth(Math.max(1f, density));
        float rule = width * .30f;
        canvas.drawLine((width - rule) / 2f, center, (width + rule) / 2f, center, paint);
    }

    private void drawHorizontal(Canvas canvas, float width, float height) {
        // Both pairs use the same compact size, with a separately drawn colon.
        prepareText(classic, 100f, ink);
        float referenceWidth = paint.measureText("88") * 2f + 32f;
        paint.getTextBounds("88", 0, 2, bounds);
        float size = Math.max(1f, 100f * Math.min(width * .74f / referenceWidth,
                height * .234f / Math.max(1f, bounds.height())));
        prepareText(classic, size, ink);
        float hourWidth = paint.measureText(hours);
        paint.getTextBounds("88", 0, 2, bounds);
        float baseline = height / 2f - (bounds.top + bounds.bottom) / 2f;
        float colonY = height / 2f, dotGap = bounds.height() * .13f;
        float minuteWidth = paint.measureText(minutes);
        float separatorWidth = size * .32f;
        float left = (width - hourWidth - separatorWidth - minuteWidth) / 2f;
        prepareText(classic, size, ink);
        canvas.drawText(hours, left + hourWidth / 2f, baseline, paint);
        canvas.drawText(minutes, left + hourWidth + separatorWidth + minuteWidth / 2f, baseline, paint);
        paint.setColor(muted);
        float dotRadius = Math.max(.75f, size * .014f);
        float colonX = left + hourWidth + separatorWidth / 2f;
        canvas.drawCircle(colonX, colonY - dotGap, dotRadius, paint);
        canvas.drawCircle(colonX, colonY + dotGap, dotRadius, paint);
    }

    private void drawDial(Canvas canvas, float width, float height) {
        boolean compact = height <= 220f * density;
        float diameter = Math.min(width * .82f, height * (compact ? .60f : .65f));
        float digitalHeight = diameter * (compact ? .44f : .16f), gap = diameter * .12f;
        float top = (height - diameter - gap - digitalHeight) / 2f;
        float cx = width / 2f, cy = top + diameter / 2f, radius = diameter / 2f - 2f * density;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, density));
        paint.setColor(Color.argb(34, Color.red(muted), Color.green(muted), Color.blue(muted)));
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setColor(muted);
        for (int i = 0; i < 60; i++) {
            double angle = i * Math.PI / 30;
            float outer = radius - 3f * density;
            float inner = outer - diameter * (i % 5 == 0 ? .043f : .018f);
            canvas.drawLine(cx + (float) Math.sin(angle) * inner, cy - (float) Math.cos(angle) * inner,
                    cx + (float) Math.sin(angle) * outer, cy - (float) Math.cos(angle) * outer, paint);
        }
        float labelSize = diameter * .056f, labelRadius = radius * .73f;
        centeredText(canvas, "12", cx, cy - labelRadius, serif, labelSize, muted);
        centeredText(canvas, "3", cx + labelRadius, cy, serif, labelSize, muted);
        centeredText(canvas, "6", cx, cy + labelRadius, serif, labelSize, muted);
        centeredText(canvas, "9", cx - labelRadius, cy, serif, labelSize, muted);
        drawHand(canvas, cx, cy, (hour % 12) * 30f + minute * .5f, radius * .47f,
                Math.max(2f, diameter * .014f), ink);
        drawHand(canvas, cx, cy, minute * 6f, radius * .71f,
                Math.max(1.5f, diameter * .009f), accent);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ink);
        canvas.drawCircle(cx, cy, Math.max(2f, diameter * .014f), paint);
        if (compact) {
            drawPreviewTime(canvas, cx, top + diameter + gap + digitalHeight / 2f,
                    width * .80f, digitalHeight);
        } else {
            float digitalSize = textSizeFor(serif, "88:88", width * .66f, digitalHeight);
            centeredText(canvas, time, cx, top + diameter + gap + digitalHeight / 2f, serif, digitalSize, ink);
        }
    }

    private void drawPreviewTime(Canvas canvas, float x, float y, float width, float height) {
        previewTimePaint.setTypeface(classic);
        previewTimePaint.setColor(ink);
        previewTimePaint.setTextSize(100f);
        Paint.FontMetrics metrics = previewTimePaint.getFontMetrics();
        float size = 100f * Math.min(width / Math.max(1f, previewTimePaint.measureText("88:88")),
                Math.max(1f, height - 2f * density) / Math.max(1f, metrics.bottom - metrics.top));
        previewTimePaint.setTextSize(Math.max(1f, size));
        // Use the full font line box so small preview glyphs keep all their edges.
        StaticLayout label = StaticLayout.Builder.obtain(time, 0, time.length(), previewTimePaint,
                        Math.max(1, (int) width))
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(true)
                .setMaxLines(1)
                .build();
        canvas.save();
        canvas.translate(x - label.getWidth() / 2f, y - label.getHeight() / 2f);
        label.draw(canvas);
        canvas.restore();
    }

    private void drawHand(Canvas canvas, float cx, float cy, float angle, float length, float stroke, int color) {
        canvas.save();
        canvas.translate(cx, cy);
        canvas.rotate(angle);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(color);
        canvas.drawLine(0, length * .12f, 0, -length, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
        canvas.restore();
    }
}
