package com.xperiatouch.clock;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Checks the first viewport with native wrapping, then ordinary continuous scrolling. */
final class BriefingLayoutChecks {
    static void run(Context context) {
        Typeface font = context.getResources().getFont(R.font.zhuque_fangsong);
        for (float scale : new float[] {1f, 1.35f}) {
            List<View> rows = new ArrayList<>();
            List<TextView> titles = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                String title = i % 2 == 0 ? "新消息" :
                        "新品发布与技术动态：让长标题在不同宽度下自动换行，确保最后一条新闻的文字和来源都完整显示。";
                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(context, 2), dp(context, 9), dp(context, 2), dp(context, 9));
                TextView source = new TextView(context);
                source.setText("科技资讯 · 查看详情");
                source.setTypeface(font);
                source.setTextSize(11.8f * scale);
                row.addView(source);
                TextView headline = new TextView(context);
                headline.setTypeface(font);
                headline.setTextSize(18.88f * scale);
                headline.setText(title);
                headline.setMaxLines(2);
                headline.setEllipsize(android.text.TextUtils.TruncateAt.END);
                headline.setLineSpacing(dp(context, 2), 1f);
                row.addView(headline);
                rows.add(row);
                titles.add(headline);
            }
            BriefingScrollView list = new BriefingScrollView(context);
            list.setPadding(0, dp(context, 6), 0, dp(context, 4));
            list.setItems(rows);
            for (int[] size : new int[][] {{455, 604}, {320, 420}, {600, 630}}) {
                list.scrollTo(0, 0);
                layout(list, size[0], size[1]);
                require(titles.get(0).getLineCount() == 1, "Short title did not use one line");
                require(titles.get(1).getLineCount() == 2, "Long title did not wrap into two lines");
                require(rows.get(1).getMeasuredHeight() > rows.get(0).getMeasuredHeight(),
                        "Wrapping was not included in the measured row height");
                checkFirstScreen(list, rows);
                list.scrollTo(0, 37);
                require(list.getScrollY() == 37, "List snaps to a page instead of scrolling continuously");
                list.scrollTo(0, list.getChildAt(0).getHeight());
                int viewport = size[1] - list.getPaddingTop() - list.getPaddingBottom();
                require(rows.get(rows.size() - 1).getBottom() - list.getScrollY() <= viewport,
                        "Last article cannot be scrolled fully into view");
                require(rows.get(rows.size() - 1).getTop() - list.getScrollY() >= 0,
                        "Last article is clipped at the top when scrolling to the end");
            }
            // An intermediate wide/unconstrained parent probe must not keep stale spacing.
            list.scrollTo(0, 0);
            list.measure(View.MeasureSpec.makeMeasureSpec(1400, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            layout(list, 455, 604);
            checkFirstScreen(list, rows);
            list.scrollTo(0, 37);
            list.requestLayout();
            layout(list, 455, 604);
            require(list.getScrollY() == 37, "Remeasurement reset the reading position");
            list.setItems(Collections.singletonList(rows.get(0)));
            layout(list, 455, 604);
            require(list.getScrollY() == 0, "A shorter refresh left the list scrolled out of view");
            require(rows.get(0).getTop() == 0, "A short list retained the stretched spacing");
            list.setItems(Collections.emptyList());
            layout(list, 455, 604);
            require(((ViewGroup) list.getChildAt(0)).getChildCount() == 0, "Empty briefing retained old rows");
        }
        checkBoundaryAndFewArticles(context);
    }

    private static void checkFirstScreen(BriefingScrollView list, List<View> rows) {
        require(list.getChildCount() == 1, "List retained separate pagination controls");
        require(((ViewGroup) list.getChildAt(0)).getChildCount() == rows.size(), "A news article was lost");
        int viewport = list.getHeight() - list.getPaddingTop() - list.getPaddingBottom();
        int visible = 0, naturalHeight = 0;
        int previousBottom = 0;
        List<Integer> gaps = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            View row = rows.get(i);
            require(row.getVisibility() == View.VISIBLE, "An article was hidden as a separate page");
            require(row.getTop() >= previousBottom, "News rows overlap");
            require(row.getHeight() == row.getMeasuredHeight(), "A row was compressed or clipped");
            if (row.getTop() < viewport) {
                require(row.getBottom() <= viewport, "First screen exposes only half a news row");
                visible++;
                naturalHeight += row.getMeasuredHeight();
            } else if (i == visible) {
                require(row.getTop() == viewport, "Next news row does not start at the viewport boundary");
                require(naturalHeight + row.getMeasuredHeight() > viewport,
                        "The first screen could fit another complete article");
            }
            if (i > 0) gaps.add(row.getTop() - previousBottom);
            previousBottom = row.getBottom();
        }
        require(visible > 0, "First screen is empty");
        require(Collections.max(gaps) - Collections.min(gaps) <= 1, "News spacing is uneven");
    }

    private static void checkBoundaryAndFewArticles(Context context) {
        BriefingScrollView list = new BriefingScrollView(context);
        List<View> rows = new ArrayList<>();
        for (int rowHeight : new int[] {60, 95, 60, 30, 80}) {
            View row = new View(context) {
                @Override protected void onMeasure(int w, int h) {
                    setMeasuredDimension(MeasureSpec.getSize(w), rowHeight);
                }
            };
            rows.add(row);
        }
        list.setItems(rows);
        layout(list, 455, 215);
        checkFirstScreen(list, rows);
        require(rows.get(2).getBottom() == 215, "Exact-fit article was unnecessarily moved");
        layout(list, 455, 230);
        checkFirstScreen(list, rows);
        require(rows.get(3).getTop() == 230, "Remaining viewport space was not distributed");
        layout(list, 455, 350);
        require(rows.get(4).getBottom() == 325, "A few articles were stretched to fill the screen");
        list.scrollTo(0, 37);
        require(list.getScrollY() == 0, "A fully visible list can scroll into empty space");
    }

    private static void layout(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
