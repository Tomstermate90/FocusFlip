package com.alex_lior_tomer.focusflip.database.models;

/**
 * Model representing aggregated daily statistics.
 * Used for displaying daily summaries and charts.
 */
public class DailyStats {
    
    public String date;
    public long totalTime;
    public int totalDistractions;
    public int totalNotificationsBlocked;
    public int sessionCount;

    public DailyStats() {
    }

    public DailyStats(String date, long totalTime, int totalDistractions, 
                     int totalNotificationsBlocked, int sessionCount) {
        this.date = date;
        this.totalTime = totalTime;
        this.totalDistractions = totalDistractions;
        this.totalNotificationsBlocked = totalNotificationsBlocked;
        this.sessionCount = sessionCount;
    }

    /**
     * Get average session duration for this day.
     */
    public long getAverageSessionDuration() {
        if (sessionCount == 0) return 0;
        return totalTime / sessionCount;
    }
}
