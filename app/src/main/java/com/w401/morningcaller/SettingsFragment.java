package com.w401.morningcaller;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceFragment;
import android.provider.OpenableColumns;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;

/** 시스템 Preference 위젯 → 삼성 기기에서는 삼성 기본 설정 화면 스타일로 그려진다. */
public class SettingsFragment extends PreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final int REQ_PICK_AUDIO = 1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getPreferenceManager().setSharedPreferencesName(Prefs.FILE);
        addPreferencesFromResource(R.xml.prefs);

        findPreference("schedule_time").setOnPreferenceClickListener(p -> {
            if (Prefs.isClockMode(getActivity())) showClockPicker();
            else showTimerPicker();
            return true;
        });
        findPreference("audio").setOnPreferenceClickListener(p -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("audio/*");
            startActivityForResult(i, REQ_PICK_AUDIO);
            return true;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        prefs().registerOnSharedPreferenceChangeListener(this);
        updateSummaries();
    }

    @Override
    public void onPause() {
        prefs().unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
        updateSummaries();
    }

    private SharedPreferences prefs() {
        return getPreferenceManager().getSharedPreferences();
    }

    private void updateSummaries() {
        Activity a = getActivity();
        if (a == null) return;
        SharedPreferences p = prefs();

        String number = p.getString(Prefs.NUMBER, "");
        findPreference(Prefs.NUMBER).setSummary(number.isEmpty() ? getString(R.string.not_set) : number);

        String name = p.getString(Prefs.NAME, "");
        findPreference(Prefs.NAME).setSummary(name.isEmpty() ? getString(R.string.name_hint) : name);

        ListPreference mode = (ListPreference) findPreference(Prefs.MODE);
        mode.setSummary(mode.getEntry());

        String time;
        if (Prefs.isClockMode(a)) {
            time = Scheduler.formatClock(a, Scheduler.nextClockMillis(
                    p.getInt(Prefs.CLOCK_H, 9), p.getInt(Prefs.CLOCK_M, 0)));
        } else {
            int sec = Prefs.delaySeconds(a);
            time = sec == 0 ? Scheduler.formatDuration(a, 0)
                    : getString(R.string.timer_summary, Scheduler.formatDuration(a, sec));
        }
        findPreference("schedule_time").setSummary(time);

        findPreference("audio").setSummary(p.getString(Prefs.AUDIO_LABEL, getString(R.string.not_set)));
    }

    /** 시계 앱 타이머처럼 시간/분/초 휠로 고르기 */
    private void showTimerPicker() {
        Activity a = getActivity();
        int sec = Prefs.delaySeconds(a);

        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        row.setPadding(pad, pad, pad, 0);

        NumberPicker h = picker(a, 23, sec / 3600);
        NumberPicker m = picker(a, 59, (sec % 3600) / 60);
        NumberPicker s = picker(a, 59, sec % 60);
        addColumn(row, h, R.string.unit_hour);
        addColumn(row, m, R.string.unit_minute);
        addColumn(row, s, R.string.unit_second);

        new AlertDialog.Builder(a)
                .setTitle(R.string.timer_dialog_title)
                .setView(row)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    int total = h.getValue() * 3600 + m.getValue() * 60 + s.getValue();
                    prefs().edit().putString(Prefs.DELAY, String.valueOf(total)).apply();
                })
                .show();
    }

    private static NumberPicker picker(Activity a, int max, int value) {
        NumberPicker p = new NumberPicker(a);
        p.setMinValue(0);
        p.setMaxValue(max);
        p.setValue(value);
        return p;
    }

    private void addColumn(LinearLayout row, NumberPicker p, int unitRes) {
        LinearLayout col = new LinearLayout(row.getContext());
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView label = new TextView(row.getContext());
        label.setText(unitRes);
        label.setGravity(Gravity.CENTER);
        col.addView(label);
        col.addView(p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        row.addView(col, lp);
    }

    /** 정확한 시각 고르기 (지난 시각이면 내일로 예약) */
    private void showClockPicker() {
        Activity a = getActivity();
        SharedPreferences p = prefs();
        new TimePickerDialog(a, (view, hour, minute) -> p.edit()
                .putInt(Prefs.CLOCK_H, hour)
                .putInt(Prefs.CLOCK_M, minute)
                .apply(),
                p.getInt(Prefs.CLOCK_H, 9), p.getInt(Prefs.CLOCK_M, 0),
                DateFormat.is24HourFormat(a)).show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_PICK_AUDIO || resultCode != Activity.RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        ContentResolver cr = getActivity().getContentResolver();
        try {
            cr.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) { }

        String label = uri.getLastPathSegment();
        try (Cursor c = cr.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) label = c.getString(0);
        } catch (Exception ignored) { }

        prefs().edit()
                .putString(Prefs.AUDIO_URI, uri.toString())
                .putString(Prefs.AUDIO_LABEL, label)
                .apply();
    }
}
