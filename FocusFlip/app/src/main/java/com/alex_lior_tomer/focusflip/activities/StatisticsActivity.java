package com.alex_lior_tomer.focusflip.activities;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.alex_lior_tomer.focusflip.R;
import com.alex_lior_tomer.focusflip.database.StudyRepository;
import com.alex_lior_tomer.focusflip.database.models.DailyStats;
import com.alex_lior_tomer.focusflip.utils.LocaleHelper;
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
    private Handler uiHandler;

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);

        repository = new StudyRepository(this);
        preferencesManager = new PreferencesManager(this);
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
        repository.getWeeklyStats(weeklyStats -> {
            updateChart(weeklyStats);
        });

        repository.getTotalStudyTime(totalTime -> {
            totalTimeText.setText(TimeUtils.formatDurationLong(totalTime, this));
        });

        long dailyGoalMs = preferencesManager.getDailyGoalMinutes() * 60 * 1000L;
        repository.getGoalsAchievedCount(dailyGoalMs, goalsAchieved -> {
            goalsAchievedText.setText(String.valueOf(goalsAchieved));
        });

        repository.getTotalNotificationsBlocked(notificationsBlocked -> {
            notificationsBlockedText.setText(String.valueOf(notificationsBlocked));
        });

        repository.getTotalDistractions(distractions -> {
            distractionsText.setText(String.valueOf(distractions));
        });

        repository.getAverageSessionTime(averageSession -> {
            averageSessionText.setText(TimeUtils.formatDuration(averageSession));
        });

        calculateStreak();
    }

    private void calculateStreak() {
        repository.getRecentStats(30, recentStats -> {
            int dailyGoalMs = preferencesManager.getDailyGoalMinutes() * 60 * 1000;
            int streak = 0;
            Calendar calendar = Calendar.getInstance();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

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
        String lang = preferencesManager.getAppLanguage();
        SimpleDateFormat dayFormat = new SimpleDateFormat("EEE", new Locale(lang));

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
