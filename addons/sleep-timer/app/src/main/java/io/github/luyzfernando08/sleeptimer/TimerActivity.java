package io.github.luyzfernando08.sleeptimer;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The card: pick when the TV goes to sleep, or cancel the timer. Choosing closes the screen. */
public class TimerActivity extends Activity {
    private TextView state, first, cancel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(0xFF0B0D12);
        int pad = Ui.dp(this, 48);
        root.setPadding(pad, pad, pad, pad);

        TextView title = Ui.text(this, 34, Color.WHITE);
        title.setText(R.string.app_name);
        root.addView(title);
        TextView hint = Ui.text(this, 16, 0xB3FFFFFF);
        hint.setText(R.string.hint);
        hint.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
        root.addView(hint);
        state = Ui.text(this, 18, 0xFFB9B2FF);
        state.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 16));
        root.addView(state);

        // Keep the preset row unchanged; optional custom entry and Cancel go underneath.
        LinearLayout lengths = new LinearLayout(this);
        lengths.setOrientation(LinearLayout.HORIZONTAL);
        lengths.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(lengths);
        for (int minutes : Timer.MINUTES) {
            TextView row = Ui.row(lengths, 150);
            if (minutes != Timer.MINUTES[Timer.MINUTES.length - 1]) {
                ((LinearLayout.LayoutParams) row.getLayoutParams()).setMarginEnd(Ui.dp(this, 12));
            }
            row.setGravity(Gravity.CENTER);
            row.setText(getString(R.string.in_minutes, minutes));
            row.setOnClickListener(v -> {
                Timer.start(this, minutes);
                finish();
            });
            if (first == null) first = row;
        }
        if (getResources().getBoolean(R.bool.custom_minutes_enabled)) {
            TextView custom = Ui.row(root, 798);
            custom.setText(R.string.custom);
            custom.setOnClickListener(v -> showCustomMinutes());
        }
        cancel = Ui.row(root, 798);
        cancel.setText(R.string.cancel);
        cancel.setOnClickListener(v -> {
            Timer.cancel(this);
            refresh();
        });

        setContentView(root);
    }

    private void showCustomMinutes() {
        String range = getString(R.string.custom_range, TimerOptions.MIN_MINUTES, TimerOptions.MAX_MINUTES);
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setHint(range);
        // Number remotes work without a keyboard covering the dialog. Do not truncate to three digits:
        // typing 7200 must be rejected, not silently accepted as a 720-minute timer.
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(10)});
        input.setShowSoftInputOnFocus(false);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 24);
        box.setPadding(pad, 0, pad, 0);
        TextView hint = Ui.text(this, 16, 0xB3FFFFFF);
        hint.setText(R.string.custom_hint);
        box.addView(hint);
        box.addView(input);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.custom_title)
                .setView(box)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.custom_start, null)
                .create();
        dialog.show();
        Runnable submit = () -> {
            int minutes = TimerOptions.parseMinutes(input.getText().toString());
            if (minutes == -1) {
                input.setError(range);
                return;
            }
            Timer.start(this, minutes);
            dialog.dismiss();
            finish();
        };
        // Override after show: invalid input must not dismiss the dialog or schedule an alarm.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> submit.run());
        dialog.setOnKeyListener((d, key, event) -> {
            int digit = remoteDigit(key);
            if (digit >= 0 || key == KeyEvent.KEYCODE_DEL) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (digit >= 0) input.append(Integer.toString(digit));
                    else if (input.length() > 0) input.getText().delete(input.length() - 1, input.length());
                    input.setError(null);
                }
                return true;
            }
            if (input.hasFocus() && (key == KeyEvent.KEYCODE_DPAD_CENTER
                    || key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) submit.run();
                return true;
            }
            return false;
        });
        input.setOnEditorActionListener((v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_DONE) return false;
            submit.run();
            return true;
        });
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).requestFocus();
    }

    private static int remoteDigit(int key) {
        if (key >= KeyEvent.KEYCODE_0 && key <= KeyEvent.KEYCODE_9) return key - KeyEvent.KEYCODE_0;
        if (key >= KeyEvent.KEYCODE_NUMPAD_0 && key <= KeyEvent.KEYCODE_NUMPAD_9) return key - KeyEvent.KEYCODE_NUMPAD_0;
        return -1;
    }

    /** Also when the screen comes back from the background, so it never shows a timer that has changed. */
    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        (Timer.end(this) != 0 ? cancel : first).requestFocus();
    }

    private void refresh() {
        long end = Timer.end(this);
        if (!Timer.canSleep()) state.setText(R.string.no_service);
        else if (end == 0) state.setText(R.string.state_off);
        else state.setText(getString(R.string.state_on, DateFormat.getTimeFormat(this).format(end)));
    }
}
