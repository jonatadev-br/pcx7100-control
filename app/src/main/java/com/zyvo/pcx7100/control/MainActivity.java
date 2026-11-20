package com.zyvo.pcx7100.control;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;

import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_BT_CONNECT = 2001;

    // Views
    private MaterialToolbar toolbar;
    private TextView connectionStatus, connectionSubtitle, volumeValue, bassValue, virtValue;
    private MaterialButton btnConnect;
    private ImageButton btnMute, btnMax, btnPrev, btnPlayPause, btnNext;
    private SeekBar volumeSeekBar, bassSeek, virtSeek;
    private ChipGroup presetGroup;
    private LinearLayout eqBandsContainer;

    // Audio engine
    private AudioController audio;

    // EQ state
    private SeekBar[] bandSeekBars;
    private TextView[] bandValueTexts;
    private short[] bandMin, bandMax;
    private boolean suppressBandListener = false;

    private boolean isPlaying = false;

    private static final String[] PRESET_NAMES = {"Flat", "Classic", "Rock", "Pop", "Jazz"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        audio = new AudioController(this);

        initViews();
        setupToolbar();
        setupVolume();
        setupMedia();
        setupEqualizer();
        setupEffects();
        setupConnection();

        requestBluetoothPermissionIfNeeded();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        connectionStatus = findViewById(R.id.connectionStatus);
        connectionSubtitle = findViewById(R.id.connectionSubtitle);
        volumeValue = findViewById(R.id.volumeValue);
        bassValue = findViewById(R.id.bassValue);
        virtValue = findViewById(R.id.virtValue);
        btnConnect = findViewById(R.id.btnConnect);
        btnMute = findViewById(R.id.btnMute);
        btnMax = findViewById(R.id.btnMax);
        btnPrev = findViewById(R.id.btnPrev);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnNext = findViewById(R.id.btnNext);
        volumeSeekBar = findViewById(R.id.volumeSeekBar);
        bassSeek = findViewById(R.id.bassSeek);
        virtSeek = findViewById(R.id.virtSeek);
        presetGroup = findViewById(R.id.presetGroup);
        eqBandsContainer = findViewById(R.id.eqBandsContainer);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(true);
        }
    }

    // ---------------- Volume ----------------
    private void setupVolume() {
        int max = audio.getMaxVolume();
        volumeSeekBar.setMax(max);
        int cur = audio.getVolume();
        volumeSeekBar.setProgress(cur);
        updateVolumeLabel(cur, max);

        volumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    audio.setVolume(progress);
                    updateVolumeLabel(progress, audio.getMaxVolume());
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnMute.setOnClickListener(v -> {
            audio.setVolume(0);
            volumeSeekBar.setProgress(0);
            updateVolumeLabel(0, audio.getMaxVolume());
        });

        btnMax.setOnClickListener(v -> {
            int m = audio.getMaxVolume();
            audio.setVolume(m);
            volumeSeekBar.setProgress(m);
            updateVolumeLabel(m, m);
        });
    }

    private void updateVolumeLabel(int cur, int max) {
        int pct = max > 0 ? Math.round(cur * 100f / max) : 0;
        volumeValue.setText(pct + "%");
    }

    // ---------------- Media ----------------
    private void setupMedia() {
        btnPlayPause.setOnClickListener(v -> {
            isPlaying = !isPlaying;
            audio.sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
            updatePlayPauseIcon();
        });
        btnPrev.setOnClickListener(v -> audio.sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS));
        btnNext.setOnClickListener(v -> audio.sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT));
        updatePlayPauseIcon();
    }

    private void updatePlayPauseIcon() {
        btnPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
        btnPlayPause.setContentDescription(
                getString(isPlaying ? R.string.media_pause : R.string.media_play));
    }

    // ---------------- Equalizer ----------------
    private void setupEqualizer() {
        if (!audio.hasEqualizer()) {
            TextView msg = new TextView(this);
            msg.setText(R.string.eq_unavailable);
            msg.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            eqBandsContainer.addView(msg);
            return;
        }

        short count = audio.getBandCount();
        short[] range = audio.getBandLevelRange();
        bandMin = new short[count];
        bandMax = new short[count];
        bandSeekBars = new SeekBar[count];
        bandValueTexts = new TextView[count];

        for (int i = 0; i < count; i++) {
            bandMin[i] = range[0];
            bandMax[i] = range[1];
            eqBandsContainer.addView(buildBandRow(i, count));
        }

        presetGroup.setOnCheckedStateChangeListener((group, checkedIds) ->
                applyPresetForCheckedId(checkedIds));

        // Apply the default (Flat) preset on start.
        applyPreset("Flat");
    }

    private View buildBandRow(int index, int count) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(6);
        row.setLayoutParams(rowParams);

        TextView freq = new TextView(this);
        freq.setText(audio.bandLabel(index));
        freq.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        freq.setTextSize(11);
        LinearLayout.LayoutParams freqParams = new LinearLayout.LayoutParams(dp(44),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        freq.setLayoutParams(freqParams);

        SeekBar bar = new SeekBar(this);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bar.setLayoutParams(barParams);
        bar.setMax(100);
        bar.setProgressDrawable(ContextCompat.getDrawable(this, R.drawable.slider_track));
        bar.setThumb(ContextCompat.getDrawable(this, R.drawable.slider_thumb));

        TextView value = new TextView(this);
        value.setTextSize(11);
        value.setTextColor(ContextCompat.getColor(this, R.color.accent));
        value.setGravity(android.view.Gravity.END);
        LinearLayout.LayoutParams valParams = new LinearLayout.LayoutParams(dp(56),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        value.setLayoutParams(valParams);

        row.addView(freq);
        row.addView(bar);
        row.addView(value);

        bandSeekBars[index] = bar;
        bandValueTexts[index] = value;

        // Initialize progress from the current device level.
        short current = audio.getBandLevel(index);
        int progress = levelToProgress(index, current);
        bar.setProgress(progress);
        value.setText(formatDb(current));

        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int p, boolean fromUser) {
                if (suppressBandListener) return;
                applyBandLevel(index, p);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        return row;
    }

    private int levelToProgress(int band, short level) {
        int min = bandMin[band], max = bandMax[band];
        if (max <= min) return 50;
        return Math.round((level - min) * 100f / (max - min));
    }

    private void applyBandLevel(int band, int progress) {
        int min = bandMin[band], max = bandMax[band];
        int level = min + Math.round((max - min) * progress / 100f);
        audio.setBandLevel(band, (short) level);
        if (bandValueTexts[band] != null) {
            bandValueTexts[band].setText(formatDb((short) level));
        }
    }

    private String formatDb(short millibels) {
        int db = Math.round(millibels / 100f);
        String sign = db > 0 ? "+" : "";
        return sign + db + " dB";
    }

    private void applyPresetForCheckedId(List<Integer> checkedIds) {
        if (checkedIds.isEmpty()) return;
        int id = checkedIds.get(0);
        if (id == R.id.chipFlat) applyPreset("Flat");
        else if (id == R.id.chipClassic) applyPreset("Classic");
        else if (id == R.id.chipRock) applyPreset("Rock");
        else if (id == R.id.chipPop) applyPreset("Pop");
        else if (id == R.id.chipJazz) applyPreset("Jazz");
    }

    private void applyPreset(String preset) {
        if (bandSeekBars == null) return;
        int n = bandSeekBars.length;
        suppressBandListener = true;
        for (int i = 0; i < n; i++) {
            double t = n > 1 ? (double) i / (n - 1) : 0.5;
            double frac = clamp01(presetFraction(preset, t));
            int progress = (int) Math.round(frac * 100);
            if (bandSeekBars[i] != null) {
                bandSeekBars[i].setProgress(progress);
            }
            applyBandLevel(i, progress);
        }
        suppressBandListener = false;
    }

    private double presetFraction(String preset, double t) {
        switch (preset) {
            case "Rock":    return 0.5 + 0.32 * Math.cos(2 * Math.PI * t);  // boost lows/highs, cut mids
            case "Pop":     return 0.5 - 0.30 * Math.cos(2 * Math.PI * t);  // boost mids
            case "Classic": return 0.5 + 0.10 * Math.sin(Math.PI * t);     // gentle warm mid
            case "Jazz":    return 0.55 + 0.10 * Math.cos(Math.PI * t);    // warm lows
            case "Flat":
            default:        return 0.5;
        }
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    // ---------------- Effects ----------------
    private void setupEffects() {
        bassSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                audio.setBassStrength(progress);
                bassValue.setText(progress + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        virtSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                audio.setVirtualizerStrength(progress);
                virtValue.setText(progress + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    // ---------------- Connection ----------------
    private void setupConnection() {
        btnConnect.setOnClickListener(v -> openBluetoothSettings());
        updateConnectionState();
    }

    private void openBluetoothSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(this, R.string.bluetooth_off, Toast.LENGTH_SHORT).show();
        }
    }

    private void updateConnectionState() {
        boolean connected = isA2dpConnected();
        if (connected) {
            connectionStatus.setText(R.string.status_connected);
            connectionStatus.setTextColor(ContextCompat.getColor(this, R.color.accent));
            connectionSubtitle.setText(R.string.device_name);
            btnConnect.setVisibility(View.GONE);
            toolbar.setSubtitle(R.string.status_connected);
        } else {
            connectionStatus.setText(R.string.status_disconnected);
            connectionStatus.setTextColor(
                    ContextCompat.getColor(this, R.color.status_disconnected));
            connectionSubtitle.setText(R.string.connect_hint);
            btnConnect.setVisibility(View.VISIBLE);
            toolbar.setSubtitle(R.string.status_disconnected);
        }
    }

    private boolean isA2dpConnected() {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) return false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
            return adapter.getProfileConnectionState(BluetoothProfile.A2DP)
                    == BluetoothProfile.STATE_CONNECTED;
        } catch (Exception e) {
            return false;
        }
    }

    private void requestBluetoothPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                        REQ_BT_CONNECT);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BT_CONNECT) {
            updateConnectionState();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Volume may have changed elsewhere (e.g. hardware keys) — resync.
        int max = audio.getMaxVolume();
        int cur = audio.getVolume();
        volumeSeekBar.setMax(max);
        volumeSeekBar.setProgress(cur);
        updateVolumeLabel(cur, max);
        updateConnectionState();
    }

    @Override
    protected void onDestroy() {
        if (audio != null) audio.release();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
