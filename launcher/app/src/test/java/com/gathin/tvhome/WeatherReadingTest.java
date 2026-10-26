package com.gathin.tvhome;

import static org.junit.Assert.*;

import org.junit.Test;

public class WeatherReadingTest {
    private static final long NOW = 10_000_000L;

    @Test public void clearSkyHasSunByDayAndMoonByNight() {
        for (int code : new int[]{0, 1}) {
            assertEquals(WeatherReading.SUN, WeatherReading.icon(code, true));
            assertEquals(WeatherReading.MOON, WeatherReading.icon(code, false));
        }
        assertEquals(WeatherReading.PARTLY, WeatherReading.icon(2, true));
        assertEquals(WeatherReading.CLOUD, WeatherReading.icon(3, false));
    }

    @Test public void rainSnowFogAndStormHaveTheirOwnIcons() {
        for (int code : new int[]{51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82})
            assertEquals(WeatherReading.RAIN, WeatherReading.icon(code, true));
        for (int code : new int[]{71, 73, 75, 77, 85, 86})
            assertEquals(WeatherReading.SNOW, WeatherReading.icon(code, false));
        for (int code : new int[]{45, 48}) assertEquals(WeatherReading.FOG, WeatherReading.icon(code, true));
        for (int code : new int[]{95, 96, 99}) assertEquals(WeatherReading.STORM, WeatherReading.icon(code, false));
    }

    @Test public void unknownConditionsAreNotDisplayedAsFairWeather() {
        for (int code : new int[]{-1, 4, 42, 60, 90, 100, Integer.MAX_VALUE})
            assertNull(WeatherReading.from(18, code, true, NOW, NOW));
    }

    @Test public void oldFutureAndMissingTimestampsAreRejected() {
        assertNotNull(WeatherReading.from(18, 0, true, NOW, NOW));
        assertNotNull(WeatherReading.from(18, 0, true, NOW - WeatherReading.MAX_AGE_MS + 1, NOW));
        assertNull(WeatherReading.from(18, 0, true, NOW - WeatherReading.MAX_AGE_MS, NOW));
        assertNull(WeatherReading.from(18, 0, true, NOW + 1, NOW));
        assertNull(WeatherReading.from(18, 0, true, 0, NOW));
        assertNull(WeatherReading.from(18, 0, true, Long.MIN_VALUE, NOW));
    }

    @Test public void invalidTemperaturesCannotReachTheHeader() {
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -101, 71})
            assertNull(WeatherReading.from(value, 0, true, NOW, NOW));
        assertNotNull(WeatherReading.from(-100, 0, false, NOW, NOW));
        assertNotNull(WeatherReading.from(70, 0, true, NOW, NOW));
    }

    @Test public void degreesMatchTheWeatherAppAndHandleNegativeZero() {
        assertEquals("18°C", WeatherReading.from(17.6, 0, true, NOW, NOW).temperature());
        assertEquals("-3°C", WeatherReading.from(-2.6, 3, false, NOW, NOW).temperature());
        assertEquals("0°C", WeatherReading.from(-0.4, 3, false, NOW, NOW).temperature());
    }
}
