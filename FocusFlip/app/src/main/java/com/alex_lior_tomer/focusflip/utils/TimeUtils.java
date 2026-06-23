package com.alex_lior_tomer.focusflip.utils;

import android.content.Context;

import com.alex_lior_tomer.focusflip.R;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Formatters for the timer display and the stats tiles. */
public class TimeUtils {

    /** "HH:MM:SS" once we cross an hour, otherwise "MM:SS". */
    public static String formatDuration(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;

        if (hours > 0) {
            return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    /** Long-form for the stats screen, e.g. "2 Hours 30 Minutes". */
    public static String formatDurationLong(long millis, Context context) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;

        if (hours > 0 && minutes > 0) {
            return context.getString(R.string.hours_minutes_format, hours, minutes);
        } else if (hours > 0) {
            return context.getString(R.string.hours_format, hours);
        } else if (minutes > 0) {
            return context.getString(R.string.minutes_format, minutes);
        }
        return context.getString(R.string.less_than_minute);
    }

    /** Short form used inside the foreground notification: "90m", "2h", "2:30". */
    public static String formatMinutes(int minutes) {
        if (minutes >= 60) {
            int hours = minutes / 60;
            int mins = minutes % 60;
            if (mins > 0) {
                return String.format(Locale.US, "%d:%02d", hours, mins);
            }
            return String.format(Locale.US, "%dh", hours);
        }
        return String.format(Locale.US, "%dm", minutes);
    }
}
