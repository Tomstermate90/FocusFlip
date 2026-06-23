package com.alex_lior_tomer.focusflip.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.alex_lior_tomer.focusflip.R;
import com.alex_lior_tomer.focusflip.activities.MainActivity;
import com.alex_lior_tomer.focusflip.database.StudyDatabase;
import com.alex_lior_tomer.focusflip.database.models.StudySession;
import com.alex_lior_tomer.focusflip.utils.PreferencesManager;
import com.alex_lior_tomer.focusflip.utils.TimeUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Foreground service that drives a focus session: listens to the accelerometer
 * and proximity sensor to detect face-down (focus) vs face-up (distraction),
 * runs the silence-mode policy, ticks a 1Hz timer, and persists the session
 * to SQLite on stop.
 */
public class FocusService extends Service implements SensorEventListener {

    public static final String ACTION_START = "com.alex_lior_tomer.focusflip.ACTION_START";
    public static final String ACTION_STOP = "com.alex_lior_tomer.focusflip.ACTION_STOP";
    public static final String ACTION_FOCUS_UPDATE = "com.alex_lior_tomer.focusflip.ACTION_FOCUS_UPDATE";

    public static final String EXTRA_SESSION_TIME = "session_time";
    public static final String EXTRA_IS_FOCUSING = "is_focusing";
    public static final String EXTRA_DISTRACTIONS = "distractions";

    private static final String CHANNEL_ID = "focus_channel";
    private static final int NOTIFICATION_ID = 1;

    // Accelerometer Z < -8 m/s² means the device is roughly face-down on a flat
    // surface. We rate-limit sensor handling to 2Hz to avoid jitter near the
    // threshold flipping focus state on and off rapidly.
    private static final float FACE_DOWN_THRESHOLD = -8.0f;
    private static final long SENSOR_UPDATE_INTERVAL = 500;

    // Cap a wake-lock at 4 hours. Long enough for any realistic single study
    // block, short enough that a user who forgets to stop the session won't
    // drain their battery overnight. The lock auto-releases on stop anyway;
    // this is just the failsafe.
    private static final long WAKE_LOCK_TIMEOUT_MS = 4L * 60 * 60 * 1000;

    // The foreground notification only needs to redraw when the focus state
    // flips or once every ~30s for the ticking timer. Rebuilding it every
    // second (as the broadcast does) hammers the system NotificationManager
    // for no UX benefit.
    private static final long NOTIFICATION_REFRESH_MS = 30_000;

    private static boolean isRunning = false;
    private boolean isSessionActive = false;
    private boolean isFocusing = false;

    private long sessionStartTime = 0;
    private long totalFocusTime = 0;
    private long focusStartTime = 0;
    private int distractionsCount = 0;
    private long lastNotificationRefresh = 0;
    private boolean lastNotificationFocusing = false;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor proximitySensor;
    private float lastZ = 0;
    private float lastProximity = -1;
    private long lastSensorUpdate = 0;

    private Vibrator vibrator;
    private AudioManager audioManager;
    private int previousRingerMode;

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;

    private StudyDatabase database;
    private ExecutorService executor;
    private PreferencesManager preferencesManager;

    private PowerManager.WakeLock wakeLock;

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
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        database = StudyDatabase.getInstance(this);
        executor = Executors.newSingleThreadExecutor();
        preferencesManager = new PreferencesManager(this);

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FocusFlip::FocusWakeLock");

        createNotificationChannel();
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

    public void startSession() {
        if (isSessionActive) return;

        isSessionActive = true;
        isRunning = true;
        sessionStartTime = System.currentTimeMillis();
        totalFocusTime = 0;
        distractionsCount = 0;

        if (!wakeLock.isHeld()) {
            wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS);
        }

        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        if (proximitySensor != null) {
            sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        startForeground(NOTIFICATION_ID, buildNotification(0, false));
        startTimer();
        applySilenceMode();
    }

    public void stopSession() {
        if (!isSessionActive) return;

        isSessionActive = false;
        isRunning = false;

        if (isFocusing && focusStartTime > 0) {
            totalFocusTime += System.currentTimeMillis() - focusStartTime;
        }

        if (wakeLock.isHeld()) {
            wakeLock.release();
        }

        sensorManager.unregisterListener(this);
        stopTimer();
        restoreAudioMode();
        saveSession();

        stopForeground(true);
        stopSelf();
    }

    private void startTimer() {
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isSessionActive) return;
                broadcastUpdate();
                long now = System.currentTimeMillis();
                if (isFocusing != lastNotificationFocusing
                        || now - lastNotificationRefresh >= NOTIFICATION_REFRESH_MS) {
                    updateNotification();
                    lastNotificationRefresh = now;
                    lastNotificationFocusing = isFocusing;
                }
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.post(timerRunnable);
    }

    private void stopTimer() {
        timerHandler.removeCallbacks(timerRunnable);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            lastZ = event.values[2];
        } else if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
            lastProximity = event.values[0];
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSensorUpdate < SENSOR_UPDATE_INTERVAL) return;
        lastSensorUpdate = currentTime;

        boolean wasFocusing = isFocusing;

        // Proximity reads 0 when near, up to maxRange when far. Some devices
        // report a binary 0/5 instead of a gradient — treating "anything below
        // max range" as near handles both styles. If the device has no
        // proximity sensor at all we fall back to the accelerometer alone.
        boolean isNear = proximitySensor == null || lastProximity < proximitySensor.getMaximumRange();
        isFocusing = lastZ < FACE_DOWN_THRESHOLD && isNear;

        if (wasFocusing && !isFocusing) {
            distractionsCount++;
            if (focusStartTime > 0) {
                totalFocusTime += currentTime - focusStartTime;
            }
            focusStartTime = 0;
            vibrate(false);
            refreshNotificationNow();
        } else if (!wasFocusing && isFocusing) {
            focusStartTime = currentTime;
            vibrate(true);
            refreshNotificationNow();
        }
    }

    private void refreshNotificationNow() {
        updateNotification();
        lastNotificationRefresh = System.currentTimeMillis();
        lastNotificationFocusing = isFocusing;
    }

    private void vibrate(boolean focusing) {
        if (vibrator == null || !vibrator.hasVibrator()) return;

        if (focusing) {
            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            long[] pattern = {0, 200, 100, 200};
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

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
        String goalStr = TimeUtils.formatMinutes(preferencesManager.getDailyGoalMinutes());
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
        Notification notification = buildNotification(getSessionTime(), isFocusing);
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

    public boolean isSessionActive() { return isSessionActive; }
    public boolean isFocusing()      { return isFocusing; }
    public int getDistractionsCount(){ return distractionsCount; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(this);
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
        executor.shutdown();
        isRunning = false;
    }
}
