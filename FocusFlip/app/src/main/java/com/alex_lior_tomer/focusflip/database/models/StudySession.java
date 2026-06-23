package com.alex_lior_tomer.focusflip.database.models;

/** A single focus session: its time bounds, how long the user actually focused,
 *  how many flip-ups happened, and how many notifications were swallowed. */
public class StudySession {

    public long id;
    public long startTime;
    public long endTime;
    public long focusDuration;
    public int distractionsCount;
    public int notificationsBlocked;

    public StudySession() {}
}
