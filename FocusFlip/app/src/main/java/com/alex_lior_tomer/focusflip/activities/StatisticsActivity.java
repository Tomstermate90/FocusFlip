package com.alex_lior_tomer.focusflip.activities;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.alex_lior_tomer.focusflip.R;
import com.alex_lior_tomer.focusflip.database.StudyRepository;
import com.alex_lior_tomer.focusflip.database.models.DailyStats;
import com.alex_lior_tomer.focusflip.utils.PreferencesManager;
import com.alex_lior_tomer.focusflip.utils.TimeUtils;
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

public class StatisticsActivity extends AppCompatActivity {

    private BarChart weeklyChart;
    private TextView totalTimeText;
    private TextView goalsAchievedText;
    private TextView notificationsBlockedText;
    private TextView distractionsText;
    private TextView averageSessionText;
    private TextView streakText;

    private StudyRepository repository;
    private PreferencesManager preferencesManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);

        repository = new StudyRepository(this);
        preferencesManager = new PreferencesManager(this);

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
        paintFromCache();
        refreshFromDb();
        calculateStreak();
    }

    /** Paint last-known values immediately so tiles never flash "0" / "00:00". */
    private void paintFromCache() {
        long totalMs = preferencesManager.getCachedTotalMs();
        if (totalMs >= 0) totalTimeText.setText(TimeUtils.formatDurationLong(totalMs, this));

        int goals = preferencesManager.getCachedGoalsAchieved();
        if (goals >= 0) goalsAchievedText.setText(String.valueOf(goals));

        int notifs = preferencesManager.getCachedTotalNotifs();
        if (notifs >= 0) notificationsBlockedText.setText(String.valueOf(notifs));

        int distractions = preferencesManager.getCachedTotalDistractions();
        if (distractions >= 0) distractionsText.setText(String.valueOf(distractions));

        long avgMs = preferencesManager.getCachedAvgSessionMs();
        if (avgMs >= 0) averageSessionText.setText(TimeUtils.formatDuration(avgMs));
    }

    /** Pull fresh values from the DB, paint, and write back to the cache. */
    private void refreshFromDb() {
        long dailyGoalMs = preferencesManager.getDailyGoalMs();
        final long[] snap = {-1, -1, -1, -1};   // total, notifs, distractions, avg
        final int[] goalsSnap = {-1};

        repository.getWeeklyStats(this::updateChart);

        repository.getTotalStudyTime(totalTime -> {
            totalTimeText.setText(TimeUtils.formatDurationLong(totalTime, this));
            snap[0] = totalTime;
            maybeCacheLifetime(snap, goalsSnap);
        });

        repository.getGoalsAchievedCount(dailyGoalMs, goalsAchieved -> {
            goalsAchievedText.setText(String.valueOf(goalsAchieved));
            goalsSnap[0] = goalsAchieved;
            maybeCacheLifetime(snap, goalsSnap);
        });

        repository.getTotalNotificationsBlocked(notificationsBlocked -> {
            notificationsBlockedText.setText(String.valueOf(notificationsBlocked));
            snap[1] = notificationsBlocked;
            maybeCacheLifetime(snap, goalsSnap);
        });

        repository.getTotalDistractions(distractions -> {
            distractionsText.setText(String.valueOf(distractions));
            snap[2] = distractions;
            maybeCacheLifetime(snap, goalsSnap);
        });

        repository.getAverageSessionTime(averageSession -> {
            averageSessionText.setText(TimeUtils.formatDuration(averageSession));
            snap[3] = averageSession;
            maybeCacheLifetime(snap, goalsSnap);
        });
    }

    /** Write the snapshot once all five callbacks have reported in. */
    private void maybeCacheLifetime(long[] snap, int[] goalsSnap) {
        if (snap[0] < 0 || snap[1] < 0 || snap[2] < 0 || snap[3] < 0 || goalsSnap[0] < 0) return;
        preferencesManager.cacheLifetimeStats(
                snap[0], goalsSnap[0], (int) snap[1], (int) snap[2], snap[3]);
    }

    private void calculateStreak() {
        repository.getRecentStats(30, recentStats -> {
            long dailyGoalMs = preferencesManager.getDailyGoalMs();
            int streak = 0;
            Calendar calendar = Calendar.getInstance();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

            for (int i = 0; i < 30; i++) {
                String dateStr = dateFormat.format(calendar.getTime());
                DailyStats stats = findStatsForDate(recentStats, dateStr);
                
                if (stats != null && stats.totalTime >= dailyGoalMs) {
                    streak++;
                } else if (i == 0) {
                    // It's okay if today's goal isn't met yet, but check yesterday
                    continue;
                } else {
                    break;
                }
                calendar.add(Calendar.DAY_OF_YEAR, -1);
            }
            
            streakText.setText(String.format(Locale.getDefault(), "%d %s", streak, getString(R.string.days)));
        });
    }

    private void updateChart(List<DailyStats> weeklyStats) {
        List<BarEntry> goalEntries = new ArrayList<>();
        List<BarEntry> actualEntries = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        int dailyGoalMinutes = preferencesManager.getDailyGoalMinutes();
        SimpleDateFormat dayFormat = new SimpleDateFormat("EEE", Locale.ENGLISH);

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

        BarDataSet goalDataSet = new BarDataSet(goalEntries, getString(R.string.chart_label_goal));
        goalDataSet.setColor(ContextCompat.getColor(this, R.color.chart_goal));
        goalDataSet.setDrawValues(false);

        BarDataSet actualDataSet = new BarDataSet(actualEntries, getString(R.string.chart_label_actual));
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

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        repository.shutdown();
    }
}
