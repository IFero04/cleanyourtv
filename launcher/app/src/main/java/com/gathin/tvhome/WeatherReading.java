package com.gathin.tvhome;

/** Small, location-free snapshot from the optional weather source. No Android or network work. */
final class WeatherReading {
    static final long MAX_AGE_MS = 60 * 60_000L;
    static final int SUN = 0, MOON = 1, PARTLY = 2, CLOUD = 3, RAIN = 4, SNOW = 5, FOG = 6, STORM = 7;
    final double celsius;
    final int code;
    final boolean day;
    final long updatedAt;

    private WeatherReading(double celsius, int code, boolean day, long updatedAt) {
        this.celsius = celsius;
        this.code = code;
        this.day = day;
        this.updatedAt = updatedAt;
    }

    static WeatherReading from(double celsius, int code, boolean day, long updatedAt, long now) {
        if (!Double.isFinite(celsius) || celsius < -100 || celsius > 70 || icon(code, day) < 0
                || !fresh(updatedAt, now)) return null;
        return new WeatherReading(celsius, code, day, updatedAt);
    }

    static boolean fresh(long updatedAt, long now) {
        return updatedAt > 0 && now >= updatedAt && now - updatedAt < MAX_AGE_MS;
    }

    String temperature() {
        return Math.round(celsius) + "°C";
    }

    /** WMO conditions, including clear nights; an unknown code must not pretend to be sunny. */
    static int icon(int code, boolean day) {
        switch (code) {
            case 0: case 1: return day ? SUN : MOON;
            case 2: return PARTLY;
            case 3: return CLOUD;
            case 45: case 48: return FOG;
            case 51: case 53: case 55: case 56: case 57:
            case 61: case 63: case 65: case 66: case 67:
            case 80: case 81: case 82: return RAIN;
            case 71: case 73: case 75: case 77: case 85: case 86: return SNOW;
            case 95: case 96: case 99: return STORM;
            default: return -1;
        }
    }
}
