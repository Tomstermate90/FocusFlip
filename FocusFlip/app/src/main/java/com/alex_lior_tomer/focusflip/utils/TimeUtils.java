package com.alex_lior_tomer.focusflip.utils;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for time formatting operations.
 */
public class TimeUtils {

    /**
     * Format duration in milliseconds to HH:MM:SS format.
     */
    public static String formatDuration(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }
    }

    /**
     * Format duration in milliseconds to a long format (e.g., "2 שעות 30 דקות").
     */
    public static String formatDurationLong(long millis, android.content.Context context) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;

        if (hours > 0 && minutes > 0) {
            return context.getString(com.alex_lior_tomer.focusflip.R.string.hours_minutes_format, hours, minutes);
        } else if (hours > 0) {
            return context.getString(com.alex_lior_tomer.focusflip.R.string.hours_format, hours);
        } else if (minutes > 0) {
            return context.getString(com.alex_lior_tomer.focusflip.R.string.minutes_format, minutes);
        } else {
            return context.getString(com.alex_lior_tomer.focusflip.R.string.less_than_minute);
        }
    }

    /**
     * Format minutes to a readable string.
     */
    public static String formatMinutes(int minutes) {
        if (minutes >= 60) {
            int hours = minutes / 60;
            int mins = minutes % 60;
            if (mins > 0) {
                return String.format(Locale.getDefault(), "%d:%02d", hours, mins);
            } else {
                return String.format(Locale.getDefault(), "%dh", hours);
            }
        } else {
            return String.format(Locale.getDefault(), "%dm", minutes);
        }
    }

    /**
     * Format minutes to a long readable string in Hebrew.
     */
    public static String formatMinutesLong(int minutes, android.content.Context context) {
        if (minutes >= 60) {
            int hours = minutes / 60;
            int mins = minutes % 60;
            if (mins > 0) {
                return context.getString(com.alex_lior_tomer.focusflip.R.string.hours_minutes_format, hours, mins);
            } else {
                return context.getString(com.alex_lior_tomer.focusflip.R.string.hours_format, hours);
            }
        } else {
            return context.getString(com.alex_lior_tomer.focusflip.R.string.minutes_format, minutes);
        }
    }

    /**
     * Get progress percentage.
     */
    public static int getProgressPercentage(long current, long goal) {
        if (goal <= 0) return 0;
        int progress = (int) ((current * 100) / goal);
        return Math.min(progress, 100);
    }

    /**
     * Check if the current time is within a range.
     */
    public static boolean isTimeInRange(int currentHour, int currentMinute,
                                         int startHour, int startMinute,
                                         int endHour, int endMinute) {
        int currentTime = currentHour * 60 + currentMinute;
        int startTime = startHour * 60 + startMinute;
        int endTime = endHour * 60 + endMinute;

        if (startTime <= endTime) {
            return currentTime >= startTime && currentTime <= endTime;
        } else {
            // Range crosses midnight
            return currentTime >= startTime || currentTime <= endTime;
        }
    }
}
