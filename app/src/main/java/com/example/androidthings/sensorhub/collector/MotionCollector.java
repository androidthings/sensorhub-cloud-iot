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
package com.example.androidthings.sensorhub.collector;

import android.util.Log;

import com.example.androidthings.sensorhub.SensorData;
import com.google.android.things.contrib.driver.button.Button;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MotionCollector implements EventSensorCollector {

    private static final String TAG = MotionCollector.class.getSimpleName();

    private static final String SENSOR_TYPE_MOTION_DETECTION = "motion";

    private String gpioPin;
    private Button button;
    private boolean enabled;
    private Callback eventCallback;
    private int lastReading;

    private Button.OnButtonEventListener buttonEventListener = ((button1, pressed) -> {
        if (eventCallback != null ) {
            int sensorReading = pressed ? 1 : 0;
            lastReading = sensorReading;
            Log.d(TAG, "On change " + SENSOR_TYPE_MOTION_DETECTION + ": " + sensorReading);
            SensorData data = new SensorData(SENSOR_TYPE_MOTION_DETECTION, sensorReading);
            eventCallback.onEventCollected(data);
        }
    });

    public MotionCollector(String gpioPin) {
        this.gpioPin = gpioPin;
    }

    @Override
    public void setEventCallback(Callback eventCallback) {
        if (eventCallback != this.eventCallback) {
            this.eventCallback = eventCallback;
        }
    }

    @Override
    public boolean activate() {
        if (button != null) {
            return true;
        }
        try {
            button = new Button(gpioPin, Button.LogicState.PRESSED_WHEN_LOW);
            button.setOnButtonEventListener(buttonEventListener);
            enabled = true;
            Log.d(TAG, "Initialized motion detector");
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "Could not initialize motion detector on gpio pin " + gpioPin, t);
        }
        return false;
    }

    @Override
    public void setEnabled(String sensor, boolean enabled) {
        switch (sensor) {
            case SENSOR_TYPE_MOTION_DETECTION:
                this.enabled = enabled;
                break;
            default:
                Log.w(TAG, "Don't know what sensor is " + sensor + ". Ignoring.");
        }
    }

    @Override
    public boolean isEnabled(String sensor) {
        return enabled;
    }

    @Override
    public List<String> getAvailableSensors() {
        return Collections.singletonList(SENSOR_TYPE_MOTION_DETECTION);
    }

    @Override
    public List<String> getEnabledSensors() {
        List<String> sensors = new ArrayList<>();
        if (enabled) {
            sensors.add(SENSOR_TYPE_MOTION_DETECTION);
        }
        return sensors;
    }

    @Override
    public void collectRecentReadings(List<SensorData> output) {
        if (button != null) {
            output.add(new SensorData(SENSOR_TYPE_MOTION_DETECTION, lastReading));
        }
    }

    @Override
    public void closeQuietly() {
        if (button != null) {
            try {
                button.close();
            } catch (IOException e) {
                // close quietly
            }
            button = null;
        }
    }
}
