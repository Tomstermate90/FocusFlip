package com.focusflip.app.activities;

import android.Manifest;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.focusflip.app.R;
import com.focusflip.app.services.NotificationMonitorService;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY = 2000;
    
    private ProgressBar progressBar;
    private TextView statusText;
    private Button permissionButton;
    
    private boolean notificationPermissionGranted = false;
    private boolean dndPermissionGranted = false;
    private boolean notificationListenerGranted = false;
    private boolean alarmPermissionGranted = false;

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                notificationPermissionGranted = isGranted;
                checkAllPermissions();
            });

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
        // Check notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionGranted = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        } else {
            notificationPermissionGranted = true;
        }
        
        // Check DND access
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        dndPermissionGranted = notificationManager.isNotificationPolicyAccessGranted();
        
        // Check notification listener access
        notificationListenerGranted = isNotificationListenerEnabled();
        
        // Check alarm permission (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            android.app.AlarmManager alarmManager = getSystemService(android.app.AlarmManager.class);
            alarmPermissionGranted = alarmManager.canScheduleExactAlarms();
        } else {
            alarmPermissionGranted = true;
        }
        
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
        return notificationPermissionGranted && dndPermissionGranted && 
               notificationListenerGranted && alarmPermissionGranted;
    }

    private void showPermissionRequired() {
        progressBar.setVisibility(View.GONE);
        statusText.setText(R.string.permission_required);
        permissionButton.setVisibility(View.VISIBLE);
    }

    private void requestMissingPermissions() {
        if (!notificationPermissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        
        if (!dndPermissionGranted) {
            showDndPermissionDialog();
            return;
        }
        
        if (!notificationListenerGranted) {
            showNotificationListenerDialog();
            return;
        }
        
        if (!alarmPermissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            showAlarmPermissionDialog();
            return;
        }
        
        checkAllPermissions();
    }

    private void showDndPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("הרשאת נא לא להפריע")
                .setMessage("האפליקציה צריכה גישה למצב 'נא לא להפריע' כדי להשתיק התראות בזמן למידה.")
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                    startActivity(intent);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showNotificationListenerDialog() {
        new AlertDialog.Builder(this)
                .setTitle("גישה להתראות")
                .setMessage("האפליקציה צריכה גישה להתראות כדי לספור הסחות דעת ולעזור לך להתרכז.")
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                    startActivity(intent);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showAlarmPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("הרשאת התראות מתוזמנות")
                .setMessage("האפליקציה צריכה הרשאה לתזמן התראות כדי להזכיר לך על יעד הלמידה.")
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
        statusText.setText("מתחיל...");
        
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }, 500);
    }
}
