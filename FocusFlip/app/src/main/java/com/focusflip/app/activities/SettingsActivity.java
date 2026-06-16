package com.focusflip.app.activities;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.focusflip.app.R;
import com.focusflip.app.receivers.GoalCheckReceiver;
import com.focusflip.app.utils.PreferencesManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;

import java.util.Calendar;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private Slider goalSlider;
    private TextView goalValueText;
    private TextView reminderTimeText;
    private RadioGroup silenceModeGroup;
    private RadioButton radioSilenceAll;
    private RadioButton radioSilenceNotifications;
    private RadioButton radioVibrateOnly;
    private MaterialButton saveButton;

    private PreferencesManager preferencesManager;
    private int selectedHour = 20;
    private int selectedMinute = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        preferencesManager = new PreferencesManager(this);

        setupToolbar();
        initViews();
        loadCurrentSettings();
        setupListeners();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.settings);
        }
    }

    private void initViews() {
        goalSlider = findViewById(R.id.goalSlider);
        goalValueText = findViewById(R.id.goalValueText);
        reminderTimeText = findViewById(R.id.reminderTimeText);
        silenceModeGroup = findViewById(R.id.silenceModeGroup);
        radioSilenceAll = findViewById(R.id.radioSilenceAll);
        radioSilenceNotifications = findViewById(R.id.radioSilenceNotifications);
        radioVibrateOnly = findViewById(R.id.radioVibrateOnly);
        saveButton = findViewById(R.id.saveButton);
    }

    private void loadCurrentSettings() {
        // Load daily goal
        int currentGoal = preferencesManager.getDailyGoalMinutes();
        goalSlider.setValue(currentGoal);
        updateGoalValueText(currentGoal);

        // Load reminder time
        selectedHour = preferencesManager.getReminderHour();
        selectedMinute = preferencesManager.getReminderMinute();
        updateReminderTimeText();

        // Load silence mode
        int silenceMode = preferencesManager.getSilenceMode();
        switch (silenceMode) {
            case PreferencesManager.SILENCE_ALL:
                radioSilenceAll.setChecked(true);
                break;
            case PreferencesManager.SILENCE_NOTIFICATIONS:
                radioSilenceNotifications.setChecked(true);
                break;
            case PreferencesManager.VIBRATE_ONLY:
                radioVibrateOnly.setChecked(true);
                break;
        }
    }

    private void setupListeners() {
        goalSlider.addOnChangeListener((slider, value, fromUser) -> {
            updateGoalValueText((int) value);
        });

        findViewById(R.id.reminderTimeContainer).setOnClickListener(v -> showTimePicker());

        saveButton.setOnClickListener(v -> saveSettings());
    }

    private void updateGoalValueText(int minutes) {
        if (minutes >= 60) {
            int hours = minutes / 60;
            int mins = minutes % 60;
            if (mins > 0) {
                goalValueText.setText(String.format(Locale.getDefault(), 
                        "%d שעות %d דקות", hours, mins));
            } else {
                goalValueText.setText(String.format(Locale.getDefault(), 
                        "%d שעות", hours));
            }
        } else {
            goalValueText.setText(String.format(Locale.getDefault(), 
                    "%d דקות", minutes));
        }
    }

    private void updateReminderTimeText() {
        reminderTimeText.setText(String.format(Locale.getDefault(), 
                "%02d:%02d", selectedHour, selectedMinute));
    }

    private void showTimePicker() {
        TimePickerDialog timePickerDialog = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    selectedHour = hourOfDay;
                    selectedMinute = minute;
                    updateReminderTimeText();
                },
                selectedHour,
                selectedMinute,
                true
        );
        timePickerDialog.show();
    }

    private void saveSettings() {
        // Save daily goal
        int goalMinutes = (int) goalSlider.getValue();
        preferencesManager.setDailyGoalMinutes(goalMinutes);

        // Save reminder time
        preferencesManager.setReminderTime(selectedHour, selectedMinute);

        // Save silence mode
        int silenceMode;
        if (radioSilenceAll.isChecked()) {
            silenceMode = PreferencesManager.SILENCE_ALL;
        } else if (radioSilenceNotifications.isChecked()) {
            silenceMode = PreferencesManager.SILENCE_NOTIFICATIONS;
        } else {
            silenceMode = PreferencesManager.VIBRATE_ONLY;
        }
        preferencesManager.setSilenceMode(silenceMode);

        // Schedule daily goal check alarm
        scheduleGoalCheckAlarm();

        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void scheduleGoalCheckAlarm() {
        AlarmManager alarmManager = getSystemService(AlarmManager.class);
        
        Intent intent = new Intent(this, GoalCheckReceiver.class);
        intent.setAction(GoalCheckReceiver.ACTION_CHECK_GOAL);
        
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                GoalCheckReceiver.REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Set alarm time for today or tomorrow
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, selectedHour);
        calendar.set(Calendar.MINUTE, selectedMinute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        // If the time has already passed today, schedule for tomorrow
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        // Schedule repeating alarm
        alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY,
                pendingIntent
        );
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
