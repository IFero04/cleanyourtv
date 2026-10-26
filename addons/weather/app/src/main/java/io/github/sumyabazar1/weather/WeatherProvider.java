package io.github.sumyabazar1.weather;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;

import org.json.JSONException;

import java.io.IOException;

/** Optional, demand-driven current weather for a same-signed Home. Never exports a location. */
public final class WeatherProvider extends ContentProvider {
    private static final long MAX_AGE_MS = 60 * 60_000L;
    private long lastAttempt = -1;

    @Override public boolean onCreate() { return true; }

    static boolean callerAllowed(String caller, int signatureResult) {
        return "com.gathin.tvhome".equals(caller) && signatureResult == PackageManager.SIGNATURE_MATCH;
    }

    static boolean fresh(long now, long updated, long limit) {
        return updated > 0 && now >= updated && now - updated < limit;
    }

    static boolean refreshDue(long now, long updated, long elapsed, long attempt) {
        return !fresh(now, updated, Forecast.REFRESH_MS)
                && (attempt < 0 || elapsed < attempt || elapsed - attempt >= Forecast.RETRY_MS);
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        // ContentProvider.call does not enforce read/write permissions for the method: check the real caller.
        String caller = getCallingPackage();
        if (!callerAllowed(caller, caller == null ? PackageManager.SIGNATURE_NO_MATCH
                : getContext().getPackageManager().checkSignatures(getContext().getPackageName(), caller))) {
            throw new SecurityException("Only the same-signed Home may read current weather");
        }
        if (!"current".equals(method)) throw new IllegalArgumentException("Unknown weather method");
        // Arguments are intentionally unused: a caller cannot choose a location, URL or preference.
        return current();
    }

    private synchronized Bundle current() {
        Context context = getContext();
        SharedPreferences place = context.getSharedPreferences("place", Context.MODE_PRIVATE);
        if (!place.contains("name") || !place.contains("lat") || !place.contains("lon")) return new Bundle();
        long lat = place.getLong("lat", 0), lon = place.getLong("lon", 0);
        double latitude = Double.longBitsToDouble(lat), longitude = Double.longBitsToDouble(lon);
        if (!Forecast.validPlace(latitude, longitude)) return new Bundle();
        SharedPreferences cache = context.getSharedPreferences("home_weather", Context.MODE_PRIVATE);
        if (cache.getLong("lat", Long.MIN_VALUE) != lat || cache.getLong("lon", Long.MIN_VALUE) != lon) {
            cache.edit().clear().putLong("lat", lat).putLong("lon", lon).apply();
            lastAttempt = -1;
        }
        long now = System.currentTimeMillis(), elapsed = SystemClock.elapsedRealtime();
        if (refreshDue(now, cache.getLong("updated", 0), elapsed, lastAttempt)) {
            lastAttempt = elapsed;
            try {
                Forecast.Report report = Forecast.fetchCurrent(latitude, longitude);
                // Changing city while a request was in flight must not publish the previous place's reading.
                if (place.getLong("lat", 0) != lat || place.getLong("lon", 0) != lon) return new Bundle();
                if (Double.isFinite(report.tempC) && report.tempC >= -100 && report.tempC <= 70
                        && report.code >= 0 && report.code <= 99) {
                    cache.edit().clear().putLong("lat", lat).putLong("lon", lon)
                            .putLong("temperature", Double.doubleToRawLongBits(report.tempC))
                            .putInt("code", report.code).putBoolean("day", report.day)
                            .putLong("updated", System.currentTimeMillis()).apply();
                }
            } catch (IOException | JSONException | IllegalArgumentException ignored) {
                // Keep a recent cached reading; never log request URLs or the private place.
            }
        }
        long updated = cache.getLong("updated", 0);
        if (!fresh(System.currentTimeMillis(), updated, MAX_AGE_MS)
                || place.getLong("lat", 0) != lat || place.getLong("lon", 0) != lon) return new Bundle();
        Bundle result = new Bundle();
        result.putDouble("temperature_c", Double.longBitsToDouble(cache.getLong("temperature", 0)));
        int code = cache.getInt("code", -1);
        boolean day = cache.getBoolean("day", true);
        result.putInt("code", code);
        result.putBoolean("day", day);
        result.putLong("updated_at", updated);
        result.putString("condition", context.getString(Forecast.sky(code, day)));
        return result;
    }

    // This is not a general data store or a file-sharing provider.
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        throw new UnsupportedOperationException("Use current weather");
    }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
