package com.w401.morningcaller;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.util.Log;

/** 가짜 통화 한 건. 받으면 지정한 오디오(mp3)를 통화 음성 경로로 재생한다. */
class FakeConnection extends Connection {
    private static final String TAG = "MorningCaller";

    private final Context context;
    private final Uri audioUri;
    private final boolean hangupAfterPlayback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private boolean ended;

    FakeConnection(Context context, Uri audioUri, boolean hangupAfterPlayback) {
        this.context = context;
        this.audioUri = audioUri;
        this.hangupAfterPlayback = hangupAfterPlayback;
    }

    @Override
    public void onAnswer() {
        setActive();
        // 통화 오디오 경로가 잡힐 시간을 조금 준 뒤 재생
        handler.postDelayed(this::startPlayback, 700);
    }

    @Override
    public void onAnswer(int videoState) {
        onAnswer();
    }

    @Override
    public void onReject() {
        end(DisconnectCause.REJECTED);
    }

    @Override
    public void onDisconnect() {
        end(DisconnectCause.LOCAL);
    }

    @Override
    public void onAbort() {
        end(DisconnectCause.CANCELED);
    }

    @Override
    public void onHold() {
        if (player != null && player.isPlaying()) player.pause();
        setOnHold();
    }

    @Override
    public void onUnhold() {
        setActive();
        if (player != null) player.start();
    }

    private void startPlayback() {
        if (ended) return;
        if (audioUri == null) {
            Log.w(TAG, "no audio selected");
            return;
        }
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            player.setDataSource(context, audioUri);
            player.setOnPreparedListener(MediaPlayer::start);
            player.setOnCompletionListener(mp -> {
                if (hangupAfterPlayback) handler.postDelayed(() -> end(DisconnectCause.REMOTE), 500);
            });
            player.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer error " + what + "/" + extra);
                return true;
            });
            player.prepareAsync();
        } catch (Exception e) {
            Log.e(TAG, "playback failed", e);
            releasePlayer();
        }
    }

    private void releasePlayer() {
        if (player != null) {
            try { player.stop(); } catch (IllegalStateException ignored) { }
            player.release();
            player = null;
        }
    }

    private void end(int cause) {
        if (ended) return;
        ended = true;
        handler.removeCallbacksAndMessages(null);
        releasePlayer();
        setDisconnected(new DisconnectCause(cause));
        destroy();
    }
}
