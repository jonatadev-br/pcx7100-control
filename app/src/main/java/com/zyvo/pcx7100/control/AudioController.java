package com.zyvo.pcx7100.control;

import android.content.Context;
import android.media.AudioManager;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.Virtualizer;
import android.util.Log;
import android.view.KeyEvent;

/**
 * Controls the sound that leaves the phone towards the PCX7100 speaker.
 *
 * The PCX7100 only accepts audio streaming (A2DP) + basic media transport (AVRCP).
 * It has NO remote protocol for its own EQ / lights / Ex Bass (those are physical
 * buttons on the speaker). So we shape the sound on the phone side:
 *  - Volume      -> AudioManager STREAM_MUSIC (drives the speaker volume via AVRCP absolute volume)
 *  - Play/Pause/Next/Prev -> media key events (sent to the speaker via AVRCP)
 *  - Equalizer   -> android.media.audiofx.Equalizer on the global output mix (real DSP)
 *  - Ex Bass     -> BassBoost
 *  - 3D          -> Virtualizer
 */
public class AudioController {
    private static final String TAG = "PCX7100_Audio";

    private final AudioManager audioManager;

    private Equalizer equalizer;
    private BassBoost bassBoost;
    private Virtualizer virtualizer;

    // 0 = global output mix, so the effects apply to everything the phone plays
    // (including the Bluetooth stream going to the speaker).
    private static final int GLOBAL_SESSION = 0;

    public AudioController(Context context) {
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        initEffects();
    }

    private void initEffects() {
        try {
            equalizer = new Equalizer(0, GLOBAL_SESSION);
            equalizer.setEnabled(true);
        } catch (Exception e) {
            Log.e(TAG, "Equalizer init failed", e);
            equalizer = null;
        }
        try {
            bassBoost = new BassBoost(0, GLOBAL_SESSION);
            bassBoost.setEnabled(false);
        } catch (Exception e) {
            Log.e(TAG, "BassBoost init failed", e);
            bassBoost = null;
        }
        try {
            virtualizer = new Virtualizer(0, GLOBAL_SESSION);
            virtualizer.setEnabled(false);
        } catch (Exception e) {
            Log.e(TAG, "Virtualizer init failed", e);
            virtualizer = null;
        }
    }

    // ---------------- Volume ----------------
    public int getMaxVolume() {
        return audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    }

    public int getVolume() {
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
    }

    public void setVolume(int value) {
        int max = getMaxVolume();
        int clamped = Math.max(0, Math.min(max, value));
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, clamped, 0);
    }

    // ---------------- Media transport ----------------
    public void sendMediaKey(int keyCode) {
        long now = System.currentTimeMillis();
        audioManager.dispatchMediaKeyEvent(
                new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
        audioManager.dispatchMediaKeyEvent(
                new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
    }

    // ---------------- Equalizer ----------------
    public boolean hasEqualizer() {
        return equalizer != null;
    }

    public short getBandCount() {
        return equalizer != null ? equalizer.getNumberOfBands() : 0;
    }

    public short[] getBandLevelRange() {
        if (equalizer != null) return equalizer.getBandLevelRange();
        return new short[]{-1500, 1500};
    }

    /** Center frequency of a band, in milliHertz (e.g. 60000 == 60 Hz). */
    public int getCenterFreq(int band) {
        return equalizer != null ? equalizer.getCenterFreq((short) band) : 0;
    }

    public void setBandLevel(int band, short level) {
        if (equalizer != null) equalizer.setBandLevel((short) band, level);
    }

    public short getBandLevel(int band) {
        return equalizer != null ? equalizer.getBandLevel((short) band) : 0;
    }

    /** Human readable frequency label for a band, e.g. "60 Hz" or "3.6 kHz". */
    public String bandLabel(int band) {
        int mHz = getCenterFreq(band);      // milliHertz
        int hz = mHz / 1000;
        if (hz >= 1000) {
            double k = hz / 1000.0;
            String s = (k == Math.floor(k)) ? String.valueOf((int) k)
                    : String.format(java.util.Locale.US, "%.1f", k);
            return s + "k";
        }
        return hz + "";
    }

    // ---------------- Ex Bass (BassBoost) ----------------
    public boolean hasBassBoost() {
        return bassBoost != null;
    }

    /** percent 0..100 */
    public void setBassStrength(int percent) {
        if (bassBoost == null) return;
        int p = Math.max(0, Math.min(100, percent));
        bassBoost.setStrength((short) (p * 10)); // 0..1000
        bassBoost.setEnabled(p > 0);
    }

    // ---------------- 3D (Virtualizer) ----------------
    public boolean hasVirtualizer() {
        return virtualizer != null;
    }

    /** percent 0..100 */
    public void setVirtualizerStrength(int percent) {
        if (virtualizer == null) return;
        int p = Math.max(0, Math.min(100, percent));
        virtualizer.setStrength((short) (p * 10)); // 0..1000
        virtualizer.setEnabled(p > 0);
    }

    public void release() {
        if (equalizer != null) {
            try { equalizer.release(); } catch (Exception ignored) {}
            equalizer = null;
        }
        if (bassBoost != null) {
            try { bassBoost.release(); } catch (Exception ignored) {}
            bassBoost = null;
        }
        if (virtualizer != null) {
            try { virtualizer.release(); } catch (Exception ignored) {}
            virtualizer = null;
        }
    }
}
