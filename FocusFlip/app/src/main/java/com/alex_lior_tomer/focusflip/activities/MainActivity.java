package com.alex_lior_tomer.focusflip.activities;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.alex_lior_tomer.focusflip.R;
import com.alex_lior_tomer.focusflip.database.StudyRepository;
import com.alex_lior_tomer.focusflip.services.FocusService;
import com.alex_lior_tomer.focusflip.utils.LocaleHelper;
import com.alex_lior_tomer.focusflip.utils.PreferencesManager;
import com.alex_lior_tomer.focusflip.utils.TimeUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    // UI Elements
    private TextView timerText;
    private TextView statusText;
    private ImageView statusIcon;
    private CardView statusCard;
    private ProgressBar goalProgress;
    private TextView goalProgressText;
    private TextView todayTimeText;
    private TextView distractionsText;
    private MaterialButton startStopButton;
    private FloatingActionButton statsButton;

    // Service
    private FocusService focusService;
    private boolean serviceBound = false;

    // Data
    private PreferencesManager preferencesManager;
    private StudyRepository repository;

    // Broadcast receiver for service updates
    private final BroadcastReceiver focusUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (FocusService.ACTION_FOCUS_UPDATE.equals(intent.getAction())) {
                long sessionTime = intent.getLongExtra(FocusService.EXTRA_SESSION_TIME, 0);
                boolean isFocusing = intent.getBooleanExtra(FocusService.EXTRA_IS_FOCUSING, false);
                int distractions = intent.getIntExtra(FocusService.EXTRA_DISTRACTIONS, 0);
                
                updateUI(sessionTime, isFocusing, distractions);
            }
        }
    };

    // Service connection
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            FocusService.FocusBinder binder = (FocusService.FocusBinder) service;
            focusService = binder.getService();
            serviceBound = true;
            updateUIFromService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            focusService = null;
        }
    };

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        initViews();
        initData();
        setupListeners();
        loadTodayStats();
    }

    private void initViews() {
        timerText = findViewById(R.id.timerText);
        statusText = findViewById(R.id.statusText);
        statusIcon = findViewById(R.id.statusIcon);
        statusCard = findViewById(R.id.statusCard);
        goalProgress = findViewById(R.id.goalProgress);
        goalProgressText = findViewById(R.id.goalProgressText);
        todayTimeText = findViewById(R.id.todayTimeText);
        distractionsText = findViewById(R.id.distractionsText);
        startStopButton = findViewById(R.id.startStopButton);
        statsButton = findViewById(R.id.statsButton);
    }

    private void initData() {
        preferencesManager = new PreferencesManager(this);
        repository = new StudyRepository(this);
    }

    private void setupListeners() {
        startStopButton.setOnClickListener(v -> toggleFocusSession());

        statsButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, StatisticsActivity.class);
            startActivity(intent);
        });
    }

    private void toggleFocusSession() {
        if (serviceBound && focusService != null) {
            if (focusService.isSessionActive()) {
                stopFocusSession();
            } else {
                startFocusSession();
            }
        } else {
            startFocusSession();
        }
    }

    private void startFocusSession() {
        Intent serviceIntent = new Intent(this, FocusService.class);
        serviceIntent.setAction(FocusService.ACTION_START);
        ContextCompat.startForegroundService(this, serviceIntent);
        
        // Bind to service
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        
        // Update UI
        updateButtonState(true);
        statusCard.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pulse));
    }

    private void stopFocusSession() {
        if (serviceBound && focusService != null) {
            focusService.stopSession();
        }
        
        Intent serviceIntent = new Intent(this, FocusService.class);
        serviceIntent.setAction(FocusService.ACTION_STOP);
        startService(serviceIntent);
        
        updateButtonState(false);
        statusCard.clearAnimation();
    }

    private void updateButtonState(boolean isActive) {
        if (isActive) {
            startStopButton.setText(R.string.stop_session);
            startStopButton.setBackgroundTintList(
                    ContextCompat.getColorStateList(this, R.color.error));
        } else {
            startStopButton.setText(R.string.start_session);
            startStopButton.setBackgroundTintList(
                    ContextCompat.getColorStateList(this, R.color.primary));
        }
    }

    private void updateUI(long sessionTime, boolean isFocusing, int distractions) {
        // Update timer
        timerText.setText(TimeUtils.formatDuration(sessionTime));
        
        // Update status
        if (isFocusing) {
            statusText.setText(R.string.focus_active);
            statusIcon.setImageResource(R.drawable.ic_focus);
            statusIcon.setColorFilter(ContextCompat.getColor(this, R.color.focus_active));
            statusCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.secondary_light));
        } else {
            statusText.setText(R.string.focus_paused);
            statusIcon.setImageResource(R.drawable.ic_phone_up);
            statusIcon.setColorFilter(ContextCompat.getColor(this, R.color.focus_paused));
            statusCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.warning));
        }
        
        // Update distractions
        distractionsText.setText(String.valueOf(distractions));
        
        // Update progress
        updateGoalProgress(sessionTime);
    }

    private void updateUIFromService() {
        if (serviceBound && focusService != null) {
            updateUI(
                    focusService.getSessionTime(),
                    focusService.isFocusing(),
                    focusService.getDistractionsCount()
            );
            updateButtonState(focusService.isSessionActive());
        }
    }

    private void updateGoalProgress(long additionalTime) {
        repository.getTodayTotalTime(todayTime -> {
            long todayTotal = todayTime + additionalTime;
            int dailyGoalMinutes = preferencesManager.getDailyGoalMinutes();
            long dailyGoalMs = dailyGoalMinutes * 60 * 1000L;

            int progress = dailyGoalMs > 0 ?
                    (int) ((todayTotal * 100) / dailyGoalMs) : 0;
            progress = Math.min(progress, 100);

            goalProgress.setProgress(progress);
            goalProgressText.setText(getString(R.string.percentage_format, progress));
            todayTimeText.setText(TimeUtils.formatDuration(todayTotal));
        });
    }

    private void loadTodayStats() {
        repository.getTodayTotalTime(todayTotal -> {
            todayTimeText.setText(TimeUtils.formatDuration(todayTotal));
            updateGoalProgress(0);
        });
        
        repository.getTodayDistractionsCount(todayDistractions -> {
            distractionsText.setText(String.valueOf(todayDistractions));
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.action_about) {
            showAboutDialog();
            return true;
        } else if (id == R.id.action_exit) {
            showExitConfirmation();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.about_title)
                .setMessage(R.string.about_message)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void showExitConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.exit_confirm_title)
                .setMessage(R.string.exit_confirm_msg)
                .setPositiveButton(R.string.exit, (dialog, which) -> {
                    if (focusService != null && focusService.isSessionActive()) {
                        stopFocusSession();
                    }
                    finishAffinity();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        
        // Register broadcast receiver
        IntentFilter filter = new IntentFilter(FocusService.ACTION_FOCUS_UPDATE);
        LocalBroadcastManager.getInstance(this).registerReceiver(focusUpdateReceiver, filter);
        
        // Bind to service if running
        if (FocusService.isRunning()) {
            Intent serviceIntent = new Intent(this, FocusService.class);
            bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTodayStats();
        
        if (serviceBound && focusService != null) {
            updateUIFromService();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        
        LocalBroadcastManager.getInstance(this).unregisterReceiver(focusUpdateReceiver);
        
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        repository.shutdown();
    }
}
