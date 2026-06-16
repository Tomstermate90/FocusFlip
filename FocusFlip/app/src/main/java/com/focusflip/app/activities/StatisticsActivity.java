package com.focusflip.app.activities;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.focusflip.app.R;
import com.focusflip.app.database.StudyDatabase;
import com.focusflip.app.database.models.DailyStats;
import com.focusflip.app.utils.PreferencesManager;
import com.focusflip.app.utils.TimeUtils;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StatisticsActivity extends AppCompatActivity {

    private BarChart weeklyChart;
    private TextView totalTimeText;
    private TextView goalsAchievedText;
    private TextView notificationsBlockedText;
    private TextView distractionsText;
    private TextView averageSessionText;
    private TextView streakText;

    private StudyDatabase database;
    private PreferencesManager preferencesManager;
    private ExecutorService executor;
    private Handler uiHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);

        database = StudyDatabase.getInstance(this);
        preferencesManager = new PreferencesManager(this);
        executor = Executors.newSingleThreadExecutor();
        uiHandler = new Handler(Looper.getMainLooper());

        setupToolbar();
        initViews();
        loadStatistics();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.statistics);
        }
    }

    private void initViews() {
        weeklyChart = findViewById(R.id.weeklyChart);
        totalTimeText = findViewById(R.id.totalTimeText);
        goalsAchievedText = findViewById(R.id.goalsAchievedText);
        notificationsBlockedText = findViewById(R.id.notificationsBlockedText);
        distractionsText = findViewById(R.id.distractionsText);
        averageSessionText = findViewById(R.id.averageSessionText);
        streakText = findViewById(R.id.streakText);

        setupChart();
    }

    private void setupChart() {
        weeklyChart.setDrawBarShadow(false);
        weeklyChart.setDrawValueAboveBar(true);
        weeklyChart.getDescription().setEnabled(false);
        weeklyChart.setMaxVisibleValueCount(7);
        weeklyChart.setPinchZoom(false);
        weeklyChart.setDrawGridBackground(false);
        weeklyChart.setScaleEnabled(false);
        weeklyChart.getLegend().setEnabled(false);

        XAxis xAxis = weeklyChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));

        YAxis leftAxis = weeklyChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(ContextCompat.getColor(this, R.color.surface_variant));
        leftAxis.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        leftAxis.setAxisMinimum(0f);

        weeklyChart.getAxisRight().setEnabled(false);
    }

    private void loadStatistics() {
        executor.execute(() -> {
            // Get weekly data
            List<DailyStats> weeklyStats = database.studySessionDao().getWeeklyStats();
            
            // Calculate totals
            long totalTime = database.studySessionDao().getTotalStudyTime();
            int totalGoalsAchieved = database.studySessionDao().getGoalsAchievedCount();
            int totalNotificationsBlocked = database.studySessionDao().getTotalNotificationsBlocked();
            int totalDistractions = database.studySessionDao().getTotalDistractions();
            long averageSession = database.studySessionDao().getAverageSessionTime();
            int streak = calculateStreak();

            uiHandler.post(() -> {
                updateChart(weeklyStats);
                updateStats(totalTime, totalGoalsAchieved, totalNotificationsBlocked, 
                           totalDistractions, averageSession, streak);
            });
        });
    }

    private void updateChart(List<DailyStats> weeklyStats) {
        List<BarEntry> goalEntries = new ArrayList<>();
        List<BarEntry> actualEntries = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        int dailyGoalMinutes = preferencesManager.getDailyGoalMinutes();
        SimpleDateFormat dayFormat = new SimpleDateFormat("EEE", new Locale("he"));

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -6);

        for (int i = 0; i < 7; i++) {
            String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(calendar.getTime());
            labels.add(dayFormat.format(calendar.getTime()));

            DailyStats stats = findStatsForDate(weeklyStats, dateStr);
            float actualMinutes = stats != null ? stats.totalTime / 60000f : 0;
            
            goalEntries.add(new BarEntry(i, dailyGoalMinutes));
            actualEntries.add(new BarEntry(i, actualMinutes));

            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        BarDataSet goalDataSet = new BarDataSet(goalEntries, "יעד");
        goalDataSet.setColor(ContextCompat.getColor(this, R.color.chart_goal));
        goalDataSet.setDrawValues(false);

        BarDataSet actualDataSet = new BarDataSet(actualEntries, "בפועל");
        actualDataSet.setColor(ContextCompat.getColor(this, R.color.chart_actual));
        actualDataSet.setValueTextColor(ContextCompat.getColor(this, R.color.text_secondary));

        float groupSpace = 0.3f;
        float barSpace = 0.05f;
        float barWidth = 0.3f;

        BarData data = new BarData(goalDataSet, actualDataSet);
        data.setBarWidth(barWidth);

        weeklyChart.setData(data);
        weeklyChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        weeklyChart.getXAxis().setAxisMinimum(0f);
        weeklyChart.getXAxis().setAxisMaximum(7f);
        weeklyChart.groupBars(0f, groupSpace, barSpace);
        weeklyChart.invalidate();
    }

    private DailyStats findStatsForDate(List<DailyStats> stats, String date) {
        for (DailyStats stat : stats) {
            if (stat.date.equals(date)) {
                return stat;
            }
        }
        return null;
    }

    private void updateStats(long totalTime, int goalsAchieved, int notificationsBlocked,
                            int distractions, long averageSession, int streak) {
        totalTimeText.setText(TimeUtils.formatDurationLong(totalTime));
        goalsAchievedText.setText(String.valueOf(goalsAchieved));
        notificationsBlockedText.setText(String.valueOf(notificationsBlocked));
        distractionsText.setText(String.valueOf(distractions));
        averageSessionText.setText(TimeUtils.formatDuration(averageSession));
        streakText.setText(String.format(Locale.getDefault(), "%d %s", streak, getString(R.string.days)));
    }

    private int calculateStreak() {
        // Calculate consecutive days meeting goal
        List<DailyStats> recentStats = database.studySessionDao().getRecentStats(30);
        int dailyGoalMs = preferencesManager.getDailyGoalMinutes() * 60 * 1000;
        
        int streak = 0;
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        for (int i = 0; i < 30; i++) {
            String dateStr = dateFormat.format(calendar.getTime());
            DailyStats stats = findStatsForDate(recentStats, dateStr);
            
            if (stats != null && stats.totalTime >= dailyGoalMs) {
                streak++;
            } else if (i > 0) {
                // Break streak if not today and goal not met
                break;
            }
            
            calendar.add(Calendar.DAY_OF_YEAR, -1);
        }

        return streak;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
