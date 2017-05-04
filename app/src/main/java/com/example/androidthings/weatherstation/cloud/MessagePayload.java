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
package com.example.androidthings.weatherstation.cloud;

import com.example.androidthings.weatherstation.SensorData;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class MessagePayload {

    public static byte[] encode(List<SensorData> data) {
        try {
            JSONObject messagePayload = createMessagePayload(data);
            return messagePayload.toString().getBytes();
        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid message");
        }
    }

    private static JSONObject createMessagePayload(List<SensorData> data) throws JSONException {
        JSONObject messagePayload = new JSONObject();
        JSONArray dataArray = new JSONArray();
        for (SensorData el : data) {
            JSONObject sensor = new JSONObject();
            sensor.put("timestamp_" + el.getSensorName(),
                    el.getTimestamp());
            sensor.put(el.getSensorName(), el.getValue());
            dataArray.put(sensor);
        }
        messagePayload.put("data", dataArray);
        return messagePayload;
    }

}
