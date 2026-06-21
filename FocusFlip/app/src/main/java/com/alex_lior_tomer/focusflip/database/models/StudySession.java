package com.alex_lior_tomer.focusflip.database.models;

public class StudySession {

    public long id;
    public long startTime;
    public long endTime;
    public long focusDuration;
    public int distractionsCount;
    public int notificationsBlocked;

    public StudySession() {
    }

    public StudySession(long startTime, long endTime, long focusDuration,
                        int distractionsCount, int notificationsBlocked) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.focusDuration = focusDuration;
        this.distractionsCount = distractionsCount;
        this.notificationsBlocked = notificationsBlocked;
    }

    public long getTotalDuration() {
        return endTime - startTime;
    }

    public String getDateString() {
        java.text.SimpleDateFormat dateFormat =
                new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
        return dateFormat.format(new java.util.Date(startTime));
    }
}
