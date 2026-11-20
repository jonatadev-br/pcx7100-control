package com.zyvo.pcx7100.control;

import android.app.Application;

/**
 * Application entry point. The app shapes the phone's audio output (volume,
 * media transport, real equalizer, bass boost) which is streamed to the
 * PCX7100 over standard Bluetooth A2DP. No custom BLE connection is needed.
 */
public class PCXApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
    }
}
