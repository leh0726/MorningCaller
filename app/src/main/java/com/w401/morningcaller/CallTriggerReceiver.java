package com.w401.morningcaller;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** AlarmManager 예약 시각에 가짜 수신 전화를 넣는다. */
public class CallTriggerReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Scheduler.cancel(context); // 예약 상태 정리
        String err = Telephony.triggerIncomingCall(context);
        if (err != null) Log.e("MorningCaller", "trigger failed: " + err);
    }
}
