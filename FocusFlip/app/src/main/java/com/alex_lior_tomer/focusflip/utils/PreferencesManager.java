package com.alex_lior_tomer.focusflip.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * SharedPreferences wrapper. Stores user settings and a small snapshot cache
 * of stats so the UI can paint last-known values immediately on launch, then
 * overwrite them when the async DB query returns.
 */
public class PreferencesManager {

    private static final String PREFS_NAME = "focusflip_prefs";

    // Settings keys
    private static final String KEY_DAILY_GOAL_MINUTES = "daily_goal_minutes";
    private static final String KEY_REMINDER_HOUR = "reminder_hour";
    private static final String KEY_REMINDER_MINUTE = "reminder_minute";
    private static final String KEY_SILENCE_MODE = "silence_mode";

    // Stats cache keys (painted at first frame, refreshed from DB)
    private static final String KEY_CACHE_TODAY_DATE = "cache_today_date";
    private static final String KEY_CACHE_TODAY_MS = "cache_today_ms";
    private static final String KEY_CACHE_TODAY_DISTRACTIONS = "cache_today_distractions";
    private static final String KEY_CACHE_TOTAL_MS = "cache_total_ms";
    private static final String KEY_CACHE_GOALS_ACHIEVED = "cache_goals_achieved";
    private static final String KEY_CACHE_TOTAL_NOTIFS = "cache_total_notifs";
    private static final String KEY_CACHE_TOTAL_DISTRACTIONS = "cache_total_distractions";
    private static final String KEY_CACHE_AVG_SESSION_MS = "cache_avg_session_ms";

    // Silence mode constants
    public static final int SILENCE_ALL = 0;
    public static final int SILENCE_NOTIFICATIONS = 1;
    public static final int VIBRATE_ONLY = 2;

    // Default values
    private static final int DEFAULT_DAILY_GOAL = 60; // 60 minutes
    private static final int DEFAULT_REMINDER_HOUR = 20; // 8 PM
    private static final int DEFAULT_REMINDER_MINUTE = 0;
    private static final int DEFAULT_SILENCE_MODE = SILENCE_NOTIFICATIONS;

    private final SharedPreferences preferences;

    public PreferencesManager(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // Daily Goal

    public int getDailyGoalMinutes() {
        return preferences.getInt(KEY_DAILY_GOAL_MINUTES, DEFAULT_DAILY_GOAL);
    }

    public void setDailyGoalMinutes(int minutes) {
        preferences.edit().putInt(KEY_DAILY_GOAL_MINUTES, minutes).apply();
    }

    // Reminder Time

    public int getReminderHour() {
        return preferences.getInt(KEY_REMINDER_HOUR, DEFAULT_REMINDER_HOUR);
    }

    public int getReminderMinute() {
        return preferences.getInt(KEY_REMINDER_MINUTE, DEFAULT_REMINDER_MINUTE);
    }

    public void setReminderTime(int hour, int minute) {
        preferences.edit()
                .putInt(KEY_REMINDER_HOUR, hour)
                .putInt(KEY_REMINDER_MINUTE, minute)
                .apply();
    }

    // Silence Mode

    public int getSilenceMode() {
        return preferences.getInt(KEY_SILENCE_MODE, DEFAULT_SILENCE_MODE);
    }

    public void setSilenceMode(int mode) {
        preferences.edit().putInt(KEY_SILENCE_MODE, mode).apply();
    }

    /** Daily goal expressed in milliseconds. */
    public long getDailyGoalMs() {
        return getDailyGoalMinutes() * 60 * 1000L;
    }

    // Stats snapshot cache. Today's values are date-stamped so a stale day
    // (e.g. user reopens the next morning) is ignored rather than painted.

    private static String todayString() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    public void cacheTodayStats(long todayMs, int todayDistractions) {
        preferences.edit()
                .putString(KEY_CACHE_TODAY_DATE, todayString())
                .putLong(KEY_CACHE_TODAY_MS, todayMs)
                .putInt(KEY_CACHE_TODAY_DISTRACTIONS, todayDistractions)
                .apply();
    }

    /** Returns -1 when the cached value is from a previous day. */
    public long getCachedTodayMs() {
        if (!todayString().equals(preferences.getString(KEY_CACHE_TODAY_DATE, ""))) return -1;
        return preferences.getLong(KEY_CACHE_TODAY_MS, -1);
    }

    /** Returns -1 when the cached value is from a previous day. */
    public int getCachedTodayDistractions() {
        if (!todayString().equals(preferences.getString(KEY_CACHE_TODAY_DATE, ""))) return -1;
        return preferences.getInt(KEY_CACHE_TODAY_DISTRACTIONS, -1);
    }

    public void cacheLifetimeStats(long totalMs, int goalsAchieved,
                                   int totalNotifs, int totalDistractions,
                                   long avgSessionMs) {
        preferences.edit()
                .putLong(KEY_CACHE_TOTAL_MS, totalMs)
                .putInt(KEY_CACHE_GOALS_ACHIEVED, goalsAchieved)
                .putInt(KEY_CACHE_TOTAL_NOTIFS, totalNotifs)
                .putInt(KEY_CACHE_TOTAL_DISTRACTIONS, totalDistractions)
                .putLong(KEY_CACHE_AVG_SESSION_MS, avgSessionMs)
                .apply();
    }

    public long getCachedTotalMs()           { return preferences.getLong(KEY_CACHE_TOTAL_MS, -1); }
    public int  getCachedGoalsAchieved()     { return preferences.getInt(KEY_CACHE_GOALS_ACHIEVED, -1); }
    public int  getCachedTotalNotifs()       { return preferences.getInt(KEY_CACHE_TOTAL_NOTIFS, -1); }
    public int  getCachedTotalDistractions() { return preferences.getInt(KEY_CACHE_TOTAL_DISTRACTIONS, -1); }
    public long getCachedAvgSessionMs()      { return preferences.getLong(KEY_CACHE_AVG_SESSION_MS, -1); }
}
