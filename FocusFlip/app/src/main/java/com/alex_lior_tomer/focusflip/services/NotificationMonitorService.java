package com.alex_lior_tomer.focusflip.services;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Counts notifications that arrive while a focus session is active so the
 * Statistics screen can show how many distractions the user was shielded from.
 */
public class NotificationMonitorService extends NotificationListenerService {

    private static int blockedNotificationsCount = 0;

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!FocusService.isRunning()) return;
        // Don't count our own foreground notification.
        if (sbn.getPackageName().equals(getPackageName())) return;
        blockedNotificationsCount++;
    }

    public static int getBlockedCount() {
        return blockedNotificationsCount;
    }

    public static void resetBlockedCount() {
        blockedNotificationsCount = 0;
    }
}
