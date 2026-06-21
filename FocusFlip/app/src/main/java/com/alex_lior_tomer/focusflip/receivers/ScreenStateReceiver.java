package com.alex_lior_tomer.focusflip.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.alex_lior_tomer.focusflip.services.FocusService;

/**
 * Broadcast receiver that monitors screen on/off events.
 * Used to detect potential distractions when the user turns on the screen during a focus session.
 */
public class ScreenStateReceiver extends BroadcastReceiver {

    public static final String ACTION_SCREEN_STATE_CHANGED = "com.focusflip.SCREEN_STATE_CHANGED";
    public static final String EXTRA_SCREEN_ON = "screen_on";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        String action = intent.getAction();
        
        if (Intent.ACTION_SCREEN_ON.equals(action)) {
            onScreenOn(context);
        } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
            onScreenOff(context);
        }
    }

    private void onScreenOn(Context context) {
        // Screen turned on - potential distraction
        if (FocusService.isRunning()) {
            // Broadcast screen state change
            Intent broadcastIntent = new Intent(ACTION_SCREEN_STATE_CHANGED);
            broadcastIntent.putExtra(EXTRA_SCREEN_ON, true);
            context.sendBroadcast(broadcastIntent);
        }
    }

    private void onScreenOff(Context context) {
        // Screen turned off - user might be returning to focus
        if (FocusService.isRunning()) {
            Intent broadcastIntent = new Intent(ACTION_SCREEN_STATE_CHANGED);
            broadcastIntent.putExtra(EXTRA_SCREEN_ON, false);
            context.sendBroadcast(broadcastIntent);
        }
    }
}
