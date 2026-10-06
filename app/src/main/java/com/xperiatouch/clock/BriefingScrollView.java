package com.xperiatouch.clock;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

import java.util.List;

/** A continuous news list whose initial viewport ends between complete articles. */
public final class BriefingScrollView extends ScrollView {
    private final NewsColumn column;

    public BriefingScrollView(Context context) {
        super(context);
        setFillViewport(true);
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setContentDescription("简报");
        column = new NewsColumn(context);
        addView(column, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    public void setItems(List<View> items) {
        column.removeAllViews();
        for (View row : items) column.addView(row);
        column.requestLayout();
    }

    public void setGravity(int gravity) {
        column.gravity = gravity;
        column.requestLayout();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        // Weighted parents probe unconstrained sizes before assigning the real height.
        int viewport = MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED ? 0
                : Math.max(0, MeasureSpec.getSize(heightSpec) - getPaddingTop() - getPaddingBottom());
        if (column.viewportHeight != viewport) {
            column.viewportHeight = viewport;
            // ScrollView measures its child with an unconstrained height each time.
            // The spec can stay identical even though the available viewport changed.
            column.forceLayout();
        }
        super.onMeasure(widthSpec, heightSpec);
    }

    private static final class NewsColumn extends ViewGroup {
        private int viewportHeight;
        private int gravity = Gravity.TOP;
        private int spacing;
        private int remainder;
        private int fittedRows;
        private int contentHeight;

        NewsColumn(Context context) { super(context); }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int rowWidthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
            int naturalHeight = 0, firstScreenHeight = 0;
            fittedRows = 0;
            boolean firstScreenFull = false;
            for (int i = 0; i < getChildCount(); i++) {
                View row = getChildAt(i);
                row.measure(rowWidthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                int rowHeight = row.getMeasuredHeight();
                naturalHeight += rowHeight;
                if (!firstScreenFull && firstScreenHeight + rowHeight <= viewportHeight) {
                    firstScreenHeight += rowHeight;
                    fittedRows++;
                } else firstScreenFull = true;
            }
            spacing = remainder = 0;
            if (fittedRows > 0 && fittedRows < getChildCount()) {
                int remaining = viewportHeight - firstScreenHeight;
                spacing = remaining / fittedRows;
                remainder = remaining % fittedRows;
            }
            contentHeight = naturalHeight;
            for (int i = 0; i < getChildCount(); i++) contentHeight += gapAfter(i);
            setMeasuredDimension(width, resolveSize(Math.max(contentHeight, viewportHeight), heightSpec));
        }

        @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int y = 0;
            if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.CENTER_VERTICAL) {
                y = Math.max(0, (getHeight() - contentHeight) / 2);
            }
            for (int i = 0; i < getChildCount(); i++) {
                View row = getChildAt(i);
                row.layout(0, y, getWidth(), y + row.getMeasuredHeight());
                y += row.getMeasuredHeight() + gapAfter(i);
            }
        }

        private int gapAfter(int index) {
            return fittedRows == 0 ? 0 : spacing + (index % fittedRows < remainder ? 1 : 0);
        }
    }
}
