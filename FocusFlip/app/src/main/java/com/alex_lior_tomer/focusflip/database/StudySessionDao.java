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

/**
 * Data Access Object for study sessions.
 * Handles all database operations related to study sessions.
 */
public class StudySessionDao {

    private final SQLiteDatabase database;
    private final SimpleDateFormat dateFormat;

    public StudySessionDao(SQLiteDatabase database) {
        this.database = database;
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    }

    /**
     * Insert a new study session.
     */
    public long insert(StudySession session) {
        ContentValues values = new ContentValues();
        values.put(StudyDatabase.COLUMN_START_TIME, session.startTime);
        values.put(StudyDatabase.COLUMN_END_TIME, session.endTime);
        values.put(StudyDatabase.COLUMN_FOCUS_DURATION, session.focusDuration);
        values.put(StudyDatabase.COLUMN_DISTRACTIONS, session.distractionsCount);
        values.put(StudyDatabase.COLUMN_NOTIFICATIONS_BLOCKED, session.notificationsBlocked);
        
        return database.insert(StudyDatabase.TABLE_SESSIONS, null, values);
    }

    /**
     * Get today's total focus time in milliseconds.
     */
    public long getTodayTotalTime() {
        String today = dateFormat.format(new Date());
        String todayStart = today + " 00:00:00";
        String todayEnd = today + " 23:59:59";
        
        long startMs = parseDateTime(todayStart);
        long endMs = parseDateTime(todayEnd);
        
        String query = "SELECT SUM(" + StudyDatabase.COLUMN_FOCUS_DURATION + ") FROM " 
                + StudyDatabase.TABLE_SESSIONS 
                + " WHERE " + StudyDatabase.COLUMN_START_TIME + " >= ? AND " 
                + StudyDatabase.COLUMN_START_TIME + " <= ?";
        
        Cursor cursor = database.rawQuery(query, new String[]{
                String.valueOf(startMs), String.valueOf(endMs)
        });
        
        long total = 0;
        if (cursor.moveToFirst()) {
            total = cursor.getLong(0);
        }
        cursor.close();
        
        return total;
    }

    /**
     * Get today's total distractions count.
     */
    public int getTodayDistractionsCount() {
        String today = dateFormat.format(new Date());
        long startMs = getStartOfDay(today);
        long endMs = getEndOfDay(today);
        
        String query = "SELECT SUM(" + StudyDatabase.COLUMN_DISTRACTIONS + ") FROM " 
                + StudyDatabase.TABLE_SESSIONS 
                + " WHERE " + StudyDatabase.COLUMN_START_TIME + " >= ? AND " 
                + StudyDatabase.COLUMN_START_TIME + " <= ?";
        
        Cursor cursor = database.rawQuery(query, new String[]{
                String.valueOf(startMs), String.valueOf(endMs)
        });
        
        int total = 0;
        if (cursor.moveToFirst()) {
            total = cursor.getInt(0);
        }
        cursor.close();
        
        return total;
    }

    /**
     * Get weekly statistics for the last 7 days.
     */
    public List<DailyStats> getWeeklyStats() {
        List<DailyStats> stats = new ArrayList<>();
        
        String query = "SELECT date(" + StudyDatabase.COLUMN_START_TIME + "/1000, 'unixepoch', 'localtime') as day, " +
                "SUM(" + StudyDatabase.COLUMN_FOCUS_DURATION + ") as total_time, " +
                "SUM(" + StudyDatabase.COLUMN_DISTRACTIONS + ") as total_distractions, " +
                "SUM(" + StudyDatabase.COLUMN_NOTIFICATIONS_BLOCKED + ") as total_blocked, " +
                "COUNT(*) as session_count " +
                "FROM " + StudyDatabase.TABLE_SESSIONS + " " +
                "WHERE " + StudyDatabase.COLUMN_START_TIME + " >= ? " +
                "GROUP BY day ORDER BY day DESC LIMIT 7";
        
        long sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L);
        
        Cursor cursor = database.rawQuery(query, new String[]{String.valueOf(sevenDaysAgo)});
        
        while (cursor.moveToNext()) {
            DailyStats dailyStats = new DailyStats();
            dailyStats.date = cursor.getString(0);
            dailyStats.totalTime = cursor.getLong(1);
            dailyStats.totalDistractions = cursor.getInt(2);
            dailyStats.totalNotificationsBlocked = cursor.getInt(3);
            dailyStats.sessionCount = cursor.getInt(4);
            stats.add(dailyStats);
        }
        cursor.close();
        
        return stats;
    }

    /**
     * Get recent daily statistics for calculating streaks.
     */
    public List<DailyStats> getRecentStats(int days) {
        List<DailyStats> stats = new ArrayList<>();
        
        String query = "SELECT date(" + StudyDatabase.COLUMN_START_TIME + "/1000, 'unixepoch', 'localtime') as day, " +
                "SUM(" + StudyDatabase.COLUMN_FOCUS_DURATION + ") as total_time, " +
                "SUM(" + StudyDatabase.COLUMN_DISTRACTIONS + ") as total_distractions, " +
                "SUM(" + StudyDatabase.COLUMN_NOTIFICATIONS_BLOCKED + ") as total_blocked, " +
                "COUNT(*) as session_count " +
                "FROM " + StudyDatabase.TABLE_SESSIONS + " " +
                "WHERE " + StudyDatabase.COLUMN_START_TIME + " >= ? " +
                "GROUP BY day ORDER BY day DESC LIMIT ?";
        
        long daysAgo = System.currentTimeMillis() - ((long) days * 24 * 60 * 60 * 1000);
        
        Cursor cursor = database.rawQuery(query, new String[]{
                String.valueOf(daysAgo), String.valueOf(days)
        });
        
        while (cursor.moveToNext()) {
            DailyStats dailyStats = new DailyStats();
            dailyStats.date = cursor.getString(0);
            dailyStats.totalTime = cursor.getLong(1);
            dailyStats.totalDistractions = cursor.getInt(2);
            dailyStats.totalNotificationsBlocked = cursor.getInt(3);
            dailyStats.sessionCount = cursor.getInt(4);
            stats.add(dailyStats);
        }
        cursor.close();
        
        return stats;
    }

    /**
     * Get total study time across all sessions.
     */
    public long getTotalStudyTime() {
        String query = "SELECT SUM(" + StudyDatabase.COLUMN_FOCUS_DURATION + ") FROM " 
                + StudyDatabase.TABLE_SESSIONS;
        
        Cursor cursor = database.rawQuery(query, null);
        
        long total = 0;
        if (cursor.moveToFirst()) {
            total = cursor.getLong(0);
        }
        cursor.close();
        
        return total;
    }

    /**
     * Get count of days where the goal was achieved.
     */
    public int getGoalsAchievedCount(long dailyGoalMs) {
        String query = "SELECT COUNT(*) FROM (" +
                "SELECT date(" + StudyDatabase.COLUMN_START_TIME + "/1000, 'unixepoch', 'localtime') as day, " +
                "SUM(" + StudyDatabase.COLUMN_FOCUS_DURATION + ") as daily_total " +
                "FROM " + StudyDatabase.TABLE_SESSIONS + " " +
                "GROUP BY day HAVING daily_total >= ?" +
                ")";
        
        Cursor cursor = database.rawQuery(query, new String[]{String.valueOf(dailyGoalMs)});
        
        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }
        cursor.close();
        
        return count;
    }

    /**
     * Get total notifications blocked across all sessions.
     */
    public int getTotalNotificationsBlocked() {
        String query = "SELECT SUM(" + StudyDatabase.COLUMN_NOTIFICATIONS_BLOCKED + ") FROM " 
                + StudyDatabase.TABLE_SESSIONS;
        
        Cursor cursor = database.rawQuery(query, null);
        
        int total = 0;
        if (cursor.moveToFirst()) {
            total = cursor.getInt(0);
        }
        cursor.close();
        
        return total;
    }

    /**
     * Get total distractions across all sessions.
     */
    public int getTotalDistractions() {
        String query = "SELECT SUM(" + StudyDatabase.COLUMN_DISTRACTIONS + ") FROM " 
                + StudyDatabase.TABLE_SESSIONS;
        
        Cursor cursor = database.rawQuery(query, null);
        
        int total = 0;
        if (cursor.moveToFirst()) {
            total = cursor.getInt(0);
        }
        cursor.close();
        
        return total;
    }

    /**
     * Get average session time in milliseconds.
     */
    public long getAverageSessionTime() {
        String query = "SELECT AVG(" + StudyDatabase.COLUMN_FOCUS_DURATION + ") FROM " 
                + StudyDatabase.TABLE_SESSIONS;
        
        Cursor cursor = database.rawQuery(query, null);
        
        long avg = 0;
        if (cursor.moveToFirst()) {
            avg = cursor.getLong(0);
        }
        cursor.close();
        
        return avg;
    }

    // Helper methods
    
    private long parseDateTime(String dateTime) {
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            Date date = format.parse(dateTime);
            return date != null ? date.getTime() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private long getStartOfDay(String date) {
        return parseDateTime(date + " 00:00:00");
    }

    private long getEndOfDay(String date) {
        return parseDateTime(date + " 23:59:59");
    }
}
