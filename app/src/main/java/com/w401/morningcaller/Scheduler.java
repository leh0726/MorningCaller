package com.w401.morningcaller;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.format.DateFormat;

import java.util.Calendar;

/** 예약 시각 계산, AlarmManager 등록/취소, 예약 상태 관리. */
final class Scheduler {
    private Scheduler() {}

    private static PendingIntent pendingIntent(Context c, int extraFlags) {
        return PendingIntent.getBroadcast(c, 0, new Intent(c, CallTriggerReceiver.class),
                extraFlags | PendingIntent.FLAG_IMMUTABLE);
    }

    /** 현재 설정(타이머/시각 지정)으로 전화가 걸려올 시각(ms). */
    static long nextTriggerMillis(Context c) {
        SharedPreferences p = Prefs.get(c);
        if (Prefs.isClockMode(c)) {
            return nextClockMillis(p.getInt(Prefs.CLOCK_H, 9), p.getInt(Prefs.CLOCK_M, 0));
        }
        return System.currentTimeMillis() + Prefs.delaySeconds(c) * 1000L;
    }

    static long nextClockMillis(int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1);
        return cal.getTimeInMillis();
    }

    /** @return 정확한 알람으로 등록됐으면 true */
    static boolean schedule(Context c, long at) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = pendingIntent(c, PendingIntent.FLAG_UPDATE_CURRENT);
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        if (exact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
        Prefs.get(c).edit().putLong(Prefs.SCHEDULED_AT, at).apply();
        return exact;
    }

    static void cancel(Context c) {
        PendingIntent pi = pendingIntent(c, PendingIntent.FLAG_NO_CREATE);
        if (pi != null) {
            c.getSystemService(AlarmManager.class).cancel(pi);
            pi.cancel();
        }
        Prefs.get(c).edit().remove(Prefs.SCHEDULED_AT).apply();
    }

    /** 예약된 시각, 없으면 0. 재부팅 등으로 알람이 사라진 경우도 정리한다. */
    static long scheduledAt(Context c) {
        long at = Prefs.get(c).getLong(Prefs.SCHEDULED_AT, 0);
        if (at == 0) return 0;
        if (pendingIntent(c, PendingIntent.FLAG_NO_CREATE) == null
                || at < System.currentTimeMillis() - 60_000) {
            cancel(c);
            return 0;
        }
        return at;
    }

    // ---- 표시용 문자열 ----

    static String formatDuration(Context c, long sec) {
        if (sec <= 0) return c.getString(R.string.immediately);
        long h = sec / 3600, m = (sec % 3600) / 60, s = sec % 60;
        StringBuilder b = new StringBuilder();
        if (h > 0) b.append(c.getString(R.string.fmt_hours, h)).append(' ');
        if (m > 0) b.append(c.getString(R.string.fmt_minutes, m)).append(' ');
        if (s > 0) b.append(c.getString(R.string.fmt_seconds, s));
        return b.toString().trim();
    }

    /** 남은 시간을 타이머처럼 표시 (예: 4:05, 1:02:30) */
    static String formatCountdown(long sec) {
        if (sec < 0) sec = 0;
        long h = sec / 3600, m = (sec % 3600) / 60, s = sec % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }

    /** 시각 표시 (기기 12/24시간 설정을 따름). 오늘이 아니면 '내일'/'Tomorrow' 를 붙인다. */
    static String formatClock(Context c, long at) {
        String t = DateFormat.getTimeFormat(c).format(at);
        Calendar now = Calendar.getInstance();
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(at);
        boolean today = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
                && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR);
        return today ? t : c.getString(R.string.tomorrow_at, t);
    }
}
