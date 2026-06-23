package com.alex_lior_tomer.focusflip.database.models;

/** Daily aggregate row returned by the weekly/recent stat queries. */
public class DailyStats {

    public String date;
    public long totalTime;
    public int totalDistractions;
    public int totalNotificationsBlocked;
    public int sessionCount;

    public DailyStats() {}
}
