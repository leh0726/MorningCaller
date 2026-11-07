package com.w401.morningcaller;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = this::refreshScheduleUi;
    private Button scheduleButton;
    private TextView scheduleStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Telephony.registerAccount(this);

        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .replace(R.id.container, new SettingsFragment())
                    .commit();
        }

        scheduleButton = findViewById(R.id.schedule);
        scheduleStatus = findViewById(R.id.schedule_status);

        findViewById(R.id.call_now).setOnClickListener(v -> {
            if (checkReady()) report(Telephony.triggerIncomingCall(this), getString(R.string.calling_now));
        });
        scheduleButton.setOnClickListener(v -> {
            if (Scheduler.scheduledAt(this) != 0) {
                Scheduler.cancel(this);
                Toast.makeText(this, R.string.schedule_canceled, Toast.LENGTH_SHORT).show();
                refreshScheduleUi();
            } else {
                schedule();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Prefs.get(this).registerOnSharedPreferenceChangeListener(this);
        refreshScheduleUi();
    }

    @Override
    protected void onPause() {
        Prefs.get(this).unregisterOnSharedPreferenceChangeListener(this);
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
        // 예약 시각에 전화가 걸려오면 Receiver가 예약 상태를 지움 → 화면 갱신
        if (Prefs.SCHEDULED_AT.equals(key)) refreshScheduleUi();
    }

    /** 예약 중이면 '예약 취소' 버튼과 남은 시간을, 아니면 '예약' 버튼을 보여 준다. */
    private void refreshScheduleUi() {
        handler.removeCallbacks(ticker);
        long at = Scheduler.scheduledAt(this);
        if (at == 0) {
            scheduleButton.setText(R.string.schedule);
            scheduleStatus.setVisibility(android.view.View.GONE);
            return;
        }
        long left = (at - System.currentTimeMillis() + 999) / 1000;
        scheduleButton.setText(R.string.cancel_schedule);
        scheduleStatus.setText(getString(R.string.schedule_status,
                Scheduler.formatClock(this, at), Scheduler.formatCountdown(left)));
        scheduleStatus.setVisibility(android.view.View.VISIBLE);
        handler.postDelayed(ticker, 1000 - System.currentTimeMillis() % 1000);
    }

    private boolean checkReady() {
        if (Prefs.get(this).getString(Prefs.NUMBER, "").isEmpty()) {
            report(getString(R.string.err_no_number), null);
            return false;
        }
        // 계정 활성화 여부는 미리 막지 않음: Android 8 등 일부 기기는 꺼져 있어도 수신 전화가 들어옴.
        // 실제로 거부될 때만(report) 통화 계정 설정으로 안내한다.
        return true;
    }

    private void schedule() {
        if (!checkReady()) return;
        if (!Prefs.isClockMode(this) && Prefs.delaySeconds(this) == 0) {
            report(Telephony.triggerIncomingCall(this), getString(R.string.calling_now));
            return;
        }
        long at = Scheduler.nextTriggerMillis(this);
        boolean exact = Scheduler.schedule(this, at);
        Toast.makeText(this, getString(R.string.scheduled, Scheduler.formatClock(this, at)),
                Toast.LENGTH_SHORT).show();
        refreshScheduleUi();
        if (!exact) {
            startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName())));
        }
    }

    static void openCallingAccounts(Context c) {
        try {
            c.startActivity(new Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS));
        } catch (Exception e) {
            Toast.makeText(c, R.string.open_accounts_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void report(String err, String ok) {
        if (err != null) {
            Toast.makeText(this, err, Toast.LENGTH_LONG).show();
            if (err.equals(getString(R.string.err_account_disabled))) openCallingAccounts(this);
        } else if (ok != null) {
            Toast.makeText(this, ok, Toast.LENGTH_SHORT).show();
        }
    }
}
