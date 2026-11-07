package com.w401.morningcaller;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;

public class FakeCallService extends ConnectionService {

    @Override
    public Connection onCreateIncomingConnection(PhoneAccountHandle handle, ConnectionRequest request) {
        SharedPreferences p = Prefs.get(this);

        Uri address = null;
        Bundle extras = request.getExtras();
        if (extras != null) address = extras.getParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS);
        if (address == null) address = request.getAddress();
        if (address == null) {
            address = Uri.fromParts(PhoneAccount.SCHEME_TEL, p.getString(Prefs.NUMBER, ""), null);
        }

        String audio = p.getString(Prefs.AUDIO_URI, null);
        FakeConnection conn = new FakeConnection(getApplicationContext(),
                audio == null ? null : Uri.parse(audio),
                p.getBoolean(Prefs.HANGUP_AFTER, true));

        conn.setAddress(address, TelecomManager.PRESENTATION_ALLOWED);
        String name = p.getString(Prefs.NAME, "");
        if (!name.isEmpty()) conn.setCallerDisplayName(name, TelecomManager.PRESENTATION_ALLOWED);
        conn.setConnectionCapabilities(Connection.CAPABILITY_HOLD
                | Connection.CAPABILITY_SUPPORT_HOLD
                | Connection.CAPABILITY_MUTE);
        // VoIP 오디오 모드(MODE_IN_COMMUNICATION) → 앱의 MediaPlayer 소리가 통화 경로(수화부/스피커)로 나감
        conn.setAudioModeIsVoip(true);
        conn.setRinging();
        return conn;
    }

    @Override
    public void onCreateIncomingConnectionFailed(PhoneAccountHandle handle, ConnectionRequest request) {
        Log.e("MorningCaller", "onCreateIncomingConnectionFailed");
    }
}
