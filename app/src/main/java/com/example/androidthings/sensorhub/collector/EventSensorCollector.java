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

import com.example.androidthings.sensorhub.SensorData;

import java.util.List;

/**
 * Special type of {@link SensorCollector} that allows collecting events when they happen.
 * For example, a motion sensor collector generates events when motion is detected, not at
 * regular intervals. Instances of this class can decide if they also want to report the
 * collected event as a regular sensor reading in {@link #collectRecentReadings(List)}.
 */
public interface EventSensorCollector extends SensorCollector {
    interface Callback {
        void onEventCollected(SensorData data);
    }
    void setEventCallback(Callback callback);
}
