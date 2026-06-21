package com.alex_lior_tomer.focusflip.database;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.alex_lior_tomer.focusflip.database.models.DailyStats;
import com.alex_lior_tomer.focusflip.database.models.StudySession;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repository class that abstracts access to the StudyDatabase.
 * Part of the MVC (Model) implementation.
 */
public class StudyRepository {

    private final StudySessionDao studySessionDao;
    private final ExecutorService executor;
    private final Handler mainHandler;

    public interface RepositoryCallback<T> {
        void onComplete(T result);
    }

    public StudyRepository(Context context) {
        StudyDatabase db = StudyDatabase.getInstance(context);
        this.studySessionDao = db.studySessionDao();
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void insertSession(StudySession session) {
        executor.execute(() -> studySessionDao.insert(session));
    }

    public void getTodayTotalTime(RepositoryCallback<Long> callback) {
        executor.execute(() -> {
            long result = studySessionDao.getTodayTotalTime();
            mainHandler.post(() -> callback.onComplete(result));
        });
    }

    public void getTodayDistractionsCount(RepositoryCallback<Integer> callback) {
        executor.execute(() -> {
            int result = studySessionDao.getTodayDistractionsCount();
            mainHandler.post(() -> callback.onComplete(result));
        });
    }

    public void getWeeklyStats(RepositoryCallback<List<DailyStats>> callback) {
        executor.execute(() -> {
            List<DailyStats> result = studySessionDao.getWeeklyStats();
            mainHandler.post(() -> callback.onComplete(result));
        });
    }

    public void getTotalStudyTime(RepositoryCallback<Long> callback) {
        executor.execute(() -> {
            long result = studySessionDao.getTotalStudyTime();
            mainHandler.post(() -> callback.onComplete(result));
        });
    }

    public void getTotalNotificationsBlocked(RepositoryCallback<Integer> callback) {
        executor.execute(() -> {
            int result = studySessionDao.getTotalNotificationsBlocked();
            mainHandler.post(() -> callback.onComplete(result));
        });
    }

    public void getTotalDistractions(RepositoryCallback<Integer> callback) {
        executor.execute(() -> {
            int result = studySessionDao.getTotalDistractions();
            mainHandler.post(() -> callback.onComplete(result));
        });
    }

    public void getAverageSessionTime(RepositoryCallback<Long> callback) {
        executor.execute(() -> {
            long result = studySessionDao.getAverageSessionTime();
            mainHandler.post(() -> callback.onComplete(result));
        });
    }

    public void getRecentStats(int days, RepositoryCallback<List<DailyStats>> callback) {
        executor.execute(() -> {
            List<DailyStats> result = studySessionDao.getRecentStats(days);
            mainHandler.post(() -> callback.onComplete(result));
        });
    }

    public void getGoalsAchievedCount(long dailyGoalMs, RepositoryCallback<Integer> callback) {
        executor.execute(() -> {
            int result = studySessionDao.getGoalsAchievedCount(dailyGoalMs);
            mainHandler.post(() -> callback.onComplete(result));
        });
    }

    public void shutdown() {
        executor.shutdown();
    }
}
