package com.focusflip.app.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.focusflip.app.R;
import com.focusflip.app.activities.MainActivity;
import com.focusflip.app.database.StudyDatabase;
import com.focusflip.app.database.models.StudySession;
import com.focusflip.app.receivers.ScreenStateReceiver;
import com.focusflip.app.utils.PreferencesManager;
import com.focusflip.app.utils.TimeUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FocusService extends Service implements SensorEventListener {

    // Actions
    public static final String ACTION_START = "com.focusflip.ACTION_START";
    public static final String ACTION_STOP = "com.focusflip.ACTION_STOP";
    public static final String ACTION_FOCUS_UPDATE = "com.focusflip.ACTION_FOCUS_UPDATE";

    // Extras
    public static final String EXTRA_SESSION_TIME = "session_time";
    public static final String EXTRA_IS_FOCUSING = "is_focusing";
    public static final String EXTRA_DISTRACTIONS = "distractions";

    // Notification
    private static final String CHANNEL_ID = "focus_channel";
    private static final int NOTIFICATION_ID = 1;

    // Sensor threshold for face down detection
    private static final float FACE_DOWN_THRESHOLD = -8.0f;
    private static final long SENSOR_UPDATE_INTERVAL = 500; // ms

    // Service state
    private static boolean isRunning = false;
    private boolean isSessionActive = false;
    private boolean isFocusing = false;

    // Timer
    private long sessionStartTime = 0;
    private long totalFocusTime = 0;
    private long focusStartTime = 0;
    private int distractionsCount = 0;

    // Sensors
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private long lastSensorUpdate = 0;

    // Audio
    private AudioManager audioManager;
    private int previousRingerMode;

    // Screen state
    private ScreenStateReceiver screenStateReceiver;

    // Handler for timer updates
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;

    // Database
    private StudyDatabase database;
    private ExecutorService executor;
    private PreferencesManager preferencesManager;

    // Wake lock
    private PowerManager.WakeLock wakeLock;

    // Binder
    private final IBinder binder = new FocusBinder();

    public class FocusBinder extends Binder {
        public FocusService getService() {
            return FocusService.this;
        }
    }

    public static boolean isRunning() {
        return isRunning;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        database = StudyDatabase.getInstance(this);
        executor = Executors.newSingleThreadExecutor();
        preferencesManager = new PreferencesManager(this);

        // Acquire wake lock
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FocusFlip::FocusWakeLock");

        createNotificationChannel();
        setupScreenStateReceiver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            
            if (ACTION_START.equals(action)) {
                startSession();
            } else if (ACTION_STOP.equals(action)) {
                stopSession();
            }
        }
        
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.focus_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(getString(R.string.focus_channel_description));
        channel.setShowBadge(false);

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }

    private void setupScreenStateReceiver() {
        screenStateReceiver = new ScreenStateReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenStateReceiver, filter);
    }

    public void startSession() {
        if (isSessionActive) return;

        isSessionActive = true;
        isRunning = true;
        sessionStartTime = System.currentTimeMillis();
        totalFocusTime = 0;
        distractionsCount = 0;

        // Acquire wake lock
        if (!wakeLock.isHeld()) {
            wakeLock.acquire(8 * 60 * 60 * 1000L); // 8 hours max
        }

        // Register sensor listener
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);

        // Start foreground service
        Notification notification = buildNotification(0, false);
        startForeground(NOTIFICATION_ID, notification);

        // Start timer
        startTimer();

        // Apply silence mode
        applySilenceMode();
    }

    public void stopSession() {
        if (!isSessionActive) return;

        isSessionActive = false;
        isRunning = false;

        // Update focus time if currently focusing
        if (isFocusing && focusStartTime > 0) {
            totalFocusTime += System.currentTimeMillis() - focusStartTime;
        }

        // Release wake lock
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }

        // Unregister sensor listener
        sensorManager.unregisterListener(this);

        // Stop timer
        stopTimer();

        // Restore audio mode
        restoreAudioMode();

        // Save session to database
        saveSession();

        // Stop foreground
        stopForeground(true);
        stopSelf();
    }

    private void startTimer() {
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (isSessionActive) {
                    updateNotification();
                    broadcastUpdate();
                    timerHandler.postDelayed(this, 1000);
                }
            }
        };
        timerHandler.post(timerRunnable);
    }

    private void stopTimer() {
        timerHandler.removeCallbacks(timerRunnable);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSensorUpdate < SENSOR_UPDATE_INTERVAL) return;
        lastSensorUpdate = currentTime;

        float z = event.values[2];
        boolean wasFocusing = isFocusing;
        
        // Check if device is face down (z-axis pointing down)
        isFocusing = z < FACE_DOWN_THRESHOLD;

        if (wasFocusing && !isFocusing) {
            // Device was flipped up - distraction detected
            distractionsCount++;
            if (focusStartTime > 0) {
                totalFocusTime += currentTime - focusStartTime;
            }
            focusStartTime = 0;
        } else if (!wasFocusing && isFocusing) {
            // Device was flipped down - start focusing
            focusStartTime = currentTime;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed
    }

    private void applySilenceMode() {
        int silenceMode = preferencesManager.getSilenceMode();
        previousRingerMode = audioManager.getRingerMode();

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager.isNotificationPolicyAccessGranted()) {
            switch (silenceMode) {
                case PreferencesManager.SILENCE_ALL:
                    notificationManager.setInterruptionFilter(
                            NotificationManager.INTERRUPTION_FILTER_NONE);
                    break;
                case PreferencesManager.SILENCE_NOTIFICATIONS:
                    notificationManager.setInterruptionFilter(
                            NotificationManager.INTERRUPTION_FILTER_PRIORITY);
                    break;
                case PreferencesManager.VIBRATE_ONLY:
                    audioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                    break;
            }
        }
    }

    private void restoreAudioMode() {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager.isNotificationPolicyAccessGranted()) {
            notificationManager.setInterruptionFilter(
                    NotificationManager.INTERRUPTION_FILTER_ALL);
        }
        audioManager.setRingerMode(previousRingerMode);
    }

    private Notification buildNotification(long sessionTime, boolean focusing) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        String timeStr = TimeUtils.formatDuration(sessionTime);
        int dailyGoal = preferencesManager.getDailyGoalMinutes();
        String goalStr = TimeUtils.formatMinutes(dailyGoal);

        String text = getString(R.string.focus_notification_text, timeStr, goalStr);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.focus_notification_title))
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_focus)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setColor(getResources().getColor(
                        focusing ? R.color.focus_active : R.color.focus_paused, null))
                .build();
    }

    private void updateNotification() {
        long sessionTime = getSessionTime();
        Notification notification = buildNotification(sessionTime, isFocusing);
        
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    private void broadcastUpdate() {
        Intent intent = new Intent(ACTION_FOCUS_UPDATE);
        intent.putExtra(EXTRA_SESSION_TIME, getSessionTime());
        intent.putExtra(EXTRA_IS_FOCUSING, isFocusing);
        intent.putExtra(EXTRA_DISTRACTIONS, distractionsCount);
        
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void saveSession() {
        executor.execute(() -> {
            StudySession session = new StudySession();
            session.startTime = sessionStartTime;
            session.endTime = System.currentTimeMillis();
            session.focusDuration = totalFocusTime;
            session.distractionsCount = distractionsCount;
            session.notificationsBlocked = NotificationMonitorService.getBlockedCount();
            
            database.studySessionDao().insert(session);
            
            // Reset notification counter
            NotificationMonitorService.resetBlockedCount();
        });
    }

    public long getSessionTime() {
        if (!isSessionActive) return 0;
        
        long currentFocusTime = totalFocusTime;
        if (isFocusing && focusStartTime > 0) {
            currentFocusTime += System.currentTimeMillis() - focusStartTime;
        }
        return currentFocusTime;
    }

    public boolean isSessionActive() {
        return isSessionActive;
    }

    public boolean isFocusing() {
        return isFocusing;
    }

    public int getDistractionsCount() {
        return distractionsCount;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        
        if (screenStateReceiver != null) {
            unregisterReceiver(screenStateReceiver);
        }
        
        sensorManager.unregisterListener(this);
        
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
        
        executor.shutdown();
        isRunning = false;
    }
}
