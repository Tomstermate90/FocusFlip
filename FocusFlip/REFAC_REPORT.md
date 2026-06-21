# FocusFlip: Refactoring & Compliance Report

## 1. Initial Gaps Identified (Missing/Non-compliant)
Upon initial audit, the following items were identified as missing or requiring modification to meet the mandatory API 30 and MVC requirements:
*   **API Level Mismatch:** The project was initially targeting a higher API version, which needed to be downgraded to **API 30** (Android 11).
*   **Architecture Gaps:** While persistence was present, the **MVC (Model-View-Controller)** pattern was not fully enforced. Specifically, the activity was handling too much database logic directly.
*   **UI Deficiencies:** The mandatory **3-dot options menu** was missing or not visible due to theme conflicts.
*   **Localization:** Consistently applied across all system dialogues and notifications. Improved RTL/LTR support.
*   **Logic Errors:** The "Goals Achieved" statistic was simply counting days with any activity, rather than comparing performance against the user’s set daily goal.

## 2. Summary of Changes

### A. Architectural Refactoring (MVC Compliance)
*   **Model:** Strengthened the `StudyRepository` class to act as the single source of truth for data. It abstracts the `StudySessionDao` and handles background threading (via `ExecutorService`), ensuring the UI thread remains responsive.
*   **Controller:** Refactored `MainActivity` and `FocusService` to interact only with the `Repository` rather than the `Database` directly.
*   **View:** Maintained separation in XML layouts, using localized strings for all user-facing labels.

### B. Functional & System Improvements
*   **API Downgrade:** Updated `build.gradle` to `targetSdkVersion 30`.
*   **Options Menu:** Implemented a standard Material 3 overflow menu (3-dots) in the `MainActivity` including "Settings", "About", and "Exit".
*   **Theme Fix:** Switched to `Theme.Material3.Dark` and ensured the `Toolbar` correctly displays the menu icons and titles with high contrast.
*   **Goal Logic:** Updated `StudySessionDao` with a SQL query that aggregates daily focus time and compares it against the `dailyGoalMs` parameter to accurately count "Goals Achieved".
*   **Haptics:** Verified the `vibrate()` method in `FocusService` follows the requirement: 1 short pulse for "Focus Started" and a double pulse for "Distraction Detected".
*   **Localization & RTL:** 
    *   Implemented `values-he/strings.xml` for Hebrew and `values-en/strings.xml` for English.
    *   Verified all layout files use `Start`/`End` instead of `Left`/`Right` to ensure perfect RTL/LTR switching.

### C. Permissions & Security
*   **Splash Logic:** Refined `SplashActivity` to check for both `NotificationPolicyAccess` (DND) and `NotificationListenerService` access, which are required for the app to function on API 30.

## 3. Rationale for Changes
*   **API 30:** Ensures compatibility with the specific hardware/software environment required by the course.
*   **MVC Pattern:** Decoupling the database logic from the Activity makes the code testable, maintainable, and follows Android best practices.
*   **Theme Adjustments:** Material 3 was used to provide a modern, accessible UI that respects Dark Mode requirements while keeping the "3-dot" menu visible.

## 4. Test Plan (What to Verify)

### I. Sensor & Haptic Feedback
1.  **Start Session:** Tap "Start" and place the phone **face-down** on a flat surface.
    *   *Expectation:* A single vibration pulse. Timer starts.
2.  **Trigger Distraction:** Pick up the phone or flip it face-up.
    *   *Expectation:* A double vibration pulse. Status changes to "Paused".
3.  **Proximity Check:** While face-down, slide a piece of paper over the top of the phone (covering the proximity sensor).
    *   *Expectation:* Focus should only trigger if *both* face-down AND proximity (Near) are true.

### II. Notification & DND Logic
1.  **Silence Mode:** In Settings, select "Silence All". Start a session.
    *   *Expectation:* Phone should enter "Do Not Disturb" mode automatically.
2.  **Notification Counting:** Have another device send a message (WhatsApp/SMS) during an active focus session.
    *   *Expectation:* The notification should be silenced, and the "Distractions Blocked" counter in Statistics should increment.

### III. Statistics & Persistence
1.  **Goal Achievement:** Set a 1-minute goal in Settings. Focus for 65 seconds. End session.
    *   *Expectation:* The "Goals Achieved" count in Statistics should increase by 1.
2.  **Weekly Chart:** Verify that the bar chart shows two bars per day: your goal (set in settings) vs. your actual focus time.

### IV. UI & Navigation
1.  **Options Menu:** Tap the 3 dots in the top right.
    *   *Expectation:* Menu opens with "Settings", "About", and "Exit".
2.  **Localization:** Change system language between Hebrew and English.
    *   *Expectation:* The app layout should correctly flip (RTL for Hebrew, LTR for English) and all text should be translated.
