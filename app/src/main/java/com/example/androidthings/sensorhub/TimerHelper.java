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
package com.example.androidthings.sensorhub;

import android.os.SystemClock;
import android.util.Log;

import java.util.Calendar;

public class TimerHelper {
    private static final String TAG = TimerHelper.class.getSimpleName();

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

    public static long calculateNextRun(long eventsPerHour, long lastRun) {
        return lastRun + 60*60*1000L/eventsPerHour;
    }

    public static boolean canExecute(String loopType, boolean isReady) {
        long clockTime = System.currentTimeMillis();
        if (clockTime < INITIAL_VALID_TIMESTAMP) {
            Log.d(TAG, loopType + " ignored because timestamp is invalid. " +
                    "Please, set the device's date/time");
            return false;
        } else if (!isReady) {
            Log.d(TAG, loopType + " ignored because IotCoreClient is not yet connected");
            return false;
        }
        return true;
    }

}
