package com.morningpanel.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;

import java.util.Calendar;

/** Native rendering checks without a third-party test runtime. */
public final class ClockRenderingChecks extends Instrumentation {
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle status = new Bundle();
        status.putString("id", "InstrumentationTestRunner");
        status.putString("class", getClass().getName());
        status.putString("test", "clockRenderingAndCompleteBriefingRows");
        status.putInt("numtests", 1);
        status.putInt("current", 1);
        sendStatus(1, status);
        Throwable[] failure = new Throwable[1];
        runOnMainSync(() -> {
            try {
                checkClocks();
                BriefingLayoutChecks.run(getTargetContext());
            }
            catch (Throwable error) { failure[0] = error; }
        });
        Bundle result = new Bundle();
        if (failure[0] == null) {
            status.putString("stream", ".");
            sendStatus(0, status);
            result.putString("stream", "\nOK (clock rendering; complete initial news viewport, resizing and continuous scrolling)\n");
            finish(Activity.RESULT_OK, result);
        } else {
            String trace = Log.getStackTraceString(failure[0]);
            status.putString("stack", trace);
            status.putString("stream", trace);
            sendStatus(-2, status);
            result.putString("stream", trace);
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void checkClocks() {
        Typeface classic = getTargetContext().getResources().getFont(R.font.zhuque_fangsong);
        ClockFaceView clock = new ClockFaceView(getTargetContext(), classic,
                Color.rgb(42, 51, 53), Color.rgb(112, 120, 120), Color.rgb(39, 119, 121));
        int[][] sizes = {{357, 463}, {357, 421}, {150, 127}, {177, 158}};
        int[][] times = {{0, 0}, {11, 11}, {23, 59}, {8, 26}, {7, 24}};
        String[] expected = {"00:00", "11:11", "23:59", "08:26", "07:24"};
        Calendar date = Calendar.getInstance();
        date.clear();
        date.set(2026, Calendar.OCTOBER, 6);
        for (int style : AppPrefs.clockStyles()) {
            clock.setClockStyle(style);
            for (int[] size : sizes) {
                clock.layout(0, 0, size[0], size[1]);
                for (int i = 0; i < times.length; i++) {
                    date.set(Calendar.HOUR_OF_DAY, times[i][0]);
                    date.set(Calendar.MINUTE, times[i][1]);
                    clock.setTime(date.getTimeInMillis());
                    require(clock.getContentDescription().toString().contains(expected[i]), "Wrong 24-hour time");
                    require(clock.getContentDescription().toString().contains(ClockFaceView.styleName(style)), "Wrong style description");
                    checkArtworkBounds(clock, size[0], size[1], style, expected[i]);
                }
            }
        }
        date.set(Calendar.HOUR_OF_DAY, 23);
        date.set(Calendar.MINUTE, 59);
        clock.setTime(date.getTimeInMillis());
        date.add(Calendar.MINUTE, 1);
        clock.setTime(date.getTimeInMillis());
        require(clock.getContentDescription().toString().contains("00:00"), "Midnight did not update");
        clock.setClockStyle(-1);
        require(clock.getContentDescription().toString().contains("时分上下排"), "Negative style did not fall back");
        clock.setClockStyle(99);
        require(clock.getContentDescription().toString().contains("时分上下排"), "Unknown style did not fall back");
        clock.setClockStyle(2);
        require(clock.getContentDescription().toString().contains("时分上下排"), "Removed style did not fall back");
    }

    private void checkArtworkBounds(ClockFaceView clock, int width, int height, int style, String time) {
        int guard = 24;
        int bitmapWidth = width + guard * 2, bitmapHeight = height + guard * 2;
        Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(bitmap);
            canvas.translate(guard, guard);
            clock.draw(canvas);
            int[] pixels = new int[bitmapWidth * bitmapHeight];
            bitmap.getPixels(pixels, 0, bitmapWidth, 0, 0, bitmapWidth, bitmapHeight);
            int painted = 0;
            for (int y = 0; y < bitmapHeight; y++) {
                for (int x = 0; x < bitmapWidth; x++) {
                    if (Color.alpha(pixels[y * bitmapWidth + x]) == 0) continue;
                    painted++;
                    if (x < guard || x >= guard + width || y < guard || y >= guard + height) {
                        throw new AssertionError("Clipped artwork: " + ClockFaceView.styleName(style)
                                + " " + time + " at " + width + "x" + height);
                    }
                }
            }
            require(painted > 30, "Clock artwork is empty");
        } finally { bitmap.recycle(); }
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
