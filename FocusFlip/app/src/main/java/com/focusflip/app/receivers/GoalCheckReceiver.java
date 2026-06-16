package com.focusflip.app.receivers;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import com.focusflip.app.R;
import com.focusflip.app.activities.MainActivity;
import com.focusflip.app.database.StudyDatabase;
import com.focusflip.app.utils.PreferencesManager;
import com.focusflip.app.utils.TimeUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Broadcast receiver triggered by AlarmManager at the user-defined reminder time.
 * Checks if the daily study goal has been achieved and sends a notification if not.
 */
public class GoalCheckReceiver extends BroadcastReceiver {

    public static final String ACTION_CHECK_GOAL = "com.focusflip.ACTION_CHECK_GOAL";
    public static final int REQUEST_CODE = 1001;
    
    private static final String GOAL_CHANNEL_ID = "goal_reminder_channel";
    private static final int GOAL_NOTIFICATION_ID = 2;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_CHECK_GOAL.equals(intent.getAction())) {
            return;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            checkGoalAndNotify(context);
            executor.shutdown();
        });
    }

    private void checkGoalAndNotify(Context context) {
        StudyDatabase database = StudyDatabase.getInstance(context);
        PreferencesManager preferencesManager = new PreferencesManager(context);
        
        // Get today's total study time
        long todayTotalTime = database.studySessionDao().getTodayTotalTime();
        
        // Get daily goal
        int dailyGoalMinutes = preferencesManager.getDailyGoalMinutes();
        long dailyGoalMs = dailyGoalMinutes * 60 * 1000L;
        
        // Create notification channel
        createNotificationChannel(context);
        
        if (todayTotalTime >= dailyGoalMs) {
            // Goal achieved - send congratulations notification
            sendGoalAchievedNotification(context);
        } else {
            // Goal not achieved - send reminder notification
            long remainingTime = dailyGoalMs - todayTotalTime;
            sendGoalReminderNotification(context, remainingTime);
        }
    }

    private void createNotificationChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(
                GOAL_CHANNEL_ID,
                context.getString(R.string.goal_channel_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription(context.getString(R.string.goal_channel_description));
        channel.enableVibration(true);
        channel.enableLights(true);

        NotificationManager notificationManager = 
                context.getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }

    private void sendGoalReminderNotification(Context context, long remainingTime) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        String remainingTimeStr = TimeUtils.formatDuration(remainingTime);
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, GOAL_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.goal_reminder_title))
                .setContentText(context.getString(R.string.goal_reminder_text, remainingTimeStr))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setColor(context.getResources().getColor(R.color.warning, null))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(context.getString(R.string.goal_reminder_text, remainingTimeStr)));

        NotificationManager notificationManager = 
                context.getSystemService(NotificationManager.class);
        notificationManager.notify(GOAL_NOTIFICATION_ID, builder.build());
    }

    private void sendGoalAchievedNotification(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, GOAL_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_goal)
                .setContentTitle(context.getString(R.string.goal_achieved_title))
                .setContentText(context.getString(R.string.goal_achieved_text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setColor(context.getResources().getColor(R.color.success, null));

        NotificationManager notificationManager = 
                context.getSystemService(NotificationManager.class);
        notificationManager.notify(GOAL_NOTIFICATION_ID, builder.build());
    }
}
