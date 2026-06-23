package com.alex_lior_tomer.focusflip.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.alex_lior_tomer.focusflip.database.models.DailyStats;
import com.alex_lior_tomer.focusflip.database.models.StudySession;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** All raw SQL for the study_sessions table lives here. */
public class StudySessionDao {

    private final SQLiteDatabase database;
    private final SimpleDateFormat dateFormat;

    public StudySessionDao(SQLiteDatabase database) {
        this.database = database;
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    }

    public long insert(StudySession session) {
        ContentValues values = new ContentValues();
        values.put(StudyDatabase.COLUMN_START_TIME, session.startTime);
        values.put(StudyDatabase.COLUMN_END_TIME, session.endTime);
        values.put(StudyDatabase.COLUMN_FOCUS_DURATION, session.focusDuration);
        values.put(StudyDatabase.COLUMN_DISTRACTIONS, session.distractionsCount);
        values.put(StudyDatabase.COLUMN_NOTIFICATIONS_BLOCKED, session.notificationsBlocked);
        return database.insert(StudyDatabase.TABLE_SESSIONS, null, values);
    }

    public long getTodayTotalTime() {
        String today = dateFormat.format(new Date());
        return sumColumnInRange(StudyDatabase.COLUMN_FOCUS_DURATION,
                getStartOfDay(today), getEndOfDay(today));
    }

    public int getTodayDistractionsCount() {
        String today = dateFormat.format(new Date());
        return (int) sumColumnInRange(StudyDatabase.COLUMN_DISTRACTIONS,
                getStartOfDay(today), getEndOfDay(today));
    }

    /** Last 7 days, grouped per local-time day, newest first. */
    public List<DailyStats> getWeeklyStats() {
        long sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);
        return runDailyAggregate(sevenDaysAgo, 7);
    }

    /** Last N days, grouped per local-time day, newest first. */
    public List<DailyStats> getRecentStats(int days) {
        long daysAgo = System.currentTimeMillis() - ((long) days * 24 * 60 * 60 * 1000);
        return runDailyAggregate(daysAgo, days);
    }

    public long getTotalStudyTime() {
        return sumColumn(StudyDatabase.COLUMN_FOCUS_DURATION);
    }

    /** Count of distinct local-time days whose summed focus duration met the goal. */
    public int getGoalsAchievedCount(long dailyGoalMs) {
        String query = "SELECT COUNT(*) FROM (" +
                "SELECT date(" + StudyDatabase.COLUMN_START_TIME + "/1000, 'unixepoch', 'localtime') as day, " +
                "SUM(" + StudyDatabase.COLUMN_FOCUS_DURATION + ") as daily_total " +
                "FROM " + StudyDatabase.TABLE_SESSIONS + " " +
                "GROUP BY day HAVING daily_total >= ?" +
                ")";
        Cursor cursor = database.rawQuery(query, new String[]{String.valueOf(dailyGoalMs)});
        int count = 0;
        if (cursor.moveToFirst()) count = cursor.getInt(0);
        cursor.close();
        return count;
    }

    public int getTotalNotificationsBlocked() {
        return (int) sumColumn(StudyDatabase.COLUMN_NOTIFICATIONS_BLOCKED);
    }

    public int getTotalDistractions() {
        return (int) sumColumn(StudyDatabase.COLUMN_DISTRACTIONS);
    }

    public long getAverageSessionTime() {
        Cursor cursor = database.rawQuery(
                "SELECT AVG(" + StudyDatabase.COLUMN_FOCUS_DURATION + ") FROM "
                        + StudyDatabase.TABLE_SESSIONS, null);
        long avg = 0;
        if (cursor.moveToFirst()) avg = cursor.getLong(0);
        cursor.close();
        return avg;
    }

    private long sumColumn(String column) {
        Cursor cursor = database.rawQuery(
                "SELECT SUM(" + column + ") FROM " + StudyDatabase.TABLE_SESSIONS, null);
        long total = 0;
        if (cursor.moveToFirst()) total = cursor.getLong(0);
        cursor.close();
        return total;
    }

    private long sumColumnInRange(String column, long startMs, long endMs) {
        String query = "SELECT SUM(" + column + ") FROM " + StudyDatabase.TABLE_SESSIONS
                + " WHERE " + StudyDatabase.COLUMN_START_TIME + " >= ? AND "
                + StudyDatabase.COLUMN_START_TIME + " <= ?";
        Cursor cursor = database.rawQuery(query,
                new String[]{String.valueOf(startMs), String.valueOf(endMs)});
        long total = 0;
        if (cursor.moveToFirst()) total = cursor.getLong(0);
        cursor.close();
        return total;
    }

    private List<DailyStats> runDailyAggregate(long startMs, int limit) {
        List<DailyStats> stats = new ArrayList<>();
        String query = "SELECT date(" + StudyDatabase.COLUMN_START_TIME + "/1000, 'unixepoch', 'localtime') as day, " +
                "SUM(" + StudyDatabase.COLUMN_FOCUS_DURATION + ") as total_time, " +
                "SUM(" + StudyDatabase.COLUMN_DISTRACTIONS + ") as total_distractions, " +
                "SUM(" + StudyDatabase.COLUMN_NOTIFICATIONS_BLOCKED + ") as total_blocked, " +
                "COUNT(*) as session_count " +
                "FROM " + StudyDatabase.TABLE_SESSIONS + " " +
                "WHERE " + StudyDatabase.COLUMN_START_TIME + " >= ? " +
                "GROUP BY day ORDER BY day DESC LIMIT ?";
        Cursor cursor = database.rawQuery(query, new String[]{
                String.valueOf(startMs), String.valueOf(limit)});
        while (cursor.moveToNext()) {
            DailyStats d = new DailyStats();
            d.date = cursor.getString(0);
            d.totalTime = cursor.getLong(1);
            d.totalDistractions = cursor.getInt(2);
            d.totalNotificationsBlocked = cursor.getInt(3);
            d.sessionCount = cursor.getInt(4);
            stats.add(d);
        }
        cursor.close();
        return stats;
    }

    private long parseDateTime(String dateTime) {
        try {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            Date date = fmt.parse(dateTime);
            return date != null ? date.getTime() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private long getStartOfDay(String date) { return parseDateTime(date + " 00:00:00"); }
    private long getEndOfDay(String date)   { return parseDateTime(date + " 23:59:59"); }
}
