package com.alex_lior_tomer.focusflip.services;

import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Service that monitors incoming notifications from third-party apps.
 * Counts notifications blocked during focus sessions for distraction metrics.
 */
public class NotificationMonitorService extends NotificationListenerService {

    private static int blockedNotificationsCount = 0;
    private static boolean isListening = false;

    @Override
    public void onCreate() {
        super.onCreate();
        isListening = true;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        // Only count if a focus session is active
        if (FocusService.isRunning()) {
            String packageName = sbn.getPackageName();
            
            // Don't count our own notifications
            if (!packageName.equals(getPackageName())) {
                blockedNotificationsCount++;
                
                // Broadcast notification received event
                Intent intent = new Intent("com.alex_lior_tomer.focusflip.NOTIFICATION_BLOCKED");
                intent.putExtra("package", packageName);
                intent.putExtra("count", blockedNotificationsCount);
                sendBroadcast(intent);
            }
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Not needed for our use case
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        isListening = true;
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        isListening = false;
    }

    /**
     * Get the count of notifications blocked during the current session.
     */
    public static int getBlockedCount() {
        return blockedNotificationsCount;
    }

    /**
     * Reset the blocked notification counter.
     * Should be called when a focus session ends.
     */
    public static void resetBlockedCount() {
        blockedNotificationsCount = 0;
    }

    /**
     * Check if the notification listener is currently active.
     */
    public static boolean isListeningActive() {
        return isListening;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isListening = false;
    }
}
