package com.alex_lior_tomer.focusflip.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * SQLite database helper for FocusFlip.
 * Manages the creation and versioning of the database.
 */
public class StudyDatabase extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "focusflip.db";
    private static final int DATABASE_VERSION = 1;

    // Table name
    public static final String TABLE_SESSIONS = "study_sessions";

    // Column names
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_START_TIME = "start_time";
    public static final String COLUMN_END_TIME = "end_time";
    public static final String COLUMN_FOCUS_DURATION = "focus_duration";
    public static final String COLUMN_DISTRACTIONS = "distractions_count";
    public static final String COLUMN_NOTIFICATIONS_BLOCKED = "notifications_blocked";

    // Create table SQL
    private static final String SQL_CREATE_SESSIONS_TABLE =
            "CREATE TABLE " + TABLE_SESSIONS + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_START_TIME + " INTEGER NOT NULL, " +
                    COLUMN_END_TIME + " INTEGER NOT NULL, " +
                    COLUMN_FOCUS_DURATION + " INTEGER NOT NULL DEFAULT 0, " +
                    COLUMN_DISTRACTIONS + " INTEGER NOT NULL DEFAULT 0, " +
                    COLUMN_NOTIFICATIONS_BLOCKED + " INTEGER NOT NULL DEFAULT 0" +
                    ")";

    // Create index for faster date queries
    private static final String SQL_CREATE_DATE_INDEX =
            "CREATE INDEX idx_start_time ON " + TABLE_SESSIONS + " (" + COLUMN_START_TIME + ")";

    // Singleton instance
    private static StudyDatabase instance;
    private StudySessionDao studySessionDao;

    public static synchronized StudyDatabase getInstance(Context context) {
        if (instance == null) {
            instance = new StudyDatabase(context.getApplicationContext());
        }
        return instance;
    }

    private StudyDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_SESSIONS_TABLE);
        db.execSQL(SQL_CREATE_DATE_INDEX);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Handle database upgrades here
        // For now, just recreate the table (in production, migrate data properly)
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SESSIONS);
        onCreate(db);
    }

    /**
     * Get the DAO for study sessions.
     */
    public StudySessionDao studySessionDao() {
        if (studySessionDao == null) {
            studySessionDao = new StudySessionDao(getWritableDatabase());
        }
        return studySessionDao;
    }
}
