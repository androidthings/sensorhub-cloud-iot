/*
 * Copyright 2017 The Android Open Source Project
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

package com.example.androidthings.sensorhub;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.example.androidthings.sensorhub.cloud.CloudPublisherService;
import com.google.android.things.contrib.driver.bmx280.Bmx280;
import com.google.android.things.contrib.driver.button.Button;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class SensorHubActivity extends Activity {

    private static final String TAG = SensorHubActivity.class.getSimpleName();
    private static final long SENSOR_READ_INTERVAL_MS = TimeUnit.SECONDS.toMillis(20);
    /**
     * Cutoff to consider a timestamp as valid. Some boards might take some time to update
     * their network time on the first time they boot, and we don't want to publish sensor data
     * with timestamps that we know are invalid. Sensor readings will be ignored until the
     * board's time (System.currentTimeMillis) is more recent than this constant.
     */
    private static final long INITIAL_VALID_TIMESTAMP;
    static {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2016, 1, 1);
        INITIAL_VALID_TIMESTAMP = calendar.getTimeInMillis();
    }

    public static final String SENSOR_TYPE_MOTION_DETECTION = "motion";
    public static final String SENSOR_TYPE_TEMPERATURE_DETECTION = "temperature";
    public static final String SENSOR_TYPE_AMBIENT_PRESSURE_DETECTION = "ambient_pressure";

    private CloudPublisherService mPublishService;
    private Looper mSensorLooper;

    // sensors
    private Bmx280 mEnvironmentalSensor;
    private Button mMotionDetectorSensor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "Started Weather Station");

        initializeServiceIfNeeded();

        // Start the thread that collects sensor data
        HandlerThread thread = new HandlerThread("CloudPublisherService");
        thread.start();
        mSensorLooper = thread.getLooper();

        final Handler sensorHandler = new Handler(mSensorLooper);
        sensorHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    initializeServiceIfNeeded();
                    connectToAvailableSensors();
                    collectContinuousSensors();
                } catch (Throwable t) {
                    Log.e(TAG, String.format(Locale.getDefault(),
                            "Cannot collect sensor data, will try again in %d ms",
                            SENSOR_READ_INTERVAL_MS), t);
                }
                sensorHandler.postDelayed(this, SENSOR_READ_INTERVAL_MS);
            }
        }, SENSOR_READ_INTERVAL_MS);

        connectToAvailableSensors();
    }

    private void initializeServiceIfNeeded() {
        if (mPublishService == null) {
            try {
                // Bind to the service
                Intent intent = new Intent(this, CloudPublisherService.class);
                bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
            } catch (Throwable t) {
                Log.e(TAG, "Could not connect to the service, will try again later", t);
            }
        }
    }

    private void connectToAvailableSensors() {
        // Temperature and Pressure:
        if (mEnvironmentalSensor == null) {
            mEnvironmentalSensor = connectToBmx280();
        }

        if (mMotionDetectorSensor == null) {
            mMotionDetectorSensor = connectToMotionDetector();
        }
    }

    private Bmx280 connectToBmx280() {
        try {
            Bmx280 bmx280 = new Bmx280(BoardDefaults.getI2cBusForSensors());
            bmx280.setTemperatureOversampling(Bmx280.OVERSAMPLING_1X);
            bmx280.setPressureOversampling(Bmx280.OVERSAMPLING_1X);
            bmx280.setMode(Bmx280.MODE_NORMAL);
            Log.d(TAG, "Initialized BME280");
            return bmx280;
        } catch (Throwable t) {
            Log.w(TAG, "Could not initialize BME280 sensor on I2C bus " +
                    BoardDefaults.getI2cBusForSensors(), t);
            return null;
        }
    }

    private Button connectToMotionDetector() {
        try {
            Button button = new Button(BoardDefaults.getGPIOForMotionDetector(),
                    Button.LogicState.PRESSED_WHEN_HIGH);
            button.setOnButtonEventListener(new Button.OnButtonEventListener() {
                @Override
                public void onButtonEvent(Button button, boolean pressed) {
                    collectSensorOnChange(SENSOR_TYPE_MOTION_DETECTION, pressed ? 1 : 0);
                }
            });
            Log.d(TAG, "Initialized motion detector");
            return button;
        } catch (Throwable t) {
            Log.w(TAG, "Could not initialize motion detector on gpio pin " +
                    BoardDefaults.getGPIOForMotionDetector(), t);
            return null;
        }
    }

    private void collectContinuousSensors() {
        if (mPublishService != null) {
            List<SensorData> sensorsData = new ArrayList<>();
            addBmx280Readings(sensorsData);
            Log.d(TAG, "collected continuous sensor data: " + sensorsData);
            mPublishService.logSensorData(sensorsData);
        }
    }

    private void addBmx280Readings(List<SensorData> output) {
        if (mEnvironmentalSensor != null) {
            try {
                long now = System.currentTimeMillis();
                if (now >= INITIAL_VALID_TIMESTAMP) {
                    float[] data = mEnvironmentalSensor.readTemperatureAndPressure();
                    output.add(new SensorData(now, SENSOR_TYPE_TEMPERATURE_DETECTION, data[0]));
                    output.add(new SensorData(now, SENSOR_TYPE_AMBIENT_PRESSURE_DETECTION,
                            data[1]));
                } else {
                    Log.i(TAG, "Ignoring sensor readings because timestamp is invalid. " +
                            "Please, set the device's date/time");
                }
            } catch (Throwable t) {
                Log.w(TAG, "Cannot collect Bmx280 data. Ignoring it for now", t);
                closeBmx280Quietly();
            }
        }
    }

    private void collectSensorOnChange(String type, float sensorReading) {
        if (mPublishService != null) {
            Log.d(TAG, "On change " + type + ": " + sensorReading);
            long now = System.currentTimeMillis();
            if (now >= INITIAL_VALID_TIMESTAMP) {
                mPublishService.logSensorDataOnChange(new SensorData(now, type, sensorReading));
            } else {
                Log.i(TAG, "Ignoring sensor readings because timestamp is invalid. " +
                        "Please, set the device's date/time");
            }
        }
    }

    private void closeBmx280Quietly() {
        if (mEnvironmentalSensor != null) {
            try {
                mEnvironmentalSensor.close();
            } catch (IOException e) {
                // close quietly
            }
            mEnvironmentalSensor = null;
        }
    }

    private void closeMotionDetectorQuietly() {
        if (mMotionDetectorSensor != null) {
            try {
                mMotionDetectorSensor.close();
            } catch (IOException e) {
                // close quietly
            }
            mMotionDetectorSensor = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        mSensorLooper.quit();
        closeBmx280Quietly();
        closeMotionDetectorQuietly();

        // unbind from Cloud Publisher service.
        if (mPublishService != null) {
            unbindService(mServiceConnection);
        }
    }


    /**
     * Callback for service binding, passed to bindService()
     */
    private ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            CloudPublisherService.LocalBinder binder = (CloudPublisherService.LocalBinder) service;
            mPublishService = binder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mPublishService = null;
        }
    };

}
