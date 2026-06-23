package com.alex_lior_tomer.focusflip.receivers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.alex_lior_tomer.focusflip.utils.PreferencesManager;

import java.util.Calendar;

/** Re-arms the daily goal-check alarm after the device reboots. */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        rescheduleGoalCheckAlarm(context);
    }

    private void rescheduleGoalCheckAlarm(Context context) {
        PreferencesManager prefs = new PreferencesManager(context);
        int reminderHour = prefs.getReminderHour();
        int reminderMinute = prefs.getReminderMinute();

        AlarmManager alarmManager = context.getSystemService(AlarmManager.class);

        Intent alarmIntent = new Intent(context, GoalCheckReceiver.class);
        alarmIntent.setAction(GoalCheckReceiver.ACTION_CHECK_GOAL);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                GoalCheckReceiver.REQUEST_CODE,
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, reminderHour);
        calendar.set(Calendar.MINUTE, reminderMinute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY,
                pendingIntent);
    }
}
