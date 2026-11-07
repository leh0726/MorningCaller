package com.w401.morningcaller;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    static final String FILE = "morningcaller";
    static final String NUMBER = "number";
    static final String NAME = "name";
    static final String MODE = "schedule_mode";    // "timer" | "clock"
    static final String DELAY = "delay_sec";       // String, 타이머 초
    static final String CLOCK_H = "clock_hour";
    static final String CLOCK_M = "clock_minute";
    static final String AUDIO_URI = "audio_uri";
    static final String AUDIO_LABEL = "audio_label";
    static final String HANGUP_AFTER = "hangup_after";
    static final String SCHEDULED_AT = "scheduled_at"; // long, 예약된 시각(ms), 0 = 없음

    static final String MODE_TIMER = "timer";
    static final String MODE_CLOCK = "clock";

    private Prefs() {}

    static SharedPreferences get(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static boolean isClockMode(Context c) {
        return MODE_CLOCK.equals(get(c).getString(MODE, MODE_TIMER));
    }

    static int delaySeconds(Context c) {
        try {
            return Math.max(0, Integer.parseInt(get(c).getString(DELAY, "10")));
        } catch (RuntimeException e) {
            return 10;
        }
    }
}
