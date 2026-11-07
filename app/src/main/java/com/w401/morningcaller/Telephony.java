package com.w401.morningcaller;

import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;

/** 시스템 Telecom에 PhoneAccount를 등록하고 가짜 수신 전화를 넣는 도우미. */
final class Telephony {
    private static final String TAG = "MorningCaller";
    private static final String ACCOUNT_ID = "morningcaller_account";

    private Telephony() {}

    static PhoneAccountHandle handle(Context c) {
        return new PhoneAccountHandle(new ComponentName(c, FakeCallService.class), ACCOUNT_ID);
    }

    static void registerAccount(Context c) {
        TelecomManager tm = c.getSystemService(TelecomManager.class);
        PhoneAccount account = PhoneAccount.builder(handle(c), c.getString(R.string.account_label))
                // self-managed가 아닌 일반 통화 제공자 → 기본 전화 앱(InCallUI)이 수신 화면을 그려 줌
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .setShortDescription(c.getString(R.string.account_label))
                .build();
        tm.registerPhoneAccount(account);
    }

    static boolean isAccountEnabled(Context c) {
        try {
            TelecomManager tm = c.getSystemService(TelecomManager.class);
            PhoneAccount a = tm.getPhoneAccount(handle(c));
            return a != null && a.isEnabled();
        } catch (RuntimeException e) {
            Log.w(TAG, "getPhoneAccount failed", e);
            return false;
        }
    }

    /** @return null 이면 성공, 아니면 오류 메시지 */
    static String triggerIncomingCall(Context c) {
        String number = Prefs.get(c).getString(Prefs.NUMBER, "");
        if (number.isEmpty()) return c.getString(R.string.err_no_number);
        registerAccount(c);
        Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS,
                Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null));
        try {
            c.getSystemService(TelecomManager.class).addNewIncomingCall(handle(c), extras);
            return null;
        } catch (SecurityException e) {
            Log.e(TAG, "addNewIncomingCall rejected", e);
            return c.getString(R.string.err_account_disabled);
        } catch (RuntimeException e) {
            Log.e(TAG, "addNewIncomingCall failed", e);
            return e.toString();
        }
    }
}
