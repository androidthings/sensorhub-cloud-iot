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
package com.example.androidthings.weatherstation.cloud.cloudiot;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;

/**
 * Configuration container for the MQTT example
 */
public class CloudIotOptions {
    private static final String TAG = CloudIotOptions.class.getSimpleName();

    private static final String DEFAULT_REGION = "us-central1";
    private static final String DEFAULT_BRIDGE_HOSTNAME = "mqtt.googleapis.com";
    private static final short DEFAULT_BRIDGE_PORT = 443;

    public static final String RS_256_DESIGNATOR = "RS256";
    public static final String ES_256_DESIGNATOR = "ES256";

    private static final String DEFAULT_PRIVATE_KEY_FILE = "CloudIot/private_key.pkcs8";
    private static final String DEFAULT_ALGORITHM = RS_256_DESIGNATOR;

    public static final String UNUSED_ACCOUNT_NAME = "unused";

    /**
     * Notice that for CloudIoT the topic for telemetry events needs to have the format below.
     * As described <a href="https://cloud.google.com/iot/docs/protocol_bridge_guide#telemetry_events">in docs</a>,
     * messages published to a topic with this format are augmented with extra attributes and
     * forwarded to the Pub/Sub topic specified in the registry resource.
     */
    private static final String MQTT_TOPIC_FORMAT = "/devices/%s/events";
    private static final String MQTT_CLIENT_ID_FORMAT =
            "projects/%s/locations/%s/registries/%s/devices/%s";
    private static final String BROKER_URL_FORMAT = "ssl://%s:%d";

    /**
     * GCP cloud project name.
     */
    private String projectId;

    /**
     * Cloud IoT registry id.
     */
    private String registryId;

    /**
     * Cloud IoT device id.
     */
    private String deviceId;

    /**
     * Path to private key file.
     */
    private String privateKeyFile = DEFAULT_PRIVATE_KEY_FILE;

    /**
     * Encryption algorithm to use to generate the JWT. Either 'RS256' or 'ES256'.
     */
    private String algorithm = DEFAULT_ALGORITHM;

    /**
     * GCP cloud region.
     */
    private String cloudRegion = DEFAULT_REGION;

    /**
     * MQTT bridge hostname.
     */
    private String bridgeHostname = DEFAULT_BRIDGE_HOSTNAME;

    /**
     * MQTT bridge port.
     */
    private short bridgePort = DEFAULT_BRIDGE_PORT;



    public String getBrokerUrl() {
        return String.format(Locale.getDefault(), BROKER_URL_FORMAT, bridgeHostname, bridgePort);
    }

    public String getClientId() {
        return String.format(Locale.getDefault(), MQTT_CLIENT_ID_FORMAT,
                projectId, cloudRegion, registryId, deviceId);
    }

    public String getTopicName() {
        return String.format(Locale.getDefault(), MQTT_TOPIC_FORMAT, deviceId);
    }

    public String getProjectId() {
        return projectId;
    }

    public String getRegistryId() {
        return registryId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getPrivateKeyFile() {
        return privateKeyFile;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public String getCloudRegion() {
        return cloudRegion;
    }

    public String getBridgeHostname() {
        return bridgeHostname;
    }

    public short getBridgePort() {
        return bridgePort;
    }

    private CloudIotOptions() {
    }

    public boolean isValid() {
        return !TextUtils.isEmpty(projectId) &&
                !TextUtils.isEmpty(registryId) &&
                !TextUtils.isEmpty(deviceId) &&
                !TextUtils.isEmpty(privateKeyFile) &&
                !TextUtils.isEmpty(algorithm) &&
                !TextUtils.isEmpty(cloudRegion) &&
                !TextUtils.isEmpty(bridgeHostname);
    }

    public void saveToPreferences(SharedPreferences pref) {
        SharedPreferences.Editor editor = pref.edit();
        editor.putString("project_id", projectId);
        editor.putString("registry_id", registryId);
        editor.putString("device_id", deviceId);
        editor.putString("private_key_file", privateKeyFile);
        editor.putString("algorithm", algorithm);
        editor.putString("cloud_region", cloudRegion);
        editor.putString("mqtt_bridge_hostname", bridgeHostname);
        editor.putInt("mqtt_bridge_port", bridgePort);
        editor.apply();
    }


    /**
     * Construct a CloudIotOptions object from SharedPreferences.
     */
    public static CloudIotOptions from(SharedPreferences pref) {
        try {
            CloudIotOptions options = new CloudIotOptions();
            options.projectId = pref.getString("project_id", null);
            options.registryId = pref.getString("registry_id", null);
            options.deviceId = pref.getString("device_id", null);
            options.algorithm = pref.getString("algorithm", DEFAULT_ALGORITHM);
            options.privateKeyFile = pref.getString("private_key_file", DEFAULT_PRIVATE_KEY_FILE);
            options.cloudRegion = pref.getString("cloud_region", DEFAULT_REGION);
            options.bridgeHostname = pref.getString("mqtt_bridge_hostname",
                    DEFAULT_BRIDGE_HOSTNAME);
            options.bridgePort = (short) pref.getInt("mqtt_bridge_port", DEFAULT_BRIDGE_PORT);
            return options;
        } catch (Exception e) {
            throw new IllegalArgumentException("While processing configuration options", e);
        }
    }

    /**
     * Apply Bundle matched properties.
     */
    public static CloudIotOptions reconfigure(CloudIotOptions original, Bundle bundle) {
        try {
            if (Log.isLoggable(TAG, Log.INFO)) {
                HashSet<String> valid = new HashSet<>(Arrays.asList(new String[] {"project_id",
                        "registry_id", "device_id", "private_key_file", "algorithm",
                        "cloud_region", "mqtt_bridge_hostname", "mqtt_bridge_port"}));
                valid.retainAll(bundle.keySet());
                Log.i(TAG, "Configuring options using the following intent extras: " + valid);
            }

            CloudIotOptions result = new CloudIotOptions();
            result.projectId = bundle.getString("project_id", original.projectId);
            result.registryId = bundle.getString("registry_id", original.registryId);
            result.deviceId = bundle.getString("device_id", original.deviceId);
            result.algorithm = bundle.getString("algorithm", original.algorithm);
            result.privateKeyFile = bundle.getString("private_key_file", original.privateKeyFile);
            result.cloudRegion = bundle.getString("cloud_region", original.cloudRegion);
            result.bridgeHostname = bundle.getString("mqtt_bridge_hostname",
                    original.bridgeHostname);
            result.bridgePort = (short) bundle.getInt("mqtt_bridge_port", original.bridgePort);
            return result;
        } catch (Exception e) {
            throw new IllegalArgumentException("While processing configuration options", e);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CloudIotOptions)) {
            return false;
        }
        CloudIotOptions o = (CloudIotOptions) obj;
        return TextUtils.equals(projectId , o.projectId)
            && TextUtils.equals(registryId, o.registryId)
            && TextUtils.equals(deviceId, o.deviceId)
            && TextUtils.equals(algorithm, o.algorithm)
            && TextUtils.equals(privateKeyFile, o.privateKeyFile)
            && TextUtils.equals(cloudRegion, o.cloudRegion)
            && TextUtils.equals(bridgeHostname, o.bridgeHostname)
            && o.bridgePort == bridgePort;
    }
}
