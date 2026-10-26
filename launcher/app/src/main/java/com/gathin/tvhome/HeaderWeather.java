package com.gathin.tvhome;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Optional local IPC on returning to Home. No sockets, polling, receiver, service or stored location. */
final class HeaderWeather implements AutoCloseable {
    private static final String SOURCE = "io.github.sumyabazar1.weather";
    private static final String AUTHORITY = SOURCE + ".current";
    private final Context context;
    private final TextView label;
    private final View divider;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private WeatherReading reading;
    private String condition = "";
    private boolean active, pending, closed;

    HeaderWeather(Context context, TextView label, View divider) {
        this.context = context.getApplicationContext();
        this.label = label;
        this.divider = divider;
    }

    void resume() {
        active = true;
        render();
        if (pending || closed) return;
        pending = true;
        io.execute(() -> {
            Bundle result = null;
            try {
                ProviderInfo provider = context.getPackageManager().resolveContentProvider(AUTHORITY, 0);
                if (provider != null && provider.enabled && provider.exported && SOURCE.equals(provider.packageName)
                        && context.getPackageManager().checkSignatures(context.getPackageName(), SOURCE)
                        == PackageManager.SIGNATURE_MATCH) {
                    result = context.getContentResolver().call(Uri.parse("content://" + AUTHORITY), "current", null, null);
                }
            } catch (RuntimeException ignored) {
                // Missing, old, disabled or rejected source must not prevent Home opening.
            }
            Bundle snapshot = result;
            main.post(() -> {
                pending = false;
                if (closed) return;
                reading = null;
                condition = "";
                try {
                    if (snapshot != null && snapshot.containsKey("day")) {
                        reading = WeatherReading.from(snapshot.getDouble("temperature_c", Double.NaN),
                                snapshot.getInt("code", -1), snapshot.getBoolean("day"),
                                snapshot.getLong("updated_at", 0), System.currentTimeMillis());
                        String description = snapshot.getString("condition", "");
                        if (description.length() <= 80) condition = description;
                    }
                } catch (RuntimeException ignored) { reading = null; }
                if (active) render();
            });
        });
    }

    void pause() { active = false; }

    /** Called by the clock's existing minute tick: only expires local data, never contacts the source. */
    void expire() {
        if (reading != null && !WeatherReading.fresh(reading.updatedAt, System.currentTimeMillis())) {
            reading = null;
            if (active) render();
        }
    }

    private void render() {
        if (reading == null || !WeatherReading.fresh(reading.updatedAt, System.currentTimeMillis())) {
            label.setVisibility(View.GONE);
            divider.setVisibility(View.GONE);
            return;
        }
        label.setText(reading.temperature());
        label.setContentDescription(reading.temperature() + (condition.isEmpty() ? "" : ", " + condition));
        WeatherIcon icon = new WeatherIcon(WeatherReading.icon(reading.code, reading.day), reading.day,
                label.getCurrentTextColor());
        int size = Math.round(22 * label.getResources().getDisplayMetrics().density);
        icon.setBounds(0, 0, size, size);
        label.setCompoundDrawablesRelative(icon, null, null, null);
        label.setVisibility(View.VISIBLE);
        divider.setVisibility(View.VISIBLE);
    }

    @Override public void close() {
        closed = true;
        active = false;
        io.shutdownNow();
        main.removeCallbacksAndMessages(null);
    }
}
