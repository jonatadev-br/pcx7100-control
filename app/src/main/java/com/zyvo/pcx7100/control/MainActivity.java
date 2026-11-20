package com.zyvo.pcx7100.control;

import android.Manifest;
import android.animation.ValueAnimator;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

import com.zyvo.pcx7100.control.bluetooth.BluetoothConnectionService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "PCX7100_MainActivity";
    private static final int REQ_PERMISSIONS = 1001;
    private static final int REQ_ENABLE_BT = 1002;

    // Connection states
    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    // Views
    private MaterialToolbar toolbar;
    private BottomNavigationView bottomNav;
    private CardView connectionCard;
    private ImageView connectionIcon;
    private TextView connectionStatus, connectionSubtitle;
    private MaterialButton btnScanConnect;
    private SeekBar volumeSeekBar;
    private TextView volumeValue;
    private ImageView mediaArtwork;
    private TextView mediaTitle, mediaArtist;
    private MaterialButton btnPrev, btnPlayPause, btnNext;
    private LinearLayout eqBarsContainer, presetContainer;

    // State
    private boolean isPlaying = false;
    private int currentVolume = 68;
    private String currentPreset = "Flat";
    private final List<String> presets = Arrays.asList("Flat", "Bass", "Pop", "Rock", "Jazz");
    private final int[][] presetBands = {
        {35, 55, 75, 45, 65, 30, 50}, // Flat
        {80, 70, 50, 30, 20, 15, 10}, // Bass
        {40, 50, 70, 80, 60, 40, 30}, // Pop
        {60, 55, 65, 75, 80, 70, 55}, // Rock
        {30, 40, 60, 80, 70, 50, 35}  // Jazz
    };

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable eqAnimationRunnable;
    private int connectionState = STATE_DISCONNECTED;

    // Broadcast receiver for Bluetooth state
    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                updateBluetoothUI(state);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupToolbar();
        setupBottomNav();
        setupVolumeSeekBar();
        setupMediaControls();
        setupEqBars();
        setupPresets();
        setupConnectionCard();
        checkPermissionsAndInit();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        bottomNav = findViewById(R.id.bottomNav);
        connectionCard = findViewById(R.id.connectionCard);
        connectionIcon = findViewById(R.id.connectionIcon);
        connectionStatus = findViewById(R.id.connectionStatus);
        connectionSubtitle = findViewById(R.id.connectionSubtitle);
        btnScanConnect = findViewById(R.id.btnScanConnect);
        volumeSeekBar = findViewById(R.id.volumeSeekBar);
        volumeValue = findViewById(R.id.volumeValue);
        mediaArtwork = findViewById(R.id.mediaArtwork);
        mediaTitle = findViewById(R.id.mediaTitle);
        mediaArtist = findViewById(R.id.mediaArtist);
        btnPrev = findViewById(R.id.btnPrev);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnNext = findViewById(R.id.btnNext);
        eqBarsContainer = findViewById(R.id.eqBarsContainer);
        presetContainer = findViewById(R.id.presetContainer);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(true);
        }
    }

    private void setupBottomNav() {
        bottomNav.setSelectedItemId(R.id.nav_control);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_control) {
                return true;
            } else if (id == R.id.nav_eq) {
                showEqBottomSheet();
                return true;
            } else if (id == R.id.nav_media) {
                NestedScrollView sv = findViewById(R.id.scrollView);
                sv.post(() -> sv.smoothScrollTo(0, mediaArtwork.getTop()));
                return true;
            } else if (id == R.id.nav_lights) {
                showLightsBottomSheet();
                return true;
            } else if (id == R.id.nav_settings) {
                showSettingsBottomSheet();
                return true;
            }
            return false;
        });
    }

    private void setupVolumeSeekBar() {
        volumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    currentVolume = progress;
                    volumeValue.setText(progress + "%");
                    sendVolumeCommand(progress);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void setupMediaControls() {
        btnPlayPause.setOnClickListener(v -> {
            isPlaying = !isPlaying;
            updatePlayPauseButton();
            sendMediaCommand(isPlaying ? "play" : "pause");
        });
        btnPrev.setOnClickListener(v -> sendMediaCommand("prev"));
        btnNext.setOnClickListener(v -> sendMediaCommand("next"));
    }

    private void setupEqBars() {
        eqBarsContainer.removeAllViews();
        for (int i = 0; i < 7; i++) {
            View bar = new View(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, 35, 1f);
            params.setMargins(8, 0, 8, 0);
            bar.setLayoutParams(params);
            bar.setBackgroundResource(R.drawable.eq_bar);
            eqBarsContainer.addView(bar);
        }
        applyPresetBands(presetBands[0]);
        startEqAnimation();
    }

    private void setupPresets() {
        presetContainer.removeAllViews();
        for (int i = 0; i < presets.size(); i++) {
            final int index = i;
            MaterialButton btn = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btn.setText(presets.get(i));
            btn.setTextSize(11);
            btn.setAllCaps(true);
            btn.setLetterSpacing(0.08f);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(5, 5, 5, 5);
            btn.setLayoutParams(params);
            btn.setCheckable(true);
            btn.setChecked(i == 0);
            btn.setOnClickListener(v -> selectPreset(index));
            presetContainer.addView(btn);
        }
    }

    private void setupConnectionCard() {
        btnScanConnect.setOnClickListener(v -> {
            if (connectionState == STATE_CONNECTED) {
                disconnect();
            } else {
                startScan();
            }
        });
        connectionCard.setOnClickListener(v -> {
            if (connectionState != STATE_CONNECTED) {
                startScan();
            }
        });
    }

    private void checkPermissionsAndInit() {
        String[] perms;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms = new String[]{
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            };
        } else {
            perms = new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            };
        }

        List<String> needed = new ArrayList<>();
        for (String perm : perms) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                needed.add(perm);
            }
        }

        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), REQ_PERMISSIONS);
        } else {
            startBluetoothService();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                startBluetoothService();
            } else {
                Toast.makeText(this, "Permissões necessárias para Bluetooth", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startBluetoothService() {
        Intent serviceIntent = new Intent(this, BluetoothConnectionService.class);
        startService(serviceIntent);
        // Service runs in foreground, UI updates via broadcasts in real implementation
    }

    private void startScan() {
        if (!PCXApplication.getInstance().isBluetoothEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQ_ENABLE_BT);
            return;
        }

        updateConnectionUI(STATE_CONNECTING);
        // Simulate scan - in real app, call btService.startScan() via bindService
        uiHandler.postDelayed(() -> {
            BluetoothDevice mockDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:44:55");
            onDeviceFound(mockDevice);
        }, 2000);
    }

    private void onDeviceFound(BluetoothDevice device) {
        updateConnectionUI(STATE_CONNECTING);
        uiHandler.postDelayed(() -> onConnected(device), 1500);
    }

    private void onConnected(BluetoothDevice device) {
        updateConnectionUI(STATE_CONNECTED);
        uiHandler.postDelayed(() -> {
            currentVolume = 68;
            volumeSeekBar.setProgress(currentVolume);
            volumeValue.setText(currentVolume + "%");
            toolbar.setSubtitle("Bateria: 78%");
        }, 500);
    }

    private void disconnect() {
        updateConnectionUI(STATE_DISCONNECTED);
        // In real app: btService.disconnect()
    }

    private void updateConnectionUI(int state) {
        connectionState = state;
        switch (state) {
            case STATE_DISCONNECTED:
                connectionIcon.setImageResource(R.drawable.ic_bluetooth_disconnected);
                connectionIcon.setColorFilter(ContextCompat.getColor(this, R.color.status_disconnected));
                connectionStatus.setText(R.string.status_disconnected);
                connectionStatus.setTextColor(ContextCompat.getColor(this, R.color.status_disconnected));
                connectionSubtitle.setText(R.string.tap_to_scan);
                btnScanConnect.setText(R.string.tap_to_scan);
                toolbar.setSubtitle(R.string.status_disconnected);
                break;
            case STATE_CONNECTING:
                connectionIcon.setImageResource(R.drawable.ic_bluetooth_connecting);
                connectionIcon.setColorFilter(ContextCompat.getColor(this, R.color.status_connecting));
                connectionStatus.setText(R.string.status_connecting);
                connectionStatus.setTextColor(ContextCompat.getColor(this, R.color.status_connecting));
                connectionSubtitle.setText("Conectando...");
                btnScanConnect.setText("Cancelar");
                toolbar.setSubtitle(R.string.status_connecting);
                break;
            case STATE_CONNECTED:
                connectionIcon.setImageResource(R.drawable.ic_bluetooth_connected);
                connectionIcon.setColorFilter(ContextCompat.getColor(this, R.color.status_connected));
                connectionStatus.setText(R.string.status_connected);
                connectionStatus.setTextColor(ContextCompat.getColor(this, R.color.status_connected));
                connectionSubtitle.setText("PCX7100 • Sinal forte");
                btnScanConnect.setText("Desconectar");
                toolbar.setSubtitle(R.string.status_connected);
                break;
        }
    }

    private void updatePlayPauseButton() {
        btnPlayPause.setIconResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
        btnPlayPause.setContentDescription(isPlaying ? getString(R.string.media_pause) : getString(R.string.media_play));
    }

    private void selectPreset(int index) {
        currentPreset = presets.get(index);
        for (int i = 0; i < presetContainer.getChildCount(); i++) {
            View child = presetContainer.getChildAt(i);
            if (child instanceof MaterialButton) {
                ((MaterialButton) child).setChecked(i == index);
            }
        }
        applyPresetBands(presetBands[index]);
        sendEqCommand(currentPreset);
    }

    private void applyPresetBands(int[] bands) {
        for (int i = 0; i < Math.min(7, eqBarsContainer.getChildCount()); i++) {
            View bar = eqBarsContainer.getChildAt(i);
            int targetHeight = (int) (bands[i] * 0.8f);
            animateBarHeight(bar, targetHeight);
        }
    }

    private void animateBarHeight(View bar, int targetHeight) {
        animateBarHeight(bar, targetHeight, 300);
    }

    private void animateBarHeight(View bar, int targetHeight, int durationMs) {
        ViewGroup.LayoutParams lp = bar.getLayoutParams();
        ValueAnimator anim = ValueAnimator.ofInt(lp.height, targetHeight);
        anim.setDuration(durationMs);
        anim.addUpdateListener(a -> {
            lp.height = (int) a.getAnimatedValue();
            bar.setLayoutParams(lp);
        });
        anim.start();
    }

    private void startEqAnimation() {
        eqAnimationRunnable = new Runnable() {
            @Override
            public void run() {
                if (isPlaying) {
                    for (int i = 0; i < eqBarsContainer.getChildCount(); i++) {
                        View bar = eqBarsContainer.getChildAt(i);
                        int baseHeight = (int) (presetBands[presets.indexOf(currentPreset)][i] * 0.8f);
                        int variation = (int) (Math.random() * 20) - 10;
                        int newHeight = Math.max(10, Math.min(80, baseHeight + variation));
                        animateBarHeight(bar, newHeight, 150);
                    }
                }
                uiHandler.postDelayed(this, 200);
            }
        };
        uiHandler.post(eqAnimationRunnable);
    }

    private void showEqBottomSheet() {
        Toast.makeText(this, "Equalizador completo - 7 bandas", Toast.LENGTH_SHORT).show();
    }

    private void showLightsBottomSheet() {
        Toast.makeText(this, "Controle de luzes LED - Modos/Cor/Brilho", Toast.LENGTH_SHORT).show();
    }

    private void showSettingsBottomSheet() {
        Toast.makeText(this, "Configurações - Auto-reconexão, Tela, Sobre", Toast.LENGTH_SHORT).show();
    }

    private void updateBluetoothUI(int state) {
        if (state == BluetoothAdapter.STATE_OFF) {
            updateConnectionUI(STATE_DISCONNECTED);
            Toast.makeText(this, R.string.bluetooth_off, Toast.LENGTH_SHORT).show();
        }
    }

    // Service communication stubs - replace with bindService + callbacks in production
    private void sendVolumeCommand(int volume) {
        Intent intent = new Intent(BluetoothConnectionService.ACTION_SET_VOLUME);
        intent.putExtra(BluetoothConnectionService.EXTRA_VOLUME, volume);
        startService(new Intent(this, BluetoothConnectionService.class).putExtras(intent));
    }

    private void sendMediaCommand(String command) {
        Intent intent = new Intent(BluetoothConnectionService.ACTION_MEDIA_CONTROL);
        intent.putExtra(BluetoothConnectionService.EXTRA_MEDIA_COMMAND, command);
        startService(new Intent(this, BluetoothConnectionService.class).putExtras(intent));
    }

    private void sendEqCommand(String preset) {
        Intent intent = new Intent(BluetoothConnectionService.ACTION_SET_EQ);
        intent.putExtra(BluetoothConnectionService.EXTRA_EQ_PRESET, preset);
        startService(new Intent(this, BluetoothConnectionService.class).putExtras(intent));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ENABLE_BT && resultCode == RESULT_OK) {
            startScan();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(bluetoothReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(bluetoothReceiver); } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        uiHandler.removeCallbacks(eqAnimationRunnable);
        super.onDestroy();
    }
}