package com.focusflip.app.receivers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.focusflip.app.utils.PreferencesManager;

import java.util.Calendar;

/**
 * Broadcast receiver that handles device boot.
 * Re-schedules the daily goal check alarm after device restart.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        // Re-schedule the daily goal check alarm
        rescheduleGoalCheckAlarm(context);
    }

    private void rescheduleGoalCheckAlarm(Context context) {
        PreferencesManager preferencesManager = new PreferencesManager(context);
        
        int reminderHour = preferencesManager.getReminderHour();
        int reminderMinute = preferencesManager.getReminderMinute();

        // Only schedule if user has set a reminder time
        if (reminderHour < 0) return;

        AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        
        Intent alarmIntent = new Intent(context, GoalCheckReceiver.class);
        alarmIntent.setAction(GoalCheckReceiver.ACTION_CHECK_GOAL);
        
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                GoalCheckReceiver.REQUEST_CODE,
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Set alarm time
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, reminderHour);
        calendar.set(Calendar.MINUTE, reminderMinute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        // If time has already passed today, schedule for tomorrow
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        // Schedule repeating alarm
        alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY,
                pendingIntent
        );
    }
}
