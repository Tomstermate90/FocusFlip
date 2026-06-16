package com.focusflip.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Manager for user preferences using SharedPreferences.
 * Handles storing and retrieving user settings.
 */
public class PreferencesManager {

    private static final String PREFS_NAME = "focusflip_prefs";

    // Keys
    private static final String KEY_DAILY_GOAL_MINUTES = "daily_goal_minutes";
    private static final String KEY_REMINDER_HOUR = "reminder_hour";
    private static final String KEY_REMINDER_MINUTE = "reminder_minute";
    private static final String KEY_SILENCE_MODE = "silence_mode";
    private static final String KEY_FIRST_LAUNCH = "first_launch";

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

    // First Launch

    public boolean isFirstLaunch() {
        return preferences.getBoolean(KEY_FIRST_LAUNCH, true);
    }

    public void setFirstLaunchComplete() {
        preferences.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply();
    }

    /**
     * Get the daily goal in milliseconds.
     */
    public long getDailyGoalMs() {
        return getDailyGoalMinutes() * 60 * 1000L;
    }

    /**
     * Clear all preferences.
     */
    public void clearAll() {
        preferences.edit().clear().apply();
    }
}
