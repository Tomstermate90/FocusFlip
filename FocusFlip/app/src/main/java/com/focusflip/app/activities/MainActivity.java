package com.focusflip.app.activities;

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
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.focusflip.app.R;
import com.focusflip.app.database.StudyDatabase;
import com.focusflip.app.services.FocusService;
import com.focusflip.app.utils.PreferencesManager;
import com.focusflip.app.utils.TimeUtils;
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
    private FloatingActionButton settingsButton;
    private FloatingActionButton statsButton;

    // Service
    private FocusService focusService;
    private boolean serviceBound = false;

    // Data
    private PreferencesManager preferencesManager;
    private StudyDatabase database;
    private ExecutorService executor;
    private Handler uiHandler;

    // Timer update
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;

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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
        settingsButton = findViewById(R.id.settingsButton);
        statsButton = findViewById(R.id.statsButton);
    }

    private void initData() {
        preferencesManager = new PreferencesManager(this);
        database = StudyDatabase.getInstance(this);
        executor = Executors.newSingleThreadExecutor();
        uiHandler = new Handler(Looper.getMainLooper());
    }

    private void setupListeners() {
        startStopButton.setOnClickListener(v -> toggleFocusSession());
        
        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });
        
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
        executor.execute(() -> {
            long todayTotal = database.studySessionDao().getTodayTotalTime() + additionalTime;
            int dailyGoalMinutes = preferencesManager.getDailyGoalMinutes();
            long dailyGoalMs = dailyGoalMinutes * 60 * 1000L;
            
            int progress = dailyGoalMs > 0 ? 
                    (int) ((todayTotal * 100) / dailyGoalMs) : 0;
            progress = Math.min(progress, 100);
            
            int finalProgress = progress;
            uiHandler.post(() -> {
                goalProgress.setProgress(finalProgress);
                goalProgressText.setText(finalProgress + "%");
                todayTimeText.setText(TimeUtils.formatDuration(todayTotal));
            });
        });
    }

    private void loadTodayStats() {
        executor.execute(() -> {
            long todayTotal = database.studySessionDao().getTodayTotalTime();
            int todayDistractions = database.studySessionDao().getTodayDistractionsCount();
            
            uiHandler.post(() -> {
                todayTimeText.setText(TimeUtils.formatDuration(todayTotal));
                distractionsText.setText(String.valueOf(todayDistractions));
                updateGoalProgress(0);
            });
        });
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
        executor.shutdown();
    }
}
