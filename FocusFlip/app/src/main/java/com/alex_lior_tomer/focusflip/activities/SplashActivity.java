package com.alex_lior_tomer.focusflip.activities;

import android.app.NotificationManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.alex_lior_tomer.focusflip.R;

/**
 * Launcher screen. Waits a beat to show the logo, then checks the two runtime
 * permissions the app cannot live without (DND policy access and Notification
 * Listener access). If anything is missing the user is parked here behind a
 * "Grant Permissions" button until both are granted.
 */
public class SplashActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private TextView statusText;
    private Button permissionButton;

    private boolean dndPermissionGranted = false;
    private boolean notificationListenerGranted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        initViews();
        startPermissionCheck();
    }

    private void initViews() {
        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);
        permissionButton = findViewById(R.id.permissionButton);

        permissionButton.setOnClickListener(v -> requestMissingPermissions());
    }

    private void startPermissionCheck() {
        statusText.setText(R.string.checking_permissions);
        progressBar.setVisibility(View.VISIBLE);
        permissionButton.setVisibility(View.GONE);

        new Handler(Looper.getMainLooper()).postDelayed(this::checkAllPermissions, 1000);
    }

    private void checkAllPermissions() {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        dndPermissionGranted = notificationManager != null
                && notificationManager.isNotificationPolicyAccessGranted();
        notificationListenerGranted = isNotificationListenerEnabled();

        if (dndPermissionGranted && notificationListenerGranted) {
            proceedToMain();
        } else {
            showPermissionRequired();
        }
    }

    private boolean isNotificationListenerEnabled() {
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(getPackageName());
    }

    private void showPermissionRequired() {
        progressBar.setVisibility(View.GONE);
        statusText.setText(R.string.permission_required);
        permissionButton.setVisibility(View.VISIBLE);
    }

    private void requestMissingPermissions() {
        if (!dndPermissionGranted) {
            showDndPermissionDialog();
        } else if (!notificationListenerGranted) {
            showNotificationListenerDialog();
        } else {
            checkAllPermissions();
        }
    }

    private void showDndPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dnd_permission_title)
                .setMessage(R.string.dnd_permission_msg)
                .setPositiveButton(R.string.ok, (dialog, which) ->
                        startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showNotificationListenerDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.notification_listener_title)
                .setMessage(R.string.notification_listener_msg)
                .setPositiveButton(R.string.ok, (dialog, which) ->
                        startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Returning from the OS settings screens: re-check.
        if (permissionButton.getVisibility() == View.VISIBLE) {
            checkAllPermissions();
        }
    }

    private void proceedToMain() {
        progressBar.setVisibility(View.GONE);
        statusText.setText(R.string.starting);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }, 500);
    }
}
