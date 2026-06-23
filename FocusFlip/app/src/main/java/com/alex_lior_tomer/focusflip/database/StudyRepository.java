package com.alex_lior_tomer.focusflip.database;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.alex_lior_tomer.focusflip.database.models.DailyStats;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Wraps {@link StudySessionDao} with a background executor and posts results
 * back to the main thread. The Model layer in the app's MVC split — Activities
 * never touch the database directly.
 *
 * The DAO is acquired lazily on the executor so the very first repository
 * call doesn't drag {@code getWritableDatabase()} onto the UI thread.
 */
public class StudyRepository {

    private final Context appContext;
    private final ExecutorService executor;
    private final Handler mainHandler;

    private volatile StudySessionDao studySessionDao;

    public interface RepositoryCallback<T> {
        void onComplete(T result);
    }

    public StudyRepository(Context context) {
        this.appContext = context.getApplicationContext();
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    private StudySessionDao dao() {
        if (studySessionDao == null) {
            studySessionDao = StudyDatabase.getInstance(appContext).studySessionDao();
        }
        return studySessionDao;
    }

    public void getTodayTotalTime(RepositoryCallback<Long> callback) {
        executor.execute(() -> {
            long result = dao().getTodayTotalTime();
            mainHandler.post(() -> callback.onComplete(result));
        });
    }

    public void getTodayDistractionsCount(RepositoryCallback<Integer> callback) {
        executor.execute(() -> {
            int result = dao().getTodayDistractionsCount();
            mainHandler.post(() -> callback.onComplete(result));
        });
    }

    public void getWeeklyStats(RepositoryCallback<List<DailyStats>> callback) {
        executor.execute(() -> {
            List<DailyStats> result = dao().getWeeklyStats();
            mainHandler.post(() -> callback.onComplete(result));
        });
    }

    public void getTotalStudyTime(RepositoryCallback<Long> callback) {
        executor.execute(() -> {
            long result = dao().getTotalStudyTime();
            mainHandler.post(() -> callback.onComplete(result));
        });
    }

    public void getTotalNotificationsBlocked(RepositoryCallback<Integer> callback) {
        executor.execute(() -> {
            int result = dao().getTotalNotificationsBlocked();
            mainHandler.post(() -> callback.onComplete(result));
        });
    }

    public void getTotalDistractions(RepositoryCallback<Integer> callback) {
        executor.execute(() -> {
            int result = dao().getTotalDistractions();
            mainHandler.post(() -> callback.onComplete(result));
        });
    }

    public void getAverageSessionTime(RepositoryCallback<Long> callback) {
        executor.execute(() -> {
            long result = dao().getAverageSessionTime();
            mainHandler.post(() -> callback.onComplete(result));
        });
    }

    public void getRecentStats(int days, RepositoryCallback<List<DailyStats>> callback) {
        executor.execute(() -> {
            List<DailyStats> result = dao().getRecentStats(days);
            mainHandler.post(() -> callback.onComplete(result));
        });
    }

    public void getGoalsAchievedCount(long dailyGoalMs, RepositoryCallback<Integer> callback) {
        executor.execute(() -> {
            int result = dao().getGoalsAchievedCount(dailyGoalMs);
            mainHandler.post(() -> callback.onComplete(result));
        });
    }

    public void shutdown() {
        executor.shutdown();
    }
}
