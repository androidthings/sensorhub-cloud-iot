/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.androidthings.sensorhub.iotcore;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.util.Log;

import com.example.androidthings.sensorhub.AuthKeyGenerator;
import com.example.androidthings.sensorhub.Parameters;
import com.example.androidthings.sensorhub.SensorData;
import com.example.androidthings.sensorhub.TimerHelper;
import com.example.androidthings.sensorhub.collector.EventSensorCollector;
import com.example.androidthings.sensorhub.collector.SensorCollector;
import com.google.android.things.iotcore.ConnectionCallback;
import com.google.android.things.iotcore.IotCoreClient;
import com.google.android.things.iotcore.TelemetryEvent;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class SensorHub {
    private static final String TAG = "sensorhub";

    private static final int DEFAULT_TELEMETRY_PER_HOUR = 60*3; // every 20 seconds
    private static final int DEFAULT_STATE_UPDATES_PER_HOUR = 60; // every minute

    private HandlerThread backgroundThread;
    private Handler eventsHandler;
    private Handler recurrentTasksHandler;

    /**
     * Version of the configuration reported in the device status state (device to cloud).
     * Device config messages (cloud to device) should use a version greater than this value,
     * otherwise it will be ignored.
     */
    private int configurationVersion;

    private int telemetryEventsPerHour;
    private int stateUpdatesPerHour;

    private long lastTelemetryRun;
    private long lastStateUpdateRun;

    private List<SensorCollector> collectors;

    private Parameters params;
    private IotCoreClient iotCoreClient;

    private AtomicBoolean ready;

    public SensorHub(Parameters params) {
        this.ready =  new AtomicBoolean(false);
        this.configurationVersion = 0;
        this.telemetryEventsPerHour = DEFAULT_TELEMETRY_PER_HOUR;
        this.stateUpdatesPerHour = DEFAULT_STATE_UPDATES_PER_HOUR;
        this.params = params;
        this.collectors = new ArrayList<>();
    }


    /**
     * Register a sensor collector. When the SensorHub is started, it will fetch sensor readings
     * from the active collectors.
     *
     * Collectors that implement {@link EventSensorCollector} have their event callback overridden
     * to call the {@link #processSensorEvent(SensorData)} method.
     *
     * @param collector
     */
    public void registerSensorCollector(@NonNull SensorCollector collector) {
        collectors.add(collector);
        if (collector instanceof EventSensorCollector) {
            ((EventSensorCollector) collector).setEventCallback(this::processSensorEvent);
        }
    }

    /**
     * Start sensor collection and reporting.
     * @throws GeneralSecurityException
     * @throws IOException
     */
    public void start() throws GeneralSecurityException, IOException {
        initializeIfNeeded();

        backgroundThread = new HandlerThread("IotCoreThread");
        backgroundThread.start();
        eventsHandler = new Handler(backgroundThread.getLooper());
        recurrentTasksHandler = new Handler(backgroundThread.getLooper());

        recurrentTasksHandler.post(recurrentTelemetryPublisher);
        recurrentTasksHandler.post(stateUpdatePublisher);
    }

    public void stop() {
        Log.d(TAG, "Stop SensorHub");
        backgroundThread.quitSafely();
        closeCollectors();
        iotCoreClient.disconnect();
    }

    private void initializeIfNeeded() {
        ready.set(false);
        AuthKeyGenerator keyGenerator = null;
        try {
            keyGenerator = new AuthKeyGenerator(params.getKeyAlgorithm());
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalArgumentException("Cannot create a key generator", e);
        }
        iotCoreClient = new IotCoreClient.Builder()
                .setConnectionParams(params.getConnectionParams())
                .setKeyPair(keyGenerator.getKeyPair())
                .setConnectionCallback(new ConnectionCallback() {
                    @Override
                    public void onConnected() {
                        ready.set(true);
                    }

                    @Override
                    public void onDisconnected(int i) {
                        ready.set(false);
                    }
                })
                .setOnConfigurationListener(this::onConfigurationReceived)
                .build();
        connectIfNeeded();
    }

    private void connectIfNeeded() {
        if (iotCoreClient != null && !iotCoreClient.isConnected()) {
            iotCoreClient.connect();
        }
    }

    private void onConfigurationReceived(byte[] bytes) {
        if (bytes.length == 0) {
            Log.w(TAG, "Ignoring empty device config event");
            return;
        }
        MessagePayload.DeviceConfig deviceConfig = MessagePayload.parseDeviceConfigPayload(
                new String(bytes));
        if (deviceConfig.version <= configurationVersion) {
            Log.w(TAG, "Ignoring device config message with old version. Current version: " +
                    configurationVersion + ", Version received: " + deviceConfig.version);
            return;
        }
        Log.i(TAG, "Applying device config: " + deviceConfig);
        configurationVersion = deviceConfig.version;

        recurrentTasksHandler.post(() -> {
            reconfigure(deviceConfig);
        });
    }

    private void reconfigure(MessagePayload.DeviceConfig deviceConfig) {
        telemetryEventsPerHour = deviceConfig.telemetryEventsPerHour;
        stateUpdatesPerHour = deviceConfig.stateUpdatesPerHour;

        HashSet<String> toEnable = new HashSet<>(Arrays.asList(deviceConfig.activeSensors));

        for (SensorCollector collector: collectors) {
            for (String sensor: collector.getAvailableSensors()) {
                boolean enable = toEnable.remove(sensor);
                collector.setEnabled(sensor, enable);
            }
        }

        if (!toEnable.isEmpty()) {
            Log.w(TAG, "Ignoring unknown sensors in device config active-sensors: " +
                    toEnable);
        }

        // reconfigure recurrent tasks:
        recurrentTasksHandler.removeCallbacks(recurrentTelemetryPublisher);
        recurrentTasksHandler.removeCallbacks(stateUpdatePublisher);
        scheduleNextSensorCollection();
        scheduleNextStatusUpdate();
    }

    private void processSensorEvent(SensorData event) {
        if (eventsHandler == null) {
            Log.i(TAG, "Ignoring event because the background handler is " +
                    "not running (has the event thread been initiated yet?). Event: " +
                    event);
            return;
        }
        eventsHandler.post(() -> publishTelemetry(Collections.singletonList(event)));
    }

    private void publishTelemetry(List<SensorData> currentReadings) {
        String payload = MessagePayload.createTelemetryMessagePayload(currentReadings);
        Log.d(TAG, "Publishing telemetry: " + payload);
        if (iotCoreClient == null) {
            Log.w(TAG, "Ignoring sensor readings because IotCoreClient is not yet active.");
            return;
        }
        TelemetryEvent event = new TelemetryEvent(payload.getBytes(),
                null, TelemetryEvent.QOS_AT_LEAST_ONCE);
        iotCoreClient.publishTelemetry(event);
    }

    private void publishDeviceState() {
        List<String> activeSensors = new ArrayList<>();
        List<String> allSensors = new ArrayList<>();
        for (SensorCollector collector: collectors) {
            allSensors.addAll(collector.getAvailableSensors());
            activeSensors.addAll(collector.getEnabledSensors());
        }
        String payload = MessagePayload.createDeviceStateUpdatePayload(
                configurationVersion, telemetryEventsPerHour, stateUpdatesPerHour,
                allSensors, activeSensors);
        Log.d(TAG, "Publishing device state: " + payload);
        if (iotCoreClient == null) {
            Log.w(TAG, "Refusing to publishTelemetry device state because IotCoreClient is " +
                    "not yet active.");
            return;
        }
        iotCoreClient.publishDeviceState(payload.getBytes());
    }

    private List<SensorData> collectCurrentSensorsReadings() {
        List<SensorData> sensorsData = new ArrayList<>();
        for (SensorCollector collector: collectors) {
            try {
                collector.activate();
                collector.collectRecentReadings(sensorsData);
            } catch (Throwable t) {
                Log.e(TAG, "Cannot collect recent readings of " +
                        collector.getAvailableSensors() + ", will try again in the next run.", t);
            }
        }
        Log.d(TAG, "collected sensor data: " + sensorsData);
        return sensorsData;
    }

    private void closeCollectors() {
        for (SensorCollector collector: collectors) {
            collector.closeQuietly();
        }
    }

    private void scheduleNextSensorCollection() {
        long nextRun = TimerHelper.calculateNextRun(telemetryEventsPerHour, lastTelemetryRun);
        recurrentTasksHandler.postAtTime(recurrentTelemetryPublisher, nextRun);
    }

    private void scheduleNextStatusUpdate() {
        long nextRun = TimerHelper.calculateNextRun(stateUpdatesPerHour, lastStateUpdateRun);
        recurrentTasksHandler.postAtTime(stateUpdatePublisher, nextRun);
    }

    private final Runnable recurrentTelemetryPublisher = new Runnable() {
        @Override
        public void run() {
            lastTelemetryRun = SystemClock.uptimeMillis();
            connectIfNeeded();
            if (TimerHelper.canExecute("Telemetry loop", ready.get())) {
                try {
                    List<SensorData> currentReadings = collectCurrentSensorsReadings();
                    publishTelemetry(currentReadings);
                } catch (Throwable t) {
                    Log.e(TAG, "Cannot publish recurrent telemetry events, " +
                            "will try again later", t);
                }
            }
            scheduleNextSensorCollection();
        }
    };

    private final Runnable stateUpdatePublisher = new Runnable() {
        @Override
        public void run() {
            lastStateUpdateRun = SystemClock.uptimeMillis();
            connectIfNeeded();
            if (TimerHelper.canExecute("State update loop", ready.get())) {
                try {
                    publishDeviceState();
                } catch (Throwable t) {
                    Log.e(TAG, "Cannot publish device state, will try again later", t);
                }
            }
            scheduleNextStatusUpdate();
        }
    };

}
