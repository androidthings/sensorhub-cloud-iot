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

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.things.iotcore.ConnectionParams;

/**
 * Container for the Cloud IoT Core initialization parameters
 */
public class Parameters {
    private static final String TAG = Parameters.class.getSimpleName();

    private String projectId;
    private String registryId;
    private String cloudRegion;
    private String deviceId;
    private String keyAlgorithm;

    private Parameters() {
    }

    public String getProjectId() {
        return projectId;
    }

    public String getRegistryId() {
        return registryId;
    }

    public String getCloudRegion() {
        return cloudRegion;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getKeyAlgorithm() {
        return keyAlgorithm;
    }

    public ConnectionParams getConnectionParams() {
        return new ConnectionParams.Builder()
                .setProjectId(getProjectId())
                .setRegistry(getRegistryId(), getCloudRegion())
                .setDeviceId(getDeviceId())
                .build();
    }

    private boolean isValid() {
        return projectId != null &&
                registryId != null &&
                cloudRegion != null &&
                deviceId != null &&
                (keyAlgorithm == null ||
                        AuthKeyGenerator.SUPPORTED_KEY_ALGORITHMS.contains(keyAlgorithm));
    }

    @Override
    public String toString() {
        return "Parameters{" +
                "projectId='" + projectId + '\'' +
                ", registryId='" + registryId + '\'' +
                ", cloudRegion='" + cloudRegion + '\'' +
                ", deviceId='" + deviceId + '\'' +
                ", keyAlgorithm='" + keyAlgorithm + '\'' +
                '}';
    }

    public void saveToPreferences(SharedPreferences pref) {
        SharedPreferences.Editor editor = pref.edit();
        editor.putString("project_id", getProjectId());
        editor.putString("registry_id", getRegistryId());
        editor.putString("device_id", getDeviceId());
        editor.putString("cloud_region", getCloudRegion());
        editor.putString("key_algorithm", getKeyAlgorithm());
        editor.apply();
    }

    /**
     * Construct a Parameters object from a SharedPreferences and an optional bundle.
     */
    public static Parameters from(@NonNull SharedPreferences prefs, @Nullable Bundle bundle) {
        Parameters params = new Parameters();
        params.projectId = prefs.getString("project_id", null);
        params.registryId = prefs.getString("registry_id", null);
        params.cloudRegion = prefs.getString("cloud_region", null);
        params.deviceId = prefs.getString("device_id", null);
        params.keyAlgorithm = prefs.getString("key_algorithm", null);
        if (bundle != null) {
            params.projectId = bundle.getString("project_id", params.projectId);
            params.registryId = bundle.getString("registry_id", params.registryId);
            params.cloudRegion = bundle.getString("cloud_region", params.cloudRegion);
            params.deviceId = bundle.getString("device_id", params.deviceId);
            params.keyAlgorithm = bundle.getString("key_algorithm", params.keyAlgorithm);
        }

        if (!params.isValid()) {
            Log.w(TAG, "Invalid parameters: " + params);
            return null;
        }
        return params;
    }
}
