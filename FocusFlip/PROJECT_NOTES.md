# FocusFlip — Project Notes

**Course:** Mobile App Development Workshop 2026
**Lecturer:** Ilan Perets
**Team:** Alex Politsan, Lior Cohen Twito, Tomer Levi
**Package:** `com.alex_lior_tomer.focusflip`
**Build target:** Java, Android Studio, `minSdk 26`, `targetSdk 30` (Android 11 / R)

This document explains what the app does, how it maps to Ilan's required-deliverables
list, and where in the codebase each piece lives — so anyone (us, the grader, or a
future maintainer) can read the project end-to-end without having to dig.

---

## 1. What the app does

FocusFlip is a study-focus tracker. The user lays their phone face-down on the desk;
the app detects that pose and starts counting "focus time". Picking the phone up
flips the session into a "distraction" state, increments a counter, and double-buzzes
to nudge the user back. While focusing, the app puts the phone into a chosen silence
mode (full DND, notifications-only, or vibrate-only) and a notification listener
counts every notification that arrived during the session so the user can see, after
the fact, how many distractions they were shielded from. Sessions are persisted to
SQLite, aggregated per day, and visualised on a weekly bar chart with a streak count.

A daily alarm fires at a user-chosen time to either congratulate the user (goal met)
or remind them how much focus time is left to hit today's goal.

---

## 2. Requirements → where they live in the code

Every line item from Ilan's spec, mapped to the file(s) that satisfy it.

### 2.1 Language and environment
- **Java only, Android Studio, no deprecated methods.** All sources are `.java`.
  We removed the previous `Resources.updateConfiguration` use (deprecated since
  API 25) along with the rest of the locale-switching plumbing. The `Vibrator`
  call uses `VibrationEffect` (API 26+), not the deprecated `vibrate(long)`
  overload.
- **API 30 target.** `FocusFlip/app/build.gradle` → `targetSdk 30`. `compileSdk`
  is 34 because AppCompat 1.6.1 and Material 1.11.0 require it; this does not
  change runtime behaviour — the app still declares Android 11 as its target
  platform.

### 2.2 Mandatory activities (≥3)
We ship four.
- **SplashActivity** (`activities/SplashActivity.java`) — shows the logo, app name,
  team names, and version (via the About dialog launched from the menu). Checks
  the two required runtime permissions (DND policy access, Notification Listener
  access) before letting the user through.
- **MainActivity** (`activities/MainActivity.java`) — the home screen. Big timer,
  status card, today's progress, today's distractions, Start/Stop button, FAB to
  Statistics, and the 3-dot menu.
- **SettingsActivity** (`activities/SettingsActivity.java`) — two persistent
  settings (we ship four): daily goal (15–480 min), daily reminder time, silence
  mode (DND / notifications / vibrate). All persisted via SharedPreferences.
- **StatisticsActivity** (`activities/StatisticsActivity.java`) — weekly chart
  (goal vs actual) plus five tiles: total study time, goals achieved, notifications
  blocked, distractions detected, average session, plus a 30-day streak count.

### 2.3 App components (≥2 of Service / BroadcastReceiver / ContentProvider)
We ship five components in those categories.
- **`services/FocusService`** — bound + foreground service. Owns the session
  lifecycle, sensor listeners, vibration, DND policy, the foreground notification,
  and the local broadcast that drives the Main UI.
- **`services/NotificationMonitorService`** — `NotificationListenerService`
  subclass. Counts incoming notifications while a session is active.
- **`receivers/BootReceiver`** — listens for `BOOT_COMPLETED` and re-arms the
  daily goal-check alarm so reboots don't break reminders.
- **`receivers/GoalCheckReceiver`** — fires when the daily reminder `AlarmManager`
  triggers, looks up today's accumulated focus time, and posts either a
  congratulations notification or a "X minutes to go" reminder.

### 2.4 Hardware feature (≥1)
**Two sensors** are used. See `FocusService.onSensorChanged`:
- **Accelerometer** — `Sensor.TYPE_ACCELEROMETER`. The Z-axis value flips to a
  large negative number when the phone is laid flat face-down.
- **Proximity sensor** — `Sensor.TYPE_PROXIMITY`. We additionally require "near"
  to avoid false positives when the user just sets the phone flat with the
  screen up. The check accepts both gradient sensors (return distance in cm) and
  binary sensors (return 0 or maxRange) by treating "anything below maxRange"
  as near. Falls back to accelerometer-only when no proximity sensor exists.

### 2.5 Data management
**SQLite**, hand-rolled (no Room).
- `database/StudyDatabase.java` — `SQLiteOpenHelper`. Singleton, one table
  (`study_sessions`), one index on `start_time` for fast date queries.
- `database/StudySessionDao.java` — every query lives here. Patterns are factored
  (`sumColumn`, `sumColumnInRange`, `runDailyAggregate`) so the SQL string-building
  doesn't repeat across nine methods. Day-grouping uses SQLite's
  `date(... 'unixepoch', 'localtime')` so the user's local midnight is the boundary.
- `database/StudyRepository.java` — the Model layer. Wraps the DAO with a single
  background executor and posts callbacks back to the main thread. The DAO is
  acquired **lazily inside the executor** so the first DB open never blocks the UI.

### 2.6 Notifications (≥1 status-bar notification)
**Two channels**, both visible.
- `focus_channel` (LOW importance) — the foreground notification that runs the
  whole session. Shows live focus time vs today's goal. Refreshes when focus
  state flips or every 30s (not every second — `NOTIFICATION_REFRESH_MS` in
  `FocusService`).
- `goal_reminder_channel` (HIGH importance) — fired by `GoalCheckReceiver`.
  Either "Well done!" (goal met) or "You have X left" (goal pending).

### 2.7 Background threading (≥1)
- `StudyRepository.executor` — single-thread executor. Every read/write to SQLite
  goes through it. Results are posted back via a main-thread Handler.
- `FocusService.executor` — separate single-thread executor used only for
  `saveSession()` on stop, so writing the row never blocks `stopForeground`.
- `GoalCheckReceiver` spins up a one-shot executor to read today's total without
  holding up the receiver thread.

### 2.8 App bar with 3-dot menu
`MainActivity.onCreateOptionsMenu` inflates `res/menu/main_menu.xml`:
- **Settings** — launches `SettingsActivity`.
- **About** — `AlertDialog` showing team names, version, API level, course.
- **Exit** — confirmation dialog. On confirm: stops any running session, then
  `finishAffinity()` to kill the whole task.

### 2.9 OOP / MVC / documentation
- **Model**: `database/` package (DB, DAO, repository, models).
- **View**: `res/layout/*.xml`, `res/values/*.xml`, the styled cards and chart.
- **Controller**: the activities and the service — they react to user/system
  events and orchestrate the model and the view.
- **Documentation**: every class has a one-line purpose comment at the top
  saying *why* it exists, not what every line does.

---

## 3. Architectural decisions worth knowing

A short list of choices that aren't obvious from reading the code, with the
reason we made them:

1. **Single-language (English).** We initially shipped a Hebrew translation under
   `values-he/`. AppCompat's per-app locale switching, the
   `attachBaseContext` + `createConfigurationContext` pattern, and the
   `AppCompatDelegate.setApplicationLocales` API all produced inconsistent
   results on API 30 — strings stayed English while layout direction flipped to
   RTL, or vice versa. Rather than ship a half-broken language switch, we
   removed the Hebrew translation, the language toggle, and `LocaleHelper`
   entirely. The app is English-only.
2. **Optimistic UI fixes.**
   - The Start/Stop button used to flip to "Stop" instantly when the user
     pressed Start, even though the foreground service hadn't actually bound
     yet. We now **disable** the button on Start and only re-enable + relabel it
     when `ServiceConnection.onServiceConnected` confirms the service is up.
   - Stats tiles used to flash "0" then jump to the real DB value on every
     screen open. We now keep a small snapshot cache in SharedPreferences —
     today's value is date-stamped (a stale day is ignored) — so the first
     paint shows last-known values, then the async DB callback overwrites.
3. **Notification cadence.** The foreground notification rebuilds on focus-state
   flip or once every 30s (not every 1s) — gentler on the system. The main UI
   broadcast still ticks at 1Hz because it's local and free.
4. **Wake lock capped at 4h.** Long enough for any realistic single study block,
   short enough that a forgotten session won't drain the battery overnight.
5. **DAO acquisition is lazy on the background executor.** Avoids the
   first-launch DB-open call running on the main thread, which would cause a
   visible stutter on a cold start.

---

## 4. File map

```
FocusFlip/app/src/main/
├── AndroidManifest.xml          permissions, components, launcher icon
├── java/com/alex_lior_tomer/focusflip/
│   ├── activities/
│   │   ├── SplashActivity.java         logo + permission gate
│   │   ├── MainActivity.java           home screen, timer, start/stop
│   │   ├── SettingsActivity.java       goal + reminder + silence mode
│   │   └── StatisticsActivity.java     weekly chart + tiles + streak
│   ├── services/
│   │   ├── FocusService.java                 foreground service, sensors, DND
│   │   └── NotificationMonitorService.java   counts notifications during session
│   ├── receivers/
│   │   ├── BootReceiver.java            re-arms alarm on boot
│   │   └── GoalCheckReceiver.java       daily goal-check notification
│   ├── database/
│   │   ├── StudyDatabase.java           SQLiteOpenHelper singleton
│   │   ├── StudySessionDao.java         all SQL
│   │   ├── StudyRepository.java         Model layer, threading, callbacks
│   │   └── models/
│   │       ├── StudySession.java        one row
│   │       └── DailyStats.java          one aggregated day
│   └── utils/
│       ├── PreferencesManager.java       settings + stats snapshot cache
│       └── TimeUtils.java                duration formatters
└── res/
    ├── layout/   activity_main, _settings, _statistics, _splash
    ├── menu/     main_menu (3-dot menu items)
    ├── values/   strings, colors, themes (English only)
    └── drawable/ icons, progress bar, glow circle
```

---

## 5. Permissions

Declared in `AndroidManifest.xml`:
- `FOREGROUND_SERVICE` — required for `FocusService.startForeground`.
- `ACCESS_NOTIFICATION_POLICY` — needed to set the DND interruption filter.
- `RECEIVE_BOOT_COMPLETED` — for `BootReceiver` to re-arm the alarm.
- `VIBRATE` — focus/distraction haptics.
- `WAKE_LOCK` — keeps CPU running so sensor callbacks fire while the screen
  is off.

Runtime-gated permissions (checked in `SplashActivity`):
- **DND policy access** — `Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS`.
- **Notification Listener access** —
  `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`.

The Splash screen will park the user behind a "Grant Permissions" button until
both are granted, then proceed to Main.

---

## 6. Test plan (manual)

These are the flows we exercise before each submission build.

**Permissions gate** — Fresh install. Splash should park until both permissions
are granted, then proceed to Main.

**Focus session — happy path**
1. Tap **Start Session**. Button disables briefly, then reads "Stop Session".
2. Place phone face-down (or emulator: Extended Controls → Virtual sensors →
   Device Pose → Rotation X = 180°). Expect: single vibration, "Focus Session
   Active", green status card.
3. Pick phone up. Expect: double vibration, "Session Paused", warning-coloured
   status card, distractions counter increments.
4. Tap **Stop Session**. Button reverts. The foreground notification disappears.

**Persistence**
1. Run a short session (~1 minute). Stop.
2. Pull the **Statistics** screen open. The session shows on the weekly chart
   under today's bar. Total Study Time, Distractions Detected, etc. all
   incremented.
3. Kill the app from recents. Re-open. Stats screen still shows correct totals
   (DB persistence) **and** paints instantly without flashing "0"
   (SharedPreferences snapshot cache).

**Silence mode**
1. In Settings, pick "Silence All". Save. Start a session.
2. Send a test notification from another device or another app on the emulator.
   Expect: the notification is silenced. The Notifications Blocked count
   on Statistics goes up after stop.

**Daily reminder**
1. In Settings, set reminder time to ~2 min from now. Save.
2. Wait. Expect a goal-reminder notification (high priority) showing how much
   time is left to reach today's goal.

**3-dot menu**
- Settings → opens settings, back returns to Main.
- About → shows team names, version, API level, course.
- Exit → confirmation dialog; on confirm any active session is stopped and
  the task is killed.
