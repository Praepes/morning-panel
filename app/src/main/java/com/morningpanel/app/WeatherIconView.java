package com.morningpanel.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ImageView;

/** Native vector weather icons, shared by the current weather, forecasts and detail page. */
final class WeatherIconView extends ImageView {
    WeatherIconView(Context context, int code, int tint) {
        super(context);
        setScaleType(ScaleType.FIT_CENTER);
        setImageTintList(ColorStateList.valueOf(tint));
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        setKind(code);
    }

    void setKind(int code) {
        setImageResource(resourceFor(code));
    }

    private static int resourceFor(int code) {
        if (code == 0) return R.drawable.ic_weather_sun;
        if (code == 1 || code == 2) return R.drawable.ic_weather_partly_cloudy;
        if (code == 3) return R.drawable.ic_weather_cloud;
        if (code == 45 || code == 48) return R.drawable.ic_weather_fog;
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return R.drawable.ic_weather_rain;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return R.drawable.ic_weather_snow;
        if (code == 95 || code == 96 || code == 99) return R.drawable.ic_weather_thunder;
        return R.drawable.ic_weather_unknown;
    }
}
