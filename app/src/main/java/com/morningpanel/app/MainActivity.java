package com.morningpanel.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    static final String ACTION_SHOW_ALARM = "com.morningpanel.app.SHOW_ALARM";
    static final String ACTION_SHOW_MESSAGE = "com.morningpanel.app.SHOW_MESSAGE";
    static final String ACTION_WAKE_SCREEN = "com.morningpanel.app.WAKE_SCREEN";
    static final String ACTION_REFRESH_RSS = "com.morningpanel.app.REFRESH_RSS";
    private static final int REQUEST_EXPORT = 4101;
    private static final int REQUEST_IMPORT = 4102;
    private static final int INK = Color.rgb(39, 48, 53);
    private static final int MUTED = Color.rgb(104, 114, 119);
    private static final int ACCENT = Color.rgb(27, 132, 148);
    private static final int HOME_INK = Color.rgb(42, 51, 53);
    private static final int HOME_MUTED = Color.rgb(112, 120, 120);
    private static final int HOME_ACCENT = Color.rgb(39, 119, 121);
    private static final int CANVAS = Color.rgb(246, 244, 238);
    private static final int SURFACE = Color.rgb(255, 253, 248);
    private static final int GOLD = Color.rgb(153, 99, 24);
    private final Handler handler = new Handler();
    private final ExecutorService network = Executors.newFixedThreadPool(4);
    private final ExecutorService haEntityLookup = Executors.newSingleThreadExecutor();
    private ClockFaceView clockView;
    private Button clockStyleSettingsChoice;
    private TextView nextAlarmView;
    private View nextAlarmLine;
    private LinearLayout clockWeekDays;
    private TextView clockWeekMonth;
    private String clockWeekSignature = "";
    private TextView weatherTitle;
    private TextView weatherView;
    private TextView weatherCondition;
    private TextView weatherApparent;
    private TextView weatherHumidity;
    private TextView weatherRainChance;
    private TextView weatherRange;
    private WeatherIconView weatherGlyph;
    private LinearLayout weatherForecast;
    private boolean weatherForecastLoaded;
    private FrameLayout homePage;
    private LinearLayout homeContent;
    private View weatherDetailsPage;
    private View briefingDetailsPage;
    private View briefingImageViewerPage;
    private FrameLayout settingsOverlay;
    private View settingsShell;
    private View alarmEditorPage;
    private LinearLayout settingsNavigation;
    private LinearLayout settingsContent;
    private TextView settingsSectionTitle;
    private TextView settingsSectionNote;
    private TextView settingsFeedback;
    private int activeSettingsSection;
    private Typeface appTypeface;
    private boolean appTypefaceLoaded;
    private TextView haTitle;
    private TextView rssMeta;
    private TextView alarmPanelTitle;
    private LinearLayout alarmList;
    private BriefingScrollView rssList;
    private LinearLayout haList;
    private final java.util.Map<String, View> homeAssistantCards = new java.util.HashMap<>();
    private final java.util.Map<String, JSONObject> homeAssistantRealtimeStates = new java.util.HashMap<>();
    private final java.util.Map<String, Long> homeAssistantRealtimeVersions = new java.util.HashMap<>();
    private long homeAssistantStateEventVersion;
    private boolean homeAssistantStateReceiverRegistered;
    private FrameLayout sliderTrack;
    private TextView sliderKnob;
    private TextView sliderSnoozeLabel;
    private TextView sliderStopLabel;
    private int haEntityRequestGeneration;
    private View alarmPanel;
    private boolean alarmRinging;
    private boolean messageMode;
    private String activeAlarmLabel = "起床时间到了";
    private String externalMessage = "";
    private String queuedMessageAfterAlarm = "";
    private float sliderDownX;
    private float sliderOriginX;
    private static volatile MainActivity visibleInstance;
    private int refreshTurn;

    private final BroadcastReceiver homeAssistantStateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(android.content.Context context, Intent intent) {
            String serialized = intent.getStringExtra(HomeAssistantEventClient.EXTRA_STATE);
            if (serialized == null) return;
            try { applyHomeAssistantRealtimeState(new JSONObject(serialized)); }
            catch (Exception ignored) { }
        }
    };

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            if (clockView != null) clockView.setTime(System.currentTimeMillis());
            updateNextAlarm();
            handler.postDelayed(this, 1000);
        }
    };

    private final Runnable periodicRefresh = new Runnable() {
        @Override public void run() {
            if (isFinishing()) return;
            if (!messageMode) {
                refreshHomeAssistant();
            }
            refreshTurn++;
            if (!messageMode && refreshTurn % 20 == 0) HolidayCalendarClient.synchronizeAsync(MainActivity.this, false, null);
            if (!messageMode && refreshTurn % 20 == 0) refreshWeather();
            if (!messageMode && refreshTurn % 40 == 0) refreshRss();
            handler.postDelayed(this, 30000);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        AlarmScheduler.scheduleAll(this);
        HolidayCalendarClient.synchronizeAsync(this, false, null);
        HomeKeepAliveService.start(this);
        configureImmersiveMode();
        setKeepScreenAwake(AppPrefs.keepScreenOn(this));
        if (isAlarmIntent(getIntent())) prepareForAlarm();
        setContentView(buildScreen());
        handler.post(clockTick);
        applyIncomingIntent(getIntent(), false);
    }

    @Override protected void onResume() {
        super.onResume();
        visibleInstance = this;
        if (!homeAssistantStateReceiverRegistered) {
            registerReceiver(homeAssistantStateReceiver, new IntentFilter(HomeAssistantEventClient.ACTION_STATE_CHANGED));
            homeAssistantStateReceiverRegistered = true;
        }
        HomeKeepAliveService.start(this);
        if (!messageMode) {
            if (alarmList != null) refreshAlarmList();
            refreshHomeAssistant();
            refreshWeather();
            refreshRss();
        }
        handler.removeCallbacks(periodicRefresh);
        handler.postDelayed(periodicRefresh, 30000);
    }

    @Override protected void onPause() {
        if (visibleInstance == this) visibleInstance = null;
        handler.removeCallbacks(periodicRefresh);
        if (homeAssistantStateReceiverRegistered) {
            unregisterReceiver(homeAssistantStateReceiver);
            homeAssistantStateReceiverRegistered = false;
        }
        super.onPause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(clockTick);
        handler.removeCallbacks(periodicRefresh);
        network.shutdownNow();
        haEntityLookup.shutdownNow();
        super.onDestroy();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyIncomingIntent(intent, true);
        configureImmersiveMode();
    }

    @Override public void onBackPressed() {
        if (messageMode) { dismissMessage(); return; }
        if (alarmRinging) return;
        if (alarmEditorPage != null) { dismissAlarmEditor(); return; }
        if (settingsOverlay != null) { closeSettingsMenu(); return; }
        if (briefingImageViewerPage != null) { dismissBriefingImageViewer(); return; }
        if (briefingDetailsPage != null) { dismissBriefingDetails(); return; }
        if (weatherDetailsPage != null) { dismissWeatherDayDetails(); return; }
        super.onBackPressed();
    }

    static void setKeepScreenAwake(boolean keepAwake) {
        MainActivity activity = visibleInstance;
        if (activity != null) activity.runOnUiThread(() -> activity.applyKeepScreenAwake(keepAwake));
    }

    static void setWindowBrightness(float brightness) {
        MainActivity activity = visibleInstance;
        if (activity != null) activity.runOnUiThread(() -> {
            WindowManager.LayoutParams params = activity.getWindow().getAttributes();
            params.screenBrightness = brightness;
            activity.getWindow().setAttributes(params);
        });
    }

    private void configureImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) configureImmersiveMode();
    }

    private void applyKeepScreenAwake(boolean keepAwake) {
        if (keepAwake) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private boolean isAlarmIntent(Intent intent) {
        return intent != null && ACTION_SHOW_ALARM.equals(intent.getAction());
    }

    private void prepareForAlarm() {
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        AppPrefs.setKeepScreenOn(this, true);
    }

    private void applyIncomingIntent(Intent intent, boolean allowRebuild) {
        if (intent == null) return;
        String action = intent.getAction();
        if (ACTION_SHOW_ALARM.equals(action)) {
            if (messageMode && !externalMessage.isEmpty()) queuedMessageAfterAlarm = externalMessage;
            externalMessage = "";
            prepareForAlarm();
            messageMode = false;
            activeAlarmLabel = intent.getStringExtra(AlarmScheduler.EXTRA_LABEL);
            if (activeAlarmLabel == null || activeAlarmLabel.trim().isEmpty()) activeAlarmLabel = "起床时间到了";
            alarmRinging = true;
            if (allowRebuild) setContentView(buildScreen());
            showAlarmPanel();
            return;
        }
        if (ACTION_SHOW_MESSAGE.equals(action) || Intent.ACTION_SEND.equals(action)) {
            CharSequence value = intent.getCharSequenceExtra("message");
            if (value == null) value = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (value != null && value.toString().trim().length() > 0) {
                String incoming = value.toString().trim();
                if (alarmRinging) {
                    queuedMessageAfterAlarm = incoming;
                    return;
                }
                externalMessage = incoming;
                messageMode = true;
                alarmRinging = false;
                if (allowRebuild) setContentView(buildMessageScreen());
                else setContentView(buildMessageScreen());
            }
            return;
        }
        if (ACTION_WAKE_SCREEN.equals(action)) {
            messageMode = false;
            if (allowRebuild) setContentView(buildScreen());
            applyKeepScreenAwake(true);
            return;
        }
        if (ACTION_REFRESH_RSS.equals(action)) {
            messageMode = false;
            if (allowRebuild) setContentView(buildScreen());
            refreshRss();
        }
    }

    static void requestRssRefresh(android.content.Context context) {
        MainActivity activity = visibleInstance;
        if (activity != null) {
            activity.runOnUiThread(() -> activity.refreshRss());
        } else {
            context.startActivity(new Intent(context, MainActivity.class)
                    .setAction(ACTION_REFRESH_RSS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        }
    }

    private void dismissMessage() {
        messageMode = false;
        externalMessage = "";
        setContentView(buildScreen());
        refreshWeather();
        refreshRss();
        refreshHomeAssistant();
    }

    private View buildScreen() {
        FrameLayout page = new FrameLayout(this);
        homePage = page;
        weatherDetailsPage = null;
        briefingDetailsPage = null;
        briefingImageViewerPage = null;
        page.setBackground(backgroundGradient(CANVAS, Color.rgb(237, 235, 228)));

        LinearLayout root = new LinearLayout(this);
        homeContent = root;
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(38), dp(20), dp(38), dp(18));
        FrameLayout.LayoutParams rootParams = new FrameLayout.LayoutParams(-1, -1);
        page.addView(root, rootParams);

        LinearLayout content = new LinearLayout(this);
        content.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(-1, 0, 1);
        contentParams.bottomMargin = dp(16);
        root.addView(content, contentParams);

        FrameLayout clockCard = new FrameLayout(this);
        clockCard.setBackground(cardBackground(SURFACE, 26));
        LinearLayout clock = new LinearLayout(this);
        clock.setGravity(Gravity.CENTER);
        clock.setOrientation(LinearLayout.VERTICAL);
        clock.setPadding(dp(18), dp(14), dp(18), dp(14));
        clockView = new ClockFaceView(this, zhuqueTypeface(), HOME_INK, HOME_MUTED, HOME_ACCENT);
        clockView.setClockStyle(AppPrefs.clockStyle(this));
        clockView.setFocusable(true);
        clockView.setOnLongClickListener(v -> {
            showClockStylePicker();
            return true;
        });
        clockView.setClickable(false);
        nextAlarmView = compactText("", 14, GOLD, false);
        nextAlarmView.setSingleLine(true);
        nextAlarmView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nextAlarmView.setGravity(Gravity.CENTER);
        clock.addView(clockView, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout alarmLine = new LinearLayout(this);
        alarmLine.setGravity(Gravity.CENTER);
        alarmLine.setMinimumHeight(dp(44));
        MinimalGlyphView alarmGlyph = new MinimalGlyphView(this, MinimalGlyphView.ALARM, GOLD);
        LinearLayout.LayoutParams alarmGlyphParams = new LinearLayout.LayoutParams(dp(18), dp(18));
        alarmGlyphParams.rightMargin = dp(7);
        alarmLine.addView(alarmGlyph, alarmGlyphParams);
        alarmLine.addView(nextAlarmView, new LinearLayout.LayoutParams(-2, -2));
        alarmLine.setFocusable(true);
        alarmLine.setOnClickListener(v -> {
            showSettingsMenu();
            renderSettingsSection(0);
        });
        nextAlarmLine = alarmLine;
        clock.addView(alarmLine, marginTop(12));
        FrameLayout.LayoutParams clockArea = new FrameLayout.LayoutParams(-1, -1);
        clockArea.topMargin = dp(14);
        clockArea.bottomMargin = dp(182);
        clockCard.addView(clock, clockArea);
        clockCard.addView(buildClockWeekCalendar());
        LinearLayout.LayoutParams clockParams = new LinearLayout.LayoutParams(0, -1, 0.98f);
        clockParams.rightMargin = dp(28);
        content.addView(clockCard, clockParams);

        LinearLayout weather = new LinearLayout(this);
        weather.setOrientation(LinearLayout.VERTICAL);
        weather.setGravity(Gravity.TOP);
        weather.setPadding(dp(22), dp(10), dp(20), dp(13));
        weather.setBackground(cardBackground(SURFACE, 26));
        LinearLayout weatherHeader = new LinearLayout(this);
        weatherHeader.setGravity(Gravity.CENTER_VERTICAL);
        weatherTitle = compactText(AppPrefs.city(this), 22, HOME_ACCENT, true);
        weatherTitle.setSingleLine(true);
        weatherTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        weatherHeader.addView(weatherTitle, new LinearLayout.LayoutParams(0, -2, 1));
        weather.addView(weatherHeader);

        weatherView = compactText("—°", 88, HOME_INK, false);
        weatherView.setSingleLine(true);
        weatherCondition = compactText("正在获取天气…", 22, HOME_INK, false);
        weatherCondition.setMaxLines(1);
        weatherCondition.setEllipsize(android.text.TextUtils.TruncateAt.END);
        weatherGlyph = new WeatherIconView(this, 3, HOME_ACCENT);
        weatherGlyph.setVisibility(View.INVISIBLE);
        LinearLayout currentWeather = new LinearLayout(this);
        currentWeather.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout currentSummary = new LinearLayout(this);
        currentSummary.setOrientation(LinearLayout.VERTICAL);
        currentSummary.addView(weatherView);
        currentSummary.addView(weatherCondition, marginTop(3));
        currentWeather.addView(currentSummary, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams glyphParams = new LinearLayout.LayoutParams(dp(76), dp(76));
        glyphParams.leftMargin = dp(16);
        currentWeather.addView(weatherGlyph, glyphParams);
        weather.addView(currentWeather, marginTop(-4));

        LinearLayout weatherMeasures = new LinearLayout(this);
        weatherApparent = addWeatherMeasure(weatherMeasures, "体感");
        weatherHumidity = addWeatherMeasure(weatherMeasures, "湿度");
        weatherRainChance = addWeatherMeasure(weatherMeasures, "降雨概率");
        weatherRange = addWeatherMeasure(weatherMeasures, "今日范围");
        weather.addView(weatherMeasures, marginTop(18));
        View weatherDivider = new View(this);
        weatherDivider.setBackgroundColor(Color.argb(34, 65, 81, 82));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
        dividerParams.topMargin = dp(20);
        dividerParams.bottomMargin = dp(18);
        weather.addView(weatherDivider, dividerParams);
        weatherForecast = new LinearLayout(this);
        weatherForecast.setOrientation(LinearLayout.VERTICAL);
        weatherForecastLoaded = false;
        weatherForecast.addView(compactText("预报正在更新…", 14, HOME_MUTED, false));
        ScrollView forecastScroll = new ScrollView(this);
        forecastScroll.setVerticalScrollBarEnabled(false);
        forecastScroll.addView(weatherForecast);
        weather.addView(forecastScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout.LayoutParams weatherParams = new LinearLayout.LayoutParams(0, -1, 1.0f);
        weatherParams.rightMargin = dp(28);
        content.addView(weather, weatherParams);

        LinearLayout news = new LinearLayout(this);
        news.setOrientation(LinearLayout.VERTICAL);
        news.setPadding(dp(22), dp(18), dp(22), dp(18));
        news.setBackground(cardBackground(SURFACE, 26));
        LinearLayout newsHeader = new LinearLayout(this);
        newsHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView briefingTitle = text("简报", 22, HOME_INK, false);
        newsHeader.addView(briefingTitle, new LinearLayout.LayoutParams(0, -2, 1));
        news.addView(newsHeader);
        rssMeta = text("正在获取资讯…", 12, HOME_MUTED, false);
        news.addView(rssMeta, marginTop(7));
        rssList = new BriefingScrollView(this);
        rssList.setPadding(0, dp(6), 0, dp(4));
        news.addView(rssList, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout.LayoutParams newsParams = new LinearLayout.LayoutParams(0, -1, 1.24f);
        content.addView(news, newsParams);

        LinearLayout statusBand = new LinearLayout(this);
        statusBand.setGravity(Gravity.CENTER_VERTICAL);
        statusBand.setPadding(0, 0, 0, 0);
        LinearLayout ha = new LinearLayout(this);
        ha.setOrientation(LinearLayout.VERTICAL);
        haTitle = text("HOME ASSISTANT", 12, HOME_ACCENT, true);
        HorizontalScrollView haScroll = new HorizontalScrollView(this);
        haScroll.setHorizontalScrollBarEnabled(false);
        haList = new LinearLayout(this);
        haList.setGravity(Gravity.CENTER_VERTICAL);
        haList.addView(text("未连接", 14, HOME_MUTED, false));
        haScroll.addView(haList);
        ha.addView(haScroll);
        statusBand.addView(ha, new LinearLayout.LayoutParams(0, -2, 1));
        FrameLayout settings = new FrameLayout(this);
        settings.setFocusable(true);
        settings.setContentDescription("设置");
        settings.setBackground(new android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(Color.argb(28, 72, 91, 91)), null,
                cardBackground(Color.WHITE, 24)));
        settings.setOnClickListener(v -> showSettingsMenu());
        MinimalGlyphView settingsGlyph = new MinimalGlyphView(this, MinimalGlyphView.SETTINGS,
                Color.rgb(134, 142, 142));
        settings.addView(settingsGlyph, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        settingsParams.leftMargin = dp(12);
        statusBand.addView(settings, settingsParams);
        root.addView(statusBand);

        alarmPanel = buildAlarmPanel();
        alarmPanel.setVisibility(alarmRinging ? View.VISIBLE : View.GONE);
        int alarmWidth = Math.min(dp(900), getResources().getDisplayMetrics().widthPixels - dp(96));
        FrameLayout.LayoutParams alarmParams = new FrameLayout.LayoutParams(alarmWidth, dp(142), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        alarmParams.bottomMargin = dp(20);
        page.addView(alarmPanel, alarmParams);
        updateNextAlarm();
        return page;
    }

    private void applyClockStyle(int style) {
        AppPrefs.setClockStyle(this, style);
        String name = ClockFaceView.styleName(AppPrefs.clockStyle(this));
        if (clockView != null) clockView.setClockStyle(AppPrefs.clockStyle(this));
        if (clockStyleSettingsChoice != null) clockStyleSettingsChoice.setText(name + "  ⌄");
    }

    private void showClockStylePicker() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(22), dp(24), dp(20));
        content.setBackground(cardBackground(SURFACE, 22));
        content.addView(compactText("时钟样式", 23, HOME_INK, true));
        LinearLayout choices = new LinearLayout(this);
        content.addView(choices, marginTop(16));
        AlertDialog dialog = new AlertDialog.Builder(this).setView(content).create();
        int current = AppPrefs.clockStyle(this);
        long previewTime = System.currentTimeMillis();
        int[] styles = AppPrefs.clockStyles();
        for (int i = 0; i < styles.length; i++) {
            final int style = styles[i];
            boolean selected = style == current;
            LinearLayout option = new LinearLayout(this);
            option.setOrientation(LinearLayout.VERTICAL);
            option.setGravity(Gravity.CENTER_HORIZONTAL);
            option.setPadding(dp(10), dp(12), dp(10), dp(14));
            GradientDrawable background = cardBackground(selected ? Color.rgb(235, 242, 237) : Color.rgb(247, 246, 241), 16);
            if (selected) background.setStroke(dp(1), HOME_ACCENT);
            option.setBackground(background);
            ClockFaceView preview = new ClockFaceView(this, zhuqueTypeface(), HOME_INK, HOME_MUTED, HOME_ACCENT);
            preview.setClockStyle(style);
            preview.setTime(previewTime);
            preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            option.addView(preview, new LinearLayout.LayoutParams(-1, dp(210)));
            option.setFocusable(true);
            option.setContentDescription(ClockFaceView.styleName(style) + (selected ? "，当前样式" : ""));
            option.setOnClickListener(v -> {
                applyClockStyle(style);
                dialog.dismiss();
            });
            LinearLayout.LayoutParams optionParams = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) optionParams.leftMargin = dp(10);
            choices.addView(option, optionParams);
        }
        Button close = button("关闭", false);
        close.setOnClickListener(v -> dialog.dismiss());
        content.addView(close, marginTop(16));
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(Math.min(dp(840), getResources().getDisplayMetrics().widthPixels - dp(96)), -2);
            dialog.getWindow().getDecorView().setSystemUiVisibility(getWindow().getDecorView().getSystemUiVisibility());
        }
        configureImmersiveMode();
    }

    private LinearLayout buildClockWeekCalendar() {
        LinearLayout calendar = new LinearLayout(this);
        calendar.setOrientation(LinearLayout.VERTICAL);
        LinearLayout calendarHeading = new LinearLayout(this);
        calendarHeading.setGravity(Gravity.CENTER_VERTICAL);
        clockWeekMonth = compactText("", 16, HOME_MUTED, false);
        calendarHeading.addView(clockWeekMonth, new LinearLayout.LayoutParams(-2, -2));
        View divider = new View(this);
        divider.setBackgroundColor(Color.argb(30, 65, 81, 82));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(0, dp(1), 1);
        dividerParams.leftMargin = dp(18);
        calendarHeading.addView(divider, dividerParams);
        calendar.addView(calendarHeading);
        clockWeekDays = new LinearLayout(this);
        clockWeekDays.setGravity(Gravity.CENTER_VERTICAL);
        calendar.addView(clockWeekDays, marginTop(20));
        clockWeekSignature = "";

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        params.leftMargin = dp(30);
        params.rightMargin = dp(30);
        params.bottomMargin = dp(38);
        calendar.setLayoutParams(params);
        return calendar;
    }

    private LinearLayout panel() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(16), dp(12), dp(16), dp(12));
        view.setBackground(cardBackground(SURFACE, 18));
        return view;
    }

    private LinearLayout sectionHeader(String title, String subtitle) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(text(title, 19, INK, true), new LinearLayout.LayoutParams(0, dp(34), 1));
        row.addView(text(subtitle, 12, ACCENT, true));
        return row;
    }

    private View buildMessageScreen() {
        homePage = null;
        homeContent = null;
        weatherDetailsPage = null;
        briefingDetailsPage = null;
        briefingImageViewerPage = null;
        FrameLayout page = new FrameLayout(this);
        page.setBackground(backgroundGradient(CANVAS, Color.rgb(237, 235, 228)));
        TextView message = text(externalMessage, 44, HOME_INK, true);
        message.setGravity(Gravity.CENTER);
        message.setPadding(dp(72), dp(40), dp(72), dp(40));
        message.setOnClickListener(v -> dismissMessage());
        page.setOnClickListener(v -> dismissMessage());
        page.addView(message, new FrameLayout.LayoutParams(-1, -1));
        return page;
    }

    private View buildAlarmPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(11), dp(20), dp(12));
        panel.setBackground(cardBackground(Color.argb(245, 255, 253, 248), 22));
        alarmPanelTitle = text(activeAlarmLabel, 16, GOLD, true);
        alarmPanelTitle.setGravity(Gravity.CENTER);
        panel.addView(alarmPanelTitle, new LinearLayout.LayoutParams(-1, dp(29)));

        sliderTrack = new FrameLayout(this);
        sliderTrack.setBackground(cardBackground(Color.argb(75, 225, 240, 244), 36));
        sliderSnoozeLabel = text("←  贪睡 10 分钟", 14, HOME_INK, true);
        sliderSnoozeLabel.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        FrameLayout.LayoutParams left = new FrameLayout.LayoutParams(-2, -1, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        left.leftMargin = dp(23);
        sliderTrack.addView(sliderSnoozeLabel, left);
        sliderStopLabel = text("停止闹钟  →", 14, HOME_INK, true);
        sliderStopLabel.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        FrameLayout.LayoutParams right = new FrameLayout.LayoutParams(-2, -1, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        right.rightMargin = dp(23);
        sliderTrack.addView(sliderStopLabel, right);
        sliderKnob = text("●", 24, Color.WHITE, true);
        sliderKnob.setGravity(Gravity.CENTER);
        sliderKnob.setBackground(cardBackground(HOME_ACCENT, 40));
        FrameLayout.LayoutParams knobParams = new FrameLayout.LayoutParams(dp(58), dp(58), Gravity.CENTER_VERTICAL | Gravity.LEFT);
        sliderTrack.addView(sliderKnob, knobParams);
        sliderTrack.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> resetAlarmSlider());
        sliderTrack.setOnTouchListener((v, event) -> onAlarmSliderTouch(event));
        int trackWidth = Math.min(dp(720), getResources().getDisplayMetrics().widthPixels - dp(200));
        LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(trackWidth, dp(68));
        trackParams.gravity = Gravity.CENTER_HORIZONTAL;
        trackParams.topMargin = dp(5);
        panel.addView(sliderTrack, trackParams);
        return panel;
    }

    private void showAlarmPanel() {
        dismissWeatherDayDetails();
        if (alarmPanel == null) return;
        alarmPanel.setVisibility(View.VISIBLE);
        if (alarmPanelTitle != null) alarmPanelTitle.setText(activeAlarmLabel);
        resetAlarmSlider();
    }

    private void resetAlarmSlider() {
        if (sliderTrack == null || sliderKnob == null || sliderTrack.getWidth() == 0) return;
        sliderOriginX = (sliderTrack.getWidth() - sliderKnob.getWidth()) / 2f;
        sliderKnob.setX(sliderOriginX);
        sliderKnob.setScaleX(1f);
        sliderKnob.setScaleY(1f);
        if (sliderSnoozeLabel != null) sliderSnoozeLabel.setAlpha(1f);
        if (sliderStopLabel != null) sliderStopLabel.setAlpha(1f);
    }

    private boolean onAlarmSliderTouch(MotionEvent event) {
        if (sliderKnob == null) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                sliderDownX = event.getX();
                sliderOriginX = sliderKnob.getX();
                sliderKnob.animate().scaleX(1.12f).scaleY(1.12f).setDuration(110).start();
                return true;
            case MotionEvent.ACTION_MOVE:
                float max = Math.max(0, sliderTrack.getWidth() - sliderKnob.getWidth());
                float nextX = Math.max(0, Math.min(max, sliderOriginX + event.getX() - sliderDownX));
                sliderKnob.setX(nextX);
                float progress = max == 0 ? 0f : Math.min(1f, Math.abs(nextX - max / 2f) / (max / 2f));
                float scale = 1.12f + progress * 0.12f;
                sliderKnob.animate().scaleX(scale).scaleY(scale).setDuration(75).start();
                if (sliderSnoozeLabel != null) sliderSnoozeLabel.setAlpha(nextX < max / 2f ? 1f : 0.58f);
                if (sliderStopLabel != null) sliderStopLabel.setAlpha(nextX > max / 2f ? 1f : 0.58f);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                float delta = sliderKnob.getX() - (sliderTrack.getWidth() - sliderKnob.getWidth()) / 2f;
                float threshold = sliderTrack.getWidth() * 0.22f;
                if (event.getActionMasked() == MotionEvent.ACTION_UP && Math.abs(delta) > threshold) {
                    boolean snooze = delta < 0;
                    float target = snooze ? 0f : sliderTrack.getWidth() - sliderKnob.getWidth();
                    sliderKnob.animate().x(target).scaleX(1.08f).scaleY(1.08f).setDuration(190)
                            .withEndAction(() -> finishRinging(snooze)).start();
                } else {
                    sliderKnob.animate().x((sliderTrack.getWidth() - sliderKnob.getWidth()) / 2f)
                            .scaleX(1f).scaleY(1f).setDuration(220).start();
                    if (sliderSnoozeLabel != null) sliderSnoozeLabel.animate().alpha(1f).setDuration(150).start();
                    if (sliderStopLabel != null) sliderStopLabel.animate().alpha(1f).setDuration(150).start();
                }
                return true;
            default:
                return true;
        }
    }

    private void finishRinging(boolean snooze) {
        if (!alarmRinging) return;
        if (snooze) AlarmScheduler.scheduleSnooze(this, activeAlarmLabel);
        else AlarmScheduler.cancelSnooze(this);
        stopService(new Intent(this, AlarmService.class));
        alarmRinging = false;
        if (alarmPanel != null) alarmPanel.setVisibility(View.GONE);
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(false);
            setTurnScreenOn(false);
        } else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        refreshHomeAssistant();
        if (!queuedMessageAfterAlarm.isEmpty()) {
            externalMessage = queuedMessageAfterAlarm;
            queuedMessageAfterAlarm = "";
            messageMode = true;
            setContentView(buildMessageScreen());
        }
    }

    private GradientDrawable backgroundGradient(int start, int end) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        drawable.setCornerRadius(dp(0));
        return drawable;
    }

    private void showSettingsMenu() {
        if (settingsOverlay != null || homePage == null) return;
        settingsOverlay = new FrameLayout(this);
        settingsOverlay.setBackground(backgroundGradient(CANVAS, Color.rgb(237, 235, 228)));
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(32), dp(20), dp(32), dp(22));
        settingsShell = shell;

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = button("← 主页", false);
        back.setTextSize(17);
        back.setOnClickListener(v -> closeSettingsMenu());
        header.addView(back, new LinearLayout.LayoutParams(dp(142), dp(54)));
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(22), 0, 0, 0);
        heading.addView(compactText("设置", 30, HOME_INK, true));
        heading.addView(compactText("晨间面板 · 闹钟、天气与设备", 13, HOME_MUTED, false), marginTop(3));
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        shell.addView(header);

        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams columnsParams = new LinearLayout.LayoutParams(-1, 0, 1);
        columnsParams.topMargin = dp(17);
        shell.addView(columns, columnsParams);

        ScrollView navigationScroll = new ScrollView(this);
        navigationScroll.setVerticalScrollBarEnabled(false);
        navigationScroll.setFillViewport(true);
        settingsNavigation = new LinearLayout(this);
        settingsNavigation.setOrientation(LinearLayout.VERTICAL);
        settingsNavigation.setPadding(dp(12), dp(12), dp(12), dp(12));
        settingsNavigation.setBackground(cardBackground(Color.argb(90, 255, 253, 248), 22));
        LinearLayout.LayoutParams navigationParams = new LinearLayout.LayoutParams(dp(246), -1);
        navigationParams.rightMargin = dp(18);
        navigationScroll.addView(settingsNavigation, new ScrollView.LayoutParams(-1, -2));
        columns.addView(navigationScroll, navigationParams);

        LinearLayout detail = new LinearLayout(this);
        detail.setOrientation(LinearLayout.VERTICAL);
        detail.setPadding(dp(26), dp(22), dp(26), dp(16));
        detail.setBackground(cardBackground(SURFACE, 22));
        columns.addView(detail, new LinearLayout.LayoutParams(0, -1, 1));
        settingsSectionTitle = compactText("", 25, HOME_INK, true);
        detail.addView(settingsSectionTitle);
        settingsSectionNote = compactText("", 14, HOME_MUTED, false);
        detail.addView(settingsSectionNote, marginTop(4));
        ScrollView contentScroll = new ScrollView(this);
        contentScroll.setFillViewport(false);
        contentScroll.setVerticalScrollBarEnabled(false);
        settingsContent = new LinearLayout(this);
        settingsContent.setOrientation(LinearLayout.VERTICAL);
        settingsContent.setPadding(0, dp(14), 0, dp(12));
        contentScroll.addView(settingsContent);
        detail.addView(contentScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        settingsFeedback = compactText("", 14, HOME_ACCENT, true);
        settingsFeedback.setMinHeight(dp(26));
        detail.addView(settingsFeedback, marginTop(4));

        settingsOverlay.addView(shell, new FrameLayout.LayoutParams(-1, -1));
        homeContent.setVisibility(View.INVISIBLE);
        homePage.addView(settingsOverlay, new FrameLayout.LayoutParams(-1, -1));
        renderSettingsSection(0);
        configureImmersiveMode();
    }

    private void closeSettingsMenu() {
        if (alarmEditorPage != null) { dismissAlarmEditor(); return; }
        if (settingsOverlay == null) return;
        homePage.removeView(settingsOverlay);
        settingsOverlay = null;
        settingsShell = null;
        settingsNavigation = null;
        settingsContent = null;
        settingsSectionTitle = null;
        settingsSectionNote = null;
        settingsFeedback = null;
        clockStyleSettingsChoice = null;
        alarmList = null;
        homeContent.setVisibility(View.VISIBLE);
        configureImmersiveMode();
    }

    private void renderSettingsSection(int section) {
        if (settingsContent == null) return;
        activeSettingsSection = section;
        String[] titles = {"闹钟与节假日", "天气", "新闻简报", "Home Assistant", "显示与电源", "诊断与备份"};
        String[] notes = {
                "管理本机闹钟，并查看中国大陆休息日与补班日安排。",
                "选择天气地点并调整首页天气卡片。",
                "分别编辑 RSS / Atom 来源与备注；配图只在简报详情中显示。",
                "连接家庭服务器，选择首页实体并查看本机状态。",
                "选择时钟样式，设置屏幕常亮、电池优化和系统亮度权限。",
                "检查设备能力，导入或导出闹钟配置。"
        };
        settingsNavigation.removeAllViews();
        for (int i = 0; i < titles.length; i++) {
            final int index = i;
            TextView choice = compactText(titles[i], 17, i == section ? Color.WHITE : HOME_INK, i == section);
            choice.setGravity(Gravity.CENTER_VERTICAL);
            choice.setPadding(dp(17), 0, dp(12), 0);
            choice.setBackground(cardBackground(i == section ? HOME_ACCENT : Color.TRANSPARENT, 15));
            choice.setMinHeight(dp(58));
            choice.setOnClickListener(v -> renderSettingsSection(index));
            LinearLayout.LayoutParams choiceParams = new LinearLayout.LayoutParams(-1, dp(58));
            choiceParams.bottomMargin = dp(7);
            settingsNavigation.addView(choice, choiceParams);
        }
        settingsSectionTitle.setText(titles[section]);
        settingsSectionNote.setText(notes[section]);
        settingsContent.removeAllViews();
        clockStyleSettingsChoice = null;
        settingsFeedback.setText("");
        if (section == 0) buildAlarmSettingsSection();
        else if (section == 1) buildWeatherSettingsSection();
        else if (section == 2) buildRssSettingsSection();
        else if (section == 3) buildHomeAssistantSettingsSection();
        else if (section == 4) buildDeviceSettingsSection();
        else buildDiagnosticsSettingsSection();
    }

    private void buildAlarmSettingsSection() {
        LinearLayout form = settingsContent;
        addSettingsLabel(form, "日历地区");
        Spinner region = new Spinner(this);
        ArrayAdapter<String> regionAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{"中国大陆"});
        regionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        region.setAdapter(regionAdapter);
        region.setSelection(0);
        region.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                AppPrefs.setHolidayRegion(MainActivity.this, "CN");
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        form.addView(region, new LinearLayout.LayoutParams(dp(260), dp(54)));

        LinearLayout calendarRow = new LinearLayout(this);
        calendarRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView calendarStatus = compactText(HolidayCalendarStore.status(this), 14, HOME_MUTED, false);
        calendarRow.addView(calendarStatus, new LinearLayout.LayoutParams(0, -2, 1));
        Button refresh = button("刷新日历", false);
        refresh.setTextSize(15);
        refresh.setOnClickListener(v -> {
            calendarStatus.setText("正在更新中国大陆年度日历…");
            refresh.setEnabled(false);
            HolidayCalendarClient.synchronizeAsync(this, true, () -> runOnUiThread(() -> {
                if (calendarStatus.getParent() != null) calendarStatus.setText(HolidayCalendarStore.status(this));
                refresh.setEnabled(true);
                refreshAlarmList();
                updateNextAlarm();
                showSettingsFeedback("日历已检查；无效或未发布的数据不会覆盖本机缓存。", true);
            }));
        });
        calendarRow.addView(refresh, new LinearLayout.LayoutParams(dp(146), dp(50)));
        form.addView(calendarRow, marginTop(5));
        TextView sourceNote = compactText("来源：holiday.ailcc.com 中国法定节假日 API。数据在后台预先缓存，闹钟响铃不依赖网络。年度安排未发布或无法连接时，保留上次有效日历；没有年度缓存则暂按周一至周五判断。", 13, HOME_MUTED, false);
        sourceNote.setLineSpacing(dp(3), 1f);
        form.addView(sourceNote, marginTop(6));

        Button add = button("＋  新增闹钟", true);
        add.setOnClickListener(v -> editAlarm(null));
        form.addView(add, marginTop(15));
        TextView listHeading = compactText("已设闹钟", 17, HOME_INK, true);
        form.addView(listHeading, marginTop(15));
        alarmList = new LinearLayout(this);
        alarmList.setOrientation(LinearLayout.VERTICAL);
        form.addView(alarmList, marginTop(5));
        refreshAlarmList();
    }

    private void buildWeatherSettingsSection() {
        LinearLayout form = settingsContent;
        EditText city = settingField("城市名称", AppPrefs.city(this), InputType.TYPE_CLASS_TEXT);
        EditText latitude = settingField("纬度（-90 到 90）", String.valueOf(AppPrefs.latitude(this)),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText longitude = settingField("经度（-180 到 180）", String.valueOf(AppPrefs.longitude(this)),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        Button searchPlace = button("搜索城市或地区", false);
        searchPlace.setTextSize(16);
        searchPlace.setOnClickListener(v -> promptWeatherPlace(city, latitude, longitude));
        form.addView(searchPlace);
        form.addView(city, marginTop(8));
        form.addView(latitude, marginTop(4));
        form.addView(longitude, marginTop(4));
        Button save = button("保存天气地点", true);
        save.setOnClickListener(v -> {
            try {
                double lat = Double.parseDouble(latitude.getText().toString());
                double lon = Double.parseDouble(longitude.getText().toString());
                if (lat < -90 || lat > 90 || lon < -180 || lon > 180 || city.getText().toString().trim().isEmpty())
                    throw new IllegalArgumentException("城市名称或经纬度不正确。");
                AppPrefs.setWeather(this, city.getText().toString(), lat, lon);
                if (weatherTitle != null) weatherTitle.setText(AppPrefs.city(this));
                refreshWeather();
                showSettingsFeedback("天气地点已保存。", true);
            } catch (Exception error) { showSettingsFeedback("无法保存：" + safeMessage(error), false); }
        });
        form.addView(save, marginTop(15));
    }

    private void buildRssSettingsSection() {
        LinearLayout form = settingsContent;
        TextView recommendations = compactText("每个 RSS / Atom 地址都是独立来源。备注仅用于你自己识别来源；首页简报不显示配图，打开详情后才加载图片。", 14, HOME_MUTED, false);
        recommendations.setLineSpacing(dp(3), 1f);
        form.addView(recommendations);
        LinearLayout sourceList = new LinearLayout(this);
        sourceList.setOrientation(LinearLayout.VERTICAL);
        ArrayList<EditText[]> fields = new ArrayList<>();
        for (AppPrefs.RssSource source : AppPrefs.rssSources(this))
            addRssSourceEditorRow(sourceList, fields, source);
        form.addView(sourceList, marginTop(10));
        Button addSource = button("＋  添加新闻来源", false);
        addSource.setOnClickListener(v -> {
            addRssSourceEditorRow(sourceList, fields, new AppPrefs.RssSource("", ""));
            settingsContent.post(() -> {
                if (sourceList.getChildCount() > 0) sourceList.getChildAt(sourceList.getChildCount() - 1).requestFocus();
            });
        });
        form.addView(addSource, marginTop(9));
        Button save = button("保存新闻来源", true);
        save.setOnClickListener(v -> {
            ArrayList<AppPrefs.RssSource> sources = new ArrayList<>();
            for (EditText[] row : fields) {
                String url = row[0].getText().toString().trim();
                String note = row[1].getText().toString().trim();
                if (url.isEmpty()) {
                    if (!note.isEmpty()) {
                        showSettingsFeedback("请为填写了备注的来源补上 RSS / Atom 地址，或删除该行。", false);
                        return;
                    }
                    continue;
                }
                if (!(url.startsWith("https://") || url.startsWith("http://"))) {
                    showSettingsFeedback("每个新闻源都必须以 http:// 或 https:// 开头。", false);
                    return;
                }
                sources.add(new AppPrefs.RssSource(url, note));
            }
            AppPrefs.setRssSources(this, sources);
            refreshRss();
            showSettingsFeedback("新闻来源已保存，正在更新简报。", true);
        });
        form.addView(save, marginTop(12));
    }

    private void addRssSourceEditorRow(LinearLayout parent, ArrayList<EditText[]> fields, AppPrefs.RssSource source) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(10), dp(14), dp(11));
        card.setBackground(cardBackground(Color.WHITE, 16));
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = compactText("新闻来源 " + (parent.getChildCount() + 1), 15, HOME_ACCENT, true);
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button remove = button("删除", false);
        remove.setTextSize(14);
        remove.setMinHeight(dp(42));
        remove.setPadding(dp(14), 0, dp(14), 0);
        heading.addView(remove);
        card.addView(heading);
        EditText address = settingField("RSS / Atom 地址", source.url,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        card.addView(address, marginTop(5));
        EditText note = settingField("备注（仅自己可见）", source.note, InputType.TYPE_CLASS_TEXT);
        card.addView(note, marginTop(5));
        EditText[] row = new EditText[]{address, note};
        fields.add(row);
        remove.setOnClickListener(v -> {
            fields.remove(row);
            parent.removeView(card);
            int index = 1;
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                if (child instanceof LinearLayout) {
                    LinearLayout childCard = (LinearLayout) child;
                    if (childCard.getChildCount() > 0 && childCard.getChildAt(0) instanceof LinearLayout) {
                        LinearLayout childHeading = (LinearLayout) childCard.getChildAt(0);
                        if (childHeading.getChildCount() > 0 && childHeading.getChildAt(0) instanceof TextView)
                            ((TextView) childHeading.getChildAt(0)).setText("新闻来源 " + index++);
                    }
                }
            }
        });
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        if (parent.getChildCount() > 0) cardParams.topMargin = dp(8);
        parent.addView(card, cardParams);
    }

    private void buildHomeAssistantSettingsSection() {
        LinearLayout form = settingsContent;
        EditText url = settingField("Home Assistant 地址，例如 http://homeassistant.local:8123", AppPrefs.haUrl(this),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        EditText token = settingField("长期访问令牌（留空保留已保存令牌）", "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText entities = new EditText(this);
        entities.setTypeface(zhuqueTypeface());
        entities.setHint("首页 HA 实体 ID，每行一个");
        entities.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        entities.setGravity(Gravity.TOP | Gravity.LEFT);
        entities.setMinLines(3);
        entities.setText(AppPrefs.haHomeEntities(this));
        CheckBox allowHttp = settingsCheck("允许局域网 HTTP（建议使用 HTTPS）", AppPrefs.haAllowHttp(this));
        form.addView(url);
        form.addView(token, marginTop(4));
        addSettingsLabel(form, "主页显示的 HA 设备");
        form.addView(entities);
        form.addView(allowHttp, marginTop(5));
        TextView pairingId = compactText("Companion Link 配对 ID（在 HA 添加集成时输入）：\n"
                + AppPrefs.companionLinkDeviceId(this), 13, HOME_MUTED, false);
        pairingId.setTextIsSelectable(true);
        form.addView(pairingId, marginTop(5));
        TextView info = compactText("令牌由 Android Keystore 加密保存，不会放入闹钟备份。本机电量、网络和屏幕状态会在连接后自动上报。", 13, HOME_MUTED, false);
        info.setLineSpacing(dp(3), 1f);
        form.addView(info, marginTop(5));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button choose = button("选择主页实体", false);
        choose.setTextSize(15);
        choose.setOnClickListener(v -> loadHomeAssistantEntities(url.getText().toString().trim(),
                token.getText().toString().trim(), allowHttp.isChecked(), entities));
        Button report = button("测试状态上报", false);
        report.setTextSize(15);
        report.setOnClickListener(v -> testLocalStatusReport(url.getText().toString().trim(),
                token.getText().toString().trim(), allowHttp.isChecked()));
        actions.addView(choose, new LinearLayout.LayoutParams(0, dp(54), 1));
        LinearLayout.LayoutParams reportParams = new LinearLayout.LayoutParams(0, dp(54), 1);
        reportParams.leftMargin = dp(8);
        actions.addView(report, reportParams);
        form.addView(actions, marginTop(6));

        LinearLayout saveActions = new LinearLayout(this);
        Button clear = button("清除令牌", false);
        clear.setTextSize(15);
        clear.setOnClickListener(v -> showBrandedConfirm("清除 Home Assistant 令牌？",
                "清除后需要重新输入长期访问令牌才能连接。", "取消", "清除", () -> {
                    SecretStore.clearHomeAssistantToken(this);
                    HomeKeepAliveService.start(this);
                    refreshHomeAssistant();
                    showSettingsFeedback("Home Assistant 令牌已清除。", true);
                }));
        Button connect = button("测试连接", false);
        connect.setTextSize(15);
        connect.setOnClickListener(v -> {
            String enteredUrl = url.getText().toString().trim();
            String enteredToken = token.getText().toString().trim();
            boolean httpEnabled = allowHttp.isChecked();
            try {
                network.execute(() -> {
                    try {
                        String secret = enteredToken.isEmpty() ? SecretStore.homeAssistantToken(this) : enteredToken;
                        new HomeAssistantClient(enteredUrl, secret, httpEnabled).ping();
                        runOnUiThread(() -> showSettingsFeedback("Home Assistant 连接成功。", true));
                    } catch (Exception error) {
                        String message = safeMessage(error);
                        runOnUiThread(() -> showSettingsFeedback("连接失败：" + message, false));
                    }
                });
            } catch (Exception error) { showSettingsFeedback("无法开始连接测试。", false); }
        });
        Button save = button("保存设置", true);
        save.setOnClickListener(v -> {
            String base = url.getText().toString().trim();
            try {
                if (!base.isEmpty()) new HomeAssistantClient(base, "validation-only", allowHttp.isChecked()).validateForSettings();
                for (String entity : entities.getText().toString().split("[\\r\\n,]+"))
                    if (!entity.trim().isEmpty() && !entity.trim().matches("[a-zA-Z0-9_]+\\.[a-zA-Z0-9_]+"))
                        throw new IllegalArgumentException("实体 ID 格式应为 light.living_room");
                AppPrefs.setHomeAssistant(this, base, "", "", "", "", allowHttp.isChecked());
                AppPrefs.setHaHomeEntities(this, entities.getText().toString());
                if (!token.getText().toString().trim().isEmpty()) SecretStore.saveHomeAssistantToken(this, token.getText().toString());
                HomeKeepAliveService.start(this);
                refreshHomeAssistant();
                showSettingsFeedback("Home Assistant 设置已保存。", true);
            } catch (Exception error) { showSettingsFeedback("设置无效：" + safeMessage(error), false); }
        });
        saveActions.addView(clear, new LinearLayout.LayoutParams(0, dp(56), 1));
        LinearLayout.LayoutParams connectParams = new LinearLayout.LayoutParams(0, dp(56), 1);
        connectParams.leftMargin = dp(8);
        saveActions.addView(connect, connectParams);
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, dp(56), 1);
        saveParams.leftMargin = dp(8);
        saveActions.addView(save, saveParams);
        form.addView(saveActions, marginTop(9));
    }

    private void buildDeviceSettingsSection() {
        LinearLayout form = settingsContent;
        addSettingsLabel(form, "时钟样式");
        clockStyleSettingsChoice = button(ClockFaceView.styleName(AppPrefs.clockStyle(this)) + "  ⌄", false);
        clockStyleSettingsChoice.setOnClickListener(v -> showClockStylePicker());
        form.addView(clockStyleSettingsChoice, marginTop(5));
        addSettingsLabel(form, "屏幕与电源");
        CheckBox keepAwake = settingsCheck("主页保持屏幕唤醒", AppPrefs.keepScreenOn(this));
        keepAwake.setOnCheckedChangeListener((button, checked) -> {
            AppPrefs.setKeepScreenOn(this, checked);
            applyKeepScreenAwake(checked);
            showSettingsFeedback(checked ? "主页会保持屏幕唤醒。" : "已允许系统按电源策略关闭屏幕。", true);
        });
        form.addView(keepAwake);
        Button brightness = button(Settings.System.canWrite(this) ? "系统亮度控制：已授权" : "授权系统亮度控制", false);
        brightness.setOnClickListener(v -> {
            if (android.os.Build.VERSION.SDK_INT >= 23 && !Settings.System.canWrite(this)) {
                try { startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName()))); }
                catch (Exception error) { showSettingsFeedback("无法打开系统亮度授权页。", false); }
            } else showSettingsFeedback("系统亮度控制已授权。", true);
        });
        form.addView(brightness, marginTop(8));
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        boolean ignoring = android.os.Build.VERSION.SDK_INT < 23 || power.isIgnoringBatteryOptimizations(getPackageName());
        Button battery = button(ignoring ? "后台电池限制：已忽略" : "关闭本应用的电池优化", false);
        battery.setOnClickListener(v -> {
            if (android.os.Build.VERSION.SDK_INT >= 23 && !power.isIgnoringBatteryOptimizations(getPackageName())) {
                try { startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName()))); }
                catch (Exception error) {
                    try { startActivity(new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
                    catch (Exception ignoredError) { showSettingsFeedback("无法打开电池优化设置。", false); }
                }
            } else showSettingsFeedback("本应用已从电池优化中排除。", true);
        });
        form.addView(battery, marginTop(8));
        TextView info = compactText("常驻服务、开机恢复和本地闹钟用于降低被系统回收的风险。系统权限说明和授权由 Android 提供。", 14, HOME_MUTED, false);
        info.setLineSpacing(dp(4), 1f);
        form.addView(info, marginTop(12));
    }

    private void buildDiagnosticsSettingsSection() {
        LinearLayout form = settingsContent;
        String system = "Android " + android.os.Build.VERSION.RELEASE + "（API " + android.os.Build.VERSION.SDK_INT + ")"
                + "\n型号：" + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
                + "\n设备：" + android.os.Build.DEVICE
                + "\n画面方向：横屏；目标画布 1366 × 768"
                + "\n\n应用级 Root：点击下方检查"
                + "\n投影引擎和光源状态取决于当前 Xperia Touch 固件。";
        TextView report = compactText(system, 15, HOME_INK, false);
        report.setLineSpacing(dp(4), 1f);
        report.setPadding(dp(16), dp(14), dp(16), dp(14));
        report.setBackground(cardBackground(Color.rgb(247, 246, 241), 16));
        form.addView(report);
        Button requestRoot = button("请求 / 重新检测 Root 授权", true);
        requestRoot.setOnClickListener(v -> {
            requestRoot.setEnabled(false);
            requestRoot.setText("等待 Root 授权…");
            new Thread(() -> {
                String result;
                try { result = probeRoot(); }
                catch (Exception ignored) { result = "应用级 Root：检查失败"; }
                String finalResult = result;
                runOnUiThread(() -> {
                    report.setText(system.replace("应用级 Root：点击下方检查", finalResult));
                    requestRoot.setText(finalResult.contains("已授权") ? "Root 已授权 · 重新检测" : "重试 Root 授权");
                    requestRoot.setEnabled(true);
                });
            }, "xperia-root-request").start();
        });
        form.addView(requestRoot, marginTop(9));
        Button sleep = button("测试休眠并自动唤醒", false);
        sleep.setOnClickListener(v -> {
            if (!LocalDeviceControl.hasRoot()) { showSettingsFeedback("请先请求并授予应用 Root 权限。", false); return; }
            boolean keepAwake = AppPrefs.keepScreenOn(this);
            sleep.setEnabled(false);
            LocalDeviceControl.sleep(this);
            handler.postDelayed(() -> {
                LocalDeviceControl.wake(this);
                AppPrefs.setKeepScreenOn(this, keepAwake);
                MainActivity.setKeepScreenAwake(keepAwake);
                sleep.setEnabled(true);
                showSettingsFeedback("已发送唤醒命令；请确认屏幕与投影已恢复。", true);
            }, 2200L);
        });
        form.addView(sleep, marginTop(7));
        TextView backupNote = compactText("闹钟备份包含时间、标签、重复方式和补班日选项，不含 Home Assistant 密钥。", 14, HOME_MUTED, false);
        backupNote.setLineSpacing(dp(3), 1f);
        form.addView(backupNote, marginTop(18));
        LinearLayout backup = new LinearLayout(this);
        Button export = button("导出闹钟 JSON", false);
        export.setOnClickListener(v -> chooseExport());
        Button restore = button("导入闹钟 JSON", true);
        restore.setOnClickListener(v -> chooseImport());
        backup.addView(export, new LinearLayout.LayoutParams(0, dp(58), 1));
        LinearLayout.LayoutParams restoreParams = new LinearLayout.LayoutParams(0, dp(58), 1);
        restoreParams.leftMargin = dp(9);
        backup.addView(restore, restoreParams);
        form.addView(backup, marginTop(8));
    }

    private CheckBox settingsCheck(String label, boolean checked) {
        CheckBox box = new CheckBox(this);
        box.setTypeface(zhuqueTypeface());
        box.setText(label);
        box.setTextColor(HOME_INK);
        box.setTextSize(16);
        box.setMinHeight(dp(54));
        box.setButtonTintList(ColorStateList.valueOf(HOME_ACCENT));
        box.setChecked(checked);
        return box;
    }

    private void addSettingsLabel(LinearLayout parent, String title) {
        TextView label = compactText(title, 15, HOME_ACCENT, true);
        label.setPadding(0, dp(9), 0, dp(4));
        parent.addView(label);
    }

    private void showSettingsFeedback(String message, boolean success) {
        if (settingsFeedback == null) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            return;
        }
        settingsFeedback.setText(message);
        settingsFeedback.setTextColor(success ? HOME_ACCENT : Color.rgb(164, 72, 52));
    }

    private void showBrandedConfirm(String title, String message, String negative, String positive, Runnable action) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(26), dp(22), dp(26), dp(20));
        content.setBackground(cardBackground(SURFACE, 22));
        content.addView(compactText(title, 22, HOME_INK, true));
        TextView body = compactText(message, 16, HOME_MUTED, false);
        body.setLineSpacing(dp(4), 1f);
        content.addView(body, marginTop(10));
        LinearLayout buttons = new LinearLayout(this);
        Button cancel = button(negative, false);
        cancel.setOnClickListener(v -> dialog.dismiss());
        Button accept = button(positive, true);
        accept.setOnClickListener(v -> { dialog.dismiss(); action.run(); });
        buttons.addView(cancel, new LinearLayout.LayoutParams(0, dp(56), 1));
        LinearLayout.LayoutParams acceptParams = new LinearLayout.LayoutParams(0, dp(56), 1);
        acceptParams.leftMargin = dp(8);
        buttons.addView(accept, acceptParams);
        content.addView(buttons, marginTop(17));
        dialog.setContentView(content);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(Math.min(dp(560), getResources().getDisplayMetrics().widthPixels - dp(48)), -2);
        }
    }

    private void showBrandedDialog(android.app.Dialog dialog, int maxWidthDp, int maxHeightDp) {
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            int maxWidth = Math.min(dp(maxWidthDp), getResources().getDisplayMetrics().widthPixels - dp(48));
            int maxHeight = Math.min(dp(maxHeightDp), getResources().getDisplayMetrics().heightPixels - dp(48));
            dialog.getWindow().setLayout(maxWidth, maxHeight);
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private void showBrandedMessage(String title, String message) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(25), dp(22), dp(25), dp(20));
        root.setBackground(cardBackground(SURFACE, 22));
        root.addView(compactText(title, 22, HOME_INK, true));
        TextView body = compactText(message, 16, HOME_MUTED, false);
        body.setLineSpacing(dp(4), 1f);
        root.addView(body, marginTop(10));
        Button done = button("完成", true);
        done.setOnClickListener(v -> dialog.dismiss());
        root.addView(done, marginTop(16));
        dialog.setContentView(root);
        showBrandedDialog(dialog, 580, 620);
    }

    private void showAlarmSettings() {
        LinearLayout form = settingsForm();
        TextView note = text("闹钟在本机运行。响铃时回到主页，在底部滑动处理。", 14, MUTED, false);
        form.addView(note, marginTop(4));
        Button add = button("＋  新增闹钟", true);
        add.setOnClickListener(v -> editAlarm(null));
        form.addView(add, marginTop(10));
        ScrollView scroll = new ScrollView(this);
        alarmList = new LinearLayout(this);
        alarmList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(alarmList);
        form.addView(scroll, new LinearLayout.LayoutParams(-1, dp(390)));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("闹钟管理").setView(form)
                .setPositiveButton("完成", null).create();
        dialog.show();
        dialog.getWindow().setLayout(Math.min(dp(930), getResources().getDisplayMetrics().widthPixels - dp(40)),
                Math.min(dp(650), getResources().getDisplayMetrics().heightPixels - dp(32)));
        refreshAlarmList();
    }

    private void showDeviceSettings() {
        LinearLayout form = settingsForm();
        CheckBox keepAwake = new CheckBox(this);
        keepAwake.setTypeface(zhuqueTypeface());
        keepAwake.setText("主页保持屏幕唤醒");
        keepAwake.setTextColor(INK);
        keepAwake.setButtonTintList(ColorStateList.valueOf(ACCENT));
        keepAwake.setChecked(AppPrefs.keepScreenOn(this));
        keepAwake.setOnCheckedChangeListener((button, checked) -> {
            AppPrefs.setKeepScreenOn(this, checked);
            applyKeepScreenAwake(checked);
        });
        form.addView(keepAwake);
        Button brightness = button(Settings.System.canWrite(this) ? "系统亮度控制：已授权" : "授权系统亮度控制", false);
        brightness.setOnClickListener(v -> {
            if (android.os.Build.VERSION.SDK_INT >= 23 && !Settings.System.canWrite(this)) {
                try { startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName()))); }
                catch (Exception error) { Toast.makeText(this, "无法打开系统亮度授权页", Toast.LENGTH_LONG).show(); }
            } else Toast.makeText(this, "系统亮度控制已授权", Toast.LENGTH_SHORT).show();
        });
        form.addView(brightness, marginTop(8));
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        boolean ignoring = android.os.Build.VERSION.SDK_INT < 23 || power.isIgnoringBatteryOptimizations(getPackageName());
        Button battery = button(ignoring ? "后台电池限制：已忽略" : "关闭本应用的电池优化", false);
        battery.setOnClickListener(v -> {
            if (android.os.Build.VERSION.SDK_INT >= 23 && !power.isIgnoringBatteryOptimizations(getPackageName())) {
                try { startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName()))); }
                catch (Exception error) { startActivity(new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
            } else Toast.makeText(this, "本应用已从电池优化中排除", Toast.LENGTH_SHORT).show();
        });
        form.addView(battery, marginTop(8));
        TextView info = text("常驻前台服务、开机恢复和闹钟服务用于降低被回收风险。安卓及设备厂商仍可能在强制停止、极端省电或内存压力时终止应用；闹钟会在系统允许的范围内恢复。", 14, MUTED, false);
        info.setLineSpacing(dp(4), 1f);
        form.addView(info, marginTop(12));
        new AlertDialog.Builder(this).setTitle("屏幕与后台保活").setView(form)
                .setPositiveButton("完成", null).show();
    }

    private void showWeatherSettings() {
        LinearLayout form = settingsForm();
        EditText city = settingField("城市名称", AppPrefs.city(this), InputType.TYPE_CLASS_TEXT);
        EditText latitude = settingField("纬度（-90 到 90）", String.valueOf(AppPrefs.latitude(this)),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText longitude = settingField("经度（-180 到 180）", String.valueOf(AppPrefs.longitude(this)),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        Button searchPlace = button("搜索城市或地区", false);
        searchPlace.setTextSize(16);
        searchPlace.setOnClickListener(v -> promptWeatherPlace(city, latitude, longitude));
        form.addView(searchPlace);
        form.addView(city);
        form.addView(latitude);
        form.addView(longitude);
        AlertDialog weatherDialog = new AlertDialog.Builder(this).setTitle("天气地点").setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    try {
                        double lat = Double.parseDouble(latitude.getText().toString());
                        double lon = Double.parseDouble(longitude.getText().toString());
                        if (lat < -90 || lat > 90 || lon < -180 || lon > 180 || city.getText().toString().trim().isEmpty())
                            throw new IllegalArgumentException();
                        AppPrefs.setWeather(this, city.getText().toString(), lat, lon);
                        weatherTitle.setText(AppPrefs.city(this));
                        refreshWeather();
                    } catch (Exception error) {
                        Toast.makeText(this, "城市或经纬度无效", Toast.LENGTH_LONG).show();
                    }
                }).create();
        weatherDialog.setOnDismissListener(dialog -> configureImmersiveMode());
        weatherDialog.show();
    }

    private void promptWeatherPlace(EditText city, EditText latitude, EditText longitude) {
        EditText query = settingField("输入城市或地区名称", city.getText().toString(), InputType.TYPE_CLASS_TEXT);
        android.app.Dialog dialog = new android.app.Dialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(20), dp(24), dp(18));
        root.setBackground(cardBackground(SURFACE, 22));
        root.addView(compactText("搜索天气地点", 22, HOME_INK, true));
        TextView hint = compactText("输入城市或地区，选择匹配地点后会自动填写坐标。", 14, HOME_MUTED, false);
        root.addView(hint, marginTop(7));
        root.addView(query, marginTop(8));
        TextView status = compactText("", 14, HOME_ACCENT, false);
        root.addView(status, marginTop(5));
        LinearLayout actions = new LinearLayout(this);
        Button cancel = button("取消", false);
        cancel.setOnClickListener(v -> dialog.dismiss());
        Button searchButton = button("搜索", true);
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(54), 1));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(0, dp(54), 1);
        searchParams.leftMargin = dp(8);
        actions.addView(searchButton, searchParams);
        root.addView(actions, marginTop(10));
        dialog.setContentView(root);
        searchButton.setOnClickListener(v -> {
            String search = query.getText().toString().trim();
            if (search.isEmpty()) {
                status.setText("先输入城市或地区名称。");
                status.setTextColor(Color.rgb(164, 72, 52));
                return;
            }
            searchButton.setEnabled(false);
            status.setText("正在搜索地点…");
            network.execute(() -> {
                try {
                    List<WeatherClient.Place> places = WeatherClient.searchPlaces(search);
                    runOnUiThread(() -> {
                        if (places.isEmpty()) {
                            status.setText("没有找到地点。请换个关键词或手动填写经纬度。");
                            status.setTextColor(Color.rgb(164, 72, 52));
                            searchButton.setEnabled(true);
                            return;
                        }
                        showWeatherPlaceChoices(places, city, latitude, longitude);
                        dialog.dismiss();
                    });
                } catch (Exception error) {
                    String reason = safeMessage(error);
                    runOnUiThread(() -> {
                        status.setText("地点搜索失败：" + reason);
                        status.setTextColor(Color.rgb(164, 72, 52));
                        searchButton.setEnabled(true);
                    });
                }
            });
        });
        showBrandedDialog(dialog, 700, 500);
    }

    private void showWeatherPlaceChoices(List<WeatherClient.Place> places, EditText city,
                                         EditText latitude, EditText longitude) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(18), dp(22), dp(16));
        root.setBackground(cardBackground(SURFACE, 22));
        root.addView(compactText("选择天气地点", 21, HOME_INK, true));
        ScrollView scroll = new ScrollView(this);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        for (WeatherClient.Place place : places) {
            TextView row = compactText(place.label(), 16, HOME_INK, false);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), 0, dp(14), 0);
            row.setBackground(cardBackground(Color.rgb(247, 246, 241), 13));
            row.setMinHeight(dp(56));
            row.setOnClickListener(v -> {
                city.setText(place.name);
                latitude.setText(String.valueOf(place.latitude));
                longitude.setText(String.valueOf(place.longitude));
                dialog.dismiss();
            });
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, dp(56));
            rowParams.topMargin = dp(6);
            rows.addView(row, rowParams);
        }
        scroll.addView(rows);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        Button close = button("返回", false);
        close.setOnClickListener(v -> dialog.dismiss());
        root.addView(close, marginTop(9));
        dialog.setContentView(root);
        showBrandedDialog(dialog, 700, 680);
    }

    private void showRssSettings() {
        LinearLayout form = settingsForm();
        EditText address = new EditText(this);
        address.setTypeface(zhuqueTypeface());
        address.setHint("一行一个 RSS / Atom 地址；留空可关闭新闻");
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_VARIATION_URI);
        address.setGravity(Gravity.TOP | Gravity.LEFT);
        address.setMinLines(4);
        address.setText(AppPrefs.rssUrl(this));
        TextView recommendations = text("已预置中文科技源：少数派（数字生活与效率）、爱范儿（消费科技与产品）、IT之家（快讯）。可以删改或继续添加。", 13, MUTED, false);
        recommendations.setLineSpacing(dp(3), 1f);
        form.addView(recommendations, marginTop(4));
        form.addView(address);
        new AlertDialog.Builder(this).setTitle("科技新闻源").setMessage("首页按来源轮流显示标题；点选后打开原文。")
                .setView(form).setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    String value = address.getText().toString().trim();
                    for (String line : value.split("[\\r\\n,]+")) {
                        String url = line.trim();
                        if (!url.isEmpty() && !(url.startsWith("https://") || url.startsWith("http://"))) {
                            Toast.makeText(this, "每个新闻源都必须以 http:// 或 https:// 开头", Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                    AppPrefs.setRssUrl(this, value);
                    refreshRss();
                }).show();
    }

    private void showHomeAssistantSettings() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout form = settingsForm();
        EditText url = settingField("Home Assistant 地址，例如 http://homeassistant.local:8123", AppPrefs.haUrl(this),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        EditText token = settingField("长期访问令牌（留空保留已保存令牌）", "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText entities = new EditText(this);
        entities.setTypeface(zhuqueTypeface());
        entities.setHint("要显示在主页的 HA 实体 ID，每行一个，例如 light.living_room");
        entities.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        entities.setGravity(Gravity.TOP | Gravity.LEFT);
        entities.setMinLines(3);
        entities.setText(AppPrefs.haHomeEntities(this));
        Button chooseEntities = button("从 HA 选择主页实体", false);
        chooseEntities.setTextSize(15);
        Button reportDevice = button("测试本机状态上报", false);
        reportDevice.setTextSize(15);
        TextView pairingId = text("Companion Link 配对 ID（在 HA 添加集成时输入）：\n"
                + AppPrefs.companionLinkDeviceId(this), 13, MUTED, false);
        pairingId.setTextIsSelectable(true);
        TextView deviceInfo = text("连接后，本机电量、充电、网络、屏幕状态与能力会自动上报。安装 Companion Link Home Assistant 集成后，会出现对应设备与控制实体。", 13, MUTED, false);
        deviceInfo.setLineSpacing(dp(3), 1f);
        form.addView(url);
        form.addView(token);
        form.addView(pairingId, marginTop(8));
        form.addView(text("主页显示的 HA 设备", 14, ACCENT, true), marginTop(10));
        form.addView(entities);
        form.addView(chooseEntities, marginTop(4));
        form.addView(reportDevice, marginTop(6));
        form.addView(deviceInfo, marginTop(10));
        CheckBox allowHttp = new CheckBox(this);
        allowHttp.setTypeface(zhuqueTypeface());
        allowHttp.setText("允许局域网 HTTP（只允许本地地址；建议使用 HTTPS）");
        allowHttp.setTextSize(13);
        allowHttp.setButtonTintList(ColorStateList.valueOf(ACCENT));
        allowHttp.setChecked(AppPrefs.haAllowHttp(this));
        form.addView(allowHttp);
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Home Assistant 与本机设备")
                .setMessage("Home Assistant 地址和令牌由你配置。令牌使用 Android Keystore 加密保存，不进入闹钟备份。")
                .setView(scroll).setNegativeButton("清除令牌", null)
                .setNeutralButton("连接测试", null).setPositiveButton("保存", null).create();
        dialog.setOnShowListener(ignored -> {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            form.setFocusableInTouchMode(true);
            form.requestFocus();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String base = url.getText().toString().trim();
                try {
                    if (!base.isEmpty()) new HomeAssistantClient(base, "validation-only", allowHttp.isChecked()).validateForSettings();
                        for (String entity : entities.getText().toString().split("[\\r\\n,]+")) {
                            if (!entity.trim().isEmpty() && !entity.trim().matches("[a-zA-Z0-9_]+\\.[a-zA-Z0-9_]+"))
                                throw new IllegalArgumentException("实体 ID 格式应为 light.living_room");
                        }
                        AppPrefs.setHomeAssistant(this, base, "", "", "", "", allowHttp.isChecked());
                        AppPrefs.setHaHomeEntities(this, entities.getText().toString());
                        if (!token.getText().toString().trim().isEmpty()) SecretStore.saveHomeAssistantToken(this, token.getText().toString());
                    dialog.dismiss();
                        HomeKeepAliveService.start(this);
                    refreshHomeAssistant();
                } catch (Exception error) {
                    Toast.makeText(this, "设置无效：" + safeMessage(error), Toast.LENGTH_LONG).show();
                }
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                String enteredUrl = url.getText().toString();
                String enteredToken = token.getText().toString().trim();
                boolean httpEnabled = allowHttp.isChecked();
                network.execute(() -> {
                    try {
                        String secret = enteredToken.isEmpty() ? SecretStore.homeAssistantToken(this) : enteredToken;
                        new HomeAssistantClient(enteredUrl, secret, httpEnabled).ping();
                        runOnUiThread(() -> Toast.makeText(this, "Home Assistant 连接成功", Toast.LENGTH_LONG).show());
                    } catch (Exception error) {
                        String message = safeMessage(error);
                        runOnUiThread(() -> Toast.makeText(this, "连接失败：" + message, Toast.LENGTH_LONG).show());
                    }
                });
            });
            chooseEntities.setOnClickListener(v -> loadHomeAssistantEntities(url.getText().toString().trim(),
                    token.getText().toString().trim(), allowHttp.isChecked(), entities));
            reportDevice.setOnClickListener(v -> testLocalStatusReport(url.getText().toString().trim(),
                    token.getText().toString().trim(), allowHttp.isChecked()));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v ->
                    new AlertDialog.Builder(this).setTitle("清除 Home Assistant 令牌？")
                            .setMessage("清除后需要重新输入长期访问令牌才能连接。")
                            .setNegativeButton("取消", null)
                            .setPositiveButton("清除", (d, which) -> {
                                SecretStore.clearHomeAssistantToken(this);
                                HomeKeepAliveService.start(this);
                                refreshHomeAssistant();
                                dialog.dismiss();
                            }).show());
        });
        dialog.setOnDismissListener(ignored -> configureImmersiveMode());
        dialog.show();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    private void loadHomeAssistantEntities(String base, String enteredToken, boolean allowHttp, EditText target) {
        if (base.isEmpty() || (enteredToken.isEmpty() && !SecretStore.hasHomeAssistantToken(this))) {
            showSettingsFeedback("先填写 Home Assistant 地址和长期访问令牌。", false);
            return;
        }
        showSettingsFeedback("正在读取 Home Assistant 实体…", true);
        final int request = ++haEntityRequestGeneration;
        Runnable timeout = () -> {
            if (request != haEntityRequestGeneration) return;
            haEntityRequestGeneration++;
            showSettingsFeedback("读取超时。请检查地址、令牌和局域网连接后重试。", false);
        };
        handler.postDelayed(timeout, 22000L);
        try {
            haEntityLookup.execute(() -> {
            try {
                String secret = enteredToken.isEmpty() ? SecretStore.homeAssistantToken(this) : enteredToken;
                JSONArray states = new HomeAssistantClient(base, secret, allowHttp).states();
                ArrayList<JSONObject> items = new ArrayList<>();
                for (int i = 0; i < states.length(); i++) {
                    JSONObject state = states.optJSONObject(i);
                    if (state != null && !state.optString("entity_id", "").isEmpty()) items.add(state);
                }
                runOnUiThread(() -> {
                    if (request != haEntityRequestGeneration) return;
                    handler.removeCallbacks(timeout);
                    if (isFinishing() || settingsOverlay == null) {
                        showSettingsFeedback("已读取 " + items.size() + " 个 HA 实体；重新打开设置后可再次选择。", true);
                        return;
                    }
                    showSettingsFeedback("已读取 " + items.size() + " 个 Home Assistant 实体。", true);
                    showHomeAssistantEntityPicker(items, target);
                });
            } catch (Exception error) {
                String message = safeMessage(error);
                runOnUiThread(() -> {
                    if (request != haEntityRequestGeneration) return;
                    handler.removeCallbacks(timeout);
                    showSettingsFeedback("读取实体失败：" + message, false);
                });
            }
            });
        } catch (RuntimeException error) {
            handler.removeCallbacks(timeout);
            haEntityRequestGeneration++;
            showSettingsFeedback("无法开始读取实体，请重试。", false);
        }
    }

    private void showHomeAssistantEntityPicker(List<JSONObject> states, EditText target) {
        EditText search = new EditText(this);
        search.setTypeface(zhuqueTypeface());
        search.setSingleLine(true);
        search.setHint("搜索名称、实体 ID、状态或领域");
        search.setPadding(dp(12), 0, dp(12), 0);
        ListView list = new ListView(this);
        list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        Set<String> selected = new LinkedHashSet<>();
        for (String id : target.getText().toString().split("[\\r\\n,]+")) {
            if (!id.trim().isEmpty()) selected.add(id.trim());
        }
        android.app.Dialog dialog = new android.app.Dialog(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(19), dp(22), dp(16));
        content.setBackground(cardBackground(SURFACE, 22));
        content.addView(compactText("选择主页实体（" + states.size() + " 个）", 21, HOME_INK, true));
        content.addView(search, new LinearLayout.LayoutParams(-1, dp(54)));
        content.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout actions = new LinearLayout(this);
        Button cancel = button("取消", false);
        cancel.setOnClickListener(v -> dialog.dismiss());
        Button apply = button("使用所选实体", true);
        apply.setOnClickListener(v -> {
            StringBuilder value = new StringBuilder();
            for (String id : selected) {
                if (value.length() > 0) value.append('\n');
                value.append(id);
            }
            target.setText(value.toString());
            dialog.dismiss();
        });
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(54), 1));
        LinearLayout.LayoutParams applyParams = new LinearLayout.LayoutParams(0, dp(54), 1);
        applyParams.leftMargin = dp(8);
        actions.addView(apply, applyParams);
        content.addView(actions, marginTop(8));

        ArrayList<JSONObject> visible = new ArrayList<>();
        Runnable filter = () -> {
            visible.clear();
            String query = search.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
            ArrayList<String> labels = new ArrayList<>();
            for (JSONObject state : states) {
                String id = state.optString("entity_id", "");
                JSONObject attributes = state.optJSONObject("attributes");
                String name = attributes == null ? id : attributes.optString("friendly_name", id);
                String value = state.optString("state", "");
                String domain = id.contains(".") ? id.substring(0, id.indexOf('.')) : "";
                String searchText = (name + " " + id + " " + value + " " + domain).toLowerCase(java.util.Locale.ROOT);
                if (query.isEmpty() || searchText.contains(query)) {
                    visible.add(state);
                    labels.add(name + " · " + value + "\n" + id);
                }
            }
            list.clearChoices();
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_multiple_choice, labels) {
                @Override public View getView(int position, View convertView, ViewGroup parent) {
                    View row = super.getView(position, convertView, parent);
                    TextView label = row.findViewById(android.R.id.text1);
                    label.setTypeface(zhuqueTypeface());
                    label.setTextSize(14);
                    label.setTextColor(HOME_INK);
                    row.setPadding(dp(8), dp(4), dp(8), dp(4));
                    return row;
                }
            };
            list.setAdapter(adapter);
            for (int i = 0; i < visible.size(); i++)
                if (selected.contains(visible.get(i).optString("entity_id"))) list.setItemChecked(i, true);
        };
        filter.run();
        list.setOnItemClickListener((parent, view, position, itemId) -> {
            String entityId = visible.get(position).optString("entity_id", "");
            if (list.isItemChecked(position)) selected.add(entityId);
            else selected.remove(entityId);
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filter.run(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        dialog.setContentView(content);
        showBrandedDialog(dialog, 900, 660);
    }

    private void testLocalStatusReport(String base, String enteredToken, boolean allowHttp) {
        if (base.isEmpty() || (enteredToken.isEmpty() && !SecretStore.hasHomeAssistantToken(this))) {
            showSettingsFeedback("先填写 Home Assistant 地址和长期访问令牌。", false);
            return;
        }
        showSettingsFeedback("正在上报本机状态…", true);
        network.execute(() -> {
            try {
                String secret = enteredToken.isEmpty() ? SecretStore.homeAssistantToken(this) : enteredToken;
                HomeAssistantClient client = new HomeAssistantClient(base, secret, allowHttp);
                client.ping();
                JSONObject status = LocalDeviceStatus.capture(this);
                client.fireEvent(CompanionLinkProtocol.EVENT_UPDATE, status);
                JSONArray entities = client.states();
                int integrationEntities = 0;
                for (int i = 0; i < entities.length(); i++) {
                    String id = entities.optJSONObject(i) == null ? "" : entities.optJSONObject(i).optString("entity_id", "");
                    if (id.toLowerCase(java.util.Locale.ROOT).contains("companion_link")) integrationEntities++;
                }
                String battery = status.optInt("battery_percent", -1) < 0 ? "未知"
                        : status.optInt("battery_percent") + "%";
                String networkState = status.optString("network_type", "未知");
                String screenState = status.optBoolean("screen_awake", false) ? "亮屏" : "休眠";
                String message = "状态事件已送达 Home Assistant。\n电量 " + battery + " · 网络 " + networkState
                        + " · 屏幕 " + screenState + " · Root " + (status.optBoolean("root_available") ? "可用" : "不可用")
                        + (integrationEntities == 0
                        ? "\n\n当前服务器没有 Companion Link 集成实体。请先在 HA 安装并添加集成，再检查传感器和控制项。"
                        : "\n\n已发现 " + integrationEntities + " 个 Companion Link 集成实体。");
                runOnUiThread(() -> showBrandedMessage("本机状态上报结果", message));
            } catch (Exception error) {
                String result = safeMessage(error);
                runOnUiThread(() -> showSettingsFeedback("状态上报失败：" + result, false));
            }
        });
    }

    private LinearLayout settingsForm() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), dp(8));
        return form;
    }

    private EditText settingField(String hint, String value, int inputType) {
        EditText field = new EditText(this);
        field.setTypeface(zhuqueTypeface());
        field.setSingleLine(true);
        field.setHint(hint);
        field.setInputType(inputType);
        if (value != null && !value.isEmpty()) field.setText(value);
        return field;
    }

    private void refreshWeather() {
        if (weatherView == null || network.isShutdown()) return;
        weatherTitle.setText(AppPrefs.city(this));
        String cached = AppPrefs.weatherCache(this);
        if (!cached.isEmpty()) {
            setWeatherDisplay(cached);
            weatherGlyph.setKind(weatherCodeFromText(normalizeWeatherDisplay(cached)));
        }
        String city = AppPrefs.city(this);
        double latitude = AppPrefs.latitude(this);
        double longitude = AppPrefs.longitude(this);
        network.execute(() -> {
            try {
                WeatherClient.Weather weather = WeatherClient.fetch(latitude, longitude);
                String result = weather.display(city);
                AppPrefs.cacheWeather(this, result, System.currentTimeMillis());
                runOnUiThread(() -> {
                    if (weatherView == null) return;
                    setCurrentWeather(weather.temperature, weather.description, weather.apparent + "°",
                            weather.humidity + "%", weather.rainChance + "%", weather.low, weather.high);
                    weatherGlyph.setKind(weather.weatherCode);
                    renderWeatherForecast(weather);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (weatherView == null) return;
                    if (cached.isEmpty()) {
                        showWeatherUnavailable("天气暂不可用");
                    }
                    if (!weatherForecastLoaded) renderWeatherForecast(null);
                });
            }
        });
    }

    private String normalizeWeatherDisplay(String value) {
        String result = value == null ? "" : value;
        String prefix = AppPrefs.city(this) + " · ";
        if (result.startsWith(prefix)) result = result.substring(prefix.length());
        return result;
    }

    private void setWeatherDisplay(String value) {
        if (weatherView == null) return;
        String display = normalizeWeatherDisplay(value);
        String[] lines = display.split("\n", 3);
        int degree = lines[0].indexOf('°');
        if (degree < 0) {
            showWeatherUnavailable("天气正在更新…");
            return;
        }
        String low = "—";
        String high = "—";
        if (lines.length > 2) {
            String[] range = lines[2].replaceFirst("^今日\\s*", "").split("/", 2);
            if (range.length == 2) {
                low = range[0].replace("°", "").trim();
                high = range[1].replace("°", "").trim();
            }
        }
        setCurrentWeather(lines[0].substring(0, degree), lines[0].substring(degree + 1).trim(),
                weatherValueFromCache(display, "体感"), weatherValueFromCache(display, "湿度"),
                weatherValueFromCache(display, "降雨"), low, high);
    }

    private String weatherValueFromCache(String display, String label) {
        java.util.regex.Matcher match = java.util.regex.Pattern.compile(label + "\\s+([^\\s]+)").matcher(display);
        return match.find() ? match.group(1) : "—";
    }

    private TextView addWeatherMeasure(LinearLayout measures, String label) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        TextView title = compactText(label, 16, HOME_MUTED, false);
        title.setSingleLine(true);
        column.addView(title);
        TextView value = compactText("—", 22, HOME_INK, false);
        value.setSingleLine(true);
        column.addView(value, marginTop(6));
        measures.addView(column, new LinearLayout.LayoutParams(0, -2, 1));
        return value;
    }

    private void setCurrentWeather(String temperature, String condition, String apparent,
                                   String humidity, String rainChance, String low, String high) {
        SpannableString styled = new SpannableString(temperature + "°");
        styled.setSpan(new RelativeSizeSpan(0.65f), styled.length() - 1, styled.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        weatherView.setText(styled);
        weatherCondition.setText(condition);
        weatherApparent.setText(apparent);
        weatherHumidity.setText(humidity);
        weatherRainChance.setText(rainChance);
        String range = roundedWeatherTemperature(low) + "–" + roundedWeatherTemperature(high) + "°";
        weatherRange.setText("—–—°".equals(range) ? "—" : range);
        weatherRange.setContentDescription("今日最低 " + low + " 度，最高 " + high + " 度");
        weatherGlyph.setVisibility(View.VISIBLE);
    }

    private String roundedWeatherTemperature(String value) {
        try { return String.valueOf(Math.round(Double.parseDouble(value))); }
        catch (NumberFormatException ignored) { return "—"; }
    }

    private void showWeatherUnavailable(String message) {
        setCurrentWeather("—", message, "—", "—", "—", "—", "—");
        weatherGlyph.setVisibility(View.INVISIBLE);
    }

    private int weatherCodeFromText(String display) {
        String firstLine = display == null ? "" : display.split("\\n", 2)[0];
        if (firstLine.contains("雷")) return 95;
        if (firstLine.contains("雪")) return 71;
        if (firstLine.contains("雨")) return 61;
        if (firstLine.contains("雾")) return 45;
        if (firstLine.contains("阴")) return 3;
        if (firstLine.contains("云")) return 2;
        return 0;
    }

    private void renderWeatherForecast(WeatherClient.Weather forecast) {
        if (weatherForecast == null) return;
        weatherForecast.removeAllViews();
        weatherForecastLoaded = forecast != null && !forecast.hours.isEmpty();
        TextView hoursTitle = compactText("未来时段", 16, HOME_MUTED, false);
        weatherForecast.addView(hoursTitle);
        if (forecast == null || forecast.hours.isEmpty()) {
            weatherForecast.addView(compactText("预报暂不可用", 14, HOME_MUTED, false), marginTop(10));
            return;
        }
        LinearLayout hourRow = new LinearLayout(this);
        hourRow.setGravity(Gravity.TOP);
        for (WeatherClient.ForecastHour hour : forecast.hours) {
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER_HORIZONTAL);
            tile.setPadding(dp(2), 0, dp(2), 0);
            TextView time = compactText(hour.time, 16, HOME_MUTED, false);
            time.setGravity(Gravity.CENTER);
            tile.addView(time, new LinearLayout.LayoutParams(-1, -2));
            WeatherIconView hourGlyph = new WeatherIconView(this, hour.weatherCode, HOME_ACCENT);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(40), dp(40));
            iconParams.topMargin = dp(12);
            tile.addView(hourGlyph, iconParams);
            TextView temperature = compactText(hour.temperature + "°", 30, HOME_INK, false);
            temperature.setGravity(Gravity.CENTER);
            tile.addView(temperature, marginTop(10));
            TextView rain = compactText("雨 " + hour.rainChance + "%", 15, HOME_MUTED, false);
            rain.setGravity(Gravity.CENTER);
            tile.addView(rain, marginTop(8));
            LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(dp(120), -2);
            hourRow.addView(tile, tileParams);
        }
        HorizontalScrollView hourScroll = new HorizontalScrollView(this);
        hourScroll.setHorizontalScrollBarEnabled(false);
        hourScroll.setVerticalScrollBarEnabled(false);
        hourScroll.setHorizontalFadingEdgeEnabled(false);
        hourScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hourScroll.addView(hourRow, new HorizontalScrollView.LayoutParams(-2, -2));
        hourScroll.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int tileWidth = Math.max(dp(92), (right - left) / 4);
            for (int i = 0; i < hourRow.getChildCount(); i++) {
                View tile = hourRow.getChildAt(i);
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) tile.getLayoutParams();
                if (params.width != tileWidth) {
                    params.width = tileWidth;
                    tile.setLayoutParams(params);
                }
            }
        });
        LinearLayout.LayoutParams hoursParams = new LinearLayout.LayoutParams(-1, -2);
        hoursParams.topMargin = dp(14);
        weatherForecast.addView(hourScroll, hoursParams);
        LinearLayout dailyHeader = new LinearLayout(this);
        dailyHeader.setGravity(Gravity.CENTER_VERTICAL);
        dailyHeader.addView(compactText("未来几天", 16, HOME_MUTED, false), new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams daysTitleParams = new LinearLayout.LayoutParams(-1, -2);
        daysTitleParams.topMargin = dp(32);
        daysTitleParams.bottomMargin = dp(6);
        weatherForecast.addView(dailyHeader, daysTitleParams);
        int days = Math.min(WeatherClient.FORECAST_DAYS, forecast.days.size());
        for (int i = 0; i < days; i++) {
            WeatherClient.ForecastDay day = forecast.days.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setContentDescription(day.date + "，" + day.description + "，最低 " + day.low
                    + " 度，最高 " + day.high + " 度，查看详细预报");
            row.setFocusable(true);
            row.setOnClickListener(v -> showWeatherDayDetails(day));
            TextView date = compactText(day.date, 16, HOME_INK, false);
            row.addView(date, new LinearLayout.LayoutParams(dp(52), -2));
            WeatherIconView dayGlyph = new WeatherIconView(this, day.weatherCode, HOME_ACCENT);
            LinearLayout.LayoutParams dayGlyphParams = new LinearLayout.LayoutParams(dp(28), dp(28));
            dayGlyphParams.rightMargin = dp(7);
            row.addView(dayGlyph, dayGlyphParams);
            TextView condition = compactText(day.description, 16, HOME_MUTED, false);
            condition.setMaxLines(1);
            condition.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(condition, new LinearLayout.LayoutParams(0, -2, 1));
            TextView low = compactText(day.low + "°", 18, HOME_MUTED, false);
            low.setSingleLine(true);
            low.setGravity(Gravity.RIGHT);
            row.addView(low, new LinearLayout.LayoutParams(dp(70), -2));
            TextView high = compactText(day.high + "°", 18, HOME_INK, true);
            high.setSingleLine(true);
            high.setGravity(Gravity.RIGHT);
            row.addView(high, new LinearLayout.LayoutParams(dp(70), -2));
            weatherForecast.addView(row, new LinearLayout.LayoutParams(-1, dp(60)));
            if (i < days - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(Color.argb(26, 65, 81, 82));
                weatherForecast.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
            }
        }
    }

    private void showWeatherDayDetails(WeatherClient.ForecastDay day) {
        if (homePage == null || alarmRinging) return;
        dismissWeatherDayDetails();
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(38), dp(20), dp(38), dp(18));
        page.setBackground(backgroundGradient(CANVAS, Color.rgb(237, 235, 228)));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = compactText("‹  返回", 20, HOME_ACCENT, false);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("返回首页");
        back.setFocusable(true);
        back.setBackground(cardBackground(Color.argb(30, 39, 119, 121), 24));
        back.setOnClickListener(v -> dismissWeatherDayDetails());
        header.addView(back, new LinearLayout.LayoutParams(dp(112), dp(48)));
        TextView title = compactText(AppPrefs.city(this) + " · 天气", 26, HOME_INK, false);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1);
        titleParams.leftMargin = dp(24);
        header.addView(title, titleParams);
        header.addView(compactText("每日预报", 16, HOME_MUTED, false));
        page.addView(header);

        LinearLayout content = new LinearLayout(this);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(-1, 0, 1);
        contentParams.topMargin = dp(24);
        contentParams.bottomMargin = dp(20);
        page.addView(content, contentParams);

        LinearLayout summary = weatherDetailsCard();
        summary.setGravity(Gravity.CENTER);
        TextView dayTitle = compactText(day.date, 26, HOME_ACCENT, true);
        dayTitle.setGravity(Gravity.CENTER);
        summary.addView(dayTitle);
        WeatherIconView icon = new WeatherIconView(this, day.weatherCode, HOME_ACCENT);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(132), dp(132));
        iconParams.topMargin = dp(40);
        iconParams.bottomMargin = dp(24);
        summary.addView(icon, iconParams);
        TextView condition = compactText(day.description, 32, HOME_INK, false);
        condition.setGravity(Gravity.CENTER);
        summary.addView(condition);

        LinearLayout temperatures = new LinearLayout(this);
        temperatures.addView(weatherDetailsTemperature("最低温度", day.low, HOME_MUTED),
                new LinearLayout.LayoutParams(0, -2, 1));
        temperatures.addView(weatherDetailsTemperature("最高温度", day.high, HOME_INK),
                new LinearLayout.LayoutParams(0, -2, 1));
        summary.addView(temperatures, marginTop(44));
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(0, -1, 1);
        summaryParams.rightMargin = dp(28);
        content.addView(summary, summaryParams);

        LinearLayout measures = new LinearLayout(this);
        measures.setOrientation(LinearLayout.VERTICAL);
        content.addView(measures, new LinearLayout.LayoutParams(0, -1, 1.55f));
        LinearLayout topMeasures = new LinearLayout(this);
        addWeatherDetailsMeasure(topMeasures, "降雨概率", weatherDetailValue(day.rainChance, "%"), 64, true);
        addWeatherDetailsMeasure(topMeasures, "紫外线指数", day.uv, 64, false);
        LinearLayout.LayoutParams topParams = new LinearLayout.LayoutParams(-1, 0, 1);
        topParams.bottomMargin = dp(28);
        measures.addView(topMeasures, topParams);
        LinearLayout bottomMeasures = new LinearLayout(this);
        addWeatherDetailsMeasure(bottomMeasures, "日出", day.sunrise.isEmpty() ? "—" : day.sunrise, 56, true);
        addWeatherDetailsMeasure(bottomMeasures, "日落", day.sunset.isEmpty() ? "—" : day.sunset, 56, false);
        measures.addView(bottomMeasures, new LinearLayout.LayoutParams(-1, 0, 1));
        page.addView(compactText("天气数据 · Open-Meteo", 12, HOME_MUTED, false));

        weatherDetailsPage = page;
        homeContent.setVisibility(View.INVISIBLE);
        homePage.addView(page, new FrameLayout.LayoutParams(-1, -1));
        back.requestFocus();
        configureImmersiveMode();
    }

    private LinearLayout weatherDetailsCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(28), dp(28), dp(28), dp(28));
        card.setBackground(cardBackground(SURFACE, 26));
        return card;
    }

    private LinearLayout weatherDetailsTemperature(String label, String value, int color) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        TextView temperature = compactText(weatherDetailValue(roundedWeatherTemperature(value), "°"), 62, color, false);
        temperature.setGravity(Gravity.CENTER);
        column.addView(temperature);
        TextView title = compactText(label, 18, HOME_MUTED, false);
        title.setGravity(Gravity.CENTER);
        column.addView(title, marginTop(12));
        return column;
    }

    private void addWeatherDetailsMeasure(LinearLayout row, String label, String value, int size, boolean first) {
        LinearLayout card = weatherDetailsCard();
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(compactText(label, 20, HOME_MUTED, false));
        TextView measure = compactText(value, size, HOME_INK, false);
        measure.setSingleLine(true);
        card.addView(measure, marginTop(24));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1);
        if (first) params.rightMargin = dp(28);
        row.addView(card, params);
    }

    private String weatherDetailValue(String value, String unit) {
        return value.isEmpty() || "—".equals(value) ? "—" : value + unit;
    }

    private void dismissWeatherDayDetails() {
        if (weatherDetailsPage == null) return;
        if (homePage != null) homePage.removeView(weatherDetailsPage);
        weatherDetailsPage = null;
        if (homeContent != null) homeContent.setVisibility(View.VISIBLE);
        configureImmersiveMode();
    }

    private void showBriefingDetails(RssFeedClient.Article article) {
        if (homePage == null || homeContent == null || alarmRinging) return;
        dismissWeatherDayDetails();
        dismissBriefingDetails();

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(38), dp(20), dp(38), dp(18));
        page.setBackground(backgroundGradient(CANVAS, Color.rgb(237, 235, 228)));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = compactText("‹  返回", 20, HOME_ACCENT, false);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("返回首页");
        back.setFocusable(true);
        back.setBackground(cardBackground(Color.argb(30, 39, 119, 121), 24));
        back.setOnClickListener(v -> dismissBriefingDetails());
        header.addView(back, new LinearLayout.LayoutParams(dp(112), dp(48)));
        TextView pageTitle = compactText("简报详情", 25, HOME_INK, false);
        LinearLayout.LayoutParams pageTitleParams = new LinearLayout.LayoutParams(0, -2, 1);
        pageTitleParams.leftMargin = dp(24);
        header.addView(pageTitle, pageTitleParams);
        TextView sourceBadge = compactText(article.source, 15, HOME_ACCENT, true);
        sourceBadge.setGravity(Gravity.CENTER);
        sourceBadge.setPadding(dp(16), dp(9), dp(16), dp(9));
        sourceBadge.setBackground(cardBackground(Color.argb(30, 39, 119, 121), 20));
        header.addView(sourceBadge);
        page.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1);
        scrollParams.topMargin = dp(22);
        scrollParams.bottomMargin = dp(16);
        page.addView(scroll, scrollParams);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(34), dp(30), dp(34), dp(32));
        card.setBackground(cardBackground(SURFACE, 26));
        scroll.addView(card, new ScrollView.LayoutParams(-1, -2));

        FrameLayout imageFrame = new FrameLayout(this);
        imageFrame.setBackground(cardBackground(Color.rgb(229, 232, 226), 13));
        imageFrame.setClipToOutline(true);
        ImageView coverImage = articleImage(article.imageUrl, "简报配图：" + article.title);
        imageFrame.addView(coverImage, new FrameLayout.LayoutParams(-1, -1));
        imageFrame.setFocusable(true);
        imageFrame.setOnClickListener(v -> showBriefingImageViewer(article));
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(-1, dp(300));
        imageParams.topMargin = dp(18);
        card.addView(imageFrame, imageParams);
        TextView headline = compactText(article.title, 34, HOME_INK, true);
        headline.setLineSpacing(dp(5), 1f);
        LinearLayout.LayoutParams headlineParams = new LinearLayout.LayoutParams(-1, -2);
        headlineParams.topMargin = dp(14);
        card.addView(headline, headlineParams);

        String published = article.publishedAt > 0
                ? android.text.format.DateFormat.format("M月d日  HH:mm", article.publishedAt).toString()
                : "时间未提供";
        LinearLayout metadata = new LinearLayout(this);
        metadata.setGravity(Gravity.CENTER_VERTICAL);
        metadata.addView(compactText(article.source, 15, HOME_MUTED, false));
        metadata.addView(compactText("  ·  ", 15, HOME_MUTED, false));
        metadata.addView(compactText(published, 15, HOME_MUTED, false));
        LinearLayout.LayoutParams metadataParams = new LinearLayout.LayoutParams(-1, -2);
        metadataParams.topMargin = dp(16);
        card.addView(metadata, metadataParams);

        View divider = new View(this);
        divider.setBackgroundColor(Color.argb(34, 65, 81, 82));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
        dividerParams.topMargin = dp(22);
        dividerParams.bottomMargin = dp(22);
        card.addView(divider, dividerParams);
        card.addView(compactText("文章摘要", 17, HOME_ACCENT, true));
        String summary = article.description.isEmpty()
                ? "这个订阅源没有提供文章摘要。你可以从下方打开原文继续阅读。"
                : article.description;
        TextView body = text(summary, 20, HOME_INK, false);
        body.setLineSpacing(dp(8), 1.04f);
        card.addView(body, marginTop(12));

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        TextView sourceNote = compactText("内容来自 " + article.source + " 订阅源", 14, HOME_MUTED, false);
        footer.addView(sourceNote, new LinearLayout.LayoutParams(0, -2, 1));
        TextView openOriginal = compactText("阅读原文  ↗", 17, Color.WHITE, true);
        openOriginal.setGravity(Gravity.CENTER);
        openOriginal.setPadding(dp(24), 0, dp(24), 0);
        openOriginal.setBackground(cardBackground(HOME_ACCENT, 24));
        openOriginal.setFocusable(true);
        openOriginal.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(article.url))); }
            catch (Exception ignored) { Toast.makeText(this, "无法打开新闻链接", Toast.LENGTH_SHORT).show(); }
        });
        footer.addView(openOriginal, new LinearLayout.LayoutParams(-2, dp(52)));
        page.addView(footer);

        briefingDetailsPage = page;
        homeContent.setVisibility(View.INVISIBLE);
        homePage.addView(page, new FrameLayout.LayoutParams(-1, -1));
        back.requestFocus();
        configureImmersiveMode();
    }

    private void dismissBriefingDetails() {
        if (briefingDetailsPage == null) return;
        dismissBriefingImageViewer();
        if (homePage != null) homePage.removeView(briefingDetailsPage);
        briefingDetailsPage = null;
        if (homeContent != null) homeContent.setVisibility(View.VISIBLE);
        configureImmersiveMode();
    }

    private void showBriefingImageViewer(RssFeedClient.Article article) {
        if (homePage == null || briefingDetailsPage == null || article == null) return;
        dismissBriefingImageViewer();
        FrameLayout viewer = new FrameLayout(this);
        viewer.setBackgroundColor(Color.rgb(25, 31, 33));
        viewer.setOnClickListener(v -> dismissBriefingImageViewer());
        briefingImageViewerPage = viewer;

        ImageView fullImage = new ImageView(this);
        fullImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        fullImage.setBackgroundColor(Color.rgb(25, 31, 33));
        fullImage.setImageResource(R.drawable.ic_news_image_placeholder);
        fullImage.setContentDescription("简报配图：" + article.title);
        fullImage.setOnClickListener(v -> { });
        FrameLayout.LayoutParams imageParams = new FrameLayout.LayoutParams(-1, -1);
        imageParams.leftMargin = dp(24);
        imageParams.topMargin = dp(84);
        imageParams.rightMargin = dp(24);
        imageParams.bottomMargin = dp(72);
        viewer.addView(fullImage, imageParams);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(30), dp(14), dp(30), dp(8));
        TextView back = compactText("‹  返回简报", 18, Color.WHITE, true);
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(16), 0, dp(16), 0);
        back.setBackground(cardBackground(Color.argb(55, 255, 255, 255), 22));
        back.setOnClickListener(v -> dismissBriefingImageViewer());
        topBar.addView(back, new LinearLayout.LayoutParams(-2, dp(48)));
        TextView title = compactText(article.title, 16, Color.WHITE, false);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1);
        titleParams.leftMargin = dp(20);
        topBar.addView(title, titleParams);
        viewer.addView(topBar, new FrameLayout.LayoutParams(-1, dp(70), Gravity.TOP));

        TextView imageStatus = compactText("正在加载…", 13, Color.rgb(224, 229, 226), false);
        imageStatus.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(-1, dp(40), Gravity.BOTTOM);
        statusParams.bottomMargin = dp(12);
        viewer.addView(imageStatus, statusParams);
        RssImageLoader.loadFull(this, fullImage, article.imageUrl, success -> {
            if (briefingImageViewerPage != viewer) return;
            if (success) {
                imageStatus.setVisibility(View.GONE);
            } else {
                imageStatus.setText("图片暂时无法加载，请检查网络或稍后重试");
            }
        });

        homePage.addView(viewer, new FrameLayout.LayoutParams(-1, -1));
        back.requestFocus();
        configureImmersiveMode();
    }

    private void dismissBriefingImageViewer() {
        if (briefingImageViewerPage == null) return;
        if (homePage != null) homePage.removeView(briefingImageViewerPage);
        briefingImageViewerPage = null;
        configureImmersiveMode();
    }

    private void refreshRss() {
        if (rssList == null || network.isShutdown()) return;
        String address = AppPrefs.rssUrl(this);
        if (address.isEmpty()) {
            rssMeta.setText("设置里可以添加中文科技源");
            TextView empty = text("暂无新闻源", 17, HOME_MUTED, false);
            empty.setGravity(Gravity.CENTER);
            rssList.setGravity(Gravity.CENTER_VERTICAL);
            rssList.setItems(java.util.Collections.singletonList(empty));
            return;
        }
        rssMeta.setText("正在更新新闻…");
        network.execute(() -> {
            ArrayList<RssFeedClient.Article> articles = new ArrayList<>();
            String lastError = "";
            int availableSources = 0;
            java.util.HashSet<String> seen = new java.util.HashSet<>();
            String[] sources = address.split("[\\r\\n,]+");
            for (String source : sources) {
                String feed = source.trim();
                if (feed.isEmpty()) continue;
                try {
                    List<RssFeedClient.Article> fromFeed = RssFeedClient.fetch(feed, 4);
                    if (!fromFeed.isEmpty()) availableSources++;
                    for (RssFeedClient.Article article : fromFeed) {
                        if (seen.add(article.url) && articles.size() < 12) articles.add(article);
                    }
                } catch (Exception error) { lastError = safeMessage(error); }
            }
            org.json.JSONArray rssSnapshot = new org.json.JSONArray();
            for (RssFeedClient.Article article : articles) {
                org.json.JSONObject item = new org.json.JSONObject();
                try {
                    item.put("title", article.title.length() > 300 ? article.title.substring(0, 300) : article.title)
                            .put("url", article.url.length() > 2048 ? article.url.substring(0, 2048) : article.url)
                            .put("source", article.source.length() > 120 ? article.source.substring(0, 120) : article.source)
                            .put("published_at", article.publishedAt);
                    rssSnapshot.put(item);
                } catch (org.json.JSONException ignored) { }
            }
            long refreshedAt = System.currentTimeMillis();
            AppPrefs.cacheRss(this, rssSnapshot, refreshedAt, lastError);
            try {
                HomeAssistantClient haClient = new HomeAssistantClient(AppPrefs.haUrl(this),
                        SecretStore.homeAssistantToken(this), AppPrefs.haAllowHttp(this));
                haClient.fireEvent(CompanionLinkProtocol.EVENT_UPDATE, LocalDeviceStatus.capture(this));
            } catch (Exception error) {
                android.util.Log.w("MorningPanelHA", "Unable to report RSS refresh", error);
            }
            if (!articles.isEmpty()) {
                final int sourceCount = availableSources;
                runOnUiThread(() -> {
                    if (rssList == null) return;
                    rssMeta.setText(sourceCount + " 个来源 · " + android.text.format.DateFormat.format("HH:mm", System.currentTimeMillis()));
                    rssList.setGravity(Gravity.TOP);
                    List<View> rows = new ArrayList<>();
                    for (RssFeedClient.Article article : articles) {
                        LinearLayout itemRow = new LinearLayout(this);
                        itemRow.setGravity(Gravity.CENTER_VERTICAL);
                        itemRow.setPadding(dp(2), dp(9), dp(2), dp(9));
                        LinearLayout articleContent = new LinearLayout(this);
                        articleContent.setOrientation(LinearLayout.VERTICAL);
                        TextView source = text(article.source + "   ·   查看详情", 10, HOME_ACCENT, false);
                        articleContent.addView(source);
                        TextView item = text(article.title, 16, HOME_INK, false);
                        item.setMaxLines(2);
                        item.setEllipsize(android.text.TextUtils.TruncateAt.END);
                        item.setLineSpacing(dp(2), 1f);
                        articleContent.addView(item, marginTop(2));
                        itemRow.addView(articleContent, new LinearLayout.LayoutParams(-1, -2));
                        itemRow.setFocusable(true);
                        itemRow.setContentDescription(article.source + "，" + article.title + "，点击查看简报详情");
                        View.OnClickListener openArticle = v -> showBriefingDetails(article);
                        itemRow.setOnClickListener(openArticle);
                        articleContent.setOnClickListener(openArticle);
                        item.setOnClickListener(openArticle);
                        LinearLayout completeRow = new LinearLayout(this);
                        completeRow.setOrientation(LinearLayout.VERTICAL);
                        completeRow.addView(itemRow);
                        View line = new View(this);
                        line.setBackgroundColor(Color.argb(32, 65, 81, 82));
                        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(-1, dp(1));
                        lineParams.leftMargin = dp(2);
                        completeRow.addView(line, lineParams);
                        rows.add(completeRow);
                    }
                    rssList.setItems(rows);
                });
            } else {
                String reason = lastError.isEmpty() ? "订阅源暂时没有文章" : "新闻暂不可用 · " + lastError;
                runOnUiThread(() -> {
                    if (rssList == null) return;
                    rssMeta.setText(reason);
                    rssList.setGravity(Gravity.CENTER_VERTICAL);
                    rssList.setItems(java.util.Collections.singletonList(
                            text("可在设置中修改来源", 16, MUTED, false)));
                });
            }
        });
    }

    private void refreshHomeAssistant() {
        if (haList == null || network.isShutdown()) return;
        long requestEventVersion = homeAssistantStateEventVersion;
        String base = AppPrefs.haUrl(this);
        if (base.isEmpty() || !SecretStore.hasHomeAssistantToken(this)) {
            haTitle.setText("HOME ASSISTANT  ·  未连接");
            haList.removeAllViews();
            homeAssistantCards.clear();
            return;
        }
        String[] configured = AppPrefs.haHomeEntities(this).split("[\\r\\n,]+");
        ArrayList<String> entityIds = new ArrayList<>();
        for (String entity : configured) if (!entity.trim().isEmpty()) entityIds.add(entity.trim());
        if (entityIds.isEmpty()) {
            haTitle.setText("HOME ASSISTANT  ·  设置里选择主页设备");
            haList.removeAllViews();
            homeAssistantCards.clear();
            return;
        }
        haTitle.setText("HOME ASSISTANT  ·  更新中");
        network.execute(() -> {
            try {
                String secret = SecretStore.homeAssistantToken(this);
                HomeAssistantClient client = new HomeAssistantClient(base, secret, AppPrefs.haAllowHttp(this));
                JSONArray allStates = client.states();
                java.util.HashMap<String, JSONObject> statesByEntity = new java.util.HashMap<>();
                for (int i = 0; i < allStates.length(); i++) {
                    JSONObject state = allStates.optJSONObject(i);
                    if (state != null) statesByEntity.put(state.optString("entity_id", ""), state);
                }
                ArrayList<JSONObject> states = new ArrayList<>();
                for (String entity : entityIds) {
                    JSONObject state = statesByEntity.get(entity);
                    states.add(state == null ? new JSONObject().put("entity_id", entity).put("state", "不可用") : state);
                }
                runOnUiThread(() -> {
                    if (haList == null) return;
                    haTitle.setText("HOME ASSISTANT  ·  " + android.text.format.DateFormat.format("HH:mm", System.currentTimeMillis()));
                    haList.removeAllViews();
                    homeAssistantCards.clear();
                    for (JSONObject state : states) {
                        String entityId = state.optString("entity_id", "");
                        Long eventVersion = homeAssistantRealtimeVersions.get(entityId);
                        if (eventVersion != null && eventVersion > requestEventVersion) {
                            JSONObject liveState = homeAssistantRealtimeStates.get(entityId);
                            if (liveState != null) state = liveState;
                        }
                        View card = homeEntityCard(state);
                        haList.addView(card);
                        homeAssistantCards.put(state.optString("entity_id", ""), card);
                    }
                });
            } catch (Exception error) {
                String result = "HOME ASSISTANT  ·  " + safeMessage(error);
                runOnUiThread(() -> { if (haTitle != null) haTitle.setText(result); });
            }
        });
    }

    private View homeEntityCard(JSONObject state) {
        JSONObject attributes = state.optJSONObject("attributes");
        String id = state.optString("entity_id", "HA 设备");
        String label = attributes == null ? id : attributes.optString("friendly_name", id);
        String value = state.optString("state", "未知");
        String unit = attributes == null ? "" : attributes.optString("unit_of_measurement", "");
        String domain = id.contains(".") ? id.substring(0, id.indexOf('.')) : "";
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(11), dp(5), dp(10), dp(5));
        boolean controllable = "switch".equals(domain) || "light".equals(domain)
                || "input_boolean".equals(domain) || "fan".equals(domain);
        boolean enabled = "on".equalsIgnoreCase(value);
        int glyphKind = "select".equals(domain) ? MinimalGlyphView.MODE
                : "light".equals(domain) ? MinimalGlyphView.LIGHT
                : "fan".equals(domain) ? MinimalGlyphView.FAN
                : controllable ? MinimalGlyphView.POWER : MinimalGlyphView.DEVICE;
        int glyphTint = controllable && enabled ? HOME_ACCENT : HOME_MUTED;
        MinimalGlyphView glyph = new MinimalGlyphView(this, glyphKind, glyphTint);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(28), dp(28));
        iconParams.rightMargin = dp(9);
        card.addView(glyph, iconParams);
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        TextView name = text(label, 10, HOME_MUTED, false);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        details.addView(name);
        if ("select".equals(domain)) {
            TextView choice = text(value, 11, HOME_ACCENT, false);
            choice.setMaxLines(1);
            choice.setEllipsize(android.text.TextUtils.TruncateAt.END);
            details.addView(choice, marginTop(2));
            card.setContentDescription(label + "，当前模式 " + value + "，点击更改");
            View.OnClickListener openOptions = v -> showHomeSelectOptions(state);
            card.setOnClickListener(openOptions);
        } else if ("switch".equals(domain) || "light".equals(domain)
                || "input_boolean".equals(domain) || "fan".equals(domain)) {
            String stateLabel = enabled ? "已开启" : "已关闭";
            TextView current = text(stateLabel, 11, enabled ? HOME_ACCENT : HOME_INK, false);
            current.setMaxLines(1);
            current.setEllipsize(android.text.TextUtils.TruncateAt.END);
            details.addView(current, marginTop(2));
            card.setContentDescription((enabled ? "关闭 " : "打开 ") + label);
            card.setOnClickListener(v -> {
                try { updateHomeAssistantEntityState(new JSONObject(state.toString()).put("state", enabled ? "off" : "on")); }
                catch (Exception ignored) { }
                runHomeAssistantCommand(client -> client.setSwitch(id, !enabled), "已控制 " + label);
            });
        } else {
            TextView current = text(value + (unit.isEmpty() ? "" : " " + unit), 12, HOME_INK, true);
            current.setMaxLines(1);
            current.setEllipsize(android.text.TextUtils.TruncateAt.END);
            details.addView(current, marginTop(1));
        }
        card.setBackground(cardBackground(Color.argb(75, 230, 245, 248), 14));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(185), dp(62));
        params.rightMargin = dp(8);
        card.setLayoutParams(params);
        return card;
    }

    private void updateHomeAssistantEntityState(JSONObject state) {
        String entityId = state.optString("entity_id", "");
        View current = homeAssistantCards.get(entityId);
        if (haList == null || current == null) return;
        int index = haList.indexOfChild(current);
        if (index < 0) return;
        View updated = homeEntityCard(state);
        haList.removeViewAt(index);
        haList.addView(updated, index);
        homeAssistantCards.put(entityId, updated);
    }

    private void applyHomeAssistantRealtimeState(JSONObject state) {
        String entityId = state.optString("entity_id", "");
        if (entityId.isEmpty()) return;
        homeAssistantStateEventVersion++;
        homeAssistantRealtimeStates.put(entityId, state);
        homeAssistantRealtimeVersions.put(entityId, homeAssistantStateEventVersion);
        updateHomeAssistantEntityState(state);
    }

    private void showHomeSelectOptions(JSONObject state) {
        JSONObject attributes = state.optJSONObject("attributes");
        org.json.JSONArray options = attributes == null ? null : attributes.optJSONArray("options");
        String id = state.optString("entity_id", "");
        if (options == null || options.length() == 0) {
            showBrandedMessage("无法更改选项", "此 Home Assistant 实体没有提供可选项。");
            return;
        }
        android.app.Dialog dialog = new android.app.Dialog(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(21), dp(24), dp(18));
        content.setBackground(cardBackground(SURFACE, 22));
        content.addView(compactText(attributes == null ? id : attributes.optString("friendly_name", id), 22, HOME_INK, true));
        content.addView(compactText("选择新的设备状态", 14, HOME_MUTED, false), marginTop(4));
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.VERTICAL);
        String current = state.optString("state", "");
        for (int i = 0; i < options.length(); i++) {
            String selected = options.optString(i);
            TextView choice = compactText(selected + (selected.equals(current) ? "   ·   当前" : ""), 17,
                    selected.equals(current) ? HOME_ACCENT : HOME_INK, selected.equals(current));
            choice.setGravity(Gravity.CENTER_VERTICAL);
            choice.setPadding(dp(16), 0, dp(16), 0);
            choice.setBackground(cardBackground(selected.equals(current) ? Color.argb(35, 39, 119, 121) : Color.WHITE, 14));
            choice.setMinHeight(dp(52));
            choice.setFocusable(true);
            choice.setOnClickListener(v -> {
                dialog.dismiss();
                try { updateHomeAssistantEntityState(new JSONObject(state.toString()).put("state", selected)); }
                catch (Exception ignored) { }
                runHomeAssistantCommand(client -> client.selectOption(id, selected), "已设置为 " + selected);
            });
            LinearLayout.LayoutParams choiceParams = new LinearLayout.LayoutParams(-1, dp(52));
            if (i > 0) choiceParams.topMargin = dp(6);
            choices.addView(choice, choiceParams);
        }
        scroll.addView(choices);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1);
        scrollParams.topMargin = dp(13);
        content.addView(scroll, scrollParams);
        Button close = button("返回", false);
        close.setOnClickListener(v -> dialog.dismiss());
        content.addView(close, marginTop(12));
        dialog.setContentView(content);
        dialog.setOnDismissListener(ignored -> configureImmersiveMode());
        showBrandedDialog(dialog, 620, 620);
    }

    private String entitySummary(HomeAssistantClient client, String label, String entity) {
        if (entity == null || entity.trim().isEmpty()) return label + "：未配置";
        try {
            JSONObject state = client.state(entity);
            JSONObject attributes = state.optJSONObject("attributes");
            String name = attributes == null ? label : attributes.optString("friendly_name", label);
            String unit = attributes == null ? "" : attributes.optString("unit_of_measurement", "");
            return name + "：" + state.optString("state", "未知") + (unit.isEmpty() ? "" : " " + unit);
        } catch (Exception error) {
            return label + "：未知";
        }
    }

    private void sendProjectorCommand(boolean enabled) {
        if (!homeAssistantConfigured()) {
            openHomeAssistantSettingsInApp();
            return;
        }
        runHomeAssistantCommand(client -> client.setSwitch(AppPrefs.haProjector(this), enabled),
                enabled ? "已发送投影开启命令" : "已发送投影关闭命令");
    }

    private void sendBrightnessCommand(int value) {
        if (!homeAssistantConfigured()) {
            openHomeAssistantSettingsInApp();
            return;
        }
        runHomeAssistantCommand(client -> client.setBrightness(AppPrefs.haLight(this), value),
                "已发送亮度 " + value + "% 命令");
    }

    private void openHomeAssistantSettingsInApp() {
        if (settingsOverlay == null) showSettingsMenu();
        if (settingsOverlay != null) renderSettingsSection(3);
    }

    private boolean homeAssistantConfigured() {
        return !AppPrefs.haUrl(this).isEmpty() && SecretStore.hasHomeAssistantToken(this);
    }

    private void runHomeAssistantCommand(HomeAssistantAction action, String success) {
        network.execute(() -> {
            try {
                HomeAssistantClient client = new HomeAssistantClient(AppPrefs.haUrl(this),
                        SecretStore.homeAssistantToken(this), AppPrefs.haAllowHttp(this));
                action.run(client);
                runOnUiThread(() -> {
                    Toast.makeText(this, success, Toast.LENGTH_SHORT).show();
                    handler.removeCallbacks(haFallbackRefresh);
                    handler.postDelayed(haFallbackRefresh, 1400);
                });
            } catch (Exception error) {
                String message = safeMessage(error);
                runOnUiThread(() -> {
                    Toast.makeText(this, "HA 命令失败：" + message, Toast.LENGTH_LONG).show();
                    refreshHomeAssistant();
                });
            }
        });
    }

    private interface HomeAssistantAction { void run(HomeAssistantClient client) throws Exception; }

    private final Runnable haFallbackRefresh = this::refreshHomeAssistant;

    private String safeMessage(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) return "未知错误";
        return message.length() > 110 ? message.substring(0, 110) : message;
    }

    private ImageView articleImage(String url, String description) {
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(cardBackground(Color.rgb(229, 232, 226), 13));
        image.setImageResource(R.drawable.ic_news_image_placeholder);
        image.setClipToOutline(true);
        image.setContentDescription(description);
        if (url != null && !url.isEmpty()) RssImageLoader.load(this, image, url);
        return image;
    }
    private void refreshAlarmList() {
        if (alarmList == null) return;
        alarmList.removeAllViews();
        List<AlarmItem> alarms = AlarmStore.get(this);
        if (alarms.isEmpty()) {
            TextView empty = text("还没有闹钟。创建第一个时间，之后即可在此开关或编辑。", 16, MUTED, false);
            empty.setPadding(dp(18), dp(18), dp(18), dp(18));
            empty.setBackground(cardBackground(Color.WHITE, 16));
            alarmList.addView(empty);
        }
        for (AlarmItem alarm : alarms) alarmList.addView(alarmCard(alarm), marginTop(8));
        updateNextAlarm();
    }

    private View alarmCard(AlarmItem alarm) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(8), dp(12), dp(8));
        row.setBackground(cardBackground(Color.WHITE, 16));
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        TextView time = text(String.format(java.util.Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute), 31, INK, true);
        String schedule = alarm.scheduleMode == AlarmItem.SCHEDULE_WORKDAYS
                ? "仅工作日" + (alarm.ringOnMakeupWorkdays ? " · 含周末补班" : " · 跳过周末补班")
                : repeatText(alarm.repeatMask);
        TextView subtitle = text(alarm.label + "  ·  " + schedule, 14, MUTED, false);
        details.addView(time);
        details.addView(subtitle);
        row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        Switch active = new Switch(this);
        active.setTypeface(zhuqueTypeface());
        active.setChecked(alarm.enabled);
        active.setContentDescription("启用" + alarm.label);
        active.setOnCheckedChangeListener((button, checked) -> replaceAlarm(alarm.withEnabled(checked)));
        row.addView(active, marginEnd(8));
        Button edit = smallButton("编辑");
        edit.setOnClickListener(v -> editAlarm(alarm));
        row.addView(edit, marginEnd(5));
        Button delete = smallButton("删除");
        delete.setOnClickListener(v -> showBrandedConfirm("删除这个闹钟？", alarm.label,
                "保留", "删除", () -> deleteAlarm(alarm.id)));
        row.addView(delete);
        return row;
    }

    private void editAlarm(AlarmItem existing) {
        if (settingsOverlay == null) showSettingsMenu();
        if (settingsOverlay == null) return;
        if (settingsShell != null) settingsShell.setVisibility(View.INVISIBLE);
        FrameLayout editor = new FrameLayout(this);
        editor.setBackground(backgroundGradient(CANVAS, Color.rgb(237, 235, 228)));
        alarmEditorPage = editor;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(32), dp(20), dp(32), dp(56));
        editor.addView(root, new FrameLayout.LayoutParams(-1, -1));
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect visible = new Rect();
            root.getWindowVisibleDisplayFrame(visible);
            int obscuredBottom = Math.max(0, root.getRootView().getHeight() - visible.bottom);
            int keyboardInset = obscuredBottom > dp(140) ? obscuredBottom : 0;
            int targetBottomPadding = dp(56) + keyboardInset;
            if (Math.abs(root.getPaddingBottom() - targetBottomPadding) > dp(2))
                root.setPadding(dp(32), dp(20), dp(32), targetBottomPadding);
        });

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = button("← 返回闹钟", false);
        back.setTextSize(16);
        back.setOnClickListener(v -> dismissAlarmEditor());
        header.addView(back, new LinearLayout.LayoutParams(dp(185), dp(54)));
        TextView title = compactText(existing == null ? "新增闹钟" : "编辑闹钟", 27, HOME_INK, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1);
        titleParams.leftMargin = dp(18);
        header.addView(title, titleParams);
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(0, dp(15), 0, dp(12));
        scroll.addView(form);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout columns = new LinearLayout(this);
        float screenWidthDp = getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density;
        boolean compactLayout = screenWidthDp < 1180f;
        columns.setOrientation(compactLayout ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        form.addView(columns, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout timeCard = new LinearLayout(this);
        timeCard.setGravity(Gravity.CENTER);
        timeCard.setPadding(dp(12), dp(9), dp(12), dp(9));
        timeCard.setBackground(cardBackground(SURFACE, 20));
        TimePicker picker = new TimePicker(this);
        picker.setIs24HourView(true);
        Calendar now = Calendar.getInstance();
        now.add(Calendar.MINUTE, 1);
        picker.setHour(existing == null ? now.get(Calendar.HOUR_OF_DAY) : existing.hour);
        picker.setMinute(existing == null ? now.get(Calendar.MINUTE) : existing.minute);
        timeCard.addView(picker, new LinearLayout.LayoutParams(-1, dp(280)));
        columns.addView(timeCard, new LinearLayout.LayoutParams(compactLayout ? -1 : dp(500), -2));

        LinearLayout schedule = new LinearLayout(this);
        schedule.setOrientation(LinearLayout.VERTICAL);
        schedule.setPadding(dp(22), dp(17), dp(22), dp(15));
        schedule.setBackground(cardBackground(SURFACE, 20));
        LinearLayout.LayoutParams scheduleParams = compactLayout
                ? new LinearLayout.LayoutParams(-1, -2)
                : new LinearLayout.LayoutParams(0, -2, 1);
        if (compactLayout) scheduleParams.topMargin = dp(12);
        else scheduleParams.leftMargin = dp(18);
        columns.addView(schedule, scheduleParams);

        EditText label = settingField("闹钟名称", existing == null ? "" : existing.label, InputType.TYPE_CLASS_TEXT);
        label.setHint("名称，例如：工作日");
        schedule.addView(label, new LinearLayout.LayoutParams(-1, dp(54)));
        Switch workdays = new Switch(this);
        workdays.setTypeface(zhuqueTypeface());
        workdays.setText("仅工作日响（法定休息日跳过）");
        workdays.setTextSize(16);
        workdays.setTextColor(HOME_INK);
        workdays.setButtonTintList(ColorStateList.valueOf(HOME_ACCENT));
        workdays.setChecked(existing != null && existing.scheduleMode == AlarmItem.SCHEDULE_WORKDAYS);
        schedule.addView(workdays, marginTop(10));
        TextView repeatTitle = compactText("每周重复（全部不选 = 一次性）", 14, HOME_MUTED, true);
        schedule.addView(repeatTitle, marginTop(6));
        String[] days = {"一", "二", "三", "四", "五", "六", "日"};
        CheckBox[] checks = new CheckBox[7];
        LinearLayout week = new LinearLayout(this);
        week.setGravity(Gravity.CENTER);
        for (int i = 0; i < days.length; i++) {
            checks[i] = settingsCheck(days[i], existing != null && (existing.repeatMask & (1 << i)) != 0);
            checks[i].setTextSize(14);
            week.addView(checks[i], new LinearLayout.LayoutParams(0, dp(50), 1));
        }
        schedule.addView(week);
        CheckBox makeup = settingsCheck("周末补班日也响",
                existing == null || existing.scheduleMode != AlarmItem.SCHEDULE_WORKDAYS || existing.ringOnMakeupWorkdays);
        makeup.setEnabled(workdays.isChecked());
        schedule.addView(makeup, marginTop(3));
        TextView calendarStatus = compactText(HolidayCalendarStore.status(this), 12, HOME_MUTED, false);
        schedule.addView(calendarStatus, marginTop(3));
        TextView previewTitle = compactText("未来 14 天", 15, HOME_ACCENT, true);
        schedule.addView(previewTitle, marginTop(8));
        TextView preview = compactText("", 13, HOME_INK, false);
        preview.setLineSpacing(dp(2), 1f);
        schedule.addView(preview, marginTop(4));

        Runnable updatePreview = () -> {
            makeup.setEnabled(workdays.isChecked());
            int mask = 0;
            for (int i = 0; i < checks.length; i++) if (checks[i].isChecked()) mask |= 1 << i;
            AlarmItem sample = new AlarmItem(existing == null ? -1 : existing.id, picker.getHour(), picker.getMinute(),
                    label.getText().toString(), mask, true,
                    workdays.isChecked() ? AlarmItem.SCHEDULE_WORKDAYS : AlarmItem.SCHEDULE_WEEKLY, makeup.isChecked());
            preview.setText(alarmPreview(sample));
        };
        workdays.setOnCheckedChangeListener((button, checked) -> {
            for (CheckBox check : checks) check.setEnabled(!checked);
            updatePreview.run();
        });
        makeup.setOnCheckedChangeListener((button, checked) -> updatePreview.run());
        for (CheckBox check : checks) check.setOnCheckedChangeListener((button, checked) -> updatePreview.run());
        picker.setOnTimeChangedListener((view, hourOfDay, minute) -> updatePreview.run());
        label.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updatePreview.run(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        updatePreview.run();

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        Button cancel = button("取消", false);
        cancel.setOnClickListener(v -> dismissAlarmEditor());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(62), 1));
        Button save = button("保存闹钟", true);
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, dp(62), 1);
        saveParams.leftMargin = dp(10);
        actions.addView(save, saveParams);
        save.setOnClickListener(v -> {
            int mask = 0;
            for (int i = 0; i < checks.length; i++) if (checks[i].isChecked()) mask |= 1 << i;
            int id = existing == null ? AlarmStore.nextId(this) : existing.id;
            AlarmItem item = new AlarmItem(id, picker.getHour(), picker.getMinute(), label.getText().toString(), mask,
                    existing == null || existing.enabled,
                    workdays.isChecked() ? AlarmItem.SCHEDULE_WORKDAYS : AlarmItem.SCHEDULE_WEEKLY,
                    workdays.isChecked() && makeup.isChecked());
            upsertAlarm(item);
            dismissAlarmEditor();
        });
        root.addView(actions, marginTop(8));
        settingsOverlay.addView(editor, new FrameLayout.LayoutParams(-1, -1));
        configureImmersiveMode();
    }

    private void dismissAlarmEditor() {
        if (alarmEditorPage == null) return;
        settingsOverlay.removeView(alarmEditorPage);
        alarmEditorPage = null;
        if (settingsShell != null) settingsShell.setVisibility(View.VISIBLE);
        configureImmersiveMode();
    }

    private String alarmPreview(AlarmItem alarm) {
        StringBuilder result = new StringBuilder();
        Calendar now = Calendar.getInstance();
        for (int offset = 0; offset < 14; offset++) {
            Calendar day = (Calendar) now.clone();
            day.add(Calendar.DAY_OF_YEAR, offset);
            day.set(Calendar.HOUR_OF_DAY, alarm.hour);
            day.set(Calendar.MINUTE, alarm.minute);
            day.set(Calendar.SECOND, 0);
            day.set(Calendar.MILLISECOND, 0);
            if (day.getTimeInMillis() <= System.currentTimeMillis()) continue;
            boolean rings;
            String reason;
            if (alarm.scheduleMode == AlarmItem.SCHEDULE_WORKDAYS) {
                HolidayCalendarStore.CalendarData calendar = HolidayCalendarStore.get(this, day.get(Calendar.YEAR));
                String date = HolidayCalendarStore.dateKey(day);
                boolean weekday = day.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY
                        && day.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY;
                if (calendar.workDays.contains(date)) {
                    rings = alarm.ringOnMakeupWorkdays;
                    reason = rings ? "补班响" : "补班跳过";
                } else if (calendar.restDays.contains(date)) {
                    rings = false;
                    reason = weekday ? "法定休息" : "周末休息";
                } else {
                    rings = weekday;
                    reason = weekday ? (calendar.fetchedAt == 0L ? "暂按工作日" : "工作日") : "周末";
                }
            } else {
                int mondayFirstBit = 1 << ((day.get(Calendar.DAY_OF_WEEK) + 5) % 7);
                rings = alarm.repeatMask == 0 ? offset == 0 : (alarm.repeatMask & mondayFirstBit) != 0;
                reason = alarm.repeatMask == 0 ? "一次性" : (rings ? "每周响" : "不重复");
            }
            if (result.length() > 0) result.append('\n');
            result.append(new java.text.SimpleDateFormat("M/d EEE", java.util.Locale.SIMPLIFIED_CHINESE).format(day.getTime()))
                    .append("  ·  ").append(rings ? "响铃" : "不响").append("（").append(reason).append("）");
        }
        return result.length() == 0 ? "未来 14 天没有符合规则的响铃日期。" : result.toString();
    }
    private void upsertAlarm(AlarmItem replacement) {
        List<AlarmItem> alarms = AlarmStore.get(this);
        ArrayList<AlarmItem> next = new ArrayList<>();
        boolean found = false;
        for (AlarmItem alarm : alarms) {
            if (alarm.id == replacement.id) { next.add(replacement); found = true; }
            else next.add(alarm);
        }
        if (!found) next.add(replacement);
        AlarmStore.save(this, next);
        refreshAlarmList();
    }

    private void replaceAlarm(AlarmItem replacement) { upsertAlarm(replacement); }

    private void deleteAlarm(int id) {
        ArrayList<AlarmItem> next = new ArrayList<>();
        for (AlarmItem alarm : AlarmStore.get(this)) if (alarm.id != id) next.add(alarm);
        AlarmStore.save(this, next);
        refreshAlarmList();
    }

    private void updateNextAlarm() {
        if (nextAlarmView == null) return;
        long next = Long.MAX_VALUE;
        long now = System.currentTimeMillis();
        for (AlarmItem alarm : AlarmStore.get(this)) {
            if (!alarm.enabled) continue;
            long candidate = AlarmScheduler.nextTrigger(this, alarm, now);
            if (candidate > 0 && candidate < next) {
                next = candidate;
            }
        }
        if (nextAlarmLine != null) nextAlarmLine.setVisibility(next == Long.MAX_VALUE ? View.GONE : View.VISIBLE);
        if (next != Long.MAX_VALUE) {
            Calendar today = Calendar.getInstance();
            Calendar tomorrow = (Calendar) today.clone();
            tomorrow.add(Calendar.DAY_OF_MONTH, 1);
            Calendar alarmDay = Calendar.getInstance();
            alarmDay.setTimeInMillis(next);
            String day = HolidayCalendarStore.dateKey(alarmDay);
            String when = day.equals(HolidayCalendarStore.dateKey(today)) ? "今天"
                    : day.equals(HolidayCalendarStore.dateKey(tomorrow)) ? "明天"
                    : new java.text.SimpleDateFormat("M月d日", java.util.Locale.SIMPLIFIED_CHINESE)
                            .format(new java.util.Date(next));
            String time = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.SIMPLIFIED_CHINESE)
                    .format(new java.util.Date(next));
            nextAlarmView.setText(when + " " + time);
            if (nextAlarmLine != null) nextAlarmLine.setContentDescription("下一次闹钟：" + when + " " + time + "，点击管理闹钟");
        }
        updateClockWeekCalendar();
    }

    private void updateClockWeekCalendar() {
        if (clockWeekDays == null || clockWeekMonth == null) return;
        Calendar today = Calendar.getInstance();
        Calendar weekStart = (Calendar) today.clone();
        weekStart.set(Calendar.HOUR_OF_DAY, 0);
        weekStart.set(Calendar.MINUTE, 0);
        weekStart.set(Calendar.SECOND, 0);
        weekStart.set(Calendar.MILLISECOND, 0);
        weekStart.add(Calendar.DAY_OF_MONTH, -((today.get(Calendar.DAY_OF_WEEK) + 5) % 7));
        Calendar weekEnd = (Calendar) weekStart.clone();
        weekEnd.add(Calendar.DAY_OF_MONTH, 6);
        HolidayCalendarStore.CalendarData firstYear = HolidayCalendarStore.get(this, weekStart.get(Calendar.YEAR));
        HolidayCalendarStore.CalendarData lastYear = weekEnd.get(Calendar.YEAR) == firstYear.year
                ? firstYear : HolidayCalendarStore.get(this, weekEnd.get(Calendar.YEAR));
        String todayKey = HolidayCalendarStore.dateKey(today);
        String signature = todayKey + ":" + firstYear.fetchedAt + ":" + lastYear.fetchedAt;
        if (signature.equals(clockWeekSignature)) return;
        clockWeekSignature = signature;
        clockWeekDays.removeAllViews();

        String[] weekdays = {"一", "二", "三", "四", "五", "六", "日"};
        Calendar day = (Calendar) weekStart.clone();
        for (int i = 0; i < 7; i++, day.add(Calendar.DAY_OF_MONTH, 1)) {
            HolidayCalendarStore.CalendarData data = day.get(Calendar.YEAR) == firstYear.year ? firstYear : lastYear;
            String date = HolidayCalendarStore.dateKey(day);
            boolean isToday = date.equals(todayKey);
            boolean makeup = data.workDays.contains(date);
            boolean rest = !makeup && data.restDays.contains(date);
            LinearLayout column = new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView weekday = compactText(weekdays[i], 14, HOME_MUTED, false);
            weekday.setGravity(Gravity.CENTER);
            column.addView(weekday);
            TextView number = compactText(String.valueOf(day.get(Calendar.DAY_OF_MONTH)), 22,
                    isToday ? SURFACE : HOME_INK, false);
            number.setGravity(Gravity.CENTER);
            if (isToday) number.setBackground(cardBackground(HOME_ACCENT, 22));
            LinearLayout.LayoutParams numberParams = new LinearLayout.LayoutParams(dp(44), dp(44));
            numberParams.topMargin = dp(11);
            column.addView(number, numberParams);
            TextView marker = compactText(makeup ? "班" : rest ? "休" : "", 12, makeup ? GOLD : HOME_ACCENT, false);
            marker.setGravity(Gravity.CENTER);
            marker.setVisibility(makeup || rest ? View.VISIBLE : View.INVISIBLE);
            LinearLayout.LayoutParams markerParams = new LinearLayout.LayoutParams(-1, dp(16));
            markerParams.topMargin = dp(3);
            column.addView(marker, markerParams);
            String description = new java.text.SimpleDateFormat("yyyy年M月d日 EEEE", java.util.Locale.SIMPLIFIED_CHINESE)
                    .format(day.getTime()) + (isToday ? "，今天" : "") + (makeup ? "，补班日" : rest ? "，休息日" : "");
            column.setContentDescription(description);
            column.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            weekday.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            number.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            marker.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            clockWeekDays.addView(column, new LinearLayout.LayoutParams(0, -2, 1));
        }
        java.text.SimpleDateFormat month = new java.text.SimpleDateFormat("M 月", java.util.Locale.SIMPLIFIED_CHINESE);
        String firstMonth = month.format(weekStart.getTime());
        String lastMonth = month.format(weekEnd.getTime());
        clockWeekMonth.setText(firstMonth.equals(lastMonth) ? firstMonth : firstMonth + " · " + lastMonth);
        java.text.SimpleDateFormat fullMonth = new java.text.SimpleDateFormat("yyyy年M月", java.util.Locale.SIMPLIFIED_CHINESE);
        clockWeekMonth.setContentDescription("本周日历：" + fullMonth.format(weekStart.getTime())
                + (firstMonth.equals(lastMonth) ? "" : "至" + fullMonth.format(weekEnd.getTime())));
    }

    private void showDiagnostics() {
        TextView report = text("正在检查 Android 与 Root 权限…", 17, INK, false);
        report.setPadding(dp(20), dp(12), dp(20), dp(10));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(6), dp(12), dp(8));
        content.addView(report, new LinearLayout.LayoutParams(-1, -2));
        TextView rootNote = text("点击后会调用 su 请求授权；若设备使用 Magisk 等 Root 管理器，请在系统弹窗中允许。", 13, MUTED, false);
        content.addView(rootNote, marginTop(4));
        Button requestRoot = button("请求 / 重新检测 Root 授权", true);
        content.addView(requestRoot, marginTop(8));
        Button testSleep = button("测试休眠并自动唤醒", false);
        content.addView(testSleep, marginTop(8));
        TextView sleepNote = text("测试会短暂关闭屏幕，约 2 秒后自动唤醒。投影光学引擎是否同步关闭需在 Xperia Touch 真机确认。", 12, MUTED, false);
        content.addView(sleepNote, marginTop(5));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("设备能力诊断")
                .setView(content).setPositiveButton("完成", null).create();
        dialog.show();
        String system = "Android " + android.os.Build.VERSION.RELEASE + "（API " + android.os.Build.VERSION.SDK_INT + ")"
                + "\n型号：" + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
                + "\n设备：" + android.os.Build.DEVICE
                + "\n画面方向：横屏；目标画布 1366 × 768"
                + "\n\n应用级 Root：检查中"
                + "\n模拟器 ADB Root 与应用 Root 是两项独立权限"
                + "\n投影引擎开关：未知（没有此固件的已验证驱动）"
                + "\n投影光源亮度：未知（本页滑杆只调应用画面）"
                + "\n系统休眠控制：Root 授权后可测试，硬件行为需实机确认"
                + "\n\nRoot 命令限定为检测和屏幕休眠，不接收任意 HA shell 文本。";
        report.setText(system);
        requestRoot.setOnClickListener(v -> {
            requestRoot.setEnabled(false);
            requestRoot.setText("等待 Root 授权…");
            new Thread(() -> {
                String rootStatus;
                try { rootStatus = probeRoot(); }
                catch (Exception ignored) { rootStatus = "应用级 Root：检查失败"; }
                String result = rootStatus;
                runOnUiThread(() -> {
                    report.setText(system.replace("应用级 Root：检查中", result));
                    requestRoot.setText(result.contains("已授权") ? "Root 已授权 · 重新检测" : "重试 Root 授权");
                    requestRoot.setEnabled(true);
                });
            }, "xperia-root-request").start();
        });
        testSleep.setOnClickListener(v -> {
            if (!LocalDeviceControl.hasRoot()) {
                Toast.makeText(this, "请先请求并授予应用 Root 权限", Toast.LENGTH_LONG).show();
                return;
            }
            boolean keepAwake = AppPrefs.keepScreenOn(this);
            testSleep.setEnabled(false);
            testSleep.setText("屏幕即将休眠…");
            LocalDeviceControl.sleep(this);
            handler.postDelayed(() -> {
                LocalDeviceControl.wake(this);
                AppPrefs.setKeepScreenOn(this, keepAwake);
                MainActivity.setKeepScreenAwake(keepAwake);
                testSleep.setText("测试休眠并自动唤醒");
                testSleep.setEnabled(true);
                Toast.makeText(this, "已发送唤醒命令；请确认屏幕与投影已恢复", Toast.LENGTH_LONG).show();
            }, 2200L);
        });
    }

    private String probeRoot() {
        return LocalDeviceControl.requestRootAccess() ? "应用级 Root：已授权" : "应用级 Root：未授予或不可用";
    }
    private void showBackupOptions() {
        new AlertDialog.Builder(this).setTitle("本機闹钟备份")
                .setMessage("备份文件只包含闹钟时间、标签与重复规则，不包含账号或服务密钥。")
                .setNegativeButton("取消", null)
                .setNeutralButton("导入 JSON", (d, w) -> chooseImport())
                .setPositiveButton("导出 JSON", (d, w) -> chooseExport()).show();
    }

    private void chooseExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "xperia-touch-alarms.json");
        startActivityForResult(intent, REQUEST_EXPORT);
    }

    private void chooseImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, REQUEST_IMPORT);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == REQUEST_EXPORT) {
                try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                    output.write(AlarmStore.exportJson(this).getBytes(StandardCharsets.UTF_8));
                }
                showSettingsFeedback("闹钟备份已导出。", true);
            } else if (requestCode == REQUEST_IMPORT) {
                StringBuilder source = new StringBuilder();
                try (InputStream input = getContentResolver().openInputStream(uri);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) source.append(line);
                }
                AlarmStore.save(this, AlarmStore.importJson(source.toString()));
                refreshAlarmList();
                showSettingsFeedback("闹钟设定已导入。", true);
            }
        } catch (Exception error) {
            showBrandedMessage("备份处理失败", error instanceof JSONException
                    ? "JSON 格式无效，没有套用设定。"
                    : (error.getMessage() == null ? "无法读取或写入闹钟备份。" : error.getMessage()));
        }
    }

    private String repeatText(int mask) {
        if (mask == 0) return "一次性";
        if (mask == 127) return "每天";
        if (mask == 31) return "工作日";
        if (mask == 96) return "周末";
        String[] names = {"一", "二", "三", "四", "五", "六", "日"};
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < 7; i++) if ((mask & (1 << i)) != 0) result.append(names[i]);
        return "每周" + result;
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp * 1.18f);
        view.setTextColor(color);
        Typeface face = zhuqueTypeface();
        if (face != null) view.setTypeface(bold ? Typeface.create(face, Typeface.BOLD) : face);
        else if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView compactText(String value, int sizeSp, int color, boolean bold) {
        TextView view = text(value, sizeSp, color, bold);
        view.setIncludeFontPadding(false);
        return view;
    }

    private Typeface zhuqueTypeface() {
        if (!appTypefaceLoaded) {
            appTypefaceLoaded = true;
            try { appTypeface = getResources().getFont(R.font.zhuque_fangsong); }
            catch (RuntimeException ignored) { appTypeface = null; }
        }
        return appTypeface;
    }

    private Button button(String value, boolean primary) {
        Button view = new Button(this);
        view.setTypeface(zhuqueTypeface());
        view.setText(value);
        view.setTextSize(21);
        view.setAllCaps(false);
        view.setMinWidth(0);
        view.setMinHeight(dp(56));
        view.setTextColor(primary ? Color.WHITE : INK);
        view.setBackground(cardBackground(primary ? ACCENT : Color.WHITE, 16));
        view.setBackgroundTintList(null);
        view.setStateListAnimator(null);
        view.setElevation(0f);
        view.setTranslationZ(0f);
        view.setPadding(dp(18), 0, dp(18), 0);
        return view;
    }

    private Button smallButton(String value) {
        Button view = button(value, false);
        view.setTextSize(17);
        view.setMinWidth(dp(52));
        view.setMinHeight(dp(56));
        view.setPadding(dp(7), 0, dp(7), 0);
        return view;
    }

    private GradientDrawable cardBackground(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private LinearLayout.LayoutParams marginTop(int valueDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(valueDp);
        return params;
    }

    private LinearLayout.LayoutParams marginEnd(int valueDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.leftMargin = dp(valueDp);
        return params;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
