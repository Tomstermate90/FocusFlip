package com.alex_lior_tomer.focusflip.receivers;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import com.alex_lior_tomer.focusflip.R;
import com.alex_lior_tomer.focusflip.activities.MainActivity;
import com.alex_lior_tomer.focusflip.database.StudyDatabase;
import com.alex_lior_tomer.focusflip.utils.PreferencesManager;
import com.alex_lior_tomer.focusflip.utils.TimeUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fires at the user's daily reminder time. Reads today's accumulated study
 * time, then either congratulates the user or shows how much is left.
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

        Context appContext = context.getApplicationContext();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            checkGoalAndNotify(appContext);
            executor.shutdown();
        });
    }

    private void checkGoalAndNotify(Context context) {
        long todayTotalTime = StudyDatabase.getInstance(context).studySessionDao().getTodayTotalTime();
        long dailyGoalMs = new PreferencesManager(context).getDailyGoalMs();

        createNotificationChannel(context);

        if (todayTotalTime >= dailyGoalMs) {
            sendGoalAchievedNotification(context);
        } else {
            sendGoalReminderNotification(context, dailyGoalMs - todayTotalTime);
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

        String remainingTimeStr = TimeUtils.formatDurationLong(remainingTime, context);
        
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
