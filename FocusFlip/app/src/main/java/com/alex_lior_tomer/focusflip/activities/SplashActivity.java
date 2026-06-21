package com.alex_lior_tomer.focusflip.activities;


import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;
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
import com.alex_lior_tomer.focusflip.utils.LocaleHelper;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY = 2000;
    
    private ProgressBar progressBar;
    private TextView statusText;
    private Button permissionButton;
    
    private boolean dndPermissionGranted = false;
    private boolean notificationListenerGranted = false;

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

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
        // Check DND access
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        dndPermissionGranted = notificationManager != null && notificationManager.isNotificationPolicyAccessGranted();
        
        // Check notification listener access
        notificationListenerGranted = isNotificationListenerEnabled();
        
        if (allPermissionsGranted()) {
            proceedToMain();
        } else {
            showPermissionRequired();
        }
    }

    private boolean isNotificationListenerEnabled() {
        String packageName = getPackageName();
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (flat != null) {
            return flat.contains(packageName);
        }
        return false;
    }

    private boolean allPermissionsGranted() {
        return dndPermissionGranted && notificationListenerGranted;
    }

    private void showPermissionRequired() {
        progressBar.setVisibility(View.GONE);
        statusText.setText(R.string.permission_required);
        permissionButton.setVisibility(View.VISIBLE);
    }

    private void requestMissingPermissions() {
        if (!dndPermissionGranted) {
            showDndPermissionDialog();
            return;
        }
        
        if (!notificationListenerGranted) {
            showNotificationListenerDialog();
            return;
        }
        
        checkAllPermissions();
    }

    private void showDndPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dnd_permission_title)
                .setMessage(R.string.dnd_permission_msg)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                    startActivity(intent);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showNotificationListenerDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.notification_listener_title)
                .setMessage(R.string.notification_listener_msg)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                    startActivity(intent);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showAlarmPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.alarm_permission_title)
                .setMessage(R.string.alarm_permission_msg)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                        startActivity(intent);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-check permissions when returning from settings
        if (permissionButton.getVisibility() == View.VISIBLE) {
            checkAllPermissions();
        }
    }

    private void proceedToMain() {
        progressBar.setVisibility(View.GONE);
        statusText.setText(R.string.starting);
        
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }, 500);
    }
}
