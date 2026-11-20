package com.zyvo.pcx7100.control;

import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;

public class PCXApplication extends Application {
    private static PCXApplication instance;
    private BluetoothAdapter bluetoothAdapter;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public static PCXApplication getInstance() {
        return instance;
    }

    public BluetoothAdapter getBluetoothAdapter() {
        return bluetoothAdapter;
    }

    public boolean isBluetoothSupported() {
        return bluetoothAdapter != null;
    }

    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }
}