package io.github.luyzfernando08.sleeptimer;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The few views both screens share: rounded rows that light up when focused. */
final class Ui {
    private Ui() { }

    static TextView text(Context ctx, int sp, int color) {
        TextView view = new TextView(ctx);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        return view;
    }

    static TextView row(LinearLayout parent, int widthDp) {
        Context ctx = parent.getContext();
        TextView row = text(ctx, 20, Color.WHITE);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(ctx, 24), dp(ctx, 14), dp(ctx, 24), dp(ctx, 14));
        row.setFocusable(true);
        row.setClickable(true);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, pill(ctx, 0x40FFFFFF));
        bg.addState(new int[]{}, pill(ctx, 0x14FFFFFF));
        row.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(ctx, widthDp),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(ctx, 10);
        parent.addView(row, lp);
        return row;
    }

    /** A destructive action stays red and gains a bright outline when the D-pad focuses it. */
    static TextView dangerRow(LinearLayout parent, int widthDp) {
        TextView row = row(parent, widthDp);
        Context ctx = parent.getContext();
        GradientDrawable focused = pill(ctx, 0xFFB91C1C);
        focused.setStroke(dp(ctx, 2), 0xFFFECACA);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, focused);
        bg.addState(new int[]{}, pill(ctx, 0xFF7F1D1D));
        row.setBackground(bg);
        return row;
    }

    static GradientDrawable pill(Context ctx, int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(ctx, 28));
        return d;
    }

    static int dp(Context ctx, int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }
}
