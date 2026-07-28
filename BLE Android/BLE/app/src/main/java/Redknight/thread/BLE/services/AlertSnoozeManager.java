package Redknight.thread.BLE.services;

import android.content.Context;
import android.content.SharedPreferences;

public class AlertSnoozeManager {
    private static final String PREF_NAME = "TokenAlertPrefs";
    private static final String KEY_SNOOZE_TIME = "low_token_snooze_timestamp";
    private static final long TWENTY_FOUR_HOURS_MS = 24 * 60 * 60 * 1000L; // 86,400,000 ms

    private final SharedPreferences prefs;

    public AlertSnoozeManager(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Call this when the user toggles the switch to "Mute for 24 Hours"
     */
    public void snoozeNotifications() {
        prefs.edit().putLong(KEY_SNOOZE_TIME, System.currentTimeMillis()).apply();
    }

    /**
     * Call this if the user manually unmutes before the 24 hours expire
     */
    public void clearSnooze() {
        prefs.edit().putLong(KEY_SNOOZE_TIME, 0L).apply();
    }

    /**
     * Checks if notifications should be blocked right now.
     * Automatically acts as the "unmute toggle" once 24 hours pass.
     */
    public boolean isAlertMuted() {
        long snoozeTime = prefs.getLong(KEY_SNOOZE_TIME, 0L);
        if (snoozeTime == 0L) {
            return false; // Not muted
        }

        long currentTime = System.currentTimeMillis();
        long timeElapsed = currentTime - snoozeTime;

        // If the elapsed time is less than 24 hours, the alert is still muted
        if (timeElapsed < TWENTY_FOUR_HOURS_MS) {
            return true;
        } else {
            // 24 hours have naturally passed! Automatically clear the state
            clearSnooze();
            return false;
        }
    }
}