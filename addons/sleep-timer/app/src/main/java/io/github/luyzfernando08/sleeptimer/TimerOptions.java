package io.github.luyzfernando08.sleeptimer;

/** Input rules shared by the picker and warning, independent of Android for testing. */
final class TimerOptions {
    static final int MIN_MINUTES = 1;
    static final int MAX_MINUTES = 720;
    private static final int DEFAULT_EXTENSION = 15;

    private TimerOptions() { }

    /** -1 means invalid: empty, non-digit, overflowing and out-of-range input never starts a timer. */
    static int parseMinutes(String text) {
        if (text == null) return -1;
        String value = text.trim();
        if (value.isEmpty()) return -1;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < '0' || value.charAt(i) > '9') return -1;
        }
        try {
            int minutes = Integer.parseInt(value);
            return inRange(minutes) ? minutes : -1;
        } catch (NumberFormatException overflow) {
            return -1;
        }
    }

    static int extensionMinutes(int configured) {
        return inRange(configured) ? configured : DEFAULT_EXTENSION;
    }

    private static boolean inRange(int minutes) {
        return minutes >= MIN_MINUTES && minutes <= MAX_MINUTES;
    }
}
