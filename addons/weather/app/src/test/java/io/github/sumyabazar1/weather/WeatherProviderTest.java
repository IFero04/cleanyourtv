package io.github.sumyabazar1.weather;

import static org.junit.Assert.*;

import org.junit.Test;

public class WeatherProviderTest {
    @Test public void bothIdentityAndSignatureMustMatch() {
        assertTrue(WeatherProvider.callerAllowed("com.gathin.tvhome", 0));
        assertFalse(WeatherProvider.callerAllowed("com.gathin.tvhome", -3));
        assertFalse(WeatherProvider.callerAllowed("other.app", 0));
        assertFalse(WeatherProvider.callerAllowed("com.android.shell", 0));
        assertFalse(WeatherProvider.callerAllowed(null, 0));
    }

    @Test public void successfulReadsAreCachedForThirtyMinutes() {
        long now = 10_000_000L;
        assertFalse(WeatherProvider.refreshDue(now, now, 100, -1));
        assertFalse(WeatherProvider.refreshDue(now, now - Forecast.REFRESH_MS + 1, 100, -1));
        assertTrue(WeatherProvider.refreshDue(now, now - Forecast.REFRESH_MS, 100, -1));
    }

    @Test public void failuresBackOffWithoutSchedulingAnyWork() {
        assertTrue(WeatherProvider.refreshDue(10_000_000L, 0, 500, -1));
        assertFalse(WeatherProvider.refreshDue(10_000_000L, 0, 500 + Forecast.RETRY_MS - 1, 500));
        assertTrue(WeatherProvider.refreshDue(10_000_000L, 0, 500 + Forecast.RETRY_MS, 500));
        assertTrue(WeatherProvider.refreshDue(10_000_000L, 0, 100, 500));
    }

    @Test public void futureAndExpiredReadingsAreNotFresh() {
        assertFalse(WeatherProvider.fresh(1_000, 1_001, 600));
        assertFalse(WeatherProvider.fresh(1_000, 0, 600));
        assertFalse(WeatherProvider.fresh(1_000, 400, 600));
        assertTrue(WeatherProvider.fresh(1_000, 401, 600));
    }
}
