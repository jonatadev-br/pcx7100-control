package com.zyvo.pcx7100.control.bluetooth;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.zyvo.pcx7100.control.MainActivity;
import com.zyvo.pcx7100.control.PCXApplication;
import com.zyvo.pcx7100.control.R;

import java.util.UUID;

public class BluetoothConnectionService extends Service {
    private static final String TAG = "PCX7100_Bluetooth";
    private static final String CHANNEL_ID = "pcx7100_bluetooth";
    private static final int NOTIFICATION_ID = 1;

    // PCX7100 Service/Characteristic UUIDs (common for audio devices)
    private static final UUID SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb"); // Heart Rate as placeholder
    private static final UUID VOLUME_CHAR_UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
    private static final UUID MEDIA_CHAR_UUID = UUID.fromString("00002a38-0000-1000-8000-00805f9b34fb");
    private static final UUID EQ_CHAR_UUID = UUID.fromString("00002a39-0000-1000-8000-00805f9b34fb");
    private static final UUID LIGHTS_CHAR_UUID = UUID.fromString("00002a3a-0000-1000-8000-00805f9b34fb");
    private static final UUID BATTERY_CHAR_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");
    private static final UUID CLIENT_CONFIG_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt bluetoothGatt;
    private BluetoothDevice connectedDevice;
    private ScanCallback scanCallback;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Callbacks
    private ConnectionCallback connectionCallback;
    private VolumeCallback volumeCallback;
    private BatteryCallback batteryCallback;
    private MediaCallback mediaCallback;
    private EqCallback eqCallback;
    private LightsCallback lightsCallback;

    public interface ConnectionCallback {
        void onDeviceFound(BluetoothDevice device);
        void onConnecting(BluetoothDevice device);
        void onConnected(BluetoothDevice device);
        void onDisconnected(BluetoothDevice device);
        void onConnectionFailed(String error);
        void onScanStarted();
        void onScanStopped();
    }

    public interface VolumeCallback {
        void onVolumeChanged(int volume);
        void onVolumeSet(boolean success);
    }

    public interface BatteryCallback {
        void onBatteryLevel(int level);
    }

    public interface MediaCallback {
        void onPlayStateChanged(boolean isPlaying);
        void onTrackChanged(String title, String artist);
    }

    public interface EqCallback {
        void onPresetChanged(String preset);
        void onBandsChanged(int[] bands);
    }

    public interface LightsCallback {
        void onLightsStateChanged(boolean on, int mode, int brightness, int color);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter != null) {
            scanner = bluetoothAdapter.getBluetoothLeScanner();
        }
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_START_SCAN.equals(action)) {
                startScan();
            } else if (ACTION_STOP_SCAN.equals(action)) {
                stopScan();
            } else if (ACTION_CONNECT.equals(action)) {
                String address = intent.getStringExtra(EXTRA_DEVICE_ADDRESS);
                if (address != null) {
                    connect(address);
                }
            } else if (ACTION_DISCONNECT.equals(action)) {
                disconnect();
            } else if (ACTION_SET_VOLUME.equals(action)) {
                int volume = intent.getIntExtra(EXTRA_VOLUME, 0);
                setVolume(volume);
            } else if (ACTION_MEDIA_CONTROL.equals(action)) {
                String command = intent.getStringExtra(EXTRA_MEDIA_COMMAND);
                mediaControl(command);
            } else if (ACTION_SET_EQ.equals(action)) {
                String preset = intent.getStringExtra(EXTRA_EQ_PRESET);
                setEqPreset(preset);
            } else if (ACTION_SET_LIGHTS.equals(action)) {
                boolean on = intent.getBooleanExtra(EXTRA_LIGHTS_ON, false);
                int mode = intent.getIntExtra(EXTRA_LIGHTS_MODE, 0);
                int brightness = intent.getIntExtra(EXTRA_LIGHTS_BRIGHTNESS, 100);
                int color = intent.getIntExtra(EXTRA_LIGHTS_COLOR, 0);
                setLights(on, mode, brightness, color);
            }
        }
        return START_STICKY;
    }

    // Actions
    public static final String ACTION_START_SCAN = "com.zyvo.pcx7100.control.START_SCAN";
    public static final String ACTION_STOP_SCAN = "com.zyvo.pcx7100.control.STOP_SCAN";
    public static final String ACTION_CONNECT = "com.zyvo.pcx7100.control.CONNECT";
    public static final String ACTION_DISCONNECT = "com.zyvo.pcx7100.control.DISCONNECT";
    public static final String ACTION_SET_VOLUME = "com.zyvo.pcx7100.control.SET_VOLUME";
    public static final String ACTION_MEDIA_CONTROL = "com.zyvo.pcx7100.control.MEDIA_CONTROL";
    public static final String ACTION_SET_EQ = "com.zyvo.pcx7100.control.SET_EQ";
    public static final String ACTION_SET_LIGHTS = "com.zyvo.pcx7100.control.SET_LIGHTS";

    public static final String EXTRA_DEVICE_ADDRESS = "device_address";
    public static final String EXTRA_VOLUME = "volume";
    public static final String EXTRA_MEDIA_COMMAND = "media_command";
    public static final String EXTRA_EQ_PRESET = "eq_preset";
    public static final String EXTRA_LIGHTS_ON = "lights_on";
    public static final String EXTRA_LIGHTS_MODE = "lights_mode";
    public static final String EXTRA_LIGHTS_BRIGHTNESS = "lights_brightness";
    public static final String EXTRA_LIGHTS_COLOR = "lights_color";

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "PCX7100 Bluetooth Connection",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Mantém a conexão Bluetooth ativa");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(String title, String text) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build();
    }

    public void startScan() {
        if (scanner == null || !PCXApplication.getInstance().isBluetoothEnabled()) {
            if (connectionCallback != null) {
                connectionCallback.onConnectionFailed("Bluetooth não disponível");
            }
            return;
        }

        stopScan(); // Ensure clean state

        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice device = result.getDevice();
                String name = device.getName();
                if (name != null && (name.contains("PCX7100") || name.contains("PCX") || name.contains("pcx"))) {
                    if (connectionCallback != null) {
                        connectionCallback.onDeviceFound(device);
                    }
                }
            }

            @Override
            public void onBatchScanResults(java.util.List<ScanResult> results) {}

            @Override
            public void onScanFailed(int errorCode) {
                if (connectionCallback != null) {
                    connectionCallback.onConnectionFailed("Falha no scan: " + errorCode);
                }
            }
        };

        scanner.startScan(scanCallback);
        if (connectionCallback != null) {
            connectionCallback.onScanStarted();
        }

        // Auto-stop after 15 seconds
        handler.postDelayed(this::stopScan, 15000);
    }

    public void stopScan() {
        if (scanner != null && scanCallback != null) {
            scanner.stopScan(scanCallback);
            scanCallback = null;
            if (connectionCallback != null) {
                connectionCallback.onScanStopped();
            }
        }
    }

    public void connect(String address) {
        if (bluetoothAdapter == null) return;

        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            if (connectionCallback != null) {
                connectionCallback.onConnectionFailed("Dispositivo não encontrado");
            }
            return;
        }

        if (connectionCallback != null) {
            connectionCallback.onConnecting(device);
        }

        bluetoothGatt = device.connectGatt(this, false, gattCallback);
        connectedDevice = device;

        // Connection timeout
        handler.postDelayed(() -> {
            if (bluetoothGatt != null && connectedDevice != null
                    && bluetoothManager.getConnectionState(connectedDevice, BluetoothProfile.GATT) != BluetoothProfile.STATE_CONNECTED) {
                bluetoothGatt.close();
                bluetoothGatt = null;
                if (connectionCallback != null) {
                    connectionCallback.onConnectionFailed("Timeout de conexão");
                }
            }
        }, 15000);
    }

    public void disconnect() {
        stopScan();
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
        if (connectedDevice != null && connectionCallback != null) {
            connectionCallback.onDisconnected(connectedDevice);
        }
        connectedDevice = null;
        stopForeground(true);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                handler.removeCallbacksAndMessages(null);
                gatt.discoverServices();
                startForeground(NOTIFICATION_ID, buildNotification("PCX7100 Conectado", "Controle ativo"));
                if (connectionCallback != null) {
                    connectionCallback.onConnected(connectedDevice);
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                stopForeground(true);
                bluetoothGatt.close();
                bluetoothGatt = null;
                if (connectionCallback != null) {
                    connectionCallback.onDisconnected(connectedDevice);
                }
                connectedDevice = null;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Enable notifications for characteristics
                enableNotifications(gatt, VOLUME_CHAR_UUID);
                enableNotifications(gatt, BATTERY_CHAR_UUID);
                enableNotifications(gatt, MEDIA_CHAR_UUID);
                enableNotifications(gatt, EQ_CHAR_UUID);
                enableNotifications(gatt, LIGHTS_CHAR_UUID);
                // Read initial values
                readCharacteristic(gatt, VOLUME_CHAR_UUID);
                readCharacteristic(gatt, BATTERY_CHAR_UUID);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                UUID uuid = characteristic.getUuid();
                byte[] value = characteristic.getValue();
                if (value == null || value.length == 0) return;

                if (uuid.equals(VOLUME_CHAR_UUID)) {
                    int vol = value[0] & 0xFF;
                    if (volumeCallback != null) volumeCallback.onVolumeChanged(vol);
                } else if (uuid.equals(BATTERY_CHAR_UUID)) {
                    int level = value[0] & 0xFF;
                    if (batteryCallback != null) batteryCallback.onBatteryLevel(level);
                } else if (uuid.equals(EQ_CHAR_UUID)) {
                    int[] bands = new int[value.length];
                    for (int i = 0; i < value.length; i++) {
                        bands[i] = value[i] & 0xFF;
                    }
                    if (eqCallback != null) eqCallback.onBandsChanged(bands);
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            onCharacteristicRead(gatt, characteristic, BluetoothGatt.GATT_SUCCESS);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            UUID uuid = characteristic.getUuid();
            if (uuid.equals(VOLUME_CHAR_UUID)) {
                if (volumeCallback != null) volumeCallback.onVolumeSet(status == BluetoothGatt.GATT_SUCCESS);
            }
        }

        private void enableNotifications(BluetoothGatt gatt, UUID charUuid) {
            BluetoothGattService service = gatt.getService(SERVICE_UUID);
            if (service == null) return;
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(charUuid);
            if (characteristic == null) return;
            gatt.setCharacteristicNotification(characteristic, true);
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CONFIG_DESCRIPTOR);
            if (descriptor != null) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);
            }
        }

        private void readCharacteristic(BluetoothGatt gatt, UUID charUuid) {
            BluetoothGattService service = gatt.getService(SERVICE_UUID);
            if (service == null) return;
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(charUuid);
            if (characteristic != null) {
                gatt.readCharacteristic(characteristic);
            }
        }

    };

    private void writeCharacteristic(UUID charUuid, byte[] data) {
        if (bluetoothGatt == null) return;
        BluetoothGattService service = bluetoothGatt.getService(SERVICE_UUID);
        if (service == null) return;
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(charUuid);
        if (characteristic != null) {
            characteristic.setValue(data);
            bluetoothGatt.writeCharacteristic(characteristic);
        }
    }

    public void setVolume(int volume) {
        writeCharacteristic(VOLUME_CHAR_UUID, new byte[]{(byte) volume});
    }

    public void mediaControl(String command) {
        byte[] data;
        switch (command) {
            case "play": data = new byte[]{1}; break;
            case "pause": data = new byte[]{2}; break;
            case "next": data = new byte[]{3}; break;
            case "prev": data = new byte[]{4}; break;
            default: return;
        }
        writeCharacteristic(MEDIA_CHAR_UUID, data);
    }

    public void setEqPreset(String preset) {
        writeCharacteristic(EQ_CHAR_UUID, preset.getBytes());
    }

    public void setLights(boolean on, int mode, int brightness, int color) {
        byte[] data = new byte[4];
        data[0] = (byte) (on ? 1 : 0);
        data[1] = (byte) mode;
        data[2] = (byte) brightness;
        data[3] = (byte) color;
        writeCharacteristic(LIGHTS_CHAR_UUID, data);
    }

    // Setters for callbacks
    public void setConnectionCallback(ConnectionCallback callback) {
        this.connectionCallback = callback;
    }
    public void setVolumeCallback(VolumeCallback callback) {
        this.volumeCallback = callback;
    }
    public void setBatteryCallback(BatteryCallback callback) {
        this.batteryCallback = callback;
    }
    public void setMediaCallback(MediaCallback callback) {
        this.mediaCallback = callback;
    }
    public void setEqCallback(EqCallback callback) {
        this.eqCallback = callback;
    }
    public void setLightsCallback(LightsCallback callback) {
        this.lightsCallback = callback;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        disconnect();
        super.onDestroy();
    }
}